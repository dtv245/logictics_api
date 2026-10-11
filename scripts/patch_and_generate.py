import re
import json
import os

with open('scripts/build_full_suite.py', 'r') as f:
    code = f.read()

# 1. Patch 01.3 Lark OAuth Callback
old_013 = '''    items.append(make_req("01.3 Lark OAuth Callback", "POST", "/api/auth/lark/callback", "Process OAuth code/state returned by Lark. Run manually after copying code/state or bypass if using dev JWT.", body={
        "code": "{{larkCode}}",
        "state": "{{larkState}}",
        "returnTo": "{{returnTo}}"
    }, noauth=True, test="""'''
new_013 = '''    items.append(make_req("01.3 Lark OAuth Callback", "POST", "/api/auth/lark/callback", "Process OAuth code/state returned by Lark. Run manually after copying code/state or bypass if using dev JWT.", body={
        "code": "{{larkCode}}",
        "state": "{{larkState}}",
        "returnTo": "{{returnTo}}"
    }, noauth=True, pre="""
const larkCode = pm.environment.get("larkCode");
if (!larkCode || larkCode.trim() === "") {
    console.warn("[AUTH INFO] larkCode is empty. Skipping OAuth exchange callback (assuming developer is using pre-configured accessToken).");
    pm.execution.skipRequest();
}
""", test="""'''
assert old_013 in code, "Failed to find old 01.3"
code = code.replace(old_013, new_013, 1)

# 2. Patch Folder 06
old_f06 = '''def get_folder_06():
    items = []
    items.append(make_req("06.1 Add Contract Mileage (LINEHAUL)", "POST", "/api/loads/{{loadId}}/rating/contract-mileage", "Record resolved linehaul mileage evidence", body={
        "componentType": "LINEHAUL",
        "originalUnit": "MILES",
        "originalValue": 450.0,
        "provenance": "CONTRACT_TABLE",
        "contractId": "{{ratingContractId}}",
        "contractVersion": 1
    }, pre="""
const cv = Number(pm.environment.get("ratingContractVersion")) || 1;
const body = JSON.parse(pm.request.body.raw);
body.contractVersion = cv;
pm.request.body.raw = JSON.stringify(body, null, 2);
""", test=TEST_BASE))

    items.append(make_req("06.2 Add Contract Mileage (FSC)", "POST", "/api/loads/{{loadId}}/rating/contract-mileage", "Record resolved FSC mileage evidence", body={
        "componentType": "FSC",
        "originalUnit": "MILES",
        "originalValue": 450.0,
        "provenance": "INDEX_EVIDENCE",
        "contractId": "{{ratingContractId}}",
        "contractVersion": 1
    }, pre="""
const cv = Number(pm.environment.get("ratingContractVersion")) || 1;
const body = JSON.parse(pm.request.body.raw);
body.contractVersion = cv;
pm.request.body.raw = JSON.stringify(body, null, 2);
""", test=TEST_BASE))

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
        "accessorialIds": ["{{accessorialChargeId}}"]
    }, pre="""
const cv = Number(pm.environment.get("ratingContractVersion")) || 1;
const body = JSON.parse(pm.request.body.raw);
body.contractVersion = cv;
pm.request.body.raw = JSON.stringify(body, null, 2);
""", test=TEST_BASE + """
if (d) {
    pm.test("Preview contains hashes", function () {
        pm.expect(d.inputHash).to.be.a("string").and.not.empty;
        pm.expect(d.resultHash).to.be.a("string").and.not.empty;
    });
    pm.environment.set("previewInputHash", d.inputHash);
    pm.environment.set("previewResultHash", d.resultHash);
    pm.environment.set("ratingPreviewObj", JSON.stringify(d));
    console.log("Saved previewInputHash:", d.inputHash);
}
"""))

    items.append(make_req("06.6 Rating - Accept", "POST", "/api/loads/{{loadId}}/rating/accept", "Accept pricing snapshot", body={
        "expectedInputHash": "{{previewInputHash}}",
        "expectedResultHash": "{{previewResultHash}}",
        "idempotencyKey": "RATE-ACC-{{$timestamp}}",
        "reason": "Shipper agreed rate quote",
        "reasonCode": "QUOTE_ACCEPTED",
        "rating": {}
    }, pre="""
const previewObj = JSON.parse(pm.environment.get("ratingPreviewObj") || "{}");
const body = JSON.parse(pm.request.body.raw);
body.rating = previewObj;
body.expectedInputHash = pm.environment.get("previewInputHash");
body.expectedResultHash = pm.environment.get("previewResultHash");
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

    return {"name": "06_RatingFlow", "item": items}'''

