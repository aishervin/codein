#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
BRANCH="${CODEIN_BRANCH:-main}"
RAW="https://raw.githubusercontent.com/aishervin/codein/$BRANCH"
ROOT="$HOME/.codein"
command -v pkg >/dev/null 2>&1 || { echo "Run this inside Termux." >&2; exit 1; }
mkdir -p "$ROOT"/{workspace,agents,keys,sessions,events,skills,tools,logs,bin,docs}
chmod 700 "$ROOT" "$ROOT/keys"
echo "[Codein] Preparing Termux runtime..."
pkg update -y
pkg install -y python git openssh curl
fetch() {
  local remote="$1" target="$2"
  curl --retry 3 --retry-delay 1 --fail --silent --show-error "$RAW/$remote" -o "$target"
}
fetch "termux/server.py" "$ROOT/server.py"
fetch "termux/with-key.py" "$ROOT/bin/codein-with-key"
fetch "termux/codein-opencode" "$ROOT/bin/codein-opencode"
chmod 700 "$ROOT/server.py" "$ROOT/bin/codein-with-key" "$ROOT/bin/codein-opencode"
fetch "termux/skills/integrations.md" "$ROOT/skills/integrations.md"
fetch "termux/skills/github-cloudflare.md" "$ROOT/skills/github-cloudflare.md"
fetch "termux/skills/telegram.md" "$ROOT/skills/telegram.md"
fetch "termux/skills/providers-ai-google.md" "$ROOT/skills/providers-ai-google.md"
fetch "termux/skills/development.md" "$ROOT/skills/development.md"
fetch "termux/skills/web-research.md" "$ROOT/skills/web-research.md"
fetch "docs/TOOL_DOCS_INDEX.md" "$ROOT/docs/TOOL_DOCS_INDEX.md"
fetch "docs/AGENT_SYSTEM_PROMPT.md" "$ROOT/docs/AGENT_SYSTEM_PROMPT.md"
fetch "termux/LIBRARIES.md" "$ROOT/docs/LIBRARIES.md"
chmod 600 "$ROOT/skills/"*.md "$ROOT/docs/"*.md
if [ ! -f "$ROOT/config.json" ]; then
  python - "$ROOT/config.json" <<'PY'
import json, secrets, sys
from pathlib import Path
p = Path(sys.argv[1])
p.write_text(json.dumps({"port":8765,"token":secrets.token_urlsafe(32),"workspace":str(Path.home()/".codein"/"workspace")}, indent=2)+"\n")
p.chmod(0o600)
PY
fi
# Keep the existing local token stable across upgrades so the paired Android
# app does not lose its connection every time the bridge code is refreshed.
cat > "$ROOT/ROOM.md" <<'ROOM'
# Codein Agent Room
Shared local context for every model and agent connected to Codein.
- workspace/: projects and working files
- agents/: agent profiles and responsibilities
- keys/: local API-key vault (never commit)
- sessions/: session summaries and resumable state
- events/: append-only activity journal
- skills/: reusable service and engineering workflows
- tools/: local tool adapters
- docs/: canonical documentation index, shared prompt, and dependency policy
- logs/: runtime logs

## Protocol
1. Read ROOM.md and state.json before starting a task.
2. Read the latest relevant session/event summaries.
3. Read docs/AGENT_SYSTEM_PROMPT.md and the relevant skill under skills/.
4. For API/library/model changes, verify live official docs using docs/TOOL_DOCS_INDEX.md; a URL index alone does not mean docs were checked.
5. Record a short plan and actual source URLs/date checked in events/ before meaningful changes.
6. After changes, update state.json and save a session summary.
7. Never print secret values into chat, logs, or workspace files.
8. If a required credential is missing, ask for that exact credential, store it in the local vault, then continue.
9. Check the shared vault before asking again.
ROOM
if [ ! -f "$ROOT/state.json" ]; then
cat > "$ROOT/state.json" <<'STATE'
{"schema":1,"workspace":"Codein Agent Room","active_agent":null,"active_project":null,"last_task":null,"last_updated":null,"recent_changes":[],"active_sessions":[]}
STATE
fi
cat > "$ROOT/.gitignore" <<'IGNORE'
keys/
config.json
*.secret
IGNORE
echo
echo "[Codein] Starting the local bridge with automatic restart..."
# Keep the Termux CPU awake while the bridge is needed; Android may otherwise
# suspend the local server during a slow provider model lookup.
if command -v termux-wake-lock >/dev/null 2>&1; then
  termux-wake-lock >/dev/null 2>&1 || true
