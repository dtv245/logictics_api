# AI Software Project Memory

## 1. Control

| Field | Value |
|---|---|
| Schema Version | 1 |
| Revision | 26 |
| Project | LogisticsX TMS |
| Repository Root | /home/vumoi/logictics_api |
| Execution Mode | EXISTING_PROJECT |
| Last Updated | 2026-10-03T21:51:16+07:00 |
| Current Phase | 7 — Backend Development / Phase 4B |
| Active Role | Backend Developer |
| Status | IN_PROGRESS |
| Next Role | Backend Developer |
| Next Action | Complete Phase 4B assignment-mileage pay, explicit source validation and auditable inputs; then 4C–4F independently. Phase 3 and 4A passed; no schema recreation. |
| Handoff Sequence | 26 |

## 2. Project Snapshot

- **Problem:** Modernize logistics calculations and schema without inferred legacy semantics.
- **Users:** Dispatchers, accountants, drivers, tenant administrators.
- **Goal:** Implement the convention plan with verified migrations, code, and tests.
- **Core Features:** Reporting, trip execution, cost ledger, settlement, payroll, rating, optimization.
- **Technology:** Java 21, Spring Boot 4.1, JPA, Flyway, PostgreSQL.
- **Constraints:** Preserve user worktree changes; no inferred financial or distance semantics.
- **Scope:** `plan-convention-v3-implementation-ready.md`.
- **Out of Scope:** Production deployment in this task.
- **Repository Baseline:** Existing Spring application; uncommitted Phase 0 documents, V2–V7 migrations, and implementation artifacts were already present.

## 3. Current Delivery State

### Current Objective

- **Feature/Task ID:** BE-CALC-014 / Phase 4B
- **Objective:** Complete explicit driver assignment mileage calculation and snapshot inputs.
- **Acceptance Gate:** Miles × rate, no actual/planned fallback, unsupported loaded/practical/contract source fails closed, unit/integration/regression evidence.
- **Allowed Change Scope:** Phase 4B mileage calculation slice; preserve completed Phase 0–3/4A; forward-only migrations when needed.

### Phase Status

| Phase | Primary Owner | Allowed Active Roles | Status | Outputs / Evidence |
|---|---|---|---|---|
| 0. Project Understanding | Product Owner | Product Owner; Business Analyst; Tech Lead | DONE | Phase 0 audit documents |
| 1. Business Analysis | Business Analyst | Business Analyst; Product Owner; QA / Tester; Backend Developer | DONE | BE-CALC-002–004 complete; unsupported maintenance currency and combined totals are explicitly PARTIAL per plan |
| 2. Domain Modeling | Software Architect | Software Architect; Business Analyst; Tech Lead; Database Engineer | NOT_STARTED | — |
| 3. Database Design | Database Engineer | Database Engineer; Software Architect; Backend Developer | IN_PROGRESS | V1–V11 validate; clean V1→V11 and V10→V11 upgrade verified locally; Phase 3 gates remain |
| 4. System Architecture | Software Architect | Software Architect; Tech Lead; Security Engineer; Database Engineer | DONE | ADR-001 and ADR-002 |
| 5. API Design | Tech Lead | Tech Lead; Software Architect; Backend Developer; Frontend Developer; Security Engineer | NOT_STARTED | — |
| 6. Project Structure | Tech Lead | Tech Lead; Software Architect; Backend Developer; Frontend Developer | DONE | Existing Spring layout |
| 7. Backend Development | Backend Developer | Backend Developer; Tech Lead; Database Engineer; QA / Tester; Security Engineer; Code Reviewer | IN_PROGRESS | Phase 3 complete: versioned classification, formulas, honest unknown availability, 100 regression tests incl. 9 PG cases; Phase 4A next |
| 8. Frontend Development | Frontend Developer | Frontend Developer; Tech Lead; QA / Tester; Security Engineer; Code Reviewer | NOT_APPLICABLE | API-only repository scope |
| 9. Integration | Tech Lead | Tech Lead; Backend Developer; Frontend Developer; QA / Tester | NOT_STARTED | — |
| 10. Testing | QA / Tester | QA / Tester; Backend Developer; Frontend Developer; Tech Lead | IN_PROGRESS | 87 tests, 0 failures/errors, 1 legacy skip; 8 live PostgreSQL integration cases; classification/later phases incomplete |
| 11. Security Review | Security Engineer | Security Engineer; Software Architect; Backend Developer; Frontend Developer; Code Reviewer | NOT_STARTED | — |
| 12. Performance Review | Tech Lead | Tech Lead; Database Engineer; Backend Developer; Frontend Developer; QA / Tester | NOT_STARTED | — |
| 13. Code Review | Code Reviewer | Code Reviewer; Tech Lead; Security Engineer | NOT_STARTED | — |
| 14. DevOps | DevOps Engineer | DevOps Engineer; Tech Lead; Security Engineer; QA / Tester | NOT_STARTED | — |
| 15. Observability | DevOps Engineer | DevOps Engineer; Tech Lead; Backend Developer | NOT_STARTED | — |
| 16. Documentation | Tech Lead | Tech Lead; Product Owner; Backend Developer; Frontend Developer; Database Engineer; DevOps Engineer | IN_PROGRESS | Phase 0 documentation updated |
| 17. Final Production Review | Code Reviewer | Code Reviewer; Product Owner; QA / Tester; Security Engineer; DevOps Engineer; Tech Lead | NOT_STARTED | — |

### Feature / Task Board

| ID | Requirement IDs | Owner | Status | Evidence | Next Action |
|---|---|---|---|---|---|
| BE-CALC-001 | Task 0.1–0.7 | Business Analyst | DONE | Phase 0 docs and source audit | Obtain product decision before distance-dependent work |
| BE-CALC-002 | Task 1.1 | Backend Developer | DONE | Revenue, balance, reconciliation and currency guards; legacy revenue-per-mile disabled; Maven compile passes | No further work unless new evidence/requirement appears |
| BE-CALC-003 | Task 1.2 | Backend Developer | DONE | OnTimeCalculator, TransitTimeCalculator, DelayCalculator, ExceptionMetricsService and endpoints present; delay rate included; compile passes | No further work unless verification or review finds a defect |
| BE-CALC-004 | Task 1.3 | Backend Developer | DONE | Separate expense/maintenance reports; no duplicate heuristic; unqualified maintenance currency and combined totals explicitly PARTIAL; known CPM excludes legacy distance and driver cost | No further Phase 1 work unless source semantics change |
| BE-CALC-010 | Task 3.1 | Backend Developer | DONE | Atomic audited approval/projector with expense lock; historical mileage/truck allocation snapshots and retry immutability; real PostgreSQL concurrency/rollback/snapshot tests; full startup; 65 tests, 0 failures/errors, 1 legacy skip | Proceed to Task 3.2 |
| BE-CALC-012 | Task 3.2 | Backend Developer | DONE | Precise block/hourly detention, independent amounts, RBAC/auth actor, reference validation, row-lock retry/audit and company-only ledger projection; PostgreSQL concurrency/endpoint tests; 74 tests PASS with 1 legacy skip | Proceed to Task 3.3 |
| BE-CALC-011 | Task 3.3 | Backend Developer | DONE | LOGISTICSX_COST_CLASSIFICATION V1; explainable variable/fixed/excluded/unknown inputs, formulas and ratios; 100 tests, 0 failure/error, 1 legacy skip, 9 PG integration cases | Phase 4A; do not infer ambiguous maintenance or OTHER semantics |
| BE-CALC-013 | Task 4.1 / 4A | Backend Developer | DONE | Append-only locked versioning, expiry/scope/ambiguity resolver, explicit validation and pay-period APIs; 114 regression tests incl. 11 live PG | Phase 4B mileage calculation |
| BE-CALC-014 | Task 4.2 | Backend Developer | IN_PROGRESS | Calculation, snapshots, state transitions, append-only adjustment/reversal, and signed cost credits implemented | Add tests, runtime JPA mapping verification, auth checks, and evaluate source/attribution edge cases |
| BE-CALC-015 | Task 5.1 | Database Engineer | DONE | V11 creates payroll runs/items, settlement join, payslips, and payments; local clean and upgrade succeeded | Continue Task 5.2 workflow without hardcoding jurisdiction tax rules |
| BE-CALC-015 | Tasks 5.2–5.4 | Backend Developer | NOT_STARTED | Only schema and partial Invoice deprecation annotation exist | Implement payroll workflow, payment state reconciliation, driver payslip API, and stop use of legacy payroll invoice fields |
| BE-CALC-016–017 | Phases 6–8 | Backend Developer | NOT_STARTED | No implementation evidence in this handoff | Proceed after payroll and settlement gates |

## 4. Requirements and Scope

### In Scope

- Execute plan tasks in verified order.

### Out of Scope

- Guessing legacy data semantics.

### Traceability

| Requirement / Story | Business Rule | Domain / Data | API / UI | Tests | Status |
|---|---|---|---|---|---|
| BE-CALC-001 | No hidden semantics | loads.distance; trips.total_distance | Reporting labels only | Source audit | DONE |
| BE-CALC-002 | Distance-dependent metrics must not infer legacy miles | Revenue, customer balance, reconciliation | ReportController | Maven compile passed; tests not run | DONE |
| BE-CALC-003 | OTD, transit, delay and exception metrics use requested/delivered/pickup/exception timestamps | Dedicated calculator services | ReportController | Maven compile passed; tests not run | DONE |
| BE-CALC-004 | Legacy distance is not interpreted as miles; combined cost remains PARTIAL where currencies/sources are unreconciled | Expense and maintenance reports | ReportController | Maven compile passed; tests not run | DONE |
| BE-CALC-005–009 | State changes are command-controlled; historical assignments/snapshots are preserved | V2–V8, trip execution, timeline, snapshots | Trip execution and timeline APIs | Maven + PostgreSQL/Flyway verification | DONE |
| BE-CALC-010–012 | Independent lifecycle, no duplicate source, actual-only/currency/mileage guards and user-authorized policy V1 | V9 costs/accessorials, V3 mileage; code-based classification V1 | Explainable profit/ratio metrics, unknown availability, currency/unallocated buckets | 100 tests, 0 failure/error, 1 legacy skip; 9 live PG cases | DONE |
| BE-CALC-013–014 | Policy versions are effective-dated; locked settlements are immutable | V10 policy/settlement tables | Protected policy and settlement APIs, snapshots, calculation and lock projection | Maven compile only; no tests this handoff | IN_PROGRESS |
| BE-CALC-015 Task 5.1 | Payroll is separate from invoices; payment attempts are idempotent | V11 payroll runs/items, join, payslips, payment attempts | Schema only | Flyway clean V1→V11, upgrade V10→V11, validate 11 migrations | DONE |

