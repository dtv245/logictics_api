# AI Software Project Memory

## 1. Control

| Field | Value |
|---|---|
| Schema Version | 1 |
| Revision | 63 |
| Project | LogisticsX TMS |
| Repository Root | /home/vumoi/logictics_api |
| Execution Mode | EXISTING_PROJECT |
| Last Updated | 2026-10-04T18:52:00+00:00 |
| Current Phase | 10 — Testing / Final approved backend convention plan verification |
| Active Role | QA / Tester |
| Status | DONE |
| Next Role | QA / Tester |
| Next Action | PLAN COMPLETE for approved backend Convention V1, Phases 0–8/final gates PASS at V35. Hand off docs/backend-plan-final-verification.md and progress; production sources/configuration/capture and release review/deployment are separate, not authorized/performed. Preserve dirty work and V1–V35; future schema V36+ only. |
| Handoff Sequence | 63 |

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

- **Feature/Task ID:** Approved backend Convention V1 — PLAN COMPLETE
- **Objective:** Completed convention Phases 0–8 and actual final tests/build/migration/API/history/tenant gates; hand off evidence without conflating production readiness.
- **Acceptance Gate:** PASS: final same-code clean V1→V35 / populated V34→V35 Maven clean verify/validate; 456 reported/455 executed, 0 failures/errors, one legacy skip, 160 PG; 17 Python; executable JAR/OpenAPI/checksums/diff/memory.
- **Allowed Change Scope:** Handoff only; preserve completed source and immutable/applied history. Production source/configuration/capture/release review requires separate operational work; no deployment, commit, completed-phase refactor or new business-policy assumption.

### Phase Status

| Phase | Primary Owner | Allowed Active Roles | Status | Outputs / Evidence |
|---|---|---|---|---|
| 0. Project Understanding | Product Owner | Product Owner; Business Analyst; Tech Lead | DONE | Phase 0 audit documents |
| 1. Business Analysis | Business Analyst | Business Analyst; Product Owner; QA / Tester; Backend Developer | DONE | BE-CALC-002–004 complete; unsupported maintenance currency and combined totals are explicitly PARTIAL per plan |
| 2. Domain Modeling | Software Architect | Software Architect; Business Analyst; Tech Lead; Database Engineer | NOT_STARTED | — |
| 3. Database Design | Database Engineer | Database Engineer; Software Architect; Backend Developer | DONE | Approved backend schema V35 clean/V34 upgrade/validate; V34 separately clean/V33 upgrade; earlier SHA unchanged, production rollout separate |
| 4. System Architecture | Software Architect | Software Architect; Tech Lead; Security Engineer; Database Engineer | DONE | ADR-001 and ADR-002 |
| 5. API Design | Tech Lead | Tech Lead; Software Architect; Backend Developer; Frontend Developer; Security Engineer | NOT_STARTED | — |
| 6. Project Structure | Tech Lead | Tech Lead; Software Architect; Backend Developer; Frontend Developer | DONE | Existing Spring layout |
| 7. Backend Development | Backend Developer | Backend Developer; Tech Lead; Database Engineer; QA / Tester; Security Engineer; Code Reviewer | DONE | Convention 0–8 COMPLETE under all locked decisions; V35 implementation/final QA gates PASS; no production deployment claim |
| 8. Frontend Development | Frontend Developer | Frontend Developer; Tech Lead; QA / Tester; Security Engineer; Code Reviewer | NOT_APPLICABLE | API-only repository scope |
| 9. Integration | Tech Lead | Tech Lead; Backend Developer; Frontend Developer; QA / Tester | NOT_STARTED | — |
| 10. Testing | QA / Tester | QA / Tester; Backend Developer; Frontend Developer; Tech Lead | DONE | Final same-code clean V35 / populated V34 upgrade PASS: 456 reported/455 executed, zero failures/errors, one legacy skip, 160 PG; 17 Python PASS; verify/repackage/runtime OpenAPI/hash/diff/memory PASS |
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
| BE-CALC-014 | Task 4.2 | Backend Developer | DONE | All 4A–4F gates; 141 regression tests incl.21 PG, V13 clean/upgrade, concurrency/reconciliation/history/idempotency | Jurisdiction-neutral 015B |
| BE-CALC-015 | Task 5.1 | Database Engineer | DONE | V11 creates payroll runs/items, settlement join, payslips, and payments; local clean and upgrade succeeded | Continue Task 5.2 workflow without hardcoding jurisdiction tax rules |
| BE-CALC-015 | Tasks 5.2–5.4 | Backend Developer | DONE | Verified clean V1→V20 + populated V19→V20; 192 reported / 191 executed / one legacy skip; 40 PG methods | Preserve, no repeat implementation |
| Phase 6 | 6A–6G | Backend Developer | DONE | RATE/BILL locked; final V29 clean/V27 batch/V28 upgrade/validate, 302 reported / 94 PG | Preserve completed baseline |
| BE-CALC-016 | Phase 7 | Backend Developer | DONE | All approved policy/source/score/run/Accept/tenant/concurrency gates PASS; V33 clean/upgrade/validate, 422/421 Java, 138 PG, 16 Python | Preserve completed phase; source deployment separate |
| BE-CALC-017 | Phase 8 | Backend Developer | DONE | All Fleet decisions locked; V34/V35 immutable evidence/strict report/proven miles/tenant/context gates, 456/455 Java, 160 PG, 17 Python; clean/upgrade/validate/build/hash/diff PASS | Preserve completed history/report contracts; source production capture/deployment separate |
| BE-CALC-FINAL | Convention Phases 0–8 final gate | QA / Tester | DONE | docs/backend-plan-final-verification.md; final clean/upgrade/validate/456-455/160PG/17Python/OpenAPI/JAR/SHA/diff PASS | Verified backend V1 handoff, not production readiness |

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
| BE-CALC-013–014 | Effective policies, immutable locks, append-only corrections and source eligibility | V10/V13 | Protected policy/settlement APIs and per-line actual cost | 141 regression tests incl.21 PG, clean/upgrade | DONE |
| BE-CALC-015 Task 5.1 | Payroll is separate from invoices; payment attempts are idempotent | V11 payroll runs/items, join, payslips, payment attempts | Schema only | Flyway clean V1→V11, upgrade V10→V11, validate 11 migrations | DONE |
| Convention Phases 5–6 final | Immutable financial history, completed payroll, explicit rating/tax/billing/revenue decisions | V20–V29 and retained APIs | Protected rating, billing, settlement, payroll workflows | All retained financial/date/mileage/tax/billing suites green in final 456/455 regression | DONE |
| BE-CALC-016 | Confirmed source/feasibility/score/tie and existing-Trip-only assignment | V30–V33 immutable policy/input/run/acceptance audit | Authorized policy/run/view/accept APIs | 44 optimizer PG + 76 unit/HTTP/scope, full regression and physical tenant gates | DONE |
| BE-CALC-017 | Confirmed bounded history, strict availability/numeric and proven completion mileage | V34/V35 immutable policy/events/attribution | Fleet capture + read-only report/health API | 22 Fleet PG + 12 Fleet units; context/tenant/coverage/immutability and runtime OpenAPI | DONE |

## 5. Architecture and Data Snapshot

- **Architecture Style:** Tenant-aware Spring modular monolith; ADR-001 selects database per tenant.
- **Modules / Boundaries:** Controller → service → repository/entity.
- **Dependency Direction:** HTTP depends on services; services depend on repositories/entities.
- **Authentication / Authorization:** JWT tenant claim routes to a tenant data source.
- **Data Model / Migration:** Latest V35. Clean V1→V35 / populated V34→V35 + validate/build/regression PASS; V34 separately clean/V33 upgrade PASS. All earlier SHA unchanged, V35 untouched after application; financial/optimizer/fleet history immutable. Production rollout separate.
- **API / Integration Contract:** Existing envelope/roles, real cross-domain controller/service/PostgreSQL tests; final executable local OpenAPI 3.1.0 with 134 paths/283 schemas and 16 required new operations verified. Not frontend E2E/production certification.
- **Deployment / Runtime:** Docker assets exist but are unverified.

## 6. Decisions

| ID | Date | Owner | Decision | Reason / Evidence | Consequences | Supersedes |
|---|---|---|---|---|---|---|
| DEC-001 | 2026-10-03 | Software Architect | DATABASE_PER_TENANT | ADR-001 and tenant routing source | No tenant_id on business tables | — |
| DEC-002 | 2026-10-03 | Software Architect | Flyway is schema owner from V1 baseline | ADR-002 and TenantMigrationService | Legacy EF tooling must not mutate schema | — |
| DEC-003 | 2026-10-03 | Business Analyst | Legacy distance semantics unresolved | DTOs accept raw Double; legacy spec marks unit TBD | Block distance-dependent formulas | — |
| DEC-004 | 2026-10-03 | Backend Developer | Caller-supplied maintenance CPM produces ESTIMATE, not ACTUAL | No source proves an actual incurred rate; Task 3.1 snapshot captures explicit mileage/truck/rate | Actual profitability must not include this estimate | — |
| DEC-005 | 2026-10-03 | Backend Developer | Pending/submitted expense approval atomically projects ACTUAL/VERIFIED cost with row locks | No Java approval command existed; expense schema supports approval audit | Endpoint requires accounting role and tenant employee; unknown/rejected/draft states reject | HOFF-0020 missing-hook assumption |
| DEC-006 | 2026-10-04 | Product Owner | PayrollRun terminal state is `COMPLETED` iff all items are `PAID` or audited `NO_PAYMENT_REQUIRED`; zero-net settlement remains `LOCKED`; Invoice legacy API fields remain but input is rejected | Explicit user domain decision; V20 schema, service, contract and tests implement it | No fake zero-amount payments; completedAt/source audit; historical invoice response values are read-only; Phase 6 remains gated | — |

| DEC-007 | 2026-10-04 | Product Owner | RATE-DEC-001…007 confirmed; Rating V1 FLAT/PER_MILE linehaul + INDEX_BASED_MPG FSC only | Explicit replies in docs/rating-policy-decisions.md | No default priority/mileage/MPG/rounding; accepted history immutable; methods outside V1 deferred | Earlier Phase 6 OPEN gate |

| DEC-008 | 2026-10-04 | Product Owner | Independent pickup business LocalDate, no TIMESTAMPTZ backfill/derivation, explicit audited correction and snapshot source | Explicit latest user decision; V22 and date tests | Legacy instant/API kept; new requestedPickupBusinessDate is source of pricing date; timestamp edits never mutate it | BLK-004 proposed adapter |

| DEC-009 | 2026-10-04 | Product Owner | BILL-DEC-001…006 CONFIRMED/LOCKED; Accounting tax decision, signed immutable billing chain, versioned driver basis, explicit credit evidence and stale-before-lock rejection/recalculation | User confirmations and HOFF-0047…0050; all Phase 6 gates PASS at V29 | Phase 6 COMPLETE; no unresolved requested Billing V1 policy | Earlier additional 6G gate |
| DEC-010 | 2026-10-04 | Product Owner | OPT-DEC-001 A trusted adapters, missing/stale evidence rejects; OPT-DEC-002 A all four utilities with approved forecast sources; OPT-DEC-003 A equal rank, dispatcher chooses, UUID display-only | Three explicit replies to the first OPT batch | Do not ask those directions again; exact source contracts/utility formulas/weights/precision and acceptance lifecycle action still required before authoritative implementation | HOFF-0051 pending direction choices |
| DEC-011 | 2026-10-04 | Product Owner | OPT-DEC-004…010 exact eligibility/source/curves/weights/numeric/forecast/Accept contracts CONFIRMED/LOCKED | Explicit full user batch; docs/optimization-policy-decisions.md, verified V33 gates | Resolves DEC-010 later-detail gate; source/allowlists authored, no global defaults; existing Trip assignment only, no dispatch; Phase 7 COMPLETE | Former exact OPT blocker |
| DEC-012 | 2026-10-04 | Product Owner | All required FLEET-DEC-001…009 A contracts CONFIRMED/LOCKED; 004/005 definition NOT_APPLICABLE to already approved unsupported-source UNAVAILABLE | Explicit first/follow-up replies; docs/fleet-utilization-policy-decisions.md; V34/V35 final gates | Authored maps/source/bounded evidence, no fabricated history, strict coverage/numeric, proven actual completion truck/miles; conflicts unavailable until audited correction; Phase 8 COMPLETE | Former fleet gate |

## 7. Assumptions, Risks, and Blockers

### Assumptions

| ID | Assumption | Owner | Validation / Due | Status |
|---|---|---|---|---|
| ASM-001 | Existing uncommitted changes belong to the user. | Tech Lead | Preserve during future edits | OPEN |

### Risks

| ID | Severity | Risk | Evidence | Mitigation | Owner | Status |
|---|---|---|---|---|---|---|
| RSK-001 | HIGH | Wrong distance semantics corrupt KPI, cost, or payroll. | Unit/source are absent or TBD. | Block dependent formulas. | Product Owner | OPEN |
| RSK-002 | HIGH | Production tenant history must match verified supported migration chain. | Local clean V1→V35 and populated V34→V35 plus all earlier batch validate/SHA proofs PASS; old duplicate active candidates were addressed at the baseline. Production rollout/history not inspected here. | Preserve applied files; perform separate production tenant validate/backup/rollout gate before deployment. | Database Engineer | MITIGATED |
| RSK-003 | HIGH | Task 3.3 formerly lacked authoritative variable/fixed policy. | User supplied V1 classification rules; HOFF-0025 implements policy/formulas/tests with unknown semantics preserved. | Preserve policy version and unknown metadata; production readiness remains separate. | Backend Developer | CLOSED |
| RSK-004 | HIGH | Former payroll completion/Invoice legacy closure. | Approved semantics implemented; verified clean V20 and populated V19→V20. | Preserve completed baseline and read-only history. | Product Owner / Backend Developer | CLOSED |
| RSK-005 | HIGH | Phase 6 financial correctness requires approved policy and verified sources. | RATE/BILL locked; 6A–6G verified at V29, immutable history/source/date/concurrency gates PASS. Unqualified mileage or historical NULL dates correctly fail closed. | Preserve completed baseline; no inferred mileage attribution, historical date or financial policy. | Product Owner / Tech Lead | MITIGATED |

### Blockers

| ID | Blocker | Needed To Unblock | Owner | Status |
|---|---|---|---|---|
| BLK-001 | Legacy raw distance remains unqualified; it is not a formula input in approved V1. | New V1 uses explicit decimal MILE/provenance and fail-closed legacy availability; separate written source qualification needed only to make unsupported legacy metrics available. | Product Owner | MITIGATED |
| BLK-002 | Former Task 3.3 classification design blocker. | User-authorized LOGISTICSX_COST_CLASSIFICATION V1 implemented/tested in HOFF-0025; ambiguous historical rows stay UNCLASSIFIED. | Backend Developer | RESOLVED |
| BLK-003 | Former Phase 6 business gate. | All seven RATE-DEC and FLAT/PER_MILE + INDEX_BASED_MPG V1 scope confirmed by user; implement and verify incrementally. | Product Owner / Tech Lead | RESOLVED |

| BLK-004 | Former Load date adapter provenance blocker. | User confirmed independent DATE, no historical backfill, explicit authenticated audited corrections; V22 and adapter verified. | Product Owner / Backend Developer | RESOLVED |

| BLK-005 | Former 6G additional tax/document/revenue-source contracts. | BILL-DEC-001…006 explicitly confirmed/locked and implemented; invoice-rating-v1-contract.md and final V29 gates. | Product Owner | RESOLVED |
| BLK-006 | Former optimization exact-policy business gate. | All OPT-DEC-001…010 explicitly confirmed/locked in docs/optimization-policy-decisions.md; runtime authored allowlists/source qualifications mandatory. | Product Owner | RESOLVED |
| BLK-007 | Former fleet policy gate. | Four A directions and exact FLEET-DEC-008/009 A received/locked; implement without defaults; health unavailable already authorized. | Product Owner | RESOLVED |

## 8. Artifact Index

