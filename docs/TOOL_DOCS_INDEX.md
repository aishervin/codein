# Canonical Tool Documentation Index

This is the source-of-truth URL catalog for every model connected to Codein. URLs point to official vendor documentation, not third-party tutorials. The index is intentionally version-agnostic: APIs evolve, so agents must verify current docs before implementing or changing integrations.

## Required documentation workflow

1. Identify the exact product/API and operation.
2. Open the official docs below; follow the current version selector and migration/deprecation notices.
3. Verify endpoint, HTTP method, auth scopes, required fields, rate limits, pagination, error format, and examples.
4. Prefer official OpenAPI/spec/schema or SDK reference when available.
5. If the official docs are unreachable, say so and mark the implementation as unverified; never invent an endpoint or claim docs were checked.
6. Save the URL, date checked (UTC), API/version used, and relevant caveat in the task event log. Do not cache full docs indefinitely; refresh before implementation and after errors.
7. Use a vendor SDK only when it materially improves correctness; direct HTTP via curl/Python is the lightweight default in Termux.

## GitHub
- REST API: https://docs.github.com/en/rest
- REST API overview/versioning: https://docs.github.com/en/rest/overview
- Authentication: https://docs.github.com/en/rest/authentication
- Fine-grained PATs: https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens
- GraphQL API: https://docs.github.com/en/graphql
- GitHub CLI manual: https://cli.github.com/manual/
- Actions workflows: https://docs.github.com/en/actions
- Actions artifacts: https://docs.github.com/en/actions/using-workflows/storing-workflow-data-as-artifacts
- Webhooks: https://docs.github.com/en/webhooks
- Git database/contents APIs: https://docs.github.com/en/rest/repos/contents

## Cloudflare
- Developer docs home: https://developers.cloudflare.com/
- API reference: https://developers.cloudflare.com/api/
- API tokens: https://developers.cloudflare.com/fundamentals/api/get-started/create-token/
- Workers: https://developers.cloudflare.com/workers/
- Workers runtime APIs: https://developers.cloudflare.com/workers/runtime-apis/
- Workers AI: https://developers.cloudflare.com/workers-ai/
- Pages: https://developers.cloudflare.com/pages/
- D1: https://developers.cloudflare.com/d1/
- KV: https://developers.cloudflare.com/kv/
- R2: https://developers.cloudflare.com/r2/
- Durable Objects: https://developers.cloudflare.com/durable-objects/
- Queues: https://developers.cloudflare.com/queues/
- Wrangler CLI: https://developers.cloudflare.com/workers/wrangler/
- Cloudflare Tunnel: https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/

## Telegram
- Bot API (canonical): https://core.telegram.org/bots/api
- Bot features and setup: https://core.telegram.org/bots
- Mini Apps: https://core.telegram.org/bots/webapps
- Web Apps / Mini App guidelines: https://core.telegram.org/bots/webapps#initializing-mini-apps
- Payments: https://core.telegram.org/bots/payments
- Telegram Login: https://core.telegram.org/widgets/login
- MTProto API: https://core.telegram.org/api
- TDLib: https://core.telegram.org/tdlib
- Bot API changelog: https://core.telegram.org/bots/api-changelog

## Google and Android
- Google for Developers: https://developers.google.com/
- Google APIs Explorer: https://developers.google.com/apis-explorer
- Google API discovery directory: https://www.googleapis.com/discovery/v1/apis
- Google Identity: https://developers.google.com/identity
- OAuth 2.0: https://developers.google.com/identity/protocols/oauth2
- Android Developers: https://developer.android.com/
- Android API reference: https://developer.android.com/reference
- Kotlin docs: https://kotlinlang.org/docs/home.html
- Jetpack Compose: https://developer.android.com/compose
- Android Gradle Plugin: https://developer.android.com/build
- Google AI for Developers: https://ai.google.dev/
- Gemini API docs: https://ai.google.dev/gemini-api/docs
- Gemini API reference: https://ai.google.dev/api
- Firebase docs: https://firebase.google.com/docs
- Google Cloud docs: https://cloud.google.com/docs

## AI model providers
- OpenAI API: https://platform.openai.com/docs
- OpenAI API reference: https://platform.openai.com/docs/api-reference
- Anthropic API: https://docs.anthropic.com/
- DeepSeek API: https://api-docs.deepseek.com/
- Hugging Face Hub: https://huggingface.co/docs
- Hugging Face Inference Providers: https://huggingface.co/docs/inference-providers
- OpenRouter API: https://openrouter.ai/docs
- Ollama API: https://docs.ollama.com/
- Mistral AI: https://docs.mistral.ai/
- xAI API: https://docs.x.ai/
- Cohere API: https://docs.cohere.com/
- Cloudflare Workers AI: https://developers.cloudflare.com/workers-ai/

## Core local tools and libraries
- Termux packages: https://wiki.termux.com/wiki/Package_Management
- Termux project: https://github.com/termux/termux-packages
- Python docs: https://docs.python.org/3/
- Python urllib: https://docs.python.org/3/library/urllib.html
- Python subprocess: https://docs.python.org/3/library/subprocess.html
- Python JSON: https://docs.python.org/3/library/json.html
- Git reference: https://git-scm.com/docs
- curl manual: https://curl.se/docs/
- OpenSSH manuals: https://www.openssh.com/manual.html
- JSON Schema: https://json-schema.org/
- OpenAPI specification: https://spec.openapis.org/oas/latest.html
- Model Context Protocol: https://modelcontextprotocol.io/
- MCP specification: https://modelcontextprotocol.io/specification/latest

## Freshness policy
“Latest” means the newest version currently documented by the vendor when the task is performed—not the newest version remembered by the model. Check changelog/release notes and deprecations before writing code. Do not claim that the model itself has permanently learned new documentation: the agent retrieves current documentation at task time and records sources in the shared room.
