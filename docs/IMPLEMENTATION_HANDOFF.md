# Codein implementation handoff

Update this file after each completed work chunk. Keep it in sync with `outputs/codein-handoff.md`.

## Current status
- [x] Initial repository and Termux/Agent flow reviewed.
- [x] Review report created: `outputs/codein-review.html` (workspace deliverable).
- [x] Baseline was clean `main`, HEAD `0bc77f2` (`Build main artifacts as Codein 1.0.9`).
- [x] Heartbeat now uses a 2-second MCP ping and suppresses two transient errors after first successful ping.
- [x] Authenticated SSE endpoint streams OpenAI-compatible, Gemini, and Anthropic providers; old `/api/chat` and MCP route remain.
- [x] Android client parses SSE frames, streams tokens to active chat, shows collapsible Agent trace, falls back for old server/unsupported providers, and cancels on stop/clear/history switch.
- [x] Cancellation disconnects the HTTP socket immediately so the server can stop upstream streaming.
- [x] SSE framing/auth and OpenAI/Gemini/Anthropic stream tests added; all eleven Termux tests pass; Python syntax check passes.
- [x] Live panel now shows colored waiting/receiving/tool/approval/completed stages and elapsed time; list auto-scroll includes the stage panel.
- [x] Agent Room reorganized into Models / Services / Composio / Report tabs; advanced OpenCode controls are collapsed, service/API key fields are masked, and report shows only event timestamp/kind.
- [x] SSE Kotlin client/UI is committed but not compiled (build prohibited); old-server streaming fallback remains.
- [x] Gemini `streamGenerateContent` and Anthropic Messages SSE are parsed natively, including tool calls and the existing command approval gate.
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
- `termux/server.py`: providers, legacy non-streaming chat, provider-native SSE readers, command approval/execute, MCP and REST handlers.
- `termux/codein-bootstrap.sh`: downloads server from `main` and restarts it.
- `app/src/main/java/com/example/ui/AgentTracePanels.kt`: colored processing stage/timer panel; `app/src/main/java/com/example/ui/MainScreen.kt` maps SSE stages and keeps the panel in view.
- `app/src/main/java/com/example/ui/components/CodeinSheets.kt`, `termux/composio_connect.py`, `.github/workflows/*.yml`, `docs/`.
- Connection check is MCP `ping` every 12 seconds, not `initialize` + `tools/list`. Heartbeat change uses a 2-second timeout and suppresses two failures only after the first successful ping.
- Push to `main` starts `android-ci.yml`; changes under `app/**` or workflow paths also match `android-main-build.yml`. `[skip ci]` is required to honor no-build instruction.
- GitHub `main` is ahead of this workspace Git metadata: commits were created through the connected GitHub integration because local HTTPS push had no credentials. Local source files match the pushed code, but `git status` may show them as uncommitted relative to local `HEAD`; do not blindly push or reset. Compare with GitHub `main` before further Git operations.

## Checklist

### 1. Establish baseline / handoff
- [x] Check branch, recent commit, worktree and remotes.
- [x] Inspect current connection check, server chat loop, command approval and workflow triggers.
- [x] Keep this checklist in project and refresh before/after each commit.

### 2. Termux server streaming
- [x] Add authenticated `POST /api/chat/stream` with flushed SSE JSON frames (`status`, `token`, `tool_call`, `tool_result`, `approval_required`, `error`, `done`).
- [x] Stream OpenAI-compatible providers; preserve existing `/api/chat` and MCP `provider_chat` behavior.
- [x] Add native streaming for Gemini and Anthropic; preserve text and provider tool-call structures across stream chunks.
- [x] Emit truthful waiting-for-provider and response-connected stages; no synthetic provider progress is shown while a request is pending.
- [x] Retain provider tool loop and exact-command approval gate; proposed commands still stop at `approval_required`.
- [x] Tool loop is capped at 9 iterations; streaming total time is capped at 300 seconds; broken client writes unwind and close the upstream response.
- [x] Add stdlib tests for SSE framing/auth, OpenAI/Gemini/Anthropic text and tool streams, native provider request shape and the command approval gate. Re-run after future server edits.

