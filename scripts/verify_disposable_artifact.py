#!/usr/bin/env python3
"""Start only a verified local JAR against a labelled disposable PostgreSQL server.

Retains databases and private diagnostic logs, stops its own Java processes, and
never restarts an application container. Secrets remain in child environments.
"""
import argparse
import base64
from datetime import datetime, timezone
import hashlib
import hmac
import json
import os
from pathlib import Path
import secrets
import shutil
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.request

from remediation_artifact_identity import artifact_manifest, require_runtime_identity
from verify_backend_regression import migration_versions


def verify_disposable_artifact(manifest_path, container_name):
    expected_versions = migration_versions(Path(__file__).resolve().parents[1])
    manifest = json.loads(manifest_path.read_text())
    artifact_manifest(manifest["jar_path"], manifest)
    if hashlib.sha256(Path(manifest["jar_path"]).read_bytes()).hexdigest() != manifest["jar_sha256"]:
        raise ValueError("Verified JAR hash differs")
    container = json.loads(subprocess.check_output(["docker", "inspect", container_name]))[0]
    if not container_name.startswith("codex-") or (container["Config"].get("Labels") or {}).get("logisticsx.disposable") != "true":
        raise ValueError("Only a labelled disposable PostgreSQL server is allowed")
    bindings = container["NetworkSettings"]["Ports"].get("5432/tcp") or []
    binding = next((item for item in bindings if item["HostIp"] == "127.0.0.1"), None)
    if not binding:
        raise ValueError("Disposable PostgreSQL must have an explicit loopback binding")
    settings = dict(value.split("=", 1) for value in container["Config"]["Env"] if "=" in value)
    user = settings.get("POSTGRES_USER", "postgres")
    password = settings.get("POSTGRES_PASSWORD")
    if not password:
        raise ValueError("Explicit disposable database password is required")
    database = "codex_artifact_" + datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S%f")
    subprocess.run(["docker", "exec", container_name, "createdb", "-U", user, database], check=True)
    diagnostics = Path(tempfile.mkdtemp(prefix="logisticsx-artifact-"))
    diagnostics.chmod(0o700)
    jar = diagnostics / "application.jar"
    shutil.copyfile(manifest["jar_path"], jar)
    manifest["jar_path"] = str(jar)
    private_manifest = diagnostics / "artifact-manifest.json"
    private_manifest.write_text(json.dumps(manifest, indent=2) + "\n")
    print("Disposable database: " + database, flush=True)
    print("Diagnostics: " + str(diagnostics), flush=True)
    environment = {name: os.environ[name] for name in ("PATH", "JAVA_HOME", "LANG", "TZ") if name in os.environ}
    environment.update(DB_URL=f"jdbc:postgresql://127.0.0.1:{binding['HostPort']}/{database}",
                       DB_USERNAME=user, DB_PASSWORD=password, SPRING_CONFIG_IMPORT="",
                       TENANCY_ENABLED="false", LARK_BASE_ENABLED="false",
                       SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE="4",
                       SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE="0")
    signing_key = secrets.token_hex(32)

    def request(port, path, token=None):
        headers = {"Authorization": "Bearer " + token} if token else {}
        req = urllib.request.Request(f"http://127.0.0.1:{port}" + path, headers=headers)
        try:
            response = urllib.request.urlopen(req, timeout=3)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            return response.status, json.loads(response.read())

    def token():
        def encode(value):
            return base64.urlsafe_b64encode(value).rstrip(b"=")
        now = int(time.time())
        claims = {"iss": "https://open.larksuite.com", "sub": "artifact-fixture",
                  "email": "artifact@example.test", "tenant": "artifact-local", "roles": ["ADMIN"],
                  "iat": now, "exp": now + 600}
        content = encode(b'{"alg":"HS512","typ":"JWT"}') + b"." + encode(json.dumps(claims).encode())
        return (content + b"." + encode(hmac.new(signing_key.encode(), content, hashlib.sha512).digest())).decode()

    results = {}
    for mode in ("single-tenant", "nodb", "missing-key"):
        with socket.socket() as allocated:
            allocated.bind(("127.0.0.1", 0))
            port = allocated.getsockname()[1]
        run_env = {**environment, "SERVER_PORT": str(port), "SPRING_PROFILES_ACTIVE": "nodb" if mode == "nodb" else "",
                   "LARK_ENABLED": "false" if mode == "nodb" else "true", "LARK_JWT_SECRET": "" if mode in ("missing-key", "nodb") else signing_key}
        log_path = diagnostics / (mode + ".log")
        with log_path.open("w") as log:
            process = subprocess.Popen(["java", "-jar", str(jar)], cwd=diagnostics, env=run_env,
                                       stdout=log, stderr=subprocess.STDOUT)
            try:
                deadline = time.monotonic() + 90
                ready = False
                while time.monotonic() < deadline and process.poll() is None:
                    if mode != "missing-key":
                        try:
                            ready = request(port, "/health")[0] == 200
                        except (OSError, ValueError):
                            pass
                        if ready:
                            break
                    time.sleep(0.25)
                if mode == "missing-key":
                    if process.poll() is None or process.returncode == 0 or "LARK_JWT_SECRET must be explicitly configured" not in log_path.read_text():
                        raise ValueError("Enabled authentication did not fail startup for a missing signing key; inspect " + str(log_path))
                    results[mode] = {"status": "PASS", "startup": "REJECTED_MISSING_KEY"}
                elif not ready:
                    raise ValueError("Packaged " + mode + " startup failed; inspect " + str(log_path))
                elif mode == "nodb":
                    if request(port, "/api/payments")[0] != 401:
                        raise ValueError("NoDB protected route did not return 401")
                    text = log_path.read_text()
                    if "HikariPool" in text or "Migrating schema" in text or "EntityManagerFactory" in text:
                        raise ValueError("NoDB startup unexpectedly initialized persistence")
                    results[mode] = {"status": "PASS", "health": 200, "protected": 401, "persistence": "DISABLED"}
                else:
                    output = diagnostics / "runtime-verification.json"
                    verifier = Path(__file__).with_name("verify_remediation_runtime.py")
                    subprocess.run(["python3", str(verifier), "--manifest", str(private_manifest),
                                    "--url", f"http://127.0.0.1:{port}", "--output", str(output)],
                                   env={**os.environ, "TASK_RUNTIME_TOKEN": token()}, check=True)
                    results[mode] = json.loads(output.read_text())
                    text = log_path.read_text()
                    if f"Successfully validated {len(expected_versions)} migrations" not in text or "Initialized JPA EntityManagerFactory" not in text:
                        raise ValueError("Packaged PostgreSQL/Flyway/JPA startup evidence missing")
            finally:
                if process.poll() is None:
                    process.terminate()
                    try:
                        process.wait(timeout=15)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait()
    history = subprocess.check_output(["docker", "exec", container_name, "psql", "-U", user, "-d", database,
        "-At", "-v", "ON_ERROR_STOP=1", "-c",
        "SELECT json_agg(row_to_json(h)) FROM (SELECT version,checksum,success FROM flyway_schema_history ORDER BY installed_rank) h"], text=True)
    migrations = json.loads(history)
    if [int(item["version"]) for item in migrations] != expected_versions or not all(item["success"] for item in migrations):
        raise ValueError(f"Packaged database migration history differs from V1–V{expected_versions[-1]}")
    evidence = {"status": "PASS", "verification_scope": "LOCAL_JAR", "deployment_verified": False,
                "database": database, "diagnostics": str(diagnostics), "profiles": results, "flyway_history": migrations}
    (diagnostics / "summary.json").write_text(json.dumps(evidence, indent=2) + "\n")
    print("PASS: packaged PostgreSQL, NoDB, missing-key startup and read-only runtime contract", flush=True)
    return evidence


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--container", required=True)
    args = parser.parse_args()
    try:
        verify_disposable_artifact(args.manifest, args.container)
    except (ValueError, OSError, subprocess.SubprocessError) as error:
        print("FAIL: " + str(error))
        raise SystemExit(1)
