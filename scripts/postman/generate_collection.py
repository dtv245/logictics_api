#!/usr/bin/env python3
"""Generate the importable Postman collection and local environment."""

from __future__ import annotations

from copy import deepcopy
import json
from pathlib import Path
from typing import Any


OUTPUT_DIR = Path(__file__).resolve().parent
COLLECTION_FILE = OUTPUT_DIR / "logicstic-api.postman_collection.json"
ENVIRONMENT_FILE = OUTPUT_DIR / "logicstic-local.postman_environment.json"


def script_event(listen: str, lines: list[str]) -> dict[str, Any]:
    return {
        "listen": listen,
        "script": {"type": "text/javascript", "packages": {}, "exec": lines},
    }


def skip_unless(variable: str, reason: str) -> list[str]:
    return [
        f'if (pm.environment.get("{variable}") !== "true") {{',
        f'  console.warn("{reason}");',
        "  if (pm.execution && pm.execution.skipRequest) {",
        "    pm.execution.skipRequest();",
        "  } else {",
        f'    throw new Error("{reason}");',
        "  }",
        "}",
    ]


def require_variables(*variables: str) -> list[str]:
    quoted = ", ".join(f'"{variable}"' for variable in variables)
    return [
        f"const requiredVariables = [{quoted}];",
        "const missingVariables = requiredVariables.filter(",
        "  variable => !pm.environment.get(variable)",
        ");",
        "if (missingVariables.length > 0) {",
        '  const reason = "Missing environment variable(s): " + missingVariables.join(", ");',
        "  console.warn(reason);",
        "  if (pm.execution && pm.execution.skipRequest) {",
        "    pm.execution.skipRequest();",
        "  } else {",
        "    throw new Error(reason);",
        "  }",
        "}",
    ]


def expected_status(status: int) -> list[str]:
    return [
        f'pm.test("Status is {status}", function () {{',
        f"  pm.response.to.have.status({status});",
        "});",
    ]


def save_id(variable: str) -> list[str]:
    return [
        "if (pm.response.code >= 200 && pm.response.code < 300) {",
        "  const response = pm.response.json();",
        "  if (response.data && response.data.id) {",
        f'    pm.environment.set("{variable}", response.data.id);',
        "  }",
        "}",
    ]


def save_first_id(variable: str) -> list[str]:
    return [
        "if (pm.response.code === 200) {",
        "  const response = pm.response.json();",
        "  const items = response.data && response.data.items;",
        f'  if (items && items.length > 0 && !pm.environment.get("{variable}")) {{',
        f'    pm.environment.set("{variable}", items[0].id);',
        "  }",
        "}",
    ]


def url(
    base_variable: str, path: str, query: list[tuple[str, str, bool]] | None = None
) -> dict[str, Any]:
    query = query or []
    enabled_query = [
        f"{key}={value}" for key, value, enabled in query if enabled
    ]
    raw = f"{{{{{base_variable}}}}}{path}"
    if enabled_query:
        raw += "?" + "&".join(enabled_query)
    result: dict[str, Any] = {
        "raw": raw,
        "host": [f"{{{{{base_variable}}}}}"],
        "path": [part for part in path.split("/") if part],
    }
    if query:
        result["query"] = [
            {
                "key": key,
                "value": value,
                **({"disabled": True} if not enabled else {}),
            }
            for key, value, enabled in query
        ]
    return result


def request(
    name: str,
    method: str,
    path: str,
    *,
    body: dict[str, Any] | None = None,
    query: list[tuple[str, str, bool]] | None = None,
    status: int = 200,
    save: str | None = None,
    save_first: str | None = None,
    description: str = "",
    no_auth: bool = False,
    base_variable: str = "baseUrl",
    prerequest: list[str] | None = None,
    tests: list[str] | None = None,
    formdata: list[dict[str, Any]] | None = None,
) -> dict[str, Any]:
    headers = [
        {"key": "Accept", "value": "application/json"},
        {"key": "X-Request-Id", "value": "{{requestId}}"},
    ]
    request_body = None
    if body is not None:
        headers.append({"key": "Content-Type", "value": "application/json"})
        request_body = {
            "mode": "raw",
            "raw": json.dumps(body, ensure_ascii=False, indent=2),
            "options": {"raw": {"language": "json"}},
        }
    elif formdata is not None:
        request_body = {"mode": "formdata", "formdata": formdata}

    item: dict[str, Any] = {
        "name": name,
        "request": {
            "method": method,
            "header": headers,
            "url": url(base_variable, path, query),
            "description": description,
        },
        "response": [],
    }
    if no_auth:
        item["request"]["auth"] = {"type": "noauth"}
    if request_body is not None:
        item["request"]["body"] = request_body

    events = []
    if prerequest:
        events.append(script_event("prerequest", prerequest))
    test_lines = expected_status(status)
    if save:
        test_lines += save_id(save)
    if save_first:
        test_lines += save_first_id(save_first)
    if tests:
        test_lines += tests
    events.append(script_event("test", test_lines))
    item["event"] = events
    return item


