# Codein Agent Room — shared context contract

## Purpose
All model providers and agent sessions share one local source of truth under `~/.codein` in Termux. A model is a reasoning engine; the local Agent Room supplies persistent project context, named credentials, session summaries, and recent tool activity.

## Startup sequence
1. Read `ROOM.md` and `state.json`.
2. Load active project, last task, recent changes, and relevant session summaries.
3. Check named credential index before asking the user for an API token.
4. If absent, ask for the exact credential and explain the minimum permissions needed. Save it to the local vault, then continue.
5. Record plans and meaningful actions in the event journal. Never journal raw secret values.
6. At task completion, save a compact session summary and update `state.json`.

## Credential naming
Use stable provider-qualified names: `github_pat`, `cloudflare_api_token`, `cloudflare_account_id`, `openai_api_key`, `gemini_api_key`, `deepseek_api_key`. For multiple accounts, add a suffix such as `cloudflare_api_token_personal`. Do not silently overwrite another account.

## Model independence
No model gets a separate credential database or workspace. Every provider uses the same local bridge APIs. Switching models must not change project state, event journal, session archive, or vault location.

## Tool execution
Commands execute as the Termux user. Capture exit code, stdout, stderr, and duration. Report missing packages or permissions as concrete next steps; never pretend an operation succeeded.

## Session handoff
Before yielding to another agent, persist objective/status, changed files, decisions/constraints, commands and tests with exit codes, unresolved work, and the exact next action. This keeps the workspace a shared “thinking room” rather than disconnected chats.

## Native chat handoff

After the Termux Agent Room is connected, Codein includes the compact room snapshot and credential-name index in model context. The active model may request a command with `[[CODEIN_TERMUX_EXEC]]command[[/CODEIN_TERMUX_EXEC]]`; Codein runs it as the Termux user, returns the exit code/stdout/stderr, refreshes room context, and continues the same task. A credential supplied in response to an explicit request can be stored using `[[CODEIN_SAVE_KEY name]]value[[/CODEIN_SAVE_KEY]]`; the UI suppresses the directive, stores the value locally, and redacts it from the visible user message before continuing. Completed turns are persisted as local sessions.
