# Skill: AI Providers and Google APIs

Read `TOOL_DOCS_INDEX.md` before using a provider. The official docs are canonical; refresh them for each implementation.

## Supported provider adapters
Implement providers behind one common adapter contract: provider ID, display name, base URL, auth-header strategy, model-list strategy if supported, chat/request method, streaming support, tool-call format, context limits when documented, and error normalization. Do not assume all providers use the same OpenAI-compatible schema.

Official references:
- Google Gemini: https://ai.google.dev/gemini-api/docs and https://ai.google.dev/api
- OpenAI: https://platform.openai.com/docs and https://platform.openai.com/docs/api-reference
- Anthropic: https://docs.anthropic.com/
- DeepSeek: https://api-docs.deepseek.com/
- Hugging Face: https://huggingface.co/docs/inference-providers
- OpenRouter: https://openrouter.ai/docs
- Mistral: https://docs.mistral.ai/
- xAI: https://docs.x.ai/
- Cohere: https://docs.cohere.com/
- Ollama: https://docs.ollama.com/
- Cloudflare Workers AI: https://developers.cloudflare.com/workers-ai/

## Built-in local provider catalog

The Termux bridge exposes `GET /api/providers` and `POST /api/chat`. The initial presets include Gemini, OpenAI, DeepSeek, Anthropic, OpenRouter, Mistral, xAI, and Groq, with their documented base URLs and a default model per provider. These are starting defaults, not a guarantee that model IDs remain available. Verify live provider documentation before changing defaults; allow the user to choose another supported model. The Android chat sends requests to the selected provider through the authenticated local Termux bridge, not through a scraped website.

If `/api/chat` returns `428` with `error=missing_api_key`, ask the user for that provider's key directly in the current chat. Save it under the returned `credential_name` in `~/.codein/keys/`, never echo or display the key, then retry the same request. The Android UI must redact the secret from visible history. Never ask for a key that is already present in the vault.

## Provider behavior
1. Check configured providers and available credential names before asking the user.
2. Model IDs and endpoint URLs must come from current provider docs or an explicit user configuration; never hard-code an assumed “latest model”.
3. Support multiple profiles per provider, custom compatible endpoints, model aliases, streaming on/off, and a per-profile key name in the shared vault.
4. Show provider/model/source in UI; allow manual model entry when listing is unsupported.
5. Normalize timeouts, HTTP errors, rate limits, quota failures, invalid model errors, and cancellation without hiding the original status/message.
6. Keep credentials on-device/Termux for this local-first phase. Send only the needed request to the selected provider.
7. Never route all prompts through a webview or browser scrape as the primary model path. Browser/webview support is optional and secondary.
8. “Free model” availability changes frequently. Verify the provider's current pricing/limits and clearly label free-tier limitations instead of promising free, unlimited inference.

## Google API / OAuth
- Use the official Google API discovery directory where applicable: https://www.googleapis.com/discovery/v1/apis
- Use official Google identity/OAuth documentation: https://developers.google.com/identity/protocols/oauth2
- Use least-privilege scopes; do not assume a Gemini API key is interchangeable with OAuth credentials for other Google APIs.
- Keep OAuth refresh tokens separate from API keys, and never show their raw values in logs or room context.
- Check API enablement, quota, billing requirements, consent-screen status, and redirect URI requirements from current docs.

## Tool calling
Treat model tool calls as untrusted structured input. Parse against a strict schema and allowlist. For shell execution, require an explicit Codein tool protocol; do not execute arbitrary text that merely resembles a tool call. Return bounded stdout/stderr, exit code, and elapsed time; truncate oversized output and retain a local full log with secrets redacted.
