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

Open **Actions > Android Release > Run workflow**, select the release tag, and set an integer `version_code` greater than the previous release. The workflow builds a signed `app-release.apk`, uploads it as an artifact, and publishes it to the matching GitHub Release.

Pushing a `v*` tag also starts the workflow. The tag becomes `versionName` unless the manual input overrides it.
