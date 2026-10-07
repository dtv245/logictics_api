#!/usr/bin/env python3
"""Preserve the dirty workspace; emit hashes, never file contents or secrets."""
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import subprocess
import tarfile
import tempfile

root = Path(__file__).resolve().parents[1]
diagnostics = Path(tempfile.mkdtemp(prefix="logisticsx-remediation-baseline-"))
status = subprocess.check_output(["git", "status", "--porcelain=v1", "-z", "--untracked-files=all"], cwd=root)
(diagnostics / "git-status.z").write_bytes(status)
(diagnostics / "tracked.patch").write_bytes(subprocess.check_output(["git", "diff", "--binary", "HEAD"], cwd=root))
paths = subprocess.check_output(["git", "ls-files", "-m", "-o", "--exclude-standard", "-z"], cwd=root).decode().split("\0")
hashes = {}
with tarfile.open(diagnostics / "workspace.tar.gz", "w:gz") as archive:
    for name in sorted(set(paths) - {""}):
        path = root / name
        if path.is_file():
            archive.add(path, arcname=name, recursive=False)
            hashes[name] = hashlib.sha256(path.read_bytes()).hexdigest()
manifest = {"timestamp": datetime.now(timezone.utc).isoformat(),
            "head": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip(),
            "branch": subprocess.check_output(["git", "branch", "--show-current"], cwd=root, text=True).strip(),
            "sha256": hashes}
(diagnostics / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
print(diagnostics)