## 5. Architecture and Data Snapshot

- **Architecture Style:** Tenant-aware Spring modular monolith; ADR-001 selects database per tenant.
- **Modules / Boundaries:** Controller → service → repository/entity.
- **Dependency Direction:** HTTP depends on services; services depend on repositories/entities.
- **Authentication / Authorization:** JWT tenant claim routes to a tenant data source.
- **Data Model / Migration:** Flyway V1–V12 clean and V11–V12 upgrade verified on disposable PostgreSQL; V12 renames legacy audit columns without rewriting applied migrations; supported production tenant histories still need rollout validation.
- **API / Integration Contract:** REST; Phase 1 and Task 3.1 unit/security/service integration contracts verified; documents linked below.
- **Deployment / Runtime:** Docker assets exist but are unverified.

## 6. Decisions

| ID | Date | Owner | Decision | Reason / Evidence | Consequences | Supersedes |
|---|---|---|---|---|---|---|
| DEC-001 | 2026-10-03 | Software Architect | DATABASE_PER_TENANT | ADR-001 and tenant routing source | No tenant_id on business tables | — |
| DEC-002 | 2026-10-03 | Software Architect | Flyway is schema owner from V1 baseline | ADR-002 and TenantMigrationService | Legacy EF tooling must not mutate schema | — |
| DEC-003 | 2026-10-03 | Business Analyst | Legacy distance semantics unresolved | DTOs accept raw Double; legacy spec marks unit TBD | Block distance-dependent formulas | — |
| DEC-004 | 2026-10-03 | Backend Developer | Caller-supplied maintenance CPM produces ESTIMATE, not ACTUAL | No source proves an actual incurred rate; Task 3.1 snapshot captures explicit mileage/truck/rate | Actual profitability must not include this estimate | — |
| DEC-005 | 2026-10-03 | Backend Developer | Pending/submitted expense approval atomically projects ACTUAL/VERIFIED cost with row locks | No Java approval command existed; expense schema supports approval audit | Endpoint requires accounting role and tenant employee; unknown/rejected/draft states reject | HOFF-0020 missing-hook assumption |

## 7. Assumptions, Risks, and Blockers

### Assumptions

| ID | Assumption | Owner | Validation / Due | Status |
|---|---|---|---|---|
| ASM-001 | Existing uncommitted changes belong to the user. | Tech Lead | Preserve during future edits | OPEN |

### Risks

| ID | Severity | Risk | Evidence | Mitigation | Owner | Status |
|---|---|---|---|---|---|---|
| RSK-001 | HIGH | Wrong distance semantics corrupt KPI, cost, or payroll. | Unit/source are absent or TBD. | Block dependent formulas. | Product Owner | OPEN |
| RSK-002 | HIGH | Migration chain may be invalid or differ from tenant-applied history. | Found duplicate V8/V9 files in the active Flyway location; obsolete candidates moved out of active SQL naming, but migration history has not been checked after cleanup. | Verify clean/upgrade against actual supported tenant history before marking migrations complete. | Database Engineer | OPEN |
| RSK-003 | HIGH | Task 3.3 formerly lacked authoritative variable/fixed policy. | User supplied V1 classification rules; HOFF-0025 implements policy/formulas/tests with unknown semantics preserved. | Preserve policy version and unknown metadata; production readiness remains separate. | Backend Developer | CLOSED |

### Blockers

| ID | Blocker | Needed To Unblock | Owner | Status |
|---|---|---|---|---|
| BLK-001 | Distance source, unit, and meaning are unresolved. | Written product decision. | Product Owner | OPEN |
| BLK-002 | Former Task 3.3 classification design blocker. | User-authorized LOGISTICSX_COST_CLASSIFICATION V1 implemented/tested in HOFF-0025; ambiguous historical rows stay UNCLASSIFIED. | Backend Developer | RESOLVED |

## 8. Artifact Index

| Artifact | Path / URL | Owner | Status | Last Verified |
|---|---|---|---|---|
| Convention plan | plan-convention-v3-implementation-ready.md | Product Owner | IN_PROGRESS | 2026-10-03 source audit |
| Domain audit | docs/current-domain-semantics.md | Business Analyst | DONE | 2026-10-03 source audit |
| Timezone audit | docs/legacy-timezone-semantics.md | Business Analyst | DONE | 2026-10-03 source audit |
| Tenant ADR | docs/adr/ADR-001-tenant-isolation.md | Software Architect | DONE | 2026-10-03 source audit |
| Migration ADR | docs/adr/ADR-002-migration-ownership.md | Software Architect | DONE | 2026-10-03 source audit |
| Phase 1 reporting contract | docs/reporting-contracts.md | Backend Developer | DONE | Phase 1 regressions and financial RBAC tests pass |
| Task 3.1 ledger contract | docs/cost-ledger-contracts.md | Backend Developer | DONE | PostgreSQL concurrency/rollback/snapshot tests pass |
| Task 3.2 accessorial contract | docs/accessorial-contracts.md | Backend Developer | DONE | Detention edge cases and PostgreSQL company-only concurrency/actor tests pass |
| Task 3.3 profitability contract | docs/profitability-contracts.md | Backend Developer | DONE | 100 regression tests; versioned/explainable classification and formulas verified |
| Continuation checkpoint | plan-progress-summary.md | Backend Developer | IN_PROGRESS | Phase 3 COMPLETE; Phase 4A next |

## 9. Role Handoffs

### HOFF-0001 — Business Analyst → Product Owner

- **Timestamp:** 2026-10-03T00:00:00+07:00
- **From Role:** Business Analyst
- **To Role:** Product Owner
- **Phase:** 0 — Project Understanding
- **Status:** DONE
- **Objective:** Audit BE-CALC-001 semantics and delivery gates.
- **Inputs Read:** Plan, entities, DTOs, services, V1 baseline, legacy specs, and existing Phase 0 documents.
- **Completed:** Revalidated all seven audit tasks; corrected unproven planned-distance claims; marked the completed checklist items.
- **Requirement IDs:** BE-CALC-001 / Task 0.1–0.7.
- **Files and Artifacts:** plan-convention-v3-implementation-ready.md; docs/current-domain-semantics.md; .ai-workflow/PROJECT_MEMORY.md.
- **Decisions:** DEC-003.
- **Assumptions:** ASM-001.
- **Verification:** Source audit with rg over Java, migrations, docs, tenancy, and Flyway; tests NOT RUN because this handoff changes documentation only.
- **Open Issues and Risks:** RSK-001; RSK-002.
- **Blockers:** BLK-001.
- **Next Required Action:** Product Owner records the source, unit, and meaning for both legacy distance fields.
- **Acceptance Gate:** A written decision enables a non-ambiguous metric contract.
- **Do Not Redo:** TIMESTAMPTZ baseline, tenant-routing, and Flyway baseline audits without new evidence.

### HOFF-0002 — QA / Tester → Product Owner

- **Timestamp:** 2026-10-03T12:26:42+07:00
- **From Role:** QA / Tester
- **To Role:** Product Owner
- **Phase:** 10 — Testing
- **Status:** BLOCKED
- **Objective:** Establish a baseline test result and verify whether Phase 1 tasks can be closed.
- **Inputs Read:** `ReportController`, `RevenueCalculator`, `OperationsMetricsService`, `OperatingCostCalculator`, `InvoiceReconciliationService`, Phase 1 plan criteria.
- **Completed:** Ran the full Maven test suite and compared the existing Phase 1 implementation against all specified services and endpoints.
- **Requirement IDs:** BE-CALC-002, BE-CALC-003, BE-CALC-004.
- **Files and Artifacts:** `.ai-workflow/PROJECT_MEMORY.md`.
- **Decisions:** DEC-003 remains binding.
- **Assumptions:** ASM-001.
- **Verification:** `./mvnw test` → BUILD SUCCESS; tests run 33, failures 0, errors 0, skipped 1.
- **Open Issues and Risks:** `RevenueCalculator` computes revenue-per-mile with unconfirmed `loads.distance`; `OperatingCostCalculator` computes MPG/CPM with unconfirmed `trips.total_distance`; Task 1.2 has no TransitTimeCalculator. RSK-001 remains open.
- **Blockers:** BLK-001.
- **Next Required Action:** Product Owner provides the distance decision; Backend Developer then removes/guards all inferred distance metrics and implements the missing transit-time slice.
- **Acceptance Gate:** Phase 1 services meet every listed task requirement without unconfirmed distance semantics.
- **Do Not Redo:** Maven baseline test passed at this revision unless code/dependencies change.

### HOFF-0003 — Backend Developer → Database Engineer

- **Timestamp:** 2026-10-03T12:42:21+07:00
- **From Role:** Backend Developer
- **To Role:** Database Engineer
- **Phase:** 7 — Backend Development / Phase 2
- **Status:** PAUSED
- **Objective:** Bring existing Phase 2 execution and timeline implementation into compliance with command/state-machine conventions.
- **Inputs Read:** Phase 2 plan, V2–V7 migrations, Phase 2 entities, services, controllers, repositories, and tests.
- **Completed:** Validated driver assignment type; made assignment close idempotency-safe; enforced TripStop transition ordering; removed public load-event ingestion endpoint so a client cannot submit arbitrary new status; added invalid-transition test.
- **Requirement IDs:** BE-CALC-005, BE-CALC-007, BE-CALC-008, BE-CALC-009.
- **Files and Artifacts:** `TripExecutionService.java`; `LoadTimelineController.java`; `TripExecutionServiceTest.java`; `.ai-workflow/PROJECT_MEMORY.md`.
- **Decisions:** DEC-003 remains binding; timeline events must be emitted internally after business commands rather than accepted as arbitrary public status changes.
- **Assumptions:** ASM-001.
- **Verification:** `./mvnw test` → BUILD SUCCESS; tests run 34, failures 0, errors 0, skipped 1.
- **Open Issues and Risks:** V2–V7 were not verified on PostgreSQL clean/upgrade; V2 cascade delete conflicts with the historical-assignment intent; Task 2.2 mileage-ratio service is absent. RSK-001 and RSK-002 remain open.
- **Blockers:** BLK-001 blocks legacy-distance use, but new explicit mileage fields can support Task 2.2 after its service is added.
- **Next Required Action:** Database Engineer must run clean/upgrade migration verification and use a forward migration if V2 has been applied; Backend Developer then implements loaded/empty ratio from explicit fields only.
- **Acceptance Gate:** Every Phase 2 migration and service is verified; no public endpoint permits arbitrary status assignment; ratio calculations avoid legacy distance columns.
- **Do Not Redo:** State-transition and public-endpoint audit; Maven suite passed at this revision unless code/dependencies change.

