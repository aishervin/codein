"""Codein's server-side Composio Platform client for the local Termux bridge."""
import json
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import urlparse
from urllib.request import Request, urlopen


CONFIG_PATH = Path.home() / ".codein" / "keys" / "composio_platform.json"


class WorkerAuthorizationError(RuntimeError):
    def __init__(self, status):
        self.status = status
        super().__init__("Composio Worker authorization failed. Reconnect the service in Codein.")


def worker_url(value):
    parsed = urlparse(str(value or "").strip().rstrip("/"))
    if parsed.scheme != "https" or not parsed.hostname or not parsed.hostname.endswith(".workers.dev"):
        raise ValueError("Enter the official Codein HTTPS Worker address.")
    if parsed.username or parsed.password or parsed.query or parsed.fragment or parsed.path not in ("", "/"):
        raise ValueError("Worker address must contain only its HTTPS host.")
    return "https://" + parsed.hostname.lower()


def request(base_url, path, token=None, payload=None, timeout=30):
    headers = {"Accept": "application/json", "Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    body = json.dumps(payload or {}).encode("utf-8")
    req = Request(base_url + path, data=body, headers=headers, method="POST")
    try:
        with urlopen(req, timeout=timeout) as response:
            raw = response.read(2_000_000).decode("utf-8", "replace")
    except HTTPError as exc:
        if exc.code in (401, 403):
            raise WorkerAuthorizationError(exc.code) from None
        if exc.code == 429:
            raise RuntimeError("Composio Worker is rate-limiting requests. Try again shortly.") from None
        raise RuntimeError("Composio Worker request failed (HTTP " + str(exc.code) + ").") from None
    except (URLError, TimeoutError):
        raise RuntimeError("Could not reach the Codein Composio Worker.") from None
    try:
        result = json.loads(raw)
    except ValueError:
        raise RuntimeError("Codein Composio Worker returned an invalid response.") from None
    if not isinstance(result, dict):
        raise RuntimeError("Codein Composio Worker returned an invalid response.")
    if result.get("error"):
        raise RuntimeError(str(result["error"])[:500])
    return result


def configure(url):
    base_url = worker_url(url)
    current = read_config()
    reused = current.get("worker_url") == base_url and current.get("token")

    def new_identity():
        identity = request(base_url, "/v1/bootstrap", timeout=20)
        token = str(identity.get("token") or "")
        user_id = str(identity.get("user_id") or "")
        if not token or not user_id.startswith("codein_"):
            raise RuntimeError("Codein Worker could not create an installation identity.")
        return token, user_id

    if reused:
        token = current["token"]
        user_id = current.get("user_id", "")
        session_payload = {"session_id": current.get("session_id")}
        try:
            session = request(base_url, "/v1/session", token, session_payload, timeout=45)
        except WorkerAuthorizationError as error:
            if error.status == 403 and session_payload["session_id"]:
                try:
                    session = request(base_url, "/v1/session", token, {"session_id": None}, timeout=45)
                except WorkerAuthorizationError as retry_error:
                    if retry_error.status != 401:
                        raise
                    token, user_id = new_identity()
                    reused = False
                    session = request(base_url, "/v1/session", token, {"session_id": None}, timeout=45)
            elif error.status == 401:
                token, user_id = new_identity()
                reused = False
                session = request(base_url, "/v1/session", token, {"session_id": None}, timeout=45)
            else:
                raise
    else:
        token, user_id = new_identity()
        session = request(base_url, "/v1/session", token, {"session_id": None}, timeout=45)

    session_id = str(session.get("session_id") or "")
    if not session_id.startswith("trs_"):
        raise RuntimeError("Codein Worker could not create a private Composio session.")
    CONFIG_PATH.parent.mkdir(parents=True, exist_ok=True)
    save_config({"worker_url": base_url, "token": token, "user_id": user_id, "session_id": session_id})
    return {"ok": True, "user_id": user_id, "reused": bool(reused)}


def read_config():
    try:
        value = json.loads(CONFIG_PATH.read_text(encoding="utf-8"))
        if isinstance(value, dict):
            return value
    except (OSError, ValueError):
        pass
    return {}


def save_config(config):
    CONFIG_PATH.parent.mkdir(parents=True, exist_ok=True)
    CONFIG_PATH.write_text(json.dumps(config, separators=(",", ":")) + "\n", encoding="utf-8")
    CONFIG_PATH.chmod(0o600)


def tools(config=None):
    config = config or read_config()
    if not config.get("worker_url") or not config.get("token"):
        return []
    result = request(config["worker_url"], "/v1/tools", config["token"], {"session_id": config.get("session_id")}, timeout=90)
    session_id = str(result.pop("session_id", ""))
    if session_id and session_id != config.get("session_id"):
        config["session_id"] = session_id
        save_config(config)
    return result.get("tools") or []


def call_tool(name, arguments, config=None):
    config = config or read_config()
    if not config.get("worker_url") or not config.get("token"):
        raise RuntimeError("Composio Platform is not configured in Codein.")
    result = request(config["worker_url"], "/v1/execute", config["token"], {
        "session_id": config.get("session_id"),
        "tool_slug": name,
        "arguments": arguments or {},
    }, timeout=120)
    session_id = str(result.pop("session_id", ""))
    if session_id and session_id != config.get("session_id"):
        config["session_id"] = session_id
        save_config(config)
    if result.get("result_type") == "failed":
        return {"error": str(result.get("error") or "Composio tool failed")[:1000]}
    return result.get("data", result)
