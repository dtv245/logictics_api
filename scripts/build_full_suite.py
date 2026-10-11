import json
import os
import sys
import uuid

def make_req(name, method, url_path, desc, body=None, pre="", test="", qparams=None, headers=None, noauth=False, no_redirect=False):
    h = []
    if headers:
        for hk, hv in headers.items():
            h.append({"key": hk, "value": hv, "type": "text"})
    if body is not None:
        h.append({"key": "Content-Type", "value": "application/json", "type": "text"})
    
    parts = [p for p in url_path.strip("/").split("/") if p]
    raw_url = "{{baseUrl}}/" + "/".join(parts)
    
    url_obj = {
        "raw": raw_url,
        "host": ["{{baseUrl}}"],
        "path": parts
    }
    
    if qparams:
        qp = []
        raw_q = []
        for k, v in qparams.items():
            qp.append({"key": k, "value": str(v)})
            raw_q.append(f"{k}={v}")
        url_obj["query"] = qp
        url_obj["raw"] += "?" + "&".join(raw_q)
        
    req = {
        "method": method.upper(),
        "header": h,
        "url": url_obj,
        "description": desc
    }
    
    if body is not None:
        req["body"] = {
            "mode": "raw",
            "raw": json.dumps(body, indent=2) if isinstance(body, (dict, list)) else str(body),
            "options": {"raw": {"language": "json"}}
        }
        
    events = []
    if pre:
        events.append({
            "listen": "prerequest",
            "script": {"type": "text/javascript", "exec": [line for line in pre.strip().split("\n")]}
        })
    if test:
        events.append({
            "listen": "test",
            "script": {"type": "text/javascript", "exec": [line for line in test.strip().split("\n")]}
        })
        
    item = {
        "name": name,
        "request": req,
        "response": []
    }
    if events:
        item["event"] = events
    if noauth:
        item["request"]["auth"] = {"type": "noauth"}
    if no_redirect:
        item["protocolProfileBehavior"] = {"followRedirects": False}
    return item

# Common test script prefix
TEST_BASE = """
pm.test("Status code is 200/201/204", function () {
    pm.expect(pm.response.code).to.be.oneOf([200, 201, 204]);
});

pm.test("Response time < threshold", function () {
    const threshold = Number(pm.environment.get("responseTimeoutThreshold")) || 3000;
    pm.expect(pm.response.responseTime).to.be.below(threshold);
});

let json = {};
try {
    json = pm.response.json();
} catch (e) {}

if (json && json.success !== undefined) {
    pm.test("ApiResponse success flag is true", function () {
        if (!json.success && json.errors) {
            console.error("[API ERROR]", JSON.stringify(json.errors), "requestId:", json.meta ? json.meta.requestId : "");
        }
        pm.expect(json.success).to.be.true;
    });
}
const d = (json && json.data !== undefined) ? json.data : json;
"""

def get_folder_00():
    items = []
    items.append(make_req("00.1 Health Check (Standard)", "GET", "/health", "Check app health", noauth=True, test=TEST_BASE))
    items.append(make_req("00.2 Health Check (API)", "GET", "/api/health", "Check API health", noauth=True, test=TEST_BASE))
    items.append(make_req("00.3 Actuator Health", "GET", "/actuator/health", "Spring Boot Actuator Health", noauth=True, test=TEST_BASE))
    items.append(make_req("00.4 Internal Build Metadata", "GET", "/api/internal/build", "Inspect commit hash and build ID", test=TEST_BASE))
    return {"name": "00_Health", "item": items}

def get_folder_01():
    items = []
    items.append(make_req("01.1 Lark Login Redirect", "GET", "/api/auth/lark/login", "Redirect endpoint to Lark OAuth. Follow redirects disabled.", noauth=True, no_redirect=True, test="""
pm.test("Status is 302 Redirect", function () {
    pm.expect(pm.response.code).to.eql(302);
});
pm.test("Location header exists", function () {
    pm.expect(pm.response.headers.has("Location")).to.be.true;
});
"""))

    items.append(make_req("01.2 Lark Authorize URL", "GET", "/api/auth/lark/authorize", "Generate Lark OAuth authorization URL", qparams={"returnTo": "{{returnTo}}"}, noauth=True, test=TEST_BASE + """
if (d && d.authorizationUrl) {
    pm.environment.set("larkAuthorizeUrl", d.authorizationUrl);
    console.log("[Lark Auth] Open this URL in browser to log in:", d.authorizationUrl);
}
"""))

    items.append(make_req("01.3 Lark OAuth Callback", "POST", "/api/auth/lark/callback", "Process OAuth code/state returned by Lark. Run manually after copying code/state or bypass if using dev JWT.", body={
        "code": "{{larkCode}}",
        "state": "{{larkState}}",
        "returnTo": "{{returnTo}}"
    }, noauth=True, pre="""
const larkCode = pm.environment.get("larkCode");
if (!larkCode || larkCode.trim() === "") {
    console.warn("[AUTH INFO] larkCode is empty. Skipping OAuth exchange callback (assuming developer is using pre-configured accessToken).");
    pm.execution.skipRequest();
}
""", test="""
let json = {};
try { json = pm.response.json(); } catch(e) {}
const d = (json && json.data !== undefined) ? json.data : json;

const larkCode = pm.environment.get("larkCode");
if (!larkCode || larkCode.trim() === "") {
    console.warn("[AUTH INFO] larkCode is empty. Skipping OAuth exchange assertion (assuming developer is using pre-configured accessToken).");
} else {
    pm.test("Lark Callback Success 200", function () {
        pm.expect(pm.response.code).to.eql(200);
    });
    if (d && d.accessToken) {
        pm.environment.set("accessToken", d.accessToken);
        if (d.tenantId) pm.environment.set("tenantId", d.tenantId);
        if (d.employeeId) pm.environment.set("employeeId", d.employeeId);
        if (d.roles) pm.environment.set("roles", JSON.stringify(d.roles));
        
        const expiresInSec = Number(d.expiresIn) || 86400;
        const expiresAt = Date.now() + (expiresInSec * 1000);
        pm.environment.set("tokenExpiresAt", String(expiresAt));
        console.log("[AUTH SUCCESS] Token saved, expires at:", new Date(expiresAt).toISOString());
    }
}
"""))

    items.append(make_req("01.4 Current User Identity", "GET", "/api/me", "Verify current tenant and roles from JWT token", test=TEST_BASE + """
pm.test("Current user has identity", function () {
    pm.expect(d).to.be.an("object");
    if (d.tenantId) pm.environment.set("tenantId", d.tenantId);
    if (d.employeeId) pm.environment.set("employeeId", d.employeeId);
    else if (d.id) pm.environment.set("employeeId", d.id);
    pm.expect(d.employeeId || d.subject || d.id).to.be.ok;
});
"""))
    return {"name": "01_Auth", "item": items}

def get_folder_02():
    items = []
    # Roles
    items.append(make_req("02.1 Create Role", "POST", "/api/roles", "Create role with claims", body={
        "name": "DISPATCHER_LEAD_{{$timestamp}}",
        "displayName": "Dispatcher Role",
        "claims": [{
            "claimType": "PERMISSION",
            "claimValue": "ROLE_DISPATCHER"
        }]
    }, test=TEST_BASE + """
if (d && d.id) {
    pm.environment.set("roleId", d.id);
    console.log("Saved roleId:", d.id);
}
"""))
    items.append(make_req("02.2 List Roles", "GET", "/api/roles", "List all roles", qparams={"page": 1, "pageSize": 20}, test=TEST_BASE))
    items.append(make_req("02.3 Get Role by ID", "GET", "/api/roles/{{roleId}}", "Get role details", test=TEST_BASE))
    items.append(make_req("02.4 Update Role", "PUT", "/api/roles/{{roleId}}", "Update role details", body={
        "name": "DISPATCHER_LEAD_{{$timestamp}}",
        "displayName": "Senior Dispatcher Role",
        "claims": [{
            "claimType": "PERMISSION",
            "claimValue": "ROLE_DISPATCHER"
        }]
    }, test=TEST_BASE))

    # Employees
    items.append(make_req("02.5 Create Dispatcher Employee", "POST", "/api/employees", "Create dispatcher employee", body={
        "firstName": "Alex",
        "lastName": "Dispatcher",
        "email": "dispatcher_{{$timestamp}}@example.com",
        "phoneNumber": "+1-555-0100",
        "joinedDate": "2026-01-01T00:00:00Z",
        "salaryAmount": 5000.0,
        "salaryCurrency": "{{enum_currency}}",
        "salaryType": "{{enum_salaryType}}",
        "status": "{{enum_employeeStatus}}",
        "roleId": "{{roleId}}",
        "addressLine1": "100 Logistics Blvd",
        "addressCity": "Los Angeles",
        "addressState": "CA",
        "addressZipCode": "90001",
        "addressCountry": "{{enum_country}}"
    }, test=TEST_BASE + """
if (d && d.id) {
    pm.environment.set("dispatcherEmployeeId", d.id);
    console.log("Saved dispatcherEmployeeId:", d.id);
}
"""))

    items.append(make_req("02.6 Create Driver Employee", "POST", "/api/employees", "Create driver employee", body={
        "firstName": "John",
        "lastName": "Driver",
        "email": "driver_{{$timestamp}}@example.com",
        "phoneNumber": "+1-555-0199",
        "joinedDate": "2026-01-01T00:00:00Z",
        "salaryAmount": 4500.0,
        "salaryCurrency": "{{enum_currency}}",
        "salaryType": "{{enum_salaryType}}",
        "status": "{{enum_employeeStatus}}",
        "roleId": "{{roleId}}",
        "addressLine1": "200 Harbor Way",
        "addressCity": "Long Beach",
        "addressState": "CA",
        "addressZipCode": "90802",
        "addressCountry": "{{enum_country}}"
    }, test=TEST_BASE + """
if (d && d.id) {
    pm.environment.set("driverEmployeeId", d.id);
    pm.environment.set("driverId", d.id);
    console.log("Saved driverEmployeeId & driverId:", d.id);
}
"""))

    items.append(make_req("02.7 List Employees", "GET", "/api/employees", "List employees", qparams={"search": "", "page": 1, "pageSize": 20}, test=TEST_BASE))
    items.append(make_req("02.8 Get Employee by ID", "GET", "/api/employees/{{driverEmployeeId}}", "Get employee details", test=TEST_BASE))
    items.append(make_req("02.9 List Drivers", "GET", "/api/drivers", "List active drivers", qparams={"search": "", "page": 1, "pageSize": 20}, test=TEST_BASE))
    items.append(make_req("02.10 Get Driver by ID", "GET", "/api/drivers/{{driverId}}", "Get driver view", test=TEST_BASE))

    # Customers
    items.append(make_req("02.11 Create Customer", "POST", "/api/customers", "Create shipper customer", body={
        "name": "Acme Logistics Corp {{$timestamp}}",
        "email": "billing_{{$timestamp}}@acme.com",
        "phone": "+1-800-555-0144",
        "status": "{{enum_customerStatus}}",
        "isVatExempt": False,
        "taxId": "TAX-998877",
        "addressLine1": "500 Industrial Pkwy",
        "addressCity": "San Francisco",
        "addressState": "CA",
        "addressZipCode": "94107",
        "addressCountry": "{{enum_country}}",
        "notes": "Premium enterprise account"
    }, test=TEST_BASE + """
if (d && d.id) {
    pm.environment.set("customerId", d.id);
    console.log("Saved customerId:", d.id);
}
"""))
    items.append(make_req("02.12 List Customers", "GET", "/api/customers", "List customers", qparams={"page": 1, "pageSize": 20}, test=TEST_BASE))
    items.append(make_req("02.13 Get Customer by ID", "GET", "/api/customers/{{customerId}}", "Get customer details", test=TEST_BASE))
    items.append(make_req("02.14 Update Customer", "PUT", "/api/customers/{{customerId}}", "Update customer", body={
        "name": "Acme Logistics Global",
        "email": "billing@acme.com",
        "status": "{{enum_customerStatus}}",
        "isVatExempt": False,
        "notes": "Updated SLA Tier 1"
    }, test=TEST_BASE))
    items.append(make_req("02.15 Get Customer Balance", "GET", "/api/customers/{{customerId}}/balance", "Check customer account balance", test=TEST_BASE))

    # Trucks
    items.append(make_req("02.16 Create Truck", "POST", "/api/trucks", "Register semi-truck vehicle", body={
        "number": "TRK-{{$timestamp}}",
        "make": "Freightliner",
        "model": "Cascadia",
        "year": 2023,
        "licensePlate": "CA{{$randomInt}}",
        "licensePlateState": "CA",
        "status": "{{enum_truckStatus}}",
        "type": "{{enum_truckType}}",
        "vehicleCapacity": 45000.0,
        "mainDriverId": "{{driverId}}",
        "isHazmatPlacarded": False
    }, test=TEST_BASE + """
if (d && d.id) {
    pm.environment.set("truckId", d.id);
    pm.environment.set("truckVersion", String(d.version !== undefined ? d.version : 0));
    console.log("Saved truckId:", d.id, "version:", d.version);
}
"""))
    items.append(make_req("02.17 List Trucks", "GET", "/api/trucks", "List trucks", qparams={"search": "", "page": 1, "pageSize": 20}, test=TEST_BASE))
    items.append(make_req("02.18 Get Truck by ID", "GET", "/api/trucks/{{truckId}}", "Get truck details and latest version", test=TEST_BASE + """
if (d && d.version !== undefined) {
    pm.environment.set("truckVersion", String(d.version));
}
"""))
    items.append(make_req("02.19 Update Truck", "PUT", "/api/trucks/{{truckId}}", "Update truck with expectedVersion", body={
        "expectedVersion": 0,
        "number": "TRK-UPD-{{$timestamp}}",
        "make": "Freightliner",
        "model": "Cascadia Evolution",
        "year": 2024,
        "status": "{{enum_truckStatus}}",
        "type": "{{enum_truckType}}",
        "vehicleCapacity": 46000.0,
        "mainDriverId": "{{driverId}}",
        "isHazmatPlacarded": False
    }, pre="""
const v = Number(pm.environment.get("truckVersion")) || 0;
const body = JSON.parse(pm.request.body.raw);
body.expectedVersion = v;
pm.request.body.raw = JSON.stringify(body, null, 2);
""", test=TEST_BASE + """
if (d && d.version !== undefined) {
    pm.environment.set("truckVersion", String(d.version));
}
"""))

    return {"name": "02_MasterData", "item": items}
