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
fetch "termux/composio_connect.py" "$ROOT/composio_connect.py"
fetch "termux/composio_platform.py" "$ROOT/composio_platform.py"
fetch "termux/with-key.py" "$ROOT/bin/codein-with-key"
fetch "termux/codein-bridge" "$ROOT/bin/codein-bridge"
fetch "termux/codein-opencode" "$ROOT/bin/codein-opencode"
chmod 700 "$ROOT/server.py" "$ROOT/bin/codein-with-key" "$ROOT/bin/codein-bridge" "$ROOT/bin/codein-opencode"
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
echo "[Codein] Starting the local bridge..."
"$ROOT/bin/codein-bridge" restart
TOKEN="$(python - "$ROOT/config.json" <<'PY'
import json, sys
from pathlib import Path
print(json.loads(Path(sys.argv[1]).read_text())["token"])
PY
)"
echo
echo "[Codein] Workspace and documentation skills ready: $ROOT"
echo "[Codein] Bridge controls: $ROOT/bin/codein-bridge {start|stop|restart|status|logs}"
echo "[Codein] Bridge log: $ROOT/logs/server.log"
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
