# LogisticsX implementation checkpoint

Updated: 2026-10-03T21:51:16+07:00. Sources: convention plan, current code, Flyway resources and actual Maven/PostgreSQL results. This file was missing at continuation start; prior verified handoffs were retained in `.ai-workflow/PROJECT_MEMORY.md`.

## Completed

- Phase 0, Phase 1, Phase 2: completed baseline; no reimplementation.
- Task 3.1 Shipment Cost: completed baseline, previous 65-test full-suite checkpoint preserved.
- Task 3.2 Accessorial: completed baseline, previous 74-test full-suite checkpoint preserved.
- BE-CALC-011-COST-CLASSIFICATION: versioned domain policy V1, explicit metadata for maintenance, unknown semantics retained.
- Task 3.3: actual variable/fixed/excluded/unclassified explanation, Contribution Margin, Allocated Profit, ratios, legacy percentage compatibility, currency/zero/unknown guards and grouped results.
- **Phase 3 COMPLETE** at the specified availability-aware acceptance level. Recorded unknown costs suppress derived profits; no guessed history/backfill. Empty cost sets are complete; this does not assert all company overhead has been recorded.
- Phase 4A / BE-CALC-013: versioned policies, effective resolver, explicit supported bases/rates/ratio/currency/precision validation and protected pay-period APIs. Concurrent versioning and locked historical references verified. Contract: docs/driver-pay-policy-contracts.md.
- Phase 3 commits: 36b5c9e policy/calculator; ee17155 formulas/contracts/checkpoint.
- Phase 4A commit: d1b2eb1.
- Phase 4B: MileagePayCalculator with explicit ACTUAL_ALL_MILES / PLANNED_ALL_MILES, no fallback; auditable assignment/policy ID/version/basis/rate/raw/rounded inputs persisted in settlement snapshot. Missing/invalid miles fails MILEAGE_VALIDATION_REQUIRED with no financial rows.

## In progress

- Phase 4C–4F: existing partial DriverPayEngine/correction APIs remain unverified outside the completed mileage path. Work-date eligibility, cross-period source reuse, accessorial driver attribution and reconciliation/lock/correction/concurrency still need dedicated gates.

## Not started

- Phase 5 logic 015B–015G (V11 schema already exists).
- Phase 6 contract rate/FSC logic.
- Phase 7 feasibility/optimization audit/scoring.
- Phase 8 historical utilization.

## Payroll architecture

- Payroll architecture: MULTI_JURISDICTION.
- Tax implementations: pluggable by jurisdiction.
- Current phase: jurisdiction-neutral framework (scheduled after Phase 4).
- Region-specific tax policies: implemented incrementally with authoritative policy/source; none hard-coded or currently implemented.
- Generic country/subdivision/locality jurisdiction and independent EMPLOYEE/CONTRACTOR classification; contractor does not imply zero tax.
- Resolution priority: work/payroll override → effective employee profile → tenant default. Missing jurisdiction/policy/statutory adapter yields UNAVAILABLE tax and VALIDATION_REQUIRED; blocks approval/lock/payment scheduling.
- Planned separate payroll_jurisdictions, payroll_policy_versions and employee_payroll_profiles on the next unused migration; V11 payroll schema is reused.
- Immutable payroll snapshots must include resolved jurisdiction/classification/policy ID/version/effective date and tax inputs/outputs.

## Tests

- Final clean full regression: `python3 /tmp/logisticsx_phase3_regression.py` → `./mvnw -q clean test` on a newly created disposable PostgreSQL database.
- 100 tests discovered, 0 failures, 0 errors, 1 pre-existing disabled legacy startup test; 99 executed successfully.
- 9 live PostgreSQL integration cases executed, including startup/Flyway, ledger/accessorial concurrency, API authorization, no GET writes, classification formulas and ambiguous maintenance.
- 3 policy unit tests; 9 calculator unit tests; existing profitability facts/service, shipment-cost, accessorial and other regression suites passed.
- Existing cost/accessorial test expectations unchanged. Profitability expectations changed only for the newly authorized policy, known costs and empty eligible cost sets.
- Phase 4A full regression on codex_classification_20261003145035: 114 tests, 0 failures/errors, 1 legacy skip; 11 live PG cases. Added 12 policy/resolver/period unit cases and 2 policy/period live cases, including concurrent version creation and retained locked-settlement policy reference.
- Phase 4B full regression on codex_classification_20261003145401: 119 tests, 0 failures/errors, 1 legacy skip; 12 live PG cases. Four mileage unit cases and live settlement snapshot/source validation passed. Snapshot/idempotent sequential retry verified; concurrent settlement creation remains a Phase 4F gate.
- `git diff --check` PASS. Runtime OpenAPI/controller mappings load with the full application context.

## Migrations applied

- No new migration or applied migration modification in Task 3.3.
- User-confirmed V2–V7 applied baseline preserved; existing repository chain V1–V12 migrated/validated on disposable PostgreSQL during regression.
- V9 cost/accessorial, V10 policy/settlement and V11 payroll schemas already exist. V12 is occupied by the audit-column correction; next new migration must use the next unused number (currently V13).
- Production tenant migration histories were not changed or independently queried.

## Blockers

- Classification design blocker resolved by user-authorized policy V1. Individual OTHER/ambiguous MAINTENANCE rows remain UNCLASSIFIED and explain their reason; not a blocker to availability-aware engine completion.
- Legacy distance semantics remain unresolved; use explicit V3 miles only.
- Regional statutory rates/authoritative sources are not supplied. User authorized a MULTI_JURISDICTION neutral framework/ports and fail-closed availability; this does not block framework implementation. Regional adapters will be added incrementally without guessed rates.
- Historical utilization definitions and historical vehicle-state source remain unverified.

## Next task

- Complete Phase 4C per-load/hourly/daily/flat calculations, then 4D–4F in separate verified slices. Do not recreate V10/V11 or reopen completed Phase 3 subsystems without regression evidence.
- Preserve unrelated pre-existing worktree/staged files. Commit each completed sub-task with explicit paths only.
