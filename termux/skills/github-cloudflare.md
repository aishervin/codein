# Skill: GitHub and Cloudflare Automation

## GitHub canonical docs
- REST API: https://docs.github.com/en/rest
- API versioning/authentication: https://docs.github.com/en/rest/overview and https://docs.github.com/en/rest/authentication
- Repository contents: https://docs.github.com/en/rest/repos/contents
- Pull requests: https://docs.github.com/en/rest/pulls
- GitHub Actions: https://docs.github.com/en/actions
- Workflow runs/jobs: https://docs.github.com/en/rest/actions/workflow-runs and https://docs.github.com/en/rest/actions/workflow-jobs
- Webhooks: https://docs.github.com/en/webhooks
- GraphQL: https://docs.github.com/en/graphql

Use `github_pat` for a personal access token unless a separately named GitHub App installation token is configured. Prefer fine-grained PATs with only required repositories and permissions. Use `Accept: application/vnd.github+json` and the currently documented `X-GitHub-Api-Version`. Respect pagination, secondary rate limits, and conditional requests. Before editing a file through the API, fetch its current SHA. Do not push directly to the default branch unless explicitly requested; use a feature branch and PR for substantial changes. Check CI against the exact latest head SHA.

## Cloudflare canonical docs
- Developer docs: https://developers.cloudflare.com/
- API reference: https://developers.cloudflare.com/api/
- API tokens: https://developers.cloudflare.com/fundamentals/api/get-started/create-token/
- Workers: https://developers.cloudflare.com/workers/
- Wrangler: https://developers.cloudflare.com/workers/wrangler/
- Pages: https://developers.cloudflare.com/pages/
- D1: https://developers.cloudflare.com/d1/
- KV: https://developers.cloudflare.com/kv/
- R2: https://developers.cloudflare.com/r2/
- Workers AI: https://developers.cloudflare.com/workers-ai/
- Durable Objects: https://developers.cloudflare.com/durable-objects/

Use `cloudflare_api_token` and, when needed, `cloudflare_account_id`; use `cloudflare_zone_id` only for zone-specific work. Never default to a Global API Key. Select the narrowest token template/permissions for the requested action. Verify account/zone IDs and the target environment before deployment. Read API response `success`, `errors`, and `result` rather than trusting HTTP status alone. For Wrangler projects, inspect existing configuration before changing names, bindings, compatibility dates, or deployment targets.

## Release/deploy protocol
- Inspect repository status and active branch before writing.
- Make one coherent batch of edits, run relevant tests/build, then inspect CI.
- Verify the exact commit/branch deployed and the final endpoint/status.
- Ask before destructive production changes, resource deletion, or paid resource creation.
