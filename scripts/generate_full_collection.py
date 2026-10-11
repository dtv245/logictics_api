import json
import uuid

def req_item(name, method, url_path, description, body_json=None, pre_script="", test_script="", query_params=None, headers=None, auth_noauth=False, follow_redirects=True):
    # build postman request item
    header_list = []
    if headers:
        for k, v in headers.items():
            header_list.append({"key": k, "value": v, "type": "text"})
    if body_json is not None:
        header_list.append({"key": "Content-Type", "value": "application/json", "type": "text"})

    url_parts = url_path.strip("/").split("/")
    url_obj = {
        "raw": "{{baseUrl}}/" + url_path.lstrip("/"),
        "host": ["{{baseUrl}}"],
        "path": url_parts
    }
    if query_params:
        qp_list = []
        raw_query = []
        for k, v in query_params.items():
            qp_list.append({"key": k, "value": str(v)})
            raw_query.append(f"{k}={v}")
        url_obj["query"] = qp_list
        url_obj["raw"] += "?" + "&".join(raw_query)

    req_obj = {
        "method": method.upper(),
        "header": header_list,
        "url": url_obj,
        "description": description
    }
    if body_json is not None:
        req_obj["body"] = {
            "mode": "raw",
            "raw": json.dumps(body_json, indent=2) if isinstance(body_json, (dict, list)) else str(body_json),
            "options": {
                "raw": {
                    "language": "json"
                }
            }
        }

    events = []
    if pre_script:
        events.append({
            "listen": "prerequest",
            "script": {
                "type": "text/javascript",
                "exec": [line for line in pre_script.strip().split("\n")]
            }
        })
    if test_script:
        events.append({
            "listen": "test",
            "script": {
                "type": "text/javascript",
                "exec": [line for line in test_script.strip().split("\n")]
            }
        })

    item = {
        "name": name,
        "request": req_obj,
        "response": []
    }
    if events:
        item["event"] = events
    if auth_noauth:
        item["request"]["auth"] = {
            "type": "noauth"
        }
    if not follow_redirects:
        item["protocolProfileBehavior"] = {
            "followRedirects": False
        }
    return item

print("Helper defined.")
