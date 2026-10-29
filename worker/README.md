# Codein Composio Worker

This Worker keeps the Composio Platform project key off Android and Termux. It issues a signed anonymous identity per Termux installation and uses that identity as the Composio `user_id` for every session.

## Configure and deploy

Use a Composio Platform project API key, not a `ck_` consumer key. Create a scoped project key with **Session management: read/write** and **Session tool execution: write**. Add these secrets to the Worker using Wrangler's secure prompts:

```sh
npx wrangler secret put COMPOSIO_API_KEY
npx wrangler secret put IDENTITY_SIGNING_SECRET
```

Generate the signing secret locally with `openssl rand -hex 32`; never commit either value. The Wrangler config is at the repository root, so deploy from there with:

```sh
npx wrangler deploy
```

The Worker URL is the `workers.dev` URL printed by Wrangler. Set that non-secret URL as the GitHub Actions variable `COMPOSIO_WORKER_URL` before producing a public APK. No Composio key belongs in Android build configuration.

## API

- `POST /v1/bootstrap` creates an installation identity. The signed token is stored in Termux under `~/.codein/keys/` with mode `0600`.
- `POST /v1/session` creates or resumes that installation's persistent Composio session.
- `POST /v1/tools` returns the session tool catalog after checking that the session belongs to the signed identity.
- `POST /v1/execute` executes one tool in that same verified session.
- `GET /health` is a non-secret health check.

Cloudflare rate-limit bindings cap identity creation and authenticated requests. A persistent session keeps authorization links and per-user session state valid across tool calls; the Worker checks ownership against Composio before using the session.

This initial release uses anonymous per-install identities. Connections are not recoverable after deleting the Termux data directory or moving to a new installation. Adding account sign-in and identity recovery requires a real Codein account system.