### HOFF-0004 — Database Engineer → Database Engineer

- **Timestamp:** 2026-10-03T12:47:00+07:00
- **From Role:** Database Engineer
- **To Role:** Database Engineer
- **Phase:** 3 — Database Design / Phase 2 completion
- **Status:** DONE
- **Objective:** Complete Phase 2 migration verification, forward-fix assignment history, and implement explicit-mileage ratios.
- **Inputs Read:** V1–V7 migrations, Docker Compose PostgreSQL configuration, Trip entity, existing metric DTOs, and Phase 2 plan.
- **Completed:** Added V8 forward migration replacing the V2 trip FK cascade with `ON DELETE RESTRICT`; implemented `TripMileageRatioCalculator` from V3 explicit mileage fields only; completed clean and upgrade Flyway verification; marked Tasks 2.1–2.6 done.
- **Requirement IDs:** BE-CALC-005, BE-CALC-006, BE-CALC-007, BE-CALC-008, BE-CALC-009.
- **Files and Artifacts:** `V8__preserve_trip_driver_assignment_history.sql`; `TripMileageRatioCalculator.java`; `TripMileageRatioReport.java`; `TripMileageRatioCalculatorTest.java`; plan; workflow memory.
- **Decisions:** V2 is immutable; V8 is the forward-only correction. Explicit V3 fields, not legacy `total_distance`, are the only basis for loaded/empty ratios.
- **Assumptions:** ASM-001.
- **Verification:** `./mvnw test` → BUILD SUCCESS, 36 tests run, 0 failed/errors, 1 skipped. Flyway Docker test: clean V1→V8 and upgrade V1→V2→V8 both succeeded; `flyway validate` succeeded for 8 migrations on both. PostgreSQL `pg_constraint.confdeltype = r` for `fk_trip_driver_assignments_trip` on both databases.
- **Open Issues and Risks:** RSK-001 remains: legacy distance semantics block Phase 1 metrics. V8 verification used disposable local PostgreSQL only.
- **Blockers:** BLK-001.
- **Next Required Action:** Database Engineer designs the Phase 3 cost-ledger slice; do not use legacy distance fields for its formulas.
- **Acceptance Gate:** Phase 3 design and migration plan comply with financial immutability, currency, and idempotency conventions.
- **Do Not Redo:** Phase 2 clean/upgrade migration verification unless migrations change; V8 foreign-key verification unless V8 changes.

### HOFF-0005 — Database Engineer → Backend Developer

- **Timestamp:** 2026-10-03T12:55:33+07:00
- **From Role:** Database Engineer
- **To Role:** Backend Developer
- **Phase:** 3 — Database Design / 7 — Backend Development
- **Status:** PAUSED
- **Objective:** Start the Phase 3 canonical shipment-cost ledger safely after the V8 migration-version conflict.
- **Inputs Read:** Phase 3 plan; existing V1–V8 migrations; cost/metric conventions; existing entities and repositories.
- **Completed:** Created V9 containing both shipment costs and accessorial schemas to reserve V10 for the planned settlement migration; added `ShipmentCost`, repository, approved-expense idempotent projection, and maintenance allocation calculator; updated mapping test.
- **Requirement IDs:** BE-CALC-010, BE-CALC-012.
- **Files and Artifacts:** `V9__create_shipment_costs_and_accessorial_charges.sql`; `ShipmentCost.java`; `ShipmentCostRepository.java`; `ShipmentCostEngine.java`; `ShipmentCostEngineTest.java`; `EntityMappingTests.java`.
- **Decisions:** V8 remains immutable. V9 combines both Phase 3 tables; V10 remains available for driver-pay/settlement. Shipment sources are unique by `(source_type, source_id)` when source id exists.
- **Assumptions:** ASM-001.
- **Verification:** Flyway migrated clean and upgrade local PostgreSQL from V8 to V9 successfully; schema inspected. `./mvnw test` → BUILD SUCCESS, 38 tests run, 0 failed/errors, 1 skipped.
- **Open Issues and Risks:** Accessorial entity/command/approval and detention calculator are not implemented; profitability service/endpoints are not implemented. RSK-001 remains open.
- **Blockers:** BLK-001 blocks only calculations needing legacy distance, not the new ledger schema.
- **Next Required Action:** Implement accessorial state machine and detention calculation, then profitability metrics with explicit availability for unavailable mileage metrics.
- **Acceptance Gate:** Phase 3 has no duplicate source projection; accessorial approval is guarded; profitability has currency/availability protection.
- **Do Not Redo:** V9 migration application and cost-projection unit test unless V9 or ledger fields change.

### HOFF-0006 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-03T12:58:52+07:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / Phase 3
- **Status:** PAUSED
- **Objective:** Implement the accessorial charge and detention calculation portion of Phase 3.
- **Inputs Read:** Phase 3 plan, V9 schema, existing controller/error/security conventions.
- **Completed:** Added AccessorialCharge entity/repository, create and approve commands, the two specified REST endpoints, and DetentionCalculator with explicit free-time/block/rate inputs; added free-time and rounding tests.
- **Requirement IDs:** BE-CALC-012.
- **Files and Artifacts:** `AccessorialCharge.java`; `AccessorialChargeRepository.java`; accessorial DTOs; `AccessorialChargeService.java`; `AccessorialChargeController.java`; `DetentionCalculator.java`; `DetentionCalculatorTest.java`; `EntityMappingTests.java`.
- **Decisions:** No detention free-time/block/rate default is embedded in the calculator; callers must supply explicit policy values.
- **Assumptions:** ASM-001.
- **Verification:** `./mvnw test` → BUILD SUCCESS, 40 tests run, 0 failed/errors, 1 skipped.
- **Open Issues and Risks:** Endpoint authorization is constrained by the application's current global permit-all security posture; profitability service/report endpoints remain incomplete; RSK-001 remains open.
- **Blockers:** BLK-001 only blocks legacy-distance-dependent metrics.
- **Next Required Action:** Implement profitability service using eligible invoice revenue plus approved/posted ACTUAL shipment costs; return unavailable values for mileage metrics lacking explicit actual/loaded miles.
- **Acceptance Gate:** Profitability service enforces one currency, no duplicate logical source, correct cost lifecycle filtering, and metric availability.
- **Do Not Redo:** Detention free-time/block rounding cases unless policy contract changes.

### HOFF-0007 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-03T13:01:11+07:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / Phase 3
- **Status:** PAUSED
- **Objective:** Implement load profitability using canonical actual costs.
- **Inputs Read:** Phase 3 formulas, InvoiceStatus, InvoiceRepository, ShipmentCostRepository, CurrencyGuard, MetricDto.
- **Completed:** Added `ProfitabilityService`, `LoadProfitabilityReport`, and `GET /api/loads/{loadId}/financial-summary`. Revenue includes eligible invoice subtotals; costs only include ledger rows with actual basis and approved/posted status. Currency mismatch fails fast. Mileage-derived metrics return unavailable without explicit V3 attribution.
- **Requirement IDs:** BE-CALC-011.
- **Files and Artifacts:** `ProfitabilityService.java`; `LoadProfitabilityReport.java`; `ReportController.java`.
- **Decisions:** Do not infer revenue/cost per mile or break-even loaded rate from legacy distance fields.
- **Assumptions:** ASM-001.
- **Verification:** `./mvnw test` → BUILD SUCCESS, 40 tests run, 0 failed/errors, 1 skipped.
- **Open Issues and Risks:** Profitability needs dedicated branch tests and aggregate by lane/truck endpoints; Phase 3 remains incomplete. RSK-001 remains open.
- **Blockers:** BLK-001 for legacy-distance dependent metrics.
- **Next Required Action:** Add profitability unit/API tests and aggregate queries, then close Phase 3 only after authorization and PostgreSQL migration verification rerun for V9.
- **Acceptance Gate:** All Phase 3 calculator branches and accessorial approval are test-covered; V9 clean/upgrade revalidated; authorization is enforced.
- **Do Not Redo:** Revenue/cost lifecycle filter design unless invoice or ledger status policy changes.

### HOFF-0008 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-03T13:02:52+07:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 10 — Testing / Phase 3
- **Status:** PAUSED
- **Objective:** Add critical profitability branch coverage.
- **Inputs Read:** ProfitabilityService, ledger and invoice repositories, currency exception contract.
- **Completed:** Added tests proving eligible invoice plus approved actual costs yield correct margin and that per-mile values stay unavailable; added mixed-currency rejection test.
- **Requirement IDs:** BE-CALC-011.
- **Files and Artifacts:** `ProfitabilityServiceTest.java`; `.ai-workflow/PROJECT_MEMORY.md`.
- **Decisions:** DEC-003 remains binding.
- **Assumptions:** ASM-001.
- **Verification:** `./mvnw test` → BUILD SUCCESS, 42 tests run, 0 failed/errors, 1 skipped.
- **Open Issues and Risks:** Phase 3 aggregate lane/truck reports and authorization gates remain incomplete. RSK-001 remains open.
- **Blockers:** BLK-001.
- **Next Required Action:** Complete Phase 3 report aggregation/authorization, then begin V10 settlement schema.
- **Acceptance Gate:** No Phase 3 task is marked done until API authorization and remaining report scope are implemented.
- **Do Not Redo:** Profitability lifecycle/currency test cases unless business policy changes.

### HOFF-0009 — Database Engineer → Backend Developer