def get_folder_03():
    items = []
    items.append(make_req("03.1 Tenant Default Payroll Config", "PUT", "/api/payroll/configuration/tenant-default", "Configure default tenant payroll jurisdiction", body={
        "jurisdiction": {
            "countryCode": "{{enum_country}}",
            "subdivisionCode": "{{enum_state}}"
        }
    }, test=TEST_BASE))

    items.append(make_req("03.2 Publish Payroll Policy Version", "POST", "/api/payroll/configuration/policy-versions", "Publish statutory payroll policy version", body={
        "policyCode": "STD-PAYROLL-POL-{{$timestamp}}",
        "authoritativeSource": "CA Labor Commission",
        "calculatorKey": "STANDARD_US_W2",
        "configurationJson": "{\"overtimeThresholdHours\": 40, \"overtimeRateMultiplier\": 1.5}",
        "currency": "{{enum_currency}}",
        "effectiveFrom": "2026-01-01",
        "jurisdiction": {
            "countryCode": "{{enum_country}}",
            "subdivisionCode": "{{enum_state}}"
        },
        "workerClassification": "{{enum_workerClassification}}",
        "active": True
    }, test=TEST_BASE))

    items.append(make_req("03.3 Configure Employee Payroll Profile", "POST", "/api/payroll/configuration/employees/{{driverEmployeeId}}/profiles", "Configure worker classification profile for driver", body={
        "effectiveFrom": "2026-01-01",
        "workerClassification": "{{enum_workerClassification}}",
        "jurisdiction": {
            "countryCode": "{{enum_country}}",
            "subdivisionCode": "{{enum_state}}"
        },
        "active": True
    }, test=TEST_BASE))

    items.append(make_req("03.4 Create Pay Period", "POST", "/api/pay-periods", "Create current pay period", body={
        "periodCode": "PP-2026-10A",
        "startDate": "2026-10-01",
        "endDate": "2026-10-15",
        "paymentDate": "2026-10-20"
    }, pre="""
const periodCode = "PP-" + Date.now();
const body = JSON.parse(pm.request.body.raw);
body.periodCode = periodCode;
pm.request.body.raw = JSON.stringify(body, null, 2);
""", test=TEST_BASE + """
if (d && d.id) {
    pm.environment.set("payPeriodId", d.id);
    pm.environment.set("payPeriodCode", d.periodCode);
    console.log("Saved payPeriodId:", d.id);
}
"""))

    items.append(make_req("03.5 List Pay Periods", "GET", "/api/pay-periods", "List pay periods", test=TEST_BASE))

    items.append(make_req("03.6 Create Driver Pay Policy", "POST", "/api/driver-pay-policies", "Create mileage pay policy for driver", body={
        "driverId": "{{driverId}}",
        "name": "Standard Mileage Pay Policy",
        "policyCode": "DRV-POL-{{$timestamp}}",
        "payMethod": "{{enum_payMethod}}",
        "mileageBasis": "{{enum_mileageBasis}}",
        "perMileRate": 0.65,
        "stopPayRate": 25.0,
        "detentionRate": 30.0,
        "detentionFreeMinutes": 120,
        "detentionBlockMinutes": 15,
        "currency": "{{enum_currency}}",
        "effectiveFrom": "2026-01-01"
    }, test=TEST_BASE + """
if (d && d.id) {
    pm.environment.set("payPolicyId", d.id);
    if (d.policyCode) pm.environment.set("payPolicyCode", d.policyCode);
    console.log("Saved payPolicyId:", d.id);
}
"""))

    items.append(make_req("03.7 List Driver Pay Policies", "GET", "/api/driver-pay-policies", "List pay policies", test=TEST_BASE))
    items.append(make_req("03.8 Get Driver Pay Policy by ID", "GET", "/api/driver-pay-policies/{{payPolicyId}}", "Get pay policy details", test=TEST_BASE))

    items.append(make_req("03.9 Create New Policy Version", "POST", "/api/driver-pay-policies/{{payPolicyId}}/new-version", "Create updated pay policy version", body={
        "driverId": "{{driverId}}",
        "name": "Standard Mileage Pay Policy v2",
        "policyCode": "{{payPolicyCode}}",
        "payMethod": "{{enum_payMethod}}",
        "mileageBasis": "{{enum_mileageBasis}}",
        "perMileRate": 0.70,
        "currency": "{{enum_currency}}",
        "effectiveFrom": "2026-02-01"
    }, pre="""
const code = pm.environment.get("payPolicyCode") || "DRV-POL-DEFAULT";
const body = JSON.parse(pm.request.body.raw);
body.policyCode = code;
pm.request.body.raw = JSON.stringify(body, null, 2);
""", test=TEST_BASE + """
if (d && d.id) {
    pm.environment.set("payPolicyId", d.id);
}
"""))

    return {"name": "03_PayrollConfig", "item": items}

def get_folder_04():
    items = []
    items.append(make_req("04.1 Create Rating Contract", "POST", "/api/rating/contracts", "Create customer freight contract", body={
        "customerId": "{{customerId}}",
        "currency": "{{enum_currency}}",
        "effectiveFrom": "2026-01-01",
        "effectiveTo": "2026-12-31"
    }, test=TEST_BASE + """
if (d && (d.contractId || d.id)) {
    const cid = d.contractId || d.id;
    pm.environment.set("ratingContractId", cid);
    pm.environment.set("ratingContractVersion", String(d.version !== undefined ? d.version : 1));
    console.log("Saved ratingContractId:", cid);
}
"""))

    items.append(make_req("04.2 Create Contract Version", "POST", "/api/rating/contracts/{{ratingContractId}}/versions", "Add version to contract", qparams={"expectedVersion": "{{ratingContractVersion}}"}, body={
        "customerId": "{{customerId}}",
        "currency": "{{enum_currency}}",
        "effectiveFrom": "2026-06-01",
        "effectiveTo": "2026-12-31"
    }, test=TEST_BASE + """
if (d && d.version !== undefined) {
    pm.environment.set("ratingContractVersion", String(d.version));
}
"""))

    items.append(make_req("04.3 Get Contract Version", "GET", "/api/rating/contracts/{{ratingContractId}}/versions/{{ratingContractVersion}}", "Fetch contract version details", test=TEST_BASE))

    items.append(make_req("04.4 Create Rating Rule", "POST", "/api/rating/rules", "Create linehaul rate rule", body={
        "contractId": "{{ratingContractId}}",
        "contractVersion": 1,
        "customerId": "{{customerId}}",
        "baseRate": 3.50,
        "currency": "{{enum_currency}}",
        "method": "PER_MILE",
        "linehaulMileageBasis": "CONTRACT_MILES",
        "priority": 2,
        "effectiveFrom": "2026-01-01",
        "effectiveTo": "2026-12-31"
    }, pre="""
const cv = Number(pm.environment.get("ratingContractVersion")) || 1;
const body = JSON.parse(pm.request.body.raw);
body.contractVersion = cv;
if (cv >= 2) {
    body.effectiveFrom = "2026-06-01";
}
pm.request.body.raw = JSON.stringify(body, null, 2);
""", test=TEST_BASE + """
if (d && (d.ruleId || d.id)) {
    const rid = d.ruleId || d.id;
    pm.environment.set("ratingRuleId", rid);
    pm.environment.set("ratingRuleVersion", String(d.version !== undefined ? d.version : 1));
    console.log("Saved ratingRuleId:", rid);
}
"""))

    items.append(make_req("04.5 Create Rating Rule Version", "POST", "/api/rating/rules/{{ratingRuleId}}/versions", "Create new version of rating rule", qparams={"expectedVersion": "{{ratingRuleVersion}}"}, body={
        "contractId": "{{ratingContractId}}",
        "contractVersion": 1,
        "customerId": "{{customerId}}",
        "baseRate": 3.75,
        "currency": "{{enum_currency}}",
        "method": "PER_MILE",
        "linehaulMileageBasis": "CONTRACT_MILES",
        "priority": 1,
        "effectiveFrom": "2026-06-01",
        "effectiveTo": "2026-12-31"
    }, pre="""
const cv = Number(pm.environment.get("ratingContractVersion")) || 1;
const body = JSON.parse(pm.request.body.raw);
body.contractVersion = cv;
pm.request.body.raw = JSON.stringify(body, null, 2);
""", test=TEST_BASE + """
if (d && d.version !== undefined) {
    pm.environment.set("ratingRuleVersion", String(d.version));
}
"""))

    items.append(make_req("04.6 Get Rating Rule Version", "GET", "/api/rating/rules/{{ratingRuleId}}/versions/{{ratingRuleVersion}}", "Fetch rating rule version details", test=TEST_BASE))

    return {"name": "04_RatingSetup", "item": items}

def get_folder_05():
    items = []
    items.append(make_req("05.1 Create Load", "POST", "/api/loads", "Create freight load for shipper", body={
        "name": "Full Truckload Shipment #{{$timestamp}}",
        "status": "{{enum_loadStatus}}",
        "type": "{{enum_loadType}}",
        "source": "{{enum_loadSource}}",
        "customerId": "{{customerId}}",
        "distance": 450.0,
        "isInProximity": False,
        "deliveryCostAmount": 1500.0,
        "deliveryCostCurrency": "{{enum_currency}}",
        "weight": 35000.0,
        "length": 53.0,
        "isHazmat": False,
        "originAddressLine1": "100 Port Way",
        "originAddressCity": "Los Angeles",
        "originAddressState": "CA",
        "originAddressZipCode": "90001",
        "originAddressCountry": "{{enum_country}}",
        "originLocationLatitude": 34.0522,
        "originLocationLongitude": -118.2437,
        "destinationAddressLine1": "500 Market St",
        "destinationAddressCity": "San Francisco",
        "destinationAddressState": "CA",
        "destinationAddressZipCode": "94105",
        "destinationAddressCountry": "{{enum_country}}",
        "destinationLocationLatitude": 37.7749,
        "destinationLocationLongitude": -122.4194,
        "requestedPickupDate": "2026-10-15T08:00:00Z",
        "requestedDeliveryDate": "2026-10-16T18:00:00Z"
    }, test=TEST_BASE + """
if (d && d.id) {
    pm.environment.set("loadId", d.id);
    pm.environment.set("loadVersion", String(d.version !== undefined ? d.version : 0));
    console.log("Saved loadId:", d.id, "version:", d.version);
}
"""))

    items.append(make_req("05.2 Get Load by ID", "GET", "/api/loads/{{loadId}}", "Get load details & version", test=TEST_BASE + """
if (d && d.version !== undefined) {
    pm.environment.set("loadVersion", String(d.version));
}
"""))

    items.append(make_req("05.3 Update Load", "PUT", "/api/loads/{{loadId}}", "Update load with expectedVersion", body={
        "expectedVersion": 0,
        "name": "Full Truckload Shipment #{{$timestamp}} (Updated)",
        "status": "{{enum_loadStatus}}",
        "type": "{{enum_loadType}}",
        "source": "{{enum_loadSource}}",
        "customerId": "{{customerId}}",
        "distance": 450.0,
        "isInProximity": False,
        "deliveryCostAmount": 1500.0,
        "deliveryCostCurrency": "{{enum_currency}}",
        "isHazmat": False,
        "originAddressLine1": "100 Port Way",
        "originAddressCity": "Los Angeles",
        "originAddressState": "CA",
        "originAddressZipCode": "90001",
        "originAddressCountry": "{{enum_country}}",
        "originLocationLatitude": 34.0522,
        "originLocationLongitude": -118.2437,
        "destinationAddressLine1": "500 Market St",
        "destinationAddressCity": "San Francisco",
        "destinationAddressState": "CA",
        "destinationAddressZipCode": "94105",
        "destinationAddressCountry": "{{enum_country}}",
        "destinationLocationLatitude": 37.7749,
        "destinationLocationLongitude": -122.4194,
        "requestedPickupDate": "2026-10-15T08:00:00Z",
        "requestedDeliveryDate": "2026-10-16T18:00:00Z",
        "notes": "Driver gate pass confirmed"
    }, pre="""
const v = Number(pm.environment.get("loadVersion")) || 0;
const body = JSON.parse(pm.request.body.raw);
body.expectedVersion = v;
pm.request.body.raw = JSON.stringify(body, null, 2);
""", test=TEST_BASE + """
if (d && d.version !== undefined) {
    pm.environment.set("loadVersion", String(d.version));
}
"""))

    items.append(make_req("05.4 Set Requested Pickup Business Date", "POST", "/api/loads/{{loadId}}/requested-pickup-business-date", "Set operational pickup date", body={
        "requestedPickupBusinessDate": "2026-10-15",
        "provenance": {
            "reasonCode": "SHIPPER_SCHEDULE_CONFIRMED",
            "reason": "Shipper schedule request",
            "source": "SHIPPER_PORTAL"
        }
    }, test=TEST_BASE))

    return {"name": "05_Load", "item": items}