new_f06 = '''def get_folder_06():
    items = []
    items.append(make_req("06.1 Add Contract Mileage (LINEHAUL)", "POST", "/api/loads/{{loadId}}/rating/contract-mileage", "Record resolved linehaul mileage evidence", body={
        "componentType": "LINEHAUL",
        "originalUnit": "MILES",
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
        "originalUnit": "MILES",
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
        "fscMileageEvidenceId": "{{fscMileageEvidenceId}}",
        "accessorialIds": ["{{accessorialChargeId}}"]
    }, pre="""
const cv = Number(pm.environment.get("ratingContractVersion")) || 1;
const body = JSON.parse(pm.request.body.raw);
body.contractVersion = cv;
if (pm.environment.get("linehaulMileageEvidenceId")) {
    body.linehaulMileageEvidenceId = pm.environment.get("linehaulMileageEvidenceId");
}
if (pm.environment.get("fscMileageEvidenceId")) {
    body.fscMileageEvidenceId = pm.environment.get("fscMileageEvidenceId");
}
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

    return {"name": "06_RatingFlow", "item": items}'''
assert old_f06 in code, "Failed to find old Folder 06"
code = code.replace(old_f06, new_f06, 1)

# 3. Patch Folder 07 (07.5 no fallback UUID)
old_075 = '''    items.append(make_req("07.5 Get Trip Stops", "GET", "/api/trips/{{tripId}}/stops", "List stops of trip to extract pickupStopId", test=TEST_BASE + """
pm.test("Stops array exists", function () {
    pm.expect(d).to.be.an("array");
    if (d.length > 0) {
        pm.environment.set("pickupStopId", d[0].id);
        pm.environment.set("detentionStopId", d[0].id);
        console.log("Saved pickupStopId:", d[0].id);
    } else {
        console.warn("[ASSUMPTION / SEED NOTE] No trip stops found in response. If DB does not auto-seed stops on load-trip assignment, a seeded or dummy UUID will be used for downstream execution.");
        const fallbackStopId = pm.environment.get("pickupStopId") || "{{$guid}}";
        pm.environment.set("pickupStopId", fallbackStopId);
    }
});
"""))'''

new_075 = '''    items.append(make_req("07.5 Get Trip Stops", "GET", "/api/trips/{{tripId}}/stops", "List stops of trip to extract pickupStopId", test=TEST_BASE + """
pm.test("Trip has at least one stop", function () {
    pm.expect(d).to.be.an("array").and.not.empty;
    pm.environment.set("pickupStopId", d[0].id);
    pm.environment.set("detentionStopId", d[0].id);
    console.log("Saved pickupStopId:", d[0].id);
});
"""))'''
assert old_075 in code, "Failed to find old 07.5"
code = code.replace(old_075, new_075, 1)

# 4. Patch Folder 08 (Confirmed V1 policy + units/sources)
old_f08 = '''def get_folder_08():
    items = []
    items.append(make_req("08.1 Publish Optimization Policy", "POST", "/api/optimization/policies", "Publish optimization scoring policy", body={
        "code": "OPT-POL-{{$timestamp}}",
        "approvalReference": "OPT-APPR-2026",
        "version": 1,
        "scoringPolicy": {
            "weights": {
                "DEADHEAD_MILES": 0.35,
                "DRIVER_HOS": 0.30,
                "ON_TIME_PROBABILITY": 0.35
            },
            "curves": {
                "DEADHEAD_MILES": [
                    {"raw": 0.0, "utility": 1.0},
                    {"raw": 50.0, "utility": 0.8},
                    {"raw": 150.0, "utility": 0.2}
                ]
            },
            "numeric": {
                "roundingMode": "HALF_UP",
                "scoreScale": 4,
                "weightScale": 4,
                "utilityScale": 4,
                "intermediatePrecision": 6,
                "contributionScale": 4
            }
        },
        "eligibilitySourcePolicy": {
            "authoritativeSource": "Internal Policy Engine",
            "calculatorKey": "OPT_ELIGIBILITY_V1",
            "configurationJson": "{\\"maxRadiusMiles\\": 200}",
            "currency": "{{enum_currency}}",
            "effectiveFrom": "2026-01-01",
            "jurisdiction": {
                "country": "{{enum_country}}",
                "subdivision": "{{enum_state}}"
            },
            "policyCode": "ELIG-POL-01",
            "workerClassification": "{{enum_workerClassification}}"
        }
    }, test=TEST_BASE + """
if (d && d.id) {
    pm.environment.set("optimizationPolicyId", d.id);
    console.log("Saved optimizationPolicyId:", d.id);
}
"""))'''

