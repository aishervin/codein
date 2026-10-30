# Codein Releases

The application ID stays `com.aistudio.dphnchat.kqmvzx`, so a signed release APK can update an existing installation.

## One-time GitHub setup

Create these repository Actions secrets:

- `RELEASE_KEYSTORE_BASE64`: base64 of the permanent `.jks` file
- `RELEASE_STORE_PASSWORD`: keystore password
- `RELEASE_KEY_ALIAS`: key alias
- `RELEASE_KEY_PASSWORD`: key password

For the first secret, encode the permanent key once with `base64 -w0 codein-release.jks` and paste the single-line result into GitHub. Keep the original `.jks` in a safe backup.

Never generate a new release key inside CI. Losing or replacing the permanent key prevents updates over an installed APK.

## Release

Before publishing the public Composio integration, deploy the Worker from the repository root with `npx wrangler deploy`. Android builds default to `https://codein.shen-zero.workers.dev`; set the GitHub Actions repository variable `COMPOSIO_WORKER_URL` only to override that address. The project key stays in the Worker secret and is never passed to Android builds.

Open **Actions > Android Release > Run workflow**, select the release tag, and set an integer `version_code` greater than the previous release. The v1.3.0 replacement build keeps version name `1.3.0` and uses version code `23`. The workflow builds a signed `app-release.apk`, uploads it as an artifact, and replaces the matching GitHub Release APK.

Pushing a `v*` tag also starts the workflow. The tag becomes `versionName` unless the manual input overrides it. Editing the workflow on `main` does not trigger a release build.