def json_body(**values: Any) -> dict[str, Any]:
    return values


def flow_body(value: Any) -> Any:
    """Use a run-scoped identifier instead of Postman's second-resolution timestamp."""
    if isinstance(value, str):
        return value.replace("{{$timestamp}}", "{{flowRunId}}")
    if isinstance(value, list):
        return [flow_body(item) for item in value]
    if isinstance(value, dict):
        return {key: flow_body(item) for key, item in value.items()}
    return value


def flow_cleanup(name: str, path: str, identifier: str) -> dict[str, Any]:
    return request(
        name,
        "DELETE",
        path,
        description=(
            "Deletes only the resource created by this flow. "
            "Set cleanupAfterRun=false to inspect generated data."
        ),
        prerequest=[
            'const cleanupEnabled = pm.environment.get("cleanupAfterRun") === "true";',
            f'const generatedId = pm.environment.get("{identifier}");',
            "if (!cleanupEnabled || !generatedId) {",
            f'  console.log("Skipping {name}: cleanup disabled or {identifier} is empty");',
            "  pm.execution.skipRequest();",
            "}",
        ],
        tests=[
            "if (pm.response.code === 200) {",
            f'  pm.environment.unset("{identifier}");',
            "}",
        ],
    )


PAGING = [("page", "{{page}}", True), ("pageSize", "{{pageSize}}", True)]
SORT_NAME = PAGING + [("orderBy", "name", True), ("descending", "false", True)]