- **Timestamp:** 2026-10-03T13:04:00+07:00
- **From Role:** Database Engineer
- **To Role:** Backend Developer
- **Phase:** 3 — Database Design / Phase 4 foundation
- **Status:** PAUSED
- **Objective:** Add the Phase 4 driver-pay and settlement schema.
- **Inputs Read:** Migration V10 specification and settlement invariants.
- **Completed:** Added V10 pay-period, driver-pay-policy, settlement and settlement-line tables, unique original/adjustment sequence constraints, ratio/date checks, snapshot links and optimistic version columns.
- **Requirement IDs:** BE-CALC-013, BE-CALC-014.
- **Files and Artifacts:** `V10__create_driver_pay_and_settlement_tables.sql`.
- **Decisions:** Settlement-line deletion is restricted to preserve financial history; post-lock adjustments use ORIGINAL/ADJUSTMENT/REVERSAL model.
- **Assumptions:** ASM-001.
- **Verification:** Flyway validated and migrated local PostgreSQL clean and upgrade databases from V9 to V10 successfully.
- **Open Issues and Risks:** JPA entities, calculators, reconciliation, state machine, snapshots, APIs and authorization remain unimplemented.
- **Blockers:** BLK-001 applies to any legacy-distance settlement calculation.
- **Next Required Action:** Implement settlement entities and reconciliation/state-machine commands using explicit mileage/policy data only.
- **Acceptance Gate:** Settlement calculations reconcile exact rounded earnings/deductions/reimbursements and locked records are immutable.
- **Do Not Redo:** V10 migration verification unless its checksum-changing file is amended before commit.

### HOFF-0010 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-03T15:30:00+07:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / Phase 4
- **Status:** PAUSED
- **Objective:** Start BE-CALC-013 policy versioning and restore compilability of existing Phase 3 slices.
- **Inputs Read:** Phase 4 specification, V10 schema, mapped entities, repository conventions, and Maven compiler diagnostics.
- **Completed:** Added PayPeriod and DriverPayPolicy entities, policy repository, validated request/view DTOs, and list/create/new-version API. Aligned several Phase 3 API/entity contracts to actual fields; deprecated the specified legacy payroll fields on Invoice.
- **Requirement IDs:** BE-CALC-013 partial; BE-CALC-015 Task 5.4 annotation partial.
- **Files and Artifacts:** Policy entity/DTO/repository/service/controller; `CurrencyGuard.java`; `MoneyRoundingPolicy.java`; accessorial DTO mapping; Expense/Invoice integration; `Invoice.java`.
- **Decisions:** New policy versions are new rows with later effective dates; historical versions are not overwritten. Legacy distance remains unresolved and cannot feed financial metrics.
- **Assumptions:** ASM-001.
- **Verification:** `./mvnw -q -DskipTests compile` → BUILD SUCCESS. No tests run in this handoff.
- **Open Issues and Risks:** Phase 3 still has legacy-distance outputs and authorization/coverage gaps. Phase 4 date resolution, settlement engine/state machine, snapshots and cost projection remain missing. Phases 5–7 remain unimplemented. BLK-001 remains open.
- **Blockers:** No external blocker for implementation; BLK-001 only blocks formulas requiring unresolved legacy distance.
- **Next Required Action:** Complete BE-CALC-013 effective policy resolution and authorization; implement BE-CALC-014 settlement calculation, immutable transitions, snapshots and idempotent attributed cost projection; continue through Phase 8.
- **Acceptance Gate:** Do not mark Phase 4 tasks done until engine, state machine, snapshots, projection, authorization and tests meet specification.
- **Do Not Redo:** Do not infer legacy miles; do not rerun V10 unless its migration file is amended.

### HOFF-0011 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-03T16:51:57+07:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / Phase 4
- **Status:** PAUSED
- **Objective:** Implement the BE-CALC-014 driver settlement calculation and lock lifecycle.
- **Inputs Read:** V10 settlement schema, Phase 4 task requirements, trip assignments/stops, time entries, accessorials, expenses, invoices, cost ledger, tenant security conventions.
- **Completed:** Added settlement/header/line entities and repositories, period range assignment/time/expense queries, effective-date policy resolution, pay lines for mileage/load/revenue/hour/day/flat/accessorial and explicitly typed approved reimbursement/deduction expenses, calculation snapshot inputs/results, net reconciliation, review/approve/lock transitions, retry-safe calculate/transition responses, and per-attributed-load-line idempotent `DRIVER_SETTLEMENT` ACTUAL cost projection. Added payroll-role route restrictions and actor audit lookup for review/approve/lock. No mileage is inferred from legacy distance; unallocated trip mileage and time-entry pay remain unprojected at company level.
- **Requirement IDs:** BE-CALC-013 in progress; BE-CALC-014 implementation in progress.
- **Files and Artifacts:** `DriverSettlement.java`; `SettlementLine.java`; `DriverPayEngine.java`; settlement DTOs, repositories and controller; `SecurityConfig.java`; `ForbiddenException.java`.
- **Decisions:** Multi-load trip mileage is not allocated to a load without an explicit allocation policy. Time entries without load/trip attribution do not create shipment costs. Original settlement retry returns the existing record; locked costs use source type `DRIVER_SETTLEMENT` and settlement-line id as idempotency key.
- **Assumptions:** Existing timezone representation on `time_entries.date` is used for pay-period comparisons; this needs confirmation from the existing timezone audit before production.
- **Verification:** `./mvnw -q -DskipTests compile` → BUILD SUCCESS. `git diff --check` → PASS. Unit/integration tests and runtime schema validation NOT RUN in this handoff.
- **Open Issues and Risks:** No adjustment/reversal creation API yet; employee-typed expense classification needs policy validation; payroll-role semantics must be confirmed against deployed JWT role claims; multi-load allocation remains company-level; timezone assumption is not production-verified. Phase 3 still reports inferred legacy `loads.distance` values and lacks complete gates. RSK-001 and BLK-001 remain open.
- **Blockers:** No external blocker to implementation; BLK-001 only blocks formulas requiring unresolved legacy distance.
- **Next Required Action:** Correct Phase 3 legacy mileage exposure and explicit availability reporting; add adjustment/reversal workflow; then add/run permitted test and PostgreSQL mapping checks when authorized; continue Phase 5 onward after Phase 4 gate.
- **Acceptance Gate:** Do not check Phase 4 tasks until migration/entity runtime mapping, calculations, adjustment/reversal behavior, auth, and test coverage pass.
- **Do Not Redo:** Do not alter V10 migration unless a forward migration is added; do not project shared trip/time-entry earnings to loads without explicit allocation.

### HOFF-0012 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-03T16:56:27+07:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / Phase 3 and Phase 4
- **Status:** PAUSED
- **Objective:** Remove false legacy mileage reporting and add explicit load-attributed mileage availability to profitability outputs.
- **Inputs Read:** Phase 3 formulas, existing `MetricDto`, V3 trip mileage fields, trip-stop/load associations, prior Phase 4 settlement engine.
- **Completed:** Profitability no longer reads `loads.distance` for total/loaded/empty miles. Explicit V3 actual/loaded/empty mileage is aggregated only when every trip is unambiguously assigned to one load; otherwise new `MetricDto` fields report `UNAVAILABLE` with a reason. Revenue/mile, cost/mile, break-even loaded rate, lane RPM, and truck CPM now preserve unavailable values rather than returning fabricated zero/legacy-derived figures. Settlement work from HOFF-0011 is retained.
- **Requirement IDs:** BE-CALC-011 partial; BE-CALC-013/014 still in progress.
- **Files and Artifacts:** `ProfitabilityService.java`; `LoadFinancialSummary.java`; `ProfitabilityMileageMetrics.java`; lane/truck profitability DTOs; `TripStopRepository.java`; `.ai-workflow/PROJECT_MEMORY.md`.
- **Decisions:** A trip with multiple distinct loads has no per-load mileage attribution in the present schema; do not prorate. Unit economics remain unavailable unless explicit V3 trip fields can be attributed without allocation assumptions.
- **Assumptions:** The V3 actual/loaded/empty trip fields represent miles as named; per-load attribution is valid only for trips whose stops reference exactly one distinct load.
- **Verification:** `./mvnw -q -DskipTests compile` → BUILD SUCCESS. Unit/API tests NOT RUN; PostgreSQL/Hibernate runtime mapping NOT RUN.
- **Open Issues and Risks:** Phase 1 calculators still reference unresolved legacy distance; Phase 3 test/auth/migration gates remain incomplete. Phase 4 lacks adjustment/reversal creation and tests/runtime mapping validation. Phases 5–8 are not implemented. BLK-001 remains open.
- **Blockers:** No external blocker to code work. BLK-001 blocks only legacy-distance formulas.
- **Next Required Action:** Add tests for explicit/missing/multi-load mileage availability and settlement arithmetic/transitions; validate V9/V10 mappings against PostgreSQL; design append-only reversals and corresponding cost-credit ledger semantics before implementation.
- **Acceptance Gate:** Keep Phase 3 and Phase 4 checklist items open until their relevant tests, authorization and migration/runtime verification pass.
- **Do Not Redo:** Do not restore calculations from `Load.distance`; do not prorate trip miles over multiple loads without a documented allocation policy.

### HOFF-0013 — Database Engineer → Backend Developer

- **Timestamp:** 2026-10-03T16:58:45+07:00
- **From Role:** Database Engineer
- **To Role:** Backend Developer
- **Phase:** 3 — Database Design
- **Status:** PAUSED
- **Objective:** Remove active Flyway version collisions discovered while preparing subsequent payroll migrations.
- **Inputs Read:** All resources under `db/migration/tenant`, Flyway configured locations, canonical V8/V9 contents and earlier V8/V9 handoffs.
- **Completed:** Confirmed duplicate V8 and V9 versioned SQL resources existed in the active location. Retained the canonical V8 assignment-history forward fix and canonical V9 combined shipment-cost/accessorial migration; moved the superseded standalone shipment-cost/accessorial candidates to `tenant/obsolete/*.sql.disabled`, outside active migration naming.
- **Requirement IDs:** BE-CALC-005–012 migration integrity; BE-CALC-013/014 chain prerequisite.
- **Files and Artifacts:** `db/migration/tenant/V8__preserve_trip_driver_assignment_history.sql`; `V9__create_shipment_costs_and_accessorial_charges.sql`; two obsolete candidate files renamed under `db/migration/tenant/obsolete/`.
- **Decisions:** Do not alter already-applied migration contents. Version collisions make the previous migration evidence stale until clean/upgrade and tenant-history verification are rerun.
- **Assumptions:** The combined V9 is the intended canonical migration as recorded by HOFF-0005 and the prior local V8→V9 migration test.
- **Verification:** `rg --files src/main/resources/db/migration/tenant` shows one active SQL file per version V1–V10; `./mvnw -q -DskipTests compile` → BUILD SUCCESS. PostgreSQL/Flyway NOT RUN in this handoff.
- **Open Issues and Risks:** Must compare migration history on supported tenant databases before rollout; RSK-002 reopened. Phase 4 settlement tests/runtime mapping and adjustment/reversal work remain outstanding.
- **Blockers:** No external blocker; safe deployment requires migration-history verification.
- **Next Required Action:** Run Flyway validate and clean/upgrade scenarios against disposable PostgreSQL and compare applied versions/checksums before adding V11.
- **Acceptance Gate:** Unique active migration versions and successful clean/upgrade plus supported-history validation.
- **Do Not Redo:** Do not reintroduce the standalone V8/V9 scripts as versioned migrations; do not rewrite V8/V9 checksums for databases that may have applied them.

