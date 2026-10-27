# Skill: Android, Kotlin, Termux and Local Tooling

## Canonical docs
- Android: https://developer.android.com/
- Android API reference: https://developer.android.com/reference
- Jetpack Compose: https://developer.android.com/compose
- Android Gradle build: https://developer.android.com/build
- Kotlin: https://kotlinlang.org/docs/home.html
- Termux package management: https://wiki.termux.com/wiki/Package_Management
- Termux packages source: https://github.com/termux/termux-packages
- Python: https://docs.python.org/3/
- Python subprocess: https://docs.python.org/3/library/subprocess.html
- Python urllib: https://docs.python.org/3/library/urllib.html
- Git: https://git-scm.com/docs
- curl: https://curl.se/docs/
- OpenSSH: https://www.openssh.com/manual.html
- MCP: https://modelcontextprotocol.io/specification/latest
- OpenAPI: https://spec.openapis.org/oas/latest.html

## Dependency policy
Keep the bridge small and compatible with Termux. The initial bridge uses Python standard library only. Base Termux packages are `python`, `git`, `openssh`, and `curl`; install `clang`, `make`, `cmake`, `nodejs-lts`, `jq`, or `ripgrep` only when a task requires them. Do not install a large generic dependency bundle on every device.

For Python, prefer standard-library modules (`json`, `pathlib`, `subprocess`, `urllib`, `hashlib`, `hmac`, `datetime`, `logging`) before adding PyPI packages. If a package is needed, check active Python/Termux compatibility and pin a tested version only when reproducibility requires it.

For Android, follow the project's existing Gradle wrapper, Kotlin/Compose versions, SDK levels, and architecture. Check official migration notes before changing major versions. Do not add libraries merely for convenience if platform APIs or existing dependencies already solve the task.

## Local runtime constraints
- Bind the bridge to loopback by default. Do not expose it on all interfaces without an explicit user request and an authentication/network plan.
- Android/Termux permissions still apply; no implicit root access, hidden background execution, or permission bypass.
- Capture process exit status and bounded output. Add timeouts and cancellation.
- Avoid shell-string concatenation for user-provided values; use argument arrays where possible. If a shell is necessary, quote values and validate paths.
- Never store runtime secrets or generated config in the repository.
