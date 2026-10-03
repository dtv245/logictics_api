# LogisticsX implementation checkpoint

Updated: 2026-10-03T21:44:54+07:00. Sources: convention plan, current code, Flyway resources and actual Maven/PostgreSQL results. This file was missing at continuation start; prior verified handoffs were retained in `.ai-workflow/PROJECT_MEMORY.md`.

## Completed

- Phase 0, Phase 1, Phase 2: completed baseline; no reimplementation.
- Task 3.1 Shipment Cost: completed baseline, previous 65-test full-suite checkpoint preserved.
- Task 3.2 Accessorial: completed baseline, previous 74-test full-suite checkpoint preserved.
- BE-CALC-011-COST-CLASSIFICATION: versioned domain policy V1, explicit metadata for maintenance, unknown semantics retained.
- Task 3.3: actual variable/fixed/excluded/unclassified explanation, Contribution Margin, Allocated Profit, ratios, legacy percentage compatibility, currency/zero/unknown guards and grouped results.
- **Phase 3 COMPLETE** at the specified availability-aware acceptance level. Recorded unknown costs suppress derived profits; no guessed history/backfill. Empty cost sets are complete; this does not assert all company overhead has been recorded.

## In progress

- Phase 4A: existing V10 policy/pay-period schema and partial policy CRUD inspected; effective-date resolution, historical version protection, validation and dedicated tests still need verification.
- Phase 4B–4F: existing partial DriverPayEngine/correction APIs are unverified foundation, not completed tasks.

## Not started

- Phase 5 logic 015B–015G (V11 schema already exists).
- Phase 6 contract rate/FSC logic.
- Phase 7 feasibility/optimization audit/scoring.
- Phase 8 historical utilization.

## Tests

- Final clean full regression: `python3 /tmp/logisticsx_phase3_regression.py` → `./mvnw -q clean test` on a newly created disposable PostgreSQL database.
- 100 tests discovered, 0 failures, 0 errors, 1 pre-existing disabled legacy startup test; 99 executed successfully.
- 9 live PostgreSQL integration cases executed, including startup/Flyway, ledger/accessorial concurrency, API authorization, no GET writes, classification formulas and ambiguous maintenance.
- 3 policy unit tests; 9 calculator unit tests; existing profitability facts/service, shipment-cost, accessorial and other regression suites passed.
- Existing cost/accessorial test expectations unchanged. Profitability expectations changed only for the newly authorized policy, known costs and empty eligible cost sets.
- `git diff --check` PASS. Runtime OpenAPI/controller mappings load with the full application context.

## Migrations applied

- No new migration or applied migration modification in Task 3.3.
- User-confirmed V2–V7 applied baseline preserved; existing repository chain V1–V12 migrated/validated on disposable PostgreSQL during regression.
- V9 cost/accessorial, V10 policy/settlement and V11 payroll schemas already exist. V12 is occupied by the audit-column correction; next new migration must use the next unused number (currently V13).
- Production tenant migration histories were not changed or independently queried.

## Blockers

- Classification design blocker resolved by user-authorized policy V1. Individual OTHER/ambiguous MAINTENANCE rows remain UNCLASSIFIED and explain their reason; not a blocker to availability-aware engine completion.
- Legacy distance semantics remain unresolved; use explicit V3 miles only.
- Later payroll statutory deduction policy needs a supplied jurisdiction/worker classification before legal rates can be implemented; do not invent rates.
- Historical utilization definitions and historical vehicle-state source remain unverified.

## Next task

- Complete and test Phase 4A using existing V10 schema. Do not recreate V10/V11 or reopen completed Phase 3 subsystems without regression evidence.
- Preserve unrelated pre-existing worktree/staged files. Commit each completed sub-task with explicit paths only.
