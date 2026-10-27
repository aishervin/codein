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
    "gemini": {"name":"Google Gemini","key":"gemini_api_key","base_url":"https://generativelanguage.googleapis.com/v1beta","default_model":"gemini-2.5-flash","kind":"gemini","models":["gemini-2.5-flash","gemini-2.5-pro"]},
    "openai": {"name":"OpenAI","key":"openai_api_key","base_url":"https://api.openai.com/v1","default_model":"gpt-4o-mini","kind":"openai","models":["gpt-4o-mini","gpt-4.1-mini"]},
    "deepseek": {"name":"DeepSeek","key":"deepseek_api_key","base_url":"https://api.deepseek.com","default_model":"deepseek-chat","kind":"openai","models":["deepseek-chat","deepseek-reasoner"]},
    "anthropic": {"name":"Anthropic Claude","key":"anthropic_api_key","base_url":"https://api.anthropic.com/v1","default_model":"claude-3-5-haiku-latest","kind":"anthropic","models":["claude-3-5-haiku-latest"]},
    "openrouter": {"name":"OpenRouter","key":"openrouter_api_key","base_url":"https://openrouter.ai/api/v1","default_model":"openai/gpt-4o-mini","kind":"openai","models":["openai/gpt-4o-mini","deepseek/deepseek-chat"]},
    "mistral": {"name":"Mistral","key":"mistral_api_key","base_url":"https://api.mistral.ai/v1","default_model":"mistral-small-latest","kind":"openai","models":["mistral-small-latest"]},
    "xai": {"name":"xAI Grok","key":"xai_api_key","base_url":"https://api.x.ai/v1","default_model":"grok-2-latest","kind":"openai","models":["grok-2-latest"]},
    "groq": {"name":"Groq","key":"groq_api_key","base_url":"https://api.groq.com/openai/v1","default_model":"llama-3.3-70b-versatile","kind":"openai","models":["llama-3.3-70b-versatile"]},
}

def provider_catalog():
    result=[]
    for pid, item in PROVIDERS.items():
        result.append({"id":pid,"name":item["name"],"base_url":item["base_url"],"default_model":item["default_model"],"models":item["models"],"configured":(KEYS/(item["key"]+".json")).is_file(),"credential_name":item["key"]})
    return result

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
    try:
        if provider["kind"]=="gemini":
            contents=[]
            for m in clean:
                if m["role"]=="system": system += "\n"+m["content"]; continue
                contents.append({"role":"model" if m["role"]=="assistant" else "user","parts":[{"text":m["content"]}]})
            payload={"contents":contents,"generationConfig":{"temperature":0.3}}
            if system: payload["systemInstruction"]={"parts":[{"text":system}]}
            url=provider["base_url"]+"/models/"+quote(model,safe="")+":generateContent?key="+quote(api_key,safe="")
            headers={"Content-Type":"application/json"}
        elif provider["kind"]=="anthropic":
            system_parts=[system] if system else []
            for m in clean:
                if m["role"]=="system": system_parts.append(m["content"])
            msgs=[{"role":"assistant" if m["role"]=="assistant" else "user","content":m["content"]} for m in clean if m["role"]!="system"]
            payload={"model":model,"max_tokens":4096,"messages":msgs}
            if system_parts: payload["system"]="\n\n".join(system_parts)
            url=provider["base_url"]+"/messages"
            headers={"Content-Type":"application/json","x-api-key":api_key,"anthropic-version":"2023-06-01"}
        else:
            msgs=[]
            if system: msgs.append({"role":"system","content":system})
            msgs.extend(m for m in clean if m["role"]!="system")
            payload={"model":model,"messages":msgs,"stream":False}
            url=provider["base_url"].rstrip("/")+"/chat/completions"
            headers={"Content-Type":"application/json","Authorization":"Bearer "+api_key}
        request=Request(url,data=json.dumps(payload).encode(),headers=headers,method="POST")
        with urlopen(request,timeout=timeout) as response:
            raw=json.loads(response.read(3_000_000).decode())
        if provider["kind"]=="gemini":
            candidates=raw.get("candidates") or []
            parts=((candidates[0].get("content") or {}).get("parts") or []) if candidates else []
            answer="".join(str(part.get("text","")) for part in parts)
        elif provider["kind"]=="anthropic":
            answer="".join(str(part.get("text","")) for part in raw.get("content",[]) if part.get("type")=="text")
        else:
            choices=raw.get("choices") or []
            answer=str(((choices[0].get("message") or {}).get("content","")) if choices else "")
        if not answer: return 502,{"ok":False,"error":"Provider returned an empty response","provider":pid,"model":model}
        event("provider_chat_completed",{"provider":pid,"model":model})
        return 200,{"ok":True,"provider":pid,"model":model,"content":answer}
    except HTTPError as e:
        detail=""
        try: detail=e.read(12000).decode(errors="replace")
        except Exception: pass
        event("provider_chat_error",{"provider":pid,"model":model,"status":e.code})
        return 502,{"ok":False,"error":"Provider HTTP "+str(e.code),"provider":pid,"model":model,"detail":detail[:3000]}
    except (URLError,TimeoutError) as e:
        return 502,{"ok":False,"error":"Provider connection failed","detail":str(e)[:1000]}
    except Exception as e:
        return 502,{"ok":False,"error":"Provider request failed","detail":str(e)[:1000]}


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
    def do_POST(self):
        if not self.auth(): return self.sendj(401,{"ok":False,"error":"Authorization required"})
        p=urlparse(self.path).path
        try: data=self.body()
        except Exception as e: return self.sendj(400,{"ok":False,"error":"Invalid JSON","detail":str(e)})
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

if not TOKEN: raise SystemExit("Missing token. Re-run the Codein bootstrap to initialize ~/.codein/config.json.")
print(f"[Codein] Local bridge: http://127.0.0.1:{PORT}")
print(f"[Codein] Workspace: {WORKSPACE}")
print("[Codein] Ctrl+C to stop.")
ThreadingHTTPServer(("127.0.0.1",PORT),Handler).serve_forever()
