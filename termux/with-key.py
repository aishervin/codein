#!/usr/bin/env python3
"""Load one named Codein vault credential into CODEIN_SECRET, then exec a command."""
import json
import os
import sys
from pathlib import Path

if len(sys.argv) < 4 or sys.argv[2] != "--":
    raise SystemExit("Usage: codein-with-key <credential_name> -- <command> [args...]")
name = sys.argv[1]
if not name or "/" in name or "\\" in name:
    raise SystemExit("Invalid credential name")
path = Path.home() / ".codein" / "keys" / (name + ".json")
try:
    record = json.loads(path.read_text(encoding="utf-8"))
    secret = str(record["value"])
except (OSError, ValueError, KeyError) as exc:
    raise SystemExit(f"Credential '{name}' is not available in the local vault: {exc}")
if not secret:
    raise SystemExit(f"Credential '{name}' is empty")
env = os.environ.copy()
env["CODEIN_SECRET"] = secret
os.execvpe(sys.argv[3], sys.argv[3:], env)
