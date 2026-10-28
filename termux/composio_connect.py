"""Optional Composio Connect MCP client for Codein's local Termux bridge.

Stores no credentials itself: callers provide a key read from ~/.codein/keys.
"""
import json
from urllib.request import Request, urlopen

ENDPOINT = "https://connect.composio.dev/mcp"


def rpc(api_key, method, params=None, timeout=45):
    if not api_key:
        raise ValueError("Composio Connect consumer key is not configured.")

    def send(payload, session_id=None):
        headers = {
            "x-consumer-api-key": api_key,
            "Accept": "application/json, text/event-stream",
            "Content-Type": "application/json",
            "MCP-Protocol-Version": "2025-03-26",
        }
        if session_id:
            headers["MCP-Session-Id"] = session_id
        request = Request(ENDPOINT, data=json.dumps(payload).encode("utf-8"),
                          headers=headers, method="POST")
        with urlopen(request, timeout=timeout) as response:
            body = response.read(4_000_000).decode("utf-8", "replace")
            session = response.headers.get("MCP-Session-Id")
        parsed = None
        try:
            parsed = json.loads(body)
        except Exception:
            for line in body.splitlines():
                if line.startswith("data:"):
                    try:
                        parsed = json.loads(line[5:].strip())
                        break
                    except Exception:
                        pass
        if parsed is None:
            raise RuntimeError("Composio returned an unrecognized MCP response.")
        if parsed.get("error"):
            error = parsed["error"]
            raise RuntimeError("Composio MCP error: " + str(error.get("message", "request failed"))[:500])
        return parsed.get("result") or {}, session

    _, session_id = send({
        "jsonrpc": "2.0", "id": 1, "method": "initialize",
        "params": {"protocolVersion": "2025-03-26", "capabilities": {},
                   "clientInfo": {"name": "Codein-Termux", "version": "1.0.0"}},
    })
    try:
        send({"jsonrpc": "2.0", "method": "notifications/initialized", "params": {}}, session_id)
    except Exception:
        pass
    result, _ = send({"jsonrpc": "2.0", "id": 2, "method": method, "params": params or {}}, session_id)
    return result


def tools(api_key):
    result = rpc(api_key, "tools/list")
    return result.get("tools") or []


def call_tool(api_key, name, arguments):
    return rpc(api_key, "tools/call", {"name": name, "arguments": arguments or {}}, timeout=90)
