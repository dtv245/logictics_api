#!/usr/bin/env python3
"""Targeted Maven checks on an explicitly identified disposable test server."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import tempfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--container", required=True)
parser.add_argument("--tests")
parser.add_argument("--database", required=True)
args = parser.parse_args()
if not args.container.startswith("codex-remediation-") or not args.database.startswith("codex_"):
    parser.error("Only explicitly disposable remediation servers/databases are allowed")
info = json.loads(subprocess.check_output(["docker", "inspect", args.container]))[0]
if info["Config"]["Labels"].get("logisticsx.disposable") != "true":
    parser.error("Disposable server identity label is missing")
settings = dict(item.split("=", 1) for item in info["Config"]["Env"] if "=" in item)
port = info["NetworkSettings"]["Ports"]["5432/tcp"][0]["HostPort"]
environment = os.environ.copy()
environment.update(TASK_DB_URL=f"jdbc:postgresql://localhost:{port}/{args.database}",
                   TASK_DB_USER=settings.get("POSTGRES_USER", "postgres"),
                   TASK_DB_PASSWORD=settings["POSTGRES_PASSWORD"],
                   SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE="4",
                   SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE="0")
root = Path(__file__).resolve().parents[1]
diagnostics = Path(tempfile.mkdtemp(prefix="logisticsx-remediation-tests-"))
command = [str(root / "mvnw"), "-q"]
if args.tests:
    command += ["-Dtest=" + args.tests, "test"]
else:
    command += ["clean", "verify"]
print(f"Diagnostics: {diagnostics}", flush=True)
with (diagnostics / "maven.log").open("w") as log:
    result = subprocess.run(command, cwd=root, env=environment, stdout=log, stderr=subprocess.STDOUT)
print(f"Maven exit: {result.returncode}", flush=True)
raise SystemExit(result.returncode)
