#!/usr/bin/env python3
"""Codein local Termux bridge. Python standard library only."""
import json, os, subprocess, time
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import quote, unquote, urlparse
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError

ROOT=Path.home()/".codein"
CONFIG=json.loads((ROOT/"config.json").read_text()) if (ROOT/"config.json").exists() else {}
TOKEN=str(CONFIG.get("token",""))
PORT=int(CONFIG.get("port",8765))
WORKSPACE=Path(CONFIG.get("workspace",str(ROOT/"workspace"))).expanduser()
KEYS=ROOT/"keys"; EVENTS=ROOT/"events"; SESSIONS=ROOT/"sessions"
for d in (WORKSPACE,KEYS,EVENTS,SESSIONS,ROOT/"agents",ROOT/"skills",ROOT/"tools",ROOT/"logs"): d.mkdir(parents=True,exist_ok=True)

def now(): return datetime.now(timezone.utc).isoformat()
PROVIDERS = {
    "gemini": {"name":"Google Gemini","key":"gemini_api_key","base_url":"https://generativelanguage.googleapis.com/v1beta","default_model":"","kind":"gemini","models":[]},
    "openai": {"name":"OpenAI","key":"openai_api_key","base_url":"https://api.openai.com/v1","default_model":"","kind":"openai","models":[]},
    "deepseek": {"name":"DeepSeek","key":"deepseek_api_key","base_url":"https://api.deepseek.com","default_model":"","kind":"openai","models":[]},
    "anthropic": {"name":"Anthropic Claude","key":"anthropic_api_key","base_url":"https://api.anthropic.com/v1","default_model":"","kind":"anthropic","models":[]},
    "openrouter": {"name":"OpenRouter","key":"openrouter_api_key","base_url":"https://openrouter.ai/api/v1","default_model":"","kind":"openai","models":[]},
    "mistral": {"name":"Mistral","key":"mistral_api_key","base_url":"https://api.mistral.ai/v1","default_model":"","kind":"openai","models":[]},
    "xai": {"name":"xAI Grok","key":"xai_api_key","base_url":"https://api.x.ai/v1","default_model":"","kind":"openai","models":[]},
    "groq": {"name":"Groq","key":"groq_api_key","base_url":"https://api.groq.com/openai/v1","default_model":"","kind":"openai","models":[]},
}

def provider_catalog():
    return [
        {"id":pid,"name":item["name"],"base_url":item["base_url"],
         "default_model":item["default_model"],"models":[],
         "configured":(KEYS/(item["key"]+".json")).is_file(),
         "credential_name":item["key"]}
        for pid,item in PROVIDERS.items()
    ]

def discover_provider_models(pid, api_key):
    provider=PROVIDERS.get(str(pid).strip().lower())
    key=str(api_key or "").strip()
    if not provider: return 400,{"ok":False,"error":"Unsupported provider"}
    if not key: return 400,{"ok":False,"error":"API key is required"}
    try:
        if provider["kind"]=="gemini":
            url=provider["base_url"]+"/models?key="+quote(key,safe="")
            headers={"Accept":"application/json"}
        elif provider["kind"]=="anthropic":
            url=provider["base_url"]+"/models?limit=1000"
            headers={"x-api-key":key,"anthropic-version":"2023-06-01","Accept":"application/json"}
        else:
            url=provider["base_url"].rstrip("/")+"/models"
            headers={"Authorization":"Bearer "+key,"Accept":"application/json"}
        req=Request(url,headers=headers,method="GET")
        with urlopen(req,timeout=25) as response:
            raw=json.loads(response.read(4_000_000).decode())
        entries=raw.get("models") if provider["kind"]=="gemini" else raw.get("data",[])
        models=[]
        for item in entries or []:
            if not isinstance(item,dict): continue
            mid=str(item.get("name" if provider["kind"]=="gemini" else "id","")).strip()
            if provider["kind"]=="gemini":
                mid=mid.removeprefix("models/")
                methods=item.get("supportedGenerationMethods",[])
                if "generateContent" not in methods: continue
            if mid and mid not in models: models.append(mid)
        models.sort()
        if not models: return 502,{"ok":False,"error":"Provider returned no usable models. Check key permissions and provider API access."}
        event("provider_models_discovered",{"provider":pid,"count":len(models)})
        return 200,{"ok":True,"provider":pid,"provider_name":provider["name"],"models":models}
    except HTTPError as e:
        event("provider_models_error",{"provider":pid,"status":e.code})
        detail=""
        try:
            payload=json.loads(e.read(8000).decode(errors="replace"))
            detail=str((payload.get("error") or {}).get("message") or payload.get("message") or "")
        except Exception: pass
        return 502,{"ok":False,"error":"Provider model lookup failed (HTTP "+str(e.code)+")","detail":detail[:500]}
    except (URLError,TimeoutError):
        return 502,{"ok":False,"error":"Provider connection failed while listing models"}
    except Exception as e:
        return 502,{"ok":False,"error":"Provider model lookup failed","error_type":type(e).__name__}