role_body = json_body(
    name="postman_role_{{$timestamp}}",
    displayName="Postman API Tester",
    claims=[
        {"claimType": "permission", "claimValue": "load:read"},
        {"claimType": "permission", "claimValue": "load:write"},
    ],
)
customer_body = json_body(
    name="Postman Customer {{$timestamp}}",
    email="postman+{{$timestamp}}@example.com",
    phone="+84900000001",
    status="active",
    notes="Created by the Postman collection",
    taxId="POSTMAN-{{$timestamp}}",
    isVatExempt=False,
    addressLine1="2 Dock Road",
    addressLine2=None,
    addressCity="Hai Phong",
    addressState="HP",
    addressZipCode="18000",
    addressCountry="VN",
)
employee_body = json_body(
    email="postman.employee+{{$timestamp}}@example.com",
    firstName="Postman",
    lastName="Driver",
    phoneNumber="+84900000000",
    salaryType="HOURLY",
    status="ACTIVE",
    joinedDate="{{now}}",
    roleId="{{roleId}}",
    salaryAmount=25.00,
    salaryCurrency="USD",
    addressLine1="1 Main Street",
    addressLine2=None,
    addressCity="Ha Noi",
    addressState="HN",
    addressZipCode="10000",
    addressCountry="VN",
)
terminal_body = json_body(
    name="Postman Terminal {{$timestamp}}",
    code="{{terminalCode}}",
    countryCode="US",
    type="SEA_PORT",
    notes="Created by the Postman collection",
    addressLine1="1 Terminal Way",
    addressLine2=None,
    addressCity="Newark",
    addressState="NJ",
    addressZipCode="07114",
    addressCountry="US",
)
truck_body = json_body(
    number="PM-{{$timestamp}}",
    type="freight_truck",
    vehicleCapacity=1,
    status="available",
    make="Volvo",
    model="FH16",
    year=2024,
    vin=None,
    licensePlate="51C-12345",
    licensePlateState="HN",
    isHazmatPlacarded=False,
    mainDriverId="{{employeeId}}",
    secondaryDriverId=None,
    adrEquipmentIsAdrCertified=False,
    adrEquipmentAllowedClasses=None,
    adrEquipmentOrangePlateNumber=None,
)
load_body = json_body(
    name="Postman Shipment {{$timestamp}}",
    type="general_freight",
    status="draft",
    distance=120.5,
    isInProximity=False,
    customerId="{{customerId}}",
    assignedTruckId="{{truckId}}",
    assignedDispatcherId="{{employeeId}}",
    source="manual",
    requestedPickupDate="{{pickupAt}}",
    requestedDeliveryDate="{{deliveryAt}}",
    notes="Created by the Postman collection",
    isHazmat=False,
    hazmatClass=None,
    unNumber=None,
    containerId=None,
    originTerminalId="{{terminalId}}",
    destinationTerminalId="{{terminalId}}",
    externalSourceProvider=None,
    externalSourceId=None,
    externalBrokerReference=None,
    deliveryCostAmount=1500.00,
    deliveryCostCurrency="USD",
    originAddressLine1="1 Origin Street",
    originAddressLine2=None,
    originAddressCity="Ha Noi",
    originAddressState="HN",
    originAddressZipCode="10000",
    originAddressCountry="VN",
    originLocationLatitude=21.0,
    originLocationLongitude=105.8,
    destinationAddressLine1="2 Destination Street",
    destinationAddressLine2=None,
    destinationAddressCity="Hai Phong",
    destinationAddressState="HP",
    destinationAddressZipCode="18000",
    destinationAddressCountry="VN",
    destinationLocationLatitude=20.8,
    destinationLocationLongitude=106.6,
)
trip_body = json_body(
    name="Postman Trip {{$timestamp}}",
    totalDistance=250.0,
    status="draft",
    truckId="{{truckId}}",
    stops=[
        {
            "type": "pickup",
            "order": 1,
            "loadId": "{{loadId}}",
            "addressLine1": "1 Origin Street",
            "addressLine2": None,
            "addressCity": "Ha Noi",
            "addressState": "HN",
            "addressZipCode": "10000",
            "addressCountry": "VN",
            "locationLatitude": 21.0,
            "locationLongitude": 105.8,
        },
        {
            "type": "drop_off",
            "order": 2,
            "loadId": "{{loadId}}",
            "addressLine1": "2 Destination Street",
            "addressLine2": None,
            "addressCity": "Hai Phong",
            "addressState": "HP",
            "addressZipCode": "18000",
            "addressCountry": "VN",
            "locationLatitude": 20.8,
            "locationLongitude": 106.6,
        },
    ],
)
invoice_body = json_body(
    type="load",
    status="draft",
    taxBehavior="exclusive",
    notes="Created by the Postman collection",
    dueDate="{{dueAt}}",
    loadId="{{loadId}}",
    customerId="{{customerId}}",
    employeeId="{{employeeId}}",
    subtotalAmount=1500.00,
    subtotalCurrency="USD",
    taxTotalAmount=150.00,
    taxTotalCurrency="USD",
    totalAmount=1650.00,
    totalCurrency="USD",
    periodStart=None,
    periodEnd=None,
    totalDistanceDriven=120.5,
)
payment_body = json_body(
    status="paid",
    invoiceId="{{invoiceId}}",
    amountAmount=1650.00,
    amountCurrency="USD",
    description="Postman full settlement",
    referenceNumber="PMT-{{$timestamp}}",
    stripePaymentMethodId=None,
    stripePaymentIntentId=None,
    recordedAt="{{now}}",
    billingAddressLine1="2 Dock Road",
    billingAddressLine2=None,
    billingAddressCity="Hai Phong",
    billingAddressState="HP",
    billingAddressZipCode="18000",
    billingAddressCountry="VN",
)
inspection_body = json_body(
    loadId="{{loadId}}",
    type="pickup",
    vin=None,
    vehicleYear=2024,
    vehicleMake="Volvo",
    vehicleModel="FH16",
    vehicleBodyClass="Tractor",
    containerNumber=None,
    sealNumber="SEAL-POSTMAN",
    notes="Created by the Postman collection",
    inspectorSignature="postman-signature",
    latitude=21.0,
    longitude=105.8,
    inspectedAt="{{now}}",
    inspectedById="{{employeeId}}",
    defects=[
        {"partCategory": "tire", "description": "Low tread", "severity": "major"},
        {"partCategory": "light", "description": "Rear lamp out", "severity": "minor"},
    ],
)
conversation_body = json_body(
    name="Postman Dispatch {{$timestamp}}",
    loadId="{{loadId}}",
    isTenantChat=False,
    participantIds=["{{employeeId}}"],
)
message_body = json_body(
    conversationId="{{conversationId}}",
    senderId="{{employeeId}}",
    content="Postman test message {{$timestamp}}",
)
document_metadata = json_body(
    ownerType="load",
    type="bol",
    description="Uploaded by the Postman collection",
    uploadedById="{{employeeId}}",
    loadId="{{loadId}}",
    truckId="{{truckId}}",
    employeeId="{{employeeId}}",
    recipientName=None,
    capturedAt="{{now}}",
    captureLatitude=21.0,
    captureLongitude=105.8,
    notes="Select a local file in Postman before sending",
)


def resource_folder(
    name: str,
    path: str,
    identifier: str,
    body: dict[str, Any],
    query: list[tuple[str, str, bool]],
    *,
    create_requires: tuple[str, ...] = (),
) -> dict[str, Any]:
    create_pre = require_variables(*create_requires) if create_requires else None
    return {
        "name": name,
        "item": [
            request(
                "Create",
                "POST",
                path,
                body=body,
                status=201,
                save=identifier,
                prerequest=create_pre,
            ),
            request("List / Search", "GET", path, query=query),
            request(
                "Get by ID",
                "GET",
                f"{path}/{{{{{identifier}}}}}",
                prerequest=require_variables(identifier),
            ),
            request(
                "Update",
                "PUT",
                f"{path}/{{{{{identifier}}}}}",
                body=body,
                prerequest=require_variables(identifier, *create_requires),
            ),
        ],
    }