fi
if [ -f "$ROOT/server-child.pid" ]; then
  OLD_CHILD_PID="$(cat "$ROOT/server-child.pid" 2>/dev/null || true)"
  if [ -n "$OLD_CHILD_PID" ] && kill -0 "$OLD_CHILD_PID" 2>/dev/null; then
    kill "$OLD_CHILD_PID" 2>/dev/null || true
  fi
fi
if [ -f "$ROOT/server.pid" ]; then
  OLD_PID="$(cat "$ROOT/server.pid" 2>/dev/null || true)"
  if [ -n "$OLD_PID" ] && kill -0 "$OLD_PID" 2>/dev/null; then
    kill "$OLD_PID" 2>/dev/null || true
    sleep 1
  fi
fi
nohup bash -c 'while true; do
  python "$HOME/.codein/server.py" &
  CHILD_PID=$!
  echo "$CHILD_PID" > "$HOME/.codein/server-child.pid"
  wait "$CHILD_PID"
  RC=$?
  rm -f "$HOME/.codein/server-child.pid"
  echo "[Codein] Bridge exited with code $RC; restarting in 2 seconds."
  sleep 2
done' >> "$ROOT/logs/server.log" 2>&1 &
SERVER_PID=$!
echo "$SERVER_PID" > "$ROOT/server.pid"
TOKEN="$(python - "$ROOT/config.json" <<'PY'
import json, sys
from pathlib import Path
print(json.loads(Path(sys.argv[1]).read_text())["token"])
PY
)"
BRIDGE_READY=0
for attempt in $(seq 1 20); do
  if curl --silent --fail --max-time 1 -H "Authorization: Bearer $TOKEN" "http://127.0.0.1:8765/health" >/dev/null 2>&1; then
    BRIDGE_READY=1
    break
  fi
  sleep 0.5
done
if [ "$BRIDGE_READY" -eq 1 ]; then
  echo "[Codein] Bridge is running at http://127.0.0.1:8765 (PID $SERVER_PID)"
else
  echo "[Codein] Bridge did not start. Recent log:"
  tail -n 40 "$ROOT/logs/server.log" || true
  echo "[Codein] Inspect: $ROOT/logs/server.log"
fi

echo
echo "[Codein] Workspace and documentation skills ready: $ROOT"
echo "[Codein] Bridge startup is automatic; log: $ROOT/logs/server.log"
echo "[Codein] Keep Termux open while Codein uses it."
echo
echo "[Codein] Preparing OpenCode agent API (no browser UI)..."
if "$ROOT/bin/codein-opencode" start; then
  echo "[Codein] OpenCode runtime ready on http://127.0.0.1:4096"
else
  echo "[Codein] OpenCode runtime could not start. The existing Codein bridge remains available."
  echo "[Codein] Check: $ROOT/logs/opencode.log"
fi

echo
echo "============================================================"
echo "[Codein] TERMUX CONNECTION TOKEN"
echo "Copy this token into Codein app > Termux connection:"
python - "$ROOT/config.json" <<'PY'
import json, sys
from pathlib import Path
config = json.loads(Path(sys.argv[1]).read_text())
print(config["token"])
PY
echo "============================================================"
echo "This token is private. Do not share it or post a screenshot."
echo
echo "[Codein] Bridge log: $ROOT/logs/server.log"