| Artifact | Path / URL | Owner | Status | Last Verified |
|---|---|---|---|---|
| Convention plan | plan-convention-v3-implementation-ready.md | Product Owner | DONE | 2026-10-05 approved backend V1 Phases 0–8 / final V35 gates PASS |
| Domain audit | docs/current-domain-semantics.md | Business Analyst | DONE | 2026-10-03 source audit |
| Timezone audit | docs/legacy-timezone-semantics.md | Business Analyst | DONE | 2026-10-03 source audit |
| Tenant ADR | docs/adr/ADR-001-tenant-isolation.md | Software Architect | DONE | 2026-10-03 source audit |
| Migration ADR | docs/adr/ADR-002-migration-ownership.md | Software Architect | DONE | 2026-10-03 source audit |
| Phase 1 reporting contract | docs/reporting-contracts.md | Backend Developer | DONE | Phase 1 regressions and financial RBAC tests pass |
| Task 3.1 ledger contract | docs/cost-ledger-contracts.md | Backend Developer | DONE | PostgreSQL concurrency/rollback/snapshot tests pass |
| Task 3.2 accessorial contract | docs/accessorial-contracts.md | Backend Developer | DONE | Detention edge cases and PostgreSQL company-only concurrency/actor tests pass |
| Task 3.3 profitability contract | docs/profitability-contracts.md | Backend Developer | DONE | 100 regression tests; versioned/explainable classification and formulas verified |
| Continuation checkpoint | plan-progress-summary.md | QA / Tester | DONE | Convention 0–8 PLAN COMPLETE; final V35 clean/upgrade 456/455 Java, 160 PG and 17 Python PASS |
| Rating decision gate | docs/rating-policy-decisions.md | Tech Lead | DONE | All RATE-DEC-001…007 and V1 subset explicitly user-confirmed |
| Regression runner | scripts/verify_backend_regression.py | Backend Developer | DONE | 17 Python tests PASS; final clean/upgrade verify/repackage; enforces 456/455 and 13 PG domain floors totaling 160 |
| Pickup date/mileage contract | docs/load-pickup-date-and-rating-mileage.md | Backend Developer | DONE | V22/V23 clean/upgrade/validate; 241 reported Java tests, 58 PG methods; accepted financial snapshot remains 6F |
| EIA FSC implementation | docs/rating-fsc-v1.md | Backend Developer | DONE | 6D full regression: 252 reported, 58 PG; official series mapping/local HTTP parser tests |
| Explainable rating engine | docs/rating-engine-v1.md | Backend Developer | DONE | 6E: 259 reported, 61 PG; ephemeral preview/API/source tests |
| Accepted financial rating | docs/rating-accepted-snapshots.md | Backend Developer | DONE | V24 clean/V23 upgrade/validate; 267 reported, 67 PG; actor/date/revision/history/concurrency/API guards |
| Remaining decision gates | docs/remaining-business-decision-gates.md | Backend Developer | DONE | All required RATE/BILL/OPT/FLEET V1 gates resolved and verified |
| Optimization decision gate | docs/optimization-policy-decisions.md | Backend Developer | DONE | OPT-DEC-001…010 CONFIRMED/LOCKED; exact approved contracts, no assumed parameters |
| Fleet decision gate | docs/fleet-utilization-policy-decisions.md | Backend Developer | DONE | All required 001…009 choices confirmed/locked; unavailable health outcomes authorized |
| Fleet runtime contract | docs/fleet-history-v1-contract.md | Backend Developer | DONE | Immutable real history/attribution, strict SQL reports and V35 full clean/upgrade/tenant gates |
| Final backend verification | docs/backend-plan-final-verification.md | QA / Tester | DONE | Actual final same-code clean/upgrade/regression/API/immutability/tenant matrix; production readiness separate |
| Applied migration SHA manifest | docs/verification/migration-sha256-v35.txt | QA / Tester | DONE | All 35 active applied SQL files match verified SHA; no earlier migration edit |

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

### HOFF-0027 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-03T21:55:00+07:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / Phase 4B completion → 4C
- **Status:** DONE
- **Objective:** Calculate driver mileage from explicit selected assignment source and snapshot evidence.
- **Inputs Read:** Progress/HOFF-0026, assignment entity/source, engine, rounding and snapshot architecture; existing timezone audit read only as dependency; latest user payroll steering.
- **Completed:** MileagePayCalculator and engine integration; actual/planned source selection with no fallback; source/rate validation; versioned input/raw/rounded-result snapshot; missing actual creates no settlement/snapshot. Recorded MULTI_JURISDICTION neutral payroll architecture in progress file.
- **Requirement IDs:** BE-CALC-014 / Phase 4B; subsequent 015B architecture steering.
- **Files and Artifacts:** MileagePayCalculator, DriverPayEngine integration, mileage unit tests, additive live settlement test, progress/memory.
- **Decisions:** Payroll core will use generic country/subdivision/locality and independent worker classification, jurisdiction resolution priority and versioned policy/tax ports; missing statutory inputs block finalization. No US/VN/legal-rate implementation guessed. Mileage uses assignment quantities only; no trip/legacy fallback.
- **Assumptions:** No arbitrary loaded/practical/contract attribution. Period/assignment eligibility is separate remaining 4F work.
- **Verification:** Full disposable PG clean regression on codex_classification_20261003145401 PASS: 119 tests, 0 failures/errors, 1 legacy skip; 12 live PG cases. Four mileage unit cases and persisted snapshot/source/rollback/sequential retry case PASS. No new migration; clean V1–V12 preserved. Memory validation required before next slice.
- **Open Issues and Risks:** Remaining engine paths/work-date eligibility/cross-period source reuse/corrections/concurrency are not claimed complete. No statutory adapters configured; neutral payroll remains authorized to proceed after Phase 4.
- **Blockers:** None for Phase 4C.
- **Next Required Action:** Complete per-load/hourly/daily/flat calculator and input/snapshot tests; then independent 4D–4F gates.
- **Acceptance Gate:** Formula/source/rounding/dedup unit and live integration/regression evidence before checkpoint.
- **Do Not Redo:** Explicit mileage/no-fallback policy or completed Phase 3/4A absent regression evidence; no applied migration edits.

### HOFF-0028 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-03T21:59:00+07:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / Phase 4C completion → 4D
- **Status:** DONE
- **Objective:** Complete explicit per-load/hourly/daily/flat formulas and source snapshots.
- **Inputs Read:** Progress/HOFF-0027, TimeEntry/source/audit, policy/engine/reconciliation architecture, PG interval fixtures.
- **Completed:** WorkPayCalculator and input snapshots; within-calculation load/day dedup; per-period flat source; explicit total_hours. Real PostgreSQL exposed Duration read as BigDecimal; fixed start/end JDBC INTERVAL_SECOND mapping, preserved source data.
- **Requirement IDs:** BE-CALC-014 / Phase 4C.
- **Files and Artifacts:** WorkPayCalculator/DriverPayEngine, TimeEntry mapping, work unit/live tests, settlement contracts, progress/memory.
- **Decisions:** Hourly reads persisted total_hours; no start/end or invoice-payroll inference. Line rounding precedes aggregation for reconciliation. Flat policy must start before/on period start; explicit zero hours valid.
- **Assumptions:** UTC calendar dependency retained from existing audit; no region timezone inferred from jurisdiction.
- **Verification:** Final disposable PG clean full suite on codex_classification_20261003145821 PASS: 123 tests, 0 failures/errors, 1 legacy skip; 13 PG cases. Three work unit cases and real per-load/hourly/daily/flat mapping/dedup/snapshot case PASS. Original failed interval read corrected without weakening tests; no migration.
- **Open Issues and Risks:** 4D–4F eligibility/reconciliation/accessorial attribution/concurrency/correction gates remain incomplete; no full settlement claim.
- **Blockers:** None for Phase 4D.
- **Next Required Action:** Add percentage-pay calculator using explicit eligible reconciled subtotal, ratios/currency, unknown-source guard and tests.
- **Acceptance Gate:** Unit/live/API/regression formulas and immutable input explanation before checkpoint.
- **Do Not Redo:** Completed calculator/source mapping slices absent regression evidence; no applied migration edits.

### HOFF-0029 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-03T22:01:00+07:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / Phase 4D completion → 4E
- **Status:** DONE
- **Objective:** Calculate percentage pay from explicit reconciled eligible revenue.
- **Inputs Read:** Progress/HOFF-0028, invoice eligibility/reconciliation, policy basis/ratio, engine and fixtures.
- **Completed:** PercentagePayCalculator and snapshot inputs; explicit subtotal only, ratio/currency/reconciliation guards, missing-source validation and within-calculation load dedup. No tax/quote numerator or catch-and-skip zero wages.
- **Requirement IDs:** BE-CALC-014 / Phase 4D.
- **Files and Artifacts:** PercentagePayCalculator/DriverPayEngine; percentage unit/live tests; settlement contracts; progress/memory.
- **Decisions:** Missing/noneligible invoice is validation required; subtotal must reconcile. Existing ratio and configured rounding boundaries retained.
- **Assumptions:** No source inferred for linehaul or invoice total; no statutory rates.
- **Verification:** Final clean PG regression codex_classification_20261003150051 PASS: 127 tests, 0 failures/errors, 1 legacy skip; 14 live cases. Three dedicated unit cases and live subtotal/tax exclusion/dedup/snapshot/missing-source case PASS. Initial test lambda compile error corrected before final run; no migration.
- **Open Issues and Risks:** Remaining 4E/4F attribution, eligibility/source reuse, reconciliation/workflow/corrections/concurrency gates open.
- **Blockers:** None for 4E implementation; current charge schema has no explicit driver recipient, so attribution must be proved from historical assignments or rejected as ambiguous.
- **Next Required Action:** Approved driver_pay_amount reader with assignment/occurredAt evidence, currency guards, missing/ambiguous-source validation and tests.
- **Acceptance Gate:** No guessed recipient/customer amount or duplicated driver pay; unit/live/regression evidence.
- **Do Not Redo:** Completed Phase 3/4A–4D absent regression evidence; no migration renumbering.

### HOFF-0030 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-03T22:04:00+07:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / Phase 4E completion → 4F
- **Status:** DONE
- **Objective:** Use eligible approved driver-pay amounts with proven historical recipients.
- **Inputs Read:** Progress/HOFF-0029, charge schema/approval contract, assignment intervals, trip-stop/load attribution and source tests.
- **Completed:** AccessorialDriverPayCalculator and snapshot; approved amount only, half-open historical assignment evidence, missing/ambiguous validation, zero/nonapproved/out-of-period exclusion and charge dedup. No owning subsystem rewrite or schema change.
- **Requirement IDs:** BE-CALC-014 / Phase 4E.
- **Files and Artifacts:** AccessorialDriverPayCalculator/DriverPayEngine; attribution unit/live tests; settlement contracts; progress/memory.
- **Decisions:** No charge-recipient column exists; use a uniquely proven historical assigned driver at occurred_at. Ambiguous/missing input blocks the affected calculation rather than guessing/splitting. Load-only charges resolve only with trip/assignment evidence; no customer amount fallback.
- **Assumptions:** None for missing recipients; no tax rate or current truck/main-driver inference.
- **Verification:** Clean PG regression codex_classification_20261003150336 PASS: 131 tests, 0 failures/errors, 1 legacy skip; 15 PG cases. Three unit and live approved/draft/ambiguous-driver cases PASS; prior cost/accessorial suites retained. No migration.
- **Open Issues and Risks:** Original concurrency, work-date eligibility/source reuse, reconciliation, lock immutability and correction idempotency remain 4F gates.
- **Blockers:** None for 4F; individual charges with missing/ambiguous history raise explicit validation errors.
- **Next Required Action:** Harden/test settlement aggregation and lifecycle; next forward migration for genuine correction/immutability schema requirements, no V10/V11 rewrite.
- **Acceptance Gate:** Unit/live/concurrency/lock mutation/idempotency/reconciliation/migration tests; full regression before Phase 4 COMPLETE.
- **Do Not Redo:** Completed Phase 3/4A–4E absent regression evidence; do not guess charge recipients or rewrite applied migrations.

### HOFF-0031 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-03T22:29:00+07:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / Phase 4F completion → 015B
- **Status:** DONE
- **Objective:** Complete reconciled immutable settlement aggregation and safe cost projection.
- **Inputs Read:** Progress/HOFF-0030, existing V10/V11, source/period lineage, DB/JPA projection/workflow and user MULTI_JURISDICTION payroll steering.
- **Completed:** Historical work-date eligibility, cross-period source guard, reconciliation, validation/review/approval/lock, original/correction concurrency, idempotent input-drift-protected adjustments and one reversal, per-line ACTUAL/APPROVED cost projection. V13 DB history guards and trip-only cost attribution. Phase 4 COMPLETE.
- **Requirement IDs:** BE-CALC-014 / Phase 4F; trip-only profitability invariant dependency.
- **Files and Artifacts:** Engine/reconciliation, settlement APIs/entities/repos/DTOs; V13; line-level shipment-cost and profitability dependency; unit/live tests; contracts/progress/plan/memory.
- **Decisions:** Advances/reimbursements are not operating-cost credits. Multi-load trip costs remain trip-only and suppress load profit until allocation is proved; never implicit proration. Actual completion/assignment end determines business date, not clamping. DB audit timestamps use microseconds matching PostgreSQL. Corrections require finalized parent and separate reviewed child.
- **Assumptions:** No historical daily date backfill, external production migration query or payroll tax rates. Settlement reversal line classes are payment signs; future payroll must trace original economic classes for gross/tax inputs.
- **Verification:** Final clean full PG regression codex_classification_20261003152740 PASS: 141 tests, 0 failures/errors, 1 legacy skip; 21 PG cases. Populated V12→V13 upgrade and 141-test regression codex_classification_20261003150336 PASS. Three reconciliation unit cases, explicit trip-cost availability, six live concurrency/lock/reconciliation/correction/validation cases. Maximum 300-character reversal reason PASS. Existing cost/accessorial expectations preserved; invalid toy locked fixture reconciled to its explicit net invariant.
- **Open Issues and Risks:** Payroll and Phases 6–8 incomplete. Regional statutory policies/adapters require authoritative sources; missing configuration must remain UNAVAILABLE and block finalization. Negative economic payroll recovery requires explicit validation, not clamping or paying negative amounts.
- **Blockers:** None for jurisdiction-neutral framework. Regional legal adapters remain deliberately unconfigured.
- **Next Required Action:** 015B neutral jurisdiction/profile/policy model and ports, immutable inputs/outputs and mapping/reconciliation using V11 plus V14.
- **Acceptance Gate:** Unit/live/concurrency/migration/regression evidence and fail-closed statutory availability before checkpoint.
- **Do Not Redo:** Completed Phase 3/4; do not edit applied V1–V13 or infer legal rates, trip allocation or historical dates.

### HOFF-0032 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-04T02:45:00+07:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / 015B completion → 015C
- **Status:** DONE
- **Objective:** Implement jurisdiction-neutral payroll without inferred statutory rates.
- **Inputs Read:** Progress/HOFF-0031, V11, existing JPA/rounding/snapshot/security conventions and user MULTI_JURISDICTION requirements.
- **Completed:** Generic country/subdivision/locality config and independent classification, effective profile/policy resolver, adapter dispatch ports, immutable versions and snapshots; signed reversal economic gross, reconciliation, unique settlement/supplement mapping; explicit recalculation history and UNAVAILABLE/VALIDATION_REQUIRED gates. Protected configuration/calculation/read APIs. V14 forward migration.
- **Requirement IDs:** BE-CALC-015B and user multi-jurisdiction steering.
- **Files and Artifacts:** Payroll domain/policy/tax ports and services, JPA config/run/item/supplement mappings/repos, protected controllers/DTOs; V14; 12 unit and 8 live cases; contracts/progress/memory.
- **Decisions:** Work/payroll override → effective profile → tenant default; exact jurisdiction policy scope, no implicit country fallback. CONTRACTOR does not imply zero tax. Missing profile/policy/adapter leaves tax/net null and blocks APPROVED/LOCKED/PAYMENT_SCHEDULED. Reversal follows parent economic line signs, not reversed payment class. Unpaired recovery keeps signed components and requires validation. Dispatcher is Primary to select adapters through versioned key. Mapping test now recognizes explicit assigned financial IDs and tenant INTEGER singleton; real JPA metadata/PG validation retained.
- **Assumptions:** No statutory adapter/legal rates are configured in production. Fixed test adapter exists only in TestConfiguration and is not a US/VN rule. effectiveDate and supplements/tax inputs are explicit caller inputs; no historical backfill.
- **Verification:** Final clean codex_classification_20261003194231 and populated V13→V14 codex_classification_20261003152740 full regression PASS: 161 tests, 0 failures/errors, 1 legacy skip; 29 live cases. Twelve neutral unit cases and eight live hierarchy/contractor withholding/JSON snapshots/recalculation/source-idempotency/currency rollback/concurrent versions/API cases. JSONB uses structured equality; immutable-list null validation corrected. No prior cost/accessorial test expectation change.
- **Open Issues and Risks:** 015C–015G and Phases 6–8 incomplete. Regional policies/adapters require authoritative sources. Historical V11 runs without input snapshots cannot be inferred/recalculated. Recovery workflow is explicitly unavailable.
- **Blockers:** None for 015C or other jurisdiction-neutral workflow. Regional statutory adapters remain unconfigured and actual payroll cannot finalize without them.
- **Next Required Action:** Separate 015C review/approve/lock audit/history gate; do not mark payroll or settlement PAID at lock.
- **Acceptance Gate:** Unit/live/idempotency/lock mutation/concurrency/forward migration/full regression before checkpoint.
- **Do Not Redo:** Completed Phase 3/4/015B; no V11/V14 edits, tax-zero bypass or regional legal-rate inference.