### 3. Android streaming and connection stability
- [x] Implement SSE line/event parsing (partial network chunks, UTF-8, multiple data lines) and cancelable HTTP request in `TermuxBridgeClient`.
- [x] Connect token events to active chat UI; generation guard prevents late events updating a cleared/switched chat.
- [x] Add collapsed live trace panel and stop control; existing permission panel handles approve/reject.
- [x] Color event stages, show elapsed processing time, retain final/error/cancelled states, and auto-scroll the current stage into view.
- [x] Add failure hysteresis and short ping; ping is already lightweight, so do not add tools/list polling.
- [x] Handle 401, missing provider key, timeout and old Termux server; old server falls back to MCP chat.

### 4. Agent Room UI / integration
- [x] Audit controls and callbacks before reorganizing `TermuxAgentRoomDialog`.
- [x] Simplify into Models, Services, Composio and Report tabs; advanced OpenCode controls remain collapsible.
- [x] Mask credential values and show only timestamp/kind in the report tab.
- [ ] Verify Composio Connect endpoints/tools against current code/docs before modifying OAuth behavior.
- [ ] Do not remove `COMPOSIO_API_KEY` from builds until a tested migration/replacement preserves existing users. No secret migration by assumption.

### 5. Docs, verification, commits
- [x] Update Agent Room design doc; bootstrap already fetches server from `main`, so no installer change is required.
- [x] Run Python/unit/bash checks only; do not run Gradle, APK build, release or workflow.
- [x] Review diff for approval bypass, unintended Hero/Zero/Dolphin changes, identifier/signing changes and secrets.
- [x] Update this checklist and `outputs/codein-handoff.md` with facts, tests and commit hashes.
- [x] Commit each coherent chunk on `main`, English scoped message plus `[skip ci]`; verify pushes do not start Actions.

## Recommended order
1. Consider real provider network/timeout/disconnect tests if credentials and external calls are explicitly in scope.
2. Review Kotlin changes again if they are modified; compile only if the user later permits it.
3. Finish Composio review only if its OAuth flow is part of the next requested scope.
4. Keep handoffs updated and commit small chunks with `[skip ci]`; never build/release.

## Acceptance criteria
- Provider tokens appear incrementally; old endpoints still work.
- A transient heartbeat failure does not flap status; idle bridge remains responsive.
- Stopping cancels streaming; command execution still requires explicit approval.
- Hero/Zero/Dolphin and application identifiers remain unchanged.
- Lightweight tests pass; no build/release/Actions run; commits are recorded below.

## Commits / verification
- `738a8d10eb550249df0d3e2fd00e45ce791736d6` — handoff committed to GitHub `main` (`[skip ci]`).
- `22653fe9bca2c65a1945b2a8aa4d7bd21b8a1ee0` — heartbeat hardening committed to GitHub `main` (`[skip ci]`).
- `0029e1203952fb1600d14e1f8aace3bbc77563e5` — OpenAI-compatible SSE server + tests committed to GitHub `main` (`[skip ci]`).
- `ce1b510f02d070a1416e0372402ecafe18bacc6d` — Android SSE client/UI and server safety/time limit committed to GitHub `main` (`[skip ci]`).
- `c427af68d83a8e889014be25e6f8e1d4bd7f300a` — previous handoff commit; exact SHA had 0 workflow runs.
- `0236614d6504d9a6bfcdec327b61ce06e31f161e` — immediate SSE cancellation added; its exact SHA has 0 workflow runs. Kotlin remains uncompiled.
- `3cd42a056afc8310c5cab70d4b1b5c409149ed63` — Agent Room tabs, masked credentials, report view, and design doc; exact SHA has 0 workflow runs.
- `72101067515e7afe3e1a5ee4656ff49aa4b3e42a` — updated project checklist after tab cleanup; exact SHA has 0 workflow runs.
- `0541e8a809997c8b4781a5feed8c8a1b43d90c6f` — finalized Agent Room handoff status; exact SHA has 0 workflow runs.
- `de130efc3f228ed3afdb3cac50362cc15db47af3` — native Gemini/Anthropic SSE plus parser, request and approval-gate tests; exact SHA has 0 workflow runs.
- `python3 -m unittest termux/test_mcp.py` — 11 tests passed, including Gemini/Anthropic streaming and approval gate; `python3 -m py_compile termux/server.py termux/test_mcp.py` — passed; `git diff --check` — passed.
- `bash -n termux/codein-bootstrap.sh` — passed. Checked the exact commit SHA in GitHub Actions: 0 workflow runs. No Gradle/APK build or release; Kotlin compile remains unverified.