### HOFF-0014 — Database Engineer → Backend Developer

- **Timestamp:** 2026-10-03T17:05:39+07:00
- **From Role:** Database Engineer
- **To Role:** Backend Developer
- **Phase:** 3 — Database Design / Phase 5 Task 5.1
- **Status:** PAUSED
- **Objective:** Revalidate the canonical migration chain after removing version collisions and add payroll schema with reversal-credit support.
- **Inputs Read:** Flyway resources/configuration, existing PostgreSQL clean/upgrade histories, Phase 5 schema requirement, locked settlement correction requirements.
- **Completed:** Confirmed duplicate candidate V8/V9 scripts were no longer active. Added V11 payroll runs/items, item-settlement join, payslips, payment attempts/idempotency constraints, and a narrowly scoped signed driver settlement credit constraint. V11 applied successfully to two existing V10 databases and a new empty PostgreSQL database; FK/history constraints inspected.
- **Requirement IDs:** BE-CALC-015 Task 5.1 DONE; BE-CALC-014 reversal ledger prerequisite.
- **Files and Artifacts:** `V11__create_payroll_and_payslip_tables.sql`; plan Task 5.1 checkbox; migration validation results.
- **Decisions:** Negative shipment costs are allowed only for ACTUAL DRIVER costs whose source is a driver settlement reversal or adjustment. Tax/insurance rates are not hardcoded because no jurisdiction/policy source is specified.
- **Assumptions:** `migration_clean` and `migration_upgrade` are disposable migration-verification databases; `migration_clean_v11` was newly created for clean install evidence.
- **Verification:** Flyway 11.20.3: `migration_clean` V10→V11 PASS; `migration_upgrade` V10→V11 PASS; `migration_clean_v11` empty V1→V11 PASS; validate 11 migrations PASS. Confirmed V2 trip FK and V10 settlement-line FK remain RESTRICT and V11 payroll tables exist.
- **Open Issues and Risks:** Payroll Java workflow/entities/API not implemented; V11 JPA runtime mapping/tests not run. Supported production tenant migration history still unknown, so RSK-002 remains open. Phase 4 business/test gates remain open.
- **Blockers:** No external blocker; tax calculation requires an explicit jurisdiction-backed policy before it can be safely implemented.
- **Next Required Action:** Implement Task 5.2 run calculation and approval/lock; make legal deduction logic policy-driven and fail closed when no jurisdiction policy is configured.
- **Acceptance Gate:** Payroll lock must not mark settlements/items PAID; payslips are immutable; payments are retry-safe; only SUCCEEDED/reconciled payments advance paid states.
- **Do Not Redo:** Do not change applied V1–V10 migrations; do not re-add legacy duplicate V8/V9 files; retain signed-cost guardrails.

### HOFF-0015 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-03T17:06:34+07:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / Phase 4
- **Status:** PAUSED
- **Objective:** Complete append-only settlement corrections using the V11 cost-credit constraint.
- **Inputs Read:** V10 settlement lines/parent sequence constraints, V11 signed-cost guard, existing settlement state machine and role-gated controller.
- **Completed:** Added adjustment and reversal request contracts/endpoints. New correction settlements require a LOCKED parent, serialize sequence allocation with a parent row lock, snapshot the correction, reconcile their own earning/deduction/reimbursement lines, and retain parent linkage. Reversal lines offset every parent line without mutating it; only attributed reversals of original earnings create negative shipment-cost credits. Adjustment earning/deduction lines create positive/negative driver costs respectively when explicitly load-attributed. Repeated transition responses are idempotent.
- **Requirement IDs:** BE-CALC-014 partial.
- **Files and Artifacts:** `DriverPayEngine.java`; `DriverSettlementRepository.java`; `DriverSettlementController.java`; adjustment/reversal DTOs; V11 cost-credit constraint.
- **Decisions:** Corrections are child settlements with monotonic parent sequence. No shipment-cost row is produced without explicit load attribution. Reversed deductions/reimbursements affect payroll net only, not shipment cost.
- **Assumptions:** V11 permits negative only for ACTUAL DRIVER rows from the two correction source types; verified on clean PostgreSQL migration.
- **Verification:** `./mvnw -q -DskipTests compile` → BUILD SUCCESS; PostgreSQL migration clean/upgrade evidence is HOFF-0014. Unit/API/security tests NOT RUN.
- **Open Issues and Risks:** Adjustment/reversal arithmetic and duplicate/concurrent requests need tests. JPA runtime mapping/auth claims not verified. Payroll statutory deductions need user-selected jurisdiction and driver classification. Phase 3/4 acceptance gates remain open.
- **Blockers:** Tax/insurance implementation needs the jurisdiction and worker classification requested from the user; no blocker to payroll scaffolding meanwhile.
- **Next Required Action:** Implement Phase 5 payroll run, payslip and payment state workflow with no hardcoded statutory rates; then wire a jurisdiction-specific deduction strategy after the user choice.
- **Acceptance Gate:** Payroll lock cannot mark settlements paid; payslip snapshots immutable; only successful/reconciled payments advance PAID status; all transitions and retries tested.
- **Do Not Redo:** Do not mutate locked settlements; do not project shared/unattributed costs; do not allow negative ledger rows from arbitrary sources.

### HOFF-0016 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-03T17:11:19+07:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 1 — Business Analysis / Phase 1 implementation gaps
- **Status:** PAUSED
- **Objective:** Resume the earliest incomplete plan phase and complete BE-CALC-002 while removing unsafe legacy-distance calculations.
- **Inputs Read:** Full project memory; Phase 1 checklist; report/controller/calculation source; domain entities and repositories; workflow skill and Phase Playbook Phase 1 guidance.
- **Completed:** Extracted `CustomerBalanceCalculator`; applied currency guards to invoice reconciliation, customer balances, load payments and monthly revenue; excluded non-revenue invoice statuses from balance/revenue totals; removed `loads.distance` from revenue-per-mile and return an explicit unavailable basis; removed `trips.total_distance` from MPG/CPM and use only `actual_distance_miles`; added `TransitTimeCalculator` with a report endpoint; maintenance total is derived from labor plus parts; monthly combined operating cost is marked PARTIAL with `DUPLICATE_SOURCE_NOT_FULLY_RECONCILED`. Marked Task 1.1 complete only.
- **Requirement IDs:** BE-CALC-002 DONE; BE-CALC-003/004 IN_PROGRESS; DEC-003 remains binding.
- **Files and Artifacts:** `RevenueCalculator.java`; `CustomerBalanceCalculator.java`; `InvoiceReconciliationService.java`; `OperatingCostCalculator.java`; `TransitTimeCalculator.java`; `TransitTimeReport.java`; `ReportController.java`; `MonthlyFinancialSummary.java`; `plan-convention-v3-implementation-ready.md`; `.ai-workflow/PROJECT_MEMORY.md`.
- **Decisions:** Do not treat legacy `loads.distance` or `trips.total_distance` as miles. Keep task criteria open when specified calculator/service contracts remain absent. Do not move to later plan tasks until Phase 1 criteria are completed or explicitly blocked.
- **Assumptions:** Requested report currency must match source monetary currencies; no FX conversion is performed. Maintenance schema has no currency field, so this remains an unresolved reporting limitation rather than an inferred conversion.
- **Verification:** `./mvnw -q -DskipTests compile` → PASS. Java tests NOT RUN in this handoff. Flyway NOT RUN; no migration changed.
- **Open Issues and Risks:** Phase 1 Task 1.2 still lacks the named OnTimeCalculator, DelayCalculator and ExceptionMetricsService boundaries; Task 1.3 still lacks the named expense/maintenance report services and source/currency completeness evidence. BLK-001 remains for any mileage metric that depends on unresolved legacy fields.
- **Blockers:** No blocker to continue service implementation; legacy distance formulas remain unavailable until a written source/unit/meaning decision is made.
- **Next Required Action:** Complete Phase 1 Task 1.2 service criteria, then Task 1.3 criteria; verify each before marking done. Do not continue payroll or later tasks yet.
- **Acceptance Gate:** All Task 1.1–1.3 specified service outputs and endpoint behavior meet plan criteria with no hidden distance or duplicate-source assumptions; verification evidence recorded.
- **Do Not Redo:** Do not restore revenue-per-mile from `loads.distance` or MPG/CPM from `trips.total_distance`; no schema/migration changes were made in this handoff.

### HOFF-0017 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-03T17:13:49+07:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 1 — Business Analysis / Phase 1 implementation gaps
- **Status:** PAUSED
- **Objective:** Finish Task 1.2's required service boundaries and safely advance Task 1.3 without inventing currency or duplicate-source semantics.
- **Inputs Read:** Task 1.2/1.3 plan criteria; Phase 1 calculators and DTOs; maintenance and expense entities/repositories; current domain semantics; prior HOFF-0016.
- **Completed:** Split OTD, late-delivery and exception calculations into `OnTimeCalculator`, `DelayCalculator`, and `ExceptionMetricsService`; retained a facade for existing report routes; transit calculator/report endpoint already present. Delay response now includes eligible count and late percentage. Added `ExpenseReportService` and `MaintenanceReportService`. Maintenance amounts expose PARTIAL currency availability because the legacy table has no currency column; monthly combined cost is null/PARTIAL when maintenance currency is unknown; known CPM excludes maintenance and explains that limitation; approved-expense aggregation and fuel aggregation reject currency mismatches. Marked Task 1.2 complete; Task 1.3 remains open.
- **Requirement IDs:** BE-CALC-003 DONE; BE-CALC-004 IN_PROGRESS; BE-CALC-002 DONE.
- **Files and Artifacts:** `OnTimeCalculator.java`; `DelayCalculator.java`; `ExceptionMetricsService.java`; `OperationsMetricsService.java`; `TransitTimeCalculator.java`; `ExpenseReportService.java`; `MaintenanceReportService.java`; `MaintenanceSummaryReport.java`; `DeliveryDelayReport.java`; `MonthlyFinancialSummary.java`; `OperatingCostCalculator.java`; `ReportController.java`; plan; workflow memory.
- **Decisions:** Do not infer USD (or another currency) for maintenance values where schema stores no currency. Do not sum unqualified maintenance values into expense currency totals. Keep Task 1.3 incomplete pending approved currency semantics/mapping and double-count verification.
- **Assumptions:** None added for currency or duplicate reconciliation.
- **Verification:** `./mvnw -q -DskipTests compile` → PASS; `git diff --check` → PASS. Unit/integration tests NOT RUN. Database migration NOT RUN; no schema migration changed.
- **Open Issues and Risks:** Task 1.3 maintenance values are currency-unqualified; duplicate maintenance expenses cannot be deterministically identified because `expenses.maintenance_record_id`/confirmed reconciliation mapping is absent. BLK-001 still forbids using legacy distance columns as miles.
- **Blockers:** Safe closure of Task 1.3 requires product/schema decision for legacy maintenance currency and verified duplicate-source handling. No downstream plan task should begin before this earlier task is done or explicitly blocked.
- **Next Required Action:** Obtain or establish an authorized maintenance currency mapping (likely a forward schema/domain decision if data is multi-currency), then verify cost reports and duplicate policy before checking Task 1.3.
- **Acceptance Gate:** Currency-aware maintenance report contract; no unmarked mixed-currency sum; partial combined values state exact reasons; source dedupe remains deterministic only.
- **Do Not Redo:** Do not use `loads.distance` or `trips.total_distance` for mileage; do not claim maintenance report currency from the request parameter; do not infer expense-maintenance duplicates by amount/date/vendor.