auth_test = [
    "if (pm.response.code === 200) {",
    "  const token = pm.response.json();",
    '  pm.environment.set("accessToken", token.access_token);',
    "  if (token.refresh_token) {",
    '    pm.environment.set("refreshToken", token.refresh_token);',
    "  }",
    "}",
]
auth_folder = {
    "name": "01 - Authentication",
    "description": "OAuth2/OIDC requests served by the external Identity Server.",
    "item": [
        request(
            "Get access token (password grant)",
            "POST",
            "/connect/token",
            no_auth=True,
            base_variable="identityUrl",
            tests=auth_test,
            formdata=None,
        ),
        request(
            "Refresh access token",
            "POST",
            "/connect/token",
            no_auth=True,
            base_variable="identityUrl",
            prerequest=require_variables("refreshToken"),
            tests=auth_test,
        ),
    ],
}
auth_folder["item"][0]["request"]["body"] = {
    "mode": "urlencoded",
    "urlencoded": [
        {"key": "grant_type", "value": "password", "type": "text"},
        {"key": "client_id", "value": "{{clientId}}", "type": "text"},
        {"key": "client_secret", "value": "{{clientSecret}}", "type": "text"},
        {"key": "username", "value": "{{username}}", "type": "text"},
        {"key": "password", "value": "{{password}}", "type": "text"},
        {"key": "scope", "value": "{{scope}}", "type": "text"},
    ],
}
auth_folder["item"][0]["request"]["header"].append(
    {"key": "Content-Type", "value": "application/x-www-form-urlencoded"}
)
auth_folder["item"][1]["request"]["body"] = {
    "mode": "urlencoded",
    "urlencoded": [
        {"key": "grant_type", "value": "refresh_token", "type": "text"},
        {"key": "client_id", "value": "{{clientId}}", "type": "text"},
        {"key": "client_secret", "value": "{{clientSecret}}", "type": "text"},
        {"key": "refresh_token", "value": "{{refreshToken}}", "type": "text"},
    ],
}
auth_folder["item"][1]["request"]["header"].append(
    {"key": "Content-Type", "value": "application/x-www-form-urlencoded"}
)


terminal_create_pre = [
    "const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ';",
    "let suffix = '';",
    "for (let i = 0; i < 3; i += 1) {",
    "  suffix += alphabet.charAt(Math.floor(Math.random() * alphabet.length));",
    "}",
    'pm.environment.set("terminalCode", "US" + suffix);',
]

flow_auth_request = deepcopy(auth_folder["item"][0])
flow_auth_request["name"] = "02 - Get token when accessToken is empty"
flow_auth_request["request"]["description"] = (
    "Skipped when accessToken is already set. Otherwise uses the configured "
    "Identity Server password grant."
)
flow_auth_request["event"] = [
    script_event(
        "prerequest",
        [
            'if (pm.environment.get("accessToken")) {',
            '  console.log("Using the existing accessToken; token request skipped");',
            "  pm.execution.skipRequest();",
            "}",
            'const required = ["identityUrl", "clientId", "username", "password"];',
            "const missing = required.filter(key => !pm.environment.get(key));",
            "if (missing.length > 0) {",
            "  throw new Error(",
            '    "Set accessToken or provide Identity credentials: " + missing.join(", ")',
            "  );",
            "}",
        ],
    ),
    script_event(
        "test",
        expected_status(200)
        + [
            'pm.test("Identity Server returns an access token", function () {',
            "  const token = pm.response.json();",
            '  pm.expect(token.access_token).to.be.a("string").and.not.empty;',
            "});",
        ]
        + auth_test
        + [
            "if (pm.response.code === 200) {",
            "  const token = pm.response.json();",
            "  if (token.expires_in) {",
            '    pm.environment.set("tokenExpiresAt",',
            "      new Date(Date.now() + token.expires_in * 1000).toISOString());",
            "  }",
            "}",
        ],
    ),
]

flow_start = request(
    "01 - Start flow and check health",
    "GET",
    "/api/health",
    no_auth=True,
    description=(
        "Clears IDs from a previous run, generates flowRunId, and verifies API health."
    ),
    prerequest=[
        'const generatedIds = ["roleId", "customerId", "employeeId",',
        '  "terminalId", "truckId", "loadId"];',
        "generatedIds.forEach(key => pm.environment.unset(key));",
        "const suffix = Math.floor(Math.random() * 100000)",
        '  .toString().padStart(5, "0");',
        'pm.environment.set("flowRunId", Date.now().toString() + suffix);',
        'pm.environment.set("flowStartedAt", new Date().toISOString());',
    ],
    tests=[
        'pm.test("Application reports UP", function () {',
        "  const response = pm.response.json();",
        '  pm.expect(response.status).to.eql("UP");',
        "});",
    ],
)

