#!/usr/bin/env python3
"""Read-only runtime identity/OpenAPI/Flyway snapshot; no payment commands."""
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile
from datetime import datetime, timezone
from urllib.request import urlopen
from urllib.error import HTTPError
import zipfile

root = Path(__file__).resolve().parents[1]
directory = Path(tempfile.mkdtemp(prefix="logisticsx-runtime-baseline-"))
info = json.loads(subprocess.check_output(["docker", "inspect", "logistics-api"]))[0]
subprocess.run(["docker", "cp", "logistics-api:/app/app.jar", str(directory / "runtime.jar")], check=True, stdout=subprocess.DEVNULL)
raw = urlopen("http://localhost:8080/v3/api-docs", timeout=15).read()
(directory / "openapi.json").write_bytes(raw)
api = json.loads(raw); api.pop("servers", None)
canonical = json.dumps(api, sort_keys=True, separators=(",", ":")).encode()
with zipfile.ZipFile(directory / "runtime.jar") as jar:
    names = jar.namelist()
    packaged_migrations = sorted(name.rsplit("/", 1)[-1] for name in names if name.startswith("BOOT-INF/classes/db/migration/tenant/") and name.endswith(".sql"))
    packaged_metadata = [name for name in names if name.endswith(("build-info.properties", "git.properties"))]
status = {}
for path in ("/api/payments", "/api/customers", "/api/messages", "/api/me"):
    try:
        with urlopen("http://localhost:8080" + path, timeout=15) as response:
            status[path] = response.status
    except HTTPError as error:
        status[path] = error.code
postgres = json.loads(subprocess.check_output(["docker", "inspect", "logistics-postgres"]))[0]
settings = dict(item.split("=", 1) for item in postgres["Config"]["Env"] if "=" in item)
history = subprocess.check_output(["docker", "exec", "logistics-postgres", "psql", "-U", settings.get("POSTGRES_USER", "postgres"),
                                   "-d", "us_logisticsx", "-Atc", "select version, checksum, success from public.flyway_schema_history order by installed_rank"], text=True)
manifest = {"timestamp": datetime.now(timezone.utc).isoformat(), "source_head": subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip(),
            "source_dirty": bool(subprocess.check_output(["git", "status", "--porcelain"])),
            "container": info["Id"], "image_id": info["Image"], "started_at": info["State"]["StartedAt"],
            "jar_sha256": hashlib.sha256((directory / "runtime.jar").read_bytes()).hexdigest(),
            "openapi_raw_sha256": hashlib.sha256(raw).hexdigest(), "openapi_canonical_sha256": hashlib.sha256(canonical).hexdigest(),
            "openapi_paths": len(api["paths"]), "packaged_metadata": packaged_metadata,
            "packaged_migrations": packaged_migrations, "flyway_history": history.splitlines(), "anonymous_status": status,
            "runtime_source_commit": "UNKNOWN", "diagnostics": str(directory)}
destination = root / "docs/verification/remediation-runtime-baseline.json"
destination.write_text(json.dumps(manifest, indent=2) + "\n")
print(destination)
