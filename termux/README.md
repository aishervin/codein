# Codein + Termux Agent Room

Local-first bridge and shared context for all model providers. The initial bridge uses Python standard library only; no paid server, Composio account, or hosted backend is required.

## Install in Termux

```bash
pkg install -y curl
curl -fsSL https://raw.githubusercontent.com/aishervin/codein/main/termux/codein-bootstrap.sh | bash
python ~/.codein/server.py
```

Bootstrap installs the small runtime base (`python git openssh curl`) and downloads shared prompts, service skills, dependency policy, and canonical documentation index. Optional libraries are documented but are not installed unless a task needs them.

The bridge binds to `127.0.0.1:8765`. Its bearer token is generated once and stored in `~/.codein/config.json`; keep Termux open while the app uses it.

## Shared Agent Room

`~/.codein/` is shared context for every model:
- `keys/`: named local API tokens
- `workspace/`: projects and command working directory
- `agents/`: agent profiles
- `sessions/`: resumable session summaries
- `events/journal.jsonl`: recent tool activity
- `skills/`: reusable integration and engineering procedures
- `docs/TOOL_DOCS_INDEX.md`: official docs URLs for GitHub, Cloudflare, Telegram, Google, Android, AI providers, MCP, and core tools
- `docs/AGENT_SYSTEM_PROMPT.md`: shared system prompt and execution contract
- `docs/LIBRARIES.md`: baseline and optional dependencies
- `ROOM.md`: shared protocol
- `state.json`: active project/task and recent changes

## Skills included
- `integrations.md`: credential vault and common local integration rules
- `github-cloudflare.md`: GitHub REST/GraphQL/Actions and Cloudflare APIs
- `telegram.md`: Bot API, Mini Apps, webhooks, media, and callback safety
- `providers-ai-google.md`: Gemini, OpenAI, Anthropic, DeepSeek, Hugging Face, OpenRouter, and other provider adapters
- `development.md`: Android/Kotlin/Compose, Termux, Python, Git, curl, SSH, MCP, and dependency policy
- `web-research.md`: current official documentation research and skill maintenance

## Keep knowledge current

The catalog is a list of canonical sources, not a claim that model weights are permanently updated. Before implementing or debugging an integration, the agent must open current official docs and changelogs, record URLs and date checked in the shared journal, and label anything it could not verify. Do not copy whole docs into prompts or rely on remembered model IDs/endpoints.

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

## Agent tool flow

When chat is connected to the Agent Room, the active model can request a local shell operation with the internal `[[CODEIN_TERMUX_EXEC]]...[[/CODEIN_TERMUX_EXEC]]` protocol. Codein runs it as the Termux user, returns stdout/stderr and exit status to the model, and refreshes shared room context. If the user supplies a requested API credential in chat, the app can store it locally through the `[[CODEIN_SAVE_KEY name]]...[[/CODEIN_SAVE_KEY]]` protocol and redact it from visible chat history.

Completed assistant turns are saved as local session summaries and update `state.json` so later model sessions can resume.