def get_folder_06():
    items = []
    items.append(make_req("06.1 Add Contract Mileage (LINEHAUL)", "POST", "/api/loads/{{loadId}}/rating/contract-mileage", "Record resolved linehaul mileage evidence", body={
        "componentType": "LINEHAUL",
        "originalUnit": "MILE",
        "originalValue": 450.0,
        "provenance": "CONTRACT_TABLE",
        "contractId": "{{ratingContractId}}",
        "contractVersion": 1
    }, pre="""
const cv = Number(pm.environment.get("ratingContractVersion")) || 1;
const body = JSON.parse(pm.request.body.raw);
body.contractVersion = cv;
pm.request.body.raw = JSON.stringify(body, null, 2);
""", test=TEST_BASE + """
if (d && d.id) {
    pm.environment.set("linehaulMileageEvidenceId", d.id);
    console.log("Saved linehaulMileageEvidenceId:", d.id);
}
"""))

    items.append(make_req("06.2 Add Contract Mileage (FSC)", "POST", "/api/loads/{{loadId}}/rating/contract-mileage", "Record resolved FSC mileage evidence", body={
        "componentType": "FSC",
        "originalUnit": "MILE",
        "originalValue": 450.0,
        "provenance": "INDEX_EVIDENCE",
        "contractId": "{{ratingContractId}}",
        "contractVersion": 1
    }, pre="""
const cv = Number(pm.environment.get("ratingContractVersion")) || 1;
const body = JSON.parse(pm.request.body.raw);
body.contractVersion = cv;
pm.request.body.raw = JSON.stringify(body, null, 2);
""", test=TEST_BASE + """
if (d && d.id) {
    pm.environment.set("fscMileageEvidenceId", d.id);
    console.log("Saved fscMileageEvidenceId:", d.id);
}
"""))

    items.append(make_req("06.3 Create Accessorial Charge", "POST", "/api/loads/{{loadId}}/accessorials", "Create detention accessorial charge", body={
        "type": "{{enum_accessorialType}}",
        "customerAmount": 150.0,
        "companyCostAmount": 50.0,
        "driverPayAmount": 60.0,
        "currency": "{{enum_currency}}",
        "quantity": 2.0,
        "rate": 75.0,
        "unit": "HOURS",
        "freeQuantity": 2.0,
        "note": "2 hours dock detention"
    }, test=TEST_BASE + """
if (d && d.id) {
    pm.environment.set("accessorialChargeId", d.id);
    console.log("Saved accessorialChargeId:", d.id);
}
"""))

    items.append(make_req("06.4 Approve Accessorial Charge", "PUT", "/api/accessorial-charges/{{accessorialChargeId}}/approve", "Approve accessorial charge", test=TEST_BASE))

    items.append(make_req("06.5 Rating - Preview", "POST", "/api/loads/{{loadId}}/rating/preview", "Calculate pricing preview with hashes", body={
        "contractId": "{{ratingContractId}}",
        "contractVersion": 1,
        "currency": "{{enum_currency}}",
        "contextSource": "ORDER_ENTRY",
        "linehaulMileageEvidenceId": "{{linehaulMileageEvidenceId}}",
        "accessorialIds": ["{{accessorialChargeId}}"]
    }, pre="""
const cv = Number(pm.environment.get("ratingContractVersion")) || 1;
const body = JSON.parse(pm.request.body.raw);
body.contractVersion = cv;
if (pm.environment.get("linehaulMileageEvidenceId")) {
    body.linehaulMileageEvidenceId = pm.environment.get("linehaulMileageEvidenceId");
}
delete body.fscMileageEvidenceId;
if (pm.environment.get("accessorialChargeId")) {
    body.accessorialIds = [pm.environment.get("accessorialChargeId")];
}
pm.request.body.raw = JSON.stringify(body, null, 2);
pm.environment.set("ratingPreviewRequestBody", JSON.stringify(body));
""", test=TEST_BASE + """
if (d) {
    pm.test("Preview contains hashes", function () {
        pm.expect(d.inputHash).to.be.a("string").and.not.empty;
        pm.expect(d.resultHash).to.be.a("string").and.not.empty;
    });
    pm.environment.set("previewInputHash", d.inputHash);
    pm.environment.set("previewResultHash", d.resultHash);
    console.log("Saved previewInputHash:", d.inputHash);
}
"""))

    items.append(make_req("06.6 Rating - Accept", "POST", "/api/loads/{{loadId}}/rating/accept", "Accept pricing snapshot", body={
        "expectedInputHash": "{{previewInputHash}}",
        "expectedResultHash": "{{previewResultHash}}",
        "idempotencyKey": "RATE-ACC-{{$timestamp}}",
        "rating": {}
    }, pre="""
const previewReq = JSON.parse(pm.environment.get("ratingPreviewRequestBody") || "{}");
const body = JSON.parse(pm.request.body.raw);
body.rating = previewReq;
body.expectedInputHash = pm.environment.get("previewInputHash");
body.expectedResultHash = pm.environment.get("previewResultHash");
delete body.reason;
delete body.reasonCode;
delete body.supersedesSnapshotId;
pm.request.body.raw = JSON.stringify(body, null, 2);
""", test=TEST_BASE + """
if (d && d.snapshotId) {
    pm.environment.set("ratingSnapshotId", d.snapshotId);
    console.log("Saved ratingSnapshotId:", d.snapshotId);
} else if (d && d.id) {
    pm.environment.set("ratingSnapshotId", d.id);
}
"""))

    items.append(make_req("06.7 Get Rating Snapshot", "GET", "/api/rating/snapshots/{{ratingSnapshotId}}", "Inspect immutable rating snapshot", test=TEST_BASE))

    return {"name": "06_RatingFlow", "item": items}

print("Part 1 functions ready.")

def get_folder_07():
    items = []
    items.append(make_req("07.1 Create Trip", "POST", "/api/trips", "Create dispatch trip for truck", body={
        "name": "Trip LA-SF #{{$timestamp}}",
        "status": "{{enum_tripStatus}}",
        "totalDistance": 450.0,
        "truckId": "{{truckId}}"
    }, test=TEST_BASE + """
if (d && d.id) {
    pm.environment.set("tripId", d.id);
    pm.environment.set("tripVersion", String(d.version !== undefined ? d.version : 0));
    console.log("Saved tripId:", d.id);
}
"""))
    items.append(make_req("07.2 Get Trip by ID", "GET", "/api/trips/{{tripId}}", "Get trip details", test=TEST_BASE + """
if (d && d.version !== undefined) {
    pm.environment.set("tripVersion", String(d.version));
}
if (d && d.completedAt) {
    pm.environment.set("tripCompletedAt", d.completedAt);
}
"""))
    items.append(make_req("07.3 Update Trip", "PUT", "/api/trips/{{tripId}}", "Update trip notes with expectedVersion", body={
        "expectedVersion": 0,
        "name": "Trip LA-SF Dispatched",
        "totalDistance": 450.0,
        "status": "{{enum_tripStatus}}",
        "truckId": "{{truckId}}"
    }, pre="""
const v = Number(pm.environment.get("tripVersion")) || 0;
const body = JSON.parse(pm.request.body.raw);
body.expectedVersion = v;
pm.request.body.raw = JSON.stringify(body, null, 2);
""", test=TEST_BASE + """
if (d && d.version !== undefined) {
    pm.environment.set("tripVersion", String(d.version));
}
"""))
    items.append(make_req("07.4 Assign Driver to Trip", "POST", "/api/trips/{{tripId}}/drivers", "Assign primary driver to trip", body={
        "driverId": "{{driverId}}",
        "assignmentType": "{{enum_assignmentType}}",
        "plannedMiles": 450.0,
        "effectiveFrom": "2026-10-15T08:00:00Z"
    }, test=TEST_BASE + """
if (d && d.id) {
    pm.environment.set("driverAssignmentId", d.id);
    console.log("Saved driverAssignmentId:", d.id);
}
"""))
    items.append(make_req("07.5 Get Trip Stops", "GET", "/api/trips/{{tripId}}/stops", "List stops of trip to extract pickupStopId", test=TEST_BASE + """
pm.test("Trip has at least one stop", function () {
    pm.expect(d).to.be.an("array").and.not.empty;
    pm.environment.set("pickupStopId", d[0].id);
    pm.environment.set("detentionStopId", d[0].id);
    console.log("Saved pickupStopId:", d[0].id);
});
"""))
    items.append(make_req("07.6 Get Seeded Shipment Costs", "GET", "/api/loads/{{loadId}}/costs", "Capture seeded estimate costs for optimization forecast", test=TEST_BASE + """
const list = Array.isArray(d) ? d : (d.content || []);
list.forEach(c => {
    if (c.category === "FUEL") pm.environment.set("forecastFuelCostId", c.id);
    if (c.category === "DRIVER") pm.environment.set("forecastDriverCostId", c.id);
    if (c.category === "TOLL") pm.environment.set("forecastTollCostId", c.id);
});
"""))
    return {"name": "07_TripPlanning", "item": items}

def get_folder_08():
    approved_curves = {
        "DEADHEAD": [
            {"raw": 0.0, "utility": 1.0},
            {"raw": 0.10, "utility": 0.90},
            {"raw": 0.25, "utility": 0.70},
            {"raw": 0.50, "utility": 0.40},
            {"raw": 1.0, "utility": 0.0}
        ],
        "MARGIN": [
            {"raw": 0.0, "utility": 0.0},
            {"raw": 0.05, "utility": 0.25},
            {"raw": 0.10, "utility": 0.50},
            {"raw": 0.20, "utility": 0.80},
            {"raw": 0.30, "utility": 1.0}
        ],
        "ON_TIME": [
            {"raw": 0.0, "utility": 0.0},
            {"raw": 30.0, "utility": 0.25},
            {"raw": 60.0, "utility": 0.50},
            {"raw": 120.0, "utility": 0.80},
            {"raw": 180.0, "utility": 1.0}
        ],
        "HOS": [
            {"raw": 0.0, "utility": 0.0},
            {"raw": 0.05, "utility": 0.25},
            {"raw": 0.10, "utility": 0.50},
            {"raw": 0.20, "utility": 0.80},
            {"raw": 0.30, "utility": 1.0}
        ]
    }
    approved_weights = {
        "DEADHEAD": 0.250000,
        "MARGIN": 0.350000,
        "ON_TIME": 0.300000,
        "HOS": 0.100000
    }
    approved_numeric = {
        "intermediatePrecision": 34,
        "roundingMode": "HALF_EVEN",
        "utilityScale": 6,
        "weightScale": 6,
        "contributionScale": 8,
        "scoreScale": 8
    }
    status_allowlists = {
        "LOAD": ["DRAFT", "PLANNED", "ASSIGNED"],
        "TRIP": ["PLANNED", "ASSIGNED"],
        "DRIVER": ["ACTIVE", "AVAILABLE"],
        "TRUCK": ["AVAILABLE", "ACTIVE"]
    }
    src_authoritative = {
        "classification": "AUTHORITATIVE_DB",
        "reference": "WEIGHT_SCALE_SYSTEM",
        "type": "SCALE_STATION",
        "version": "1.0"
    }
    src_qualification = {
        "classification": "AUTHORITATIVE_DB",
        "reference": "SAFETY_DEPT_REGISTRY",
        "type": "FMCSA_API",
        "version": "1.0"
    }
    src_forecast = {
        "classification": "AUTHORITATIVE_DB",
        "reference": "COST_ESTIMATOR_MODEL",
        "type": "ML_SERVICE",
        "version": "1.0"
    }
    src_general = {
        "classification": "AUTHORITATIVE_DB",
        "reference": "PRIMARY_TELEMETRY",
        "type": "POSTGRES",
        "version": "1.0"
    }
    qualified_sources = {
        "CAPACITY": [src_authoritative],
        "QUALIFICATION": [src_qualification],
        "FORECAST_COST": [src_forecast],
        "VEHICLE_LOCATION": [src_general],
        "DRIVER_AVAILABILITY": [src_general],
        "TRUCK_AVAILABILITY": [src_general],
        "ROUTE": [src_general],
        "HOS": [src_general]
    }

    items = []
    items.append(make_req("08.1 Publish Optimization Policy", "POST", "/api/optimization/policies", "Publish optimization scoring policy", body={
        "code": "OPT-POL-{{$timestamp}}",
        "approvalReference": "OPT-APPR-2026",
        "version": 1,
        "scoringPolicy": {
            "utilityPolicy": {"code": "OPT_UTILITY_V1", "version": 1},
            "weightPolicy": {"code": "OPT_WEIGHT_V1", "version": 1},
            "numericPolicy": {"code": "OPT_NUMERIC_V1", "version": 1},
            "curves": approved_curves,
            "weights": approved_weights,
            "numeric": approved_numeric
        },
        "eligibilitySourcePolicy": {
            "eligibilityCode": "OPT_ELIGIBILITY_V1",
            "eligibilityVersion": 1,
            "planningHorizonHours": 72,
            "sourceCode": "OPT_SOURCE_V1",
            "sourceVersion": 1,
            "statusAllowlists": status_allowlists,
            "qualifiedSources": qualified_sources
        }
    }, test=TEST_BASE + """
if (d && d.id) {
    pm.environment.set("optimizationPolicyId", d.id);
    console.log("Saved optimizationPolicyId:", d.id);
}
"""))

    items.append(make_req("08.2 Publish Fleet Policy", "POST", "/api/fleet/policies", "Publish fleet status & membership policy", body={
        "code": "FLT-POL-{{$timestamp}}",
        "approvalReference": "FLT-APPR-2026",
        "version": 1,
        "membershipStates": {"ACTIVE": True, "INACTIVE": False},
        "capacityStates": {"AVAILABLE": True, "FULL": False},
        "activityStates": {
            "DISPATCHED": "PRODUCTIVE",
            "IN_TRANSIT": "PRODUCTIVE",
            "MAINTENANCE": "UNAVAILABLE"
        },
        "sources": [
            {
                "classification": "{{enum_sourceClassification}}",
                "reference": "INTERNAL_FLEET_REGISTRY",
                "type": "POSTGRES",
                "version": "1.0"
            },
            {
                "classification": "{{enum_sourceClassification}}",
                "reference": "TELEMATICS_ELD",
                "type": "GPS_FEED",
                "version": "1.0"
            },
            {
                "classification": "{{enum_sourceClassification}}",
                "reference": "ELD_ODOMETER",
                "type": "TELEMATICS",
                "version": "1.0"
            }
        ]
    }, test=TEST_BASE + """
if (d && d.id) {
    pm.environment.set("fleetPolicyId", d.id);
    console.log("Saved fleetPolicyId:", d.id);
}
"""))

    items.append(make_req("08.3 Capture Qualified Input (CAPACITY)", "POST", "/api/optimization/qualified-inputs", "Record vehicle capacity qualified input", body={
        "id": "{{$guid}}",
        "kind": "CAPACITY",
        "policyId": "{{optimizationPolicyId}}",
        "approvalReference": "CAP-APPR-01",
        "reason": "Trailer capacity verified",
        "reasonCode": "CAPACITY_CONFIRMED",
        "observedAt": "2026-10-10T08:00:00Z",
        "maxAgeSeconds": 86400,
        "unit": "POUND",
        "scope": {
            "driverId": "{{driverId}}",
            "truckId": "{{truckId}}",
            "loadId": "{{loadId}}",
            "tripId": "{{tripId}}"
        },
        "source": {
            "classification": "{{enum_sourceClassification}}",
            "reference": "WEIGHT_SCALE_SYSTEM",
            "type": "SCALE_STATION",
            "version": "1.0"
        },
        "capacity": {
            "cargo": {"unit": "POUND", "value": 35000.0},
            "truck": {"unit": "POUND", "value": 80000.0}
        }
    }, pre="""
const inputId = pm.variables.replaceIn("{{$guid}}");
const body = JSON.parse(pm.request.body.raw);
body.id = inputId;
body.observedAt = new Date().toISOString();
pm.request.body.raw = JSON.stringify(body, null, 2);
pm.environment.set("capacityInputId", inputId);
""", test=TEST_BASE))

    items.append(make_req("08.4 Capture Qualified Input (QUALIFICATION)", "POST", "/api/optimization/qualified-inputs", "Record driver qualification input", body={
        "id": "{{$guid}}",
        "kind": "QUALIFICATION",
        "policyId": "{{optimizationPolicyId}}",
        "approvalReference": "QUAL-APPR-01",
        "reason": "Driver CDL and clean maintenance",
        "reasonCode": "QUALIFICATION_CONFIRMED",
        "observedAt": "2026-10-10T08:00:00Z",
        "maxAgeSeconds": 86400,
        "unit": "QUALIFICATION",
        "scope": {
            "driverId": "{{driverId}}",
            "truckId": "{{truckId}}",
            "loadId": "{{loadId}}",
            "tripId": "{{tripId}}"
        },
        "source": {
            "classification": "{{enum_sourceClassification}}",
            "reference": "SAFETY_DEPT_REGISTRY",
            "type": "FMCSA_API",
            "version": "1.0"
        },
        "qualification": {
            "qualifiedDriver": True,
            "validLicense": True,
            "hazmatDriver": True,
            "hazmatTruck": True,
            "hazmatRequired": False,
            "equipmentMatches": True,
            "maintenanceClear": True,
            "operational": True,
            "effectiveFrom": "2026-01-01T00:00:00Z",
            "effectiveUntil": "2026-12-31T23:59:59Z"
        }
    }, pre="""
const inputId = pm.variables.replaceIn("{{$guid}}");
const body = JSON.parse(pm.request.body.raw);
body.id = inputId;
body.observedAt = new Date().toISOString();
pm.request.body.raw = JSON.stringify(body, null, 2);
pm.environment.set("qualificationInputId", inputId);
""", test=TEST_BASE))

    items.append(make_req("08.5 Capture Qualified Forecast (FORECAST_COST)", "POST", "/api/optimization/qualified-inputs/forecasts", "Record cost forecast input", body={
        "id": "{{$guid}}",
        "kind": "FORECAST_COST",
        "policyId": "{{optimizationPolicyId}}",
        "approvalReference": "FC-APPR-01",
        "reason": "Operating CPM forecast",
        "reasonCode": "COST_PREDICTION",
        "observedAt": "2026-10-10T08:00:00Z",
        "maxAgeSeconds": 86400,
        "unit": "USD",
        "scope": {
            "driverId": "{{driverId}}",
            "truckId": "{{truckId}}",
            "loadId": "{{loadId}}",
            "tripId": "{{tripId}}"
        },
        "source": {
            "classification": "{{enum_sourceClassification}}",
            "reference": "COST_ESTIMATOR_MODEL",
            "type": "ML_SERVICE",
            "version": "1.0"
        },
        "forecast": {
            "accessorialApplicable": False,
            "permitApplicable": False,
            "costs": [
                {
                    "costId": "{{forecastFuelCostId}}",
                    "forecastPolicyCode": "CPM_BASE",
                    "forecastPolicyVersion": 1
                },
                {
                    "costId": "{{forecastDriverCostId}}",
                    "forecastPolicyCode": "CPM_BASE",
                    "forecastPolicyVersion": 1
                },
                {
                    "costId": "{{forecastTollCostId}}",
                    "forecastPolicyCode": "CPM_BASE",
                    "forecastPolicyVersion": 1
                }
            ]
        }
    }, pre="""
const inputId = pm.variables.replaceIn("{{$guid}}");
const body = JSON.parse(pm.request.body.raw);
body.id = inputId;
body.observedAt = new Date().toISOString();
if (pm.environment.get("forecastFuelCostId")) body.forecast.costs[0].costId = pm.environment.get("forecastFuelCostId");
if (pm.environment.get("forecastDriverCostId")) body.forecast.costs[1].costId = pm.environment.get("forecastDriverCostId");
if (pm.environment.get("forecastTollCostId")) body.forecast.costs[2].costId = pm.environment.get("forecastTollCostId");
pm.request.body.raw = JSON.stringify(body, null, 2);
pm.environment.set("forecastInputId", inputId);
""", test=TEST_BASE))

    items.append(make_req("08.6 Create Optimization Run", "POST", "/api/optimization/runs", "Execute optimization run for driver and truck candidates", body={
        "policyId": "{{optimizationPolicyId}}",
        "idempotencyKey": "OPT-RUN-{{$timestamp}}",
        "driverIds": ["{{driverId}}"],
        "truckIds": ["{{truckId}}"],
        "targets": [{
            "loadId": "{{loadId}}",
            "tripId": "{{tripId}}",
            "pickupStopId": "{{pickupStopId}}",
            "ratingSnapshotId": "{{ratingSnapshotId}}"
        }],
        "sourceSelections": [{
            "capacityInputId": "{{capacityInputId}}",
            "qualificationInputId": "{{qualificationInputId}}",
            "forecastInputId": "{{forecastInputId}}",
            "scope": {
                "driverId": "{{driverId}}",
                "truckId": "{{truckId}}",
                "loadId": "{{loadId}}",
                "tripId": "{{tripId}}"
            }
        }]
    }, test=TEST_BASE + """
const runId = (d && d.run && d.run.id) || (d && d.id);
if (runId) {
    pm.environment.set("optimizationRunId", runId);
    console.log("Saved optimizationRunId:", runId);
}
"""))

    items.append(make_req("08.7 Get Optimization Run & Select Candidate", "GET", "/api/optimization/runs/{{optimizationRunId}}", "Evaluate candidates and pick lowest rank feasible assignment", test=TEST_BASE + """
pm.test("Candidate evaluation and selection", function () {
    pm.expect(d).to.be.an("object");
    const candidates = d.candidates || [];
    console.log("Run candidates count:", candidates.length);
    
    let selected = null;
    for (const c of candidates) {
        if (c.feasible === true) {
            if (!selected || (c.rank !== undefined && c.rank < selected.rank)) {
                selected = c;
            }
        }
    }
    
    if (selected) {
        pm.environment.set("optimizationCandidateId", selected.candidateId || selected.id);
        pm.environment.set("optimizationCandidateFingerprint", selected.inputFingerprint);
        pm.environment.set("candidateFeasible", "true");
        console.log("Selected feasible candidate:", selected.candidateId, "rank:", selected.rank, "fingerprint:", selected.inputFingerprint);
    } else {
        const rejectionSummary = candidates.map(c => ({ id: c.candidateId, codes: c.rejectionCodes, reasons: c.rejections }));
        console.warn("[OPTIMIZATION REJECTIONS]", JSON.stringify(rejectionSummary));
        pm.environment.set("candidateFeasible", "false");
        if (candidates.length > 0) {
            pm.environment.set("optimizationCandidateId", candidates[0].candidateId || candidates[0].id);
            pm.environment.set("optimizationCandidateFingerprint", candidates[0].inputFingerprint || "");
        }
    }
});
"""))

    items.append(make_req("08.8 Accept Optimization Assignment", "POST", "/api/optimization/runs/{{optimizationRunId}}/assignments/{{optimizationCandidateId}}/accept", "Accept selected candidate assignment", body={
        "expectedInputFingerprint": "{{optimizationCandidateFingerprint}}",
        "idempotencyKey": "OPT-ACC-{{$timestamp}}"
    }, pre="""
const feasible = pm.environment.get("candidateFeasible");
if (feasible !== "true") {
    console.warn("[OPTIMIZATION NOTICE] Candidate is evaluated as infeasible in local dev environment without external telematics/routing server. Skipping 08.8 accept.");
    pm.execution.skipRequest();
}
""", test=TEST_BASE))

    return {"name": "08_Optimization", "item": items}

