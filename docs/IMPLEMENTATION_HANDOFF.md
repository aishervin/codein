# Codein implementation handoff

Update this file after each completed work chunk. Keep it in sync with `outputs/codein-handoff.md`.

## Current status
- [x] Initial repository and Termux/Agent flow reviewed.
- [x] Review report created: `outputs/codein-review.html` (workspace deliverable).
- [x] Baseline was clean `main`, HEAD `0bc77f2` (`Build main artifacts as Codein 1.0.9`).
- [x] Heartbeat now uses a 2-second MCP ping and suppresses two transient errors after first successful ping.
- [x] Authenticated SSE endpoint streams OpenAI-compatible, Gemini, and Anthropic providers; old `/api/chat` and MCP route remain.
- [x] Gemini tool schemas are recursively restricted to fields supported by its FunctionDeclaration schema; provider 429/503 responses are summarized instead of returning raw JSON.
- [x] Streaming chat requests have unique IDs; Stop posts an authenticated cancel request, which closes the active upstream response and prevents further provider/tool-loop iterations.
- [x] Termux bridge lifecycle is explicit (`start|stop|restart|status|logs`); bootstrap no longer installs an endless auto-restart loop.
- [x] OpenCode controls delegate to the Termux-compatible manager and stop only its managed `opencode serve` process. App controls use the authenticated MCP tool directly, not shell command text; the provider agent cannot autonomously manage OpenCode. OpenCode remains optional and does not silently replace the selected Codein chat provider.
- [ ] Re-run the updated Termux bootstrap on-device, then verify Gemini/Composio requests, Stop during a pending response, and bridge stop/start.
- [x] Android client parses provider stream events and shows a collapsible Agent trace. Termux-provider chat now uses MCP Streamable HTTP; the built-in ZERO/HERO route remains unchanged.
- [x] Cancellation disconnects the app socket and sends the request ID to the server; the server closes the provider response and unwinds the tool loop.
- [x] SSE framing/auth, provider parsing, cancellation, Gemini schema and transient 503 tests added; all sixteen Termux tests pass; Python syntax check passes.
- [x] Live panel now shows colored waiting/receiving/tool/approval/completed stages and elapsed time; list auto-scroll includes the stage panel.
- [x] When provider output is pending, the live stage remains on “waiting for model” while a timer advances; stage changes reflect actual server response and tool events.
- [x] Agent Room reorganized into Models / Services / Composio / Report tabs; advanced OpenCode controls are collapsed, service/API key fields are masked, and report shows only event timestamp/kind.
- [x] Earlier Android streaming UI was built in GitHub Actions; current MCP cutover build is pending CI verification.
- [x] Gemini `streamGenerateContent` and Anthropic Messages SSE are parsed natively, including tool calls and the existing command approval gate.
- [x] This handoff is in `docs/IMPLEMENTATION_HANDOFF.md`.

## User requirements
- Persian UI; English code and commit messages.
- Do not alter Hero/Zero or Dolphin (`chat.dphn.ai`); scope changes to Agent Room/Termux.
- Preserve `applicationId`, namespace, signing and existing user behavior.
- Work directly on `main`, make small commits and push to GitHub. The earlier no-build restriction was superseded: the user explicitly authorized building and publishing Codein 1.1.0. Avoid unintended workflow runs; release only through the reviewed manual workflow.
- Preserve command approval. `termux_exec` requires a recorded approval for the exact command; the UI first records approval, then executes it.
- Do not remove existing features without checking their callers/contracts. Preserve user changes.
- The public Composio path must not embed a project key in `BuildConfig`; use the Worker. Preserve the local Termux consumer-key fallback for existing local users.

## Relevant files and findings
- `app/src/main/java/com/example/ui/MainScreen.kt`: main chat + `TermuxAgentRoomDialog` (~1881); Termux connection state near lines 362-383.
- `app/src/main/java/com/example/termux/TermuxBridgeClient.kt`: MCP and REST client; `ping()` already calls lightweight MCP `ping`.
- `termux/server.py`: providers, legacy non-streaming chat, provider-native SSE readers, command approval/execute, MCP and REST handlers.
- `termux/codein-bootstrap.sh`: downloads server from `main` and restarts it.
- `termux/codein-bridge`: explicit Termux bridge lifecycle manager; old bridge PID files are handled during upgrade.
- `termux/codein-opencode`: starts/stops only its managed OpenCode API process; it does not stop arbitrary matching processes.
- `gemini_schema()` in `termux/server.py` removes unsupported JSON Schema draft fields from nested Gemini tool declarations, including Composio schemas.
- User screenshots show Gemini HTTP 400 errors for `$schema`/`additionalProperties`, plus a separate upstream HTTP 503 high-demand response. The Termux indicator is green in those screenshots; they confirm the bridge reached Gemini but do not prove it disconnected.
- `app/src/main/java/com/example/ui/AgentTracePanels.kt`: colored processing stage/timer panel; `app/src/main/java/com/example/ui/MainScreen.kt` maps SSE stages and keeps the panel in view.
- `app/src/main/java/com/example/ui/components/CodeinSheets.kt`, `termux/composio_connect.py`, `.github/workflows/*.yml`, `docs/`.
- Connection check is MCP `ping` every 12 seconds, not `initialize` + `tools/list`. Heartbeat change uses a 2-second timeout and suppresses two failures only after the first successful ping.
- Push to `main` starts `android-ci.yml`; changes under `app/**` also start `android-main-build.yml`. Keep the current MCP fix eligible for both checks; do not create a release tag unless asked.
- This workspace has no local Git metadata. Use the connected GitHub integration to commit; never reset or overwrite unrelated workspace files.

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
- [x] Tool loop is capped at 9 iterations; streaming total time is capped at 300 seconds; broken client writes and explicit cancel requests unwind and close the upstream response.
- [x] Add stdlib tests for SSE framing/auth, OpenAI/Gemini/Anthropic text and tool streams, native provider request shape and the command approval gate. Re-run after future server edits.