flow_validate_token = request(
    "03 - Validate token",
    "GET",
    "/api/customers",
    query=[("page", "1", True), ("pageSize", "1", True)],
    description=(
        "Fails before creating data when accessToken is missing, expired, "
        "or does not have a management role."
    ),
    prerequest=[
        'if (!pm.environment.get("accessToken")) {',
        '  throw new Error("accessToken is empty; login or paste a valid bearer token");',
        "}",
    ],
    tests=[
        'pm.test("Token is accepted by the API", function () {',
        "  const response = pm.response.json();",
        "  pm.expect(response.success).to.eql(true);",
        "});",
    ],
)

e2e_flow = {
    "name": "00 - RUN THIS - E2E Smoke Flow",
    "description": (
        "Run this folder in Collection Runner. It checks health, obtains or reuses a token, "
        "creates linked Role → Customer → Employee → Terminal → Truck → Load data, verifies "
        "the load, then deletes only the generated records in reverse order. The token needs "
        "SUPERADMIN or OWNER authority. Set cleanupAfterRun=false to keep generated data."
    ),
    "item": [
        flow_start,
        flow_auth_request,
        flow_validate_token,
        request(
            "04 - Create role",
            "POST",
            "/api/roles",
            body=flow_body(role_body),
            status=201,
            save="roleId",
        ),
        request(
            "05 - Create customer",
            "POST",
            "/api/customers",
            body=flow_body(customer_body),
            status=201,
            save="customerId",
        ),
        request(
            "06 - Create employee",
            "POST",
            "/api/employees",
            body=flow_body(employee_body),
            status=201,
            save="employeeId",
            prerequest=require_variables("roleId"),
        ),
        request(
            "07 - Create terminal",
            "POST",
            "/api/terminals",
            body=flow_body(terminal_body),
            status=201,
            save="terminalId",
            prerequest=terminal_create_pre,
        ),
        request(
            "08 - Create truck",
            "POST",
            "/api/trucks",
            body=flow_body(truck_body),
            status=201,
            save="truckId",
            prerequest=require_variables("employeeId"),
        ),
        request(
            "09 - Create load",
            "POST",
            "/api/loads",
            body=flow_body(load_body),
            status=201,
            save="loadId",
            prerequest=require_variables(
                "customerId", "truckId", "employeeId", "terminalId"
            ),
        ),
        request(
            "10 - Verify created load",
            "GET",
            "/api/loads/{{loadId}}",
            prerequest=require_variables("loadId"),
            tests=[
                'pm.test("Created load keeps its linked resources", function () {',
                "  const response = pm.response.json();",
                '  pm.expect(response.data.id).to.eql(pm.environment.get("loadId"));',
                "  pm.expect(response.data.customerId)",
                '    .to.eql(pm.environment.get("customerId"));',
                "  pm.expect(response.data.assignedTruckId)",
                '    .to.eql(pm.environment.get("truckId"));',
                "});",
            ],
        ),
        flow_cleanup("11 - Cleanup load", "/api/loads/{{loadId}}", "loadId"),
        flow_cleanup("12 - Cleanup truck", "/api/trucks/{{truckId}}", "truckId"),
        flow_cleanup(
            "13 - Cleanup terminal", "/api/terminals/{{terminalId}}", "terminalId"
        ),
        flow_cleanup(
            "14 - Cleanup employee", "/api/employees/{{employeeId}}", "employeeId"
        ),
        flow_cleanup(
            "15 - Cleanup customer", "/api/customers/{{customerId}}", "customerId"
        ),
        flow_cleanup("16 - Cleanup role", "/api/roles/{{roleId}}", "roleId"),
    ],
}