new_f08 = '''def get_folder_08():
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
"""))'''
assert old_f08 in code, "Failed to find old Folder 08"
code = code.replace(old_f08, new_f08, 1)

# Also fix 08.3 capacity unit to POUND and maxAgeSeconds
old_083 = '''        "reasonCode": "CAPACITY_CONFIRMED",
        "observedAt": "2026-10-10T08:00:00Z",
        "unit": "LBS",
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
            "cargo": {"unit": "LBS", "value": 35000.0},
            "truck": {"unit": "LBS", "value": 80000.0}
        }'''

new_083 = '''        "reasonCode": "CAPACITY_CONFIRMED",
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
        }'''
assert old_083 in code, "Failed to find old 08.3"
code = code.replace(old_083, new_083, 1)

# Fix 08.4 maxAgeSeconds
old_084 = '''        "reasonCode": "QUALIFICATION_CONFIRMED",
        "observedAt": "2026-10-10T08:00:00Z",
        "unit": "STATUS",'''
new_084 = '''        "reasonCode": "QUALIFICATION_CONFIRMED",
        "observedAt": "2026-10-10T08:00:00Z",
        "maxAgeSeconds": 86400,
        "unit": "STATUS",'''
assert old_084 in code, "Failed to find old 08.4"
code = code.replace(old_084, new_084, 1)

# Fix 08.5 maxAgeSeconds and source reference
old_085 = '''        "reasonCode": "COST_PREDICTION",
        "observedAt": "2026-10-10T08:00:00Z",
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
        },'''
new_085 = '''        "reasonCode": "COST_PREDICTION",
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
        },'''
assert old_085 in code, "Failed to find old 08.5"
code = code.replace(old_085, new_085, 1)

# 5. Patch 11.5 Approve Expense (skip if empty)
old_115 = '''    items.append(make_req("11.5 Approve Expense", "POST", "/api/expenses/{{expenseId}}/approve", "Approve expense voucher if expenseId is provided from seed or env", test="""
const expenseId = pm.environment.get("expenseId");
if (!expenseId || expenseId.trim() === "") {
    console.warn("[GAPS & ASSUMPTIONS] No expenseId found in environment. In LogisticsX OpenAPI spec, Expense creation has no public endpoint (must be pre-seeded). Skipping assertion.");
} else {
    pm.test("Expense Approved 200", function () {
        pm.expect(pm.response.code).to.eql(200);
    });
}
"""))'''

new_115 = '''    items.append(make_req("11.5 Approve Expense", "POST", "/api/expenses/{{expenseId}}/approve", "Approve expense voucher if expenseId is provided from seed or env", pre="""
const expenseId = pm.environment.get("expenseId");
if (!expenseId || expenseId.trim() === "") {
    console.warn("[GAPS & ASSUMPTIONS] No expenseId found in environment. In LogisticsX OpenAPI spec, Expense creation has no public endpoint (must be pre-seeded). Skipping request.");
    pm.execution.skipRequest();
}
""", test=TEST_BASE))'''
assert old_115 in code, "Failed to find old 11.5"
code = code.replace(old_115, new_115, 1)

# 6. Patch 14.11 outcome: SUCCEEDED
old_1411 = '''        "outcome": "MATCHED",'''
new_1411 = '''        "outcome": "SUCCEEDED",'''
assert old_1411 in code, "Failed to find old 14.11"
code = code.replace(old_1411, new_1411, 1)

