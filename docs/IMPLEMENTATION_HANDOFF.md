# Codein implementation handoff

Update this file after each completed work chunk. Keep it in sync with `outputs/codein-handoff.md`.

## Current status
- [x] Initial repository and Termux/Agent flow reviewed.
- [x] Review report created: `outputs/codein-review.html` (workspace deliverable).
- [x] Initial Git status checked: clean `main`, HEAD `0bc77f2` (`Build main artifacts as Codein 1.0.9`).
- [x] Agent Room heartbeat hardened locally: 2-second MCP ping; after first success, two transient errors do not flap UI status.
- [ ] Heartbeat code chunk not yet committed to GitHub; no tests/build/release/Actions run.
- [x] This handoff is in `docs/IMPLEMENTATION_HANDOFF.md`.

## User requirements
- Persian UI; English code and commit messages.
- Do not alter Hero/Zero or Dolphin (`chat.dphn.ai`); scope changes to Agent Room/Termux.
- Preserve `applicationId`, namespace, signing and existing user behavior.
- Work directly on `main`, make small commits and push to GitHub. Never run or trigger a build/release. Use `[skip ci]` in pushed commit messages because current push workflows otherwise build.
- Preserve command approval. `termux_exec` requires a recorded approval for the exact command; the UI first records approval, then executes it.
- Do not remove existing features without checking their callers/contracts. Preserve user changes.
- Composio key is embedded through `BuildConfig`; prefs alone do not make an APK-embedded secret secure. Do not remove/migrate it until a backward-compatible replacement exists.

## Relevant files and findings
- `app/src/main/java/com/example/ui/MainScreen.kt`: main chat + `TermuxAgentRoomDialog` (~1881); Termux connection state near lines 362-383.
- `app/src/main/java/com/example/termux/TermuxBridgeClient.kt`: MCP and REST client; `ping()` already calls lightweight MCP `ping`.
- `termux/server.py`: providers, non-streaming `provider_chat`, command approval/execute, MCP and REST handlers.
- `termux/codein-bootstrap.sh`: downloads server from `main` and restarts it.
- `app/src/main/java/com/example/ui/AgentTracePanels.kt`, `app/src/main/java/com/example/ui/components/CodeinSheets.kt`, `termux/composio_connect.py`, `.github/workflows/*.yml`, `docs/`.
- Connection check is MCP `ping` every 12 seconds, not `initialize` + `tools/list`. Heartbeat change uses a 2-second timeout and suppresses two failures only after the first successful ping.
- Push to `main` starts `android-ci.yml`; changes under `app/**` or workflow paths also match `android-main-build.yml`. `[skip ci]` is required to honor no-build instruction.

## Checklist

### 1. Establish baseline / handoff
- [x] Check branch, recent commit, worktree and remotes.
- [x] Inspect current connection check, server chat loop, command approval and workflow triggers.
- [x] Keep this checklist in project and refresh before/after each commit.

### 2. Termux server streaming
- [ ] Add authenticated `POST /api/chat/stream` with flushed SSE JSON frames and stable event schema (`status`, `token`, `tool_call`, `tool_result`, `approval_required`, `command_output`, `error`, `done`).
- [ ] Add provider-native streaming only where supported; preserve existing `/api/chat` and MCP `provider_chat` behavior.
- [ ] Retain provider tool loops and exact-command approval gate; never execute a command just because it was proposed.
- [ ] Bound iterations and total time; detect client disconnect and stop upstream work when practical.
- [ ] Add focused stdlib unit tests for SSE framing, errors, auth and approval behavior.

### 3. Android streaming and connection stability
- [ ] Implement robust SSE line/event parsing (partial chunks, UTF-8, multiple events) and cancelable HTTP request in `TermuxBridgeClient`.
- [ ] Connect token events to active chat UI; prevent late events updating a different/cleared chat.
- [ ] Add collapsed live trace panel with pending approval, approve/reject, error and stop states.
- [x] Add failure hysteresis and short ping; ping is already lightweight, so do not add tools/list polling.
- [ ] Keep handling 401, missing provider key, timeout and old Termux server explicit.

### 4. Agent Room UI / integration
- [ ] Audit controls and callbacks before reorganizing `TermuxAgentRoomDialog`.
- [ ] Simplify into Models, Services, Composio and Logs sections; keep advanced MCP settings accessible if used.
- [ ] Use one connection state with last useful error; keep credential values hidden.
- [ ] Verify Composio Connect endpoints/tools against current code/docs before modifying OAuth behavior.
- [ ] Do not remove `COMPOSIO_API_KEY` from builds until a tested migration/replacement preserves existing users. No secret migration by assumption.

### 5. Docs, verification, commits
- [ ] Update bootstrap/docs for server protocol changes; existing installer fetches server from `main`.
- [ ] Run Python/unit/bash checks only; do not run Gradle, APK build, release or workflow.
- [ ] Review full diff for approval bypass, unintended Hero/Zero/Dolphin changes, identifier/signing changes and secrets.
- [ ] Update this checklist and `outputs/codein-handoff.md` with facts, tests and commit hashes.
- [ ] Commit each coherent chunk on `main`, English scoped message plus `[skip ci]`; push without starting Actions.

## Recommended order
1. Finish this handoff and make an initial docs commit.
2. Implement and test server SSE while preserving legacy endpoint and approval rules.
3. Implement Android SSE parser/client and stream UI.
4. Stabilize connection indicator and add live trace/approval UX.
5. Simplify Agent Room; update Termux bootstrap/docs.
6. Final review, lightweight tests, update both handoffs, commit/push each chunk.

## Acceptance criteria
- Provider tokens appear incrementally; old endpoints still work.
- A transient heartbeat failure does not flap status; idle bridge remains responsive.
- Stopping cancels streaming; command execution still requires explicit approval.
- Hero/Zero/Dolphin and application identifiers remain unchanged.
- Lightweight tests pass; no build/release/Actions run; commits are recorded below.

## Commits / verification
- `738a8d10eb550249df0d3e2fd00e45ce791736d6` — handoff committed to GitHub `main` (`[skip ci]`).
- Heartbeat hardening: local changes pending GitHub commit.
- No tests/build/release/Actions run.