folders: list[dict[str, Any]] = [
    e2e_flow,
    {
        "name": "00 - System",
        "item": [
            request("Health", "GET", "/api/health", no_auth=True),
            request("OpenAPI document", "GET", "/v3/api-docs", no_auth=True),
        ],
    },
    auth_folder,
    resource_folder("02 - Roles", "/api/roles", "roleId", role_body, PAGING),
    resource_folder(
        "03 - Customers",
        "/api/customers",
        "customerId",
        customer_body,
        SORT_NAME
        + [("search", "", False), ("status", "active", False)],
    ),
    resource_folder(
        "04 - Employees",
        "/api/employees",
        "employeeId",
        employee_body,
        PAGING
        + [
            ("orderBy", "lastName", True),
            ("descending", "false", True),
            ("search", "", False),
            ("status", "ACTIVE", False),
            ("roleId", "{{roleId}}", False),
        ],
        create_requires=("roleId",),
    ),
    {
        "name": "05 - Drivers",
        "item": [
            request(
                "List / Search",
                "GET",
                "/api/drivers",
                query=PAGING
                + [
                    ("orderBy", "lastName", True),
                    ("descending", "false", True),
                    ("search", "", False),
                    ("status", "ACTIVE", False),
                ],
            ),
            request(
                "Get by ID",
                "GET",
                "/api/drivers/{{employeeId}}",
                prerequest=require_variables("employeeId"),
            ),
        ],
    },
    {
        "name": "06 - Terminals",
        "item": [
            request(
                "Create",
                "POST",
                "/api/terminals",
                body=terminal_body,
                status=201,
                save="terminalId",
                prerequest=terminal_create_pre,
            ),
            request(
                "List / Search",
                "GET",
                "/api/terminals",
                query=SORT_NAME
                + [
                    ("search", "", False),
                    ("type", "SEA_PORT", False),
                    ("countryCode", "US", False),
                ],
            ),
            request(
                "Get by ID",
                "GET",
                "/api/terminals/{{terminalId}}",
                prerequest=require_variables("terminalId"),
            ),
            request(
                "Update",
                "PUT",
                "/api/terminals/{{terminalId}}",
                body=terminal_body,
                prerequest=require_variables("terminalId", "terminalCode"),
            ),
        ],
    },
    resource_folder(
        "07 - Trucks",
        "/api/trucks",
        "truckId",
        truck_body,
        PAGING
        + [
            ("orderBy", "number", True),
            ("descending", "false", True),
            ("search", "", False),
            ("status", "available", False),
            ("type", "freight_truck", False),
        ],
        create_requires=("employeeId",),
    ),
    resource_folder(
        "08 - Loads",
        "/api/loads",
        "loadId",
        load_body,
        SORT_NAME
        + [
            ("search", "", False),
            ("status", "draft", False),
            ("customerId", "{{customerId}}", False),
            ("truckId", "{{truckId}}", False),
            ("dispatcherId", "{{employeeId}}", False),
        ],
        create_requires=("customerId", "truckId", "employeeId", "terminalId"),
    ),
    resource_folder(
        "09 - Trips",
        "/api/trips",
        "tripId",
        trip_body,
        SORT_NAME
        + [
            ("search", "", False),
            ("status", "draft", False),
            ("truckId", "{{truckId}}", False),
        ],
        create_requires=("truckId", "loadId"),
    ),
    resource_folder(
        "10 - Invoices",
        "/api/invoices",
        "invoiceId",
        invoice_body,
        PAGING
        + [
            ("orderBy", "number", True),
            ("descending", "false", True),
            ("status", "draft", False),
            ("type", "load", False),
            ("customerId", "{{customerId}}", False),
            ("employeeId", "{{employeeId}}", False),
        ],
        create_requires=("loadId", "customerId", "employeeId"),
    ),
    resource_folder(
        "11 - Payments",
        "/api/payments",
        "paymentId",
        payment_body,
        PAGING
        + [
            ("orderBy", "recordedAt", True),
            ("descending", "true", True),
            ("status", "paid", False),
            ("invoiceId", "{{invoiceId}}", False),
        ],
        create_requires=("invoiceId",),
    ),
    resource_folder(
        "12 - Inspections",
        "/api/inspections",
        "inspectionId",
        inspection_body,
        PAGING
        + [
            ("orderBy", "inspectedAt", True),
            ("descending", "true", True),
            ("loadId", "{{loadId}}", False),
            ("type", "pickup", False),
        ],
        create_requires=("loadId", "employeeId"),
    ),
    {
        "name": "13 - Messaging",
        "item": [
            request(
                "Create conversation",
                "POST",
                "/api/messages/conversations",
                body=conversation_body,
                status=201,
                save="conversationId",
                prerequest=require_variables("loadId", "employeeId"),
            ),
            request(
                "List conversations",
                "GET",
                "/api/messages/conversations",
                query=[
                    ("employeeId", "{{employeeId}}", True),
                    *PAGING,
                ],
                prerequest=require_variables("employeeId"),
            ),
            request(
                "Get conversation",
                "GET",
                "/api/messages/conversations/{{conversationId}}",
                prerequest=require_variables("conversationId"),
            ),
            request(
                "Send message",
                "POST",
                "/api/messages",
                body=message_body,
                status=201,
                save="messageId",
                prerequest=require_variables("conversationId", "employeeId"),
            ),
            request(
                "List messages",
                "GET",
                "/api/messages",
                query=[
                    ("conversationId", "{{conversationId}}", True),
                    ("page", "{{page}}", True),
                    ("pageSize", "50", True),
                ],
                prerequest=require_variables("conversationId"),
            ),
            request(
                "Unread count",
                "GET",
                "/api/messages/unread-count",
                query=[("employeeId", "{{employeeId}}", True)],
                prerequest=require_variables("employeeId"),
            ),
            request(
                "Mark conversation as read",
                "POST",
                "/api/messages/conversations/{{conversationId}}/read",
                query=[("employeeId", "{{employeeId}}", True)],
                prerequest=require_variables("conversationId", "employeeId"),
            ),
        ],
    },
    {
        "name": "14 - Notifications",
        "item": [
            request(
                "List",
                "GET",
                "/api/notifications",
                query=PAGING,
                save_first="notificationId",
            ),
            request(
                "Get by ID",
                "GET",
                "/api/notifications/{{notificationId}}",
                prerequest=require_variables("notificationId"),
            ),
            request(
                "Mark all as read",
                "POST",
                "/api/notifications/mark-all-read",
            ),
        ],
    },
    {
        "name": "15 - Documents",
        "item": [
            request(
                "Upload",
                "POST",
                "/api/documents",
                status=201,
                save="documentId",
                prerequest=skip_unless(
                    "allowFileUpload",
                    "Set allowFileUpload=true and select a file before running this request",
                )
                + require_variables("loadId", "truckId", "employeeId"),
                formdata=[
                    {
                        "key": "file",
                        "type": "file",
                        "src": [],
                        "description": "Select a local file in Postman.",
                    },
                    {
                        "key": "metadata",
                        "type": "text",
                        "value": json.dumps(
                            document_metadata, ensure_ascii=False, indent=2
                        ),
                        "contentType": "application/json",
                    },
                ],
            ),
            request(
                "List / Search",
                "GET",
                "/api/documents",
                query=PAGING
                + [
                    ("orderBy", "fileName", True),
                    ("descending", "false", True),
                    ("type", "bol", False),
                    ("status", "active", False),
                    ("loadId", "{{loadId}}", False),
                    ("truckId", "{{truckId}}", False),
                    ("employeeId", "{{employeeId}}", False),
                ],
                save_first="documentId",
            ),
            request(
                "Get by ID",
                "GET",
                "/api/documents/{{documentId}}",
                prerequest=require_variables("documentId"),
            ),
            request(
                "Download",
                "GET",
                "/api/documents/{{documentId}}/download",
                prerequest=require_variables("documentId"),
            ),
        ],
    },
]


