# Codein Termux Dependency and Library Policy

## Default baseline (installed by bootstrap)
- `python`: bridge/runtime, API calls, JSON and local automation. Initial bridge uses only Python standard library.
- `git`: clone, branch, diff, commit and repository inspection.
- `openssh`: SSH workflows where configured.
- `curl`: official API calls, downloads, and current documentation retrieval.

## Python standard library first
Prefer `json`, `pathlib`, `subprocess`, `urllib.request`, `urllib.parse`, `hashlib`, `hmac`, `secrets`, `datetime`, `logging`, `sqlite3`, `concurrent.futures`, and `unittest`. These avoid native-build problems and reduce setup time in Termux.

## Optional packages — install only when a task needs them
| Package | Use when needed | Official/source reference |
| --- | --- | --- |
| `jq` | Inspect/filter JSON from shell pipelines | https://jqlang.org/manual/ |
| `ripgrep` (`rg`) | Fast source-code search | https://github.com/BurntSushi/ripgrep |
| `nodejs-lts` + npm | JS/TS projects, Wrangler, frontend builds | https://nodejs.org/docs/latest/api/ |
| `clang`, `make`, `cmake`, `pkg-config` | Native modules or compilation required by a project | https://github.com/termux/termux-packages |
| `sqlite` | CLI inspection of SQLite databases | https://www.sqlite.org/docs.html |
| Python `requests` | Only if existing code requires requests-specific ergonomics; stdlib urllib is the default | https://requests.readthedocs.io/en/latest/ |
| Python `httpx` | Async HTTP client when concurrency/streaming needs justify it | https://www.python-httpx.org/ |
| Python `PyYAML` | YAML parsing when required by a real project | https://pyyaml.org/wiki/PyYAMLDocumentation |
| Python `jsonschema` | Runtime JSON schema validation for untrusted tool arguments | https://python-jsonschema.readthedocs.io/ |

## Android app dependencies
Do not duplicate libraries listed in Gradle. Inspect `gradle/libs.versions.toml`, module build files, and the Gradle wrapper before editing. Prefer existing Kotlin coroutines, OkHttp/Retrofit (if already present), Kotlin serialization/Moshi/Gson (whichever is already used), and Jetpack Compose components. Add a new library only when the existing stack cannot solve the task cleanly. Verify current version compatibility from official project documentation and Android/Kotlin release notes before upgrades.

## Model and integration adapters
- Keep provider adapters small and provider-neutral.
- Use official vendor SDKs only if they are maintained, supported on the target runtime, and materially reduce implementation risk.
- Termux is the integration hub; prefer direct HTTP APIs using curl or Python standard library where practical.
- Never install every optional package as a blanket bootstrap step.
- Record why a new dependency is needed, its official docs, minimum supported runtime, license, and test result in the project event journal.

## Documentation
- Termux package manager: https://wiki.termux.com/wiki/Package_Management
- Termux package sources: https://github.com/termux/termux-packages
- Python docs: https://docs.python.org/3/
- Android docs: https://developer.android.com/
- Kotlin docs: https://kotlinlang.org/docs/home.html
