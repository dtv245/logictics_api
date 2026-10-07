#!/usr/bin/env python3
"""Run regression on a dedicated PostgreSQL 16 server; retain diagnostics."""
import json
import os
from pathlib import Path
import secrets
import subprocess
import time
import uuid

root = Path(__file__).resolve().parents[1]
name = "codex-remediation-" + uuid.uuid4().hex[:12]
environment = os.environ.copy()
environment["POSTGRES_PASSWORD"] = secrets.token_hex(32)
subprocess.run(["docker", "run", "--detach", "--name", name,
                "--label", "logisticsx.disposable=true", "--publish", "127.0.0.1::5432",
                "--env", "POSTGRES_PASSWORD", "postgres:16-alpine"], env=environment,
               check=True, stdout=subprocess.DEVNULL)
info = json.loads(subprocess.check_output(["docker", "inspect", name]))[0]
port = info["NetworkSettings"]["Ports"]["5432/tcp"][0]["HostPort"]
print(f"Isolated server: {name}; loopback port: {port}", flush=True)
deadline = time.monotonic() + 30
while subprocess.run(["docker", "exec", name, "pg_isready", "-U", "postgres"],
                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode:
    if time.monotonic() > deadline:
        raise RuntimeError("Isolated PostgreSQL did not become ready; container retained")
    time.sleep(0.2)
result = subprocess.run(["python3", str(root / "scripts/verify_backend_regression.py"),
                         "--container", name, "--port", port], cwd=root)
print(f"Server retained for diagnostics: {name}", flush=True)
raise SystemExit(result.returncode)