### HOFF-0018 — Backend Developer → Product Owner

- **Timestamp:** 2026-10-03T17:15:20+07:00
- **From Role:** Backend Developer
- **To Role:** Product Owner
- **Phase:** 1 — Business Analysis / Task 1.3 decision gate
- **Status:** BLOCKED
- **Objective:** Resolve the remaining Task 1.3 domain decision without guessing currency for legacy maintenance amounts.
- **Inputs Read:** V1 baseline schema, `MaintenanceRecord` entity, maintenance report implementation, current-domain audit, Task 1.3 plan text.
- **Completed:** Verified `maintenance_records` has labor/parts/total amounts but no currency column; implementation now returns those amounts with `currency=null`, `availability=PARTIAL`, and reason `MAINTENANCE_CURRENCY_NOT_STORED_IN_LEGACY_SCHEMA`. Monthly combined cost is omitted and marked PARTIAL when maintenance currency is unknown. No currency was inferred.
- **Requirement IDs:** BE-CALC-004 blocked; BE-CALC-002/003 done.
- **Files and Artifacts:** `V1__baseline_business_schema.sql`; `MaintenanceRecord.java`; `MaintenanceReportService.java`; `MaintenanceSummaryReport.java`; `MonthlyFinancialSummary.java`; `ReportController.java`; plan; workflow memory.
- **Decisions:** Do not presume USD or another currency, and do not compare/add maintenance values to currency-tagged expenses until source currency is authoritative. Do not alter the already applied V1 migration; any schema repair must be forward-only.
- **Assumptions:** None.
- **Verification:** `rg`/source inspection confirms no maintenance currency column in the V1 DDL/entity. `./mvnw -q -DskipTests compile` → PASS; `git diff --check` → PASS; memory validator → PASS. Unit/integration tests NOT RUN.
- **Open Issues and Risks:** Existing maintenance monetary values cannot be safely labeled or combined; expenses-to-maintenance duplicates cannot be deterministically linked in the current schema.
- **Blockers:** Product decision required: (A) confirm one canonical tenant/base currency for all existing maintenance records and provide the historical-data guarantee, or (B) authorize a forward migration adding currency plus a backfill/default policy. Also confirm whether adding `expenses.maintenance_record_id` is authorized now or retain the partial/no-dedup behavior until the planned later schema task.
- **Next Required Action:** Product Owner supplies the currency decision and duplicate-linking scope; Backend Developer resumes Task 1.3 and does not advance to later plan tasks until its criteria are met.
- **Acceptance Gate:** Currency basis is authoritative and represented in the report; combined costs remain PARTIAL until deterministic duplicate linkage/reconciliation is in place; task verification passes.
- **Do Not Redo:** Do not modify V1; do not report maintenance as USD merely because report APIs default to USD; do not infer duplicate expenses by amount/date/vendor.

### HOFF-0019 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-03T17:15:20+07:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 1 — Business Analysis complete / Phase 3 Task 3.1 start
- **Status:** DONE
- **Objective:** Close Phase 1 Task 1.3 using its explicitly allowed PARTIAL semantics and resume the earliest incomplete implementation task.
- **Inputs Read:** Task 1.3 acceptance text; V1 and V5 migrations; `Expense.maintenanceRecord`; Task 3.1 plan; current shipment cost engine and allocator.
- **Completed:** Confirmed `expenses.maintenance_record_id` exists in V5. Task 1.3 is implemented with expense/maintenance reports separated, no amount/date/vendor duplicate heuristic, no unlabeled maintenance currency, monthly combined amount withheld and marked PARTIAL, known CPM explicitly PARTIAL and without legacy distance. Marked Task 1.3 done at this plan-authorized partial availability level; Phase 1 now satisfies all three tasks. Began Task 3.1 source audit and found its maintenance allocator still uses unresolved `loads.distance` and expense synchronization currently does not enforce approved status.
- **Requirement IDs:** BE-CALC-002–004 DONE; BE-CALC-010 IN_PROGRESS.
- **Files and Artifacts:** Phase 1 report calculators/DTOs/controller; V5 `maintenance_record_id`; plan Task 1.3 checkbox; `.ai-workflow/PROJECT_MEMORY.md`; Task 3.1 engine and allocator audit.
- **Decisions:** HOFF-0018's product decision blocker is superseded: the plan explicitly accepts PARTIAL combined cost before reconciliation, and currency-unknown maintenance is represented as such without conversion or aggregation. This does not authorize guessing currency or changing schema. Proceed in plan order to Task 3.1 because Phase 1 and Phase 2 are complete.
- **Assumptions:** No new business assumptions; maintenance-currency gap remains visible in API metadata.
- **Verification:** Phase 1 Java code `./mvnw -q -DskipTests compile` → PASS; `git diff --check` → PASS; workflow memory validator → PASS. Unit/integration tests NOT RUN.
- **Open Issues and Risks:** Task 3.1 `CostAllocator` uses `loads.distance`; `ShipmentCostEngine` needs approved-only synchronization and must not update finalized rows. RSK-001 remains for every unresolved legacy mileage field.
- **Blockers:** None for Task 3.1 implementation; allocation may return unavailable/reject if explicit trip miles are absent or shared across multiple loads.
- **Next Required Action:** Harden approved expense projection and immutable status behavior; calculate maintenance allocations only from explicit V3 actual miles with unambiguous one-load trip attribution.
- **Acceptance Gate:** Task 3.1 code and migrations meet listed invariants; compile and proportionate tests/migration checks pass before ticking its plan item.
- **Do Not Redo:** Do not use `loads.distance`/`trips.total_distance`; do not revisit Phase 1 currency decision unless authoritative source evidence changes.

### HOFF-0020 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-03T17:21:56+07:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / Phase 3 Task 3.1
- **Status:** PAUSED
- **Objective:** Continue the earliest incomplete task after Tasks 1.1–1.3 and Phase 2 are complete; harden Task 3.1 without advancing to later tasks.
- **Inputs Read:** Task 3.1 plan, canonical V9 migration, ShipmentCost controllers/services/entities/repositories, Expense schema/status, current SecurityConfig, explicit mileage attribution implementation.
- **Completed:** Replaced maintenance allocation's use of `loads.distance` with explicit `trips.actual_distance_miles`, rejecting missing mileage and multi-load trips; required currency and non-negative rate. Expense ledger projection now rejects non-approved expenses, creates/updates only pre-finalized rows, preserves finalized rows and detects source drift; bulk sync considers approved rows only. Added source enum/status/reference validation, non-negative manual cost validation, direct driver assignment, and RBAC protection for cost routes. Consolidated approved expense projection behavior behind one calculation engine. Updated Task 3.1 migration reference to canonical V9 because V8 is reserved for the assignment-history forward-fix. Tasks 1.1–1.3 are checked complete; Task 3.1 remains unchecked.
- **Requirement IDs:** BE-CALC-010 IN_PROGRESS; BE-CALC-002–004 DONE.
- **Files and Artifacts:** `CostAllocator.java`; `ShipmentCostEngine.java` in `service/cost` and `service/calculation`; `SecurityConfig.java`; Task 3.1 migration/checklist text; workflow memory.
- **Decisions:** No legacy distance or implicit USD fallback for maintenance allocation. Allocation rejects ambiguous multi-load trips. New costs start only DRAFT/VERIFIED and source projection remains idempotent by `(source_type, source_id)`.
- **Assumptions:** No expense approval transition exists in the Java source tree, so current explicit `POST /api/loads/{loadId}/costs/sync-expenses` is the only available synchronization trigger; this may not meet “automatic on approval” until integrated with the upstream approval workflow.
- **Verification:** `./mvnw -q -DskipTests compile` → PASS; `git diff --check` → PASS. Unit/integration tests NOT RUN. Prior clean/upgrade V1–V11 migration verification remains valid; no migration changed in this handoff.
- **Open Issues and Risks:** There is no Java expense-approval service/event to invoke projection at approval time. Task 3.1 remains open until that hook is integrated or the manual sync contract is confirmed. Runtime/JPA and endpoint authorization behavior were not integration-tested.
- **Blockers:** No blocker to code work; do not start Task 3.2 before approval-hook acceptance is resolved.
- **Next Required Action:** Identify the actual expense approval owner/integration path (or add the authorized approval command) and invoke the idempotent projection transactionally; then run applicable verification before marking Task 3.1 complete.
- **Acceptance Gate:** Approved load-attributed expenses project exactly once as ACTUAL costs; finalized rows stay immutable; maintenance allocation is based on explicit one-load actual miles; approval-to-ledger flow is verified.
- **Do Not Redo:** Do not use legacy distance fields; do not map missing currency to USD; do not let arbitrary API callers set costs to APPROVED/POSTED or mutate finalized costs.

