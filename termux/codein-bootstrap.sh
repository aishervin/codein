#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
BRANCH="${CODEIN_BRANCH:-main}"
RAW="https://raw.githubusercontent.com/aishervin/codein/$BRANCH"
ROOT="$HOME/.codein"

# Keep logs clean while making interactive Termux installs easier to follow.
if [[ -t 1 && -z "${NO_COLOR:-}" ]]; then
  C_RESET=$'\033[0m'
  C_BOLD=$'\033[1m'
  C_CYAN=$'\033[36m'
  C_BLUE=$'\033[34m'
  C_GREEN=$'\033[32m'
  C_YELLOW=$'\033[33m'
  C_RED=$'\033[31m'
  C_WHITE=$'\033[97m'
else
  C_RESET=''
  C_BOLD=''
  C_CYAN=''
  C_BLUE=''
  C_GREEN=''
  C_YELLOW=''
  C_RED=''
  C_WHITE=''
fi

codein_banner() {
  printf '\n%b' "$C_CYAN$C_BOLD"
  printf '  +--------------------------------------------------+\n'
  printf '%b' "$C_WHITE$C_BOLD"
  printf '  |                CODEIN / TERMUX                  |\n'
  printf '%b' "$C_BLUE"
  printf '  |        Local Agent Room and MCP Bridge          |\n'
  printf '%b' "$C_CYAN$C_BOLD"
  printf '  +--------------------------------------------------+\n'
  printf '%b\n\n' "$C_RESET"
}

codein_step() {
  printf '\n%b[STEP]%b %s\n' "$C_BLUE$C_BOLD" "$C_RESET" "$*"
}

codein_info() {
  printf '%b[INFO]%b %s\n' "$C_CYAN" "$C_RESET" "$*"
}

codein_success() {
  printf '%b[ OK ]%b %s\n' "$C_GREEN$C_BOLD" "$C_RESET" "$*"
}

codein_warn() {
  printf '%b[WARN]%b %s\n' "$C_YELLOW$C_BOLD" "$C_RESET" "$*"
}

codein_error() {
  printf '%b[FAIL]%b %s\n' "$C_RED$C_BOLD" "$C_RESET" "$*" >&2
}

on_error() {
  local status=$?
  trap - ERR
  codein_error "Setup stopped with exit code $status. Review the output above."
  exit "$status"
}
trap on_error ERR

codein_banner
codein_step "1/5  Preparing the Termux runtime"
command -v pkg >/dev/null 2>&1 || { codein_error "Run this inside Termux."; exit 1; }
mkdir -p "$ROOT"/{workspace,agents,keys,sessions,events,skills,tools,logs,bin,docs}
chmod 700 "$ROOT" "$ROOT/keys"
codein_info "Updating Termux packages..."
pkg update -y
pkg install -y python git openssh curl
codein_success "Required packages are ready."

# Validate the full update before replacing any installed bridge files.
codein_step "2/5  Downloading bridge files"
STAGE="$(mktemp -d "$ROOT/.update.XXXXXX")"
cleanup() { rm -rf "$STAGE"; }
trap cleanup EXIT
CODEIN_FETCHED=0
CODEIN_FETCH_TOTAL=17
fetch() {
  local remote target staged
  remote="$1"
  target="$2"
  staged="$STAGE/$remote"
  CODEIN_FETCHED=$((CODEIN_FETCHED + 1))
  printf '\n%b[DOWNLOAD %02d/%02d]%b %s\n' "$C_CYAN$C_BOLD" "$CODEIN_FETCHED" "$CODEIN_FETCH_TOTAL" "$C_RESET" "$remote"
  mkdir -p "$(dirname "$staged")"
  curl --retry 3 --retry-delay 1 --fail --silent --show-error "$RAW/$remote" -o "$staged"
  printf '%s|%s\n' "$remote" "$target" >> "$STAGE/install.list"
  codein_success "Staged $remote"
}
fetch "termux/server.py" "$ROOT/server.py"
fetch "termux/composio_connect.py" "$ROOT/composio_connect.py"
fetch "termux/composio_platform.py" "$ROOT/composio_platform.py"
fetch "termux/with-key.py" "$ROOT/bin/codein-with-key"
fetch "termux/codein-bridge" "$ROOT/bin/codein-bridge"
fetch "termux/codein-opencode" "$ROOT/bin/codein-opencode"
fetch "termux/codein-links" "$ROOT/bin/codein-links"
fetch "termux/skills/integrations.md" "$ROOT/skills/integrations.md"
fetch "termux/skills/github-cloudflare.md" "$ROOT/skills/github-cloudflare.md"
fetch "termux/skills/telegram.md" "$ROOT/skills/telegram.md"
fetch "termux/skills/providers-ai-google.md" "$ROOT/skills/providers-ai-google.md"
fetch "termux/skills/development.md" "$ROOT/skills/development.md"
fetch "termux/skills/web-research.md" "$ROOT/skills/web-research.md"
fetch "termux/skills/codein-capabilities.md" "$ROOT/skills/codein-capabilities.md"
fetch "docs/TOOL_DOCS_INDEX.md" "$ROOT/docs/TOOL_DOCS_INDEX.md"
fetch "docs/AGENT_SYSTEM_PROMPT.md" "$ROOT/docs/AGENT_SYSTEM_PROMPT.md"
fetch "termux/LIBRARIES.md" "$ROOT/docs/LIBRARIES.md"