def mcp_tools_catalog():
    return [
        {"name":"termux_exec","description":"Run a shell command as the Termux user in the shared Codein workspace. Use only for actions authorized by the user.","inputSchema":{"type":"object","properties":{"command":{"type":"string","description":"Shell command to run"},"timeout":{"type":"integer","minimum":1,"maximum":900,"default":120}},"required":["command"]}},
        {"name":"agent_room_get","description":"Read shared Agent Room context, workspace state, recent events, sessions and skills. Only credential names are included; never return secret values.","inputSchema":{"type":"object","properties":{}}},
        {"name":"credentials_list","description":"List names of saved local credentials without exposing credential values.","inputSchema":{"type":"object","properties":{}}},
        {"name":"credential_save","description":"Save a user-provided API credential into the private local Termux vault. Never echo the value.","inputSchema":{"type":"object","properties":{"name":{"type":"string"},"value":{"type":"string"}},"required":["name","value"]}},
        {"name":"session_save","description":"Save or update a short local session summary for Agent Room continuity.","inputSchema":{"type":"object","properties":{"id":{"type":"string"},"title":{"type":"string"},"summary":{"type":"string"},"next_action":{"type":"string"}},"required":["id","title","summary"]}},
        {"name":"event_record","description":"Append a brief non-secret activity event to the local Agent Room journal.","inputSchema":{"type":"object","properties":{"kind":{"type":"string"},"summary":{"type":"string"}},"required":["kind","summary"]}},
        {"name":"state_update","description":"Update shared Agent Room task state using a partial JSON object.","inputSchema":{"type":"object","properties":{"fields":{"type":"object"}},"required":["fields"]}},
        {"name":"provider_models_list","description":"Query a supported provider's live model catalog with a user-supplied API key. This does not store or log the key.","inputSchema":{"type":"object","properties":{"provider":{"type":"string","enum":sorted(PROVIDERS)},"api_key":{"type":"string"}},"required":["provider","api_key"]}},
        {"name":"provider_chat","description":"Send a user chat turn to the selected configured AI provider. The server can call local MCP tools during the turn and returns the final answer.","inputSchema":{"type":"object","properties":{"provider":{"type":"string","enum":sorted(PROVIDERS)},"model":{"type":"string"},"messages":{"type":"array","items":{"type":"object","properties":{"role":{"type":"string","enum":["user","assistant","system"]},"content":{"type":"string"}},"required":["role","content"]}},"system":{"type":"string"},"timeout":{"type":"integer","minimum":10,"maximum":180}},"required":["provider","model","messages"]}},
        {"name":"opencode_manage","description":"Manage OpenCode agent engine: install, run, or check status.","inputSchema":{"type":"object","properties":{"action":{"type":"string","enum":["install","run","status","stop"]},"args":{"type":"string"}},"required":["action"]}}
    ]

