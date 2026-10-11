import re
import json

with open("scripts/build_full_suite.py") as f:
    code = f.read()

# 1. Fix 02.1 Create Role & 02.4 Update Role (claims)
old_role_create = '''    items.append(make_req("02.1 Create Role", "POST", "/api/roles", "Create security role", body={
        "name": "Fleet Dispatcher Lead",
        "code": "DISPATCHER_LEAD",
        "description": "Lead dispatcher role"
    }, test=TEST_BASE + """'''
new_role_create = '''    items.append(make_req("02.1 Create Role", "POST", "/api/roles", "Create security role", body={
        "name": "DISPATCHER_LEAD_{{$timestamp}}",
        "displayName": "Fleet Dispatcher Lead",
        "claims": [{
            "claimType": "PERMISSION",
            "claimValue": "DISPATCH"
        }]
    }, test=TEST_BASE + """'''
if old_role_create in code:
    code = code.replace(old_role_create, new_role_create, 1)

old_role_update = '''    items.append(make_req("02.4 Update Role", "PUT", "/api/roles/{{roleId}}", "Update role definition", body={
        "name": "Senior Fleet Dispatcher Lead",
        "description": "Updated lead dispatcher role with audit rights"
    }, test=TEST_BASE))'''
new_role_update = '''    items.append(make_req("02.4 Update Role", "PUT", "/api/roles/{{roleId}}", "Update role definition", body={
        "name": "DISPATCHER_LEAD_{{$timestamp}}",
        "displayName": "Senior Fleet Dispatcher Lead",
        "claims": [{
            "claimType": "PERMISSION",
            "claimValue": "DISPATCH"
        }]
    }, test=TEST_BASE))'''
if old_role_update in code:
    code = code.replace(old_role_update, new_role_update, 1)

# 2. Fix 02.5 & 02.6 Employee joinedDate
code = code.replace('"joinedDate": "2026-01-01",', '"joinedDate": "2026-01-01T00:00:00Z",')

# 3. Fix 02.7, 02.9, 02.17 search query param
code = code.replace('make_req("02.7 List Employees", "GET", "/api/employees", "List employees", qparams={"page": 1, "pageSize": 20}',
                    'make_req("02.7 List Employees", "GET", "/api/employees", "List employees", qparams={"search": "", "page": 1, "pageSize": 20}')
code = code.replace('make_req("02.9 List Drivers", "GET", "/api/drivers", "List active drivers", qparams={"page": 1, "pageSize": 20}',
                    'make_req("02.9 List Drivers", "GET", "/api/drivers", "List active drivers", qparams={"search": "", "page": 1, "pageSize": 20}')
code = code.replace('make_req("02.17 List Trucks", "GET", "/api/trucks", "List fleet trucks", qparams={"page": 1, "pageSize": 20}',
                    'make_req("02.17 List Trucks", "GET", "/api/trucks", "List fleet trucks", qparams={"search": "", "page": 1, "pageSize": 20}')

