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

# Validate the full update before replacing any installed bridge files.
STAGE="$(mktemp -d "$ROOT/.update.XXXXXX")"
cleanup() { rm -rf "$STAGE"; }
trap cleanup EXIT
fetch() {
  local remote target staged
  remote="$1"
  target="$2"
  staged="$STAGE/$remote"
  mkdir -p "$(dirname "$staged")"
  curl --retry 3 --retry-delay 1 --fail --silent --show-error "$RAW/$remote" -o "$staged"
  printf '%s|%s\n' "$remote" "$target" >> "$STAGE/install.list"
}
fetch "termux/server.py" "$ROOT/server.py"
fetch "termux/composio_connect.py" "$ROOT/composio_connect.py"
fetch "termux/composio_platform.py" "$ROOT/composio_platform.py"
fetch "termux/with-key.py" "$ROOT/bin/codein-with-key"
fetch "termux/codein-bridge" "$ROOT/bin/codein-bridge"
fetch "termux/codein-opencode" "$ROOT/bin/codein-opencode"
fetch "termux/skills/integrations.md" "$ROOT/skills/integrations.md"
fetch "termux/skills/github-cloudflare.md" "$ROOT/skills/github-cloudflare.md"
fetch "termux/skills/telegram.md" "$ROOT/skills/telegram.md"
fetch "termux/skills/providers-ai-google.md" "$ROOT/skills/providers-ai-google.md"
fetch "termux/skills/development.md" "$ROOT/skills/development.md"
fetch "termux/skills/web-research.md" "$ROOT/skills/web-research.md"
fetch "docs/TOOL_DOCS_INDEX.md" "$ROOT/docs/TOOL_DOCS_INDEX.md"
fetch "docs/AGENT_SYSTEM_PROMPT.md" "$ROOT/docs/AGENT_SYSTEM_PROMPT.md"
fetch "termux/LIBRARIES.md" "$ROOT/docs/LIBRARIES.md"

python -m py_compile "$STAGE/termux/server.py" "$STAGE/termux/composio_connect.py" "$STAGE/termux/composio_platform.py" "$STAGE/termux/with-key.py"
bash -n "$STAGE/termux/codein-bridge" "$STAGE/termux/codein-opencode"
while IFS='|' read -r remote target; do
  [ -n "$remote" ] || continue
  mkdir -p "$(dirname "$target")"
  cp "$STAGE/$remote" "$target.new.$$"
  if [[ "$target" == "$ROOT/server.py" || "$target" == "$ROOT/bin/"* ]]; then
    chmod 700 "$target.new.$$"
  else
    chmod 600 "$target.new.$$"
  fi
  mv -f "$target.new.$$" "$target"
done < "$STAGE/install.list"

if [ ! -f "$ROOT/config.json" ]; then
  python - "$ROOT/config.json" <<'PY'
import json, secrets, sys
from pathlib import Path
p = Path(sys.argv[1])
p.write_text(json.dumps({"port":8765,"token":secrets.token_urlsafe(32),"workspace":str(Path.home()/".codein"/"workspace")}, indent=2)+"\n")
p.chmod(0o600)
PY
fi

# Keep local user data and the Android pairing token stable across upgrades.
if [ ! -f "$ROOT/ROOM.md" ]; then
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
fi
if [ ! -f "$ROOT/state.json" ]; then
  printf '%s\n' '{"schema":1,"workspace":"Codein Agent Room","active_agent":null,"active_project":null,"last_task":null,"last_updated":null,"recent_changes":[],"active_sessions":[]}' > "$ROOT/state.json"
fi
if [ ! -f "$ROOT/.gitignore" ]; then
  printf '%s\n' 'keys/' 'config.json' '*.secret' > "$ROOT/.gitignore"
fi

echo
echo "[Codein] Starting the local bridge..."
"$ROOT/bin/codein-bridge" restart
TOKEN="$(python - "$ROOT/config.json" <<'PY'
import json, sys
from pathlib import Path
print(json.loads(Path(sys.argv[1]).read_text())["token"])
PY
)"
python - "$ROOT/config.json" <<'PY'
import json, sys
from urllib.request import Request, urlopen
config = json.load(open(sys.argv[1], encoding="utf-8"))
request = Request(
    "http://127.0.0.1:%d/mcp" % int(config.get("port", 8765)),
    data=b'{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-03-26","capabilities":{},"clientInfo":{"name":"Codein installer","version":"1"}}}',
    headers={"Authorization":"Bearer " + config["token"], "Content-Type":"application/json", "Accept":"application/json"},
)
with urlopen(request, timeout=5) as response:
    result = json.load(response).get("result", {})
version = result.get("serverInfo", {}).get("version", "")
parts = [int(part) if part.isdigit() else 0 for part in version.split(".")]
if (parts + [0, 0])[:3] < [1, 2, 0]:
    raise SystemExit("[Codein] MCP bridge update verification failed; expected version 1.2.0 or newer.")
print("[Codein] MCP bridge verified: version " + version)
PY

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