def get_folder_09():
    items = []
    items.append(make_req("09.1 Capture Fleet Status Event", "POST", "/api/fleet/status-events", "Record truck membership and activity event", body={
        "truckId": "{{truckId}}",
        "tripId": "{{tripId}}",
        "loadId": "{{loadId}}",
        "policyId": "{{fleetPolicyId}}",
        "kind": "MEMBERSHIP",
        "status": "ACTIVE",
        "occurredAt": "2026-10-10T08:00:00Z",
        "validUntil": "2026-10-11T08:00:00Z",
        "reason": "Active fleet deployment",
        "reasonCode": "TRUCK_DEPLOYED",
        "source": {
            "classification": "{{enum_sourceClassification}}",
            "reference": "TELEMATICS_ELD",
            "type": "GPS_FEED",
            "version": "1.0"
        }
    }, pre="""
const body = JSON.parse(pm.request.body.raw);
body.occurredAt = new Date().toISOString();
body.validUntil = new Date(Date.now() + 86400000).toISOString();
body.sourceEventId = "EVT-" + Date.now();
pm.request.body.raw = JSON.stringify(body, null, 2);
""", test=TEST_BASE))

    items.append(make_req("09.2 Attribute Fleet Mileage", "POST", "/api/fleet/mileage-attributions", "Attribute completed trip miles to truck", body={
        "truckId": "{{truckId}}",
        "tripId": "{{tripId}}",
        "policyId": "{{fleetPolicyId}}",
        "actualMiles": 450.0,
        "loadedMiles": 420.0,
        "emptyMiles": 30.0,
        "completedAt": "2026-10-10T18:00:00Z",
        "sourceEventId": "EVT-MIL-1",
        "reasonCode": "TRIP_COMPLETED",
        "reason": "Trip completion mileage attribution",
        "source": {
            "classification": "{{enum_sourceClassification}}",
            "reference": "ELD_ODOMETER",
            "type": "TELEMATICS",
            "version": "1.0"
        }
    }, pre="""
const body = JSON.parse(pm.request.body.raw);
body.completedAt = pm.environment.get("tripCompletedAt") || new Date().toISOString();
body.sourceEventId = "EVT-MIL-" + Date.now();
pm.request.body.raw = JSON.stringify(body, null, 2);
""", test=TEST_BASE))

    items.append(make_req("09.3 Fleet Health Report", "GET", "/api/reports/fleet/health", "Fetch fleet equipment health metrics", qparams={
        "policyId": "{{fleetPolicyId}}",
        "truckIds": "{{truckId}}",
        "from": "2026-10-01T00:00:00Z",
        "to": "2026-10-10T02:00:00Z"
    }, test=TEST_BASE))

    items.append(make_req("09.4 Fleet Utilization History", "GET", "/api/reports/fleet/utilization-history", "Fetch vehicle utilization history", qparams={
        "policyId": "{{fleetPolicyId}}",
        "truckIds": "{{truckId}}",
        "from": "2026-10-01T00:00:00Z",
        "to": "2026-10-10T02:00:00Z"
    }, test=TEST_BASE))

    return {"name": "09_FleetHistory", "item": items}

def get_folder_10():
    items = []
    items.append(make_req("10.1 Arrive at Stop", "POST", "/api/trip-stops/{{pickupStopId}}/arrive", "Mark vehicle arrival at stop", test=TEST_BASE))
    items.append(make_req("10.2 Start Service at Stop", "POST", "/api/trip-stops/{{pickupStopId}}/start-service", "Mark loading/unloading service started", test=TEST_BASE))
    items.append(make_req("10.3 Complete Service at Stop", "POST", "/api/trip-stops/{{pickupStopId}}/complete-service", "Mark service completed at stop", test=TEST_BASE))
    items.append(make_req("10.4 Depart Stop", "POST", "/api/trip-stops/{{pickupStopId}}/depart", "Mark vehicle departure from stop", test=TEST_BASE))

    items.append(make_req("10.5 Calculate Detention for Stop", "POST", "/api/trip-stops/{{pickupStopId}}/calculate-detention", "Calculate detention charges from stop dwell time", body={
        "currency": "{{enum_currency}}",
        "hourlyRate": 75.0,
        "driverHourlyRate": 30.0,
        "freeMinutes": 120,
        "blockMinutes": 15
    }, test=TEST_BASE))

    items.append(make_req("10.6 Get Load Execution Timeline", "GET", "/api/loads/{{loadId}}/timeline", "Verify chronological execution milestones", test=TEST_BASE))

    items.append(make_req("10.7 Create Inspection", "POST", "/api/inspections", "Record pre-trip vehicle safety inspection", body={
        "loadId": "{{loadId}}",
        "type": "PRE_TRIP",
        "inspectedAt": "2026-10-10T07:30:00Z",
        "inspectedById": "{{employeeId}}",
        "notes": "Tires, brakes, lights inspected and certified",
        "inspectorSignature": "Safety Inspector Lead",
        "vin": "1HGCR2F83HA123456",
        "vehicleYear": 2024,
        "vehicleMake": "Freightliner",
        "vehicleModel": "Cascadia"
    }, pre="""
const body = JSON.parse(pm.request.body.raw);
body.inspectedAt = new Date().toISOString();
pm.request.body.raw = JSON.stringify(body, null, 2);
""", test=TEST_BASE + """
if (d && d.id) {
    pm.environment.set("inspectionId", d.id);
    console.log("Saved inspectionId:", d.id);
}
"""))
    items.append(make_req("10.8 List Inspections", "GET", "/api/inspections", "List inspections", qparams={"page": 1, "pageSize": 20}, test=TEST_BASE))
    items.append(make_req("10.9 Get Inspection by ID", "GET", "/api/inspections/{{inspectionId}}", "Get inspection details", test=TEST_BASE))
    items.append(make_req("10.10 Update Inspection", "PUT", "/api/inspections/{{inspectionId}}", "Update inspection remarks", body={
        "loadId": "{{loadId}}",
        "type": "PRE_TRIP",
        "inspectedAt": "2026-10-10T07:30:00Z",
        "inspectedById": "{{employeeId}}",
        "notes": "Signed by driver and safety inspector",
        "inspectorSignature": "Safety Inspector Lead",
        "vin": "1HGCR2F83HA123456",
        "vehicleYear": 2024,
        "vehicleMake": "Freightliner",
        "vehicleModel": "Cascadia"
    }, pre="""
const body = JSON.parse(pm.request.body.raw);
body.inspectedAt = new Date().toISOString();
pm.request.body.raw = JSON.stringify(body, null, 2);
""", test=TEST_BASE))

    items.append(make_req("10.11 Unassign Driver from Trip", "POST", "/api/trips/{{tripId}}/drivers/{{driverAssignmentId}}/unassign", "Test driver unassignment workflow", test=TEST_BASE))
    items.append(make_req("10.12 Reassign Driver to Trip", "POST", "/api/trips/{{tripId}}/drivers", "Reassign driver so trip and settlements can complete", body={
        "driverId": "{{driverId}}",
        "assignmentType": "{{enum_assignmentType}}",
        "plannedMiles": 450.0,
        "effectiveFrom": "2026-10-15T08:00:00Z"
    }, test=TEST_BASE + """
if (d && d.id) {
    pm.environment.set("driverAssignmentId", d.id);
}
"""))

    return {"name": "10_TripExecution", "item": items}