### HOFF-0021 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-03T17:39:26+07:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / Phase 1 regression repair and Phase 3 Task 3.1
- **Status:** DONE
- **Objective:** Finish earlier-task defects before completing the canonical cost-ledger gate.
- **Inputs Read:** Full workflow skill/playbook and memory; Phase 1/2/3 task criteria and conventions; report calculators, security, expense/ledger/allocation schema and services; regression failures and real PostgreSQL startup traces.
- **Completed:** Repaired stale regression fixtures and real sub-minute delay/no-data/duration/partial-mileage/driver-cost/currency defects. Added versioned per-boundary rounding config, financial-report RBAC and contracts. Added accounting-only authenticated expense approval with audit and atomic ledger projection; pessimistic source locks serialize retries. Hardened finalized source drift detection and reserved system source types. Allocation uses historical truck and explicit single-load trip mileage, creates ESTIMATE with immutable snapshots and handles JSONB retry equality. Repaired duplicate bean/controller registration, Boot 4 Jackson injection and UTC auditing provider. Added V12 forward-only legacy audit naming alignment required by full Hibernate schema validation. Marked Task 3.1 complete only after verification; Task 3.2 is next and unchecked.
- **Requirement IDs:** BE-CALC-002–004 regression fixes; BE-CALC-009 snapshot enforcement; BE-CALC-010 DONE.
- **Files and Artifacts:** FinancialRoundingPolicy, CurrencyGuard, reporting calculators/DTOs/controller, application config, SecurityConfig, ExpenseApprovalService/Controller, ledger projector/allocator/repository locks, snapshot service, auditing config, canonical controller registration, V12 migration, Phase 1/cost contracts, unit/security/PostgreSQL tests, plan and workflow memory.
- **Decisions:** DEC-004/005. No legacy miles, currency guessing, manual system-source impersonation or finalized overwrite. V12 preserves data/types and applied V1–V11 checksums; production rollout requires checking external audit-column consumers.
- **Assumptions:** Explicit expense review states PENDING/SUBMITTED/PENDING_APPROVAL; approved source is verification, not shipment-cost approval. No production DB writes.
- **Verification:** `./mvnw -q clean test` with disposable PostgreSQL clean V1–V12 PASS; final `./mvnw -q test` PASS, 65 tests, 0 failures/errors, 1 legacy disabled startup test (new live integration covers full startup). V11–V12 upgrade PASS. Four PostgreSQL integration cases cover full entity/controller startup, concurrent expense retry, rollback and allocation snapshot/finalized retry. `git diff --check` PASS. Memory validator executed at handoff.
- **Open Issues and Risks:** Actual supported production tenant histories and old audit-column consumers need rollout validation; unresolved legacy distance remains disallowed. Task 3.2 has approval actor spoofing, weak state/idempotency and detention rounding/missing-time issues. Later plan phases are incomplete.
- **Blockers:** None to Task 3.2 implementation; unknown legacy semantics block only formulas using those fields.
- **Next Required Action:** Fix canonical accessorial service/controller approval authorization, reference validation and concurrent retry; implement precise detention blocks/hourly calculation and verify before marking Task 3.2 done.
- **Acceptance Gate:** Task 3.2 correct amounts, validated timestamps/rates/references, accounting-controlled audited approval, no duplicate source or rewritten final audit, tests pass.
- **Do Not Redo:** Do not rerun old failed fixtures unchanged or rewrite applied migrations. Keep historical handoffs unchanged; retain legacy mileage and maintenance-currency restrictions.

### HOFF-0022 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-03T17:43:40+07:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / Phase 3 Task 3.2
- **Status:** DONE
- **Objective:** Complete accessorial/detention only after Task 3.1 gate passed.
- **Inputs Read:** Task 3.2 criteria, canonical V9, both existing detention implementations, canonical and legacy controllers/services, DTOs/repositories/entities, security config, prior handoff.
- **Completed:** Detention retains fractional seconds for block ceiling and continuous-hour pricing, rounds only final money, rejects missing/reversed timestamps and invalid rates; read-only preview does not persist snapshots. Accessorial validates reference/type/attribution/nonnegative independent amounts. Approval uses authenticated employee, pending-state rule, row lock and immutable original audit; company-only ACTUAL/APPROVED projection is idempotent. RBAC prevents driver/dispatcher approval. Legacy duplicate service/controller adapters are inactive. Corrected plan's migration reference to the existing canonical shared V9; marked Task 3.2 complete.
- **Requirement IDs:** BE-CALC-012 DONE.
- **Files and Artifacts:** AccessorialService/Controller, DetentionCalculator implementations, request/result DTOs, AccessorialChargeRepository lock, SecurityConfig, deprecated inactive legacy adapter, detention/security/PostgreSQL tests, docs/accessorial-contracts.md, plan and memory.
- **Decisions:** Only company cost goes to shipment ledger; customer and driver amounts retain their separate invoice/settlement ownership. Fractional duration fields supplement integer-minute displays. No new migration version, no guessed currency, no client approval actor.
- **Assumptions:** Optional unspecified company/driver amount means explicitly no amount requested in this manual accessorial contract; supplied values remain independent. BlockMinutes zero means continuous-hour mode.
- **Verification:** Full `./mvnw -q test` with disposable PostgreSQL PASS: 74 tests, 0 failures/errors, 1 legacy disabled startup test; 6 PostgreSQL integration cases include actual endpoint actor-spoof protection, concurrent accessorial approval and unique company-only projection. Unit detention boundary/currency/timezone/error cases PASS; financial RBAC tests PASS; `git diff --check` PASS. Memory validator run at handoff.
- **Open Issues and Risks:** Later profitability/policy/settlement/payroll phases incomplete; migration rollout still needs supported tenant-history and external audit-consumer checks. No production deployment performed.
- **Blockers:** None to Task 3.3; unresolved legacy distance remains disallowed.
- **Next Required Action:** Reconcile ProfitabilityService with actual eligible invoice subtotals, ACTUAL APPROVED/POSTED ledger sums, variable/fixed allocation split and currency-safe grouped reports; test missing-mileage/zero-denominator semantics.
- **Acceptance Gate:** Task 3.3 formulas and all listed reports obey actual-only/currency/source/mileage invariants; relevant unit, live API/integration and authorization tests pass before ticking it or advancing Phase 4.
- **Do Not Redo:** Do not reactivate duplicate adapters, fabricate zero detention for missing timestamps, count customer/driver accessorial amounts as company cost, or rewrite existing V9.

### HOFF-0023 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-03T17:47:03+07:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / Phase 3 Task 3.3 domain gate
- **Status:** BLOCKED
- **Objective:** Audit earliest remaining task without claiming financial formulas have authoritative inputs.
- **Inputs Read:** Task 3.3 and financial conventions; ProfitabilityService and all related DTOs/routes/tests; V9 shipment ledger schema and cost categories; V2 specification sections 14, 35 and 69; repository-wide classification search; Control and HOFF-0022.
- **Completed:** Confirmed neither schema nor specifications define variable versus allocated-fixed classification; V2 explicitly notes fixed allocation is not modeled. Identified remaining quote-revenue fallback, noneligible invoice inclusion, grouped currency/current-truck attribution, swallowed exceptions and missing grouped report endpoints. Protected financial-summary/profitability paths with accounting RBAC and added denial tests. Kept Task 3.3 unchecked and did not advance Phase 4. Earlier Tasks 3.1/3.2 remain verified complete.
- **Requirement IDs:** BE-CALC-011 BLOCKED; BE-CALC-010/012 DONE.
- **Files and Artifacts:** SecurityConfig, FinancialReportSecurityTest, Task 3.3 progress note and workflow memory; source/spec audit only for profitability formulas.
- **Decisions:** No inferred fixed/variable category mapping or claim all costs are variable. Request a material business classification decision, not generic implementation permission. Prefer explicit ledger classification with historical UNCLASSIFIED over guessed migration/backfill; an authoritative supplied versioned mapping is an alternative.
- **Assumptions:** None about fixed/variable classifications. Full source/spec search found formulas but no policy mapping or classification column.
- **Verification:** Final full `./mvnw -q test` with disposable PostgreSQL PASS: 74 tests, 0 failures/errors, 1 legacy startup skip; 6 live integration tests cover full startup and implemented ledger/accessorial flows. Task 3.3 business formulas NOT VERIFIED/NOT COMPLETE; existing minimal regression tests are not a completion gate. `git diff --check` PASS; memory validator run at handoff.
- **Open Issues and Risks:** RSK-003; later phases incomplete. RSK-001/002 and V12 external-consumer rollout coordination remain open. No production DB/application deployment changed.
- **Blockers:** BLK-002: authoritative ActualVariableCost / AllocatedFixedCost classification choice. BLK-001 remains limited to unresolved legacy-mile formulas.
- **Next Required Action:** User/Product Owner chooses explicit ledger cost_class with UNCLASSIFIED history or supplies versioned category/source classification rules; Backend Developer then finishes Task 3.3 formulas, safe availability/grouping/attribution, report endpoints and tests.
- **Acceptance Gate:** Classification is explicit/auditable, actual-only and cost-stage rules hold, currencies are not mixed, historical mileage/truck attribution is preserved, missing inputs produce honest availability, all listed routes and proportionate verification pass before checkbox or Phase 4.
- **Do Not Redo:** Do not reopen completed Tasks 3.1/3.2 absent new evidence; do not treat this source audit or minimal profitability tests as Task 3.3 completion; do not infer legacy mileage or fixed/variable classification.