state_transition_guard = skip_unless(
    "allowStateTransitions",
    "Set allowStateTransitions=true to run state-machine transitions",
)
cancel_guard = skip_unless(
    "allowCancellation",
    "Set allowCancellation=true and run cancel instead of the normal transition sequence",
)
folders.append(
    {
        "name": "90 - State transitions (opt-in)",
        "description": (
            "Skipped by default. Enable allowStateTransitions for the normal sequence. "
            "Cancellation is mutually exclusive and uses allowCancellation."
        ),
        "item": [
            request(
                "Load: dispatch",
                "POST",
                "/api/loads/{{loadId}}/dispatch",
                prerequest=state_transition_guard + require_variables("loadId"),
            ),
            request(
                "Load: pick up",
                "POST",
                "/api/loads/{{loadId}}/pick-up",
                prerequest=state_transition_guard + require_variables("loadId"),
            ),
            request(
                "Load: deliver",
                "POST",
                "/api/loads/{{loadId}}/deliver",
                prerequest=state_transition_guard + require_variables("loadId"),
            ),
            request(
                "Load: cancel",
                "POST",
                "/api/loads/{{loadId}}/cancel",
                prerequest=cancel_guard + require_variables("loadId"),
            ),
            request(
                "Trip: dispatch",
                "POST",
                "/api/trips/{{tripId}}/dispatch",
                prerequest=state_transition_guard + require_variables("tripId"),
            ),
            request(
                "Trip: complete",
                "POST",
                "/api/trips/{{tripId}}/complete",
                prerequest=state_transition_guard + require_variables("tripId"),
            ),
            request(
                "Trip: cancel",
                "POST",
                "/api/trips/{{tripId}}/cancel",
                prerequest=cancel_guard + require_variables("tripId"),
            ),
        ],
    }
)


delete_targets = [
    ("Document", "/api/documents/{{documentId}}", "documentId"),
    ("Inspection", "/api/inspections/{{inspectionId}}", "inspectionId"),
    ("Payment", "/api/payments/{{paymentId}}", "paymentId"),
    ("Invoice", "/api/invoices/{{invoiceId}}", "invoiceId"),
    ("Trip", "/api/trips/{{tripId}}", "tripId"),
    ("Load", "/api/loads/{{loadId}}", "loadId"),
    ("Truck", "/api/trucks/{{truckId}}", "truckId"),
    ("Terminal", "/api/terminals/{{terminalId}}", "terminalId"),
    ("Employee", "/api/employees/{{employeeId}}", "employeeId"),
    ("Customer", "/api/customers/{{customerId}}", "customerId"),
    ("Role", "/api/roles/{{roleId}}", "roleId"),
]
folders.append(
    {
        "name": "99 - Cleanup (destructive, opt-in)",
        "description": "Every request is skipped unless allowDestructive=true.",
        "item": [
            request(
                f"DELETE {name}",
                "DELETE",
                path,
                prerequest=skip_unless(
                    "allowDestructive",
                    "Set allowDestructive=true to execute cleanup requests",
                )
                + require_variables(identifier),
            )
            for name, path, identifier in delete_targets
        ],
    }
)


