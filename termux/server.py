#!/usr/bin/env python3
"""Codein local Termux bridge. Python standard library only."""
import hashlib, json, os, subprocess, threading, time, uuid
from contextlib import contextmanager
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import quote, unquote, urlparse
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError
import composio_connect
import composio_platform

ROOT=Path.home()/".codein"
SERVER_VERSION="1.3.0"
CONFIG=json.loads((ROOT/"config.json").read_text()) if (ROOT/"config.json").exists() else {}
TOKEN=str(CONFIG.get("token",""))
PORT=int(CONFIG.get("port",8765))
WORKSPACE=Path(CONFIG.get("workspace",str(ROOT/"workspace"))).expanduser()
KEYS=ROOT/"keys"; EVENTS=ROOT/"events"; SESSIONS=ROOT/"sessions"; PERMISSIONS=ROOT/"permissions"
for d in (WORKSPACE,KEYS,EVENTS,SESSIONS,PERMISSIONS,ROOT/"agents",ROOT/"skills",ROOT/"tools",ROOT/"logs"): d.mkdir(parents=True,exist_ok=True)

class RequestCancelled(Exception): pass

ACTIVE_STREAMS={}
PENDING_CANCELLATIONS={}
ACTIVE_STREAMS_LOCK=threading.Lock()

class StreamControl:
    def __init__(self):
        self.cancelled=threading.Event()
        self.lock=threading.Lock()
        self.response=None

    def cancel(self):
        self.cancelled.set()
        with self.lock:
            response=self.response
        if response is not None:
            try: response.close()
            except Exception: pass

    def check(self):
        if self.cancelled.is_set(): raise RequestCancelled()

    def attach(self,response):
        with self.lock:
            if self.cancelled.is_set():
                try: response.close()
                except Exception: pass
                raise RequestCancelled()
            self.response=response

    def detach(self,response):
        with self.lock:
            if self.response is response: self.response=None

@contextmanager
def tracked_response(control,response):
    if control: control.attach(response)
    try: yield response
    finally:
        if control: control.detach(response)
        try: response.close()
        except Exception: pass

def register_stream(request_id):
    with ACTIVE_STREAMS_LOCK:
        current=time.monotonic()
        for key,expires in list(PENDING_CANCELLATIONS.items()):
            if expires<=current: PENDING_CANCELLATIONS.pop(key,None)
        control=StreamControl()
        if PENDING_CANCELLATIONS.pop(request_id,None): control.cancelled.set()
        ACTIVE_STREAMS[request_id]=control
        return control

def cancel_stream(request_id):
    with ACTIVE_STREAMS_LOCK:
        current=time.monotonic()
        for key,expires in list(PENDING_CANCELLATIONS.items()):
            if expires<=current: PENDING_CANCELLATIONS.pop(key,None)
        control=ACTIVE_STREAMS.get(request_id)
        if control is None:
            while len(PENDING_CANCELLATIONS)>=256:
                PENDING_CANCELLATIONS.pop(next(iter(PENDING_CANCELLATIONS)))
            PENDING_CANCELLATIONS[request_id]=time.monotonic()+60
            return False
    control.cancel()
    return True

def unregister_stream(request_id,control):
    with ACTIVE_STREAMS_LOCK:
        if ACTIVE_STREAMS.get(request_id) is control: ACTIVE_STREAMS.pop(request_id,None)

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


def composio_tool_catalog():
    platform_config=composio_platform.read_config()
    if platform_config.get("worker_url") and platform_config.get("token"):
        try:
            remote=composio_platform.tools(platform_config)
            return [{"name":"composio__"+str(t["name"]),
                     "description":"Composio connected-service tool. "+str(t.get("description",""))[:1000],
                     "inputSchema":t.get("inputSchema") or {"type":"object","properties":{}}}
                    for t in remote if isinstance(t,dict) and t.get("name")]
        except Exception as exc:
            event("composio_tools_error",{"error":str(exc)[:250]})
            return []
    key_data=readj(KEYS/"composio_consumer_api_key.json",{})
    key=str(key_data.get("value","")).strip()
    if not key: return []
    try:
        remote=composio_connect.tools(key)
        return [{"name":"composio__"+str(t["name"]),
                 "description":"Composio connected-service tool. "+str(t.get("description",""))[:1000],
                 "inputSchema":t.get("inputSchema") or {"type":"object","properties":{}}}
                for t in remote if isinstance(t,dict) and t.get("name")]
    except Exception as exc:
        event("composio_tools_error",{"error":str(exc)[:250]})
        return []

