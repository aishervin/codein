# Skill: Telegram Bots and Mini Apps

Canonical docs:
- Bot API: https://core.telegram.org/bots/api
- API changelog: https://core.telegram.org/bots/api-changelog
- Bot basics: https://core.telegram.org/bots
- Mini Apps: https://core.telegram.org/bots/webapps
- Telegram Login: https://core.telegram.org/widgets/login
- MTProto: https://core.telegram.org/api
- TDLib: https://core.telegram.org/tdlib

## Workflow
1. Before changing a bot, check the current Bot API docs and changelog for the feature and the minimum supported client/API behavior.
2. Confirm webhook vs long polling, deployment runtime, secret handling, and update types before editing.
3. For messages/media, verify current method signatures, file-size limits, parse modes, caption limits, inline keyboard schema, and edit/copy/forward semantics in the live docs.
4. Handle updates idempotently where feasible; Telegram may retry webhook deliveries. Validate webhook secret tokens where configured.
5. For Mini Apps, validate init data server-side as documented; do not trust client-supplied user identity or init data fields without verification.
6. Use stable callback_data values within documented limits; validate every callback and authorize sensitive actions server-side.
7. Keep bot tokens in the local credential vault or deployment secrets. Never log token-bearing URLs or raw update payloads if they contain secrets or personal data.
8. Test with a private test bot/chat before changing production webhooks, bulk-sending, or editing published channel content.
9. Do not claim Telegram supports a method/field until it is confirmed in the current official Bot API reference.

## Credential names
Use `telegram_bot_token`, `telegram_bot_token_<botname>`, and when needed `telegram_webhook_secret`. For deployments, store platform-specific secret names separately from local credentials.
