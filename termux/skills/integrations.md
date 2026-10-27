# Local integrations skill

This skill is shared by every model in the Codein Agent Room. Use it whenever the user asks to work with a connected service.

## Before requesting credentials
1. Read the shared workspace context and list credential names using the local bridge.
2. Search for a matching name (for example `github_pat` or `cloudflare_api_token`).
3. If the credential is absent, ask for the exact token needed in chat, explain the service and permissions briefly, save it in the local vault, and continue. Never ask again if the matching key already exists.
4. Never include secret values in session summaries, event logs, command output intended for chat, or project files.

Use `~/.codein/bin/codein-with-key <credential_name> -- <command> [args...]` to inject a stored token into a child process as `CODEIN_SECRET` without printing it. For example, use the helper to run curl with an `Authorization: Bearer $CODEIN_SECRET` header. Never use `set -x`, `env`, `printenv`, or other secret-dumping operations while working with credentials.

## API-first service access
Use Termux `curl` and `python` for direct APIs; no third-party integration service is required.

### GitHub
- Token key name: `github_pat`
- API base: `https://api.github.com`
- Header: `Authorization: Bearer $TOKEN`
- Send `Accept: application/vnd.github+json` and `X-GitHub-Api-Version: 2022-11-28`.
- Prefer the least permissions that cover the task. Use the token stored in the vault; do not echo it.

### Cloudflare
- Token key name: `cloudflare_api_token`
- API base: `https://api.cloudflare.com/client/v4`
- Header: `Authorization: Bearer $TOKEN`
- For account-level operations, also ask for and store `cloudflare_account_id` if it is not available.
- If a request genuinely requires a token with broad permissions, explain which operation needs it and ask the user to supply the appropriate token. Do not assume every Cloudflare task needs a global API key.

## Shared thinking-room workflow
- Read `ROOM.md`, `state.json`, recent events, and relevant session summaries before starting.
- Save a short plan and meaningful changes to the event journal.
- After finishing, save a session summary and update `state.json` with active project, last task, and recent changes.
- Reuse named keys and session context across models; provider switching must not create a separate workspace.