# 7. Patch 18.2 & 18.3 Documents (skip if empty)
old_182_183 = '''    items.append(make_req("18.2 Get Document by ID", "GET", "/api/documents/{{documentId}}", "Get document metadata (skip if no documents in database)", test="""
const docId = pm.environment.get("documentId");
if (!docId || docId.trim() === "") {
    console.warn("[GAPS & ASSUMPTIONS] No documentId found. Spec has no document upload endpoint; skipping assertion.");
} else {
    pm.test("Status code is 200", function () {
        pm.expect(pm.response.code).to.eql(200);
    });
}
"""))
    items.append(make_req("18.3 Delete Document by ID", "DELETE", "/api/documents/{{documentId}}", "Delete document metadata (skip if no documentId)", test="""
const docId = pm.environment.get("documentId");
if (!docId || docId.trim() === "") {
    console.warn("No documentId found to delete; skipping.");
} else {
    pm.test("Status code is 200/204", function () {
        pm.expect(pm.response.code).to.be.oneOf([200, 204]);
    });
}
"""))'''

new_182_183 = '''    items.append(make_req("18.2 Get Document by ID", "GET", "/api/documents/{{documentId}}", "Get document metadata (skip if no documents in database)", pre="""
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
"""))'''
assert old_182_183 in code, "Failed to find old 18.2/18.3"
code = code.replace(old_182_183, new_182_183, 1)

# 8. Add build_environment into main()
env_builder_code = '''
def build_environment():
    dev_token = ""
    if os.path.exists("scripts/dev_token.txt"):
        with open("scripts/dev_token.txt") as f:
            dev_token = f.read().strip()

    env_vars = [
        # Base & Auth
        {"key": "baseUrl", "value": "http://localhost:8080", "enabled": True},
        {"key": "accessToken", "value": dev_token, "enabled": True},
        {"key": "tokenExpiresAt", "value": "1823131298000" if dev_token else "0", "enabled": True},
        {"key": "tenantId", "value": "local-development" if dev_token else "", "enabled": True},
        {"key": "employeeId", "value": "ba3b955b-7642-4713-a9e6-2877faf66bd3" if dev_token else "", "enabled": True},
        {"key": "roles", "value": '["ACCOUNTANT","ADMIN","DISPATCHER"]' if dev_token else "[]", "enabled": True},
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
        {"key": "inspectionId", "value": "", "enabled": True},
        {"key": "shipmentCostId", "value": "", "enabled": True},
        {"key": "expenseId", "value": "", "enabled": True},
        {"key": "taxAssessmentId", "value": "", "enabled": True},
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
        {"key": "enum_shipmentCostBasis", "value": "ESTIMATED", "enabled": True},
        {"key": "enum_shipmentCostSourceType", "value": "MANUAL_ENTRY", "enabled": True},
        {"key": "enum_legacyInvoiceStatus", "value": "DRAFT", "enabled": True},
        {"key": "enum_legacyInvoiceType", "value": "STANDARD", "enabled": True},
        {"key": "enum_paymentStatus", "value": "PENDING", "enabled": True},
        {"key": "enum_taxRequirementReason", "value": "STANDARD_SALES_TAX", "enabled": True},
        {"key": "enum_taxRequirement", "value": "REQUIRED", "enabled": True},
        {"key": "enum_workerClassification", "value": "EMPLOYEE", "enabled": True},
        {"key": "enum_payMethod", "value": "PER_MILE", "enabled": True},
        {"key": "enum_mileageBasis", "value": "CONTRACT_MILES", "enabled": True},
        {"key": "enum_sourceClassification", "value": "AUTHORITATIVE_DB", "enabled": True}
    ]

    return {
        "id": "logisticsx-env-v1",
        "name": "LogisticsX Environment",
        "values": env_vars,
        "_postman_variable_scope": "environment"
    }
'''

old_main_end = '''    with open("postman/LogisticsX_Part3.postman_collection.json", "w") as f:
        json.dump(part3_col, f, indent=2)
    print("Saved postman/LogisticsX_Part3.postman_collection.json")'''

new_main_end = '''    with open("postman/LogisticsX_Part3.postman_collection.json", "w") as f:
        json.dump(part3_col, f, indent=2)
    print("Saved postman/LogisticsX_Part3.postman_collection.json")

    env_data = build_environment()
    with open("postman/LogisticsX.postman_environment.json", "w") as f:
        json.dump(env_data, f, indent=2)
    print("Saved postman/LogisticsX.postman_environment.json")'''

code = code.replace("def build_collection(name, folders):", env_builder_code + "\ndef build_collection(name, folders):", 1)
assert old_main_end in code, "Failed to find old main end"
code = code.replace(old_main_end, new_main_end, 1)

with open('scripts/build_full_suite.py', 'w') as f:
    f.write(code)

print("Successfully patched scripts/build_full_suite.py!")