def mcp_tools_catalog():
    local_tools = [
        {"name":"composio_platform_configure","description":"Set up Codein's Composio Platform Worker and create or reuse this Termux installation's private Composio identity. The Worker address is supplied by the app; never ask the user for a Composio API key.","inputSchema":{"type":"object","properties":{"worker_url":{"type":"string","description":"The HTTPS workers.dev address configured by the Codein app."}}, "required":["worker_url"]}},
        {"name":"composio_tools_refresh","description":"Discover Composio session tools for this installation's private user identity.","inputSchema":{"type":"object","properties":{}}},
        {"name":"termux_exec","description":"Run a shell command as the Termux user in the shared Codein workspace. This tool requires explicit user approval for each command.","inputSchema":{"type":"object","properties":{"command":{"type":"string","description":"Shell command to run"},"timeout":{"type":"integer","minimum":1,"maximum":900,"default":120}},"required":["command"]}},
        {"name":"termux_command_approve","description":"Record user approval for the exact shell command shown in the app permission prompt. Do not call unless the user explicitly approved it.","inputSchema":{"type":"object","properties":{"command":{"type":"string","description":"Exact command approved by the user"},"always":{"type":"boolean","default":False,"description":"Persist approval for this exact command string"}},"required":["command"]}},
        {"name":"agent_room_get","description":"Read shared Agent Room context, workspace state, recent events, sessions and skills. Only credential names are included; never return secret values.","inputSchema":{"type":"object","properties":{}}},
        {"name":"credentials_list","description":"List names of saved local credentials without exposing credential values.","inputSchema":{"type":"object","properties":{}}},
        {"name":"credential_save","description":"Save a user-provided API credential into the private local Termux vault. Never echo the value.","inputSchema":{"type":"object","properties":{"name":{"type":"string"},"value":{"type":"string"}},"required":["name","value"]}},
        {"name":"session_save","description":"Save or update a short local session summary for Agent Room continuity.","inputSchema":{"type":"object","properties":{"id":{"type":"string"},"title":{"type":"string"},"summary":{"type":"string"},"next_action":{"type":"string"}},"required":["id","title","summary"]}},
        {"name":"event_record","description":"Append a brief non-secret activity event to the local Agent Room journal.","inputSchema":{"type":"object","properties":{"kind":{"type":"string"},"summary":{"type":"string"}},"required":["kind","summary"]}},
        {"name":"state_update","description":"Update shared Agent Room task state using a partial JSON object.","inputSchema":{"type":"object","properties":{"fields":{"type":"object"}},"required":["fields"]}},
        {"name":"provider_models_list","description":"Query a supported provider's live model catalog with a user-supplied API key. This does not store or log the key.","inputSchema":{"type":"object","properties":{"provider":{"type":"string","enum":sorted(PROVIDERS)},"api_key":{"type":"string"}},"required":["provider","api_key"]}},
        {"name":"provider_models_saved_list","description":"Query a provider model catalog using the API key already saved in the local credential vault.","inputSchema":{"type":"object","properties":{"provider":{"type":"string","enum":sorted(PROVIDERS)}},"required":["provider"]}},
        {"name":"provider_model_test","description":"Send one short text-generation probe to a single provider model. The optional test key is used only for this request and is never saved. This probe has no local tools.","inputSchema":{"type":"object","properties":{"provider":{"type":"string","enum":sorted(PROVIDERS)},"model":{"type":"string"},"test_api_key":{"type":"string"}},"required":["provider","model"]}},
        {"name":"provider_chat","description":"Send a user chat turn to the selected configured AI provider. The server can call local MCP tools during the turn and returns the final answer.","inputSchema":{"type":"object","properties":{"provider":{"type":"string","enum":sorted(PROVIDERS)},"model":{"type":"string"},"messages":{"type":"array","items":{"type":"object","properties":{"role":{"type":"string","enum":["user","assistant","system"]},"content":{"type":"string"}},"required":["role","content"]}},"system":{"type":"string"},"timeout":{"type":"integer","minimum":10,"maximum":180}},"required":["provider","model","messages"]}},
        {"name":"opencode_manage","description":"Manage the optional local OpenCode API: install/start, check status, restart, or stop. This does not replace the selected Codein chat provider.","inputSchema":{"type":"object","properties":{"action":{"type":"string","enum":["install","run","start","status","restart","stop"]}},"required":["action"]}}
    ]
    return local_tools + composio_tool_catalog()