def dispatch_mcp_tool(name, args):
    args=args if isinstance(args,dict) else {}
    if name=="termux_exec":
        command=str(args.get("command","")).strip()
        timeout=max(1,min(int(args.get("timeout",120)),900))
        if not command: raise ValueError("Command is empty")
        event("command_started",{"command":command[:500]}); start=time.monotonic()
        try:
            run=subprocess.run(["/data/data/com.termux/files/usr/bin/bash","-lc",command],cwd=str(WORKSPACE),capture_output=True,text=True,timeout=timeout,env={**os.environ,"CODEIN_ROOT":str(ROOT),"CODEIN_WORKSPACE":str(WORKSPACE)})
            result={"exit_code":run.returncode,"stdout":run.stdout[-100000:],"stderr":run.stderr[-30000:],"duration_ms":int((time.monotonic()-start)*1000)}
        except subprocess.TimeoutExpired as exc:
            result={"exit_code":124,"error":"Command timed out","stdout":str(exc.stdout or "")[-100000:],"stderr":str(exc.stderr or "")[-30000:],"duration_ms":int((time.monotonic()-start)*1000)}
        event("command_finished",{"exit_code":result["exit_code"],"duration_ms":result["duration_ms"]})
        return result
    if name=="agent_room_get":
        journal=EVENTS/"journal.jsonl"
        lines=journal.read_text(encoding="utf-8").splitlines()[-25:] if journal.exists() else []
        sessions=[]
        for file in sorted(SESSIONS.glob("*.json"),key=lambda x:x.stat().st_mtime,reverse=True)[:5]:
            item=readj(file,{})
            sessions.append({"id":item.get("id"),"title":item.get("title"),"summary":str(item.get("summary",""))[-1800:],"next_action":item.get("next_action")})
        events=[json.loads(line) for line in lines if line.strip()]
        skills={f.stem:f.read_text(encoding="utf-8")[:10000] for f in (ROOT/"skills").glob("*.md")}
        room_file=ROOT/"ROOM.md"
        return {"root":str(ROOT),"workspace":str(WORKSPACE),"room":room_file.read_text(encoding="utf-8") if room_file.exists() else "","state":readj(ROOT/"state.json",{}),"credentials":sorted(x.stem for x in KEYS.glob("*.json") if x.is_file()),"events":events,"sessions":sessions,"skills":skills}
    if name=="credentials_list":
        return {"credentials":sorted(x.stem for x in KEYS.glob("*.json") if x.is_file())}
    if name=="credential_save":
        name=str(args.get("name","")).strip(); value=str(args.get("value",""))
        if not name or any(char in name for char in "/\\") or not value: raise ValueError("Credential name and value are required")
        path=KEYS/(name+".json")
        path.write_text(json.dumps({"name":name,"value":value,"updated_at":now()},ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
        path.chmod(0o600); event("credential_saved",{"name":name})
        return {"ok":True,"name":name}
    if name=="session_save":
        payload=dict(args)
        sid="".join(char for char in str(payload.get("id") or int(time.time())) if char.isalnum() or char in "-_")[:80]
        if not sid: raise ValueError("Invalid session id")
        payload["id"]=sid; payload["updated_at"]=now()
        path=SESSIONS/(sid+".json")
        path.write_text(json.dumps(payload,ensure_ascii=False,indent=2)+"\n",encoding="utf-8"); path.chmod(0o600)
        event("session_saved",{"id":sid,"title":payload.get("title","")})
        return {"ok":True,"id":sid}
    if name=="event_record":
        event(str(args.get("kind","note"))[:100],{"summary":str(args.get("summary",""))[:1000]})
        return {"ok":True}
    if name=="state_update":
        fields=args.get("fields") or {}
        if not isinstance(fields,dict): raise ValueError("fields must be an object")
        state=readj(ROOT/"state.json",{}); state.update(fields); state["last_updated"]=now()
        path=ROOT/"state.json"; path.write_text(json.dumps(state,ensure_ascii=False,indent=2)+"\n",encoding="utf-8"); path.chmod(0o600)
        event("workspace_state_updated",{"keys":sorted(fields.keys())})
        return {"ok":True,"state":state}
    if name=="provider_models_list":
        status,result=discover_provider_models(args.get("provider"),args.get("api_key"))
        if status!=200: raise RuntimeError(result.get("error","Provider model discovery failed")+(": "+result.get("detail","") if result.get("detail") else ""))
        return result
    if name=="provider_chat":
        status,result=provider_chat(args)
        return result
    if name=="opencode_manage":
        action=str(args.get("action","status"))
        if action=="install":
            run=subprocess.run(["/data/data/com.termux/files/usr/bin/npm","install","-g","opencode-ai"],capture_output=True,text=True)
            return {"ok":run.returncode==0,"status":"Installation finished","stdout":run.stdout,"stderr":run.stderr}
        if action=="run":
            # Start OpenCode server in background
            proc=subprocess.Popen(["opencode","serve","--port","8766"],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,start_new_session=True)
            return {"ok":True,"status":"OpenCode server started (PID "+str(proc.pid)+")"}
        if action=="status":
            # Check if opencode process is running
            run=subprocess.run(["pgrep","-f","opencode"],capture_output=True,text=True)
            return {"ok":True,"status":"Running" if run.returncode==0 else "Stopped"}
        if action=="stop":
            subprocess.run(["pkill","-f","opencode"])
            return {"ok":True,"status":"OpenCode server stopped"}
        return {"ok":False,"error":"Invalid action"}
    raise ValueError("Unknown MCP tool: "+str(name))

def provider_chat(data):
    pid=str(data.get("provider","")).strip().lower()
    provider=PROVIDERS.get(pid)
    if not provider: return 400,{"ok":False,"error":"Unknown provider","providers":list(PROVIDERS)}
    key_data=readj(KEYS/(provider["key"]+".json"),{})
    api_key=str(key_data.get("value","")).strip()
    if not api_key: return 428,{"ok":False,"error":"missing_api_key","provider":pid,"provider_name":provider["name"],"credential_name":provider["key"],"message":"API key is not configured. Ask the user to provide it in this chat; store it in the local Termux vault before retrying."}
    model=str(data.get("model") or provider["default_model"]).strip()
    messages=data.get("messages") or []
    if not isinstance(messages,list) or not messages: return 400,{"ok":False,"error":"messages must be a non-empty array"}
    clean=[]
    for item in messages[-40:]:
        if not isinstance(item,dict): continue
        role=str(item.get("role","user"))
        if role not in ("user","assistant","system"): continue
        content=str(item.get("content",""))
        if content: clean.append({"role":role,"content":content[:50000]})
    system=str(data.get("system",""))[:30000]
    timeout=max(10,min(int(data.get("timeout",120)),180))
    # Provider-native model calls receive only local action tools. Avoid recursion
    # and don't ask the model to invent API keys for provider discovery.
    tool_catalog=[tool for tool in mcp_tools_catalog() if tool["name"] not in ("provider_chat","provider_models_list","credential_save")]
    try:
        if provider["kind"]=="gemini":
            contents=[]
            for item in clean:
                if item["role"]=="system": system += "\n"+item["content"]; continue
                contents.append({"role":"model" if item["role"]=="assistant" else "user","parts":[{"text":item["content"]}]})
            function_declarations=[{"name":tool["name"],"description":tool["description"],"parameters":tool["inputSchema"]} for tool in tool_catalog]
            url=provider["base_url"]+"/models/"+quote(model,safe="")+":generateContent?key="+quote(api_key,safe="")
            headers={"Content-Type":"application/json"}
            for _ in range(9):
                payload={"contents":contents,"generationConfig":{"temperature":0.3},"tools":[{"functionDeclarations":function_declarations}]}
                if system: payload["systemInstruction"]={"parts":[{"text":system}]}
                request=Request(url,data=json.dumps(payload).encode(),headers=headers,method="POST")
                with urlopen(request,timeout=timeout) as response: raw=json.loads(response.read(3_000_000).decode())
                candidates=raw.get("candidates") or []
                candidate=candidates[0] if candidates else {}
                model_content=candidate.get("content") or {}
                parts=model_content.get("parts") or []
                calls=[part.get("functionCall") for part in parts if isinstance(part,dict) and isinstance(part.get("functionCall"),dict)]
                if not calls:
                    answer="".join(str(part.get("text","")) for part in parts if isinstance(part,dict))
                    break
                contents.append(model_content)
                tool_responses=[]
                for call in calls[:12]:
                    tool_name=str(call.get("name",""))
                    arguments=call.get("args") or {}
                    try: tool_result=dispatch_mcp_tool(tool_name,arguments)
                    except Exception as exc: tool_result={"error":str(exc)[:1500]}
                    tool_responses.append({"functionResponse":{"name":tool_name,"response":{"result":tool_result}}})
                contents.append({"role":"user","parts":tool_responses})
            else: answer="The MCP tool loop reached its safety limit. Please continue with the available result."
        elif provider["kind"]=="anthropic":
            system_parts=[system] if system else []
            for item in clean:
                if item["role"]=="system": system_parts.append(item["content"])
            msgs=[{"role":"assistant" if item["role"]=="assistant" else "user","content":item["content"]} for item in clean if item["role"]!="system"]
            tools=[{"name":tool["name"],"description":tool["description"],"input_schema":tool["inputSchema"]} for tool in tool_catalog]
            url=provider["base_url"]+"/messages"
            headers={"Content-Type":"application/json","x-api-key":api_key,"anthropic-version":"2023-06-01"}
            for _ in range(9):
                payload={"model":model,"max_tokens":4096,"messages":msgs,"tools":tools}
                if system_parts: payload["system"]="\n\n".join(system_parts)
                request=Request(url,data=json.dumps(payload).encode(),headers=headers,method="POST")
                with urlopen(request,timeout=timeout) as response: raw=json.loads(response.read(3_000_000).decode())
                blocks=raw.get("content") or []
                tool_uses=[block for block in blocks if isinstance(block,dict) and block.get("type")=="tool_use"]
                if not tool_uses:
                    answer="".join(str(block.get("text","")) for block in blocks if isinstance(block,dict) and block.get("type")=="text")
                    break
                msgs.append({"role":"assistant","content":blocks})
                results=[]
                for call in tool_uses[:12]:
                    try: result=dispatch_mcp_tool(str(call.get("name","")),call.get("input") or {})
                    except Exception as exc: result={"error":str(exc)[:1500]}
                    results.append({"type":"tool_result","tool_use_id":call.get("id"),"content":json.dumps(result,ensure_ascii=False)})
                msgs.append({"role":"user","content":results})
            else: answer="The MCP tool loop reached its safety limit. Please continue with the available result."
        else:
            msgs=[]
            if system: msgs.append({"role":"system","content":system})
            msgs.extend(item for item in clean if item["role"]!="system")
            tools=[{"type":"function","function":{"name":tool["name"],"description":tool["description"],"parameters":tool["inputSchema"]}} for tool in tool_catalog]
            url=provider["base_url"].rstrip("/")+"/chat/completions"
            headers={"Content-Type":"application/json","Authorization":"Bearer "+api_key}
            for _ in range(9):
                payload={"model":model,"messages":msgs,"stream":False,"tools":tools,"tool_choice":"auto"}
                request=Request(url,data=json.dumps(payload).encode(),headers=headers,method="POST")
                with urlopen(request,timeout=timeout) as response: raw=json.loads(response.read(3_000_000).decode())
                choices=raw.get("choices") or []
                message=(choices[0].get("message") or {}) if choices else {}
                calls=message.get("tool_calls") or []
                if not calls:
                    answer=str(message.get("content") or "")
                    break
                msgs.append(message)
                for call in calls[:12]:
                    function=call.get("function") or {}
                    name=str(function.get("name",""))
                    try: arguments=json.loads(function.get("arguments") or "{}")
                    except ValueError: arguments={}
                    try: result=dispatch_mcp_tool(name,arguments)
                    except Exception as exc: result={"error":str(exc)[:1500]}
                    msgs.append({"role":"tool","tool_call_id":call.get("id"),"content":json.dumps(result,ensure_ascii=False)})
            else: answer="The MCP tool loop reached its safety limit. Please continue with the available result."
        if not answer: return 502,{"ok":False,"error":"Provider returned an empty response","provider":pid,"model":model}
        event("provider_chat_completed",{"provider":pid,"model":model})
        return 200,{"ok":True,"provider":pid,"model":model,"content":answer}
    except HTTPError as exc:
        detail=""
        try: detail=exc.read(12000).decode(errors="replace")
        except Exception: pass
        event("provider_chat_error",{"provider":pid,"model":model,"status":exc.code})
        return 502,{"ok":False,"error":"Provider HTTP "+str(exc.code),"provider":pid,"model":model,"detail":detail[:3000]}
    except (URLError,TimeoutError):
        return 502,{"ok":False,"error":"Provider connection failed. Check network access and try again."}
    except Exception as exc:
        return 502,{"ok":False,"error":"Provider request failed","error_type":type(exc).__name__}

def readj(p, fallback):
    try: return json.loads(p.read_text(encoding="utf-8"))
    except (OSError,ValueError): return fallback
def event(kind,data):
    with (EVENTS/"journal.jsonl").open("a",encoding="utf-8") as f: f.write(json.dumps({"at":now(),"kind":kind,"data":data},ensure_ascii=False)+"\n")

class Handler(BaseHTTPRequestHandler):
    server_version="CodeinTermux/1.0"
    def log_message(self,*args): pass
    def sendj(self,status,obj):
        b=json.dumps(obj,ensure_ascii=False).encode()
        self.send_response(status); self.send_header("Content-Type","application/json; charset=utf-8")
        self.send_header("Content-Length",str(len(b))); self.send_header("Access-Control-Allow-Origin","*")
        self.send_header("Access-Control-Allow-Headers","Authorization, Content-Type")
        self.send_header("Access-Control-Allow-Methods","GET, POST, OPTIONS"); self.end_headers(); self.wfile.write(b)
    def auth(self): return bool(TOKEN) and self.headers.get("Authorization","")=="Bearer "+TOKEN
    def body(self):
        n=min(int(self.headers.get("Content-Length","0")),2_000_000)
        return json.loads((self.rfile.read(n) if n else b"{}").decode())
    def do_OPTIONS(self): self.sendj(200,{"ok":True})
    def do_GET(self):
        if not self.auth(): return self.sendj(401,{"ok":False,"error":"Authorization required"})
        p=urlparse(self.path).path
        if p=="/mcp":
            self.send_response(405); self.send_header("Allow","POST"); self.send_header("Content-Length","0"); self.end_headers()
            return
        if p=="/health": return self.sendj(200,{"ok":True,"service":"codein-termux","time":now(),"workspace":str(WORKSPACE)})
        if p=="/api/workspace": return self.sendj(200,{"ok":True,"root":str(ROOT),"workspace":str(WORKSPACE),"state":readj(ROOT/"state.json",{})})
        if p=="/api/room":
            room_file=ROOT/"ROOM.md"
            journal=EVENTS/"journal.jsonl"
            lines=journal.read_text(encoding="utf-8").splitlines()[-25:] if journal.exists() else []
            sessions=[]
            for file in sorted(SESSIONS.glob("*.json"),key=lambda x:x.stat().st_mtime,reverse=True)[:5]:
                item=readj(file,{})
                sessions.append({"id":item.get("id"),"title":item.get("title"),"summary":str(item.get("summary",""))[-1800:],"next_action":item.get("next_action"),"updated_at":item.get("updated_at")})
            skills={f.stem:f.read_text(encoding="utf-8")[:10000] for f in (ROOT/"skills").glob("*.md")}
            events=[]
            for line in lines:
                if line.strip():
                    item=json.loads(line)
                    events.append({"at":item.get("at"),"kind":item.get("kind"),"data":item.get("data",{})})
            return self.sendj(200,{"ok":True,"root":str(ROOT),"workspace":str(WORKSPACE),"room":room_file.read_text(encoding="utf-8") if room_file.exists() else "","state":readj(ROOT/"state.json",{}),"credentials":sorted(x.stem for x in KEYS.glob("*.json") if x.is_file()),"events":events,"sessions":sessions,"skills":skills})
        if p=="/api/keys": return self.sendj(200,{"ok":True,"keys":sorted(x.stem for x in KEYS.glob("*.json") if x.is_file())})
        if p=="/api/providers": return self.sendj(200,{"ok":True,"providers":provider_catalog()})
        if p.startswith("/api/keys/"):
            name=unquote(p[len("/api/keys/"):]).strip()
            if not name or "/" in name or "\\" in name: return self.sendj(400,{"ok":False,"error":"Invalid key name"})
            item=readj(KEYS/(name+".json"),None)
            if item is None: return self.sendj(404,{"ok":False,"error":"Credential not found","name":name})
            return self.sendj(200,{"ok":True,"name":name,"value":item.get("value",""),"updated_at":item.get("updated_at")})
        if p=="/api/events":
            f=EVENTS/"journal.jsonl"; lines=f.read_text(encoding="utf-8").splitlines()[-100:] if f.exists() else []
            return self.sendj(200,{"ok":True,"events":[json.loads(x) for x in lines if x.strip()]})
        if p=="/api/sessions":
            items=[readj(f,{}) for f in sorted(SESSIONS.glob("*.json"),key=lambda x:x.stat().st_mtime,reverse=True)[:50]]
            return self.sendj(200,{"ok":True,"sessions":items})
        return self.sendj(404,{"ok":False,"error":"Unknown endpoint"})
    def mcp_result(self, request_id, result, status=200):
        self.sendj(status, {"jsonrpc":"2.0","id":request_id,"result":result})

    def mcp_error(self, request_id, code, message, data=None, status=200):
        error={"code":code,"message":message}
        if data is not None: error["data"]=data
        self.sendj(status,{"jsonrpc":"2.0","id":request_id,"error":error})

    def handle_mcp(self):
        if not self.auth(): return self.mcp_error(None,-32001,"Authorization required",status=401)
        origin=self.headers.get("Origin")
        allowed_origins={"http://127.0.0.1:"+str(PORT),"http://localhost:"+str(PORT)}
        if origin and origin not in allowed_origins:
            return self.mcp_error(None,-32003,"Origin not allowed",status=403)
        try: message=self.body()
        except Exception: return self.mcp_error(None,-32700,"Parse error",status=400)
        if not isinstance(message,dict) or message.get("jsonrpc")!="2.0" or not isinstance(message.get("method"),str):
            return self.mcp_error(message.get("id") if isinstance(message,dict) else None,-32600,"Invalid Request",status=400)
        method=message["method"]; request_id=message.get("id")
        params=message.get("params") or {}
        # MCP notifications have no request ID and must not receive a JSON-RPC response.
        if request_id is None:
            self.send_response(202); self.send_header("Content-Length","0"); self.end_headers()
            return
        if method=="initialize":
            return self.mcp_result(request_id,{
                "protocolVersion":"2025-03-26",
                "capabilities":{"tools":{"listChanged":False}},
                "serverInfo":{"name":"codein-termux","version":"1.1.0"},
                "instructions":"Local Codein Termux tools. Execute only user-authorized actions. Never expose local credential values."
            })
        if method=="ping": return self.mcp_result(request_id,{})
        if method=="tools/list":
            return self.mcp_result(request_id,{"tools":mcp_tools_catalog()})
        if method=="tools/call":
            name=str(params.get("name","")); args=params.get("arguments") or {}
            try:
                result=dispatch_mcp_tool(name,args)
                return self.mcp_result(request_id,{"content":[{"type":"text","text":json.dumps(result,ensure_ascii=False)}],"isError":False})
            except Exception as exc:
                return self.mcp_result(request_id,{"content":[{"type":"text","text":str(exc)[:1500]}],"isError":True})
        return self.mcp_error(request_id,-32601,"Method not found")

    def do_POST(self):
        if urlparse(self.path).path=="/mcp":
            return self.handle_mcp()
        if not self.auth(): return self.sendj(401,{"ok":False,"error":"Authorization required"})
        p=urlparse(self.path).path
        try: data=self.body()
        except Exception as e: return self.sendj(400,{"ok":False,"error":"Invalid JSON","detail":str(e)})
        if p=="/api/providers/models":
            status,result=discover_provider_models(data.get("provider"),data.get("api_key"))
            return self.sendj(status,result)
        if p=="/api/chat":
            status,result=provider_chat(data)
            return self.sendj(status,result)
        if p=="/api/keys":
            name=str(data.get("name","")).strip(); value=str(data.get("value",""))
            if not name or any(c in name for c in "/\\") or not value: return self.sendj(400,{"ok":False,"error":"Name and non-empty value required"})
            f=KEYS/(name+".json"); f.write_text(json.dumps({"name":name,"value":value,"updated_at":now()},ensure_ascii=False,indent=2)+"\n"); f.chmod(0o600)
            event("credential_saved",{"name":name})
            return self.sendj(200,{"ok":True,"name":name,"saved_to":str(f)})
        if p=="/api/events":
            event(str(data.get("kind","note"))[:100],data.get("data",{})); return self.sendj(200,{"ok":True})
        if p=="/api/state":
            state=readj(ROOT/"state.json",{}); state.update(data); state["last_updated"]=now()
            f=ROOT/"state.json"; f.write_text(json.dumps(state,ensure_ascii=False,indent=2)+"\n"); f.chmod(0o600)
            event("workspace_state_updated",{"keys":sorted(data.keys())})
            return self.sendj(200,{"ok":True,"state":state})
        if p=="/api/sessions":
            sid="".join(c for c in str(data.get("id") or int(time.time())) if c.isalnum() or c in "-_")[:80]
            if not sid: return self.sendj(400,{"ok":False,"error":"Invalid session id"})
            data["id"]=sid; data["updated_at"]=now(); f=SESSIONS/(sid+".json")
            f.write_text(json.dumps(data,ensure_ascii=False,indent=2)+"\n"); f.chmod(0o600)
            event("session_saved",{"id":sid,"title":data.get("title","")})
            return self.sendj(200,{"ok":True,"id":sid})
        if p=="/api/exec":
            command=str(data.get("command","")).strip(); timeout=max(1,min(int(data.get("timeout",120)),900))
            if not command: return self.sendj(400,{"ok":False,"error":"Command is empty"})
            event("command_started",{"command":command[:500]}); start=time.monotonic()
            try:
                r=subprocess.run(["/data/data/com.termux/files/usr/bin/bash","-lc",command],cwd=str(WORKSPACE),capture_output=True,text=True,timeout=timeout,env={**os.environ,"CODEIN_ROOT":str(ROOT),"CODEIN_WORKSPACE":str(WORKSPACE)})
                result={"ok":r.returncode==0,"exit_code":r.returncode,"stdout":r.stdout[-100000:],"stderr":r.stderr[-30000:],"duration_ms":int((time.monotonic()-start)*1000)}
            except subprocess.TimeoutExpired as e: result={"ok":False,"error":"Command timed out","stdout":str(e.stdout or "")[-100000:],"stderr":str(e.stderr or "")[-30000:],"duration_ms":int((time.monotonic()-start)*1000)}
            event("command_finished",{"exit_code":result.get("exit_code"),"duration_ms":result.get("duration_ms")})
            return self.sendj(200,result)
        return self.sendj(404,{"ok":False,"error":"Unknown endpoint"})

if __name__ == "__main__":
    if not TOKEN: raise SystemExit("Missing token. Re-run the Codein bootstrap to initialize ~/.codein/config.json.")
    print(f"[Codein] Local MCP endpoint: http://127.0.0.1:{PORT}/mcp")
    print(f"[Codein] Workspace: {WORKSPACE}")
    print("[Codein] Ctrl+C to stop.")
    ThreadingHTTPServer(("127.0.0.1",PORT),Handler).serve_forever()
