# Codein + Termux Agent Room

First local-first bridge; Python standard library only. No paid server, Composio account, or hosted backend is required.

## Install in Termux

```bash
pkg install -y curl
curl -fsSL https://raw.githubusercontent.com/aishervin/codein/feature/termux-agent-room/termux/codein-bootstrap.sh | bash
python ~/.codein/server.py
```

The bridge binds to `127.0.0.1:8765`. Its bearer token is generated once and stored in `~/.codein/config.json`; keep Termux open while the app uses it.

## Shared Agent Room

`~/.codein/` is shared context for every model:
- `keys/`: named local API tokens
- `workspace/`: projects and command working directory
- `agents/`: agent profiles
- `sessions/`: resumable session summaries
- `events/journal.jsonl`: recent tool activity
- `skills/`: reusable procedures
- `tools/`: adapters
- `ROOM.md`: agent protocol
- `state.json`: active project/task and recent changes

Before asking for a token, check the vault names. Save a supplied credential with `POST /api/keys`; retrieve a named one with `GET /api/keys/<name>`. Never log raw secret values.

## API

All requests require `Authorization: Bearer <token>`; POST requests use JSON.

- `GET /health`
- `GET /api/workspace`
- `GET /api/keys` (names only)
- `GET /api/keys/<name>` (returns that secret to an authorized client)
- `POST /api/keys`: `{"name":"cloudflare_api_token","value":"..."}`
- `GET /api/events`, `POST /api/events`: `{"kind":"task_note","data":{"summary":"..."}}`
- `GET /api/sessions`, `POST /api/sessions`: `{"id":"session-1","title":"...","summary":"..."}`
- `POST /api/state`: partial state JSON
- `POST /api/exec`: `{"command":"git status --short","timeout":120}`

The exec endpoint runs commands as the Termux user, in the shared workspace. It does not grant root or bypass Android storage permission prompts.

## Integration status

This is the Termux-side foundation. The Android app still needs its UI/runtime wired to connect to this bridge, save the bearer token, and display terminal output. This commit does not claim the current APK already calls these endpoints.
