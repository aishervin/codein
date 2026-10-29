# Codein implementation handoff

Update this checklist after each chunk.

## Progress
- [x] Initial repo/Termux flow reviewed; clean main at `0bc77f2`.
- [x] Review report: `outputs/codein-review.html` (workspace only).
- [x] Handoff added to `docs/IMPLEMENTATION_HANDOFF.md`.
- [ ] No code changes or tests yet. No build/release/Actions run.
- Connection check is already MCP `ping` every 12 seconds, NOT initialize/tools-list. One transient error immediately flips UI state false.
- Command flow records approval for the exact command, then executes it. Preserve this gate.

## Requirements
- Persian UI, English code/commit messages. Do not alter Hero/Zero or Dolphin (`chat.dphn.ai`).
- Preserve app id, namespace, signing and existing user behavior.
- Make small commits directly on `main`, push with `[skip ci]`; never run/trigger builds/releases. Push workflows otherwise build.
- Never execute proposed shell commands without explicit user approval. Do not remove Composio BuildConfig key until a backward-compatible alternative exists; prefs alone do not secure an APK-embedded key.

## Files examined
`MainScreen.kt` (connection state and Agent Room UI), `TermuxBridgeClient.kt` (MCP/HTTP client), `termux/server.py` (provider chat/tools/approval), `termux/codein-bootstrap.sh`, `.github/workflows/android-{ci,main-build}.yml`.

## Checklist
### Server SSE
- [ ] Add authenticated `POST /api/chat/stream` with flushed SSE JSON frames and stable events: status, token, tool_call, tool_result, approval_required, command_output, error, done.
- [ ] Native streaming per supported provider; preserve `/api/chat` and MCP `provider_chat`.
- [ ] Preserve tool loop, exact-command approval, iteration/time limits and stop on disconnect where practical.
- [ ] Add stdlib tests for framing, errors, auth, legacy behavior and approval.
### Android client/UI
- [ ] Robust UTF-8 SSE parser (split chunks/multiple events), cancellable request, clear 401/timeout/missing-key errors.
- [ ] Stream tokens into current chat only; prevent late events updating a changed chat.
- [ ] Collapsible live trace panel with approval/reject/error/stop states. Stop must cancel request.
- [ ] Add connection failure hysteresis/backoff; ping is already light; do not poll tools/list.
### Agent Room / integration
- [ ] Audit callbacks; simplify UI into Models, Services, Composio, Logs without removing needed advanced settings.
- [ ] One connection status and useful last error; never show credential values.
- [ ] Verify Composio flow before edits; defer key migration until safe/tested.
### Finish and commits
- [ ] Update Termux bootstrap/docs for protocol changes.
- [ ] Run only Python/unit/bash checks. Do not run Gradle, APK build, release or workflow.
- [ ] Review diff for approval bypass, Hero/Zero/Dolphin edits, identifiers/signing changes and secrets.
- [ ] Update this checklist and workspace handoff with tests/commit hashes.
- [ ] Small English scoped commits on `main`, append `[skip ci]`, push; verify no Actions run.

## Order
1. Server SSE + tests. 2. Android parser/stream UI. 3. Stability and approvals panel. 4. Agent Room cleanup. 5. Bootstrap/docs and review. Commit each coherent chunk.

## Acceptance
Incremental tokens; legacy API works; transient ping failures do not flap; stop cancels; shell remains approval-gated; Hero/Zero/Dolphin/app id unchanged; lightweight tests only; no builds.

## Commits / verification
- Handoff commit pending verification.