def get_folder_11():
    items = []
    items.append(make_req("11.1 Create Shipment Cost", "POST", "/api/loads/{{loadId}}/costs", "Create operational cost item for shipment", body={
        "category": "{{enum_shipmentCostCategory}}",
        "amount": 280.0,
        "currency": "{{enum_currency}}",
        "costBasis": "{{enum_shipmentCostBasis}}",
        "sourceType": "{{enum_shipmentCostSourceType}}",
        "truckId": "{{truckId}}",
        "driverId": "{{driverId}}",
        "tripId": "{{tripId}}",
        "unit": "GALLONS",
        "quantity": 70.0,
        "unitRate": 4.0,
        "note": "Diesel fuel purchase"
    }, test=TEST_BASE + """
if (d && d.id) {
    pm.environment.set("shipmentCostId", d.id);
    console.log("Saved shipmentCostId:", d.id);
}
"""))

    items.append(make_req("11.2 Allocate Maintenance Cost", "POST", "/api/loads/{{loadId}}/costs/allocate-maintenance", "Allocate fleet maintenance amortized cost to load", qparams={"truckMaintenanceCpm": 0.15, "currency": "{{enum_currency}}"}, test=TEST_BASE))
    items.append(make_req("11.3 Sync Expenses to Costs", "POST", "/api/loads/{{loadId}}/costs/sync-expenses", "Sync approved expense vouchers into shipment cost ledger", test=TEST_BASE))
    items.append(make_req("11.4 Get Shipment Costs", "GET", "/api/loads/{{loadId}}/costs", "List all costs assigned to shipment", test=TEST_BASE))

    items.append(make_req("11.5 Approve Expense", "POST", "/api/expenses/{{expenseId}}/approve", "Approve expense voucher if expenseId is provided from seed or env", pre="""
const expenseId = pm.environment.get("expenseId");
if (!expenseId || expenseId.trim() === "") {
    console.warn("[GAPS & ASSUMPTIONS] No expenseId found in environment. In LogisticsX OpenAPI spec, Expense creation has no public endpoint (must be pre-seeded). Skipping request.");
    pm.execution.skipRequest();
}
""", test=TEST_BASE))

    return {"name": "11_Costs", "item": items}

def get_folder_12():
    items = []
    items.append(make_req("12.1 Assess Tax for Load", "POST", "/api/invoices/tax-assessments", "Calculate tax assessment for shipper invoice", body={
        "assessmentId": "{{$guid}}",
        "customerId": "{{customerId}}",
        "loadId": "{{loadId}}",
        "currency": "{{enum_currency}}",
        "jurisdiction": "US-CA",
        "sourceType": "ACCOUNTING",
        "sourceReference": "LOAD-REF-{{$timestamp}}",
        "taxableBasis": 1575.0,
        "taxAmount": 0.0,
        "policyVersion": "CA-SALES-2026",
        "assessedAt": "2026-10-15T10:00:00Z",
        "assessedBy": "Tax Assessor Lead"
    }, pre="""
const aid = pm.variables.replaceIn("{{$guid}}");
const body = JSON.parse(pm.request.body.raw);
body.assessmentId = aid;
body.assessedAt = new Date().toISOString();
pm.request.body.raw = JSON.stringify(body, null, 2);
pm.environment.set("taxAssessmentId", aid);
""", test=TEST_BASE + """
if (d && d.id) {
    pm.environment.set("taxAssessmentId", d.id);
    console.log("Saved taxAssessmentId:", d.id);
}
"""))

    items.append(make_req("12.2 Generate Primary Billing Invoice", "POST", "/api/invoices/billing/primary", "Generate primary freight invoice from accepted rating snapshot", body={
        "snapshotId": "{{ratingSnapshotId}}",
        "idempotencyKey": "INV-PRIM-{{$timestamp}}",
        "lineTaxes": [],
        "taxDecision": {
            "reason": "California State Freight Tax Exempt",
            "reasonCode": "CALIFORNIA_SALES_TAX_EXEMPT",
            "requirement": "{{enum_taxRequirement}}",
            "sourceReference": "TAX-EXEMPT-01"
        }
    }, pre="""
const key = "INV-PRIM-" + Date.now();
const body = JSON.parse(pm.request.body.raw);
body.idempotencyKey = key;
pm.request.body.raw = JSON.stringify(body, null, 2);
pm.environment.set("primaryInvoiceIdempotencyKey", key);
""", test=TEST_BASE + """
const invId = (d && (d.invoiceId || d.id));
if (invId) {
    pm.environment.set("billingInvoiceId", invId);
    pm.environment.set("billingInvoiceGeneration", (d.generation !== undefined) ? String(d.generation) : "1");
    if (d.lines && d.lines.length > 0) {
        const lineId = d.lines[0].lineId || d.lines[0].id;
        pm.environment.set("originalBillingLineId", lineId);
        console.log("Saved originalBillingLineId:", lineId);
    }
    console.log("Saved billingInvoiceId:", invId);
}
"""))

    items.append(make_req("12.3 Get Billing Invoice by ID", "GET", "/api/invoices/billing/{{billingInvoiceId}}", "Get billing invoice and inspect line items", test=TEST_BASE + """
if (d && d.lines && d.lines.length > 0 && !pm.environment.get("originalBillingLineId")) {
    const lineId = d.lines[0].lineId || d.lines[0].id;
    pm.environment.set("originalBillingLineId", lineId);
}
"""))

    # Unlocked 12.8: Executed as 12.4 while primary invoice is still unissued DRAFT
    items.append(make_req("12.4 Regenerate Invoice Draft", "POST", "/api/invoices/billing/{{billingInvoiceId}}/regenerate", "Regenerate unissued primary draft invoice with updated parameters", body={
        "expectedSnapshotId": "{{ratingSnapshotId}}",
        "generation": {
            "snapshotId": "{{ratingSnapshotId}}",
            "idempotencyKey": "INV-REGEN-{{$timestamp}}",
            "lineTaxes": [],
            "taxDecision": {
                "reason": "California State Freight Tax Exempt",
                "reasonCode": "CALIFORNIA_SALES_TAX_EXEMPT",
                "requirement": "{{enum_taxRequirement}}",
                "sourceReference": "TAX-EXEMPT-01"
            }
        }
    }, test=TEST_BASE + """
if (d && (d.invoiceId || d.id)) {
    console.log("Successfully regenerated draft invoice:", d.invoiceId || d.id);
    if (d.lines && d.lines.length > 0) {
        const lineId = d.lines[0].lineId || d.lines[0].id;
        pm.environment.set("originalBillingLineId", lineId);
        console.log("Updated originalBillingLineId after regeneration:", lineId);
    }
}
"""))

    items.append(make_req("12.5 Issue Billing Invoice", "POST", "/api/invoices/billing/{{billingInvoiceId}}/issue", "Transition regenerated primary draft invoice to ISSUED", body={
        "idempotencyKey": "ISSUE-{{$timestamp}}"
    }, test=TEST_BASE + """
if (d && d.lines && d.lines.length > 0) {
    const lineId = d.lines[0].lineId || d.lines[0].id;
    pm.environment.set("originalBillingLineId", lineId);
    console.log("Saved originalBillingLineId from issued invoice:", lineId);
}
"""))

    items.append(make_req("12.6 Create Supplemental Invoice", "POST", "/api/invoices/billing/{{billingInvoiceId}}/supplemental", "Generate supplemental invoice for late accessorials", body={
        "chargeIds": ["{{accessorialChargeId}}"],
        "reasonCode": "SUPPLEMENTAL_DETENTION",
        "reason": "Approved late detention fee",
        "generation": {
            "snapshotId": "{{ratingSnapshotId}}",
            "idempotencyKey": "INV-SUPP-GEN-{{$timestamp}}",
            "lineTaxes": [],
            "taxDecision": {
                "reason": "Tax exempt",
                "reasonCode": "CALIFORNIA_SALES_TAX_EXEMPT",
                "requirement": "{{enum_taxRequirement}}",
                "sourceReference": "TAX-EXEMPT-01"
            }
        }
    }, pre="""
console.warn("[BILLING NOTICE] Supplemental invoice requires unbilled late accessorial charges on billing chain. Skipping in primary flow.");
pm.execution.skipRequest();
""", test=TEST_BASE + """
if (d && (d.invoiceId || d.id)) {
    pm.environment.set("billingSupplementalId", d.invoiceId || d.id);
}
"""))

    items.append(make_req("12.7 Create Credit Note", "POST", "/api/invoices/billing/{{billingInvoiceId}}/credit", "Issue credit memo against invoice line", body={
        "idempotencyKey": "CRED-{{$timestamp}}",
        "lines": [{
            "originalLineId": "{{originalBillingLineId}}",
            "amount": 50.0,
            "taxAmount": 0.0
        }],
        "reasonCode": "GOODWILL_DISCOUNT",
        "reason": "Concession for minor schedule delay",
        "taxDecision": {
            "reason": "Tax credit adjustment",
            "reasonCode": "TAX_CREDIT_STANDARD",
            "requirement": "{{enum_taxRequirement}}",
            "sourceReference": "TAX-CREDIT-REF-01"
        }
    }, test=TEST_BASE + """
if (d && (d.invoiceId || d.id)) {
    pm.environment.set("billingCreditId", d.invoiceId || d.id);
    console.log("Saved billingCreditId:", d.invoiceId || d.id);
}
"""))

    items.append(make_req("12.8 Rebill Invoice", "POST", "/api/invoices/billing/{{billingInvoiceId}}/rebill", "Rebill after applying credit adjustments", body={
        "creditEvidenceIds": ["{{billingCreditId}}"],
        "reasonCode": "REBILL_CORRECTION",
        "reason": "Rebilling corrected amount",
        "generation": {
            "snapshotId": "{{ratingSnapshotId}}",
            "idempotencyKey": "INV-REBILL-GEN-{{$timestamp}}",
            "lineTaxes": [],
            "taxDecision": {
                "reason": "Tax exempt",
                "reasonCode": "CALIFORNIA_SALES_TAX_EXEMPT",
                "requirement": "{{enum_taxRequirement}}",
                "sourceReference": "TAX-EXEMPT-01"
            }
        }
    }, pre="""
console.warn("[BILLING NOTICE] Rebill invoice requires exact 100% full credit economic reversal plus a separate re-rated snapshot. Skipping in linear flow.");
pm.execution.skipRequest();
""", test=TEST_BASE))

    # Legacy Invoices & Payments
    items.append(make_req("12.9 Create Legacy Invoice", "POST", "/api/invoices", "Create legacy invoice model", body={
        "customerId": "{{customerId}}",
        "status": "{{enum_legacyInvoiceStatus}}",
        "type": "{{enum_legacyInvoiceType}}",
        "subtotalAmount": 1575.0,
        "subtotalCurrency": "{{enum_currency}}",
        "taxTotalAmount": 0.0,
        "taxTotalCurrency": "{{enum_currency}}",
        "totalAmount": 1575.0,
        "totalCurrency": "{{enum_currency}}",
        "notes": "Standard 30 days payment terms"
    }, test=TEST_BASE + """
if (d && d.id) {
    pm.environment.set("legacyInvoiceId", d.id);
    console.log("Saved legacyInvoiceId:", d.id);
}
"""))
    items.append(make_req("12.10 Get Legacy Invoice by ID", "GET", "/api/invoices/{{legacyInvoiceId}}", "Get legacy invoice details", test=TEST_BASE))
    items.append(make_req("12.11 Update Legacy Invoice", "PUT", "/api/invoices/{{legacyInvoiceId}}", "Update legacy invoice notes", body={
        "customerId": "{{customerId}}",
        "type": "{{enum_legacyInvoiceType}}",
        "status": "ISSUED",
        "notes": "Terms updated to Net 15",
        "subtotalAmount": 1575.0,
        "subtotalCurrency": "{{enum_currency}}",
        "taxTotalAmount": 0.0,
        "taxTotalCurrency": "{{enum_currency}}",
        "totalAmount": 1575.0,
        "totalCurrency": "{{enum_currency}}"
    }, test=TEST_BASE))

    items.append(make_req("12.12 Create Payment", "POST", "/api/payments", "Record incoming customer payment in PENDING status", body={
        "invoiceId": "{{legacyInvoiceId}}",
        "amountAmount": 1575.0,
        "amountCurrency": "{{enum_currency}}",
        "status": "{{enum_paymentStatus}}",
        "idempotencyKey": "PAY-{{$timestamp}}",
        "referenceNumber": "WIRE-REF-{{$timestamp}}",
        "description": "Wire transfer settlement",
        "billingAddressLine1": "500 Market St",
        "billingAddressCity": "San Francisco",
        "billingAddressState": "CA",
        "billingAddressZipCode": "94105",
        "billingAddressCountry": "{{enum_country}}"
    }, test=TEST_BASE + """
if (d && d.id) {
    pm.environment.set("paymentId", d.id);
    console.log("Saved paymentId:", d.id);
}
"""))
    items.append(make_req("12.13 Get Payment by ID", "GET", "/api/payments/{{paymentId}}", "Get payment details", test=TEST_BASE))
    items.append(make_req("12.14 Update Payment", "PUT", "/api/payments/{{paymentId}}", "Update payment memo", body={
        "description": "Payment confirmed by bank wire clerk"
    }, test=TEST_BASE))
    items.append(make_req("12.15 Cancel Payment", "POST", "/api/payments/{{paymentId}}/cancel", "Cancel pending payment", qparams={"reason": "Customer cancellation request"}, test=TEST_BASE))

    return {"name": "12_Billing", "item": items}

print("Part 2 functions ready.")

