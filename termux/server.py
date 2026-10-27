#!/usr/bin/env python3
"""Codein local Termux bridge. Python standard library only."""
import json, os, subprocess, time
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import unquote, urlparse

ROOT=Path.home()/".codein"
CONFIG=json.loads((ROOT/"config.json").read_text()) if (ROOT/"config.json").exists() else {}
TOKEN=str(CONFIG.get("token",""))
PORT=int(CONFIG.get("port",8765))
WORKSPACE=Path(CONFIG.get("workspace",str(ROOT/"workspace"))).expanduser()
KEYS=ROOT/"keys"; EVENTS=ROOT/"events"; SESSIONS=ROOT/"sessions"
for d in (WORKSPACE,KEYS,EVENTS,SESSIONS,ROOT/"agents",ROOT/"skills",ROOT/"tools",ROOT/"logs"): d.mkdir(parents=True,exist_ok=True)

def now(): return datetime.now(timezone.utc).isoformat()
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
            lines=journal.read_text(encoding="utf-8").splitlines()[-50:] if journal.exists() else []
            sessions=[readj(f,{}) for f in sorted(SESSIONS.glob("*.json"),key=lambda x:x.stat().st_mtime,reverse=True)[:10]]
            skills={f.stem:f.read_text(encoding="utf-8")[:20000] for f in (ROOT/"skills").glob("*.md")}
            return self.sendj(200,{"ok":True,"root":str(ROOT),"workspace":str(WORKSPACE),"room":room_file.read_text(encoding="utf-8") if room_file.exists() else "","state":readj(ROOT/"state.json",{}),"credentials":sorted(x.stem for x in KEYS.glob("*.json") if x.is_file()),"events":[json.loads(x) for x in lines if x.strip()],"sessions":sessions,"skills":skills})
        if p=="/api/keys": return self.sendj(200,{"ok":True,"keys":sorted(x.stem for x in KEYS.glob("*.json") if x.is_file())})
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
