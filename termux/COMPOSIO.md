# Composio Platform for Codein

The public Codein app uses Composio Platform sessions. Each Termux installation gets its own signed identity; connected accounts and tool calls are isolated under that Composio user ID. App users do not need a Composio API key.

## Setup

1. Deploy `worker/` using the instructions in `worker/README.md`.
2. Configure the Android build with the resulting Worker URL through the GitHub Actions variable `COMPOSIO_WORKER_URL`.
3. Re-run the Codein Termux installer to install the Platform client helper.
4. Connect Termux in Codein, open the Composio tab, and choose **Set up securely**. The helper requests a signed per-install identity and stores it locally in `~/.codein/keys/composio_platform.json` with file permissions `0600`.
5. Choose a service and select **Connect**. Codein requests a hosted authorization link directly through the Composio tool and displays it beneath that service's buttons. The link opens in the Android system browser for OAuth compatibility. After approving access, return to Codein and tap **Check status**.

No additional Termux package is needed for Composio; the helper uses Python's standard library. Codein Browser uses Android System WebView and can open a page externally if a site does not render correctly in its embedded view.

Composio stores connected-account credentials. Neither Codein nor its model handles provider OAuth tokens.

## Architecture

- Public Worker source: `worker/src/index.mjs`
- Composio API key and identity-signing key: Cloudflare Worker secrets only.
- Platform helper: `termux/composio_platform.py`
- Termux bridge creates one persistent Composio session for each installation. The Worker checks session ownership against the signed identity before returning tools or executing actions.
- Legacy locally stored consumer-key connections remain supported for existing installations, but the public UI no longer requests those keys.

## Identity and recovery

This version uses an anonymous identity per Termux installation, not a Codein account. Deleting the Termux data directory or moving to another installation loses access to the old Composio identity and its connected accounts. A future account system is required for cross-device recovery.

If the Worker rejects a tool or catalog request, the helper revalidates the current session and retries once. If the installation token itself is rejected, it provisions a replacement anonymous identity; connected accounts from the prior identity are not transferred and may need to be connected again.

The Worker rate-limits bootstrap and authenticated API calls. Create a scoped Composio Platform project key with Session management read/write and Session tool execution write permissions. Do not place it in Android build settings, GitHub Actions variables, chat, or source control.

References:
- https://docs.composio.dev/docs/authentication
- https://docs.composio.dev/docs/sessions-via-mcp
- https://docs.composio.dev/reference/api-reference/tool-router