### HOFF-0033 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-04T02:49:00+07:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / 015C completion → 015D
- **Status:** DONE
- **Objective:** Complete immutable payroll review/approval/lock without premature paid state.
- **Inputs Read:** Progress/HOFF-0032, existing payroll reconciliation/source claims, V14 and user payment-state rules.
- **Completed:** Separate ordered workflow service/domain gate, actor audit and retry stability; V15 locked header/item/claim/snapshot protection and all-items PAID gate. No tax/region rule added.
- **Requirement IDs:** BE-CALC-015C.
- **Files and Artifacts:** PayrollWorkflow/Service, protected controller/Run review audit, V15, workflow unit/live tests, contracts/progress/memory.
- **Decisions:** Resolve/reconcile before review, approve and lock. State retry preserves first actor/time. Lock does not pay settlements/items. Historical tax policy is the snapshotted version, not re-resolved from current legal policy.
- **Assumptions:** No external provider or actual legal adapter; production missing configuration remains validation-required.
- **Verification:** Clean codex_classification_20261003194644 and populated V14→V15 codex_classification_20261003194231 full regression PASS: 166 tests, 0 failures/errors, 1 legacy skip; 31 PG. Three workflow unit and two live lock concurrency/history/API audit cases, run cannot become PAID while required items incomplete.
- **Open Issues and Risks:** 015D–015G and Phases 6–8 incomplete. Locked payroll has no payslip/payment until their separate sub-tasks.
- **Blockers:** None for 015D neutral implementation; statutory adapters remain unconfigured.
- **Next Required Action:** Immutable payslip issuance/PDF plus authenticated driver-owned read routes from convention plan.
- **Acceptance Gate:** Unit/live/concurrency/idempotency/ownership/history/migration/full regression before checkpoint.
- **Do Not Redo:** Completed Phases 3/4/015B–015C; no applied migration edits or unlock/paid-at-lock behavior.

### HOFF-0034 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-04T02:59:00+07:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / 015D completion → 015E
- **Status:** DONE
- **Objective:** Immutable payslip/PDF issuance with driver ownership.
- **Inputs Read:** Progress/HOFF-0033, V11 payslip/document schema and storage capabilities, employee identity, convention routes and official Apache PDFBox Maven/version docs.
- **Completed:** Lock atomically issues snapshotted payslip and persisted Unicode PDF/checksum/renderer/actor; retry/concurrency preserves original artifact. Protected driver list/detail/PDF routes, cross-driver denial, private PDF response. V16 artifact/source/immutability guards. Corrected proven settlement DTO storage-padding retry drift without changing financial value or prior test expectations.
- **Requirement IDs:** BE-CALC-015D.
- **Files and Artifacts:** Payslip entity/repo/service/PDF renderer/DTO/controller, lock hook/RBAC, V16, font/license, isolated PDFBox POM dependency; unit/live tests/contracts/progress/memory.
- **Decisions:** No object-storage implementation exists; persist artifact atomically in payslip ledger. Historical external references stay immutable and explicit PDF-unavailable is returned if no local artifact exists. Font resource is configurable; unsupported glyphs block issuance instead of substituting names. Display never rounds further and preserves complete IDs across pages. Tax display uses stored rounded item deductions, not adapter raw values.
- **Assumptions:** No external notification/transfer, current tax implementation or PDF provider guessed. DejaVuSans covers tested Vietnamese script; other glyph sets require configured font. Existing unrelated POM changes remain outside this task's commit.
- **Verification:** Final clean codex_classification_20261003195715 and populated V15→V16 codex_classification_20261003194644 full regression PASS: 169 tests, 0 failures/errors, 1 legacy skip; 32 PG cases. Two Unicode/multipage PDF parse unit cases and live issuance/concurrency/immutability/employee-name history/checksum/ownership/API case. Glyph encoding and decimal display defects fixed; genuine prior retry scale regression fixed in DTO instead of weakening tests.
- **Open Issues and Risks:** 015E–015G and Phases 6–8 incomplete. No provider adapter/credential or statutory source configured in production.
- **Blockers:** None for neutral payment scheduling/verified ports/manual reconciliation; actual regional statutory calculation remains unconfigured.
- **Next Required Action:** Separate 015E payment scheduling and provider dispatch; never mark paid merely from lock/schedule/processing.
- **Acceptance Gate:** Unit/live/concurrency/idempotency/monetary-history/migration/full regression before checkpoint.
- **Do Not Redo:** Completed Phase 3/4/015B–015D; no V11–V16 edits, artifact regeneration or country-specific rates.

### HOFF-0035 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-04T03:07:08+07:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / 015E completion → 015F
- **Status:** DONE
- **Objective:** Idempotent payment scheduling and safe provider dispatch.
- **Inputs Read:** Progress/HOFF-0034, V11 payment schema, employee connected-account source, immutable run/item/source/payslip gates.
- **Completed:** Immutable payment attempts/provider/destination snapshots, parent-first scalar lock protocol, one active attempt, source/payment scheduling state, committed PROCESSING intent and configured-provider port. No paid-at-submission or actual external transfer.
- **Requirement IDs:** BE-CALC-015E.
- **Files and Artifacts:** Payment entity/repo/DTO/controller, scheduling/dispatch/provider/instruction services; V17; unit/live tests/contracts/progress/memory.
- **Decisions:** Immutable item net is payout source, never caller totals. Missing adapter keeps SCHEDULED; unknown outcome keeps PROCESSING and prevents new attempts until evidence. Intent commits before I/O. Scalar parent ID before reading item/payment fixes observed stale-context concurrency regression. Zero-net completion requires explicit policy; no fabricated transfer.
- **Assumptions:** Production has no payment provider adapter/credentials; fixed asynchronous fixture sends no network request. No bank account or legal policy inferred.
- **Verification:** Clean codex_classification_20261003200459 and populated V16→V17 codex_classification_20261003195715 full regression PASS: 174 tests, 0 failures/errors, 1 legacy skip; 34 PG cases. Three dispatch ordering/provider/unknown-outcome unit and two live concurrency/attempt/input-drift/history/source-state cases.
- **Open Issues and Risks:** 015F–015G and Phases 6–8 incomplete; actual provider and statutory configurations unavailable. Unknown-outcome dispatch requires verified callback/bank reconciliation; no resend/new-attempt guess.
- **Blockers:** None for verified callback port/core/manual reconciliation. Zero-net no-payment completion needs explicit business policy.
- **Next Required Action:** 015F verified provenance/receipt/idempotency, exact amount/currency identity and all-required-items payment reconciliation.
- **Acceptance Gate:** Unit/live/concurrency/idempotency/history/forward migration/full regression.
- **Do Not Redo:** Completed Phase 3/4/015B–015E; no applied migration changes, tax/provider defaults or prematurely paid states.

### HOFF-0036 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-04T03:20:19+07:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / 015F completion → 015G
- **Status:** DONE
- **Objective:** Verified callback provenance/idempotency and exact financial completion.
- **Inputs Read:** Progress/HOFF-0035, V17 attempts, existing tenant filter/context/routing/active-registry service as dependency only, user payment-state invariants.
- **Completed:** Verifier port and trusted tenant scope, immutable canonical/raw-body/proof event journal; duplicate/drift and exact identity guards, failed retry history, per-item/settlement completion, all-items run completion and late-outcome cases. V18 DB success evidence guards.
- **Requirement IDs:** BE-CALC-015F.
- **Files and Artifacts:** Callback/verifier/tenant scope/outcome/event DTO/controller/entity/repo; source case guards; V18; unit/live tests/contracts/progress/memory.
- **Decisions:** Callback claim is not financial proof. Actual adapters must authenticate provider/merchant and trusted registered tenant before DB binding. No tenant header/default fallback. Receipt stores original verified body/proof and canonical financial identity; duplicate proof timestamps/whitespace do not alter event identity. Late events require explicit reconciliation and block more dispatch; successful history never downgrades.
- **Assumptions:** No production provider/callback verifier or regional statutory adapter configured. Test signature/provider are local fixtures only. Existing tenant isolation implementation was not audited/reimplemented.
- **Verification:** Final clean codex_classification_20261003201635 and populated V17→V18 codex_classification_20261003200305 full regression PASS: 184 tests, 0 failures/errors, 1 legacy skip; 38 PG cases. Six unit identity/late-outcome/tenant-scope cases and four live duplicate concurrency/partial all-items/failure-retry/late-source/input-drift/unsigned/API/DB-evidence cases. Earlier 181-test upgrade also passed before adding tenant scope; final evidence is 184.
- **Open Issues and Risks:** 015G and Phases 6–8 incomplete. Unresolved late/conflicting payment cases await manual explicit evidence. Provider/legal configurations remain unavailable.
- **Blockers:** None for manual bank reconciliation; zero-net no-payment completion still needs explicit policy.
- **Next Required Action:** 015G authorized bank attestation and append-only case resolution; no unlock/successful-payment rewrite or unsupported negative recovery.
- **Acceptance Gate:** Unit/live/idempotency/concurrency/reconciliation/history/forward migration/full regression before neutral Phase 5 core gate.
- **Do Not Redo:** Completed Phase 3/4/015B–015F; no V11–V18 edits, default tenant/tax/provider guesses or paid-at-schedule behavior.

### HOFF-0037 — Backend Developer → Tech Lead

- **Timestamp:** 2026-10-04T08:00:00+07:00
- **From Role:** Backend Developer
- **To Role:** Tech Lead
- **Phase:** 7 — Backend Development / 015G complete; Phase 6 entry-gate assessment
- **Status:** DONE
- **Objective:** Finish audited manual bank reconciliation and assess Phase 5 exit / Phase 6 entry without guessing business policy.
- **Inputs Read:** HOFF-0036; Phase 5 and 6 plan; payroll contracts; current Invoice service/entity/DTO callsites; migration history; GitNexus analysis/impact limitation.
- **Completed:** Added authorized open-case and reconcile-bank APIs, actor/evidence capture, exact payment/case identity validation, case and payment/run locking, idempotent replay and unique bank transaction guard. Late explicit success can settle failed item through resolved case only; successful history cannot downgrade. V19 adds forward-only bank evidence constraints and narrow locked-item transition. Added concurrent duplicate reconciliation integration coverage; adapted open case assertion to shared suite state and corrected stale API JSON wrapper expectation. Phase 6 decision recorded NOT MET with concrete conditions.
- **Requirement IDs:** BE-CALC-015G; Phase 5 exit; Phase 6 entry.
- **Files and Artifacts:** Manual bank request/case DTO/service/controller/repository; PayrollPaymentOutcomeService; V19; unit and CostLedgerPostgresTest; payroll contract; plan/progress and memory.
- **Decisions:** No production provider or statutory policy assumed. Same idempotency key and actor plus same structured request replays; a unique bank transaction reference cannot resolve another case. Manual evidence does not create a transfer. Phase 6 stays closed until predecessor/product contracts are complete. Future schema work starts V20+, never stale V12.
- **Assumptions:** DBs created for verification are disposable local PostgreSQL clones; no production DB was touched. Dirty worktree contains unrelated existing user files and remains preserved.
- **Verification:** Clean PostgreSQL `./mvnw -q clean test` PASS on codex_classification_20261004005739: 188 tests, 0 failures/errors, 1 legacy skip; 39 live PostgreSQL integration methods. Targeted clean DB manual reconciliation PASS. Full clean V1→V19 applied. Clone of populated V18 DB codex_classification_20261003201635 upgraded to disposable codex_classification_20261004005930 and full regression PASS (188, 0 failures/errors, 1 skip). Unit tests include evidence, amount drift and success-history protection; live test covers concurrent same-key replay, audit, authorization and source uniqueness. `git diff --check` still reports one blank line at EOF in pre-existing modified `DriverPayPolicyController.java`; left untouched.
- **Open Issues and Risks:** Phase 5 parent remains incomplete: net-zero no-payment disposition is undecided; Task 5.4 Invoice employee/period/distance/hours fields are annotated deprecated but InvoiceService, InvoiceView/Mapper still read/write them. Production regional adapters/provider and supported tenant migration history are not verified. Phase 6 needs explicit rate/FSC sources/precedence/mileage/currency/rounding/snapshot contracts; existing plan's V12 migration pointer is stale. GitNexus MCP impact query is unavailable due graph DB schema mismatch; CLI index completed and caller scan used, but this is not equivalent to full impact graph.
- **Blockers:** BLK-003 remains OPEN; do not open Phase 6 yet.
- **Next Required Action:** Product Owner specifies zero-net disposition and Phase 6 business inputs; Tech Lead confirms invoice legacy-field retirement scope and corrects plan migration numbering; then Backend Developer closes Task 5.4/Phase 5 gate.
- **Acceptance Gate:** 015G is DONE after clean + populated upgrade suite evidence. Phase 6 entry requires all Phase 5 Task 5.4 and payroll disposition criteria, approved authoritative DOE/index and eligible-mile/MPG/rate contracts, currency and rounding/precedence, immutable quote/invoice snapshot/idempotency rules, and migration plan beginning V20+.
- **Do Not Redo:** Do not modify V11–V18; do not apply changes to source V18 database; do not infer zero-net disposition or FSC/rate sources; preserve unrelated dirty worktree changes.

### HOFF-0038 — Backend Developer → Tech Lead

- **Timestamp:** 2026-10-04T09:25:00+07:00
- **From Role:** Backend Developer
- **To Role:** Tech Lead
- **Phase:** 7 — Backend Development / Phase 5 finalization; Phase 6 gate assessment
- **Status:** DONE
- **Objective:** Implement the approved zero-net payroll disposition and migrate run lifecycle from `PAID` to `COMPLETED`, finish Invoice Task 5.4, and verify before Phase 6.
- **Inputs Read:** HOFF-0037; explicit user decision for run/item/payment semantics; Invoice API compatibility decision; V11–V19 migration chain and current API/service callsites.
- **Completed:** Added V20 with legacy run backfill, completed audit, terminal/item transition guards and no-payment audit constraints; completion/disposition services and endpoint; completion DTO/entity semantics; provider/bank success now completes only all-terminal runs. Zero-net uses item `NO_PAYMENT_REQUIRED`, no payment record and keeps settlement `LOCKED`. Invoice commands reject employee/period/distance legacy inputs and employee filter; mapper ignores writes; old response values remain deprecated read-only compatibility projection. Updated payroll state docs and Phase 6 migration number to V21+.
- **Requirement IDs:** BE-CALC-015 Task 5.2/5.4; Phase 5 exit; Phase 6 entry gate.
- **Files and Artifacts:** V20 migration; payroll run/item entities, DTOs/services/controller; Invoice request/view/service/mapper/repository; payroll and rating docs; plan/progress; unit and PostgreSQL tests.
- **Assumptions:** PostgreSQL test databases and populated V19 clone are disposable local fixtures; no production database was touched. All preexisting unrelated worktree changes belong to the user and remain preserved.
- **Decisions:** Run is `COMPLETED` iff each item is `PAID` or `NO_PAYMENT_REQUIRED`; run completion stores timestamp and source/actor. Zero-net item requires exact zero, actor, reason code/text, no attempts/unresolved case and is idempotent; settlement stays immutable `LOCKED`. Invoice legacy fields remain in API/read projection but all supplied legacy write/filter values reject explicitly. Phase 6 remains design-only pending authoritative rating/FSC contracts.
- **Verification:** `./mvnw -q -DskipTests test-compile` passed. Targeted Invoice/payroll unit tests passed. Clean disposable V1→V20 and populated V19→V20 clone regression passed: each 192 tests, 0 failures/errors, 1 legacy skip; 40 live PostgreSQL methods. Populated clone's 7 preexisting `PAID` runs were backfilled to `COMPLETED/LEGACY_PAID_RUN`. `git diff --check` has one existing unrelated blank line at EOF in modified `DriverPayPolicyController.java`; preserved.
- **Open Issues and Risks:** No authoritative DOE/index, eligible-mile, MPG, currency/rounding, rate precedence, min/max or invoice snapshot/idempotency contracts; Phase 6 remains NOT MET. Production provider/legal adapters and supported production tenant migration history are not verified. GitNexus MCP impact remains unavailable due graph DB schema mismatch; source caller scans were used.
- **Blockers:** BLK-003 remains OPEN for Phase 6 pricing inputs only; Phase 5 domain and Invoice decisions are resolved.
- **Next Required Action:** Present Phase 5 completion and Phase 6 gate; request authoritative pricing/FSC contract decisions. Do not implement Phase 6 until approval.
- **Acceptance Gate:** Clean V1→V20 and populated V19→V20 regression pass; run backfill to `COMPLETED/LEGACY_PAID_RUN`; Task 5.4 behavior tested; Phase 6 remains closed absent all approved pricing/FSC inputs.
- **Do Not Redo:** Do not edit applied V11–V19; do not fake zero payment; do not call PayrollRun `PAID`; preserve unrelated dirty worktree changes and read-only historical Invoice projection.

### HOFF-0039 — Tech Lead → Backend Developer

