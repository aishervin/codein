#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
REPO_BASE="https://raw.githubusercontent.com/aishervin/codein/feature/termux-agent-room/termux"
ROOT="$HOME/.codein"
command -v pkg >/dev/null 2>&1 || { echo "Run this inside Termux." >&2; exit 1; }
mkdir -p "$ROOT"/{workspace,agents,keys,sessions,events,skills,tools,logs}
chmod 700 "$ROOT" "$ROOT/keys"
echo "[Codein] Preparing Termux runtime..."
pkg update -y
pkg install -y python git openssh curl
curl -fsSL "$REPO_BASE/server.py" -o "$ROOT/server.py"
chmod 700 "$ROOT/server.py"
curl -fsSL "$REPO_BASE/skills/integrations.md" -o "$ROOT/skills/integrations.md"
chmod 600 "$ROOT/skills/integrations.md"
if [ ! -f "$ROOT/config.json" ]; then
  python - "$ROOT/config.json" <<'PY'
import json, secrets, sys
from pathlib import Path
p = Path(sys.argv[1])
p.write_text(json.dumps({"port":8765,"token":secrets.token_urlsafe(32),"workspace":str(Path.home()/".codein"/"workspace")}, indent=2)+"\n")
p.chmod(0o600)
PY
fi
cat > "$ROOT/ROOM.md" <<'ROOM'
# Codein Agent Room
Shared local context for every model and agent connected to Codein.
- workspace/: projects and working files
- agents/: agent profiles and responsibilities
- keys/: local API-key vault (never commit)
- sessions/: session summaries and resumable state
- events/: append-only activity journal
- skills/: reusable workflows
- tools/: local tool adapters
- logs/: runtime logs

## Protocol
1. Read ROOM.md and state.json before starting a task.
2. Read the latest relevant session/event summaries.
3. Record a short plan in events/ before meaningful changes.
4. After changes, update state.json and save a session summary.
5. Never print secret values into chat, logs, or workspace files.
6. If a required credential is missing, ask for that exact credential, store it in the local vault, then continue.
7. Check the shared vault before asking again.
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
echo "[Codein] Workspace ready: $ROOT"
echo "[Codein] Start bridge: python $ROOT/server.py"
echo "[Codein] Keep Termux open while Codein uses it."
