# Phase 4A driver pay policies and periods

Existing V10 owns driver_pay_policies/pay_periods. No migration is recreated or modified.

GET/POST /api/driver-pay-policies, POST /api/driver-pay-policies/{id}/new-version and GET/POST /api/pay-periods require ADMIN, ACCOUNTANT, PAYROLL or PAYROLL_MANAGER. Create returns 201; invalid inputs/stale version return 400. Driver/anonymous access returns 403.

Policy versions append new rows. New versions lock the supplied row, require it to still be the latest version of its policyCode, keep driver/default scope and require a later effectiveFrom. No command edits the old row or effectiveTo. Concurrent successors from the same version produce one new version and one POLICY_VERSION_STALE, preserving locked settlement references. Creation races are protected by V10's unique policy_code/policy_version constraint.

Resolver uses the work date, inclusive effective dates, and driver-specific scope before defaults. Within a policyCode, the most recent started version supersedes predecessors; expiration does not reactivate a historical open-ended version. Multiple eligible policy codes fail DRIVER_PAY_POLICY_AMBIGUOUS; missing policy fails DRIVER_PAY_POLICY_UNAVAILABLE. No arbitrary highest version across unrelated policies.

PER_MILE, PER_LOAD, PERCENT_REVENUE, HOURLY, DAILY and FLAT_RATE require respective nonnegative rates. Percentage is a ratio in [0,1]. Persistence precision is validated before PostgreSQL can silently round a rate. Currency must be explicit and canonical; dates must be ordered.

Supported mileage bases are ACTUAL_ALL_MILES and PLANNED_ALL_MILES from assignment.actual_miles/planned_miles. Existing ACTUAL/ACTUAL_MILES and PLANNED/PLANNED_MILES aliases normalize to explicit bases for new policies; historical rows are not rewritten. Loaded/practical/contract miles lack a confirmed per-driver attributable source and fail MILEAGE_BASIS_UNAVAILABLE. No actual-to-planned fallback.

PERCENT_REVENUE currently supports INVOICE_SUBTOTAL only. Invoice total with tax and unmodeled linehaul sources fail REVENUE_BASIS_UNAVAILABLE. Settlement calculation/reconciliation is a separate Phase 4D gate.

Pay periods have create/list commands, ordered inclusive dates, optional payment date, unique periodCode and OPEN initial state. Existing contracts do not prohibit overlapping periods; settlement source eligibility/deduplication remains a separate calculation gate.

Verification: 12 dedicated unit cases; 2 live PostgreSQL API/concurrency/history cases; full regression 114 tests, 0 failures/errors, 1 pre-existing legacy skip; 11 live PostgreSQL cases executed. Later settlement/payroll gates remain open.
