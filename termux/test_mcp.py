#!/usr/bin/env python3
"""Smoke tests for the local MCP JSON-RPC transport and tool routing."""
import json
import io
import os
import sys
import tempfile
import threading
import time
import unittest
from unittest.mock import patch
from types import SimpleNamespace
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

    def test_chat_stream_sends_flushed_sse_frames(self):
        def fake_chat(data, emit):
            emit({"type":"token","text":"hello"})
            emit({"type":"done"})
            return 200,{"ok":True}

        with patch.object(mcp_server, "provider_chat", side_effect=fake_chat):
            request=Request(self.url.replace("/mcp","/api/chat/stream"),data=b"{}",headers={
                "Authorization":"Bearer mcp-test-token",
                "Content-Type":"application/json"
            },method="POST")
            with urlopen(request,timeout=3) as response:
                frames=[]
                done_seen=False
                while True:
                    line=response.readline()
                    if not line: break
                    frames.append(line.decode("utf-8"))
                    if done_seen and line in (b"\n",b"\r\n"): break
                    if '"type":"done"' in line.decode("utf-8"): done_seen=True
                body="".join(frames)
                self.assertEqual(response.headers.get_content_type(),"text/event-stream")
        self.assertIn('data: {"type":"token","text":"hello"}\n\n',body)
        self.assertIn('data: {"type":"done"}\n\n',body)

    def test_chat_stream_rejects_missing_token(self):
        request=Request(self.url.replace("/mcp","/api/chat/stream"),data=b"{}",headers={
            "Content-Type":"application/json"
        },method="POST")
        with self.assertRaises(HTTPError) as error:
            urlopen(request,timeout=3)
        self.assertEqual(error.exception.code,401)

    def test_openai_stream_collects_text_and_tool_call_deltas(self):
        chunks=[
            {"choices":[{"delta":{"content":"hello "}}]},
            {"choices":[{"delta":{"content":"world","tool_calls":[{"index":0,"id":"call-1","function":{"name":"termux_","arguments":"{"}}]}}]},
            {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"name":"exec","arguments":"\"command\":"}}]}}]},
            {"choices":[{"delta":{"tool_calls":[{"index":0,"function":{"arguments":"\"pwd\"}"}}]}}]},
        ]
        raw=b"".join(("data: "+json.dumps(chunk)+"\n\n").encode() for chunk in chunks)+b"data: [DONE]\n\n"
        emitted=[]
        message=mcp_server.read_openai_stream(io.BytesIO(raw),emitted.append,time.monotonic()+5)
        self.assertEqual(message["content"],"hello world")
        self.assertEqual(message["tool_calls"][0]["function"]["name"],"termux_exec")
        self.assertEqual(json.loads(message["tool_calls"][0]["function"]["arguments"]),{"command":"pwd"})
        self.assertEqual([item["text"] for item in emitted], ["hello ","world"])

    def test_gemini_stream_collects_text_and_partial_function_args(self):
        chunks=[
            {"candidates":[{"content":{"parts":[{"text":"hello "}]}}]},
            {"candidates":[{"content":{"parts":[{"text":"world"},{"thoughtSignature":"sig","functionCall":{"name":"termux_exec","args":{"command":"pwd"}}}]}}]},
        ]
        raw=b"".join(("data: "+json.dumps(chunk)+"\n\n").encode() for chunk in chunks)
        emitted=[]
        message=mcp_server.read_gemini_stream(io.BytesIO(raw),emitted.append,time.monotonic()+5)
        self.assertEqual(message["content"],"hello world")
        self.assertEqual(message["provider_content"]["role"],"model")
        self.assertEqual(message["provider_content"]["parts"][2]["thoughtSignature"],"sig")
        self.assertEqual(message["tool_calls"][0]["function"]["name"],"termux_exec")
        self.assertEqual(json.loads(message["tool_calls"][0]["function"]["arguments"]),{"command":"pwd"})
        self.assertEqual([item["text"] for item in emitted],["hello ","world"])

    def test_anthropic_stream_collects_text_and_tool_json(self):
        events=[
            ("content_block_start",{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}),
            ("content_block_delta",{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"hello"}}),
            ("content_block_start",{"type":"content_block_start","index":1,"content_block":{"type":"tool_use","id":"tool-1","name":"termux_exec","input":{}}}),
            ("content_block_delta",{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":'{"command":"pwd"}'}}),
            ("message_stop",{"type":"message_stop"}),
        ]
        raw=b"".join(("event: "+name+"\ndata: "+json.dumps(payload)+"\n\n").encode() for name,payload in events)
        emitted=[]
        message=mcp_server.read_anthropic_stream(io.BytesIO(raw),emitted.append,time.monotonic()+5)
        self.assertEqual(message["content"],"hello")
        self.assertEqual(message["tool_calls"][0]["id"],"tool-1")
        self.assertEqual(message["tool_calls"][0]["function"]["name"],"termux_exec")
        self.assertEqual(json.loads(message["tool_calls"][0]["function"]["arguments"]),{"command":"pwd"})
        self.assertEqual([item["text"] for item in emitted],["hello"])

    def test_native_provider_stream_requests(self):
        responses={
            "gemini":b'data: {"candidates":[{"content":{"parts":[{"text":"Gemini"}]}}]}\n\n',
            "anthropic":b'event: content_block_start\ndata: {"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}\n\nevent: content_block_delta\ndata: {"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Claude"}}\n\nevent: message_stop\ndata: {"type":"message_stop"}\n\n',
        }
        for provider,expected_text in (("gemini","Gemini"),("anthropic","Claude")):
            requests=[]
            def fake_urlopen(request,timeout):
                requests.append((request,timeout))
                return io.BytesIO(responses[provider])
            with self.subTest(provider=provider), \
                    patch.object(mcp_server,"readj",return_value={"value":"test-api-key"}), \
                    patch.object(mcp_server,"urlopen",side_effect=fake_urlopen):
                events=[]
                status,result=mcp_server.provider_chat({"provider":provider,"model":"test-model","messages":[{"role":"user","content":"hello"}]},events.append)
            self.assertEqual(status,200)
            self.assertEqual(result["content"],expected_text)
            request_payload=json.loads(requests[0][0].data)
            if provider=="anthropic": self.assertTrue(request_payload["stream"])
            self.assertIn(":streamGenerateContent?alt=sse&key=test-api-key" if provider=="gemini" else "/messages",requests[0][0].full_url)
            self.assertIn({"type":"token","text":expected_text},events)

    def test_native_provider_tool_calls_keep_command_approval_gate(self):
        anthropic_events=[
            ("content_block_start",{"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"tool-1","name":"termux_exec","input":{}}}),
            ("content_block_delta",{"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":json.dumps({"command":"echo safe"})}}),
            ("message_stop",{"type":"message_stop"}),
        ]
        responses={
            "gemini":b'data: {"candidates":[{"content":{"parts":[{"functionCall":{"name":"termux_exec","args":{"command":"echo safe"}}}]}}]}\n\n',
            "anthropic":b"".join(("event: "+name+"\ndata: "+json.dumps(payload)+"\n\n").encode() for name,payload in anthropic_events),
        }
        for provider in ("gemini","anthropic"):
            with self.subTest(provider=provider), \
                    patch.object(mcp_server,"readj",return_value={"value":"test-api-key"}), \
                    patch.object(mcp_server,"urlopen",return_value=io.BytesIO(responses[provider])) as urlopen_mock, \
                    patch.object(mcp_server,"dispatch_mcp_tool",return_value={"permission_required":True,"command":"echo safe"}) as dispatch_mock:
                events=[]
                status,result=mcp_server.provider_chat({"provider":provider,"model":"test-model","messages":[{"role":"user","content":"run a command"}]},events.append)
            self.assertEqual(status,200)
            self.assertTrue(result["permission_required"])
            self.assertEqual(result["command"],"echo safe")
            urlopen_mock.assert_called_once()
            dispatch_mock.assert_called_once_with("termux_exec",{"command":"echo safe"})
            self.assertIn({"type":"approval_required","command":"echo safe"},events)

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
        self.assertTrue({"termux_exec", "termux_command_approve", "agent_room_get", "provider_models_list", "provider_models_saved_list", "provider_chat"} <= names)

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

    def test_termux_exec_requires_exact_one_time_approval(self):
        command = "printf approval-test"
        pending = self.call_tool("termux_exec", {"command": command})
        self.assertTrue(pending.get("permission_required"))
        self.assertEqual(pending.get("command"), command)

        approval = self.call_tool("termux_command_approve", {"command": command})
        self.assertTrue(approval.get("approved_once"))

        completed = SimpleNamespace(returncode=0, stdout="approved", stderr="")
        with patch.object(mcp_server.subprocess, "run", return_value=completed) as run_command:
            result = self.call_tool("termux_exec", {"command": command})
            run_command.assert_called_once()
        self.assertEqual(result.get("exit_code"), 0)
        self.assertEqual(result.get("stdout"), "approved")

        denied_again = self.call_tool("termux_exec", {"command": command})
        self.assertTrue(denied_again.get("permission_required"))

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