### 3. Android streaming and connection stability
- [x] Implement SSE line/event parsing (partial network chunks, UTF-8, multiple data lines) and cancelable HTTP request in `TermuxBridgeClient`; cancellation sends a server-side request ID.
- [x] Connect token events to active chat UI; generation guard prevents late events updating a cleared/switched chat.
- [x] Add collapsed live trace panel and stop control; existing permission panel handles approve/reject.
- [x] Color event stages, show elapsed processing time, retain final/error/cancelled states, and auto-scroll the current stage into view.
- [x] Add failure hysteresis and short ping; ping is already lightweight, so do not add tools/list polling.
- [x] Handle 401, missing provider key, timeout and old Termux server; outdated bridges prompt for the updater rather than silently using a legacy chat route.
- [x] OpenCode service controls use a managed PID file and do not use broad `pkill`; there is not yet automatic chat-provider fallback to OpenCode.

### 4. Agent Room UI / integration
- [x] Audit controls and callbacks before reorganizing `TermuxAgentRoomDialog`.
- [x] Simplify into Models, Services, Composio and Report tabs; advanced OpenCode controls remain collapsible.
- [x] Mask credential values and show only timestamp/kind in the report tab.
- [x] Verify Composio Platform session, tool, execution, and connection APIs against current official docs.
- [x] Replace the Android-bundled `COMPOSIO_API_KEY` with a server-only Platform Worker; preserve existing Termux consumer-key files as a local-only fallback.

### 5. Docs, verification, commits
- [x] Update bootstrap to install the bridge lifecycle manager and start it without an endless supervisor loop.
- [x] Run the updated Termux suite (20 tests), Python syntax checks, all three Termux shell syntax checks; local Kotlin compilation is unavailable because Java is absent.
- [x] Review diff for approval bypass, unintended Hero/Zero/Dolphin changes, identifier/signing changes and secrets.
- [x] Update this checklist and `outputs/codein-handoff.md` with facts, tests and commit hashes.
- [x] Commit each coherent chunk on `main`, English scoped message plus `[skip ci]`; verify pushes do not start Actions.

## Recommended order
1. Test the updated bootstrap on-device: bridge `status`, Stop a pending chat, then bridge `restart` and `stop`.
2. Test Gemini once directly and once with Composio tools; HTTP 429/503 is a provider-capacity issue, not evidence the bridge died.
3. Verify optional OpenCode install/start/status/stop from Agent Room. Chat stays on the chosen Codein provider; no automatic model/provider switch occurs.
4. If the connection indicator still drops, capture `~/.codein/logs/server.log` and compare authenticated `/health` results with app status.

## Acceptance criteria
- Provider tokens appear incrementally; old endpoints still work.
- A transient heartbeat failure does not flap status; idle bridge remains responsive.
- Stopping cancels the provider response at the bridge; command execution still requires explicit approval.
- Hero/Zero/Dolphin and application identifiers remain unchanged.
- Lightweight tests pass; signed v1.1.0 release workflow succeeds and its APK is published; commits and workflow run are recorded below.

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
- `8159b3ead9393899330d890c390dccd0658ae2ae` — colored live processing stages, elapsed timer, auto-scroll and server provider-stage events; exact SHA has 0 workflow runs. Kotlin remains uncompiled by request.
- `bd5a87f57d870e6c9833590bb01144e5d228ed10` — structured WebView Termux handoff, v1.1.0 release settings and workflow hardening; `[skip ci]`.
- Earlier baseline: `python3 -B -m unittest termux/test_mcp.py` — 13 tests passed; `python3 -B -m py_compile termux/server.py termux/test_mcp.py` — passed; `git diff --check` — passed.
- `fdf6203` — cancel Codein streams, manage Termux/OpenCode lifecycle, Gemini schema/error handling; committed locally on `main` with `[skip ci]`. This commit has not been pushed; local GitHub write credentials are unavailable, and the connected integration commit request exceeded this environment's argument-size limit.
- `python3 -B -m unittest termux/test_mcp.py` — 16 tests passed; Python `compile()` syntax checks, `bash -n termux/codein-bootstrap.sh termux/codein-bridge termux/codein-opencode`, and `git diff --check` passed. Local Kotlin compilation is unavailable because Java/Gradle are absent.
- The authorized GitHub Android Release workflow previously built the signed APK and published v1.1.0. No workflow has run for the local commit.
- Android Release run `40909550882` — success on `bd5a87f57d870e6c9833590bb01144e5d228ed10`. Published `Codein-1.1.0-Universal.apk` (10,946,816 bytes); release: https://github.com/aishervin/codein/releases/tag/v1.1.0 . That historical APK build included the `COMPOSIO_API_KEY` build field; rotate any key previously injected there before using it again. Secret values were not accessed.