def dispatch_mcp_tool(name, args):
    args=args if isinstance(args,dict) else {}
    if name=="composio_platform_configure":
        result=composio_platform.configure(args.get("worker_url"))
        event("composio_platform_configured",{"configured":True,"reused":bool(result.get("reused"))})
        return {"ok":True,"reused":bool(result.get("reused")),"message":"Codein Composio Platform is ready for this Termux installation. The identity token was saved in the private local vault."}
    if name=="composio_configure":
        value=str(args.get("consumer_api_key","")).strip()
        if not value.startswith("ck_") or len(value)<12: raise ValueError("A valid Composio Connect consumer key (ck_...) is required.")
        secret_file=KEYS/"composio_consumer_api_key.json"
        secret_file.write_text(json.dumps({"name":"composio_consumer_api_key","value":value,"updated_at":now()},ensure_ascii=False),encoding="utf-8")
        secret_file.chmod(0o600)
        event("composio_configured",{"configured":True})
        return {"ok":True,"message":"Composio key saved locally. Call composio_tools_refresh to discover service tools."}
    if name=="composio_tools_refresh":
        tools=composio_tool_catalog()
        if not tools: raise RuntimeError("No Composio tools found. Configure the Codein Worker and connect a service first.")
        return {"ok":True,"count":len(tools),"tools":[{"name":t["name"],"description":t["description"][:180]} for t in tools]}
    if name.startswith("composio__"):
        platform_config=composio_platform.read_config()
        if platform_config.get("worker_url") and platform_config.get("token"):
            result=composio_platform.call_tool(name[len("composio__"):],args,platform_config)
            event("composio_tool_called",{"tool":name})
            return result
        key=str(readj(KEYS/"composio_consumer_api_key.json",{}).get("value","")).strip()
        if not key: raise RuntimeError("Composio is not configured.")
        result=composio_connect.call_tool(key,name[len("composio__"):],args)
        event("composio_tool_called",{"tool":name})
        return result
    if name=="termux_command_approve":
        command=str(args.get("command","")).strip()
        if not command: raise ValueError("Command is empty")
        if bool(args.get("always",False)):
            policy_path=PERMISSIONS/"always.json"
            policy=readj(policy_path,{"commands":[]})
            commands=set(str(item) for item in policy.get("commands",[]) if isinstance(item,str))
            commands.add(command)
            policy_path.write_text(json.dumps({"commands":sorted(commands),"updated_at":now()},ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
            policy_path.chmod(0o600)
            event("command_always_allowed",{"command":command[:500]})
            return {"ok":True,"approved_always":True}
        approval=PERMISSIONS/(hashlib.sha256(command.encode("utf-8")).hexdigest()+".json")
        approval.write_text(json.dumps({"command":command,"approved_at":now()},ensure_ascii=False),encoding="utf-8")
        approval.chmod(0o600)
        event("command_approval_granted",{"command":command[:500]})
        return {"ok":True,"approved_once":True}
    if name=="termux_exec":
        command=str(args.get("command","")).strip()
        timeout=max(1,min(int(args.get("timeout",120)),900))
        if not command: raise ValueError("Command is empty")
        policy=readj(PERMISSIONS/"always.json",{"commands":[]})
        always_allowed=command in policy.get("commands",[])
        approval=PERMISSIONS/(hashlib.sha256(command.encode("utf-8")).hexdigest()+".json")
        approved=readj(approval,{})
        if not always_allowed and approved.get("command") != command:
            event("command_permission_requested",{"command":command[:500]})
            return {"permission_required":True,"command":command}
        if not always_allowed:
            try: approval.unlink()
            except OSError: pass
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
    if name=="provider_models_saved_list":
        pid=str(args.get("provider","")).strip().lower()
        provider=PROVIDERS.get(pid)
        if not provider: raise ValueError("Unsupported provider")
        saved=readj(KEYS/(provider["key"]+".json"),{})
        api_key=str(saved.get("value","")).strip()
        if not api_key: raise RuntimeError("No saved API key for "+provider["name"]+". Save the key once in Integrations first.")
        status,payload=discover_provider_models(pid,api_key)
        if status!=200: raise RuntimeError(str(payload.get("error","Model lookup failed"))+(": "+str(payload.get("detail")) if payload.get("detail") else ""))
        return payload
    if name=="provider_model_test":
        pid=str(args.get("provider","")).strip().lower()
        model=str(args.get("model","")).strip()
        if pid not in PROVIDERS: raise ValueError("Unsupported provider")
        if not model or len(model)>300: raise ValueError("A valid model ID is required")
        marker="CODEIN_MODEL_READY"
        status,result=provider_chat({
            "provider":pid,
            "model":model,
            "messages":[{"role":"user","content":"Reply with exactly this text and nothing else: "+marker}],
            "test_api_key":args.get("test_api_key"),
            "model_probe":True,
            "timeout":18,
        })
        answer=str(result.get("content") or "")
        if status!=200 or not result.get("ok") or marker not in answer:
            raise RuntimeError("Model did not pass the live text-response test")
        return {"ok":True,"verified":True,"provider":pid,"model":model}
    if name=="provider_chat":
        status,result=provider_chat(args)
        return result
    if name=="opencode_manage":
        action=str(args.get("action","status"))
        if action not in ("install","run","start","status","restart","stop"):
            return {"ok":False,"error":"Invalid action"}
        manager=ROOT/"bin"/"codein-opencode"
        if not manager.is_file(): return {"ok":False,"error":"OpenCode manager is missing. Re-run the Codein Termux installer."}
        manager_action="start" if action in ("install","run") else action
        run=subprocess.run([str(manager),manager_action],capture_output=True,text=True,timeout=180)
        return {"ok":run.returncode==0,"action":action,"status":run.stdout.strip()[-2000:],"error":run.stderr.strip()[-2000:]}
    raise ValueError("Unknown MCP tool: "+str(name))

def read_openai_stream(response, emit, deadline):
    content=[]
    calls={}
    for raw_line in response:
        if time.monotonic()>deadline:
            raise TimeoutError("Provider chat exceeded its total time limit")
        line=raw_line.decode("utf-8",errors="replace").strip()
        if not line.startswith("data:"):
            continue
        payload=line[5:].strip()
        if payload=="[DONE]":
            break
        try: chunk=json.loads(payload)
        except ValueError: continue
        choices=chunk.get("choices") or []
        if not choices: continue
        delta=choices[0].get("delta") or {}
        text=delta.get("content")
        if isinstance(text,str) and text:
            content.append(text)
            emit({"type":"token","text":text})
        for item in delta.get("tool_calls") or []:
            if not isinstance(item,dict): continue
            index=item.get("index",0)
            call=calls.setdefault(index,{"id":"","type":"function","function":{"name":"","arguments":""}})
            if item.get("id"): call["id"]+=str(item["id"])
            function=item.get("function") or {}
            call["function"]["name"]+=str(function.get("name") or "")
            call["function"]["arguments"]+=str(function.get("arguments") or "")
    message={"role":"assistant","content":"".join(content)}
    if calls:
        message["tool_calls"]=[calls[key] for key in sorted(calls)]
    return message

def iter_sse_events(response, deadline):
    data=[]
    event_name=""
    for raw_line in response:
        if time.monotonic()>deadline:
            raise TimeoutError("Provider chat exceeded its total time limit")
        line=raw_line.decode("utf-8",errors="replace").rstrip("\r\n")
        if not line:
            if data:
                try: payload=json.loads("\n".join(data))
                except ValueError: payload=None
                if payload is not None: yield event_name,payload
            data=[]; event_name=""
        elif line.startswith(":"):
            continue
        elif line.startswith("event:"):
            event_name=line[6:].strip()
        elif line.startswith("data:"):
            data.append(line[5:].lstrip())
    if data:
        try: payload=json.loads("\n".join(data))
        except ValueError: payload=None
        if payload is not None: yield event_name,payload

def read_gemini_stream(response, emit, deadline):
    text=[]
    calls=[]
    call_indexes={}
    provider_parts=[]
    for _,chunk in iter_sse_events(response,deadline):
        if chunk.get("error"):
            raise RuntimeError("Gemini streaming request failed")
        candidates=chunk.get("candidates") or []
        if not candidates: continue
        content=(candidates[0].get("content") or {})
        occurrences={}
        for part in content.get("parts") or []:
            if not isinstance(part,dict): continue
            value=part.get("text")
            if isinstance(value,str) and value:
                text.append(value)
                emit({"type":"token","text":value})
                provider_parts.append(dict(part))
            call=part.get("functionCall")
            if not isinstance(call,dict): continue
            name=str(call.get("name") or "")
            occurrence=occurrences.get(name,0)
            occurrences[name]=occurrence+1
            index=call_indexes.get((name,occurrence))
            if index is None:
                index=len(calls)
                call_indexes[(name,occurrence)]=index
                stored_part={key:value for key,value in part.items() if key!="functionCall"}
                stored_call={key:value for key,value in call.items() if key!="args"}
                stored_call["args"]={}
                stored_part["functionCall"]=stored_call
                calls.append({"name":name,"args":{},"part":stored_part})
            args=call.get("args") or {}
            calls[index]["part"].update({key:value for key,value in part.items() if key!="functionCall"})
            calls[index]["part"]["functionCall"].update({key:value for key,value in call.items() if key!="args"})
            if isinstance(args,dict):
                calls[index]["args"].update(args)
                calls[index]["part"]["functionCall"]["args"].update(args)
    message={"role":"assistant","content":"".join(text)}
    if calls:
        message["provider_content"]={"role":"model","parts":provider_parts+[call["part"] for call in calls]}
        message["tool_calls"]=[{"id":"gemini-"+str(index),"type":"function","function":{"name":call["name"],"arguments":json.dumps(call["args"],ensure_ascii=False)}} for index,call in enumerate(calls)]
    return message

def read_anthropic_stream(response, emit, deadline):
    blocks={}
    for event_name,payload in iter_sse_events(response,deadline):
        kind=payload.get("type") or event_name
        if kind=="error":
            raise RuntimeError("Anthropic streaming request failed")
        index=payload.get("index")
        if kind=="content_block_start" and isinstance(index,int):
            block=payload.get("content_block") or {}
            blocks[index]=dict(block)
            if block.get("type")=="tool_use": blocks[index]["input_json"]=""
        elif kind=="content_block_delta" and isinstance(index,int):
            block=blocks.setdefault(index,{"type":"text","text":""})
            delta=payload.get("delta") or {}
            if delta.get("type")=="text_delta":
                value=str(delta.get("text") or "")
                block["text"]=str(block.get("text") or "")+value
                if value: emit({"type":"token","text":value})
            elif delta.get("type")=="input_json_delta":
                block["input_json"]+=str(delta.get("partial_json") or "")
    content=[]
    calls=[]
    for index in sorted(blocks):
        block=blocks[index]
        if block.get("type")=="tool_use":
            raw=block.pop("input_json","")
            try: block["input"]=json.loads(raw) if raw else block.get("input") or {}
            except ValueError: block["input"]={}
            calls.append(block)
        content.append(block)
    message={"role":"assistant","content":"".join(str(block.get("text","")) for block in content if block.get("type")=="text"),"provider_content":content}
    if calls:
        message["tool_calls"]=[{"id":call.get("id",""),"type":"function","function":{"name":call.get("name",""),"arguments":json.dumps(call.get("input") or {},ensure_ascii=False)}} for call in calls]
    return message

GEMINI_SCHEMA_FIELDS={
    "type","format","description","nullable","enum","items","maxItems","minItems",
    "properties","required","minProperties","maxProperties","minimum","maximum",
    "minLength","maxLength","pattern","anyOf","propertyOrdering"
}

def gemini_schema(schema):
    if not isinstance(schema,dict): return {"type":"object","properties":{}}
    result={key:value for key,value in schema.items() if key in GEMINI_SCHEMA_FIELDS and key not in ("items","properties","required","anyOf")}
    if isinstance(schema.get("items"),dict):
        item=gemini_schema(schema["items"])
        if item: result["items"]=item
    if isinstance(schema.get("properties"),dict):
        properties={}
        for name,value in schema["properties"].items():
            cleaned=gemini_schema(value)
            if cleaned: properties[name]=cleaned
        result["properties"]=properties
    if isinstance(schema.get("required"),list):
        properties=result.get("properties")
        required=[name for name in schema["required"] if isinstance(name,str) and (not isinstance(properties,dict) or name in properties)]
        if required: result["required"]=required
    if isinstance(schema.get("anyOf"),list):
        variants=[gemini_schema(item) for item in schema["anyOf"]]
        variants=[item for item in variants if item]
        if variants: result["anyOf"]=variants
    if "type" not in result:
        if "properties" in result: result["type"]="object"
        elif "items" in result: result["type"]="array"
    return result

def provider_chat(data, emit=None, cancel=None):
    pid=str(data.get("provider","")).strip().lower()
    provider=PROVIDERS.get(pid)
    if not provider: return 400,{"ok":False,"error":"Unknown provider","providers":list(PROVIDERS)}
    key_data=readj(KEYS/(provider["key"]+".json"),{})
    api_key=str(data.get("test_api_key") or key_data.get("value","")).strip()
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
    deadline=time.monotonic()+max(30,min(int(data.get("total_timeout",300)),300)) if emit else None
    model_probe=bool(data.get("model_probe"))
    # Probes must not expose action tools or let a model trigger local side effects.
    tool_catalog=[] if model_probe else [tool for tool in mcp_tools_catalog() if tool["name"] not in ("provider_chat","provider_models_list","provider_models_saved_list","provider_model_test","credential_save","termux_command_approve","opencode_manage","composio_platform_configure")]
    try:
        if cancel: cancel.check()
        if provider["kind"]=="gemini":
            contents=[]
            for item in clean:
                if item["role"]=="system": system += "\n"+item["content"]; continue
                contents.append({"role":"model" if item["role"]=="assistant" else "user","parts":[{"text":item["content"]}]})
            function_declarations=[{"name":tool["name"],"description":tool["description"],"parameters":gemini_schema(tool.get("inputSchema"))} for tool in tool_catalog]
            method=":streamGenerateContent?alt=sse&key=" if emit else ":generateContent?key="
            url=provider["base_url"]+"/models/"+quote(model,safe="")+method+quote(api_key,safe="")
            headers={"Content-Type":"application/json"}
            for attempt in range(9):
                if cancel: cancel.check()
                if emit and time.monotonic()>deadline: return 504,{"ok":False,"error":"Provider chat exceeded its total time limit"}
                if emit: emit({"type":"status","stage":"waiting_provider","provider":pid,"iteration":attempt+1})
                payload={"contents":contents,"generationConfig":{"temperature":0 if model_probe else 0.3,"maxOutputTokens":24} if model_probe else {"temperature":0.3},"tools":[{"functionDeclarations":function_declarations}]}
                if model_probe: payload.pop("tools",None)
                if system: payload["systemInstruction"]={"parts":[{"text":system}]}
                request=Request(url,data=json.dumps(payload).encode(),headers=headers,method="POST")
                request_timeout=min(timeout,max(1,int(deadline-time.monotonic()))) if emit else timeout
                with tracked_response(cancel,urlopen(request,timeout=request_timeout)) as response:
                    if emit:
                        emit({"type":"status","stage":"provider_response","provider":pid,"iteration":attempt+1})
                        message=read_gemini_stream(response,emit,deadline)
                        model_content=message.get("provider_content") or {"role":"model","parts":[{"text":message.get("content","")}]}
                        calls=[{"name":call["function"]["name"],"args":json.loads(call["function"]["arguments"])} for call in message.get("tool_calls",[])]
                    else:
                        raw=json.loads(response.read(3_000_000).decode())
                        candidates=raw.get("candidates") or []
                        candidate=candidates[0] if candidates else {}
                        model_content=candidate.get("content") or {}
                        parts=model_content.get("parts") or []
                        calls=[part.get("functionCall") for part in parts if isinstance(part,dict) and isinstance(part.get("functionCall"),dict)]
                if not calls:
                    answer=message.get("content","") if emit else "".join(str(part.get("text","")) for part in parts if isinstance(part,dict))
                    break
                contents.append(model_content)
                tool_responses=[]
                for call in calls[:12]:
                    if cancel: cancel.check()
                    tool_name=str(call.get("name",""))
                    arguments=call.get("args") or {}
                    if emit: emit({"type":"tool_call","name":tool_name})
                    try: tool_result=dispatch_mcp_tool(tool_name,arguments)
                    except Exception as exc: tool_result={"error":str(exc)[:1500]}
                    if isinstance(tool_result,dict) and tool_result.get("permission_required"):
                        if emit: emit({"type":"approval_required","command":str(tool_result.get("command",""))})
                        return 200,{"ok":True,"permission_required":True,"command":str(tool_result.get("command",""))}
                    if emit: emit({"type":"tool_result","name":tool_name,"summary":"Tool returned an error" if isinstance(tool_result,dict) and tool_result.get("error") else "Tool completed"})
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
            for attempt in range(9):
                if cancel: cancel.check()
                if emit and time.monotonic()>deadline: return 504,{"ok":False,"error":"Provider chat exceeded its total time limit"}
                if emit: emit({"type":"status","stage":"waiting_provider","provider":pid,"iteration":attempt+1})
                payload={"model":model,"max_tokens":24 if model_probe else 4096,"messages":msgs,"stream":bool(emit)}
                if not model_probe: payload["tools"]=tools
                if system_parts: payload["system"]="\n\n".join(system_parts)
                request=Request(url,data=json.dumps(payload).encode(),headers=headers,method="POST")
                request_timeout=min(timeout,max(1,int(deadline-time.monotonic()))) if emit else timeout
                with tracked_response(cancel,urlopen(request,timeout=request_timeout)) as response:
                    if emit:
                        emit({"type":"status","stage":"provider_response","provider":pid,"iteration":attempt+1})
                        message=read_anthropic_stream(response,emit,deadline)
                        blocks=message.get("provider_content") or []
                    else:
                        raw=json.loads(response.read(3_000_000).decode())
                        blocks=raw.get("content") or []
                tool_uses=[block for block in blocks if isinstance(block,dict) and block.get("type")=="tool_use"]
                if not tool_uses:
                    answer=message.get("content","") if emit else "".join(str(block.get("text","")) for block in blocks if isinstance(block,dict) and block.get("type")=="text")
                    break
                msgs.append({"role":"assistant","content":blocks})
                results=[]
                for call in tool_uses[:12]:
                    if cancel: cancel.check()
                    if emit: emit({"type":"tool_call","id":str(call.get("id","")),"name":str(call.get("name",""))})
                    try: result=dispatch_mcp_tool(str(call.get("name","")),call.get("input") or {})
                    except Exception as exc: result={"error":str(exc)[:1500]}
                    if isinstance(result,dict) and result.get("permission_required"):
                        if emit: emit({"type":"approval_required","command":str(result.get("command",""))})
                        return 200,{"ok":True,"permission_required":True,"command":str(result.get("command",""))}
                    if emit: emit({"type":"tool_result","id":str(call.get("id","")),"name":str(call.get("name","")),"summary":"Tool returned an error" if isinstance(result,dict) and result.get("error") else "Tool completed"})
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
            for attempt in range(9):
                if cancel: cancel.check()
                if emit and time.monotonic()>deadline:
                    return 504,{"ok":False,"error":"Provider chat exceeded its total time limit"}
                if emit: emit({"type":"status","stage":"waiting_provider","provider":pid,"iteration":attempt+1})
                payload={"model":model,"messages":msgs,"stream":bool(emit)}
                if model_probe:
                    payload["max_completion_tokens" if pid=="openai" else "max_tokens"]=24
                    if pid!="openai": payload["temperature"]=0
                else:
                    payload.update({"tools":tools,"tool_choice":"auto"})
                request=Request(url,data=json.dumps(payload).encode(),headers=headers,method="POST")
                request_timeout=min(timeout,max(1,int(deadline-time.monotonic()))) if emit else timeout
                with tracked_response(cancel,urlopen(request,timeout=request_timeout)) as response:
                    if emit:
                        emit({"type":"status","stage":"provider_response","provider":pid,"iteration":attempt+1})
                        message=read_openai_stream(response,emit,deadline)
                    else:
                        raw=json.loads(response.read(3_000_000).decode())
                        choices=raw.get("choices") or []
                        message=(choices[0].get("message") or {}) if choices else {}
                calls=message.get("tool_calls") or []
                if not calls:
                    answer=str(message.get("content") or "")
                    break
                msgs.append(message)
                for call in calls[:12]:
                    if cancel: cancel.check()
                    function=call.get("function") or {}
                    name=str(function.get("name",""))
                    if emit: emit({"type":"tool_call","id":str(call.get("id","")),"name":name})
                    try: arguments=json.loads(function.get("arguments") or "{}")
                    except ValueError: arguments={}
                    try: result=dispatch_mcp_tool(name,arguments)
                    except Exception as exc: result={"error":str(exc)[:1500]}
                    if isinstance(result,dict) and result.get("permission_required"):
                        if emit: emit({"type":"approval_required","command":str(result.get("command",""))})
                        return 200,{"ok":True,"permission_required":True,"command":str(result.get("command",""))}
                    if emit:
                        if isinstance(result,dict) and "exit_code" in result:
                            summary="Command finished with exit code "+str(result.get("exit_code"))
                        elif isinstance(result,dict) and result.get("error"):
                            summary="Tool returned an error"
                        else:
                            summary="Tool completed"
                        emit({"type":"tool_result","id":str(call.get("id","")),"name":name,"summary":summary})
                    msgs.append({"role":"tool","tool_call_id":call.get("id"),"content":json.dumps(result,ensure_ascii=False)})
            else: answer="The MCP tool loop reached its safety limit. Please continue with the available result."
        if not answer: return 502,{"ok":False,"error":"Provider returned an empty response","provider":pid,"model":model}
        event("provider_chat_completed",{"provider":pid,"model":model})
        if emit: emit({"type":"done","provider":pid,"model":model})
        return 200,{"ok":True,"provider":pid,"model":model,"content":answer}
    except RequestCancelled:
        return 499,{"ok":False,"error":"Request cancelled"}
    except HTTPError as exc:
        raw_detail=""
        try: raw_detail=exc.read(12000).decode(errors="replace")
        except Exception: pass
        detail=raw_detail
        try:
            parsed=json.loads(raw_detail)
            provider_error=parsed.get("error",{}) if isinstance(parsed,dict) else {}
            if isinstance(provider_error,dict): detail=str(provider_error.get("message") or provider_error.get("status") or raw_detail)
        except (TypeError,ValueError): pass
        event("provider_chat_error",{"provider":pid,"model":model,"status":exc.code})
        error="Provider is temporarily busy (HTTP "+str(exc.code)+")" if exc.code in (429,503) else "Provider HTTP "+str(exc.code)
        return 502,{"ok":False,"error":error,"provider":pid,"model":model,"detail":detail[:3000]}
    except (URLError,TimeoutError):
        if cancel and cancel.cancelled.is_set(): return 499,{"ok":False,"error":"Request cancelled"}
        return 502,{"ok":False,"error":"Provider connection failed or exceeded the time limit. Check network access and try again."}
    except Exception as exc:
        if cancel and cancel.cancelled.is_set(): return 499,{"ok":False,"error":"Request cancelled"}
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

    def send_mcp_sse(self, payload):
        frame=("event: message\ndata: "+json.dumps(payload,ensure_ascii=False,separators=(",",":"))+"\n\n").encode("utf-8")
        self.wfile.write(frame)
        self.wfile.flush()

    def handle_mcp_provider_chat(self, request_id, params):
        request_key=str(request_id)
        control=register_stream(request_key)
        self.send_response(200)
        self.send_header("Content-Type","text/event-stream; charset=utf-8")
        self.send_header("Cache-Control","no-cache, no-transform")
        self.send_header("X-Accel-Buffering","no")
        self.end_headers()

        def emit(payload):
            control.check()
            self.send_mcp_sse({
                "jsonrpc":"2.0",
                "method":"notifications/message",
                "params":{"level":"info","logger":"codein.provider_chat","data":payload}
            })

        try:
            arguments=params.get("arguments") or {}
            status,result=provider_chat(arguments,emit,control)
            if status==499:
                raise RequestCancelled()
            self.send_mcp_sse({
                "jsonrpc":"2.0",
                "id":request_id,
                "result":{"content":[{"type":"text","text":json.dumps(result,ensure_ascii=False)}],"isError":status!=200}
            })
        except RequestCancelled:
            try:
                self.send_mcp_sse({"jsonrpc":"2.0","id":request_id,"error":{"code":-32800,"message":"Request cancelled"}})
            except (BrokenPipeError,ConnectionResetError):
                pass
        except (BrokenPipeError,ConnectionResetError):
            pass
        except Exception as exc:
            try:
                self.send_mcp_sse({"jsonrpc":"2.0","id":request_id,"result":{"content":[{"type":"text","text":str(exc)[:1500]}],"isError":True}})
            except (BrokenPipeError,ConnectionResetError):
                pass
        finally:
            unregister_stream(request_key,control)

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
            if method=="notifications/cancelled":
                cancelled_id=params.get("requestId")
                if cancelled_id is not None: cancel_stream(str(cancelled_id))
            self.send_response(202); self.send_header("Content-Length","0"); self.end_headers()
            return
        if method=="initialize":
            return self.mcp_result(request_id,{
                "protocolVersion":"2025-03-26",
                "capabilities":{"logging":{},"tools":{"listChanged":False}},
                "serverInfo":{"name":"codein-termux","version":SERVER_VERSION},
                "instructions":"Local Codein Termux tools. Execute only user-authorized actions. Never expose local credential values."
            })
        if method=="ping": return self.mcp_result(request_id,{})
        if method=="tools/list":
            return self.mcp_result(request_id,{"tools":mcp_tools_catalog()})
        if method=="tools/call":
            name=str(params.get("name","")); args=params.get("arguments") or {}
            if name=="provider_chat" and "text/event-stream" in self.headers.get("Accept",""):
                return self.handle_mcp_provider_chat(request_id,params)
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
        if p=="/api/chat/cancel":
            request_id=str(data.get("request_id","")).strip()
            if not request_id or len(request_id)>128 or any(not (char.isalnum() or char in "-_") for char in request_id):
                return self.sendj(400,{"ok":False,"error":"Invalid request_id"})
            return self.sendj(200,{"ok":True,"cancelled":cancel_stream(request_id)})
        if p=="/api/chat/stream":
            request_id=str(data.get("request_id","")).strip() or uuid.uuid4().hex
            if len(request_id)>128 or any(not (char.isalnum() or char in "-_") for char in request_id):
                return self.sendj(400,{"ok":False,"error":"Invalid request_id"})
            control=register_stream(request_id)
            self.send_response(200)
            self.send_header("Content-Type","text/event-stream; charset=utf-8")
            self.send_header("Cache-Control","no-cache, no-transform")
            self.send_header("X-Accel-Buffering","no")
            self.send_header("Access-Control-Allow-Origin","*")
            self.end_headers()
            def emit_sse(payload):
                control.check()
                self.send_sse(payload)
            try:
                emit_sse({"type":"status","status":"starting"})
                status,result=provider_chat(data,emit_sse,control)
                if status!=200 and status!=499:
                    emit_sse({"type":"error","error":result.get("error","Chat failed"),"detail":result.get("detail",""),"credential_name":result.get("credential_name",""),"provider_name":result.get("provider_name","")})
            except RequestCancelled:
                return
            except (BrokenPipeError,ConnectionResetError):
                return
            except Exception as exc:
                try: emit_sse({"type":"error","error":"Streaming failed","detail":str(exc)[:500]})
                except (BrokenPipeError,ConnectionResetError): pass
                except RequestCancelled: pass
            finally:
                unregister_stream(request_id,control)
            return
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

    def send_sse(self, payload):
        frame=("data: "+json.dumps(payload,ensure_ascii=False,separators=(",",":"))+"\n\n").encode("utf-8")
        self.wfile.write(frame)
        self.wfile.flush()

if __name__ == "__main__":
    if not TOKEN: raise SystemExit("Missing token. Re-run the Codein bootstrap to initialize ~/.codein/config.json.")
    print(f"[Codein] Local MCP endpoint: http://127.0.0.1:{PORT}/mcp")
    print(f"[Codein] Workspace: {WORKSPACE}")
    print("[Codein] Ctrl+C to stop.")
    ThreadingHTTPServer(("127.0.0.1",PORT),Handler).serve_forever()
