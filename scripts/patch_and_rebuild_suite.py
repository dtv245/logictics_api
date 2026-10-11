import re

with open("scripts/build_full_suite.py", "r") as f:
    code = f.read()

# 1. Update build_environment to NOT hardcode JWT or read dev_token.txt into template
old_env_func = """def build_environment():
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
        {"key": "roles", "value": '["ACCOUNTANT","ADMIN","DISPATCHER"]' if dev_token else "[]", "enabled": True},"""

new_env_func = """def build_environment():
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
        {"key": "expenseId", "value": "11111111-2222-3333-4444-555555555555", "enabled": True},"""

assert old_env_func in code, "Could not find old_env_func"
code = code.replace(old_env_func, new_env_func)

# 2. Update REPORT_JPA_TEST to remove 500 acceptance and tag with [KNOWN-BUG]
old_report_test = """REPORT_JPA_TEST = \"\"\"
pm.test(\"Status code is 200 or 500 (known Spring Data JPA parameter typing bug)\", function () {
    pm.expect(pm.response.code).to.be.oneOf([200, 500]);
});
if (pm.response.code === 200) {
    pm.test(\"ApiResponse success flag is true\", function () {
        const json = pm.response.json();
        pm.expect(json.success).to.be.true;
    });
} else {
    console.warn(\"[GAPS & ASSUMPTIONS] Spring Data JPA untyped parameter bug in PostgreSQL (:from IS NULL OR ...). Endpoint responded with 500 as documented.\");
}
\"\"\""""

new_report_test = """REPORT_JPA_TEST = \"\"\"
pm.test(\"[KNOWN-BUG] Status code is 200\", function () {
    pm.expect(pm.response.code).to.eql(200);
});
pm.test(\"[KNOWN-BUG] ApiResponse success flag is true\", function () {
    let json = {};
    try { json = pm.response.json(); } catch(e) {}
    pm.expect(json.success).to.be.true;
});
if (pm.response.code === 500) {
    console.error(\"[KNOWN-BUG] Endpoint failed with 500: Spring Data JPA untyped parameter ($3) in PostgreSQL (:from IS NULL OR ...). Root fix requires parameter casting in repository.\");
}
\"\"\""""

assert old_report_test in code, "Could not find old_report_test"
code = code.replace(old_report_test, new_report_test)

# 3. Update get_folder_17 (Lark Base) to remove 502 and add larkConfigured check
old_folder_17 = """def get_folder_17():
    items = []
    LARK_BASE_TEST = \"\"\"
pm.test(\"Status code is 200, 201, or 502 Bad Gateway (requires external Lark Open API credentials)\", function () {
    pm.expect(pm.response.code).to.be.oneOf([200, 201, 502]);
});
if (pm.response.code === 200 || pm.response.code === 201) {
    pm.test(\"ApiResponse success flag is true\", function () {
        const json = pm.response.json();
        pm.expect(json.success).to.be.true;
    });
}
\"\"\"
    items.append(make_req(\"17.1 Lark Base: Sync Load\", \"POST\", \"/api/lark/base/sync/load/{{loadId}}\", \"Synchronize load status to Lark Base multi-dimensional table\", test=LARK_BASE_TEST))
    items.append(make_req(\"17.2 Lark Base: Sync Driver\", \"POST\", \"/api/lark/base/sync/driver/{{driverEmployeeId}}\", \"Synchronize driver record to Lark Base table\", test=LARK_BASE_TEST))
    items.append(make_req(\"17.3 Lark Base: List Synchronized Loads\", \"GET\", \"/api/lark/base/loads\", \"Read synchronized loads from Lark Base\", test=LARK_BASE_TEST))
    return {\"name\": \"17_LarkBase\", \"item\": items}"""

new_folder_17 = """def get_folder_17():
    items = []
    LARK_BASE_PRE = \"\"\"
const configured = pm.environment.get(\"larkConfigured\");
if (configured !== \"true\" && configured !== true) {
    console.warn(\"[LARK NOTICE] External Lark Base Open API credentials are not configured (larkConfigured != true). Skipping request.\");
    pm.execution.skipRequest();
}
\"\"\"
    LARK_BASE_TEST = \"\"\"
pm.test(\"Status code is 200 or 201\", function () {
    pm.expect(pm.response.code).to.be.oneOf([200, 201]);
});
pm.test(\"ApiResponse success flag is true\", function () {
    const json = pm.response.json();
    pm.expect(json.success).to.be.true;
});
\"\"\"
    items.append(make_req(\"17.1 Lark Base: Sync Load\", \"POST\", \"/api/lark/base/sync/load/{{loadId}}\", \"Synchronize load status to Lark Base multi-dimensional table\", pre=LARK_BASE_PRE, test=LARK_BASE_TEST))
    items.append(make_req(\"17.2 Lark Base: Sync Driver\", \"POST\", \"/api/lark/base/sync/driver/{{driverEmployeeId}}\", \"Synchronize driver record to Lark Base table\", pre=LARK_BASE_PRE, test=LARK_BASE_TEST))
    items.append(make_req(\"17.3 Lark Base: List Synchronized Loads\", \"GET\", \"/api/lark/base/loads\", \"Read synchronized loads from Lark Base\", pre=LARK_BASE_PRE, test=LARK_BASE_TEST))
    return {\"name\": \"17_LarkBase\", \"item\": items}"""