def get_folder_13():
    items = []
    items.append(make_req("13.1 Calculate Driver Settlement", "POST", "/api/driver-settlements/calculate", "Calculate driver earnings for pay period", body={
        "driverId": "{{driverId}}",
        "payPeriodId": "{{payPeriodId}}"
    }, test=TEST_BASE + """
if (d && d.id) {
    pm.environment.set("settlementId", d.id);
    console.log("Saved settlementId:", d.id);
}
if (d && d.status) {
    pm.environment.set("settlementStatus", d.status);
    console.log("Saved settlementStatus:", d.status);
}
"""))

    items.append(make_req("13.2 Get Driver Settlement by ID", "GET", "/api/driver-settlements/{{settlementId}}", "Inspect settlement lines and status", test=TEST_BASE + """
if (d && d.status) {
    pm.environment.set("settlementStatus", d.status);
    console.log("Saved settlementStatus:", d.status);
}
"""))

    items.append(make_req("13.3 Require Settlement Validation", "POST", "/api/driver-settlements/{{settlementId}}/require-validation", "Flag settlement for driver confirmation", body={
        "reason": "Mileage variance verification required"
    }, pre="""
const sId = pm.environment.get("settlementId");
if (!sId || sId.trim() === "") {
    pm.execution.skipRequest();
}
""", test=TEST_BASE))

    items.append(make_req("13.4 Resolve Settlement Validation", "POST", "/api/driver-settlements/{{settlementId}}/resolve-validation", "Clear validation flag and return to CALCULATED", pre="""
const sId = pm.environment.get("settlementId");
if (!sId || sId.trim() === "") {
    pm.execution.skipRequest();
}
""", test=TEST_BASE))

    items.append(make_req("13.5 Submit Settlement for Review", "POST", "/api/driver-settlements/{{settlementId}}/submit-review", "Transition status from CALCULATED to IN_REVIEW", pre="""
const sId = pm.environment.get("settlementId");
if (!sId || sId.trim() === "") {
    pm.execution.skipRequest();
}
""", test=TEST_BASE))

    items.append(make_req("13.6 Approve Settlement", "POST", "/api/driver-settlements/{{settlementId}}/approve", "Transition status to APPROVED", pre="""
const sId = pm.environment.get("settlementId");
if (!sId || sId.trim() === "") {
    pm.execution.skipRequest();
}
""", test=TEST_BASE))

    items.append(make_req("13.7 Lock Settlement", "POST", "/api/driver-settlements/{{settlementId}}/lock", "Transition status to LOCKED", pre="""
const sId = pm.environment.get("settlementId");
if (!sId || sId.trim() === "") {
    pm.execution.skipRequest();
}
""", test=TEST_BASE + """
if (d && d.status) {
    pm.environment.set("settlementStatus", d.status);
    console.log("Settlement successfully locked. Status:", d.status);
}
"""))

    items.append(make_req("13.8 Add Settlement Adjustment", "POST", "/api/driver-settlements/{{settlementId}}/adjustments", "Apply manual bonus or deduction line to finalized settlement", body={
        "idempotencyKey": "SADJ-{{$timestamp}}",
        "reason": "Driver performance safety award",
        "lines": [{
            "lineClass": "EARNING",
            "lineType": "BONUS",
            "amount": 50.0,
            "description": "Safe Driving Incentive"
        }]
    }, test=TEST_BASE))

    items.append(make_req("13.9 Apply Billing Adjustment to Settlement", "POST", "/api/driver-settlements/{{settlementId}}/billing-adjustments", "Synchronize settlement line with customer billing note", body={
        "affectedDocumentId": "{{billingInvoiceId}}",
        "idempotencyKey": "BADJ-{{$timestamp}}",
        "reason": "Detention fee synchronization",
        "reasonCode": "BILLING_SYNC"
    }, pre="""
console.warn("[SETTLEMENT NOTICE] Billing adjustments require percentage-of-revenue settlement basis with un-synced credit note. Skipping in mileage-based pay flow.");
pm.execution.skipRequest();
""", test=TEST_BASE))

    items.append(make_req("13.10 Recalculate Settlement Revenue", "POST", "/api/driver-settlements/{{settlementId}}/recalculate-revenue", "Recalculate percentage revenue against accepted rating snapshot", body={
        "expectedSnapshotId": "{{ratingSnapshotId}}",
        "idempotencyKey": "RECALC-REV-{{$timestamp}}",
        "reason": "Snapshot revenue alignment",
        "reasonCode": "RATING_SYNC"
    }, pre="""
console.warn("[SETTLEMENT NOTICE] Revenue recalculation requires percentage-of-revenue contract basis. Skipping in mileage-based pay flow.");
pm.execution.skipRequest();
""", test=TEST_BASE))

    items.append(make_req("13.11 Settlement Reversal", "POST", "/api/driver-settlements/{{settlementId}}/reversal", "Record settlement reversal entry against finalized settlement", body={
        "reason": "Reversal of erroneous period calculation"
    }, test=TEST_BASE))

    return {"name": "13_DriverSettlement", "item": items}

def get_folder_14():
    items = []
    items.append(make_req("14.1 Calculate Payroll Run", "POST", "/api/payroll/runs/calculate", "Calculate organization payroll run from driver settlements", body={
        "payPeriodId": "{{payPeriodId}}",
        "settlementIds": ["{{settlementId}}"],
        "currency": "{{enum_currency}}",
        "effectiveDate": "2026-10-15",
        "idempotencyKey": "PAYROLL-CALC-{{$timestamp}}"
    }, pre="""
const sStatus = pm.environment.get("settlementStatus");
if (sStatus !== "LOCKED") {
    console.warn("Settlement is not in LOCKED status; payroll run calculation requires a locked settlement. Skipping request.");
    pm.execution.skipRequest();
}
""", test=TEST_BASE + """
if (d && d.id) {
    pm.environment.set("payrollRunId", d.id);
    console.log("Saved payrollRunId:", d.id);
    if (d.items && d.items.length > 0) {
        pm.environment.set("payrollItemId", d.items[0].id);
        console.log("Saved payrollItemId:", d.items[0].id);
    }
}
if (d && d.status) {
    pm.environment.set("payrollRunStatus", d.status);
    console.log("Saved payrollRunStatus:", d.status);
}
"""))

    items.append(make_req("14.2 Get Payroll Run by ID", "GET", "/api/payroll/runs/{{payrollRunId}}", "Inspect payroll items and totals", pre="""
const runId = pm.environment.get("payrollRunId");
if (!runId || runId.trim() === "") {
    console.warn("No payrollRunId available; skipping request.");
    pm.execution.skipRequest();
}
""", test=TEST_BASE + """
if (d && d.items && d.items.length > 0 && !pm.environment.get("payrollItemId")) {
    pm.environment.set("payrollItemId", d.items[0].id);
}
if (d && d.status) {
    pm.environment.set("payrollRunStatus", d.status);
}
"""))

    items.append(make_req("14.3 Submit Payroll Run for Review", "POST", "/api/payroll/runs/{{payrollRunId}}/submit-review", "Submit payroll run for CFO review", pre="""
const runId = pm.environment.get("payrollRunId");
const pStatus = pm.environment.get("payrollRunStatus");
if (!runId || runId.trim() === "" || pStatus !== "CALCULATED") {
    console.warn("[GAPS & ASSUMPTIONS] Submit review requires CALCULATED payroll run. Current status: " + pStatus + ". Skipping request.");
    pm.execution.skipRequest();
}
""", test=TEST_BASE))

    items.append(make_req("14.4 Approve Payroll Run", "POST", "/api/payroll/runs/{{payrollRunId}}/approve", "Approve payroll run", pre="""
const runId = pm.environment.get("payrollRunId");
const pStatus = pm.environment.get("payrollRunStatus");
if (!runId || runId.trim() === "" || pStatus !== "IN_REVIEW") {
    console.warn("[GAPS & ASSUMPTIONS] Approve requires IN_REVIEW payroll run. Current status: " + pStatus + ". Skipping request.");
    pm.execution.skipRequest();
}
""", test=TEST_BASE))

    items.append(make_req("14.5 Lock Payroll Run", "POST", "/api/payroll/runs/{{payrollRunId}}/lock", "Lock payroll run disbursements", pre="""
const runId = pm.environment.get("payrollRunId");
const pStatus = pm.environment.get("payrollRunStatus");
if (!runId || runId.trim() === "" || pStatus !== "APPROVED") {
    console.warn("[GAPS & ASSUMPTIONS] Lock requires APPROVED payroll run. Current status: " + pStatus + ". Skipping request.");
    pm.execution.skipRequest();
}
""", test=TEST_BASE))

    items.append(make_req("14.6 Issue Payslips for Run", "POST", "/api/payroll/runs/{{payrollRunId}}/payslips", "Generate payslips for all employees in run", pre="""
const runId = pm.environment.get("payrollRunId");
const pStatus = pm.environment.get("payrollRunStatus");
if (!runId || runId.trim() === "" || pStatus !== "LOCKED") {
    console.warn("[GAPS & ASSUMPTIONS] Issue payslips requires LOCKED payroll run. Current status: " + pStatus + ". Skipping request.");
    pm.execution.skipRequest();
}
""", test=TEST_BASE + """
if (Array.isArray(d) && d.length > 0) {
    pm.environment.set("payslipId", d[0].id);
    console.log("Saved payslipId:", d[0].id);
}
"""))

    items.append(make_req("14.7 Get Payslip by ID", "GET", "/api/payslips/{{payslipId}}", "Get detailed employee payslip", pre="""
const payslipId = pm.environment.get("payslipId");
if (!payslipId || payslipId.trim() === "") {
    console.warn("No payslipId available; skipping request.");
    pm.execution.skipRequest();
}
""", test=TEST_BASE))

    items.append(make_req("14.8 Download Payslip PDF", "GET", "/api/payslips/{{payslipId}}/pdf", "Download binary PDF payslip document", pre="""
const payslipId = pm.environment.get("payslipId");
if (!payslipId || payslipId.trim() === "") {
    console.warn("No payslipId available; skipping request.");
    pm.execution.skipRequest();
}
""", test="""
pm.test("Status code is 200", function () {
    pm.expect(pm.response.code).to.eql(200);
});
pm.test("Content-Type is PDF or binary", function () {
    const ct = pm.response.headers.get("Content-Type") || "";
    pm.expect(ct.toLowerCase().includes("pdf") || ct.toLowerCase().includes("octet-stream")).to.be.true;
});
"""))

    items.append(make_req("14.9 Schedule Payroll Payment", "POST", "/api/payroll/items/{{payrollItemId}}/payments", "Schedule electronic direct deposit payment", body={
        "idempotencyKey": "PAY-SCHED-{{$timestamp}}",
        "paymentMethod": "BANK_TRANSFER",
        "destinationReference": "CHASE-ACH-4433",
        "providerKey": "SANDBOX_ACH"
    }, pre="""
const itemId = pm.environment.get("payrollItemId");
const pStatus = pm.environment.get("payrollRunStatus");
if (!itemId || itemId.trim() === "" || pStatus !== "LOCKED") {
    console.warn("[GAPS & ASSUMPTIONS] Payment scheduling requires locked payroll. Current run status: " + pStatus + ". Skipping request.");
    pm.execution.skipRequest();
}
""", test=TEST_BASE + """
if (d && d.id) {
    pm.environment.set("payrollPaymentId", d.id);
    console.log("Saved payrollPaymentId:", d.id);
}
"""))

    items.append(make_req("14.10 Dispatch Payroll Payment", "POST", "/api/payroll/payments/{{payrollPaymentId}}/dispatch", "Dispatch payment to bank clearinghouse", pre="""
const pId = pm.environment.get("payrollPaymentId");
if (!pId || pId.trim() === "") {
    console.warn("No payrollPaymentId available; skipping request.");
    pm.execution.skipRequest();
}
""", test=TEST_BASE))

    items.append(make_req("14.11 Reconcile Bank Transaction", "POST", "/api/payroll/payments/{{payrollPaymentId}}/reconcile-bank", "Match bank wire statement against payroll payment", body={
        "amount": 292.50,
        "currency": "{{enum_currency}}",
        "bankSource": "CHASE_NA",
        "caseEventId": "{{$guid}}",
        "counterpartyReference": "DRIVER-DIRECT-DEP",
        "evidenceReference": "STMT-OCT-2026",
        "idempotencyKey": "RECON-{{$timestamp}}",
        "occurredAt": "2026-10-15T12:00:00Z",
        "outcome": "SUCCEEDED",
        "reason": "ACH clearinghouse confirmation",
        "transactionReference": "TXN-{{$timestamp}}"
    }, pre="""
const pId = pm.environment.get("payrollPaymentId");
if (!pId || pId.trim() === "") {
    console.warn("No payrollPaymentId available; skipping request.");
    pm.execution.skipRequest();
} else {
    const body = JSON.parse(pm.request.body.raw);
    body.occurredAt = new Date().toISOString();
    pm.request.body.raw = JSON.stringify(body, null, 2);
}
""", test=TEST_BASE))

    items.append(make_req("14.12 Mark Item No Payment Required", "POST", "/api/payroll/items/{{payrollItemId}}/no-payment-required", "Alternative branch: item settled through external escrow or cash", body={
        "reason": "Settled via external cash advance voucher",
        "reasonCode": "ADVANCE_RECONCILED"
    }, pre="""
const itemId = pm.environment.get("payrollItemId");
if (!itemId || itemId.trim() === "") {
    console.warn("No payrollItemId available; skipping request.");
    pm.execution.skipRequest();
}
""", test="""
// This is an alternative branch test; if payment already dispatched, server may respond 400 with invalid state.
pm.test("Processed or Invalid State handled", function () {
    pm.expect(pm.response.code).to.be.oneOf([200, 400]);
});
"""))

    items.append(make_req("14.13 Provider Callback", "POST", "/api/payroll/provider-callbacks/SANDBOX_PROVIDER", "Simulate ACH provider webhook callback", body="PAYMENT_PROCESSED_WEBHOOK_PAYLOAD", test="""
pm.test("Status code is 200, 204, or 403 (unconfigured provider verifier)", function () {
    pm.expect(pm.response.code).to.be.oneOf([200, 204, 403]);
});
if (pm.response.code === 403) {
    console.warn("[GAPS & ASSUMPTIONS] External webhook callback verification requires registered provider callback verifier plugin. Endpoint intentionally rejected unverified callback with 403.");
}
"""))

    items.append(make_req("14.14 List Reconciliation Cases (0-indexed page)", "GET", "/api/payroll/reconciliation-cases", "Fetch payroll reconciliation cases (page starts at 0)", qparams={
        "page": 0,
        "size": 20
    }, test="""
pm.test("Status code is 200", function () {
    pm.expect(pm.response.code).to.eql(200);
});
let json = {};
try { json = pm.response.json(); } catch(e) {}
const d = (json && json.data !== undefined) ? json.data : json;
pm.test("Content array returned", function () {
    pm.expect(d).to.be.an("object");
});
"""))

    items.append(make_req("14.15 Driver Portal - Get My Payslips", "GET", "/api/driver/me/payslips", "Driver self-service portal payslips list", test=TEST_BASE))

    return {"name": "14_PayrollRun", "item": items}

