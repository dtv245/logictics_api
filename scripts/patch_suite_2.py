#!/usr/bin/env python3

with open("scripts/build_full_suite.py", "r") as f:
    code = f.read()

# Fix 1: 08.6 save runId from d.run.id or d.id
old_08_6 = '''    }, test=TEST_BASE + """
if (d && d.id) {
    pm.environment.set("optimizationRunId", d.id);
    console.log("Saved optimizationRunId:", d.id);
}
"""))'''

new_08_6 = '''    }, test=TEST_BASE + """
const runId = (d && d.run && d.run.id) || (d && d.id);
if (runId) {
    pm.environment.set("optimizationRunId", runId);
    console.log("Saved optimizationRunId:", runId);
}
"""))'''

assert old_08_6 in code, "old_08_6 not found in code"
code = code.replace(old_08_6, new_08_6)

# Fix 2: 12.12 amountAmount: 1575.0
old_12_12 = '''    items.append(make_req("12.12 Create Payment", "POST", "/api/payments", "Record incoming customer payment in PENDING status", body={
        "invoiceId": "{{legacyInvoiceId}}",
        "amountAmount": 1701.0,'''

new_12_12 = '''    items.append(make_req("12.12 Create Payment", "POST", "/api/payments", "Record incoming customer payment in PENDING status", body={
        "invoiceId": "{{legacyInvoiceId}}",
        "amountAmount": 1575.0,'''

assert old_12_12 in code, "old_12_12 not found in code"
code = code.replace(old_12_12, new_12_12)

# Fix 3: enum_shipmentCostBasis ESTIMATE instead of ESTIMATED
old_cost_basis = '{"key": "enum_shipmentCostBasis", "value": "ESTIMATED", "enabled": True},'
new_cost_basis = '{"key": "enum_shipmentCostBasis", "value": "ESTIMATE", "enabled": True},'

assert old_cost_basis in code, "old_cost_basis not found in code"
code = code.replace(old_cost_basis, new_cost_basis)

with open("scripts/build_full_suite.py", "w") as f:
    f.write(code)

print("Applied 3 fixes to scripts/build_full_suite.py!")