assert old_folder_17 in code, "Could not find old_folder_17"
code = code.replace(old_folder_17, new_folder_17)

# 4. Update 19.4 Malformed UUID Format to remove 500 and tag with [KNOWN-BUG]
old_19_4 = """    items.append(make_req(\"19.4 Validation: Malformed UUID Format -> 400/404/500\", \"GET\", \"/api/loads/not-a-valid-uuid\", \"Invalid UUID in path variable must return 400, 404, or 500 (MethodArgumentTypeMismatch)\", test=\"\"\"
pm.test(\"Status code is 400 Bad Request, 404 Not Found, or 500\", function () {
    pm.expect(pm.response.code).to.be.oneOf([400, 404, 500]);
});
\"\"\"))"""

new_19_4 = """    items.append(make_req(\"19.4 Validation: Malformed UUID Format -> 400/404\", \"GET\", \"/api/loads/not-a-valid-uuid\", \"Invalid UUID in path variable must return 400 or 404\", test=\"\"\"
pm.test(\"[KNOWN-BUG] Status code is 400 Bad Request or 404 Not Found\", function () {
    pm.expect(pm.response.code).to.be.oneOf([400, 404]);
});
if (pm.response.code === 500) {
    console.error(\"[KNOWN-BUG] MethodArgumentTypeMismatchException returned 500 instead of 400 Bad Request (GlobalExceptionHandler missing handler).\");
}
\"\"\"))"""

assert old_19_4 in code, "Could not find old_19_4"
code = code.replace(old_19_4, new_19_4)

# 5. In Folder 12: Move 12.8 Regenerate Invoice before Issue Billing Invoice
old_folder_12_middle = """    items.append(make_req("12.3 Get Billing Invoice by ID", "GET", "/api/invoices/billing/{{billingInvoiceId}}", "Get billing invoice and inspect line items", test=TEST_BASE + \"\"\"
if (d && d.lines && d.lines.length > 0 && !pm.environment.get("originalBillingLineId")) {
    const lineId = d.lines[0].lineId || d.lines[0].id;
    pm.environment.set("originalBillingLineId", lineId);
}
\"\"\"))

    items.append(make_req("12.4 Issue Billing Invoice", "POST", "/api/invoices/billing/{{billingInvoiceId}}/issue", "Transition invoice to ISSUED", body={
        "idempotencyKey": "ISSUE-{{$timestamp}}"
    }, test=TEST_BASE))

    items.append(make_req("12.5 Create Supplemental Invoice", "POST", "/api/invoices/billing/{{billingInvoiceId}}/supplemental", "Generate supplemental invoice for late accessorials", body={
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
    }, pre=\"\"\"
console.warn("[BILLING NOTICE] Supplemental invoice requires unbilled late accessorial charges on billing chain. Skipping in primary flow.");
pm.execution.skipRequest();
\"\"\", test=TEST_BASE + \"\"\"
if (d && (d.invoiceId || d.id)) {
    pm.environment.set("billingSupplementalId", d.invoiceId || d.id);
}
\"\"\"))

    items.append(make_req("12.6 Create Credit Note", "POST", "/api/invoices/billing/{{billingInvoiceId}}/credit", "Issue credit memo against invoice line", body={
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
    }, test=TEST_BASE + \"\"\"
if (d && (d.invoiceId || d.id)) {
    pm.environment.set("billingCreditId", d.invoiceId || d.id);
    console.log("Saved billingCreditId:", d.invoiceId || d.id);
}
\"\"\"))

    items.append(make_req("12.7 Rebill Invoice", "POST", "/api/invoices/billing/{{billingInvoiceId}}/rebill", "Rebill after applying credit adjustments", body={
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
    }, pre=\"\"\"
console.warn("[BILLING NOTICE] Rebill invoice requires specific credit adjustment lifecycle state. Skipping in linear flow.");
pm.execution.skipRequest();
\"\"\", test=TEST_BASE))

    items.append(make_req("12.8 Regenerate Invoice", "POST", "/api/invoices/billing/{{billingInvoiceId}}/regenerate", "Regenerate invoice from snapshot", body={
        "expectedSnapshotId": "{{ratingSnapshotId}}",
        "generation": 1
    }, pre=\"\"\"
console.warn("[BILLING NOTICE] Invoice regeneration requires void/superseded primary state. Skipping in linear flow.");
pm.execution.skipRequest();
\"\"\", test=TEST_BASE))"""

