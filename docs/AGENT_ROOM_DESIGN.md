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

After the Termux Agent Room is connected, Codein includes the compact room snapshot and credential-name index in model context. The built-in Hero/Zero WebView chat requests a shell command through the versioned `[[CODEIN_TOOL_REQUEST:v1]]` JSON envelope; Codein validates the protocol, tool name, and command bounds, then shows the reason and exact command for explicit approval. Only after approval does it execute as the Termux user, display exit code/stdout/stderr in a terminal card, refresh room context, and return the result as untrusted tool context to the same conversation. The legacy command envelope remains accepted during the transition. A credential supplied in response to an explicit request can be stored using `[[CODEIN_SAVE_KEY name]]value[[/CODEIN_SAVE_KEY]]`; the UI suppresses the directive, stores the value locally, and redacts it from the visible user message before continuing. Completed turns are persisted as local sessions.

Provider chats selected from Termux use authenticated MCP Streamable HTTP: Android calls `tools/call` for `provider_chat`, receives progress through `notifications/message`, and sends `notifications/cancelled` when the user stops a response. OpenAI-compatible providers, Gemini, and Anthropic stream tokens and tool lifecycle events. The built-in ZERO/HERO WebView is a separate online route and does not use the local MCP bridge. Legacy `/api/chat` endpoints remain for existing local clients, but Android provider chat does not fall back to them. Shell tools stop at approval, and the Android panel approves the exact command before execution. The Agent Room dialog is divided into Models, Services, Composio, and Report tabs. The report tab displays only event timestamps and kinds, never journal payloads or credential values.
