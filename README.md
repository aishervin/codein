# Codein

**SHΞN™ Coder** is a lightweight Android Compose client with a black neo-morphic interface, thin neon-orange borders, model presets, prompt studio, and streaming chat responses.

The app keeps the existing application ID `com.aistudio.dphnchat.kqmvzx` so a correctly signed release can update an installed build. The chat page runs inside a small, non-visible WebView and the custom Compose UI uses its same-origin session to stream `/api/chat` responses. The service endpoint is an internal site endpoint and may change independently of this app.

## Local build

```bash
./gradlew assembleDebug
```

For a signed release, set `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` before running `assembleRelease`.

## GitHub releases

The `Android Release` workflow supports both `v*` tag pushes and manual releases. It requires the four stable signing secrets documented in [`docs/release.md`](docs/release.md). The workflow never generates a replacement release key.