codein_step "3/5  Validating staged files"
python -m py_compile "$STAGE/termux/server.py" "$STAGE/termux/composio_connect.py" "$STAGE/termux/composio_platform.py" "$STAGE/termux/with-key.py"
bash -n "$STAGE/termux/codein-bridge" "$STAGE/termux/codein-opencode" "$STAGE/termux/codein-links"
codein_success "Python and shell syntax checks passed."

codein_step "4/5  Installing the bridge and preparing Agent Room"
CODEIN_INSTALLED=0
while IFS='|' read -r remote target; do
  [ -n "$remote" ] || continue
  CODEIN_INSTALLED=$((CODEIN_INSTALLED + 1))
  mkdir -p "$(dirname "$target")"
  cp "$STAGE/$remote" "$target.new.$$"
  if [[ "$target" == "$ROOT/server.py" || "$target" == "$ROOT/bin/"* ]]; then
    chmod 700 "$target.new.$$"
  else
    chmod 600 "$target.new.$$"
  fi
  mv -f "$target.new.$$" "$target"
done < "$STAGE/install.list"
codein_success "Installed $CODEIN_INSTALLED validated bridge files."

if [ ! -f "$ROOT/config.json" ]; then
  codein_info "Creating a private local pairing configuration..."
  python - "$ROOT/config.json" <<'PY'
import json, secrets, sys
from pathlib import Path
p = Path(sys.argv[1])
p.write_text(json.dumps({"port":8765,"token":secrets.token_urlsafe(32),"workspace":str(Path.home()/".codein"/"workspace")}, indent=2)+"\n")
p.chmod(0o600)
PY
  codein_success "Created the local pairing configuration."
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
  codein_success "Created the Agent Room guide."
fi
if ! grep -Fq "## Agent capability check" "$ROOT/ROOM.md"; then
cat >> "$ROOT/ROOM.md" <<'ROOM'

## Agent capability check
Before acting, read `docs/AGENT_SYSTEM_PROMPT.md` and `skills/codein-capabilities.md`, then confirm the live MCP tools and connection state. Only claim local shell, workspace, credential, network, or browser-opening abilities when the corresponding tool/command is available. No root access is provided; opening a URL does not grant access to inspect the user's browser page.
ROOM
fi
if [ ! -f "$ROOT/state.json" ]; then
  printf '%s\n' '{"schema":1,"workspace":"Codein Agent Room","active_agent":null,"active_project":null,"last_task":null,"last_updated":null,"recent_changes":[],"active_sessions":[]}' > "$ROOT/state.json"
fi
if [ ! -f "$ROOT/.gitignore" ]; then
  printf '%s\n' 'keys/' 'config.json' '*.secret' > "$ROOT/.gitignore"
fi

codein_step "5/5  Starting and verifying local services"
codein_info "Restarting the local bridge..."
"$ROOT/bin/codein-bridge" restart
TOKEN="$(python - "$ROOT/config.json" <<'PY'
import json, sys
from pathlib import Path
print(json.loads(Path(sys.argv[1]).read_text())["token"])
PY
)"
MCP_VERSION="$(python - "$ROOT/config.json" <<'PY'
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
if (parts + [0, 0])[:3] < [1, 5, 1]:
    raise SystemExit("[Codein] MCP bridge update verification failed; expected version 1.5.1 or newer.")
print(version)
PY
)"
codein_success "MCP bridge verified: version $MCP_VERSION"
codein_info "Workspace and documentation skills are ready at $ROOT"
codein_info "Bridge controls: $ROOT/bin/codein-bridge {start|stop|restart|status|logs}"
codein_info "Bridge log: $ROOT/logs/server.log"
codein_info "Keep Termux open while Codein uses it."

codein_step "Opening parallel connections"
if "$ROOT/bin/codein-links" start; then
  codein_success "Parallel links are up; the watchdog keeps them alive."
else
  codein_warn "Some links are not ready yet. The watchdog retries every 10 seconds."
  codein_info "Check their status with: $ROOT/bin/codein-links status"
fi

printf '\n%b' "$C_YELLOW$C_BOLD"
printf '  +--------------------------------------------------+\n'
printf '  |           TERMUX CONNECTION TOKEN                |\n'
printf '  |  Copy this token into Codein > Termux settings   |\n'
printf '  +--------------------------------------------------+\n'
printf '%b' "$C_RESET"
python - "$ROOT/config.json" <<'PY'
import json, sys
from pathlib import Path
config = json.loads(Path(sys.argv[1]).read_text())
print(config["token"])
PY
codein_warn "This token is private. Do not share it or post a screenshot."
codein_info "Bridge log: $ROOT/logs/server.log"