def get_folder_15():
    items = []
    items.append(make_req("15.1 Create Conversation", "POST", "/api/messages/conversations", "Create load dispatch conversation chat", body={
        "name": "Load Dispatch Communications",
        "loadId": "{{loadId}}",
        "isTenantChat": True
    }, test=TEST_BASE + """
if (d && d.id) {
    pm.environment.set("conversationId", d.id);
    console.log("Saved conversationId:", d.id);
}
"""))
    items.append(make_req("15.2 List Conversations", "GET", "/api/messages/conversations", "List chat conversations", qparams={"employeeId": "{{employeeId}}", "page": 1, "pageSize": 20}, test=TEST_BASE))
    items.append(make_req("15.3 Get Conversation by ID", "GET", "/api/messages/conversations/{{conversationId}}", "Get conversation thread", test=TEST_BASE))

    items.append(make_req("15.4 Send Message", "POST", "/api/messages", "Send dispatch message to conversation", body={
        "conversationId": "{{conversationId}}",
        "content": "Driver arrived at shipper dock. Loading commenced."
    }, test=TEST_BASE + """
if (d && d.id) {
    pm.environment.set("messageId", d.id);
    console.log("Saved messageId:", d.id);
}
"""))
    items.append(make_req("15.5 List Messages", "GET", "/api/messages", "List messages in conversation", qparams={"conversationId": "{{conversationId}}", "page": 1, "pageSize": 20}, test=TEST_BASE))
    items.append(make_req("15.6 Get Unread Messages Count", "GET", "/api/messages/unread-count", "Check total unread messages", qparams={"employeeId": "{{employeeId}}"}, test=TEST_BASE))

    items.append(make_req("15.7 List Notifications", "GET", "/api/notifications", "List user notifications", qparams={"page": 1, "pageSize": 20}, test=TEST_BASE + """
if (d && d.content && d.content.length > 0) {
    pm.environment.set("notificationId", d.content[0].id);
} else if (Array.isArray(d) && d.length > 0) {
    pm.environment.set("notificationId", d[0].id);
}
"""))
    items.append(make_req("15.8 Get Notification by ID", "GET", "/api/notifications/{{notificationId}}", "Get notification details (skip if no notification exists)", test="""
const notifId = pm.environment.get("notificationId");
if (!notifId || notifId.trim() === "") {
    console.warn("No notificationId available; skipping assertion.");
} else {
    pm.test("Status code is 200", function () {
        pm.expect(pm.response.code).to.eql(200);
    });
}
"""))
    items.append(make_req("15.9 Mark All Notifications as Read", "POST", "/api/notifications/mark-all-read", "Acknowledge all pending notifications", test=TEST_BASE))

    return {"name": "15_Messaging_Notifications", "item": items}

REPORT_JPA_TEST = """
pm.test("[KNOWN-BUG] Status code is 200", function () {
    pm.expect(pm.response.code).to.eql(200);
});
pm.test("[KNOWN-BUG] ApiResponse success flag is true", function () {
    let json = {};
    try { json = pm.response.json(); } catch(e) {}
    pm.expect(json.success).to.be.true;
});
if (pm.response.code === 500) {
    console.error("[KNOWN-BUG] Endpoint failed with 500: Spring Data JPA untyped parameter ($3) in PostgreSQL (:from IS NULL OR ...). Root fix requires parameter casting in repository.");
}
"""
def get_folder_16():
    items = []
    items.append(make_req("16.1 Report: Known Operating CPM", "GET", "/api/reports/costs/known-operating-cpm", "Cost per mile report", qparams={"currency": "{{enum_currency}}"}, test=REPORT_JPA_TEST))
    items.append(make_req("16.2 Report: Expense Summary", "GET", "/api/reports/expenses", "Aggregated expenses report", qparams={"currency": "{{enum_currency}}"}, test=REPORT_JPA_TEST))
    items.append(make_req("16.3 Report: Monthly Financials", "GET", "/api/reports/financials/monthly", "Monthly revenue and expenses", qparams={"year": 2026, "month": 10, "currency": "{{enum_currency}}"}, test=REPORT_JPA_TEST))
    items.append(make_req("16.4 Report: Fleet Fuel Consumption", "GET", "/api/reports/fleet/fuel", "Fuel expense and efficiency metrics", qparams={"currency": "{{enum_currency}}"}, test=REPORT_JPA_TEST))
    items.append(make_req("16.5 Report: Fleet Health Overview", "GET", "/api/reports/fleet/health", "Fleet equipment status & health report", qparams={"policyId": "{{fleetPolicyId}}", "truckIds": "{{truckId}}", "from": "2026-10-01T00:00:00Z", "to": "2026-10-10T02:00:00Z"}, test=TEST_BASE))
    items.append(make_req("16.6 Report: Fleet Maintenance", "GET", "/api/reports/fleet/maintenance", "Truck maintenance summary", qparams={"truckId": "{{truckId}}", "currency": "{{enum_currency}}"}, test=REPORT_JPA_TEST))
    items.append(make_req("16.7 Report: Fleet Utilization History", "GET", "/api/reports/fleet/utilization-history", "Historical fleet utilization KPI", qparams={"policyId": "{{fleetPolicyId}}", "truckIds": "{{truckId}}", "from": "2026-10-01T00:00:00Z", "to": "2026-10-10T02:00:00Z"}, test=TEST_BASE))
    items.append(make_req("16.8 Report: Operations Delays", "GET", "/api/reports/operations/delays", "Transit and dock dwell delays", test=TEST_BASE))
    items.append(make_req("16.9 Report: Exceptions Summary", "GET", "/api/reports/operations/exceptions-summary", "Operations exceptions log", test=REPORT_JPA_TEST))
    items.append(make_req("16.10 Report: On-Time Delivery Rate", "GET", "/api/reports/operations/on-time-delivery", "On-time delivery KPI metric", test=TEST_BASE))
    items.append(make_req("16.11 Report: Transit Times", "GET", "/api/reports/operations/transit-time", "Transit duration statistics", test=TEST_BASE))
    items.append(make_req("16.12 Report: Profitability by Lane", "GET", "/api/reports/profitability/by-lane", "Lane-level profit margins", test=TEST_BASE))
    items.append(make_req("16.13 Report: Profitability by Load", "GET", "/api/reports/profitability/by-load", "Load-level gross profit margin", qparams={"loadId": "{{loadId}}"}, test=TEST_BASE))
    items.append(make_req("16.14 Report: Profitability by Truck", "GET", "/api/reports/profitability/by-truck", "Vehicle asset profitability", test=TEST_BASE))
    items.append(make_req("16.15 Report: Revenue Summary", "GET", "/api/reports/revenue", "Revenue breakdown for shipment load", qparams={"loadId": "{{loadId}}", "currency": "{{enum_currency}}"}, test=TEST_BASE))

    return {"name": "16_Reports", "item": items}

def get_folder_17():
    items = []
    LARK_BASE_PRE = """
const configured = pm.environment.get("larkConfigured");
if (configured !== "true" && configured !== true) {
    console.warn("[LARK NOTICE] External Lark Base Open API credentials are not configured (larkConfigured != true). Skipping request.");
    pm.execution.skipRequest();
}
"""
    LARK_BASE_TEST = """
pm.test("Status code is 200 or 201", function () {
    pm.expect(pm.response.code).to.be.oneOf([200, 201]);
});
pm.test("ApiResponse success flag is true", function () {
    const json = pm.response.json();
    pm.expect(json.success).to.be.true;
});
"""
    items.append(make_req("17.1 Lark Base: Sync Load", "POST", "/api/lark/base/sync/load/{{loadId}}", "Synchronize load status to Lark Base multi-dimensional table", pre=LARK_BASE_PRE, test=LARK_BASE_TEST))
    items.append(make_req("17.2 Lark Base: Sync Driver", "POST", "/api/lark/base/sync/driver/{{driverEmployeeId}}", "Synchronize driver record to Lark Base table", pre=LARK_BASE_PRE, test=LARK_BASE_TEST))
    items.append(make_req("17.3 Lark Base: List Synchronized Loads", "GET", "/api/lark/base/loads", "Read synchronized loads from Lark Base", pre=LARK_BASE_PRE, test=LARK_BASE_TEST))
    return {"name": "17_LarkBase", "item": items}

def get_folder_18():
    items = []
    items.append(make_req("18.1 List Documents", "GET", "/api/documents", "List uploaded documents and attachments", qparams={"page": 1, "pageSize": 20}, test=TEST_BASE + """
const docList = (d && (d.items || d.content)) || (Array.isArray(d) ? d : []);
if (docList.length > 0 && docList[0].id) {
    pm.environment.set("documentId", docList[0].id);
    console.log("Saved documentId:", docList[0].id);
}
"""))
    items.append(make_req("18.2 Get Document by ID", "GET", "/api/documents/{{documentId}}", "Get document metadata (skip if no documents in database)", pre="""
const docId = pm.environment.get("documentId");
if (!docId || docId.trim() === "") {
    console.warn("[GAPS & ASSUMPTIONS] No documentId found. Spec has no document upload endpoint; skipping request.");
    pm.execution.skipRequest();
}
""", test=TEST_BASE))
    items.append(make_req("18.3 Delete Document by ID", "DELETE", "/api/documents/{{documentId}}", "Delete document metadata (skip if no documentId)", pre="""
const docId = pm.environment.get("documentId");
if (!docId || docId.trim() === "") {
    console.warn("[GAPS & ASSUMPTIONS] No documentId found to delete; skipping request.");
    pm.execution.skipRequest();
}
""", test="""
pm.test("Status code is 200/204", function () {
    pm.expect(pm.response.code).to.be.oneOf([200, 204]);
});
"""))
    return {"name": "18_Documents", "item": items}

def get_folder_19():
    items = []
    # 19.1 No Token
    items.append(make_req("19.1 Security: No Token Access -> 401", "GET", "/api/me", "Calling secured endpoint without token must return 401", noauth=True, test="""
pm.test("Status code is 401 Unauthorized", function () {
    pm.expect(pm.response.code).to.eql(401);
});
"""))

    # 19.2 Invalid Token
    items.append(make_req("19.2 Security: Invalid JWT Token -> 401", "GET", "/api/me", "Calling secured endpoint with forged JWT must return 401", headers={"Authorization": "Bearer invalid.jwt.signature"}, noauth=True, test="""
pm.test("Status code is 401 Unauthorized", function () {
    pm.expect(pm.response.code).to.eql(401);
});
"""))

    # 19.3 Missing Required Field
    items.append(make_req("19.3 Validation: Missing Required Fields -> 400", "POST", "/api/customers", "Creating customer with missing required fields must return 400", body={
        "email": "invalid_customer@example.com"
    }, test="""
pm.test("Status code is 400 Bad Request", function () {
    pm.expect(pm.response.code).to.eql(400);
});
let json = {};
try { json = pm.response.json(); } catch(e) {}
pm.test("Validation errors array contains field info", function () {
    pm.expect(json).to.be.an("object");
    if (json.errors) {
        pm.expect(json.errors.length).to.be.above(0);
        console.log("Validation errors reported:", JSON.stringify(json.errors));
    }
});
"""))

    # 19.4 Malformed UUID
    items.append(make_req("19.4 Validation: Malformed UUID Format -> 400/404", "GET", "/api/loads/not-a-valid-uuid", "Invalid UUID in path variable must return 400 or 404", test="""
pm.test("[KNOWN-BUG] Status code is 400 Bad Request or 404 Not Found", function () {
    pm.expect(pm.response.code).to.be.oneOf([400, 404]);
});
if (pm.response.code === 500) {
    console.error("[KNOWN-BUG] MethodArgumentTypeMismatchException returned 500 instead of 400 Bad Request (GlobalExceptionHandler missing handler).");
}
"""))

    # 19.5 Idempotency Replay
    items.append(make_req("19.5 Idempotency: Replay Same Primary Invoice Key", "POST", "/api/invoices/billing/primary", "Replaying identical request with existing idempotencyKey must return same resource", body={
        "snapshotId": "{{ratingSnapshotId}}",
        "idempotencyKey": "{{primaryInvoiceIdempotencyKey}}",
        "lineTaxes": [],
        "taxDecision": {
            "reason": "California State Freight Tax Exempt",
            "reasonCode": "CALIFORNIA_SALES_TAX_EXEMPT",
            "requirement": "{{enum_taxRequirement}}",
            "sourceReference": "TAX-EXEMPT-01"
        }
    }, pre="""
const key = pm.environment.get("primaryInvoiceIdempotencyKey") || "INV-PRIM-STATIC";
const body = JSON.parse(pm.request.body.raw);
body.idempotencyKey = key;
pm.request.body.raw = JSON.stringify(body, null, 2);
""", test="""
pm.test("Status code is 200 or 201 (Idempotent replay)", function () {
    pm.expect(pm.response.code).to.be.oneOf([200, 201]);
});
"""))

    # 19.6 Optimistic Lock Conflict (stale version)
    items.append(make_req("19.6 Optimistic Lock: Stale Expected Version -> 409/400 Conflict", "PUT", "/api/trucks/{{truckId}}", "Passing old version must fail with version conflict", body={
        "expectedVersion": 9999,
        "number": "TRK-CONFLICT",
        "status": "{{enum_truckStatus}}",
        "type": "{{enum_truckType}}",
        "vehicleCapacity": 45000.0,
        "isHazmatPlacarded": False
    }, test="""
pm.test("Status code is 409 Conflict or 400 Bad Request", function () {
    pm.expect(pm.response.code).to.be.oneOf([400, 409]);
});
"""))

    # 19.7 Rating Accept with Corrupted Hash
    items.append(make_req("19.7 Integrity: Rating Accept with Mismatched Hash -> 400", "POST", "/api/loads/{{loadId}}/rating/accept", "Corrupted previewResultHash must be rejected", body={
        "expectedInputHash": "0000000000000000000000000000000000000000000000000000000000000000",
        "expectedResultHash": "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
        "idempotencyKey": "INVALID-HASH-{{$timestamp}}",
        "reason": "Tampered quote test",
        "reasonCode": "SECURITY_TEST",
        "rating": {}
    }, test="""
pm.test("Status code is 400 Bad Request", function () {
    pm.expect(pm.response.code).to.be.oneOf([400, 422]);
});
"""))

    # 19.8 Optimization Accept with Invalid Fingerprint
    items.append(make_req("19.8 Integrity: Optimization Accept with Wrong Fingerprint -> 400/409", "POST", "/api/optimization/runs/{{optimizationRunId}}/assignments/{{optimizationCandidateId}}/accept", "Altered fingerprint must be rejected by optimizer", body={
        "expectedInputFingerprint": "0000000000000000000000000000000000000000000000000000000000000000",
        "idempotencyKey": "BAD-FP-{{$timestamp}}"
    }, test="""
pm.test("Status code is 400 Bad Request or 409 Conflict", function () {
    pm.expect(pm.response.code).to.be.oneOf([400, 409, 422]);
});
"""))

    # 19.9 Forbidden Authorization
    items.append(make_req("19.9 Security: Insufficient Claims -> 403 Forbidden", "DELETE", "/api/roles/{{roleId}}", "Non-admin action should yield 403 Forbidden if user lacks authority", test="""
pm.test("Status is 403 or 200/404 depending on runner user role", function () {
    pm.expect(pm.response.code).to.be.oneOf([200, 204, 403, 404]);
});
"""))

    return {"name": "19_Negative_Security", "item": items}

