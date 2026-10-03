# LogisticsX implementation checkpoint

Updated: 2026-10-03T22:29:00+07:00. Sources: convention plan, current code, Flyway resources and actual Maven/PostgreSQL results. This file was missing at continuation start; prior verified handoffs were retained in `.ai-workflow/PROJECT_MEMORY.md`.

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
- Phase 4C: WorkPayCalculator for per-load/hourly/daily/flat, within-calculation load/day dedup, explicit time_entries.total_hours, immutable input snapshot. Fixed proven TimeEntry Duration→PostgreSQL interval JDBC mapping defect; no migration or test expectation weakening.
- Phase 4D: explicit PERCENT_REVENUE × eligible reconciled INVOICE_SUBTOTAL; ratio [0,1], no tax numerator, load-level within-calculation dedup, snapshot inputs and missing invoice validation.
- Phase 4E: approved driver_pay_amount only; recipient proven from historical assignment at occurred_at; missing/ambiguous recipient fails validation, never inferred from customer amount. Charge ID dedup and attribution snapshot, including load-only charge evidence. Existing accessorial ledger unchanged.

- Phase 4F: reconciled immutable financial workflow, historical work-date eligibility and cross-period source reuse guard, concurrent original/lock/corrections, append-only idempotent adjustments/reversal, line-level approved cost projection and trip-only attribution. V13 database protections verified clean and upgraded from populated V12.
- **Phase 4 COMPLETE**. Contract: docs/settlement-contracts.md. No unlock or mutation of locked history.

## In progress

- Phase 015B: jurisdiction-neutral payroll calculation/reconciliation framework, using existing V11 and next forward migration V14.

## Not started

- Phase 5 logic 015C–015G (V11 schema already exists; 015B now in progress).
- Phase 6 contract rate/FSC logic.
- Phase 7 feasibility/optimization audit/scoring.
- Phase 8 historical utilization.

## Payroll architecture

- Payroll architecture: MULTI_JURISDICTION.
- Tax implementations: pluggable by jurisdiction.
- Current phase: jurisdiction-neutral framework (015B in progress).
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
- Phase 4C full regression on codex_classification_20261003145821: 123 tests, 0 failures/errors, 1 legacy skip; 13 live PG cases. Three work-pay unit cases and per-load/hourly/daily/flat live source/dedup/snapshot case passed after fixing the real interval mapping defect.
- Phase 4D final clean regression on codex_classification_20261003150051: 127 tests, 0 failures/errors, 1 legacy skip; 14 live PG cases. Three percentage-pay unit cases and live subtotal/tax exclusion/dedup/missing-source snapshot case PASS.
- Phase 4E clean regression on codex_classification_20261003150336: 131 tests, 0 failures/errors, 1 legacy skip; 15 live PG cases. Three attribution unit cases and approved driver amount/draft exclusion/ambiguous recipient integration PASS.
- Phase 4F clean full regression on codex_classification_20261003152740: 141 tests, 0 failures/errors, 1 pre-existing legacy skip; 21 live PG cases. Three reconciliation unit cases, trip-only profitability guard, six live settlement/concurrency/correction/validation cases PASS. V12→V13 upgrade and full regression on populated codex_classification_20261003150336 PASS with the same 141-test count. Maximum-length reversal reason verified; historical invalid toy fixture corrected to satisfy its stated monetary invariant.
- `git diff --check` PASS. Runtime OpenAPI/controller mappings load with the full application context.

## Migrations applied

- No new migration or applied migration modification in Task 3.3.
- User-confirmed V2–V7 applied baseline preserved; existing repository chain V1–V12 migrated/validated on disposable PostgreSQL during regression.
- V9 cost/accessorial, V10 policy/settlement and V11 payroll schemas already exist. V12 is occupied by the audit-column correction; V13 adds settlement idempotency, workflow/history guards and explicit trip-only attribution; next unused migration is V14.
- Production tenant migration histories were not changed or independently queried.

## Blockers

- Classification design blocker resolved by user-authorized policy V1. Individual OTHER/ambiguous MAINTENANCE rows remain UNCLASSIFIED and explain their reason; not a blocker to availability-aware engine completion.
- Legacy distance semantics remain unresolved; use explicit V3 miles only.
- Regional statutory rates/authoritative sources are not supplied. User authorized a MULTI_JURISDICTION neutral framework/ports and fail-closed availability; this does not block framework implementation. Regional adapters will be added incrementally without guessed rates.
- Historical utilization definitions and historical vehicle-state source remain unverified.

## Next task

- Implement jurisdiction-neutral Phase 015B: generic jurisdiction/profile/policy resolution, settlement mapping, signed reversal explanation, tax ports, availability and reconciliation. No region-specific legal rates.
- Preserve unrelated pre-existing worktree/staged files. Commit each completed sub-task with explicit paths only.