# 4. Fix Folder 04: RatingSetup
old_f04 = '''def get_folder_04():
    items = []
    items.append(make_req("04.1 Create Rating Contract", "POST", "/api/rating/contracts", "Create customer freight contract", body={
        "customerId": "{{customerId}}",
        "currency": "{{enum_currency}}",
        "effectiveFrom": "2026-01-01",
        "effectiveTo": "2026-12-31"
    }, test=TEST_BASE + """
if (d && d.id) {
    pm.environment.set("ratingContractId", d.id);
    pm.environment.set("ratingContractVersion", "1");
    console.log("Saved ratingContractId:", d.id);
}
"""))

    items.append(make_req("04.2 Create Contract Version", "POST", "/api/rating/contracts/{{ratingContractId}}/versions", "Add version to contract", body={
        "effectiveFrom": "2026-06-01",
        "effectiveTo": "2026-12-31"
    }, test=TEST_BASE + """
if (d && d.version !== undefined) {
    pm.environment.set("ratingContractVersion", String(d.version));
}
"""))

    items.append(make_req("04.3 Get Contract Version", "GET", "/api/rating/contracts/{{ratingContractId}}/versions/{{ratingContractVersion}}", "Fetch contract version details", test=TEST_BASE))

    items.append(make_req("04.4 Create Rating Rule", "POST", "/api/rating/rules", "Create linehaul and FSC rule", body={
        "contractId": "{{ratingContractId}}",
        "contractVersion": 1,
        "customerId": "{{customerId}}",
        "baseRate": 3.50,
        "currency": "{{enum_currency}}",
        "method": "PER_MILE",
        "linehaulMileageBasis": "CONTRACT_MILES",
        "priority": 1,
        "effectiveFrom": "2026-01-01",
        "fsc": {
            "method": "PER_MILE",
            "baseRate": 0.45,
            "currency": "{{enum_currency}}",
            "effectiveFrom": "2026-01-01"
        }
    }, pre="""
const cv = Number(pm.environment.get("ratingContractVersion")) || 1;
const body = JSON.parse(pm.request.body.raw);
body.contractVersion = cv;
pm.request.body.raw = JSON.stringify(body, null, 2);
""", test=TEST_BASE + """
if (d && d.id) {
    pm.environment.set("ratingRuleId", d.id);
    pm.environment.set("ratingRuleVersion", "1");
    console.log("Saved ratingRuleId:", d.id);
}
"""))

    items.append(make_req("04.5 Create Rating Rule Version", "POST", "/api/rating/rules/{{ratingRuleId}}/versions", "Create new version of rating rule", body={
        "baseRate": 3.75,
        "currency": "{{enum_currency}}",
        "method": "PER_MILE",
        "priority": 1,
        "effectiveFrom": "2026-06-01"
    }, test=TEST_BASE + """
if (d && d.version !== undefined) {
    pm.environment.set("ratingRuleVersion", String(d.version));
}
"""))

    items.append(make_req("04.6 Get Rating Rule Version", "GET", "/api/rating/rules/{{ratingRuleId}}/versions/{{ratingRuleVersion}}", "Fetch rating rule version details", test=TEST_BASE))

    return {"name": "04_RatingSetup", "item": items}'''

new_f04 = '''def get_folder_04():
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
        "priority": 1,
        "effectiveFrom": "2026-01-01",
        "effectiveTo": "2026-12-31"
    }, pre="""
const cv = Number(pm.environment.get("ratingContractVersion")) || 1;
const body = JSON.parse(pm.request.body.raw);
body.contractVersion = cv;
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
    }, test=TEST_BASE + """
if (d && d.version !== undefined) {
    pm.environment.set("ratingRuleVersion", String(d.version));
}
"""))

    items.append(make_req("04.6 Get Rating Rule Version", "GET", "/api/rating/rules/{{ratingRuleId}}/versions/{{ratingRuleVersion}}", "Fetch rating rule version details", test=TEST_BASE))

    return {"name": "04_RatingSetup", "item": items}'''

if old_f04 in code:
    code = code.replace(old_f04, new_f04, 1)

# 5. Fix Folder 05: Load
old_f05 = '''def get_folder_05():
    items = []
    items.append(make_req("05.1 Create Load", "POST", "/api/loads", "Create freight load for shipper", body={
        "name": "Full Truckload Shipment #{{$timestamp}}",
        "status": "{{enum_loadStatus}}",
        "type": "{{enum_loadType}}",
        "source": "{{enum_loadSource}}",
        "customerId": "{{customerId}}",
        "weight": 35000.0,
        "length": 53.0,
        "isHazmat": False,
        "isInProximity": False,
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
        "name": "Updated Freight Load",
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
        "businessDate": "2026-10-15",
        "reason": "Shipper schedule request",
        "reasonCode": "SHIPPER_SCHEDULE_CONFIRMED"
    }, test=TEST_BASE))

    return {"name": "05_Load", "item": items}'''

new_f05 = '''def get_folder_05():
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

    return {"name": "05_Load", "item": items}'''

if old_f05 in code:
    code = code.replace(old_f05, new_f05, 1)

# 6. Fix 06.1 and 06.2 originalUnit: MILE
code = code.replace('"originalUnit": "MILES",', '"originalUnit": "MILE",')

with open("scripts/build_full_suite.py", "w") as f:
    f.write(code)

print("Applied all fixes to scripts/build_full_suite.py successfully!")