### HOFF-0024 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-03T17:54:23+07:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / Phase 3 Task 3.3 independently specified facts
- **Status:** BLOCKED
- **Objective:** Make meaningful in-scope progress without guessing the unresolved classification decision or advancing later tasks.
- **Inputs Read:** Full workflow skill/playbook/memory, Task 3.3 criteria, existing profitability implementation/DTOs/routes, invoice eligibility/reconciliation, currency/rounding/metric contracts, V1/V9 schemas, trip-stop repository and unit/live test fixtures.
- **Completed:** Rebuilt actual financial facts using eligible reconciled invoice subtotals, no quote fallback, only ACTUAL APPROVED/POSTED costs and signed credits. Estimate variance requires accepted APPROVED/POSTED estimate and rounds raw differences once. Added all grouped report routes. Groups partition by currency, derive truck from trip association, retain unknown/multiple trucks in an UNALLOCATED bucket and propagate errors instead of silently skipping. Mileage uses explicit V3 and exactly one persisted load attribution per trip; missing/shared/zero/inconsistent partitions suppress affected metrics, including break-even when total attribution is incomplete. Classification-dependent profit/margin fields now null/UNAVAILABLE with explicit reason instead of treating all cost as variable. Added contracts, 11 dedicated fact regression cases, real report API/auth/error/read-only checks, and plan subtask checkboxes. Parent Task 3.3 remains unchecked; Phase 4 not advanced.
- **Requirement IDs:** BE-CALC-011 partial; earlier BE-CALC-010/012 completion retained.
- **Files and Artifacts:** ProfitabilityService, LoadFinancialSummary/LoadProfitabilityReport/lane/truck DTOs, ReportController, ProfitabilityServiceTest/ProfitabilityFactsTest, FinancialReportSecurityTest, CostLedgerPostgresTest, docs/profitability-contracts.md, plan/memory.
- **Decisions:** No schema/business classification choice inferred from the repeated implementation request. Work independent of BLK-002 was authorized and completed. No dashboard writes/snapshots; no quote-to-actual substitution; no foreign-currency addition; no guessed proration or current-load truck history. Truck basis is persisted trip association, not an immutable truck-history claim.
- **Assumptions:** Estimate APPROVED/POSTED is the accepted authoritative subset; VERIFIED caller-supplied allocation is not an authoritative estimate. Class-dependent formulas remain explicitly unavailable until their inputs exist. No new migration or production deployment.
- **Verification:** `./mvnw -q clean test` and final `./mvnw -q test` with newly created disposable PostgreSQL PASS: 87 tests, 0 failures/errors, 1 disabled legacy startup test. 8 real PostgreSQL cases include four report endpoints, serialization/403/400 behavior, reconciliation with line items, and no GET ledger/snapshot write. Clean V1–V12 migrates and all JPA/controllers load. `git diff --check` PASS; workflow validator run at handoff.
- **Open Issues and Risks:** Classification-dependent formulas still NOT COMPLETE; no classification-specific positive formula tests possible without authoritative input model. Full scan/per-load query pattern requires later performance review; supported production migration histories/external V12 consumers remain rollout risks. Later plan phases incomplete.
- **Blockers:** BLK-002 remains only the variable/allocated-fixed input model/policy decision for Task 3.3 completion. BLK-001 remains limited to unresolved legacy-mile formulas.
- **Next Required Action:** Choose explicit ledger classification retaining historical UNCLASSIFIED or supply authoritative versioned category/source rules; implement its inputs, contribution/allocated-profit/margin formulas and availability tests; rerun relevant verification, then mark Task 3.3 and advance Phase 4.
- **Acceptance Gate:** Profitability formulas are calculated only from authoritative classes; unknown history remains unavailable without fabricated zero/backfill; routes preserve the now-verified actual/currency/mileage/authorization invariants; parent checkbox only after complete criteria.
- **Do Not Redo:** Do not reintroduce quoted revenue, noneligible invoice statuses, VERIFIED actual costs, currency mixing, catch-and-skip, mutable load truck attribution, or legacy-distance unit economics. Earlier Tasks 3.1/3.2 are not reopened absent new evidence.

### HOFF-0025 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-03T21:44:54+07:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / Phase 3 completion → Phase 4A
- **Status:** DONE
- **Objective:** Resolve BE-CALC-011-COST-CLASSIFICATION and finish Task 3.3 using the explicit continuation requirements.
- **Inputs Read:** Full memory/skill/playbook; plan; enum/entities; both shipment engines/allocator; profitability service/DTOs/tests; snapshot architecture; live PostgreSQL fixtures and API contracts.
- **Completed:** Added versioned context-aware policy V1, calculator, actual-cost explanation, contribution/allocated-profit and ratio metrics, legacy PERCENT compatibility, currency/policy/unknown/zero guards and grouped formulas. Maintenance requires explicit DIRECT or ALLOCATION/CPM_MILEAGE; OTHER stays unknown. Existing estimates never become actual. Preserved read-only GET/no snapshots and all earlier baseline invariants. Created the previously missing plan-progress-summary.md; Phase 3 marked COMPLETE.
- **Requirement IDs:** BE-CALC-011-COST-CLASSIFICATION; BE-CALC-011; regression BE-CALC-010/012.
- **Files and Artifacts:** service/profitability policy/calculator/service; classification/profitability DTOs; policy/calculator/facts/service tests; CostLedgerPostgresTest; docs/profitability-contracts.md; plan; progress; memory.
- **Decisions:** User's supplied V1 mapping supersedes the HOFF-0023/0024 design blocker. INSURANCE uses the authorized default fixed category rule; no invented enum categories or migration/backfill. New ratio metrics use RATIO (0.25); legacy MARGIN_PERCENT retains PERCENT (25). Recorded ledger completeness differs from availability of all company overhead. Future persisted profitability must serialize the full policy/input/result explanation; current architecture persists no profitability results.
- **Assumptions:** No classification inference for missing/ambiguous maintenance metadata. No production data altered. Existing staged Dockerfile/application changes belong to the user and must remain untouched by task commits.
- **Verification:** Final python3 /tmp/logisticsx_phase3_regression.py → ./mvnw -q clean test on codex_classification_20261003144358 PASS: 100 tests, 0 failures/errors, 1 pre-existing legacy skip; 9 PostgreSQL cases executed. Clean V1–V12 and full JPA/controller startup PASS. Policy/calculator tests cover every category, metadata conflicts, credit/zero/unknown/exclusion, rounding, policy mismatch, currency mismatch and weighted grouping. git diff --check PASS; memory validator required before next slice.
- **Open Issues and Risks:** Later phases remain incomplete. Production migration histories/rollout and report performance remain separate risks. Legacy distance/period-overhead data are not invented. Payroll legal policy and historical vehicle-state definitions still need authoritative inputs.
- **Blockers:** None for Phase 4A. BLK-002 RESOLVED at the availability-aware engine level; individual unknown costs correctly suppress affected profit metrics. BLK-001 remains limited to legacy-mile formulas.
- **Next Required Action:** Read progress checkpoint, then finish/test existing V10 driver policy/pay-period slice; preserve old versions and deterministic work-date resolution. Continue Phase 4 in separate verified sub-tasks.
- **Acceptance Gate:** Phase 4A unit/integration/RBAC/concurrency validation; no renumbering/recreation of existing migrations; all regression suites remain green.
- **Do Not Redo:** Phase 0–2 audits, Shipment Cost/Accessorial subsystems or classified-profit formulas unless new regression evidence appears. Do not guess OTHER/ambiguous maintenance, infer actual from estimate, write snapshots on GET or mix currencies.

### HOFF-0026 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-03T21:51:16+07:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / Phase 4A completion → 4B
- **Status:** DONE
- **Objective:** Complete existing V10 policy/pay-period slice without recreating schema or overwriting history.
- **Inputs Read:** Progress/Control/HOFF-0025; V10; policy/entity/repositories/DTO/controller; assignment mileage source; existing engine resolver and security.
- **Completed:** New-version locks/latest-family check and fixed scope; resolver chooses work-date family/version with explicit driver/default precedence, refuses ambiguity and prevents expired-version resurrection. Added rate/currency/precision/ratio/date and sourced-basis validation; protected pay-period create/list APIs. Prepared resolver/canonical basis engine integration for later monetary gates; does not claim full engine verified.
- **Requirement IDs:** BE-CALC-013 / Phase 4A.
- **Files and Artifacts:** DriverPayPolicyService/Resolver/Repository; PayPeriodService/Repository/Controller; SecurityConfig; policy/resolver/period unit tests; additive PG policy tests; driver-pay-policy-contracts; plan/progress/memory. Existing engine resolver/basis integration remains foundation for BE-CALC-014.
- **Decisions:** Only assignment ACTUAL_ALL_MILES/PLANNED_ALL_MILES and reconciled INVOICE_SUBTOTAL have supported driver/revenue sources. Historical alias rows preserved. No arbitrary selection across policy families; no historical row mutation. No new migration.
- **Assumptions:** None for unavailable per-driver loaded/practical/contract miles. Period overlaps are not newly forbidden; source eligibility is a later engine invariant.
- **Verification:** ./mvnw -q -Dtest=DriverPayPolicyServiceTest,DriverPayPolicyResolverTest,PayPeriodServiceTest test PASS; full disposable PG clean test PASS: 114 tests, 0 failure/error, 1 legacy skip, 11 live PG cases. Concurrent versions produced one successor/one POLICY_VERSION_STALE; locked settlement retained old policy ID/version/rate. Endpoint 201/400/403 contracts PASS. git diff --check PASS; memory validator required before next slice.
- **Open Issues and Risks:** Settlement engine monetary/reconciliation/idempotency/workflow tests still incomplete. Payroll jurisdiction/worker-classification question sent asynchronously; no statutory rates assumed. Production rollout/performance remain separate risks.
- **Blockers:** None for Phase 4B.
- **Next Required Action:** Complete/test assignment-mileage calculator, explicit unavailable source errors and full input snapshots; continue remaining Phase 4 slices only after per-task tests/checkpoint.
- **Acceptance Gate:** Unit/integration/regression for selected mileage basis and no fallback; snapshot contains basis/rate/source IDs/version.
- **Do Not Redo:** Existing V10/V11, policy version history, completed Phase 3 and 4A unless regression evidence changes.

## 10. Final Readiness

| Check | Status | Evidence / Exception |
|---|---|---|
| Requirements implemented and traced | IN_PROGRESS | Phases 0–2 and Tasks 3.1–3.2 verified; later tasks remain open |
| Build successful | PASS | `./mvnw -q -DskipTests compile` after Phase 3/4 changes |
| Tests successful | IN_PROGRESS | 87 tests, 0 failures/errors, 1 legacy skip; 8 live PostgreSQL cases; classification/later features incomplete |
| API working | IN_PROGRESS | Implemented ledger/accessorial/report routes verified; class-dependent profit formulas and later APIs incomplete |
| Frontend working | NOT_APPLICABLE | API-only scope |
| Database migrations working | IN_PROGRESS | V1–V12 clean and V11–V12 upgrade verified; supported production tenant history not available |
| Authentication and authorization working | IN_PROGRESS | Payroll/policy routes require payroll-related roles; integration behavior not verified |
| Validation and error handling working | NOT_STARTED | Not verified |
| Security reviewed | NOT_STARTED | Not reviewed |
| Performance reviewed | NOT_STARTED | Not reviewed |
| No hardcoded secrets | NOT_STARTED | Not scanned |
| Deployment and rollback working | NOT_STARTED | Not verified |
| Observability ready | NOT_STARTED | Not verified |
| Documentation complete | IN_PROGRESS | Phase 0 updated |
| No unresolved release-blocking defects | FAIL | BLK-001 and later phases incomplete; classification BLK-002 resolved |

**Production Verdict:** NOT READY

**Residual Risks / Approved Exceptions:** RSK-001; RSK-002; RSK-003. No production-readiness exception is approved.