collection = {
    "info": {
        "_postman_id": "75cd5130-2aa4-4b69-a868-1e531f1c0001",
        "name": "Logicstic API",
        "description": (
            "Import with logicstic-local.postman_environment.json. Run folder "
            "'00 - RUN THIS - E2E Smoke Flow' in Collection Runner. Supply either accessToken "
            "or Identity Server credentials. The remaining folders are a manual API catalogue."
        ),
        "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
    },
    "auth": {
        "type": "bearer",
        "bearer": [{"key": "token", "value": "{{accessToken}}", "type": "string"}],
    },
    "event": [
        script_event(
            "prerequest",
            [
                "pm.variables.set('requestId', pm.variables.replaceIn('{{$guid}}'));",
                "const now = new Date();",
                "pm.variables.set('now', now.toISOString());",
                "pm.variables.set(",
                "  'pickupAt',",
                "  new Date(now.getTime() + 24 * 60 * 60 * 1000).toISOString()",
                ");",
                "pm.variables.set(",
                "  'deliveryAt',",
                "  new Date(now.getTime() + 3 * 24 * 60 * 60 * 1000).toISOString()",
                ");",
                "pm.variables.set(",
                "  'dueAt',",
                "  new Date(now.getTime() + 30 * 24 * 60 * 60 * 1000).toISOString()",
                ");",
            ],
        ),
        script_event(
            "test",
            [
                'pm.test("No server-side failure", function () {',
                "  pm.expect(pm.response.code).to.be.below(500);",
                "});",
                "const contentType = pm.response.headers.get('Content-Type') || '';",
                "const path = pm.request.url.getPath();",
                "if (contentType.includes('application/json')",
                "    && path.startsWith('/api/')",
                "    && path !== '/api/health') {",
                "  const response = pm.response.json();",
                '  pm.test("ApiResponse envelope is present", function () {',
                "    pm.expect(response).to.have.property('success');",
                "    pm.expect(response).to.have.property('code');",
                "    pm.expect(response).to.have.property('message');",
                "    pm.expect(response).to.have.property('errors');",
                "    pm.expect(response).to.have.property('meta');",
                "  });",
                "}",
            ],
        ),
    ],
    "item": folders,
}


environment_values = [
    ("baseUrl", "http://localhost:18080", "default", True),
    ("identityUrl", "https://localhost:7001", "default", True),
    ("clientId", "logisticsx.client", "default", True),
    ("clientSecret", "", "secret", True),
    ("username", "", "default", True),
    ("password", "", "secret", True),
    ("scope", "openid profile logisticsx.api", "default", True),
    ("accessToken", "", "secret", True),
    ("refreshToken", "", "secret", True),
    ("tokenExpiresAt", "", "default", True),
    ("flowRunId", "", "default", True),
    ("flowStartedAt", "", "default", True),
    ("cleanupAfterRun", "true", "default", True),
    ("page", "1", "default", True),
    ("pageSize", "20", "default", True),
    ("allowFileUpload", "false", "default", True),
    ("allowStateTransitions", "false", "default", True),
    ("allowCancellation", "false", "default", True),
    ("allowDestructive", "false", "default", True),
    ("terminalCode", "", "default", True),
    ("roleId", "", "default", True),
    ("customerId", "", "default", True),
    ("employeeId", "", "default", True),
    ("terminalId", "", "default", True),
    ("truckId", "", "default", True),
    ("loadId", "", "default", True),
    ("tripId", "", "default", True),
    ("invoiceId", "", "default", True),
    ("paymentId", "", "default", True),
    ("inspectionId", "", "default", True),
    ("conversationId", "", "default", True),
    ("messageId", "", "default", True),
    ("notificationId", "", "default", True),
    ("documentId", "", "default", True),
]
environment = {
    "id": "c2955c94-ee66-449f-ae8b-1e531f1c0002",
    "name": "Logicstic Local",
    "values": [
        {"key": key, "value": value, "type": kind, "enabled": enabled}
        for key, value, kind, enabled in environment_values
    ],
    "_postman_variable_scope": "environment",
    "_postman_exported_at": "2026-07-27T00:00:00.000Z",
    "_postman_exported_using": "Logicstic Postman generator",
}


def main() -> None:
    COLLECTION_FILE.write_text(
        json.dumps(collection, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    ENVIRONMENT_FILE.write_text(
        json.dumps(environment, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(f"Wrote {COLLECTION_FILE}")
    print(f"Wrote {ENVIRONMENT_FILE}")


if __name__ == "__main__":
    main()