new_folder_12_middle = """    items.append(make_req("12.3 Get Billing Invoice by ID", "GET", "/api/invoices/billing/{{billingInvoiceId}}", "Get billing invoice and inspect line items", test=TEST_BASE + \"\"\"
if (d && d.lines && d.lines.length > 0 && !pm.environment.get("originalBillingLineId")) {
    const lineId = d.lines[0].lineId || d.lines[0].id;
    pm.environment.set("originalBillingLineId", lineId);
}
\"\"\"))

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
    }, test=TEST_BASE + \"\"\"
if (d && (d.invoiceId || d.id)) {
    console.log("Successfully regenerated draft invoice:", d.invoiceId || d.id);
}
\"\"\"))

    items.append(make_req("12.5 Issue Billing Invoice", "POST", "/api/invoices/billing/{{billingInvoiceId}}/issue", "Transition regenerated primary draft invoice to ISSUED", body={
        "idempotencyKey": "ISSUE-{{$timestamp}}"
    }, test=TEST_BASE))

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
    }, pre=\"\"\"
console.warn("[BILLING NOTICE] Supplemental invoice requires unbilled late accessorial charges on billing chain. Skipping in primary flow.");
pm.execution.skipRequest();
\"\"\", test=TEST_BASE + \"\"\"
if (d && (d.invoiceId || d.id)) {
    pm.environment.set("billingSupplementalId", d.invoiceId || d.id);
}
\"\"\"))

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
    }, test=TEST_BASE + \"\"\"
if (d && (d.invoiceId || d.id)) {
    pm.environment.set("billingCreditId", d.invoiceId || d.id);
    console.log("Saved billingCreditId:", d.invoiceId || d.id);
}
\"\"\"))

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
    }, pre=\"\"\"
console.warn("[BILLING NOTICE] Rebill invoice requires exact 100% full credit economic reversal plus a separate re-rated snapshot. Skipping in linear flow.");
pm.execution.skipRequest();
\"\"\", test=TEST_BASE))"""

assert old_folder_12_middle in code, "Could not find old_folder_12_middle"
code = code.replace(old_folder_12_middle, new_folder_12_middle)

# 6. In Folder 13: Remove sStatus === 'LOCKED' skip from 13.3-13.7
old_folder_13_trans = """    items.append(make_req("13.3 Require Settlement Validation", "POST", "/api/driver-settlements/{{settlementId}}/require-validation", "Flag settlement for driver confirmation", body={
        "reason": "Mileage variance verification required"
    }, pre=\"\"\"
const sStatus = pm.environment.get("settlementStatus");
if (sStatus === "LOCKED") {
    console.warn("Settlement is already LOCKED; skipping transitional request.");
    pm.execution.skipRequest();
}
\"\"\", test=TEST_BASE))

    items.append(make_req("13.4 Resolve Settlement Validation", "POST", "/api/driver-settlements/{{settlementId}}/resolve-validation", "Clear validation flag and return to CALCULATED", pre=\"\"\"
const sStatus = pm.environment.get("settlementStatus");
if (sStatus === "LOCKED") {
    console.warn("Settlement is already LOCKED; skipping transitional request.");
    pm.execution.skipRequest();
}
\"\"\", test=TEST_BASE))

    items.append(make_req("13.5 Submit Settlement for Review", "POST", "/api/driver-settlements/{{settlementId}}/submit-review", "Transition status from CALCULATED to IN_REVIEW", pre=\"\"\"
const sStatus = pm.environment.get("settlementStatus");
if (sStatus === "LOCKED") {
    console.warn("Settlement is already LOCKED; skipping transitional request.");
    pm.execution.skipRequest();
}
\"\"\", test=TEST_BASE))

    items.append(make_req("13.6 Approve Settlement", "POST", "/api/driver-settlements/{{settlementId}}/approve", "Transition status to APPROVED", pre=\"\"\"
const sStatus = pm.environment.get("settlementStatus");
if (sStatus === "LOCKED") {
    console.warn("Settlement is already LOCKED; skipping transitional request.");
    pm.execution.skipRequest();
}
\"\"\", test=TEST_BASE))

    items.append(make_req("13.7 Lock Settlement", "POST", "/api/driver-settlements/{{settlementId}}/lock", "Transition status to LOCKED", pre=\"\"\"
const sStatus = pm.environment.get("settlementStatus");
if (sStatus === "LOCKED") {
    console.warn("Settlement is already LOCKED; skipping lock request.");
    pm.execution.skipRequest();
}
\"\"\", test=TEST_BASE + \"\"\"
if (d && d.status) {
    pm.environment.set("settlementStatus", d.status);
}
\"\"\"))"""

