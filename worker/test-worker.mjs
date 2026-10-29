import assert from "node:assert/strict";
import { test } from "node:test";
import worker from "./src/index.mjs";

const key = "test-signing-secret-that-is-at-least-32-bytes";
const apiKey = "test-composio-project-key";

function envWithFetch(fetcher) {
  return {
    IDENTITY_SIGNING_SECRET: key,
    COMPOSIO_API_KEY: apiKey,
    BOOTSTRAP_LIMITER: { limit: async () => ({ success: true }) },
    API_LIMITER: { limit: async () => ({ success: true }) },
    __fetch: fetcher,
  };
}

async function bootstrap(env) {
  const response = await worker.fetch(new Request("https://worker.test/v1/bootstrap", { method: "POST" }), env);
  assert.equal(response.status, 200);
  return response.json();
}

test("bootstrap issues unique signed install identities", async () => {
  const env = envWithFetch(() => { throw new Error("unexpected fetch"); });
  const first = await bootstrap(env);
  const second = await bootstrap(env);
  assert.match(first.user_id, /^codein_[A-Za-z0-9]{32}$/);
  assert.notEqual(first.user_id, second.user_id);
  assert.match(first.token, /^v1\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/);
});

test("tool routes reject missing and forged identities", async () => {
  const env = envWithFetch(() => { throw new Error("unexpected fetch"); });
  const request = (token) => new Request("https://worker.test/v1/tools", {
    method: "POST",
    headers: token ? { Authorization: "Bearer " + token } : {},
    body: "{}",
  });
  assert.equal((await worker.fetch(request(""), env)).status, 401);
  assert.equal((await worker.fetch(request("v1.eyJzdWIiOiJjb2RlaW5fYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhYWFhIn0.invalid"), env)).status, 401);
});

test("bootstrap fails closed without a rate limit binding", async () => {
  const env = envWithFetch(() => { throw new Error("unexpected fetch"); });
  env.BOOTSTRAP_LIMITER = undefined;
  const response = await worker.fetch(new Request("https://worker.test/v1/bootstrap", { method: "POST" }), env);
  assert.equal(response.status, 429);
});

test("Composio project key is only used by Worker upstream calls", async () => {
  const calls = [];
  const sessionId = "trs_0123456789abcdef";
  let ownerId = "";
  const env = envWithFetch(async (input, init) => {
    const url = new URL(input);
    calls.push({ url: url.pathname, headers: new Headers(init.headers), body: init.body });
    if (url.pathname.endsWith("/tool_router/session") && init.method === "POST") {
      ownerId = JSON.parse(init.body).user_id;
      return Response.json({ session_id: sessionId });
    }
    if (url.pathname.endsWith("/tool_router/session/" + sessionId) && init.method === "GET") {
      return Response.json({ session_id: sessionId, config: { user_id: ownerId } });
    }
    if (url.pathname.endsWith("/tools")) {
      return Response.json({ items: [{ slug: "COMPOSIO_SEARCH_TOOLS", description: "Search", input_parameters: { type: "object", properties: {} } }] });
    }
    if (url.pathname.endsWith("/execute")) {
      return Response.json({ result_type: "completed", data: { ok: true } });
    }
    throw new Error("Unexpected upstream path " + url.pathname);
  });
  const identity = await bootstrap(env);
  const tokenRequest = (path, body) => new Request("https://worker.test" + path, {
    method: "POST",
    headers: { Authorization: "Bearer " + identity.token, "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const createdSession = await worker.fetch(tokenRequest("/v1/session", {}), env);
  assert.deepEqual(await createdSession.json(), { session_id: sessionId });
  const toolsResponse = await worker.fetch(tokenRequest("/v1/tools", { session_id: sessionId }), env);
  assert.deepEqual(await toolsResponse.json(), { session_id: sessionId, tools: [{ name: "COMPOSIO_SEARCH_TOOLS", description: "Search", inputSchema: { type: "object", properties: {} } }] });
  const executeResponse = await worker.fetch(tokenRequest("/v1/execute", { session_id: sessionId, tool_slug: "COMPOSIO_SEARCH_TOOLS", arguments: {} }), env);
  assert.deepEqual(await executeResponse.json(), { result_type: "completed", data: { ok: true }, session_id: sessionId });
  assert.ok(calls.filter((call) => call.url.includes("/tool_router/session")).every((call) => call.headers.get("x-api-key") === apiKey));
  assert.equal(JSON.parse(calls.find((call) => call.url.endsWith("/tool_router/session") && call.body)?.body || "{}").user_id, identity.user_id);
  assert.ok(calls.some((call) => call.url.endsWith("/execute")));
  assert.ok(calls.some((call) => call.url.endsWith("/tools")));
});

test("a session owned by a different installation is rejected", async () => {
  const env = envWithFetch(async (input) => {
    const url = new URL(input);
    if (url.pathname.endsWith("/tool_router/session/trs_other")) {
      return Response.json({ config: { user_id: "codein_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" } });
    }
    throw new Error("Unexpected upstream path " + url.pathname);
  });
  const identity = await bootstrap(env);
  const response = await worker.fetch(new Request("https://worker.test/v1/tools", {
    method: "POST",
    headers: { Authorization: "Bearer " + identity.token, "Content-Type": "application/json" },
    body: JSON.stringify({ session_id: "trs_other" }),
  }), env);
  assert.equal(response.status, 403);
  assert.deepEqual(await response.json(), { error: "Session does not belong to this installation" });
});