- **Timestamp:** 2026-10-04T12:20:00+07:00
- **From Role:** Tech Lead
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / Convention Phase 6 preparation → 6A
- **Status:** DONE
- **Objective:** Clean harmless diff warning, confirm V20 checkpoint and record authoritative rating decisions before implementation.
- **Inputs Read:** Continuation plan/progress; current Java, V1–V20, Invoice controllers, tests, ADRs and payroll semantics; user RATE-DEC replies.
- **Completed:** Removed only redundant EOF blank line; documented seven confirmed policies and approved V1 subset; added safe reproducible isolated PostgreSQL regression runner and seven runner tests; corrected stale current-state Phase 5/gate records without rewriting old handoffs.
- **Requirement IDs:** Phase 6 gate / RATE-DEC-001…007.
- **Files and Artifacts:** docs/rating-policy-decisions.md; docs/rating-fsc-contracts.md; plan/progress; scripts/verify_backend_regression.py; scripts/tests/test_verify_backend_regression.py; DriverPayPolicyController.java (whitespace only); memory.
- **Decisions:** DEC-007; all seven confirmed, V1 FLAT/PER_MILE + INDEX_BASED_MPG. No guessed advanced formulas.
- **Assumptions:** ASM-001. Disposable local clone only; source baseline queried read-only.
- **Verification:** git diff --check PASS. Python unittest seven PASS. Populated V20→V20 disposable codex_regression_20261004050344284081 full suite PASS: 192 reported, 191 executed, zero failures/errors, one named legacy skip; 40 PostgreSQL methods. Flyway validation PASS. Evidence /tmp/logisticsx-regression-pf4nji1t/{maven.log,summary.json}. Source still seven LEGACY_PAID_RUN, no PAID runs. No migration added.
- **Open Issues and Risks:** GitNexus impact risk UNKNOWN due graph42/runtime40 mismatch; graph query empty, source inspection confirms no rating implementation; no all-clear graph claim. Qualified planned/actual load mileage missing; approved fail-closed behavior required.
- **Blockers:** None for 6A. Later Phase 7/8 dependencies and policy inputs remain.
- **Next Required Action:** Backend Developer adds V21 schema/domain/version services and new unit/PostgreSQL cases; then 6B resolver.
- **Acceptance Gate:** Clean latest + populated V20 upgrade + validate + complete growing regression; V1–V20 untouched.
- **Do Not Redo:** Convention Phases 0–5 and legacy backfill; never mutate source DB or user edits.

### HOFF-0040 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-04T12:35:00+07:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / Convention 6A → 6B
- **Status:** DONE
- **Objective:** Publish immutable versioned Rating V1 customer contracts/rules without hidden financial defaults.
- **Inputs Read:** HOFF-0039; seven RATE decisions and approved V1 subset; existing entity/service/controller/security conventions; PostgreSQL V1/Flyway baseline.
- **Completed:** Additive V21 identity/version tables, finite decimal/method/date/FSC/context constraints and immutable history triggers; JDBC immutable records preserve exact decimals; authoring service serializes append with expected version; authenticated ADMIN/ACCOUNTANT API with tenant employee audit. No fake engine/calculation or speculative advanced formula.
- **Requirement IDs:** Phase 6A / RATE-DEC-001/002/003/004/005.
- **Files and Artifacts:** V21; service/rating domain/validator/service; RatePolicyRepository; dto/rating; RatePolicyController; narrow new SecurityConfig route guard; seven unit tests; four RatingPolicyPostgresTest methods; domain contract/plan/progress/memory.
- **Decisions:** DEC-007. Published versions immutable immediately; overlapping versions are not silently superseded or closed; later resolver must expose winning ambiguity. Pricing method scope exactly user-approved.
- **Assumptions:** No provider observations have been fetched. Exact EIA series adapter belongs to 6D; no financial sample defaults.
- **Verification:** Unit seven PASS. Clean V1→V21 codex_regression_20261004053017140739 and populated V20→V21 codex_regression_20261004053108159552 complete regression/validate PASS: each 203 reported, 202 executed, zero failures/errors, one legacy skip; 44 PostgreSQL methods. Evidence /tmp/logisticsx-regression-b1_hwkmp and /tmp/logisticsx-regression-z7n7seuj. V1–V20 SHA256 unchanged; git diff --check PASS.
- **Open Issues and Risks:** Graph risk UNKNOWN for RateRule/SecurityConfig due graph storage mismatch; graph-first attempts recorded, source scans and full security/application regression cover relevant routes. Source Load TIMESTAMPTZ does not preserve original local-date offset; doc corrected earlier proposed extraction.
- **Blockers:** None for pure 6B resolver. Load adapter separate DATE/no historical guess proposal awaiting business confirmation.
- **Next Required Action:** Add/test deterministic explicit LocalDate resolver; continue provenance after date adapter decision.
- **Acceptance Gate:** 6B effective/date/dimension/priority/tie/currency tests plus growing full PostgreSQL regression.
- **Do Not Redo:** Convention 0–5, applied V1–V21 or source backfills; preserve append-only old HOFF blocks.

### HOFF-0041 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-04T12:40:00+07:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / Convention 6B → 6C
- **Status:** DONE
- **Objective:** Deterministic rule resolution with no implicit precedence or historical business-date guess.
- **Inputs Read:** HOFF-0040; RATE-DEC-001/002; V21 immutable domain/repository; runtime Load/TripStop timestamp and attribution fields.
- **Completed:** Read-only LocalDate/customer/context resolver: null wildcard/exact dimensions, inclusive effective periods, smallest explicit priority, winning tie error without specificity/newest/order tie-break; compatible currency and pinned effective contract validation. No public arbitrary-date pricing API or authoritative Load adapter.
- **Requirement IDs:** Phase 6B / RATE-DEC-001.
- **Files and Artifacts:** RateMatchContext; RateRuleResolver; 14 unit cases; two additional RatingPolicyPostgresTest methods; domain/decision contracts, plan/progress/memory.
- **Decisions:** DEC-007. Currency mismatch rejects the winner rather than using a hidden lower-priority fallback; contract identity/version must be proven. No clock fallback. All seven RATE decisions remain confirmed.
- **Assumptions:** LocalDate source adapter not inferred from persisted TIMESTAMPTZ; proposal explicitly awaiting user confirmation.
- **Verification:** Latest clean V1→V21 codex_regression_20261004053802305662 and populated V20→V21 codex_regression_20261004053624414292 full regression + Flyway validate PASS: each 219 reported, 218 executed, zero failures/errors, exactly one named legacy skip, 46 executed PostgreSQL methods. Evidence /tmp/logisticsx-regression-fsawox9p and /tmp/logisticsx-regression-mm1jcc4q. Seven Python gate tests PASS. git diff --check PASS. V1–V20 hashes unchanged; prior 38 handoffs unchanged.
- **Open Issues and Risks:** Phase 6 not complete; 6C–6G and phases 7–8 remain. Graph storage mismatch keeps impact UNKNOWN; targeted source scans and full real-PG tests used, no false graph all-clear.
- **Blockers:** BLK-004 only blocks Load date adapter. Qualified load-level planned/actual source absent; 6C must preserve unavailable/attribution-required outcomes. No blocker for independent contract provenance preparation.
- **Next Required Action:** 6C contract mileage evidence/resolver and per-component provenance; confirm separate DATE capture before changing Load API/persistence. Then 6D→6G in approved V1 scope.
- **Acceptance Gate:** Growing regression and live PostgreSQL source/precision/attribution/history tests; any new V22+ schema proves clean/previous-latest upgrade/validate.
- **Do Not Redo:** Applied V1–V21, convention Phases 0–5, 6A/6B or historical legacy backfills without concrete regression evidence.

### HOFF-0042 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-04T13:05:00+07:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / Convention 6C date adapter → mileage provenance
- **Status:** DONE
- **Objective:** Implement user-confirmed independent Load pickup LocalDate without fabricating history.
- **Inputs Read:** Plan/progress/HOFF-0041; latest explicit date decision; Load DTO/entity/mapper/controller/service callsites; V21 and existing date/tenant/audit conventions.
- **Completed:** V22 nullable DATE/current-audit pointer, immutable chained corrections and SQL guard; explicit create/update date command input with authenticated actor/reason/provenance; timestamp-only edits retain business date; audited historical remediation with stale guard; exact rating pricingDate/source input, no timezone/clock/instant fallback. Preserved legacy timestamp names for compatibility; equivalent new business field is requestedPickupBusinessDate.
- **Requirement IDs:** RATE-DEC-001/002 date provenance / explicit user date decision.
- **Files and Artifacts:** V22; Load entity/DTO/view/mapper/service; date provenance/remediation DTO/service/controller; narrow security matcher; LoadRatingContextService and RatingPricingDate; seven PostgreSQL/two unit date cases; docs/load-pickup-date-and-rating-mileage.md; progress/memory.
- **Decisions:** DEC-008. No timezone-derived path implemented: only explicit LocalDate. Null/omitted date does not clear/recompute existing promise.
- **Assumptions:** None for business date; captured input is not an accepted financial snapshot.
- **Verification:** Clean V1→V22 codex_regression_20261004060002540892 and populated V21→V22 codex_regression_20261004060134144460 full regression/validate PASS: 228 reported, 227 executed, zero failures/errors, one legacy skip; 53 live PostgreSQL methods. Logs /tmp/logisticsx-regression-yplgkruq and /tmp/logisticsx-regression-4c2bcbsb. Before upgrade, a separate V21 staging clone was seeded with a TIMESTAMPTZ offset; after upgrade that historical Load has appointment present, business date/audit NULL. Staging source remains V21.
- **Open Issues and Risks:** GitNexus graph version mismatch keeps impact UNKNOWN; attempted graph-first impact for affected Load/mapper/service/security symbols, checked concrete callsites and full regression. Exact accepted snapshot date persistence/testing belongs to 6F and remains unimplemented, not skipped or faked.
- **Blockers:** BLK-004 resolved. Planned/actual Load mileage sources remain unqualified and must fail closed.
- **Next Required Action:** Add V23 immutable effective-contract Load mileage evidence and explicit per-component resolver/provenance; validate decimal/source/attribution and clean/V22 upgrade regression.
- **Acceptance Gate:** 6C unit/real-PG provenance, unavailable/negative/zero/precision/multi-load cases; preserve all current tests and old migration checksums.
- **Do Not Redo:** V1–V22 or historical date backfill; do not change pickup date when appointment/actual instant changes.

### HOFF-0043 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-04T13:13:42+07:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / Convention 6C → 6D
- **Status:** DONE
- **Objective:** Explicit per-component Load mileage provenance without inventing attribution or losing decimal precision.
- **Inputs Read:** HOFF-0042; RATE-DEC-002 and approved method scope; immutable V21 contracts/rules, V22 audited business date; Trip/TripStop schema/source and existing test conventions.
- **Completed:** V23 immutable Load/component/effective-contract mileage evidence, original value/unit + normalized NUMERIC(12,3), provenance/actor/time/context guards. Explicit component evidence selection and basis matching, currency/customer/contract/version/effective date validation; monetary minimum NOT_APPLICABLE, zero contract miles valid. No planned/actual/Trip/legacy allocation fallback; missing qualified source errors and multi-load attribution-required. Authenticated ADMIN/ACCOUNTANT capture API derives actor from session. Added eight unit and five PG cases.
- **Requirement IDs:** Convention 6C / RATE-DEC-002/005.
- **Files and Artifacts:** V23; ContractMileage DTO/validator/evidence; RatingMileageComponent/ResolvedRatingMileage; RatingMileageRepository/Service/Controller; narrow route guard; unit/PG tests; date/mileage and decision/domain contracts; plan/progress/memory.
- **Decisions:** DEC-007/008. V1 accepts explicit MILE; no guessed unit conversion. Original values must be exactly representable in existing distance precision; no pre-tier rounding. Explicit agreement evidence remains available even for a multi-load Trip because it is already Load-attributed.
- **Assumptions:** Planned/actual route/movement-leg authoritative Load sources are absent; availability errors are actual supported outcomes, not stub zero results. No financial snapshot is written by GET/preview or evidence resolution.
- **Verification:** Clean V1→V23 codex_regression_20261004060841260687 (/tmp/logisticsx-regression-lp8ksqdo), populated V22→V23 codex_regression_20261004060931218651 (/tmp/logisticsx-regression-xh8p0yla), full suite + Flyway validate PASS: each 241 reported / 240 executed / zero failures/errors / one named legacy skip. 58 PG methods (40 baseline + 6 rule + 7 date + 5 mileage), none skipped. Seven Python verifier tests PASS; git diff --check clean; V1–V21 hashes unchanged. V22 source remains unchanged and historical seeded Load business DATE stays NULL.
- **Open Issues and Risks:** Source-qualified planned/actual mileage ingestion is not implemented; no production fallback. Phase 6D–6G and Phase 7/8 remain. Exact accepted snapshot pricingDate/source persistence/test belongs to 6F, not the immutable input record tested here. GitNexus graph version mismatch keeps impact UNKNOWN; graph-first checks/source scan/full real-PG regression recorded.
- **Blockers:** None for approved 6D implementation. Historical NULL date/absent qualified mileage remains fail-closed until explicit proven input.
- **Next Required Action:** 6D EIA supported region/series provider and selection/retrieval/version audit; index stale/unavailable, explicit MPG/base and approved FSC rounding; then 6E explainable engine, 6F immutable snapshots, 6G invoice integration.
- **Acceptance Gate:** Preserve current growing regression/PG/skip budget; any V24+ clean/previous-latest upgrade/validate. No magic prices/MPG or unlimited last-known index fallback.
- **Do Not Redo:** Applied V1–V23, completed convention 0–5/6A–6C, historical legacy backfill/date guesses. Old handoffs remain append-only.

### HOFF-0044 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-04T13:35:00+07:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / Convention 6D → 6E
- **Status:** DONE
- **Objective:** Approved FSC calculation and authoritative EIA adapter without financial defaults.
- **Inputs Read:** Full memory, continuation plan/progress, RATE decisions, V21 FSC contract and existing services; official EIA ULSD history links/API documentation.
- **Completed:** EIA verified supported-region ULSD series, weekly nonfuture retrieval, provider data hash/version/time audit, sanitized unavailable errors; selector latest/unique/age gate; explicit MPG/base/mileage calculator, DECIMAL128 and unit-6-before-total rounding; CurrencyScaleProvider and min/max boundary. Eleven unit/local HTTP tests.
- **Requirement IDs:** Convention 6D / RATE-DEC-002…005.
- **Files and Artifacts:** service/rating provider/selector/calculator/rounding/domain; two test classes; docs/rating-fsc-v1.md; plan/progress/memory.
- **Decisions:** DEC-007. Official series mapping is technical qualification, not a fallback policy. No observations or financial snapshots written on preview.
- **Assumptions:** No actual EIA credential deployment verified; absence fails closed. Existing phase rounding is unchanged.
- **Verification:** Clean codex_regression_20261004063251074099, /tmp/logisticsx-regression-3rl3vk6a full suite/Flyway validate PASS: 252 reported / 251 executed / zero failures/errors / one named legacy skip; 58 live PG methods. No new migration. Applied V1–V23 untouched.
- **Open Issues and Risks:** Graph impact UNKNOWN (storage version mismatch), source checks and growing regression used. Phase 6E–6G and 7–8 remain. Asked structured Phase 7/HOS/scoring, Phase 8 lifecycle/utilization and explicit billing-tax-source decisions; no choices assumed.
- **Blockers:** None for 6E/6F. Tax assessment/generation policy and Phase 7/8 business answers pending.
- **Next Required Action:** 6E explainable immutable value output and ephemeral real Load preview; then persisted immutable acceptance in 6F.
- **Acceptance Gate:** New calculation/preview/source/auth/readonly tests plus full growing PostgreSQL suite; no GET snapshots/hidden policy.
- **Do Not Redo:** V1–V23 and complete 0–5/6A–6D, legacy backfills and old handoffs. No fake index/default MPG or inferred history.