new_folder_13_trans = """    items.append(make_req("13.3 Require Settlement Validation", "POST", "/api/driver-settlements/{{settlementId}}/require-validation", "Flag settlement for driver confirmation", body={
        "reason": "Mileage variance verification required"
    }, pre=\"\"\"
const sId = pm.environment.get("settlementId");
if (!sId || sId.trim() === "") {
    pm.execution.skipRequest();
}
\"\"\", test=TEST_BASE))

    items.append(make_req("13.4 Resolve Settlement Validation", "POST", "/api/driver-settlements/{{settlementId}}/resolve-validation", "Clear validation flag and return to CALCULATED", pre=\"\"\"
const sId = pm.environment.get("settlementId");
if (!sId || sId.trim() === "") {
    pm.execution.skipRequest();
}
\"\"\", test=TEST_BASE))

    items.append(make_req("13.5 Submit Settlement for Review", "POST", "/api/driver-settlements/{{settlementId}}/submit-review", "Transition status from CALCULATED to IN_REVIEW", pre=\"\"\"
const sId = pm.environment.get("settlementId");
if (!sId || sId.trim() === "") {
    pm.execution.skipRequest();
}
\"\"\", test=TEST_BASE))

    items.append(make_req("13.6 Approve Settlement", "POST", "/api/driver-settlements/{{settlementId}}/approve", "Transition status to APPROVED", pre=\"\"\"
const sId = pm.environment.get("settlementId");
if (!sId || sId.trim() === "") {
    pm.execution.skipRequest();
}
\"\"\", test=TEST_BASE))

    items.append(make_req("13.7 Lock Settlement", "POST", "/api/driver-settlements/{{settlementId}}/lock", "Transition status to LOCKED", pre=\"\"\"
const sId = pm.environment.get("settlementId");
if (!sId || sId.trim() === "") {
    pm.execution.skipRequest();
}
\"\"\", test=TEST_BASE + \"\"\"
if (d && d.status) {
    pm.environment.set("settlementStatus", d.status);
    console.log("Settlement successfully locked. Status:", d.status);
}
\"\"\"))"""

assert old_folder_13_trans in code, "Could not find old_folder_13_trans"
code = code.replace(old_folder_13_trans, new_folder_13_trans)

# 7. In Folder 14: Ensure 14.1 and 14.2 run when settlement is locked, and document why 14.3-14.12 skip
old_14_pre = """    items.append(make_req("14.1 Calculate Payroll Run", "POST", "/api/payroll/runs/calculate", "Calculate organization payroll run from driver settlements", body={
        "payPeriodId": "{{payPeriodId}}",
        "settlementIds": ["{{settlementId}}"],
        "currency": "{{enum_currency}}",
        "effectiveDate": "2026-10-15",
        "idempotencyKey": "PAYROLL-CALC-{{$timestamp}}"
    }, pre=\"\"\"
const sStatus = pm.environment.get("settlementStatus");
if (sStatus !== "LOCKED") {
    console.warn("Settlement is not in LOCKED status; payroll run calculation requires a locked settlement. Skipping request.");
    pm.execution.skipRequest();
}
\"\"\", test=TEST_BASE + \"\"\"
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
\"\"\"))"""

new_14_pre = """    items.append(make_req("14.1 Calculate Payroll Run", "POST", "/api/payroll/runs/calculate", "Calculate organization payroll run from driver settlements", body={
        "payPeriodId": "{{payPeriodId}}",
        "settlementIds": ["{{settlementId}}"],
        "currency": "{{enum_currency}}",
        "effectiveDate": "2026-10-15",
        "idempotencyKey": "PAYROLL-CALC-{{$timestamp}}"
    }, pre=\"\"\"
const sStatus = pm.environment.get("settlementStatus");
if (sStatus !== "LOCKED") {
    console.warn("Settlement is not in LOCKED status; payroll run calculation requires a locked settlement. Skipping request.");
    pm.execution.skipRequest();
}
\"\"\", test=TEST_BASE + \"\"\"
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
\"\"\"))"""

assert old_14_pre in code, "Could not find old_14_pre"
code = code.replace(old_14_pre, new_14_pre)

with open("scripts/build_full_suite.py", "w") as f:
    f.write(code)

print("Successfully patched scripts/build_full_suite.py!")
