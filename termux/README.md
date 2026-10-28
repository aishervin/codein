# Codein + Termux MCP Agent Room

Codein uses a local **Model Context Protocol (MCP)** server inside Termux. The Android app is the MCP client. Provider chat, live model discovery, Agent Room reads, and local terminal execution use the MCP JSON-RPC endpoint at `http://127.0.0.1:8765/mcp`.

The bridge uses Python's standard library; no hosted server, paid MCP relay, or external daemon is required.

## Install / start

Run in Termux:

```bash
pkg install -y curl
curl -fsSL https://raw.githubusercontent.com/aishervin/codein/main/termux/codein-bootstrap.sh | bash
```

The bootstrap installs `python git openssh curl`, initializes `~/.codein`, creates a local bearer token only on first setup, and starts the MCP service in the background with a restart loop. Re-running the installer updates the bridge while preserving the paired app's token. **Do not separately run `python ~/.codein/server.py` after bootstrap**; it can collide with the already-running server on port 8765.

Copy the private token printed by the installer into Codein's Termux connection dialog. The service binds only to `127.0.0.1`; it is not exposed to the LAN. Keep Termux alive while Codein is working. If Android suspends the process, the bootstrap's supervisor restarts the Python server when it exits.

Logs: `~/.codein/logs/server.log`

## MCP protocol

The server supports MCP JSON-RPC `2025-03-26` over Streamable HTTP at the single `/mcp` endpoint:

- `initialize`, `notifications/initialized`, `ping`
- `tools/list`
- `tools/call`

Tools exposed by the server:

- `termux_exec`: run a shell command as the Termux user in the shared workspace
- `agent_room_get`: read workspace state, recent events, sessions, skills, and credential names (not values)
- `credentials_list`: list local credential names
- `provider_models_list`: query a provider's current model list using a user-supplied key; the key is not persisted by this tool
- `provider_chat`: send a model turn to the configured provider; the provider adapter exposes local MCP tools to models that support native tool calls

The local server validates a bearer token on every request. MCP tool execution is powerful: commands run with the Termux user's permissions, so only use this local endpoint for actions authorized by the user. Never commit `~/.codein` or reveal its token/key values.

## Shared Agent Room

`~/.codein/` is shared context for every model:
- `keys/`: named local API credentials
- `workspace/`: projects and command working directory
- `agents/`: agent profiles
- `sessions/`: resumable session summaries
- `events/journal.jsonl`: recent tool activity
- `skills/`: reusable integration and engineering procedures
- `docs/TOOL_DOCS_INDEX.md`: canonical official docs URLs
- `docs/AGENT_SYSTEM_PROMPT.md`: shared prompt and execution contract
- `docs/LIBRARIES.md`: baseline and optional dependencies
- `ROOM.md`: shared protocol
- `state.json`: active project/task and recent changes

## Keep knowledge current

The catalog is a list of canonical sources, not a claim that model weights are permanently updated. Before implementing or debugging an integration, agents should open current official docs and changelogs, record URLs and date checked in the shared journal, and label anything they could not verify. Do not copy whole docs into prompts or rely on remembered model IDs/endpoints.

## Compatibility endpoints

Older `/api/*` routes remain in the Python bridge for migration compatibility and diagnostic use. Current Codein Android MCP actions should use `/mcp` for MCP initialization, tool discovery/calls, provider model lookup, provider chat, Agent Room reads, and shell execution.