### HOFF-0045 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-04T13:41:16+07:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / Convention 6E → 6F
- **Status:** DONE
- **Objective:** Explainable Rating V1 from authoritative Load/rule/component sources.
- **Inputs Read:** HOFF-0044; RATE decisions; Load/Accessorial and rating repositories/resolver/source contracts; security routes and tax-source gap.
- **Completed:** Explicit context provenance, server Load customer/date, independent component evidence, approved customer-charge selection and no double FSC; pure explainable engine; short read transaction before provider I/O; accounting-only ephemeral preview and structured existing SLF4J logs. Four unit/three PG methods.
- **Requirement IDs:** Convention 6E / RATE-DEC-001…005.
- **Files and Artifacts:** RatingPreviewRequest; RatingInputs/AccessorialInput/Line/Preview; RatingInputLoader/Engine/PreviewService/Controller; engine/PG tests; docs/rating-engine-v1.md; plan/progress/memory.
- **Decisions:** DEC-007/008. Request dimensions are explicit, no guessed Load lane/equipment; date/customer cannot be caller overrides. Tax excluded by separate policy, not fabricated zero.
- **Assumptions:** None for monetary sources; no financial acceptance yet.
- **Verification:** Clean codex_regression_20261004064011710033, /tmp/logisticsx-regression-imgezs39 full suite/Flyway validate PASS: 259 reported / 258 executed / zero failures/errors / one legacy skip; 61 live PG methods. Incorrect new test multiplication expectation independently checked as exact integer arithmetic and corrected; no baseline weakened. No new migration.
- **Open Issues and Risks:** Graph impact UNKNOWN, source scans/full real-PG regression used. 6F/6G and 7/8 remain. Tax assessment and later policies awaiting user answer.
- **Blockers:** None for 6F.
- **Next Required Action:** V24 accepted immutable snapshots, exact date/source persistence, idempotency and append-only correction/concurrency tests; keep external HTTP outside transaction.
- **Acceptance Gate:** Clean latest/populated V23 upgrade/validate/growing suite; accepted inputs stay unchanged after Load/rule/provider revisions.
- **Do Not Redo:** V1–V23/completed 0–5/6A–6E/old handoffs; no tax default, guessed miles or financial snapshot on preview.

### HOFF-0046 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-04T14:01:00+07:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / Convention 6F → 6G decision gate
- **Status:** DONE
- **Objective:** Real immutable accepted rating and exact business-date source persistence, not generic snapshot scaffolding.
- **Inputs Read:** HOFF-0045; confirmed RATE/date semantics; existing security/JDBC/JSONB/actor conventions and immutable policy source; focused invoice consumers/HOS/fleet policy gaps, no Phase 0–5 re-audit.
- **Completed:** V24 accepted financial snapshot scalar/JSONB/date/source/rule/result/reconciliation/history guards; exact currencyScale and all provider/component/audit inputs; canonical preview/command fingerprints, early replay/conflict, stale acceptance, short locked input/publication revalidation transaction, append-only coded corrections. Two unit and six PG/API methods. Added narrow snapshot read route guard after real 403 test exposed method-security annotation was insufficient.
- **Requirement IDs:** Convention 6F / RATE-DEC-005/006/007 retry safety / exact user pricingDate snapshot test.
- **Files and Artifacts:** V24; RatingFingerprintService/SnapshotService/Writer/Repository; AcceptedRatingSnapshot/AcceptRequest/Preview fields; RatingController/SecurityConfig; fingerprint/PG tests; docs/rating-accepted-snapshots.md and remaining-business-decision-gates.md; plan/progress/memory.
- **Decisions:** DEC-007/008. Replay retains existing accepted outcome after current Load/rule/provider changes. No inferred root uniqueness for ratings; invoice business key is distinct. Tax remains excluded pending explicit assessment. Existing published migrations/history untouched.
- **Assumptions:** None for additional billing choices. Live EIA credential deployment remains unverified; test observation provider exists only in the test context.
- **Verification:** Final clean codex_regression_20261004065734077168 (/tmp/logisticsx-regression-kp09gd53) and populated V23→V24 clone codex_regression_20261004065914986712 (/tmp/logisticsx-regression-pefyz2ee), full suite/Flyway validate PASS: each 267 reported / 266 executed / zero failures/errors / one named legacy skip; 67 PG methods. Seven Python verifier tests PASS. git diff --check and touched untracked source whitespace clean. V1–V23 SHA-256 unchanged; V24 unchanged since first migration gate. Missing route guard test fixed, not skipped; all final gates rerun.
- **Open Issues and Risks:** Graph impact UNKNOWN due storage mismatch; pre-edit graph attempts, concrete source checks and full security/PG regression used. 6G/7/8/final plan gates remain. Multiple invoices would invalidate Optional one-invoice financial/payroll source consumers; no constraint drop or guessed sign/wage recalculation. Original dirty worktree preserved.
- **Blockers:** BLK-005 additional invoice tax/sign/composition/driver-revenue-source answers; BLK-006 scoring/full HOS sources; BLK-007 historical utilization policy. Unsupported fleet health may remain UNAVAILABLE and is not an extra blocker.
- **Next Required Action:** Receive structured business answers, implement 6G end-to-end and migration/concurrency/full regression, mark Phase 6 COMPLETE only then; proceed 7 then 8/final verification. No useful independent production work remains without encoding those financial/source choices; no unused speculative schema/interfaces added.
- **Acceptance Gate:** Next V25+ clean/current-latest upgrade/validate; generated invoice uses accepted snapshot, not current rate, has real approved tax and deterministic business/request invariants; later phases need approved policies.
- **Do Not Redo:** V1–V24, complete convention 0–5/6A–6F, historical legacy/date backfill or old handoffs. Do not fabricate zero tax, duplicate base freight, legal HOS limits, weights or utilization history.

### HOFF-0047 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-04T18:28:00+07:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / Convention 6G.A → billing commands
- **Status:** DONE
- **Objective:** Record locked continuation decisions and implement audited immutable tax evidence independently of invoice calculation.
- **Inputs Read:** Full workflow memory/skill/playbook, current plan/progress/rating docs/ADRs, Invoice/DriverPayPolicy/settlement consumers, V1/V10/V13/V24, controllers and existing PG tests; latest user locked decisions and BILL-DEC-004 reply.
- **Completed:** Locked BILL-DEC-001…004 without reopening RATE policies; V25 immutable assessment, explicit source/reference/jurisdiction/value/currency/assessor audit plus authenticated Accounting capture audit, safe same-ID replay/conflict, exact currency precision, tenant-owned references and protected API. No tax engine/rates/default zero, historical backfill or generated invoice claimed.
- **Requirement IDs:** 6G TaxAssessment; BILL-DEC-001/004; migration and regression governance.
- **Files and Artifacts:** invoice-rating-v1-contract/remaining gates/rating decisions/plan/progress; TaxAssessment DTO/domain/repository/service/controller; narrow SecurityConfig guard; V25; four unit/four PG methods; memory.
- **Decisions:** User confirmed tax external/accounting source, configurable driver revenue, signed economic documents, and Accounting REQUIRED/NOT_REQUIRED audit. Existing V24 runtime wins stale V21 checkpoint. New BILL-DEC-005 question concerns full reversal represented by multiple partial credit documents only; no answer inferred.
- **Assumptions:** None for tax requirement, legal rates, financial sign or history. SourceType provider label is never unauthenticated ingestion; Accounting-authenticated capture attests supplied assessment provenance. No live external integration claimed.
- **Verification:** Baseline populated V24 clone codex_regression_20261004111750511153 PASS: 267 reported/266 executed/one legacy skip, 67 PG. Four unit tests PASS. Clean V1→V25 codex_regression_20261004112441009879 (/tmp/logisticsx-regression-243ws1x1) and populated V24→V25 codex_regression_20261004112612125787 (/tmp/logisticsx-regression-qswxpyzf) full regression + Flyway validate PASS: each 275 reported/274 executed/zero failures/errors/one named legacy skip; 71 real PG methods. Seven Python verifier tests PASS. V1–V24 SHA256 unchanged; git diff --check clean.
- **Open Issues and Risks:** Graph-first queries/impact still UNKNOWN due storage42/runtime40 mismatch; concrete caller/source scans and full PG/security regression used, no graph all-clear. Remaining 6G financial commands/consumers/history gate and later phases not complete.
- **Blockers:** No blocker to independent billing implementation. BILL-DEC-005 exact multi-partial-credit rebill proof is awaiting answer. BLK-006/007 remain future optimization/fleet gates.
- **Next Required Action:** Implement dedicated accepted-snapshot invoice generation, tax-decision audit, business/idempotency uniqueness, signed chain corrections and consistent revenue/driver source handling; do not start Phase 7 before complete Phase 6 gates.
- **Acceptance Gate:** Growing unit/real PG/concurrency/security matrix, clean V1→V26+, populated V25→latest, validate, immutable earlier checksums and diff check; full 6G matrix before Phase 6 COMPLETE.
- **Do Not Redo:** Applied V1–V25, completed convention 0–5/6A–6F, source backfills or old handoffs; no defaults from isVatExempt.

### HOFF-0048 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-04T18:44:00+07:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / Convention 6G.B → signed chain
- **Status:** DONE
- **Objective:** Real accepted-snapshot PRIMARY generation, audited tax requirement/allocations and protected financial command history.
- **Inputs Read:** HOFF-0047; locked BILL/RATE decisions; invoice/JPA/mapper/CRUD/revenue/payment/driver consumers, V1/V25, actual clean migration diagnostics and PG tests.
- **Completed:** V26 exact NUMERIC invoice amounts, accepted snapshot/context FK, purpose/sign/business uniqueness, tax decision/assessment and immutable operation/key/hash command outcomes. Dedicated accounting-only generation/issue/regenerate/read routes; stale draft reference guard, replay retains existing outcome after issue/current Load/rule changes, tax allocations reconcile without invented tax rates. Generic CRUD cannot mutate/delete rated or issued financial history. Seven live PG methods.
- **Requirement IDs:** 6G PRIMARY/TaxAssessment/idempotency/draft regeneration/issued immutability/currency/concurrency/auth.
- **Files and Artifacts:** V26, BillingRepository/Service/Controller/domain, GenerateInvoiceRequest, invoice numeric/sign fields and InvoiceService edit gate, narrow route security, BillingPrimaryPostgresTest; invoice contract/progress/memory.
- **Decisions:** BILL-DEC-004 Accounting explicit REQUIRED/NOT_REQUIRED. BILL-DEC-005 newly CONFIRMED: multiple issued partial credits with explicit IDs can prove exact full subtotal/tax reversal before rebill; prior pending question in HOFF-0047 superseded.
- **Assumptions:** None for tax/legal rates/financial identity. Existing single-load index deliberately retained until all signed multiplicity consumers are implemented in next slice; not a complete 6G claim.
- **Verification:** Clean codex_regression_20261004114029043703 (/tmp/logisticsx-regression-hrcwjrui) and populated V25→V26 codex_regression_20261004114225857415 (/tmp/logisticsx-regression-_c5li4sk), full regression + Flyway validate PASS: 282 reported/281 executed/zero failures/errors/one legacy skip, 78 real PG methods. Initial real schema run exposed SMALLINT/Integer mapping, then JDBC parameter-count defect; Java fixes and all gates rerun, no migrations rewritten or baseline tests weakened. V1–V25 SHA256 unchanged; V26 unchanged after its first application.
- **Open Issues and Risks:** Impact graph UNKNOWN due graph42/runtime40 mismatch; attempted all financial/payroll symbols and concrete callers checked. Signed corrections, multiplicity/financial consumers/driver correction and later phases still incomplete.
- **Blockers:** None for remaining 6G: all BILL-DEC-001…005 locked. Future OPT/FLEET gates remain; do not begin them before dependencies complete.
- **Next Required Action:** V27+ signed billing chains, exact credit caps/evidence and incremental charge claims; adapt one-invoice consumers consistently, preserve legacy history and run all migration/PG/regression gates. Then append-only driver revenue corrections.
- **Acceptance Gate:** Full required 6G matrix including supplementary/partial/full/overcredit/rebill/net revenue/driver corrections, concurrency and tenant isolation; clean latest/previous-latest upgrade/validate/diff/checksums.
- **Do Not Redo:** V1–V26, previous financial history or completed 0–5/6A–6F. Do not re-rate invoice or mutate issued history; original regenerate command may replay safely after issuance.

### HOFF-0049 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-04T19:07:00+07:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / Convention 6G.C → driver consistency
- **Status:** DONE
- **Objective:** Explicit signed supplemental/credit/rebill chain with financial consumers and database caps.
- **Inputs Read:** HOFF-0048; locked BILL decisions including new confirmed multi-credit evidence; billing/revenue/payroll callers, V13/V26, PostgreSQL tests and source dependencies.
- **Completed:** V27 multiplicity with no legacy classification, positive-face credit caps, explicit immutable multi-credit rebill evidence, new accepted snapshot full replacement, incremental approved-charge claims, issued-line move protection. Financial reports and driver percentage calculations use explicit economic signs and eligible runtime statuses. Four new unit and six PostgreSQL cases.
- **Requirement IDs:** 6G supplemental/credit/rebill/billing-chain revenue/versioned driver basis/concurrency/history.
- **Files and Artifacts:** BillingCorrectionRequests, BillingService/Repository/Controller, V27, invoice mapping/view/repository, revenue/profit/balance calculators, PercentagePayCalculator/DriverPayEngine/PolicyService, unit/PG tests and domain/progress docs.
- **Decisions:** BILL-DEC-005 permits multiple issued partial credit evidence IDs. BILL-DEC-006 newly confirmed A: reject stale revenue at approve/lock, explicit recalculation and repeated approval required.
- **Assumptions:** None for financial sign, tax, historical identity or pay basis. Explicit legacy INVOICE_SUBTOTAL compatibility retained, never used as a hidden default.
- **Verification:** Clean codex_regression_20261004120335988095 (/tmp/logisticsx-regression-2y0o8fiz) and populated V26→V27 codex_regression_20261004120507136354 (/tmp/logisticsx-regression-mdlydb65), full regression/Flyway validate PASS: 292 reported/291 executed/zero failures/errors/one legacy skip, 84 PG methods. V1–V26 unchanged; V27 frozen after first application; diff check clean.
- **Open Issues and Risks:** Graph impact remains UNKNOWN (storage42/runtime40); concrete source fallback and PG/security regression used. Driver consistency/corrections and later phases remain incomplete.
- **Blockers:** None for 6G.D; BILL-DEC-001…006 confirmed. Future OPT/FLEET gates remain.
- **Next Required Action:** Implement audited pre-lock drift/recalculation and automatic or explicit retry-safe post-lock driver revenue adjustments under original policy/work-date; never mutate parent payroll/history. Complete Phase 6 gate before Phase 7.
- **Acceptance Gate:** Driver bases/work-date/stale/reapproval/post-lock/concurrency/history plus all billing tests; clean V28+/previous-latest upgrade/validate/checksums/diff.
- **Do Not Redo:** V1–V27 or completed 0–5/6A–6F; no legacy purpose/date backfill or issued invoice re-rating.

### HOFF-0050 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-04T19:37:00+07:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / Convention Phase 6 COMPLETE → Phase 7 policy audit
- **Status:** DONE
- **Objective:** Complete driver revenue consistency and full Phase 6 gates, automatically continue to optimization policy audit.
- **Inputs Read:** HOFF-0049, BILL-DEC-006 user confirmation, driver engine/snapshots/policy/work-date/correction consumers, V13/V15/V27, physical PG tests/security/controller/exception contracts and regression diagnostics.
- **Completed:** V28 immutable revenue commands/evidence; approve/lock drift guard; explicit audited recalculation resets review/approval and appends snapshots. Issue atomically applies post-lock source under frozen earning-date policy, appends reviewed-later adjustment or legitimate no-pay-impact audit, never fake payment. Aggregate rounding, replay/source dedup and sorted lock order. V29 driver snapshot immutability; deferred-method domain error; real two-database PostgreSQL financial isolation. Phase 6 COMPLETE under locked V1 scope; Phase 7 audit begins without permission request.
- **Requirement IDs:** 6G driver basis/work-date/stale/reapproval/post-lock/locked payroll/payslip/concurrency/tenant/rounding/error contracts; Phase 6 final gates.
- **Files and Artifacts:** SettlementRevenueGuard/Service, DriverPayEngine/Controller, BillingService issue hook, V28/V29, GlobalExceptionHandler/RatingMethod, BillingPrimaryPostgresTest (23 methods), settlement-billing-revenue-consistency and plan/progress/decision docs.
- **Decisions:** BILL-DEC-006 A CONFIRMED/LOCKED: reject stale before lock; explicit recalculate and reapprove. All RATE-DEC-001…007/BILL-DEC-001…006 remain locked. No optimization/fleet choices inferred.
- **Assumptions:** None for compensation, tax, history or mileage. Driver source date comes from existing evidenced earning line; no invoice/current-date policy fallback. No external provider/payment call or unsupported new outbox framework; issue side effects are local atomic DB facts.
- **Verification:** Final-source clean codex_regression_20261004123217069452 (/tmp/logisticsx-regression-qbea0dmf), populated V27→V29 codex_regression_20261004123004873068 (/tmp/logisticsx-regression-joiuq31o), previous-latest V28→V29 codex_regression_20261004123506439284 (/tmp/logisticsx-regression-feim5x6x), full regression/Flyway validate PASS: each 302 reported/301 executed/zero failures/errors/one named legacy skip; 94 PG methods, 207 executed non-PG unit/API cases. Seven Python verifier tests PASS. V1–V29 frozen checksums unchanged; diff clean. Initial new fixture mistakes corrected against source, not by skipping or rewriting migrations/baseline assertions.
- **Open Issues and Risks:** Graph impact UNKNOWN due storage42/runtime40, concrete source fallback and full PG/security regressions used. Production configuration/rollout is not claimed. Exact OPT normalization/weights/ties/full HOS/source policy and future FLEET history semantics still require source/business confirmation.
- **Blockers:** No Phase 6 blocker. Phase 7 audit must determine exact unresolved OPT choices; Phase 8 dependency remains.
- **Next Required Action:** Read actual Phase 7 plan/source/approved docs, record evidence and ask one grouped new OPT batch only where correctness depends on unspecified semantics. Continue any useful assumption-free preparation; do not add unused speculative engines/schema to claim completion.
- **Acceptance Gate:** Confirmed Phase 7 semantics then its full feasibility/scoring/stale/concurrency tests; preserve completed Phase 6 and immutable V1–V29.
- **Do Not Redo:** Completed 0–6 or locked RATE/BILL decisions; source backfills, old handoffs or applied SQL. Do not declare PLAN COMPLETE before 7–8/final gates.

