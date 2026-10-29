const COMPOSIO_API = "https://backend.composio.dev/api/v3.1";
const encoder = new TextEncoder();

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Headers": "Authorization, Content-Type",
      "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
    },
  });
}

function base64url(bytes) {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
}

function decodeBase64url(value) {
  const base64 = value.replaceAll("-", "+").replaceAll("_", "/");
  const binary = atob(base64 + "=".repeat((4 - (base64.length % 4)) % 4));
  return Uint8Array.from(binary, (char) => char.charCodeAt(0));
}

async function signingKey(secret) {
  if (!secret || secret.length < 32) throw new Error("Worker signing secret is not configured");
  return crypto.subtle.importKey("raw", encoder.encode(secret), { name: "HMAC", hash: "SHA-256" }, false, ["sign", "verify"]);
}

async function issueIdentity(env) {
  const userId = "codein_" + crypto.randomUUID().replaceAll("-", "");
  const payload = base64url(encoder.encode(JSON.stringify({ v: 1, sub: userId })));
  const unsigned = "v1." + payload;
  const signature = new Uint8Array(await crypto.subtle.sign("HMAC", await signingKey(env.IDENTITY_SIGNING_SECRET), encoder.encode(unsigned)));
  return { user_id: userId, token: unsigned + "." + base64url(signature) };
}

async function identityFromRequest(request, env) {
  const authorization = request.headers.get("Authorization") || "";
  const token = authorization.startsWith("Bearer ") ? authorization.slice(7) : "";
  const parts = token.split(".");
  if (parts.length !== 3 || parts[0] !== "v1") return null;
  try {
    const unsigned = parts.slice(0, 2).join(".");
    const payload = JSON.parse(new TextDecoder().decode(decodeBase64url(parts[1])));
    if (payload.v !== 1 || !/^codein_[A-Za-z0-9]{32}$/.test(payload.sub || "")) return null;
    const valid = await crypto.subtle.verify("HMAC", await signingKey(env.IDENTITY_SIGNING_SECRET), decodeBase64url(parts[2]), encoder.encode(unsigned));
    return valid ? payload.sub : null;
  } catch {
    return null;
  }
}

async function limit(binding, key) {
  if (!binding) return false;
  const result = await binding.limit({ key });
  return result.success;
}

async function readJson(request) {
  const text = await request.text();
  if (text.length > 1_000_000) throw new Error("Request body too large");
  return text ? JSON.parse(text) : {};
}

async function composioRequest(env, path, method = "GET", body) {
  if (!env.COMPOSIO_API_KEY) throw new Error("Composio project key is not configured");
  const response = await (env.__fetch || fetch)(COMPOSIO_API + path, {
    method,
    headers: {
      "x-api-key": env.COMPOSIO_API_KEY,
      Accept: "application/json",
      ...(body ? { "Content-Type": "application/json" } : {}),
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
  });
  if (!response.ok) {
    const error = new Error("Composio request failed (HTTP " + response.status + ")");
    error.status = response.status;
    throw error;
  }
  return response.status === 204 ? {} : response.json();
}

async function createSession(env, userId) {
  const created = await composioRequest(env, "/tool_router/session", "POST", {
    user_id: userId,
    manage_connections: { enable: true, enable_wait_for_connections: true },
    workbench: { enable: false },
  });
  if (!created.session_id) throw new Error("Composio did not create a session");
  return created.session_id;
}

async function ensureSession(env, userId, sessionId) {
  if (sessionId) {
    try {
      const session = await composioRequest(env, "/tool_router/session/" + encodeURIComponent(sessionId));
      if (session.config?.user_id !== userId) throw new Error("Session does not belong to this installation");
      return sessionId;
    } catch (error) {
      if (error.status !== 400 && error.status !== 404) throw error;
    }
  }
  return createSession(env, userId);
}

async function tools(env, sessionId) {
  const result = await composioRequest(env, "/tool_router/session/" + encodeURIComponent(sessionId) + "/tools?limit=500");
  return {
    session_id: sessionId,
    tools: (result.items || []).filter((item) => item.slug).map((item) => ({
      name: item.slug,
      description: String(item.description || item.name || "Composio tool").slice(0, 1500),
      inputSchema: item.input_parameters || { type: "object", properties: {} },
    })),
  };
}

async function execute(env, sessionId, body) {
  const slug = String(body.tool_slug || "");
  if (!/^[A-Z0-9_]{1,100}$/.test(slug)) throw new Error("Invalid Composio tool name");
  if (!body.arguments || typeof body.arguments !== "object" || Array.isArray(body.arguments)) {
    throw new Error("Tool arguments must be an object");
  }
  const result = await composioRequest(
    env,
    "/tool_router/session/" + encodeURIComponent(sessionId) + "/execute",
    "POST",
    {
      tool_slug: slug,
      arguments: body.arguments,
      ...(body.input_responses ? { input_responses: body.input_responses } : {}),
      ...(body.request_state ? { request_state: body.request_state } : {}),
    },
  );
  return { ...result, session_id: sessionId };
}

function responseStatus(error) {
  if (error.message === "Request body too large") return 413;
  if (error.message.startsWith("Invalid Composio tool name") || error.message.startsWith("Tool arguments must")) return 400;
  if (error.message.startsWith("Session does not belong")) return 403;
  return 502;
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method === "OPTIONS") return new Response(null, {
      status: 204,
      headers: {
        "Access-Control-Allow-Origin": "*",
        "Access-Control-Allow-Headers": "Authorization, Content-Type",
        "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
      },
    });
    if (url.pathname === "/health" && request.method === "GET") return json({ ok: true });

    if (url.pathname === "/v1/bootstrap" && request.method === "POST") {
      try {
        const ip = request.headers.get("CF-Connecting-IP") || "unknown";
        if (!(await limit(env.BOOTSTRAP_LIMITER, ip))) return json({ error: "Too many requests" }, 429);
        return json(await issueIdentity(env));
      } catch {
        return json({ error: "Identity service is not configured" }, 503);
      }
    }

    if (!["/v1/session", "/v1/tools", "/v1/execute"].includes(url.pathname) || request.method !== "POST") {
      return json({ error: "Not found" }, 404);
    }

    try {
      const userId = await identityFromRequest(request, env);
      if (!userId) return json({ error: "Unauthorized" }, 401);
      if (!(await limit(env.API_LIMITER, userId))) return json({ error: "Too many requests" }, 429);
      const body = await readJson(request);
      if (url.pathname === "/v1/session") {
        return json({ session_id: await ensureSession(env, userId, body.session_id) });
      }
      const sessionId = await ensureSession(env, userId, body.session_id);
      const result = url.pathname === "/v1/tools" ? await tools(env, sessionId) : await execute(env, sessionId, body);
      return json(result);
    } catch (error) {
      const status = responseStatus(error);
      return json({ error: status === 413 ? "Request body too large" :
        status === 400 ? "Invalid tool request" :
        status === 403 ? "Session does not belong to this installation" :
        "Composio request failed" }, status);
    }
  },
};
