"""Bind a dirty source snapshot to packaged build metadata without reading secrets."""
import hashlib
import json
import re
from pathlib import Path
import subprocess
import uuid
import zipfile


def source_snapshot(root):
    root = Path(root)
    paths = [root / "pom.xml", root / "Dockerfile"] + sorted(
        path for path in (root / "src/main").rglob("*") if path.is_file())
    files = {str(path.relative_to(root)): hashlib.sha256(path.read_bytes()).hexdigest() for path in paths}
    digest = hashlib.sha256(json.dumps(files, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
    commit = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
    dirty = bool(subprocess.check_output(["git", "status", "--porcelain=v1", "-z"], cwd=root))
    return {"source_commit": commit, "source_hash": digest, "dirty": dirty,
            "build_id": "remediation-" + uuid.uuid4().hex, "source_files": files}


def maven_properties(snapshot):
    return ["-Dremediation.source.commit=" + snapshot["source_commit"],
            "-Dremediation.source.hash=" + snapshot["source_hash"],
            "-Dremediation.source.dirty=" + str(snapshot["dirty"]).lower(),
            "-Dremediation.build.id=" + snapshot["build_id"]]


def artifact_manifest(jar, snapshot):
    jar = Path(jar)
    with zipfile.ZipFile(jar) as archive:
        # Boot 4 retains META-INF resources at the archive root. Earlier layouts
        # can put application metadata under BOOT-INF/classes instead.
        metadata_paths = [name for name in ("META-INF/build-info.properties",
            "BOOT-INF/classes/META-INF/build-info.properties") if name in archive.namelist()]
        if len(metadata_paths) != 1:
            raise ValueError("Packaged build metadata is missing or ambiguous")
        metadata = dict(line.split("=", 1) for line in archive.read(
            metadata_paths[0]).decode().splitlines()
            if line.strip() and not line.startswith("#") and "=" in line)
        expected = {"build.source.commit": snapshot["source_commit"], "build.source.hash": snapshot["source_hash"],
                    "build.source.dirty": str(snapshot["dirty"]).lower(), "build.id": snapshot["build_id"]}
        if any(metadata.get(name) != value for name, value in expected.items()):
            raise ValueError("Packaged metadata differs from the verified source snapshot")
        migrations = {name.rsplit("/", 1)[1]: hashlib.sha256(archive.read(name)).hexdigest()
                      for name in archive.namelist() if name.startswith("BOOT-INF/classes/db/migration/tenant/") and name.endswith(".sql")}
        if not migrations:
            raise ValueError("Packaged tenant migrations are missing")
        source_migrations = {name.rsplit("/", 1)[1]: digest for name, digest in snapshot["source_files"].items()
                             if name.startswith("src/main/resources/db/migration/tenant/") and name.endswith(".sql")}
        if migrations != source_migrations:
            raise ValueError("Packaged migration bytes differ from verified source migrations")
    return {**snapshot, "jar_path": str(jar.resolve()), "jar_sha256": hashlib.sha256(jar.read_bytes()).hexdigest(),
            "packaged_build": metadata, "packaged_migrations": migrations,
            "artifact_kind": "LOCAL_JAR", "image_id": None, "deployment_verified": False}


def require_runtime_identity(manifest, response):
    metadata = manifest["packaged_build"]
    expected = {"version": metadata["build.version"], "sourceCommit": manifest["source_commit"],
                "sourceHash": manifest["source_hash"], "buildId": manifest["build_id"], "dirty": manifest["dirty"]}
    if any(response.get(name) != value for name, value in expected.items()):
        raise ValueError("Runtime build identity differs from the manifest")


def require_image_identity(manifest, image, container):
    if not manifest.get("image_id") or image.get("Id") != manifest["image_id"] or container.get("Image") != image.get("Id"):
        raise ValueError("Running image differs from the release manifest")
    labels = image.get("Config", {}).get("Labels") or {}
    expected = {"org.opencontainers.image.revision": manifest["source_commit"],
                "org.opencontainers.image.version": manifest["packaged_build"]["build.version"],
                "io.logisticsx.source.hash": manifest["source_hash"],
                "io.logisticsx.source.dirty": str(manifest["dirty"]).lower(), "io.logisticsx.build.id": manifest["build_id"]}
    if any(labels.get(name) != value for name, value in expected.items()):
        raise ValueError("Required image revision/build metadata is missing or differs")


def require_release_identity(manifest):
    if manifest.get("dirty") is not False or not re.fullmatch(r"[0-9a-f]{40}|[0-9a-f]{64}", manifest.get("source_commit", "")) or not manifest.get("image_id"):
        raise ValueError("Release requires a reviewed clean source commit and an explicit immutable image")
    for field in ("source_hash", "openapi_canonical_sha256"):
        if not re.fullmatch(r"[0-9a-f]{64}", manifest.get(field, "")):
            raise ValueError("Release requires an explicit verified digest: " + field)