### HOFF-0051 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-04T19:40:00+07:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / Convention Phase 7 policy/source gate
- **Status:** BLOCKED
- **Objective:** Begin Phase 7 automatically after Phase 6; verify actual confirmed policy/source inputs without financial or compliance guesses.
- **Inputs Read:** HOFF-0050, Phase 7 plan, existing decision gates, full legacy AI-dispatch doc, Java HOS status/log/violation, Truck/Load/Employee/assignments, actual Java service/controller/provider inventory and profitability contract.
- **Completed:** Phase 5+6 dependency PASS. Source inventory proves absence of full Java HosFeasibilityService/routing/availability providers, incomplete capacity/weight/equipment qualification, no approved normalization/weights/tie/action. Existing actual-profitability report is not an expected dispatch margin source. Created exact OPT-DEC-001…003 decision register and sent one structured batch with options/evidence/recommendation/impact; no RATE/BILL questions repeated.
- **Requirement IDs:** Phase 7 policy audit and no-guessed-policy gate; optimizer hard feasibility/prospective economics/ranking/acceptance semantics.
- **Files and Artifacts:** docs/optimization-policy-decisions.md, remaining-business-decision-gates.md, plan/progress/memory. No new production class/table/score or fake candidate.
- **Decisions:** Architectural hard-feasibility/utility-range/weights-sum/complete audit/atomic idempotent stale acceptance remain locked. Exact OPT source/curves/weights/precision/ties/domain action are BLOCKED, not defaulted. Legacy C# AI documentation is not Java runtime authority.
- **Assumptions:** None for HOS legal rules, source units/freshness, expected margin, objective weights or accepted lifecycle action. No unused speculative abstraction/schema added to imply completion.
- **Verification:** Read-only source/doc audit; prior final Phase 6 gates remain 302 reported/301 executed/zero failures/errors/one legacy skip, 94 real PG methods. No Java/SQL change after those gates; diff/memory validation required at handoff. Graph-first query returned no processes; actual source scan used, no graph all-clear.
- **Open Issues and Risks:** Capacity unit/Load cargo source, full HOS/source freshness, expected cost coverage, normalization/weights and tie/acceptance lifecycle must be approved. Phase 8 depends on completed Phase 7 and later fleet semantics.
- **Blockers:** New OPT-DEC-001…003 business batch pending. No unresolved Rating V1 or Billing decision. Meaningful assumption-free policy inventory is complete; production schema/engine would encode unresolved choices.
- **Next Required Action:** On answers, lock only confirmed exact contracts and continue 7A onward with full PG/concurrency/migration gates. Do not request permission merely to continue or reopen completed phases.
- **Acceptance Gate:** Confirmed source/normalization/weights/tie/action, real feasibility/scoring/persistence/acceptance tests, then Phase 8 and final plan gates.
- **Do Not Redo:** Completed 0–6, RATE/BILL decisions, V1–V29/backfills/old handoffs. No authoritative HOS calculation from remaining minutes or fake expected margin from actual zero.

### HOFF-0052 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-04T19:53:35+07:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / Convention Phase 7 supplemental parameter gate
- **Status:** BLOCKED
- **Objective:** Lock received OPT directions without substituting them for missing exact business contracts.
- **Inputs Read:** HOFF-0051, three explicit user OPT replies, optimization decision register, current progress and memory; final Phase 6 verification evidence unchanged.
- **Completed:** Recorded OPT-DEC-001 trusted adapters with missing/stale rejection, OPT-DEC-002 all four utilities with approved forecast sources, OPT-DEC-003 equal rank/dispatcher choice. Updated register/progress and removed stale current Phase 6 pending statements while retaining historical verification. Sent one supplemental structured batch asking only missing exact contracts and acceptance action, never the confirmed directions again.
- **Requirement IDs:** Phase 7 locked-decision persistence and no hidden source/scoring/acceptance policy.
- **Files and Artifacts:** docs/optimization-policy-decisions.md; plan-progress-summary.md; .ai-workflow/PROJECT_MEMORY.md.
- **Decisions:** DEC-009 records completed locked Billing V1; DEC-010 records all three selected OPT directions. Equal rank is not an open tie-break question. Missing parameters are not ASSUMED/DEFAULTED.
- **Assumptions:** None for provider qualification, capacity units, freshness windows, candidate statuses/pairing, utility curves/weights/score precision, expected cost coverage or accepted Trip lifecycle action.
- **Verification:** Documentation-only changes since final Phase 6 clean V1→V29 / populated V27/V28→V29 full suite and Flyway validate PASS: 302 reported/301 executed/zero failures/errors/one legacy skip; 94 real PostgreSQL methods and 207 executed non-PG cases. No Java/SQL change, migration or additional test skip. Diff and memory validator checked at handoff.
- **Open Issues and Risks:** Choosing full utilities does not supply their exact formulas/weights; choosing equal rank does not authorize reserve/create/dispatch side effects. Missing/stale trusted evidence rejects, but provider/source and freshness contracts still need qualification.
- **Blockers:** Only exact OPT-DEC-001 source/unit/freshness/eligibility contracts, OPT-DEC-002 utility formulas/parameters/weights/precision and expected economics composition, OPT-DEC-003 acceptance lifecycle action. Phase 8 waits for completed Phase 7. No Rating/Billing V1 policy blocker.
- **Next Required Action:** Receive supplemental fields, lock each supplied contract and implement confirmed Phase 7 tasks with real PG/migration/concurrency gates; then Phase 8. Do not reask selected A/A/A or return to completed 0–6 absent a proven defect.
- **Acceptance Gate:** Complete exact policy/source contract before production feasibility/scoring/acceptance; preserve completed Phase 6 and immutable V1–V29, then complete 7–8/final plan gates.
- **Do Not Redo:** Applied SQL/backfills, prior handoffs, locked RATE/BILL/OPT directions. Do not turn missing financial/compliance input into a zero or fabricated feasible candidate. PLAN is not COMPLETE.

### HOFF-0053 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-04T13:19:49+00:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / Convention Phase 7 decision register and regression foundation
- **Status:** BLOCKED
- **Objective:** Follow current continuation with only unresolved Phase 7 questions and useful assumption-free technical preparation.
- **Inputs Read:** Full workflow skill/playbook/memory; relevant Phase 7/8 plan; progress; full optimization/rating/domain/settlement contracts; tenant/migration ADRs; focused Load/Truck/Employee/Trip statuses, HOS observations, cost enums, Trip controllers/services, verification runner/tests and configured quality inventory. No Phase 0–6 implementation audit or business reopening.
- **Completed:** Split confirmed OPT directions from seven exact remaining contracts as OPT-DEC-004…010; sent ONE consolidated batch with current evidence/options/recommendation/reason and DB/service/API/test impact. Corrected current plan tolerance/version/tie text to locked decisions. Hardened runner from historical 192/40 to current 302 reported/301 executed and eight per-domain PG floors totaling 94, detects duplicated XML suites and future PG skips; six new Python verifier tests. No optimizer scoring/source/assignment assumptions or speculative tables.
- **Requirement IDs:** Current continuation sections 4–6/8–20/29/59/62–65; regression guarantee preservation independent of business policy.
- **Files and Artifacts:** optimization-policy-decisions.md; remaining-business-decision-gates.md; plan/progress; scripts/verify_backend_regression.py; scripts/tests/test_verify_backend_regression.py; memory.
- **Decisions:** OPT-DEC-001…003 remain CONFIRMED/LOCKED; unconfirmed exact fields now 004…010, not renamed or changed approved semantics. Canonical optimizer MILE and no binary-float authoritative ranking follow current user scope. No numerical policy/provider/status/source/action selected from recommendations.
- **Assumptions:** None about optimizer business policy. Synthetic XML tests test only the verification harness, not production feasibility/economic data.
- **Verification:** python3 -m unittest discover -s scripts/tests -v PASS: 13 verifier cases. Fresh python3 scripts/verify_backend_regression.py PASS on codex_regression_20261004131345874121 (/tmp/logisticsx-regression-dbl7046z): clean V1→V29, Flyway validate, 302 reported/301 executed/zero failures/errors/one named legacy skip, 94 PG methods including each completed suite floor. No new SQL; prior V28→V29 upgrade accepted unchanged. V1–V29 SHA256 unchanged; diff and memory validation at handoff. No Checkstyle/SpotBugs/ArchUnit gate configured in inspected POM/.github/tests; none skipped or installed.
- **Open Issues and Risks:** Graph-first query empty; impact verify_reports/ReportGateTest UNKNOWN due storage42/runtime40 mismatch. Source caller search confirms runner/main plus verifier tests; actual Python and full PG regression used, no graph all-clear. Only technical verification foundation is complete, not production optimizer.
- **Blockers:** OPT-DEC-004…010 exact eligibility, source/unit/freshness, raw definitions/curves, weights, precision, cost coverage and Accept action pending in one batch. Phase 8 waits for Phase 7 COMPLETE. No RATE/BILL or confirmed OPT decision unresolved.
- **Next Required Action:** Lock genuinely supplied exact contracts, implement 7A onward and migration/PG/scoring/concurrency gates; complete 7 then inspect relevant Phase 8 sources and one FLEET batch if needed. Do not repeatedly ask confirmed directions or normal implementation permission.
- **Acceptance Gate:** Complete explicit OPT contracts and working source/feasibility/scoring/acceptance before Phase 7 COMPLETE; preserve 302/301/94, one named legacy skip, immutable earlier migrations, then Phase 8/final system gates.
- **Do Not Redo:** Completed 0–6, applied V1–V29, historical backfills/handoffs, locked RATE/BILL/OPT directions. No hidden weights/TTL/statuses/forecast zero/Haversine/fixed-speed ETA or dispatch side effects. PLAN remains NOT COMPLETE.

### HOFF-0054 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-04T16:18:24+00:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / Convention Phase 7 domain and audit slice
- **Status:** DONE
- **Objective:** Lock all supplied Optimization V1 answers and implement the smallest verifiable approved domain/audit slice without reopening Phase 0–6.
- **Inputs Read:** Current continuation/user OPT-DEC-004…010 answers, full workflow skill/playbook and memory, relevant plan/progress/optimization/rating/settlement contracts; focused entities, controllers/security, source/provenance, cost/assignment schema and verification gates. Graph-first query/impact followed by source confirmation because graph storage42/runtime40 yields UNKNOWN.
- **Completed:** All ten OPT decisions CONFIRMED/LOCKED. Exact curves/weights/DECIMAL128/HALF_EVEN/DENSE_RANK and explainable contribution reconciliation. Candidate-bound source/class/unit/freshness evidence, explicit km/lb conversions, full-HOS subsystem port/route-plan binding, independent rejection aggregation and complete approved forecast coverage/zero/currency guards. V30 immutable authored policy/run/candidate audit with published policy API and authorization; V31 forward-only NULL-score constraint correction. No provider/allowlist seeded.
- **Requirement IDs:** BE-CALC-016 domain/audit slice; all OPT-DEC-001…010. Full Phase 7 is IN_PROGRESS, not DONE.
- **Files and Artifacts:** OptimizationScoringPolicy/Engine; OptimizationEvidence/Validator/HardFeasibility/ForecastResolver; integration/hos/HosFeasibilityService; OptimizationAudit/Repository/PolicyService/PolicyController; narrow SecurityConfig guard; V30/V31; three unit classes and OptimizationAuditPostgresTest; regression runner/Python tests; optimization decisions/domain contract, remaining gates, plan/progress/memory.
- **Decisions:** Exact user policies only: 72h horizon, explicit status/source authoring, endpoint TTLs/units, four approved curves/weights, HALF_EVEN scales6/8, required variable forecasts/pre-tax rating, equal dense ranks and existing-Trip assignment WITHOUT create/dispatch. No repeated decision request. Driver dynamic evidence has 5m cap; authoritative DB snapshot follows explicit source validity. Qualification sources must be authoritative effective/versioned DB records.
- **Assumptions:** None for business semantics. Pure ports/test fixtures do not constitute configured production provider or accepted assignment.
- **Verification:** 56 new units and 11 new real PG methods. Clean codex_regression_20261004141122415158 (/tmp/logisticsx-regression-l32xdl4n), V30→V31 clone codex_regression_20261004141406735169 (/tmp/logisticsx-regression-iv2zph0s), populated V29→V31 clone codex_regression_20261004141746797894 (/tmp/logisticsx-regression-sd95_06z): each 369 reported/368 executed, 0 failures/errors, 1 named legacy skip and 105 PG methods. Flyway validate PASS, V1–V30 SHA unchanged, diff clean. 14 Python verifier tests PASS; new floors 369/368 and each of nine PG suites required.
- **Validation NOT Performed:** Full production source adapter/run/accept stale/concurrency workflow, Phase 8 or final plan gate; no production deployment/provider call claimed.
- **Open Issues and Risks:** Full workflow wiring remains. Exact allowlists/source registrations must be published by authenticated authority. V30 initial parser defects rolled back before apply; after applied V30 exposed rejected-score NOT NULL defect, fixed only by V31, not historical edit. V30 upgrade source has applied/validated schema and preserved financial baseline, but its new rejected-candidate test originally failed; the V31 clone resolves it and passes all tests. Diagnostic databases/logs retained.
- **Blockers:** No Optimization V1 business decision open. Phase 8 awaits Phase 7 completion and later fleet semantics.
- **Next Required Action:** Wire qualified authoritative DB evidence and trusted source/HOS adapters; scoped real candidate generation; authorized immutable run/view; transactional idempotent existing-Trip assignment with current-input/financial/resource revalidation and PG concurrency/physical tenant isolation. New migrations V32+. Do not stop or request permission for normal technical implementation.
- **Acceptance Gate:** Whole Phase 7 requires all feasibility/scoring/precision/source/coverage/rank/persistence/stale/retry/accept/concurrency/tenant tests and clean/latest/previous upgrade/validate/checksum/diff gates; then automatically Phase 8 business/source gate and implementation.
- **Do Not Redo:** Completed 0–6 or their RATE/BILL decisions; all confirmed OPT decisions; applied V1–V31; historical payroll backfills or prior handoffs. No fake score for infeasible/missing inputs, no remaining-hours legal HOS shortcut, no hidden status/source/financial default, no dispatch side effect.

### HOFF-0055 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-04T16:42:50+00:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / Convention Phase 7 authoritative source-capture slice
- **Status:** DONE
- **Objective:** Persist real candidate-bound source evidence without interpreting legacy unitless fields or mutating financial history.
- **Inputs Read:** Existing plan/progress, optimization decisions/domain/evidence/forecast/policy/audit, source-capture V32 draft, ledger/domain schema, Spring authorization, existing PG fixture and verification runner. Graph query empty and impact UNKNOWN due storage-version mismatch; focused source references confirmed.
- **Completed:** V32 append-only capacity/qualification/forecast evidence with approved source/policy, real Trip/Load/Driver/Truck context, units/as-of/validity, actor/reason/approval and immutable corrections. Explicit original-unit normalization; all qualification assertions mandatory. Forecast source capture freezes only exact locked ESTIMATE/APPROVED ledger values, audited zero and explicit applicability. Deferred PG guards reconcile complete forecast payload/links atomically. API authorization, normalized identity retry/conflict, correction concurrency and live ledger/supersession stale checks.
- **Requirement IDs:** BE-CALC-016; OPT-DEC-001/004/005/009. This source-capture slice is DONE; full Phase 7 remains IN_PROGRESS.
- **Files and Artifacts:** V32; OptimizationQualifiedInput, OptimizationQualifiedInputRepository/Service/Controller, narrow SecurityConfig guards, OptimizationQualifiedInputPostgresTest, verifier/Python tests, optimization contract/decisions, remaining gates, progress/memory.
- **Decisions:** Existing locked source/unit/qualification/forecast contracts only; explicit source registration and authored policy required. Existing ADMIN source-authority and ACCOUNTANT forecast roles reused; no new role or financial policy. Forecast money read from real ledger, never caller-provided.
- **Assumptions:** No new business assumption. Test-only sources/allowlists are explicitly fixture approvals; not production defaults or configured external provider claims.
- **Verification:** Compile/test-compile PASS. Clean codex_regression_20261004164009903565 (/tmp/logisticsx-regression-ixafwbyc) and V31→V32 clone codex_regression_20261004164143038095 (/tmp/logisticsx-regression-paqz2n6q): each 380 reported/379 executed, 0 failure/error, one legacy skip, 116 live PG methods (11 new). Flyway validate and V1–V31 SHA comparison PASS; git diff --check clean; 15 Python verifier tests PASS.
- **Validation NOT Performed:** Full source HTTP/full-HOS production adapter/run/view/Accept workflow, physical optimizer tenant isolation or Phase 8/final plan gate. No production source/deployment claimed.
- **Open Issues and Risks:** Initial V32 clean run had four baseline-suite connection-limit errors, while all new source methods passed. Bounded verifier-only Hikari pools to four/zero idle; reran both gates green, no test skipped or production configuration changed. V32 remained immutable after first apply. Qualified evidence must be selected explicitly per candidate by run wiring; sources require approved configuration.
- **Blockers:** No unresolved Optimization V1 business decision. Phase 8 is gated on Phase 7 completion and later fleet semantics.
- **Next Required Action:** Implement configured authenticated tenant-bound routing/availability/full-HOS adapter, batched real candidate generation and immutable run/view, then transactional existing-Trip-only acceptance without dispatch; revalidate evidence/ledger/resources and prove stale/retry/concurrency/physical tenant isolation. New migrations V33+.
- **Acceptance Gate:** Whole Phase 7 feasibility/score/rank/coverage/run/accept/concurrency/tenant matrix plus clean/previous upgrade/validate/checksum/diff; then automatically Phase 8.
- **Do Not Redo:** Applied V1–V32, completed phases 0–6, RATE/BILL/confirmed OPT questions, historical payroll backfill or earlier handoffs. No fake capacity/cost/score, timestamp date backfill, guessed source or dispatch side effect.