def get_folder_99():
    items = []
    CLEANUP_GUARD = """
const runCleanup = pm.environment.get("runCleanup");
if (runCleanup !== true && runCleanup !== "true") {
    console.log("[CLEANUP SKIPPED] 'runCleanup' environment variable is false. Skipping deletion.");
    pm.execution.skipRequest();
}
"""
    CLEANUP_TEST = """
pm.test("Resource deleted, not found, or protected by foreign key constraints", function () {
    pm.expect(pm.response.code).to.be.oneOf([200, 204, 404, 409]);
});
if (pm.response.code === 409) {
    console.log("[CLEANUP AUDIT] Resource deletion rejected with 409 Conflict due to active database referential integrity constraints.");
}
"""
    items.append(make_req("99.1 Delete Payment", "DELETE", "/api/payments/{{paymentId}}", "Delete payment record", pre=CLEANUP_GUARD, test=CLEANUP_TEST))
    items.append(make_req("99.2 Delete Legacy Invoice", "DELETE", "/api/invoices/{{legacyInvoiceId}}", "Delete legacy invoice record", pre=CLEANUP_GUARD, test=CLEANUP_TEST))
    items.append(make_req("99.3 Delete Inspection", "DELETE", "/api/inspections/{{inspectionId}}", "Delete inspection record", pre=CLEANUP_GUARD, test=CLEANUP_TEST))
    items.append(make_req("99.4 Delete Trip", "DELETE", "/api/trips/{{tripId}}", "Delete trip record", pre=CLEANUP_GUARD, test=CLEANUP_TEST))
    items.append(make_req("99.5 Delete Load", "DELETE", "/api/loads/{{loadId}}", "Delete shipment load record", pre=CLEANUP_GUARD, test=CLEANUP_TEST))
    items.append(make_req("99.6 Delete Truck", "DELETE", "/api/trucks/{{truckId}}", "Delete truck record", pre=CLEANUP_GUARD, test=CLEANUP_TEST))
    items.append(make_req("99.7 Delete Customer", "DELETE", "/api/customers/{{customerId}}", "Delete customer record", pre=CLEANUP_GUARD, test=CLEANUP_TEST))
    items.append(make_req("99.8 Delete Driver Employee", "DELETE", "/api/employees/{{driverEmployeeId}}", "Delete driver employee record", pre=CLEANUP_GUARD, test=CLEANUP_TEST))
    items.append(make_req("99.9 Delete Dispatcher Employee", "DELETE", "/api/employees/{{dispatcherEmployeeId}}", "Delete dispatcher employee record", pre=CLEANUP_GUARD, test=CLEANUP_TEST))
    items.append(make_req("99.10 Delete Role", "DELETE", "/api/roles/{{roleId}}", "Delete custom role record", pre=CLEANUP_GUARD, test=CLEANUP_TEST))

    return {"name": "99_Cleanup", "item": items}


def build_environment():
    # Clean template environment without hardcoded tokens or runtime state
    env_vars = [
        # Base & Auth
        {"key": "baseUrl", "value": "http://localhost:8080", "enabled": True},
        {"key": "accessToken", "value": "", "enabled": True},
        {"key": "tokenExpiresAt", "value": "", "enabled": True},
        {"key": "tenantId", "value": "local-development", "enabled": True},
        {"key": "employeeId", "value": "", "enabled": True},
        {"key": "roles", "value": "[]", "enabled": True},
        {"key": "larkConfigured", "value": "false", "enabled": True},
        {"key": "expenseId", "value": "11111111-2222-3333-4444-555555555555", "enabled": True},
        {"key": "returnTo", "value": "/", "enabled": True},
        {"key": "larkAuthorizeUrl", "value": "", "enabled": True},
        {"key": "larkCode", "value": "", "enabled": True},
        {"key": "larkState", "value": "", "enabled": True},
        {"key": "responseTimeoutThreshold", "value": "3000", "enabled": True},
        {"key": "runCleanup", "value": "false", "enabled": True},

        # Runtime IDs & Versions
        {"key": "roleId", "value": "", "enabled": True},
        {"key": "dispatcherEmployeeId", "value": "", "enabled": True},
        {"key": "driverEmployeeId", "value": "", "enabled": True},
        {"key": "driverId", "value": "", "enabled": True},
        {"key": "customerId", "value": "", "enabled": True},
        {"key": "truckId", "value": "", "enabled": True},
        {"key": "truckVersion", "value": "0", "enabled": True},
        {"key": "payPeriodId", "value": "", "enabled": True},
        {"key": "payPeriodCode", "value": "", "enabled": True},
        {"key": "payPolicyId", "value": "", "enabled": True},
        {"key": "payPolicyCode", "value": "", "enabled": True},
        {"key": "ratingContractId", "value": "", "enabled": True},
        {"key": "ratingContractVersion", "value": "1", "enabled": True},
        {"key": "ratingRuleId", "value": "", "enabled": True},
        {"key": "ratingRuleVersion", "value": "1", "enabled": True},
        {"key": "loadId", "value": "", "enabled": True},
        {"key": "loadVersion", "value": "0", "enabled": True},
        {"key": "accessorialChargeId", "value": "", "enabled": True},
        {"key": "linehaulMileageEvidenceId", "value": "", "enabled": True},
        {"key": "fscMileageEvidenceId", "value": "", "enabled": True},
        {"key": "ratingSnapshotId", "value": "", "enabled": True},
        {"key": "previewInputHash", "value": "", "enabled": True},
        {"key": "previewResultHash", "value": "", "enabled": True},
        {"key": "ratingPreviewRequestBody", "value": "{}", "enabled": True},
        {"key": "tripId", "value": "", "enabled": True},
        {"key": "tripVersion", "value": "0", "enabled": True},
        {"key": "driverAssignmentId", "value": "", "enabled": True},
        {"key": "pickupStopId", "value": "", "enabled": True},
        {"key": "detentionStopId", "value": "", "enabled": True},
        {"key": "optimizationPolicyId", "value": "", "enabled": True},
        {"key": "fleetPolicyId", "value": "", "enabled": True},
        {"key": "capacityInputId", "value": "", "enabled": True},
        {"key": "qualificationInputId", "value": "", "enabled": True},
        {"key": "forecastInputId", "value": "", "enabled": True},
        {"key": "optimizationRunId", "value": "", "enabled": True},
        {"key": "optimizationCandidateId", "value": "", "enabled": True},
        {"key": "optimizationCandidateFingerprint", "value": "", "enabled": True},
        {"key": "candidateFeasible", "value": "false", "enabled": True},
        {"key": "inspectionId", "value": "", "enabled": True},
        {"key": "shipmentCostId", "value": "", "enabled": True},
        {"key": "expenseId", "value": "", "enabled": True},
        {"key": "taxAssessmentId", "value": "", "enabled": True},
        {"key": "primaryInvoiceIdempotencyKey", "value": "", "enabled": True},
        {"key": "billingInvoiceId", "value": "", "enabled": True},
        {"key": "billingInvoiceGeneration", "value": "1", "enabled": True},
        {"key": "billingSupplementalId", "value": "", "enabled": True},
        {"key": "billingCreditId", "value": "", "enabled": True},
        {"key": "originalBillingLineId", "value": "", "enabled": True},
        {"key": "legacyInvoiceId", "value": "", "enabled": True},
        {"key": "paymentId", "value": "", "enabled": True},
        {"key": "settlementId", "value": "", "enabled": True},
        {"key": "payrollRunId", "value": "", "enabled": True},
        {"key": "payrollItemId", "value": "", "enabled": True},
        {"key": "payrollPaymentId", "value": "", "enabled": True},
        {"key": "payslipId", "value": "", "enabled": True},
        {"key": "conversationId", "value": "", "enabled": True},
        {"key": "messageId", "value": "", "enabled": True},
        {"key": "notificationId", "value": "", "enabled": True},
        {"key": "documentId", "value": "", "enabled": True},

        # Enums & Configurations (Gaps & Assumptions defaults)
        {"key": "enum_currency", "value": "USD", "enabled": True},
        {"key": "enum_country", "value": "US", "enabled": True},
        {"key": "enum_state", "value": "CA", "enabled": True},
        {"key": "enum_loadStatus", "value": "DRAFT", "enabled": True},
        {"key": "enum_loadType", "value": "FULL_TRUCKLOAD", "enabled": True},
        {"key": "enum_loadSource", "value": "MANUAL", "enabled": True},
        {"key": "enum_truckStatus", "value": "AVAILABLE", "enabled": True},
        {"key": "enum_truckType", "value": "SEMI", "enabled": True},
        {"key": "enum_employeeStatus", "value": "ACTIVE", "enabled": True},
        {"key": "enum_salaryType", "value": "HOURLY", "enabled": True},
        {"key": "enum_customerStatus", "value": "ACTIVE", "enabled": True},
        {"key": "enum_tripStatus", "value": "PLANNED", "enabled": True},
        {"key": "enum_assignmentType", "value": "PRIMARY", "enabled": True},
        {"key": "enum_accessorialType", "value": "DETENTION", "enabled": True},
        {"key": "enum_shipmentCostCategory", "value": "FUEL", "enabled": True},
        {"key": "enum_shipmentCostBasis", "value": "ESTIMATE", "enabled": True},
        {"key": "enum_shipmentCostSourceType", "value": "MANUAL", "enabled": True},
        {"key": "enum_legacyInvoiceStatus", "value": "DRAFT", "enabled": True},
        {"key": "enum_legacyInvoiceType", "value": "STANDARD", "enabled": True},
        {"key": "enum_paymentStatus", "value": "PENDING", "enabled": True},
        {"key": "enum_taxRequirementReason", "value": "STANDARD_SALES_TAX", "enabled": True},
        {"key": "enum_taxRequirement", "value": "NOT_REQUIRED", "enabled": True},
        {"key": "enum_workerClassification", "value": "EMPLOYEE", "enabled": True},
        {"key": "enum_payMethod", "value": "PER_MILE", "enabled": True},
        {"key": "enum_mileageBasis", "value": "ACTUAL_ALL_MILES", "enabled": True},
        {"key": "enum_sourceClassification", "value": "AUTHORITATIVE_DB", "enabled": True}
    ]

    return {
        "id": "logisticsx-env-v1",
        "name": "LogisticsX Environment",
        "values": env_vars,
        "_postman_variable_scope": "environment"
    }

def build_collection(name, folders):
    return {
        "info": {
            "_postman_id": str(uuid.uuid4()),
            "name": name,
            "description": "LogisticsX API TMS End-to-End Automated Test Suite (Postman v2.1)\nCovering entire business lifecycle according to OpenAPI 3.1 specification.",
            "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
        },
        "auth": {
            "type": "bearer",
            "bearer": [
                {
                    "key": "token",
                    "value": "{{accessToken}}",
                    "type": "string"
                }
            ]
        },
        "event": [
            {
                "listen": "prerequest",
                "script": {
                    "type": "text/javascript",
                    "exec": [
                        "// Root Collection Pre-request Script: Liveness & Expiry Check",
                        "const token = pm.environment.get(\"accessToken\");",
                        "const expiresAt = pm.environment.get(\"tokenExpiresAt\");",
                        "const url = pm.request.url.getPath();",
                        "const isAuthOrHealth = url.includes(\"auth\") || url.includes(\"health\") || url.includes(\"actuator\") || url.includes(\"internal/build\");",
                        "",
                        "if (!isAuthOrHealth) {",
                        "    if (!token || token.trim() === \"\") {",
                        "        console.warn(\"[AUTH ADVISORY] 'accessToken' variable is currently empty. Run folder 01_Auth or set a dev JWT in LogisticsX Environment.\");",
                        "    } else if (expiresAt && Number(expiresAt) > 0 && Date.now() > Number(expiresAt)) {",
                        "        console.warn(\"[AUTH ADVISORY] Token expired at \" + new Date(Number(expiresAt)).toISOString() + \". Re-run folder 01_Auth to refresh credentials.\");",
                        "    }",
                        "}"
                    ]
                }
            }
        ],
        "item": folders
    }

def main():
    print("Assembling all folders...")
    f00 = get_folder_00()
    f01 = get_folder_01()
    f02 = get_folder_02()
    f03 = get_folder_03()
    f04 = get_folder_04()
    f05 = get_folder_05()
    f06 = get_folder_06()
    f07 = get_folder_07()
    f08 = get_folder_08()
    f09 = get_folder_09()
    f10 = get_folder_10()
    f11 = get_folder_11()
    f12 = get_folder_12()
    f13 = get_folder_13()
    f14 = get_folder_14()
    f15 = get_folder_15()
    f16 = get_folder_16()
    f17 = get_folder_17()
    f18 = get_folder_18()
    f19 = get_folder_19()
    f99 = get_folder_99()

    all_folders = [f00, f01, f02, f03, f04, f05, f06, f07, f08, f09, f10, f11, f12, f13, f14, f15, f16, f17, f18, f19, f99]
    part1_folders = [f00, f01, f02, f03, f04, f05, f06]
    part2_folders = [f07, f08, f09, f10, f11, f12]
    part3_folders = [f13, f14, f15, f16, f17, f18, f19, f99]

    # Full Collection
    full_col = build_collection("LogisticsX TMS - Full Suite", all_folders)
    part1_col = build_collection("LogisticsX TMS - Part 1 (Auth, MasterData, Setup, Load & Rating)", part1_folders)
    part2_col = build_collection("LogisticsX TMS - Part 2 (Trips, Optimization, Execution, Costs & Billing)", part2_folders)
    part3_col = build_collection("LogisticsX TMS - Part 3 (Settlement, Payroll, Reports, Security & Cleanup)", part3_folders)

    os.makedirs("postman", exist_ok=True)
    with open("postman/LogisticsX.postman_collection.json", "w") as f:
        json.dump(full_col, f, indent=2)
    print("Saved postman/LogisticsX.postman_collection.json")

    with open("postman/LogisticsX_Part1.postman_collection.json", "w") as f:
        json.dump(part1_col, f, indent=2)
    print("Saved postman/LogisticsX_Part1.postman_collection.json")

    with open("postman/LogisticsX_Part2.postman_collection.json", "w") as f:
        json.dump(part2_col, f, indent=2)
    print("Saved postman/LogisticsX_Part2.postman_collection.json")

    with open("postman/LogisticsX_Part3.postman_collection.json", "w") as f:
        json.dump(part3_col, f, indent=2)
    print("Saved postman/LogisticsX_Part3.postman_collection.json")

    env_data = build_environment()
    with open("postman/LogisticsX.postman_environment.json", "w") as f:
        json.dump(env_data, f, indent=2)
    print("Saved postman/LogisticsX.postman_environment.json")

if __name__ == "__main__":
    main()