## Current Composio Worker handoff (2026-10-29)
- Public integration now uses `worker/` as a server-side Composio Platform proxy. Each Termux installation receives an anonymous signed identity and persistent session; the Worker checks session ownership and rate-limits bootstrap and authenticated API calls.
- Android no longer needs a Composio project key. GitHub Actions reads the non-secret `COMPOSIO_WORKER_URL` repository variable. The legacy Termux consumer-key path remains local-only.
- Verification in this workspace: `python3 -B -m unittest termux/test_mcp.py` passed (18 tests); `node --test worker/test-worker.mjs` passed (5 tests); `node --check worker/src/index.mjs`, `bash -n termux/codein-bootstrap.sh`, Python syntax checks, and `git diff --check` passed.
- Android Kotlin compilation remains unverified because Java is unavailable (`JAVA_HOME` is unset and no `java` executable is installed).
- Cloudflare deployment is blocked: the connected Cloudflare API returned an invalid `X-Auth-Key` format error. The user says the account was registered by email; the direct Cloudflare email/password form rejected the secure sign-in attempt. The live browser is left at Cloudflare's login page for the user to take over. No Worker deployment or secret configuration has happened.
- The published `https://codein.shen-zero.workers.dev/` endpoint still returns the default `Hello world` response on `/health`, `/`, and `/v1/bootstrap`; Codein has not been deployed there. The supplied URL implies the Worker name `codein`, so `worker/wrangler.jsonc` now targets that existing Worker name.
- A Cloudflare build log ran `npx wrangler deploy` from the repository root and failed to find static files because no root Wrangler config was present. The config now lives at the repository root and points to `worker/src/index.mjs`, matching Cloudflare's existing deploy command. If the project is configured as a static Pages site, use a Workers project instead.
- Cloudflare's Git build can only deploy Worker files present in its connected remote branch; push the Worker source/config before retrying that build.
- Next: user completes Cloudflare sign-in in the live browser; configure a scoped Composio Platform project key and generated identity-signing secret through Wrangler secure prompts; deploy `worker/`; set GitHub Actions variable `COMPOSIO_WORKER_URL`; then run a public-path integration check and build the APK.

## MCP Provider Chat Cutover (2026-10-30)
- Latest screenshots show the active model is built-in ZERO while the Termux MCP bridge is disconnected. ZERO/HERO and Dolphin remain unchanged; select a Termux provider model to use the MCP route.
- Android Termux-provider chat now calls MCP `tools/call` for `provider_chat`, consumes `notifications/message` stream events, and sends `notifications/cancelled` when stopped. Legacy REST/SSE routes remain for other local clients but Android provider chat no longer uses them.
- Termux server version is 1.2.0 and advertises logging capability for MCP stream events. Tests cover progress, final responses and cancellation.
- The Termux installer stages and validates the complete update before replacing files, restarts the bridge, and verifies MCP version 1.2.0. Existing `config.json` token, API-key files, `ROOM.md`, `state.json`, and `.gitignore` are preserved.
- Android app version is 1.2.0 / versionCode 21. Main-build workflow artifact is named `Codein-1.2.0-Universal.apk`; no public release/tag is created by this change.
- Local verification: 20 Termux tests passed, 5 Worker tests passed, Python and shell syntax checks passed. GitHub Android CI and the 1.2.0 Main Build both succeeded on commit `638c8624fd9fd9a8519fa0560dc933df7a936ada`.
- APK artifact: `Codein-1.2.0-Universal` from https://github.com/aishervin/codein/actions/runs/40965865626 (available until 2026-11-12). No GitHub Release or tag was created.
- Kotlin/Gradle build could not run in this workspace because Java is unavailable; the successful Actions builds provide the Android compile verification.
- Required device check after delivery: run the installer in Termux, re-enter the existing bridge token if needed, select the Termux provider model, verify Gemini/Composio and Stop; do not run a second service or manually execute `server.py`.
- Follow-up fix: the first Termux updater run hit `bash: line 18: remote: unbound variable` before downloads or replacements. The `fetch()` helper now declares `remote`/`target` before deriving `staged`; rerunning the same updater command is safe and preserves local data.