### HOFF-0056 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-04T16:52:28+00:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / Convention Phase 7 trusted source adapter slice
- **Status:** DONE
- **Objective:** Connect approved source ports without inventing routing/availability/HOS semantics or implicit tenant/provider configuration.
- **Inputs Read:** HOFF-0055/control/current delivery, approved optimization domain/ports/contracts, existing tenant binding and HTTP provider conventions, focused tests/config. Graph-first query/impact UNKNOWN due known index storage mismatch; source confirmed new adapter names absent and HOS port callers.
- **Completed:** OptimizationInputProvider dynamic source port and authenticated HTTPS aggregator/full-HOS adapter with exact tenant/context/appointment/simulation-plan binding, original route-unit normalization and no remotely asserted DB evidence. Explicit tenant resolver requires bound multi-tenant scope or configured single-tenant identity. Sanitized transport failures, no provider endpoint/token fallback and no redirects. Existing full-HOS assessment port reused; all legal checks/headroom remain qualified provider facts.
- **Requirement IDs:** BE-CALC-016; OPT-DEC-001/005/006. Adapter slice DONE; Phase 7 run/Accept remains IN_PROGRESS.
- **Files and Artifacts:** integration/optimization/OptimizationInputProvider and TrustedOptimizationHttpAdapter, OptimizationTenantScope, 12 deterministic local HTTP tests, explicit application.yml keys, regression floors/tests, optimization domain contract/progress/memory.
- **Decisions:** All previously locked OPT decisions. Freshness caps/units/route/HOS provenance unchanged; no numeric or source policy invented. HTTPS/authentication/timeouts are technical transport safeguards, not business forecast TTLs.
- **Assumptions:** No business assumption. Local HTTP is permitted only by a package-private test constructor and loopback fixture; production constructor requires HTTPS. Deployment requires explicitly approved endpoint/token/source configuration.
- **Verification:** Compile PASS; 12 targeted HTTP/tenant unit tests PASS; full clean codex_regression_20261004165021519082 (/tmp/logisticsx-regression-05ocrc88) PASS: 392 reported/391 executed, 0 failure/error, one legacy skip, 116 PG methods. 15 Python verifier tests PASS; all V1–V32 SHA unchanged; git diff --check clean. No schema change; HOFF-0055 clean/previous-upgrade/validate evidence retained.
- **Validation NOT Performed:** Configured live provider deployment, complete run/Accept persistence/resource concurrency or optimizer physical tenant flow; Phase 8/final plan gates not passed. Adapter-only slice did not rerun previous-version upgrade.
- **Open Issues and Risks:** No source availability is fabricated if environment is unconfigured. Published source identities must match actual trusted evidence, and authoritative DB capacity/qualification/forecast evidence remains separately selected. Batched scoped generation and short-transaction acceptance still needed.
- **Blockers:** No unresolved Optimization V1 business decision. No permission question required for next normal implementation.
- **Next Required Action:** Implement bounded explicit real scopes, batched authoritative DB input/financial reads, immutable run/view and rejection/rank explanation; then existing-Trip-only atomic Accept with current-source/ledger/resource revalidation, retry/stale/concurrency and physical tenant tests. New migration only if needed, V33+.
- **Acceptance Gate:** Whole Phase 7 unit/PG/run/Accept/concurrency/tenant matrix and clean/latest/previous upgrade/validate/checksum/diff; automatically proceed to Phase 8 once PASS.
- **Do Not Redo:** Applied V1–V32; COMPLETE 0–6; RATE/BILL/confirmed OPT questions or earlier handoffs. No Haversine/fixed-MPH/remaining-drive shortcut, guessed source/default tenant, financial mutation or dispatch side effect.

### HOFF-0057 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-04T17:20:50+00:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / Convention Phase 7 scoped run/view slice
- **Status:** DONE
- **Objective:** Wire approved real candidate generation, source/financial projection and immutable explainable run before consequential assignment acceptance.
- **Inputs Read:** HOFF-0056/control/current delivery; approved optimizer policy/evidence/score/source/forecast/audit and provider contracts; real Load/Trip/Stop/Driver/Truck/assignment/accepted-rating/ledger projections, focused controllers/security and PG fixtures. Graph-first impact/query UNKNOWN from known storage mismatch, with source-confirmed callers/new symbols.
- **Completed:** Explicit target accepted snapshot/existing Trip/pickup stop plus Driver/Truck sets and qualified source selections; deterministic bounded Cartesian generation with no sampling/demo/default. Scoped SQL projections and batched evidence/supersession/live-ledger reads; full trusted routing/HOS outside transactions. Hard eligibility/source/coverage before explainable score. Short atomic run/candidate audit with DB state/source/ledger/freshness revalidation, unscored state-drift rejection and exact dense-rank ties. Authorized run/view envelope, original-outcome normalized key retry/concurrency and explicit tenant snapshot. Run makes no assignment/dispatch or financial write.
- **Requirement IDs:** BE-CALC-016 run/view slice; OPT-DEC-001…009. Full Phase 7 remains IN_PROGRESS pending Accept/resource/physical tenant/final gates.
- **Files and Artifacts:** OptimizationCandidateRepository, OptimizationQualifiedInputBatchResolver, OptimizationApplicationService/RunController, compatible OptimizationAudit record extensions and bulk QualifiedInputRepository methods; eight OptimizationScopeTest units and 11 OptimizationRunPostgresTest methods; verifier floor/PG suite registry, contract/progress/memory.
- **Decisions:** Existing confirmed explicit scope/source/effective DATE/horizon/full-HOS/variable margin/precision/rank contracts. Explicit pickup stop selects authoritative appointment, not a guessed date. The 200-candidate API bound is a visible technical work limit; oversized requests reject, never truncate. Candidate ID sorting is display only. Source retrieval timestamps may refresh only if material values/identity/version remain equal; validity still rechecked.
- **Assumptions:** No new business assumption. Qualified provider fixture mocks only external adapter, not production repositories or financial aggregates. Real accepted rating produced through Phase 6 services; costs are real approved ledger facts.
- **Verification:** Compile/test-compile and eight targeted scope units PASS. Full clean codex_regression_20261004171832858772 (/tmp/logisticsx-regression-4rjh7xrn): 411 reported/410 executed, 0 failure/error, one legacy skip, 127 PG methods. Tests include real accepted rating/costs, score reconciliation/immutability/no assignment, same-key concurrency/retry/drift, source supersession/ledger drift, unknown/started/unavailable/HOS, state change during provider, foreign IDs/rating, API roles, tied dense ranks, historical NULL-date preservation and currency mismatch. 15 Python verifier tests PASS; V1–V32 SHA unchanged; diff clean. No schema change; HOFF-0055 clean/V31-upgrade/validate migration evidence retained.
- **Validation NOT Performed:** Atomic optimizer Accept, resource claims/legacy writer concurrency guards, complete optimizer physical tenant flow or Phase 8/final gate. No configured production provider/deployment claimed; no previous-version migration rerun for this schema-free slice.
- **Open Issues and Risks:** Initial 408-test attempt had two Mockito override fixture errors; replaced overrides with doAnswer/doThrow and reran all tests green, no skip/expectation weakening. Acceptance must revalidate exact original planning scope and material/state fingerprint, then lock/commit only DB-owned facts. Existing Trip/assignment history must be reused or appended without dispatch or synthetic future earning end.
- **Blockers:** No unresolved Optimization V1 business decision; implementation only.
- **Next Required Action:** Add V33+ immutable acceptance/command audit and DB resource concurrency guards; resolve external source/HOS revalidation outside long transaction, detect stale rather than silently re-rate, acquire consistent short DB locks, reuse matching assignment or append real assignment, set existing Trip truck only and never dispatch. Verify retries/aliases/input drift, stale/already selected/concurrent Load/Driver/Truck acceptance, no history mutation, real physical tenant isolation; then Phase 7 full migration/regression gates and automatically Phase 8 policy/source audit.
- **Acceptance Gate:** Whole Phase 7 matrix and clean/latest/previous upgrade/validate/checksum/diff before COMPLETE; then FLEET-DEC batch only if new semantics genuinely missing.
- **Do Not Redo:** Applied V1–V32; COMPLETE phases 0–6; RATE/BILL/all confirmed OPT questions, prior handoffs or historical financial backfills. No financial mutation, fake metrics/cost/score, hidden resource/status/source, or dispatch side effect.

### HOFF-0058 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-04T17:54:00+00:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / Convention Phase 7 COMPLETE → Phase 8 audit
- **Status:** DONE
- **Objective:** Complete existing-Trip-only Accept and whole Phase 7 gates, then proceed automatically to historical fleet source/policy audit.
- **Inputs Read:** HOFF-0057/control/current delivery; all locked OPT contracts, candidate/state/source/HOS/ledger/run services, actual Trip assignment convention, focused security/controllers and PostgreSQL fixtures. Graph impact UNKNOWN from storage42/runtime40 mismatch; source-confirmed dependency checks and full tests used, not claimed graph all-clear.
- **Completed:** Full source/HOS/material revalidation outside long DB transaction; short sorted resource/ledger/source locks and commit-time stale guards. Reuse matching actual assignment or append real open PRIMARY assignment, set existing Trip truck only, no dispatch/Trip creation/fake earning end. Immutable acceptance and operation-key/hash command journal, same-key retry/alias audit, different-choice conflict, same-Load and Driver/Truck concurrency protection including legacy writers, physical PostgreSQL tenant isolation. Whole Phase 7 COMPLETE; Phase 8 audit opens without permission request.
- **Requirement IDs:** BE-CALC-016 Tasks 7.1–7.3 and Accept; all OPT-DEC-001…010.
- **Files and Artifacts:** OptimizationAcceptanceService/Repository, OptimizationRunController, V33__guard_atomic_optimization_acceptance.sql, OptimizationAcceptancePostgresTest (11 methods), verifier floor/PG registry and 16 Python tests; optimizer contract/decision/plan/progress/memory.
- **Decisions:** All previous RATE/BILL/OPT locked unchanged; OPT-DEC-010 B exact existing-Trip assignment only. No new business policy. Actual assignment soft-close remains real lifecycle command, never invented 72h pay interval. External interactions remain outside assignment transaction; DB-only facts need no invented outbox framework.
- **Assumptions:** No business assumption; approved endpoints/credentials/status allowlists/source registrations are explicit deployment configuration. Fixture mocks external adapter only; actual financial/qualified source/assignment repositories remain real PostgreSQL.
- **Verification:** Clean codex_regression_20261004174357934215 (/tmp/logisticsx-regression-qcfj6tp_) and populated V32→V33 clone codex_regression_20261004175111184754 (/tmp/logisticsx-regression-xvgk0h2e) PASS: 422 reported/421 executed, zero failures/errors, one legacy skip, 138 PG methods. 283 non-PG executed including 76 optimizer unit/HTTP/scope methods. 16 Python verifier tests PASS; Flyway validate PASS; V1–V32 SHA unchanged, V33 unchanged after first application; diff clean. Source V32 database unmodified. Configured Maven compile/full tests/Flyway ran; no Checkstyle/SpotBugs/ArchUnit configured in pom/CI.
- **Validation NOT Performed:** Phase 8/final full-plan gate, production endpoint deployment/rollout or general production-readiness review. PLAN NOT COMPLETE.
- **Open Issues and Risks:** First clean V33 run had one API test fixture error obtaining WebApplicationContext by type; changed fixture injection then both full gates green. Applied migration not edited, no new skip/test disabling. Qualified production providers/publication remain mandatory fail-closed configuration, not a fake local production fallback.
- **Blockers:** None for Phase 7. Phase 8 genuinely unspecified interval/productive/capacity semantics require focused evidence audit and one new decision batch if missing; unsupported fleet health may remain UNAVAILABLE as already approved.
- **Next Required Action:** Inspect relevant Truck/Trip/mileage/maintenance/history/report sources; create docs/fleet-utilization-policy-decisions.md with FLEET-DEC-001…007 accurate status/evidence/options/impacts. Ask one batch only for missing fleet semantics, continue safe assumption-free work. New schema V34+ only.
- **Acceptance Gate:** Approved fleet lifecycle/source/period/interval definitions then Phase 8 tests, clean/previous upgrade/validate/checksums/full regression/diff; final plan gate only when all convention phases COMPLETE.
- **Do Not Redo:** Complete Phases 0–7, applied V1–V33, RATE/BILL/OPT questions, immutable financial history or previous handoffs. No current-status history heuristic, invented fleet events, 24h capacity or fake KPI zero.

### HOFF-0059 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-04T18:03:00+00:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / Convention Phase 8 source/policy gate
- **Status:** BLOCKED
- **Objective:** Start Phase 8 automatically after completed Phase 7, identify only genuinely missing fleet semantics and request one batch.
- **Inputs Read:** HOFF-0058/control/current delivery, Phase 8 plan and existing fleet placeholders; focused Truck/TruckService, Trip/TripService/mileage/stop execution, DriverBehaviorEvent/EldVehicleMapping, MaintenanceRecord/Schedule/ReportService/Repository, actual ReportController/MetricAvailability and approved cost-classification policy. Graph-first query empty; source evidence confirmed separately. Read-only real V33 schema metadata inspected.
- **Completed:** No equivalent vehicle lifecycle/membership/capacity history exists; safety/identity mappings and optimizer prospective evidence cannot substitute. No approved productive/available/excluded mapping or event validity/order/correction contract. Current truck FK/Trip totals do not approve historical fleet-period mileage attribution. Created canonical FLEET-DEC-001…007 register with exact evidence/options/recommendations and DB/service/API/test impacts; four-question consolidated batch sent (001+003, 002, 006, 007). Removed old grouped fleet ID ambiguity, retaining prior unavailable permission.
- **Requirement IDs:** BE-CALC-017 / Phase 8 policy gate; FLEET-DEC-001…007.
- **Files and Artifacts:** docs/fleet-utilization-policy-decisions.md; remaining-business-decision-gates.md, convention plan/progress and memory. No Java/SQL change for this audit.
- **Decisions:** Existing no fake history/current-status utilization/zero KPI/planned-mile fallback/report GET write rules remain locked. FLEET-DEC-004/005 NOT_APPLICABLE to already authorized V1 UNAVAILABLE result for missing downtime/breakdown source; PM/cost-mile likewise unavailable if sources unqualified. No repeat health/RATE/BILL/OPT question. All recommendations for unresolved capacity/activity/interval/period are proposals, not production policy.
- **Assumptions:** None about fleet semantics. No useful safe event schema can be finalized before its lifecycle/validity is approved; no speculative interface/table added merely to claim progress.
- **Verification:** Read-only information_schema on verified codex_regression_20261004174357934215 confirms event/mapping/maintenance tables but no fleet status/membership/capacity history. Source/controller inspection confirms no /fleet/health or /executive-summary route to claim extended. No production DB queried/mutated. HOFF-0058 clean/upgrade/validate baseline retained: 422 reported/421 executed, one legacy skip, 138 PG methods, 16 Python PASS. Diff clean; memory validator PASS; HOFF-0001…0058 append-only unchanged.
- **Validation NOT Performed:** New fleet calculator/schema/tests, Phase 8 completion or final plan/system gate. No fresh regression claimed for docs-only audit. PLAN NOT COMPLETE; production readiness not claimed.
- **Open Issues and Risks:** Exact numerator/denominator and incomplete history treatment change business KPI correctness. Period/truck attribution and numeric presentation cannot reuse Rating/optimizer policy implicitly. History before proven capture remains unavailable, not synthetic.
- **Blockers:** BLK-007: receive canonical FLEET-DEC-001/002/003/006/007 answers, one batch already sent. Block is a new business semantic, not technical permission or a repeated locked decision.
- **Next Required Action:** Record exact answers CONFIRMED/LOCKED; implement minimal historical evidence/reconstruction/report/availability under approved semantics with V34+ and real PostgreSQL gates; then final cross-domain regression/clean/previous-upgrade/validate/checksum/diff verification.
- **Acceptance Gate:** Phase 8 approved lifecycle/coverage/period/source definitions, all interval/mileage/health-unavailable/tenant/report tests and migration/full regression green; only then final PLAN COMPLETE.
- **Do Not Redo:** Complete 0–7, applied V1–V33, RATE/BILL/OPT questions or old handoffs. Do not treat missing health classification as another blocking permission or fabricate historical events to pass.

