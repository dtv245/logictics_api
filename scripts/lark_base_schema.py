#!/usr/bin/env python3
"""Read-only sandbox metadata export and offline validation against the sole mapping catalog."""
import argparse
import json
import os
from pathlib import Path
import sys
from urllib.request import Request, urlopen
from urllib.parse import urlencode

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "src/main/resources/lark-base-mapping.json"

def placeholder(value):
    text = (value or "").strip().lower()
    return not text or any(part in text for part in ("xxxx", "your-", "your_", "placeholder", "changeme", "${"))

def environment(path):
    result = {}
    if path.exists():
        for line in path.read_text().splitlines():
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                result[key.strip()] = value.strip().strip("\"'")
    result.update(os.environ)
    return result

def catalog():
    return json.loads(CATALOG.read_text())["domains"]

def check_config(config):
    if config.get("LARK_BASE_ENVIRONMENT") != "sandbox":
        raise ValueError("LARK_BASE_SANDBOX_REQUIRED")
    required = ["LARK_APP_ID", "LARK_APP_SECRET", "LARK_BASE_APP_TOKEN"]
    required += [value["tableEnv"] for value in catalog().values()]
    for key in required:
        if placeholder(config.get(key)):
            raise ValueError("LARK_BASE_CONFIG_REQUIRED:" + key)

def fetch_json(url, token=None, body=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = None if body is None else json.dumps(body).encode()
    with urlopen(Request(url, data=data, headers=headers), timeout=15) as response:
        value = json.load(response)
    if value.get("code") != 0:
        raise ValueError("LARK_SCHEMA_PROVIDER_REJECTED")
    return value

def pages(url, token):
    page = None
    while True:
        query = {"page_size": 100}
        if page:
            query["page_token"] = page
        data = fetch_json(url + "?" + urlencode(query), token)["data"]
        yield from data.get("items", [])
        if not data.get("has_more"):
            return
        next_page = data.get("page_token")
        if not next_page or next_page == page:
            raise ValueError("LARK_SCHEMA_PAGINATION_INVALID")
        page = next_page

def sanitize_field(field):
    properties = field.get("property") or {}
    return {"name": field["field_name"], "type": field["type"], "ui_type": field.get("ui_type"),
            "required": field.get("required", properties.get("required")),
            "options": [item["name"] for item in properties.get("options", [])],
            "date_formatter": properties.get("date_formatter")}

def export_schema(config):
    check_config(config)  # Before any network request, including authentication.
    base = config.get("LARK_BASE_URL", "https://open.larksuite.com").rstrip("/")
    if base != "https://open.larksuite.com":
        raise ValueError("LARK_SCHEMA_OFFICIAL_HOST_REQUIRED")
    credential = fetch_json(base + "/open-apis/auth/v3/tenant_access_token/internal",
                            body={"app_id": config["LARK_APP_ID"], "app_secret": config["LARK_APP_SECRET"]})
    token = credential.get("tenant_access_token")
    if not token:
        raise ValueError("LARK_SCHEMA_AUTH_FAILED")
    app = config["LARK_BASE_APP_TOKEN"]
    tables = {item["table_id"]: item for item in pages(base + "/open-apis/bitable/v1/apps/" + app + "/tables", token)}
    domains = {}
    for domain, definition in catalog().items():
        table = config[definition["tableEnv"]]
        if table not in tables:
            raise ValueError("LARK_SCHEMA_TABLE_NOT_FOUND:" + domain)
        fields = list(pages(base + "/open-apis/bitable/v1/apps/" + app + "/tables/" + table + "/fields", token))
        domains[domain] = {"name": tables[table]["name"], "fields": [sanitize_field(field) for field in fields]}
    return {"source": "sandbox_metadata_export", "domains": domains}

def validate(schema, config):
    errors = []
    warnings = []
    for domain, definition in catalog().items():
        actual = {f["name"]: f for f in schema.get("domains", {}).get(domain, {}).get("fields", [])}
        mapped = set()
        for field in definition["fields"]:
            name = config.get(field["fieldEnv"], "").strip()
            if not name:
                errors.append("CONFIG_REQUIRED:" + field["fieldEnv"])
                continue
            if name in mapped:
                errors.append(domain + ":DUPLICATE_MAPPING:" + name)
            mapped.add(name)
            found = actual.get(name)
            if found is None:
                errors.append(domain + ":FIELD_MISSING:" + name)
                continue
            if found["type"] != field["type"]:
                errors.append(domain + ":TYPE_MISMATCH:" + name)
            missing = set(field["options"]) - set(found.get("options", []))
            if missing:
                errors.append(domain + ":OPTIONS_MISSING:" + name + ":" + ",".join(sorted(missing)))
            if found.get("required") is None:
                warnings.append(domain + ":BASE_REQUIRED_CONSTRAINT_UNKNOWN:" + name)
        for name, field in actual.items():
            if name not in mapped:
                errors.append(domain + (":REQUIRED_UNMAPPED:" if field.get("required") else ":UNOWNED_FIELD:") + name)
    return {"valid": not errors, "errors": errors, "warnings": warnings,
            "note": "Metadata may omit required/form constraints; verify those in the sandbox UI. No data written."}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["export", "validate"])
    parser.add_argument("--env-file", type=Path, default=ROOT / ".env")
    parser.add_argument("--schema", type=Path, default=ROOT / "docs/lark-base-schema.sanitized.json")
    parser.add_argument("--confirm-sandbox", action="store_true")
    args = parser.parse_args()
    try:
        config = environment(args.env_file)
        if args.command == "export":
            if not args.confirm_sandbox:
                raise ValueError("LARK_SCHEMA_EXPLICIT_SANDBOX_CONFIRMATION_REQUIRED")
            result = export_schema(config)
            args.schema.parent.mkdir(parents=True, exist_ok=True)
            args.schema.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
            for domain, table in result["domains"].items():
                for field in table["fields"]:
                    print(domain + ": " + field["name"] + " | " + str(field["type"]))
            return 0
        if not args.schema.exists():
            raise ValueError("LARK_SCHEMA_FILE_REQUIRED")
        result = validate(json.loads(args.schema.read_text()), config)
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 0 if result["valid"] else 1
    except ValueError as error:
        # Only our symbolic errors can be printed; never raw URLs/provider responses or credentials.
        message = str(error)
        print(message if message.startswith(("LARK_SCHEMA_", "LARK_BASE_")) and len(message) < 160
              else "LARK_SCHEMA_INPUT_INVALID", file=sys.stderr)
        return 1
    except Exception:
        print("LARK_SCHEMA_EXPORT_OR_VALIDATION_FAILED", file=sys.stderr)
        return 1

if __name__ == "__main__":
    raise SystemExit(main())

