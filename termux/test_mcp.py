#!/usr/bin/env python3
"""Smoke tests for the local MCP JSON-RPC transport and tool routing."""
import json
import os
import sys
import tempfile
import threading
import unittest
from pathlib import Path
from urllib.error import HTTPError
from urllib.request import Request, urlopen

TEST_HOME = tempfile.TemporaryDirectory(prefix="codein-mcp-test-")
os.environ["HOME"] = TEST_HOME.name
ROOT = Path(TEST_HOME.name) / ".codein"
ROOT.mkdir(parents=True, exist_ok=True)
(ROOT / "config.json").write_text(json.dumps({
    "port": 18765,
    "token": "mcp-test-token",
    "workspace": str(ROOT / "workspace")
}), encoding="utf-8")
sys.path.insert(0, str(Path(__file__).resolve().parent))
import server as mcp_server  # noqa: E402


class McpTransportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.httpd = mcp_server.ThreadingHTTPServer(("127.0.0.1", 0), mcp_server.Handler)
        cls.port = cls.httpd.server_address[1]
        cls.thread = threading.Thread(target=cls.httpd.serve_forever, daemon=True)
        cls.thread.start()
        cls.url = "http://127.0.0.1:" + str(cls.port) + "/mcp"

    @classmethod
    def tearDownClass(cls):
        cls.httpd.shutdown()
        cls.httpd.server_close()
        TEST_HOME.cleanup()

    def rpc(self, method, params=None, request_id=1, token="mcp-test-token"):
        payload = {"jsonrpc": "2.0", "method": method}
        if request_id is not None:
            payload["id"] = request_id
        if params is not None:
            payload["params"] = params
        request = Request(self.url, data=json.dumps(payload).encode(), headers={
            "Authorization": "Bearer " + token,
            "Accept": "application/json, text/event-stream",
            "Content-Type": "application/json"
        }, method="POST")
        with urlopen(request, timeout=3) as response:
            body = response.read().decode()
            return response.status, json.loads(body) if body else None

    def call_tool(self, name, arguments=None):
        status, response = self.rpc("tools/call", {
            "name": name, "arguments": arguments or {}
        })
        self.assertEqual(status, 200)
        self.assertNotIn("error", response)
        result = response["result"]
        self.assertFalse(result.get("isError", False), result)
        return json.loads(result["content"][0]["text"])

    def test_initialize_and_tool_catalog(self):
        status, response = self.rpc("initialize", {
            "protocolVersion": "2025-03-26",
            "capabilities": {},
            "clientInfo": {"name": "Codein MCP test", "version": "test"}
        })
        self.assertEqual(status, 200)
        self.assertEqual(response["result"]["protocolVersion"], "2025-03-26")
        status, _ = self.rpc("notifications/initialized", request_id=None)
        self.assertEqual(status, 202)
        _, response = self.rpc("tools/list")
        names = {tool["name"] for tool in response["result"]["tools"]}
        self.assertTrue({"termux_exec", "agent_room_get", "provider_models_list", "provider_chat"} <= names)

    def test_local_agent_room_tools(self):
        saved = self.call_tool("credential_save", {"name": "test_key", "value": "unit-secret"})
        self.assertEqual(saved["name"], "test_key")
        names = self.call_tool("credentials_list")["credentials"]
        self.assertIn("test_key", names)
        room = self.call_tool("agent_room_get")
        self.assertIn("test_key", room["credentials"])
        self.assertNotIn("unit-secret", json.dumps(room))
        state = self.call_tool("state_update", {"fields": {"active_project": "mcp-smoke"}})
        self.assertEqual(state["state"]["active_project"], "mcp-smoke")
        session = self.call_tool("session_save", {"id": "mcp-test", "title": "Smoke test", "summary": "ok"})
        self.assertEqual(session["id"], "mcp-test")
        self.assertTrue(self.call_tool("event_record", {"kind": "test", "summary": "mcp smoke"})["ok"])

    def test_auth_and_origin_validation(self):
        request = Request(self.url, data=b'{"jsonrpc":"2.0","id":1,"method":"ping"}', headers={
            "Authorization": "Bearer wrong-token",
            "Content-Type": "application/json"
        }, method="POST")
        with self.assertRaises(HTTPError) as error:
            urlopen(request, timeout=3)
        self.assertEqual(error.exception.code, 401)
        request = Request(self.url, data=b'{"jsonrpc":"2.0","id":1,"method":"ping"}', headers={
            "Authorization": "Bearer mcp-test-token",
            "Origin": "https://attacker.example",
            "Content-Type": "application/json"
        }, method="POST")
        with self.assertRaises(HTTPError) as error:
            urlopen(request, timeout=3)
        self.assertEqual(error.exception.code, 403)


if __name__ == "__main__":
    unittest.main()