### HOFF-0060 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-04T18:09:00+00:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / Convention Phase 8 confirmed direction receipt
- **Status:** BLOCKED
- **Objective:** Lock the four explicit A replies without silently inventing additional numeric or conflicting-interval semantics.
- **Inputs Read:** Four asynchronous business-owner replies to the FLEET batch, HOFF-0059/current control, canonical Fleet policy document and focused source/schema evidence retained from audit.
- **Completed:** FLEET-DEC-001/003 versioned explicit capacity windows/mappings; 002 explicit versioned activity mapping; 006 explicit validity-bounded intervals; 007 proven completion attribution CONFIRMED/LOCKED. No repeated question about these directions. Remaining detailed reporting coverage/precision/rounding/aggregation and conflicting interval/order/correction/outside-capacity treatment were requested in the first batch but not specified by its option labels; isolated as 008/009 and sent one exact follow-up batch with complete proposed contracts/options/impacts.
- **Requirement IDs:** BE-CALC-017, FLEET-DEC-001…009.
- **Files and Artifacts:** Fleet decision doc, remaining decision register, convention plan/progress and memory; no Java/SQL change.
- **Decisions:** Four A choices locked; mappings/sources must be explicit authored policy fields, no seeded status/productive defaults. Existing absent-health-source UNAVAILABLE permission remains applicable. 008/009 proposals are not approved merely because the architecture directions were selected.
- **Assumptions:** No report precision, partial coverage, event conflict winner or clipping rule inferred. No financial/history mutation.
- **Verification:** Documentation consistency review, git diff --check and memory validator PASS; earlier HOFF blocks preserved. No new code/schema means HOFF-0058 full V33 clean/upgrade/validate 422/421 Java /138 PG /16 Python baseline remains last actual test run, not claimed rerun.
- **Validation NOT Performed:** Fleet calculator/schema/test gate and final system/plan gate. No fake completion.
- **Open Issues and Risks:** Numeric/coverage/reconciliation directly affect the authoritative fleet KPI. User may approve the proposed exact contracts or provide exact alternatives; confirmed direction choices must not be reopened.
- **Blockers:** Only FLEET-DEC-008/009 exact detailed answers, follow-up already sent. No RATE/BILL/OPT or completed Phase 7 blocker.
- **Next Required Action:** Record 008/009 answers CONFIRMED/LOCKED, implement approved evidence/policy/interval/proven completion attribution/report with V34+, PG matrix and migration gates; then final whole-plan verification.
- **Acceptance Gate:** All actual Phase 8 implementation/tests/migrations and final cross-domain/system gates pass before PLAN COMPLETE.
- **Do Not Redo:** Completed 0–7/V1–V33, previous handoffs, four confirmed Fleet A directions, Rating/Billing/Optimization or already authorized health-unavailable questions.

### HOFF-0061 — Backend Developer → Backend Developer

- **Timestamp:** 2026-10-04T18:11:00+00:00
- **From Role:** Backend Developer
- **To Role:** Backend Developer
- **Phase:** 7 — Backend Development / Convention Phase 8 approved implementation
- **Status:** DONE
- **Objective:** Lock final exact reporting/reconciliation choices and resume implementation without repeated questions.
- **Inputs Read:** Explicit A replies for FLEET-DEC-008/009, prior HOFF-0060/current control and Fleet source/decision contract.
- **Completed:** Strict coverage/report arithmetic and explicit conflict/unavailable/correction A contracts CONFIRMED/LOCKED; all required Fleet V1 semantic gates resolved. Capacity/activity mappings and qualified sources remain mandatory published inputs, not seeded defaults. Preparation transitions to approved implementation.
- **Requirement IDs:** BE-CALC-017; FLEET-DEC-001…009.
- **Files and Artifacts:** Fleet decision doc, plan progress and memory. Java/SQL implementation starts next.
- **Decisions:** FLEET_REPORTING_V1 v1 [from,to) Instants, explicit ZoneId for dates, sum durations, DECIMAL128 ratio8/percent2 HALF_EVEN; strict unavailable on required gaps. Identity replay/conflict, occurredAt ordering, conflicting overlaps unavailable, append-only correction, no productive outside membership/capacity clipping. All previous decisions unchanged.
- **Assumptions:** None; exact values are user-confirmed.
- **Verification:** Decision receipt/document checks, memory validation and diff check PASS; previous full V33 clean/upgrade baseline retained, no new code gate claimed.
- **Validation NOT Performed:** Phase 8 implementation/full final system gate, not complete yet.
- **Open Issues and Risks:** Real evidence/policy publication must be explicit, no fabricated history or metric zero.
- **Blockers:** None; BLK-007 resolved by received decisions.
- **Next Required Action:** Implement V34+ immutable published fleet policy, audited validity-bounded sources/events and provenance-preserving completion attribution, scoped SQL interval/report calculator and role-protected API; run unit/PG/clean/upgrade/validate/checksum/diff gates then final system verification.
- **Acceptance Gate:** Phase 8 complete test matrix and final cross-domain/system gates before PLAN COMPLETE.
- **Do Not Redo:** Completed 0–7/V1–V33, historical handoffs or any confirmed RATE/BILL/OPT/FLEET question.

### HOFF-0062 — Backend Developer → QA / Tester

- **Timestamp:** 2026-10-04T18:37:00+00:00
- **From Role:** Backend Developer
- **To Role:** QA / Tester
- **Phase:** 7 — Backend Development / Convention Phase 8 COMPLETE → final verification
- **Status:** DONE
- **Objective:** Complete approved historical fleet implementation and hand off only final whole-system confirmation.
- **Inputs Read:** HOFF-0061/current control; all exact FLEET choices, focused Truck/Trip/maintenance/real mileage/controller/cost classification source evidence; full workflow playbook and scoped exit gates. Graph impact/API impact UNKNOWN from known storage mismatch; source-confirmed callers/new symbols and actual tests used.
- **Completed:** Immutable versioned authored fleet maps/source registration; audited validity-bounded membership/capacity/activity intervals with identity replay/conflict and single-chain correction. Scoped SQL interval sweep/coverage/conflict sums, strict unavailable, independent Fleet numeric/ZoneId policy, read-only report/health API. Immutable proven actual completion truck/mileage attribution, no current-FK/planned fallback or proportional split. Existing roles/tenant actor/provenance, no dashboard write. Unsupported health source metrics return authorized unavailable, not fake zero or summed unqualified maintenance sources. V35 forward context/time correction after V34 applied, no old SQL edit. Phase 8 COMPLETE, convention 0–8 implementation COMPLETE.
- **Requirement IDs:** BE-CALC-017 Tasks 8.1/8.2 and health availability; all FLEET-DEC-001…009.
- **Files and Artifacts:** FleetHistory/IntervalCalculator/Repository/Service/Controller; narrow ReportController/SecurityConfig additions; V34/V35; 12 Fleet unit and 22 PostgreSQL methods, verifier (17 Python), Fleet policy/domain docs, plan/progress/memory.
- **Decisions:** All exact confirmed Fleet maps/validity/completion/strict coverage/conflict/numeric/report period contracts; no repeated business question. Financial/RATE/BILL/OPT and old source semantics preserved. Complete cost coverage is not inferred merely from a subset of qualified ledger projections.
- **Assumptions:** No business assumption or seeded runtime policy; approved production source capture remains mandatory. Explicit technical scope bound 200 trucks rejects oversize, never samples. No synthetic history before proven sources.
- **Verification:** V34 clean codex_regression_20261004182505410806 (/tmp/logisticsx-regression-lbumccpp) and populated V33→V34 codex_regression_20261004182749457307 (/tmp/logisticsx-regression-pp8s_l1t) PASS at 454/453 Java/158 PG. V35 clean codex_regression_20261004183038137069 (/tmp/logisticsx-regression-opdrt9o_) and populated V34→V35 codex_regression_20261004183335796181 (/tmp/logisticsx-regression-pkmopxom) PASS: 456 reported/455 executed, zero failures/errors, one legacy skip, 160 PG, 295 non-PG; 17 Python PASS; Flyway validate/checksums/diff PASS. Maven clean verify includes build/repackage; executable Boot JAR manifest verified. Original source DBs unmodified. Initial Java Period wildcard import ambiguity fixed before schema gates, no test disabling.
- **Validation NOT Performed:** Final clean confirmation currently RUNNING on codex_regression_20261004183625270573 (/tmp/logisticsx-regression-uxlpmxcs); no final PLAN COMPLETE or production-ready verdict yet. Production deployment/security/load benchmark broader scope not claimed.
- **Open Issues and Risks:** Qualified endpoints/published source policies/history capture require operational rollout; unsupported health metrics honestly unavailable. No configured Checkstyle/SpotBugs/ArchUnit exists; actual configured Maven/Flyway/verifier gates ran. Self-review defect fixed forward V35 with tests.
- **Blockers:** None; all requested business decisions resolved.
- **Next Required Action:** QA owner finish final clean verify/full cross-domain matrix, previous-version upgrade/validate/hash/diff and memory/decision consistency; document exact final backend gate verdict and PLAN COMPLETE only after actual PASS. Do not reopen implementation or policies absent a proven failure.
- **Acceptance Gate:** All convention 0–8 complete and final actual system gates green, no new skips/fake metrics/hidden policy/history mutation or tenant leakage; production readiness remains separate.
- **Do Not Redo:** Completed implementation/locked questions, applied V1–V35, original DBs or prior handoffs. Do not transform approved unavailable outcomes into invented health values.

### HOFF-0063 — QA / Tester → QA / Tester

- **Timestamp:** 2026-10-04T18:52:00+00:00
- **From Role:** QA / Tester
- **To Role:** QA / Tester
- **Phase:** 10 — Testing / approved backend Convention V1 final gate
- **Status:** DONE
- **Objective:** Finish final actual cross-domain verification and hand off the completed backend plan without pretending production deployment/readiness.
- **Inputs Read:** Current Control/Current Delivery and HOFF-0062; retained completed-source/decision/checkpoint evidence; focused existing test matrices, configured root Maven gates, final runner summaries/logs, real executable JAR/OpenAPI, applied migration manifest. No completed-phase re-audit or reopened policy.
- **Completed:** Final same-code clean and populated previous-version upgrade, full regression and Flyway validate/history PASS. All required convention Phases 0–8 COMPLETE; PLAN COMPLETE for approved backend V1. Documented actual cross-domain evidence, approved unavailable outcomes and separate operational scope; corrected stale current progress/decision metadata without rewriting historical handoffs. Preserved unrelated dirty/staged work.
- **Requirement IDs:** Final convention backend plan criteria; retained BE-CALC-001…017, RATE/BILL/OPT/FLEET locked decisions and immutable financial/resource/history/tenant invariants.
- **Files and Artifacts:** docs/backend-plan-final-verification.md; docs/verification/migration-sha256-v35.txt; plan/progress/current decision/domain metadata and PROJECT_MEMORY.md. No Java/SQL change during or after final Maven runs.
- **Decisions:** All required decisions already CONFIRMED/LOCKED; no new business assumption/question. Health unsupported-source UNAVAILABLE is approved, not a missing implementation silently returning zero. Production source endpoints/published exact policies/evidence are runtime requirements, no seeds or fake forecasts.
- **Assumptions:** No business assumption. Executable runtime confirmation uses only a disposable local V35 DB and loopback, not production. No deployment/commit/remote account mutation authorized or performed.
- **Verification:** Final clean codex_regression_20261004183625270573 (/tmp/logisticsx-regression-uxlpmxcs) and populated V34→V35 codex_regression_20261004184241080791 (/tmp/logisticsx-regression-ndxdre_c) PASS; source codex_regression_20261004182505410806 unchanged. Each: Maven clean verify/repackage, 456 reported/455 executed, 0 failures/errors, exactly one named legacy skip, 160 PG across 13 independent domains, 295 non-PG. Python verifier 17 PASS. Actual executable JAR GET /v3/api-docs PASS: OpenAPI 3.1.0, 134 paths, 283 schemas, all 16 required rating/billing/optimizer/fleet operations present. Process stopped after read-only verification. All 35 SQL SHA checks PASS; V1–V34 unchanged, V35 untouched after application; diff/memory validator PASS. Prior 62 handoff bodies retained unchanged.
- **Validation NOT Performed:** Production rollout, live provider/source certification, frontend/browser E2E, broad production security/performance review. No configured Checkstyle/SpotBugs/ArchUnit/PMD/Failsafe gate exists to claim run. No coverage percentage or graph all-clear invented; graph mismatch remains UNKNOWN with source/test fallback.
- **Open Issues and Risks:** Operational publication/capture/credentials/history rollout required for AVAILABLE runtime outcomes; historical missing sources remain honest unavailable. Broader skill production readiness remains NOT READY, outside approved backend completion. No remaining backend task/business gate.
- **Blockers:** None for approved backend Convention V1.
- **Next Required Action:** Hand off final verified backend V1 artifacts and preserve completed decisions/history. Any production configuration/source/release work is a separate scope; no permission assumed for deployment. Future proven schema defect uses V36+ only.
- **Acceptance Gate:** PASS — all approved convention Phases 0–8 COMPLETE plus actual final clean/upgrade/validate/build/regression/PG/API/checksum/diff/memory evidence; no new skip, fake metric, hidden financial policy, historical mutation or tenant leakage.
- **Do Not Redo:** Completed phases/policies, V1–V35, historical backfills, source DBs or HOFF-0001…0062. Do not turn unsupported health into fake zero or conflate backend PLAN COMPLETE with production readiness.

## 10. Final Readiness

| Check | Status | Evidence / Exception |
|---|---|---|
| Requirements implemented and traced | PASS | Approved backend convention Phases 0–8 and final QA gates COMPLETE; not broader production release certification |
| Build successful | PASS | Final same-code clean and populated upgrade each run Maven clean verify/repackage; executable Boot JAR startup/OpenAPI confirmed |
| Tests successful | PASS | Final clean V35 / populated V34 upgrade: 456 reported/455 executed, 0 failures/errors, 1 named legacy skip; 160 PG and 17 Python PASS |
| API working | PASS | Scoped controller/service/PostgreSQL auth/envelope tests and actual OpenAPI 3.1.0 134 paths/283 schemas/16 required new operations; no live production provider certification |
| Frontend working | NOT_APPLICABLE | API-only scope |
| Database migrations working | PASS | Final clean V1–V35 and populated V34→V35 clone + validate PASS; all 35 SHA verified, applied files untouched; production rollout separate |
| Authentication and authorization working | PASS | Existing scoped roles/tenant actors, command/read ownership and physical tenant isolation verified in real integration tests; broader security review separate |
| Validation and error handling working | PASS | Scoped domain failure/ambiguity/date/mileage/source/tax/credit/stale/resource/availability errors and retry/conflict contracts verified |
| Security reviewed | NOT_STARTED | Not reviewed |
| Performance reviewed | NOT_STARTED | Not reviewed |
| No hardcoded secrets | NOT_STARTED | Not scanned |
| Deployment and rollback working | NOT_STARTED | Not verified |
| Observability ready | NOT_STARTED | Not verified |
| Documentation complete | IN_PROGRESS | Approved backend plan/decision/domain/final evidence docs complete; broader production operational documentation/review separate |
| No unresolved release-blocking defects | PASS | No unresolved approved backend V1 implementation/business gate; production deployment/security/performance review not certified |

**Production Verdict:** NOT READY

**Backend Convention V1 Verdict:** PLAN COMPLETE — Phases 0–8/final gates PASS at V35. This scoped verdict does not override the broader Production Verdict.

**Residual Risks / Approved Exceptions:** Unqualified legacy sources are fail-closed, unsupported health unavailable is explicitly approved. RSK-001 legacy qualification and RSK-002 production tenant rollout validation remain operational considerations; RSK-003 resolved. No production-readiness exception is approved.
