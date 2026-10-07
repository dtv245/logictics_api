#!/usr/bin/env python3
"""Read-only artifact/runtime verifier; ADMIN token comes from TASK_RUNTIME_TOKEN.

LOCAL_JAR mode verifies a disposable executable. --release additionally requires
an immutable image from a reviewed clean commit and image/container inspection.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import urllib.error
import urllib.parse
import urllib.request
try:
    from scripts.remediation_artifact_identity import artifact_manifest, require_runtime_identity, require_image_identity, require_release_identity
except ModuleNotFoundError:
    from remediation_artifact_identity import artifact_manifest, require_runtime_identity, require_image_identity, require_release_identity


def request(base, path, token=None, parse=True):
    headers = {"Authorization": "Bearer " + token} if token else {}
    req = urllib.request.Request(base.rstrip("/") + path, headers=headers)
    try:
        response = urllib.request.urlopen(req, timeout=30)
    except urllib.error.HTTPError as error:
        response = error
    with response:
        body = response.read() if parse else b""
        return response.status, json.loads(body) if body else None


def canonical_openapi(api):
    copy = json.loads(json.dumps(api))
    copy.pop("servers", None)
    for path in copy.get("paths", {}).values():
        path.pop("servers", None)
        for operation in path.values():
            if isinstance(operation, dict):
                operation.pop("servers", None)
    return json.dumps(copy, sort_keys=True, separators=(",", ":")).encode()


def require_contract(api):
    for path, operation in (("/api/payments/{id}/cancel", "post"), ("/api/payments/{id}", "delete"), ("/api/internal/build", "get")):
        if operation not in api.get("paths", {}).get(path, {}):
            raise ValueError("Required runtime operation is missing: " + operation + " " + path)
    schemas = api.get("components", {}).get("schemas", {})
    for schema, fields in {"CreatePaymentRequest": {"invoiceId", "idempotencyKey"},
                           "UpdateLoadRequest": {"expectedVersion"}, "UpdateTripRequest": {"expectedVersion"},
                           "UpdateTruckRequest": {"expectedVersion"}}.items():
        if not fields.issubset(set(schemas.get(schema, {}).get("required", []))):
            raise ValueError("Required runtime DTO fields are missing: " + schema)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", required=True, type=Path)
    parser.add_argument("--url", required=True)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--container", help="Inspect an application container read-only")
    parser.add_argument("--release", action="store_true")
    args = parser.parse_args()
    try:
        manifest = json.loads(args.manifest.read_text())
        if args.release:
            require_release_identity(manifest)
            if not args.container:
                raise ValueError("Release verification requires an explicit running container")
        artifact_manifest(manifest["jar_path"], manifest)
        if hashlib.sha256(Path(manifest["jar_path"]).read_bytes()).hexdigest() != manifest["jar_sha256"]:
            raise ValueError("JAR hash differs from the verified manifest")
        if args.container:
            container = json.loads(subprocess.check_output(["docker", "inspect", args.container]))[0]
            image = json.loads(subprocess.check_output(["docker", "image", "inspect", container["Image"]]))[0]
            require_image_identity(manifest, image, container)
        token = os.environ.get("TASK_RUNTIME_TOKEN")
        if not token:
            raise ValueError("An explicit ADMIN token in TASK_RUNTIME_TOKEN is required")
        status, build = request(args.url, "/api/internal/build", token)
        if status != 200 or not build.get("success"):
            raise ValueError("Authenticated runtime build metadata is unavailable")
        require_runtime_identity(manifest, build["data"])
        for path in ("/api/payments", "/api/customers", "/api/messages", "/api/internal/build"):
            status, _ = request(args.url, path, parse=False)
            if status != 401:
                raise ValueError("Anonymous protected endpoint did not reject 401: " + path)
        status, api = request(args.url, "/v3/api-docs")
        if status != 200:
            raise ValueError("Runtime OpenAPI is unavailable")
        require_contract(api)
        canonical_hash = hashlib.sha256(canonical_openapi(api)).hexdigest()
        if manifest.get("openapi_canonical_sha256") and canonical_hash != manifest["openapi_canonical_sha256"]:
            raise ValueError("Runtime OpenAPI differs from the approved artifact contract")
        evidence = {"status": "PASS", "verification_scope": "RELEASE_ARTIFACT" if args.release else "LOCAL_JAR",
                    "jar_sha256": manifest["jar_sha256"], "runtime_build": build["data"],
                    "openapi_canonical_sha256": canonical_hash, "openapi_paths": len(api["paths"]),
                    "anonymous_protected_status": 401, "artifact_runtime_verified": True,
                    # Per-tenant schema, clients and full release readiness are separate gates.
                    "deployment_verified": False}
        args.output.write_text(json.dumps(evidence, indent=2) + "\n")
        print(json.dumps(evidence, indent=2))
        return 0
    except (ValueError, KeyError, OSError, subprocess.CalledProcessError) as error:
        print("FAIL: " + str(error))
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
