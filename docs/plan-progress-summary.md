# LogisticsX implementation checkpoint

Updated: 2026-10-05 (Asia/Ho_Chi_Minh), final verification PASS. Sources: locked decisions, runtime code/Flyway and actual Maven/PostgreSQL results. Prior handoffs retained in PROJECT_MEMORY.md. **PLAN COMPLETE — approved backend Convention V1, Phases 0–8 COMPLETE**, latest verified V35. RATE/BILL/OPT/FLEET V1 decisions CONFIRMED/LOCKED. Final same-code clean/previous-upgrade/build/regression/validate/checksum/API/diff gates PASS. Production deployment/readiness and frontend E2E are not claimed. Evidence: [backend-plan-final-verification.md](docs/backend-plan-final-verification.md).

## Current Task

TASK: Final approved backend plan / cross-domain verification
STATUS: PASS — PLAN COMPLETE (backend Convention V1)

IMPLEMENTED:
- V34 immutable published fleet policy, explicitly authored membership/capacity/activity mappings and qualified sources; append-only bounded history with actor/reason/source identity retry/correction, and proven immutable actual completion-mileage attribution. No current status/FK history backfill.
- Scoped SQL endpoint-sweep aggregation, strict history/gap/conflict coverage, eligible duration sums and exact Fleet DECIMAL128/ratio8/percent2 HALF_EVEN. Explicit Instant or LocalDate+ZoneId period; no JVM default. Read-only utilization-history/health APIs with existing envelope/roles, no dashboard writes.
- Twenty new PG and 12 Fleet unit methods; V34 clean + populated V33→V34 full tests/validate PASS at 454 reported/453 executed, 158 PG, one legacy skip. Seventeen Python verifier tests PASS.
- Self-review adds forward V35 coherent real Trip/Load execution and actual capture-time guards, two additional PG methods. Maven clean verify/build/repackage, V35 clean and populated V34→V35 + validate/checksum/diff PASS at 456/455 with 160 PG. Phase 8 COMPLETE; final same-code whole-system matrix also PASS.
- Final executable JAR local runtime OpenAPI PASS: 3.1.0, 134 paths, 283 schemas; 16 required rating/billing/optimizer/fleet operations verified against the disposable V35 DB. Loopback verification process stopped, no production deployment or financial write.
- Documented cross-domain test/availability/operational boundaries and retained applied migration SHA artifact; no completed-phase re-audit, old migration edit or unrelated-work discard.
- Inspected relevant Truck/Trip/actual mileage, ELD mapping/safety events, maintenance records/schedules, reporting controller and real V33 schema only; no Phase 0–7 re-audit.
- Created docs/fleet-utilization-policy-decisions.md: canonical FLEET-DEC-001…009, source evidence, locked replies and DB/service/API/test impacts. Pre-implementation V33 had no vehicle lifecycle history/membership/capacity windows or approved productive mapping; V34/V35 now implement the explicit evidence model. Current status/optimizer forecast still cannot prove actual history.
- Received all four A choices: 001/003 versioned explicit capacity/mappings, 002 versioned productive mapping, 006 validity-bounded intervals, 007 proven completion attribution CONFIRMED/LOCKED. No repeated direction question. 004/005 NOT_APPLICABLE to already authorized unsupported-source UNAVAILABLE outcome.
- Exact follow-up 008/009 A replies also received/locked: strict coverage, half-open period, duration sum ratio and explicit numeric policy; explicit conflict/unavailable and audited correction. No remaining policy question.

DECISIONS USED:
- Preserve all RATE/BILL/OPT locked decisions and completed 0–7. No synthetic history, fake KPI zero, planned mileage fallback or dashboard snapshot writes. Unsupported health UNAVAILABLE is already authorized.

FILES:
- FleetHistory, FleetIntervalCalculator, FleetHistoryRepository/Service/Controller, ReportController/SecurityConfig narrow extensions; V34/V35; FleetIntervalCalculatorTest/FleetUtilizationPostgresTest; verifier; fleet decision/domain docs, plan/progress/memory.
- docs/backend-plan-final-verification.md and docs/verification/migration-sha256-v35.txt: final same-code runtime/migration/regression evidence and operational scope.

MIGRATION:
- Final verified V35. Clean codex_regression_20261004183625270573 (/tmp/logisticsx-regression-uxlpmxcs) and populated previous-latest V34→V35 codex_regression_20261004184241080791 (/tmp/logisticsx-regression-ndxdre_c) PASS; source V34 unchanged. V1–V34 SHA unchanged, V35 unchanged after application. Manifest: docs/verification/migration-sha256-v35.txt; next V36+.
- Earlier Phase 8 V35 clean codex_regression_20261004183038137069 (/tmp/logisticsx-regression-opdrt9o_) and V34→V35 codex_regression_20261004183335796181 (/tmp/logisticsx-regression-pkmopxom) also PASS, retained as batch evidence.
- V34 historical clean codex_regression_20261004182505410806 (/tmp/logisticsx-regression-lbumccpp) and populated V33→V34 codex_regression_20261004182749457307 (/tmp/logisticsx-regression-pp8s_l1t) PASS. Original sources unmodified.

TESTS:
- V34 full clean/upgrade: 454 reported/453 executed, zero failures/errors, one legacy skip, 158 PG (20 Fleet), 295 non-PG (12 Fleet units); 17 Python PASS, Flyway validate/checksum/diff PASS.
- V35 clean and upgrade verify PASS: 456 reported/455 executed, zero failures/errors, one legacy skip, 160 PG (22 Fleet), 295 non-PG (12 Fleet units); 17 Python PASS. Executable Boot JAR manifest verified; no configured Checkstyle/SpotBugs/ArchUnit omitted because none configured.
- Final same-code clean and populated previous-upgrade confirmation PASS: each 456 reported/455 executed, zero failures/errors, exactly one named legacy skip, 160 real PG and 295 non-PG methods. Seventeen Python verifier tests PASS. Runtime OpenAPI, executable JAR, checksum/diff and memory validator PASS; details in final verification doc.

INVARIANTS:
- No historical availability from Truck.status/CreatedAt, 24h capacity default, synthetic events, every-ticket breakdown or unapproved interval/rounding policy.

BLOCKERS:
- None: exact 008/009 A replies now received/locked. All FLEET V1 directions/details resolved; no unresolved RATE/BILL/OPT choice. Qualified published mappings/source/evidence remain mandatory runtime authoring inputs.

PROGRESS:
- Phase 0–8 COMPLETE; final whole-system gates PASS. PLAN COMPLETE for approved backend V1. No unresolved required business decision; unsupported health UNAVAILABLE remains the locked contract, not unfinished guessed calculations.

NEXT:
- Hand off verified backend V1 artifacts. Production source publication/capture, endpoint credentials and rollout/release review are separate operational scope; no deployment/commit performed. Future defects require evidence and forward V36+ migrations, never old-history edits.

## Historical Completed Task — Phase 7 checkpoint (superseded by final gate above)

TASK: Phase 7 atomic existing-Trip acceptance and completion gate
STATUS: PASS — Phase 7 COMPLETE; Phase 8 source/policy audit starts next

IMPLEMENTED:
- Atomic Accept revalidates current qualified evidence/HOS outside the write transaction, then locks DB-owned resources/source/ledger and rejects material drift. Assigns/reuses driver assignment and existing Trip truck only; no Trip creation, dispatch or fabricated earning end.
- V33 immutable acceptance/command audit, same-key normalized replay/conflict, new-key aliases retaining original actor/time, same-Load uniqueness and overlapping Driver/Truck resource guards. Existing legacy assignment/truck writers participate in claim protection; legitimate actual assignment soft-close remains possible.
- Eleven new PostgreSQL methods cover success/retry/alias/drift, stale driver/truck/HOS/ledger/source, concurrent Load/Driver/Truck acceptance, matching historical assignment reuse, existing API roles and physical two-database tenant isolation. Full Phase 7 matrix/gates PASS.
- All OPT-DEC-001…010 exact user replies recorded CONFIRMED/LOCKED; no repeated question.
- Exact curve/weight/DECIMAL128/HALF_EVEN scoring, contributions/reconciliation and DENSE_RANK.
- Qualified candidate-bound evidence, explicit unit conversions/source validity, full-HOS port/plan binding and aggregated hard-rejection reasons.
- Candidate contribution forecast guards: accepted pre-tax revenue, complete ESTIMATE/APPROVED coverage, audited zero and no implicit FX.
- Immutable published policy/run/candidate audit, exact policy snapshot, PostgreSQL contribution checks, explicit rejected NULL score/rank; authorized policy publish/read API.
- Regression floor now 369 reported/368 executed and nine per-domain PostgreSQL floors (105 methods).
- V32 append-only qualified capacity/qualification/forecast records with exact candidate/policy/source/unit/validity/audit, correction versioning and normalized evidence-identity retries.
- Authenticated source capture and Accounting-only forecast route; exact live ESTIMATE/APPROVED ledger references, full conditional coverage/zero evidence/currency guards, deferred atomic payload/reference reconciliation. Old capacity/date/financial fields unchanged.
- Regression floor increased to 380 reported/379 executed and ten per-domain PostgreSQL floors (116 methods); verifier-only pools bounded to four to avoid test-context connection exhaustion.
- Authenticated HTTPS routing/ELD/availability and full-HOS adapter; exact tenant/candidate/appointment/route-plan binding; explicit original-unit conversion; no endpoint/token/tenant fallback or remotely asserted DB qualifications.
- Deterministic explicit real candidate scopes, batched state/source/supersession/ledger reads, accepted pre-tax revenue, authorized run/view API and complete immutable rejection/score/source/financial explanation.
- Provider calls outside transaction; commit-time state/freshness guards; normalized-key retry/concurrency outcome and exact persisted dense-rank ties. No assignment or financial write from run.

DECISIONS USED:
- All OPT-DEC-001…010 confirmed V1 contracts. Exact authored status allowlists/source registrations mandatory; no seed. Accept action is existing-Trip assignment only, no dispatch; implemented and verified.

FILES:
- OptimizationAcceptanceService/Repository, OptimizationRunController, V33, OptimizationAcceptancePostgresTest; regression verifier floor/registry and 16 Python tests; optimization contract/decisions/plan/progress/memory.
- docs/optimization-policy-decisions.md; docs/optimization-v1-domain-contract.md; optimization domain/services, HosFeasibilityService port, OptimizationAuditRepository, OptimizationPolicyController, narrow SecurityConfig guard.
- V30/V31; 56 new unit and 11 PostgreSQL methods; regression runner/Python tests; plan/progress/memory.
- V32; OptimizationQualifiedInput domain/repository/service/controller; 11 new PostgreSQL methods; narrow source authoring role guards.
- OptimizationInputProvider, TrustedOptimizationHttpAdapter, OptimizationTenantScope, 12 local HTTP tests and explicit application configuration. No adapter schema change.
- OptimizationCandidateRepository, OptimizationQualifiedInputBatchResolver, OptimizationApplicationService/RunController, compatible extended audit records; eight scope units and 11 PG run methods. No run/view schema change.

MIGRATION:
- Current latest V33: clean V1→V33 and populated V32→V33 PASS, Flyway validate PASS. V1–V32 SHA-256 unchanged; V33 unchanged after first application. Next schema correction V34+ only.
- V30 immutable optimization policy/run/candidate audit. V31 forward-only nullable-score correction: infeasible remains unscored; feasible still requires exact scale8 score/rank.
- Historical V32 qualified candidate source capture, complete deferred forecast/ledger links and immutable corrections. Applied V1–V31 unchanged at that checkpoint.

TESTS:
- Current clean V33: codex_regression_20261004174357934215, /tmp/logisticsx-regression-qcfj6tp_; 422 reported/421 executed, zero failures/errors, one legacy skip, 138 PostgreSQL methods PASS. Executed non-PG=283; optimizer-specific unit/HTTP/scope methods=76.
- Current populated previous-latest V32→V33: clone codex_regression_20261004175111184754, /tmp/logisticsx-regression-xvgk0h2e; identical full counts and Flyway validate PASS. Source codex_regression_20261004171832858772 remains unmodified. 16 Python verifier tests PASS; checksum/diff gates PASS. No configured Checkstyle/SpotBugs/ArchUnit gate exists in pom/CI; Maven compilation/full tests and Flyway gates actually ran.
- First V33 clean attempt: 422 reported, one API-test fixture error resolving WebApplicationContext; all ten other new acceptance PG methods passed. Corrected test injection and reran both full gates above, no test disabled/skipped and applied V33 untouched.
- Current run/view clean V32: codex_regression_20261004171832858772, /tmp/logisticsx-regression-4rjh7xrn; 411 reported/410 executed, 0 failure/error, one legacy skip and 127 PG methods PASS. Non-PG executed=283; eight new scope units and 11 PG run cases. Python verifier 15 PASS; V1–V32 hashes unchanged; diff clean. Initial new run fixture override errors corrected without disabling tests. Source-capture V31→V32 migration gate retained below; no schema change in run/view slice.
- Current adapter full clean V32 regression: codex_regression_20261004165021519082, /tmp/logisticsx-regression-05ocrc88; 392 reported/391 executed, 0 failure/error, one legacy skip and 116 PG methods PASS. Non-PG executed=275; 12 new HTTP/tenant tests plus all previous baseline cases. Python verifier 15 PASS; V1–V32 SHA unchanged, diff clean. Migration V31→V32 gate is the previously verified source-capture run below, not claimed rerun for this adapter-only slice.
- Current clean V1→V32: codex_regression_20261004164009903565, /tmp/logisticsx-regression-ixafwbyc; 380 reported/379 executed, 0 failure/error, one legacy skip and 116 PG methods PASS. Existing 56 optimization domain unit cases remain green; non-PG executed=263.
- Previous-latest populated V31→V32: clone codex_regression_20261004164143038095, /tmp/logisticsx-regression-paqz2n6q; same full counts/Flyway validate PASS. Source codex_regression_20261004141122415158 remains unmodified. V1–V31 SHA unchanged and diff check clean. Python verifier 15 PASS.
- Initial V32 run: new 11 PG methods PASS, but four existing-suite errors from PostgreSQL connection exhaustion across cached contexts. No skipped/disabled test or production pool change; verifier-only bounded pools fixed the infrastructure issue, then all gates above passed. Applied V32 was not rewritten.
- Unit: 56 new optimization methods PASS; total executed non-PG unit/API=263. Python verifier=14 PASS.
- PostgreSQL: 105 methods PASS, including 11 new audit/policy/API/concurrency methods.
- Full clean: codex_regression_20261004141122415158, /tmp/logisticsx-regression-l32xdl4n; 369 reported/368 executed/0 failure/error/1 legacy skip PASS.
- Previous-latest V30→V31: clone codex_regression_20261004141406735169, /tmp/logisticsx-regression-iv2zph0s; identical full counts and Flyway validate PASS. Source V30 schema was applied/validated, with the new rejected-score test exposing its constraint defect; no baseline financial test failed. Original source was not repaired.
- Incoming populated V29→V31 batch: clone codex_regression_20261004141746797894, /tmp/logisticsx-regression-sd95_06z; full 369/368/105 counts and Flyway validate PASS. V1–V30 checksum comparison and git diff --check PASS.

INVARIANTS:
- No Phase 0–6 regression, historical/financial mutation, default provider/status/weights or fabricated score. Old migrations/checksums retained; git diff --check clean.
- Hard feasibility precedes scoring; immutable explainable run and atomic existing-Trip Accept verified. No deployed trusted provider/production rollout claimed. Explicit production source endpoints/credentials/publication remain required and unconfigured sources fail closed.

BLOCKERS:
- No unresolved Optimization V1 business decision or remaining Phase 7 implementation gate. Phase 8 requires inspection/confirmation of historical interval/productive/capacity semantics; not assumed.

PROGRESS:
- Phases 0–7 COMPLETE. Phase 8 source/policy audit next. PLAN NOT COMPLETE; final full-system gate waits for Phase 8.

NEXT:
- Inspect only Phase 8 history/maintenance/mileage/report sources; record FLEET-DEC-001…007 accurately and ask one genuinely new business-decision batch if needed. New schema V34+ only; no fabricated status history.

## Completed

- **Phase 8 COMPLETE:** V34 immutable policy/evidence/history and proven completion mileage; V35 coherent execution/time guard; strict SQL coverage/duration/numeric and actual-mile reporting, role-protected read/capture APIs. Final clean/latest/previous-upgrade/validate/build/checksum/diff/runtime API PASS, 456/455 Java, 160 PG, 17 Python. Unsupported downtime/PM/full maintenance coverage/breakdown metrics explicitly UNAVAILABLE as authorized, never fabricated zero.
- **Phase 7 COMPLETE:** Task 7.1–7.3 and explicit Accept workflow PASS at V33. Published exact policy, qualified sources, full HOS, normalized curves/weights/precision/ties, contribution forecasts, immutable explanation and atomic assignment/retry/stale/concurrency/physical tenant gates verified. Clean/upgrade/validate/checksum/diff PASS; 422 reported/421 executed/138 PG/one legacy skip. Continue Phase 8 automatically.
- **Phase 6 COMPLETE:** 6A–6G PASS under approved V1 scope. Final clean V1→V29, populated V27→V29 batch and previous-latest V28→V29, Flyway validate/checksums/diff PASS. No new skip, financial default, historical date/purpose backfill or fake payment. Phase 7 policy audit starts automatically; no permission requested to begin.
- 6G.D implementation COMPLETE: V28 revenue commands/immutable impact audit, pre-lock stale guard and explicit recalculation/reapproval, atomic post-lock issue→ADJUSTMENT, legitimate zero-pay-impact audit without fake settlement/payment, source/retry guards, original earning-date policy and accumulated rounding. V29 preserves superseded driver calculation history. Nine PG methods plus deferred-method API domain error coverage (10 new PG methods in total), including locked payroll/payslip and two real PostgreSQL tenant databases. Final gate PASS below.
- 6G.C signed billing chain COMPLETE: V27 supplemental approved-charge claims, partial/full credits and exact caps, multiple issued credit evidence for rebill, signed revenue/profit/customer balance and explicit versioned driver revenue basis. New bases never infer legacy purpose; explicit old INVOICE_SUBTOTAL compatibility remains. Six PG methods and four unit cases added. Clean and populated V26→V27 gates PASS; subsequent driver consistency/corrections completed in 6G.D.
- 6G.B PRIMARY command slice COMPLETE: V26 accepted-snapshot reference, explicit Accounting tax decision/line assessment reconciliation, deterministic business/retry guards, immutable command outcome, DRAFT regeneration/stale check and issue/history protection. Seven PG methods; three-minor-unit currency preserved exactly. The single-invoice index retained at this historical slice was replaced consistently with signed consumers in V27; full 6G is now COMPLETE.
- 6G.A COMPLETE: V25 immutable audited TaxAssessment (Accounting-authenticated capture, original assessor/source/date/currency retained), same-identity replay/conflict, exact currency precision and tenant-owned context. Four unit + four PG methods. No internal tax engine, inferred tax rate or generated invoice claimed.
- Phase 6F COMPLETE: V24 immutable accepted snapshot with exact pricing DATE/source/change ID, all rule/mileage/index/accessorial/results, currencyScale/rounding policy, authenticated actor/time; normalized-key replay/conflict, stale preview check, atomic input revalidation/publication lock and append-only audited correction. Provider revisions and Load/rule changes never mutate accepted history. Two unit + six real PostgreSQL/API methods. Fixed proven missing snapshot read route guard, no security test skipped. Contract: docs/rating-accepted-snapshots.md.
- Phase 6E COMPLETE: explainable FLAT/PER_MILE + approved FSC engine, raw/bounded/rounded lines and exact pre-tax subtotal; full explicit request context/Load date/mileage/rule/accessorial audit inputs. Accounting-only ephemeral preview API, no database mutation/network inside DB transaction. Structured logging follows existing SLF4J; no duplicate metrics framework. Four unit + three live PostgreSQL cases; docs/rating-engine-v1.md.
- Phase 6D COMPLETE: real EIA ULSD v2 provider with explicit verified region/series, no future/stale/regional fallback; sanitized missing-key/transport/parser errors; provider/date/value/retrieval/version/hash metadata. Approved INDEX_BASED_MPG/RatingPolicyV1 DECIMAL128, scale-6 unit before currency total, currency-specific scale and unrounded min/max. Eleven new unit/HTTP tests. No database writes/migration. Contract: docs/rating-fsc-v1.md.
- Phase 6 Load date adapter COMPLETE: V22 nullable independent pickup business DATE, no TIMESTAMPTZ backfill, explicit create/update capture with authenticated actor and chained immutable audit, optimistic historical remediation. Appointment-only edit preserves promised date. Rating date input carries exact date/source/audit ID; accepted snapshot persistence remains 6F, not claimed by an input object.
- Phase 6C COMPLETE: V23 immutable contract Load mileage evidence, explicit per-component IDs and exact NUMERIC(12,3)/BigDecimal precision without rounding. Effective agreement/customer/currency/rule checks; source/ref/version/original value/unit/miles/actor/time/provenance. Missing planned/actual Load source -> unavailable or multi-load attribution-required; no Trip/legacy fallback. Eight unit and five PG cases added.
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

- Phase 015B COMPLETE: MULTI_JURISDICTION neutral framework, generic config/profile/policy, exact effective resolution, tax ports, signed economic gross/settlement mapping, supplement source claims, immutable calculation history, reconciliation and fail-closed availability. Contract: docs/payroll-contracts.md.

- Phase 015C COMPLETE: availability/reconciliation-aware review/approval/lock, authenticated actor audit, concurrent retry audit stability; V15 protects locked header/items/source claims/calculation history and blocks PAID while required items are incomplete.

- Phase 015D COMPLETE: lock atomically issues immutable payslip and persisted Unicode PDF with checksum/renderer/audit. Issuance retries retain original artifact; driver list/detail/PDF APIs enforce ownership. V16 protects artifact history. No email/external message or transfer sent.

- Phase 015E COMPLETE: immutable idempotent payment attempts, source/destination snapshots, parent-first locks, one active attempt, committed dispatch intent and pluggable provider port. Schedule/PROCESSING never imply paid; missing provider/unknown outcome remain explicit.

- Phase 015F COMPLETE: authenticated verifier port, immutable event/provenance/raw-body journal, duplicate/input-drift protection, exact financial identity, success/failure reconciliation, partial-run completion and late-outcome cases. Verified tenant binding uses existing registered datasource service; no unsigned tenant/default fallback.
- Phase 5 payroll completion hardening: V20 migrates legacy run `PAID` to `COMPLETED`; audited zero-net disposition sets item `NO_PAYMENT_REQUIRED`, creates no payment and preserves settlement `LOCKED`. Run completion is idempotent and requires every item terminal.
- Task 5.4 COMPLETE: Invoice legacy payroll inputs/filter reject explicitly; mapper does not write legacy fields; historical response projection remains read-only. No schema/history deletion.
- Phase 015G COMPLETE: audited bank reconciliation/case resolution; prior clean and populated V18→V19 regression verified.
- Continuation preparation COMPLETE: removed only the surplus EOF blank line at DriverPayPolicyController.java:53; git diff --check now passes. Recorded RATE-DEC-001…007, all confirmed by the user. Added reproducible PostgreSQL regression runner and seven report-gate unit tests.
- Phase 6A COMPLETE: V21 immutable versioned customer contract/rate domain, six nullable match dimensions, explicit priority/currency/date/component/FSC inputs, authenticated authoring API and stale concurrent append guard. Seven new unit cases and four real PostgreSQL methods; contract: docs/rating-v1-domain-contract.md.
- Phase 6B COMPLETE: explicit-priority resolver, inclusive LocalDate intervals, all configured dimensions match, no specificity/newest/database-order tie breaker, winning ambiguity and currency/contract validation. Fourteen new unit cases and two live PostgreSQL cases; internal resolver only, not arbitrary caller-date financial calculation.

## In Progress

- None in approved backend Convention V1: Phases 0–8 and final gates COMPLETE. Exact published status/source mappings remain mandatory operational inputs; absence fails closed, not hidden defaults.

## Payroll architecture

- Payroll architecture: MULTI_JURISDICTION.
- Tax implementations: pluggable by jurisdiction.
- Payroll framework, 015B–015G and zero-net COMPLETED semantics are COMPLETE. Do not reimplement them without a proven regression. All convention phases and final backend gates are COMPLETE.
- Region-specific tax policies: implemented incrementally with authoritative policy/source; none hard-coded or currently implemented.
- Generic country/subdivision/locality jurisdiction and independent EMPLOYEE/CONTRACTOR classification; contractor does not imply zero tax.
- Resolution priority: work/payroll override → effective employee profile → tenant default. Missing jurisdiction/policy/statutory adapter yields UNAVAILABLE tax and VALIDATION_REQUIRED; blocks approval/lock/payment scheduling.
- V14 adds separate payroll_jurisdictions, payroll_policy_versions and employee_payroll_profiles; V11 payroll schema is reused.
- Immutable payroll snapshots include resolved jurisdiction/classification/policy ID/version/effective date and tax inputs/outputs.

## Tests

- Final acceptance: both same-code V35 clean and populated V34→V35 runs PASS at 456 reported/455 executed, 0 failures/errors, 1 named legacy skip, 160 PG methods across 13 independently enforced domain suites, 295 non-PG methods. Python verifier 17 PASS; Maven clean verify/repackage and runtime OpenAPI PASS. Earlier entries below are historical, not current remaining tasks or lowered floors.
- Historical Phase 7 domain/audit slice: 369 reported / 368 executed, 0 failures/errors, 1 legacy skip; 105 PostgreSQL methods; 56 new unit and 11 new PG cases. Clean V31 and V30→V31 PASS; 14 Python verifier tests PASS. Run/accept/provider/Phase 8 gates were still pending at that checkpoint and are complete now.
- Historical regression foundation: 13 Python verifier tests PASS. Fresh stricter-runner clean V29 regression codex_regression_20261004131345874121 (/tmp/logisticsx-regression-dbl7046z) PASS: 302 reported/301 executed/0 failures/errors/1 named legacy skip; 94 PG and all eight per-domain floors verified. Flyway validate PASS. Prior checkpoint counts below are historical evidence, not lower current acceptance thresholds.
- **Final Phase 6 gate PASS:** clean codex_regression_20261004123217069452 (/tmp/logisticsx-regression-qbea0dmf), V27→V29 batch codex_regression_20261004123004873068 (/tmp/logisticsx-regression-joiuq31o), final same-source V28→V29 codex_regression_20261004123506439284 (/tmp/logisticsx-regression-feim5x6x). Every run: 302 reported / 301 executed / 0 failures / 0 errors / 1 named legacy skip; 94 real PG methods and 207 executed non-PG unit/API cases. Flyway validate PASS, all applied hashes unchanged and diff clean. Seven Python verifier tests separately PASS.
- 6G.D final source clean V1→V29 codex_regression_20261004123217069452 (/tmp/logisticsx-regression-qbea0dmf) and populated V27→V29 codex_regression_20261004123004873068 (/tmp/logisticsx-regression-joiuq31o) PASS: 302 reported / 301 executed / zero failure/error / one named legacy skip; 94 PG methods, 207 executed non-PG unit/API cases. Earlier V28→V29 codex_regression_20261004122604091911 (/tmp/logisticsx-regression-44lg9owh) also PASS with identical counts; final same-source rerun completed successfully as recorded above. Seven Python verifier tests PASS. Initial fixture column/creation-API/decimal-padding mistakes corrected against actual source, no production migration or baseline assertion rewrite.
- 6G.C clean V1→V27 codex_regression_20261004120335988095 (/tmp/logisticsx-regression-2y0o8fiz) and populated V26→V27 codex_regression_20261004120507136354 (/tmp/logisticsx-regression-mdlydb65), regression/Flyway validate PASS: 292 reported / 291 executed / zero failures/errors / one legacy skip; 84 PG methods. V1–V26 unchanged; V27 frozen after first application. BILL-DEC-006 newly confirmed: pre-lock revenue drift rejects; explicit recalculate/reapprove required.
- 6G.B clean V1→V26 codex_regression_20261004114029043703 (/tmp/logisticsx-regression-hrcwjrui) and populated V25→V26 codex_regression_20261004114225857415 (/tmp/logisticsx-regression-_c5li4sk), full regression/Flyway validate PASS: 282 reported / 281 executed / zero failure/error / one legacy skip; 78 PG methods. Initial JPA SMALLINT and JDBC bind-count failures fixed in Java, then gates rerun; no migration rewrite or skipped test. V1–V25 checksums unchanged; V26 unchanged after first application.
- 6G.A clean V1→V25 codex_regression_20261004112441009879 (/tmp/logisticsx-regression-243ws1x1) and populated V24→V25 codex_regression_20261004112612125787 (/tmp/logisticsx-regression-qswxpyzf), full regression/Flyway validate PASS: 275 reported / 274 executed / zero failure/error / one named legacy skip; 71 PG methods. Four unit + four PG assessment tests, seven Python verifier tests PASS. V1–V24 SHA256 unchanged; git diff --check clean.
- Final 6F clean V1→V24 and populated V23→V24 clone + Flyway validate PASS: each 267 reported / 266 executed / zero failures/errors / one named legacy skip; 67 live PostgreSQL methods. Clean codex_regression_20261004065734077168 (/tmp/logisticsx-regression-kp09gd53); upgrade codex_regression_20261004065914986712 (/tmp/logisticsx-regression-pefyz2ee). Seven Python verifier tests PASS. New snapshot API test first exposed missing route protection; fixed and all gates rerun, not skipped. `git diff --check` and touched-file whitespace checks clean.
- 6E clean V1→V23/full regression + validate PASS: 259 reported / 258 executed / zero failures/errors / one legacy skip; 61 live PostgreSQL methods. codex_regression_20261004064011710033, /tmp/logisticsx-regression-imgezs39. Four engine unit and three preview PG cases PASS.
- 6D full clean V1→V23 + Flyway validate PASS: 252 reported / 251 executed / zero failures/errors / one named legacy skip, 58 live PG methods. DB codex_regression_20261004063251074099, diagnostics /tmp/logisticsx-regression-3rl3vk6a. Eleven new FSC/rounding/real local HTTP fixture tests PASS; no live EIA credential deployment claimed.
- Latest 6C clean V1→V23 and populated V22→V23 clone full regression/Flyway validate PASS: each 241 reported = 240 executed + one named legacy skip, zero failures/errors; 58 PG methods. Clean codex_regression_20261004060841260687 (/tmp/logisticsx-regression-lp8ksqdo); upgrade codex_regression_20261004060931218651 (/tmp/logisticsx-regression-xh8p0yla). Seven Python verifier tests rerun PASS. Source V21/V22 DBs remain unchanged.
- V22 clean and populated V21→V22 clone + Flyway validation PASS: 228 reported, 227 executed, zero failures/errors, one legacy skip; 53 executed PostgreSQL methods. Clean codex_regression_20261004060002540892 (/tmp/logisticsx-regression-yplgkruq); upgrade codex_regression_20261004060134144460 (/tmp/logisticsx-regression-4c2bcbsb). Staging V21 clone seeded with a legacy appointment offset before upgrade; target retains NULL business DATE/audit, original V21 source is unchanged. Seven new PG and two unit date cases PASS. Accepted rating snapshot test is pending 6F, not skipped.
- Latest 6B clean V1→V21 and populated V20→V21 clone regression/validation PASS: each 219 reported = 218 executed + one named legacy skip, zero failures/errors; 40 baseline + six new PostgreSQL methods. Clean codex_regression_20261004053802305662, diagnostics /tmp/logisticsx-regression-fsawox9p; upgrade codex_regression_20261004053624414292, diagnostics /tmp/logisticsx-regression-mm1jcc4q. Seven Python verifier tests also rerun PASS. No V21 SQL change between 6A and 6B.
- 6A clean and populated V20→V21 clone full regression PASS: each 203 reported = 202 executed + one named legacy skip, zero failures/errors; 40 baseline + four new PostgreSQL methods. Clean DB codex_regression_20261004053017140739, diagnostics /tmp/logisticsx-regression-b1_hwkmp; populated clone codex_regression_20261004053108159552, diagnostics /tmp/logisticsx-regression-z7n7seuj. Flyway validates all 21 migrations on both runs.
- Historical preparation verification: `python3 scripts/verify_backend_regression.py --upgrade-from codex_classification_20261004022720` PASS on isolated clone codex_regression_20261004050344284081. 192 Surefire-reported tests = 191 executed + 1 named legacy skip, 0 failures/errors; all 40 PostgreSQL methods executed. This accurately disambiguates earlier shorthand “192 tests”; no new skip was added.
- Historical runner unit baseline was seven tests; current `python3 -m unittest discover -s scripts/tests -v` PASS is 13 tests, including completed-domain coverage floors and duplicate suite detection. Python verifier tests remain separate from Java reported/executed counts.
- Flyway validation and history V1–V20 PASS on the clone. Log and summary: `/tmp/logisticsx-regression-pf4nji1t/maven.log`, `/tmp/logisticsx-regression-pf4nji1t/summary.json`. Source V20 DB and its 7 historical LEGACY_PAID_RUN rows were not migrated/modified.
- Reproducible clean verification: `python3 scripts/verify_backend_regression.py`; populated upgrade verification: `python3 scripts/verify_backend_regression.py --upgrade-from <verified-local-codex-database>`. Runner only creates disposable DBs/clones and retains diagnostics; it never repairs or migrates the source. New migration batches must run both modes and Flyway validation.
- Historical verification below remains evidence of completed prior slices, not the current test count.
- 3 policy unit tests; 9 calculator unit tests; existing profitability facts/service, shipment-cost, accessorial and other regression suites passed.
- Existing cost/accessorial test expectations unchanged. Profitability expectations changed only for the newly authorized policy, known costs and empty eligible cost sets.
- Phase 4A full regression on codex_classification_20261003145035: 114 tests, 0 failures/errors, 1 legacy skip; 11 live PG cases. Added 12 policy/resolver/period unit cases and 2 policy/period live cases, including concurrent version creation and retained locked-settlement policy reference.
- Phase 4B full regression on codex_classification_20261003145401: 119 tests, 0 failures/errors, 1 legacy skip; 12 live PG cases. Four mileage unit cases and live settlement snapshot/source validation passed. Snapshot/idempotent sequential retry verified; concurrent settlement creation remains a Phase 4F gate.
- Phase 4C full regression on codex_classification_20261003145821: 123 tests, 0 failures/errors, 1 legacy skip; 13 live PG cases. Three work-pay unit cases and per-load/hourly/daily/flat live source/dedup/snapshot case passed after fixing the real interval mapping defect.
- Phase 4D final clean regression on codex_classification_20261003150051: 127 tests, 0 failures/errors, 1 legacy skip; 14 live PG cases. Three percentage-pay unit cases and live subtotal/tax exclusion/dedup/missing-source snapshot case PASS.
- Phase 4E clean regression on codex_classification_20261003150336: 131 tests, 0 failures/errors, 1 legacy skip; 15 live PG cases. Three attribution unit cases and approved driver amount/draft exclusion/ambiguous recipient integration PASS.
- Phase 4F clean full regression on codex_classification_20261003152740: 141 tests, 0 failures/errors, 1 pre-existing legacy skip; 21 live PG cases. Three reconciliation unit cases, trip-only profitability guard, six live settlement/concurrency/correction/validation cases PASS. V12→V13 upgrade and full regression on populated codex_classification_20261003150336 PASS with the same 141-test count. Maximum-length reversal reason verified; historical invalid toy fixture corrected to satisfy its stated monetary invariant.
- 015B clean regression codex_classification_20261003194231 and populated V13→V14 upgrade codex_classification_20261003152740 PASS: 161 tests, 0 failures/errors, 1 legacy skip; 29 PG cases. Twelve neutral payroll unit cases and eight live hierarchy/tax-port/currency/reconciliation/idempotency/concurrency/snapshot/API cases. JSONB comparisons use structured equality. Mapping metadata test now recognizes explicitly assigned snapshot/source IDs, tenant INTEGER singleton and mapped join table; real JPA/PostgreSQL startup independently verifies all mappings.
- 015C clean codex_classification_20261003194644 and populated V14→V15 codex_classification_20261003194231 full regression PASS: 166 tests, 0 failures/errors, 1 legacy skip; 31 PG cases. Three workflow unit and two live concurrent lock/immutable header/item/claim/snapshot/API actor cases. LOCKED != PAID and all-required-item completion guard verified.
- 015D clean codex_classification_20261003195715 and populated V15→V16 codex_classification_20261003194644 full regression PASS: 169 tests, 0 failures/errors, 1 legacy skip; 32 PG cases. Two PDF Unicode/multipage round-trip unit cases and live concurrent issuance/history/ownership/API case. Proven prior settlement retry storage-padding drift fixed in DTO presentation without monetary rounding or prior expectation changes.
- 015E clean codex_classification_20261003200459 and populated V16→V17 codex_classification_20261003195715 full regression PASS: 174 tests, 0 failures/errors, 1 legacy skip; 34 PG cases. Three dispatch intent/provider/unknown-outcome unit cases and two live concurrent scheduling/dispatch/history/unknown-provider cases. Scalar parent-ID read before row locking corrected proven stale persistence-context race.
- 015F clean codex_classification_20261003201635 and populated V17→V18 codex_classification_20261003200305 full regression PASS: 184 tests, 0 failures/errors, 1 legacy skip; 38 PG cases. Six identity/late-outcome/tenant-binding unit cases and four live duplicate/partial success/failure-retry/input-drift/unsigned/API/evidence-guard cases. Existing tenant filter/routing read only as callback dependency; no Phase 0 audit repeat.
- `git diff --check` PASS. Runtime OpenAPI/controller mappings load with the full application context.

## Migrations

- Current latest verified V35: clean V1→V35 and populated V34→V35, Maven verify/Flyway validate/checksums PASS. V34 separately proved clean and V33→V34. All earlier files unchanged; next V36+.
- Historical V33 checkpoint: clean V1→V33, populated V32→V33, Flyway validate and all earlier SHA-256 checks PASS. V34+ was next at that checkpoint; no applied migration edits.
- Historical V31 checkpoint: V30 immutable optimization audit; V31 forward-only rejected-candidate nullable-score fix. Clean V1→V31, previous-latest V30→V31 and populated V29→V31 + Flyway validate PASS, 369/368 tests with 105 PG. V1–V30 unchanged; V32+ was next.
- Earlier per-slice entries below are historical checkpoints; their latest/next-version notes applied only at that checkpoint. Current latest is V35 and the next unused version is V36+.
- Latest verified V29 superseded DRIVER_SETTLEMENT snapshot immutability; V28 adds audited recalculation/driver billing adjustment evidence. Final-source clean V1→V29, populated V27→V29 batch and previous-latest V28→V29 + validate PASS. V1–V29 checksums unchanged after application. Next V30+.
- Latest verified V27 signed billing chains/credit caps/immutable evidence/charge claims/issued-line move guard; clean/previous-latest upgrade/validate PASS. Next V28+, never edit V1–V27.
- Latest verified V26 PRIMARY accepted rating/financial command audit/tax/history constraints and exact numeric invoice persistence. Clean V1→V26 + populated V25→V26 + validate PASS. Next V27+, never modify V1–V26.
- Latest verified V25 immutable invoice tax assessments; clean V1→V25 + populated V24→V25 + validate PASS. No historical invoice data changed. V1–V24 hashes unchanged; next unused migration V26+.
- Latest V24 accepted immutable rating snapshot/JSONB/date/audit/rule/result/replay/correction guards; clean V1→V24, populated V23→V24 and validation PASS. V1–V23 SHA-256 unchanged; V24 unchanged after its first gate. Next unused migration V25+. No production database or original source clone modified.
- Latest V23 adds immutable contract Load mileage evidence/context/precision guards; clean V1→V23 and populated V22→V23 + validate PASS. V1–V21 hashes unchanged, V22 remains its verified applied version. Next migration V24+.
- V22 adds audited Load pickup business DATE and correction chain without rewriting appointment TIMESTAMPTZ or historical data. Verified V1–V21 hashes remain unchanged. Both clean latest and populated V21 upgrade validate. Next migration V23+.
- No new migration or applied migration modification in Task 3.3.
- User-confirmed V2–V7 applied baseline preserved; existing repository chain V1–V12 migrated/validated on disposable PostgreSQL during regression.
- V9 cost/accessorial, V10 policy/settlement and V11 payroll schemas already exist. V12 is occupied by the audit-column correction; V13 adds settlement idempotency, workflow/history guards and explicit trip-only attribution; V14 adds neutral payroll configuration/availability/snapshots/source claims; clean and populated V13→V14 upgrade verified. V15 protects immutable locked payroll history, source claims and snapshots; clean and populated V14→V15 verified. V16 adds immutable persisted payslip PDF/checksum/audit; clean and populated V15→V16 verified. V17 adds immutable payment attempts/provider/source/destination snapshots and active-attempt guard; V18 adds immutable verified payment event journal and V19 audited manual bank reconciliation. V20 now migrates legacy PAID runs to COMPLETED and adds audited NO_PAYMENT_REQUIRED; next unused migration is V21.
- Production tenant migration histories were not changed or independently queried.
- Historical V21 added rating authoring tables/guards; clean V1→V21 and populated V20→V21 clone + validation PASS. Original V20 source DB remains read-only; its historical backfill was not rerun. Latest active migration is V35 as recorded above.

## PostgreSQL Integration

- Final current matrix adds 22 Fleet methods to prior 138, total 160 PostgreSQL methods; all execute, zero PG skips/H2 replacement. Earlier entries below describe historical checkpoints.
- Historical Phase 7 matrix: 40 baseline + 6 rating policy + 7 Load date + 5 mileage + 3 preview + 6 accepted snapshot + 4 tax + 23 billing + 11 optimization audit + 11 qualified inputs + 11 run + 11 acceptance = 138 executed PostgreSQL methods; final Fleet adds 22 = 160, zero PostgreSQL skips. DB semantics and physical tenant isolation run on real PostgreSQL, not H2. Existing routing-only H2 unit fixtures remain unchanged and do not replace this coverage.
- 40 baseline `CostLedgerPostgresTest` plus six `RatingPolicyPostgresTest` methods ran on PostgreSQL 16. No integration skip or H2 substitution for these behaviors. New cases cover full decimal/audit preservation, immutable SQL history, concurrent append, invalid methods/contract identity, authoring role/actor, real resolver/date boundaries/customer filtering and overlapping-version ambiguity.
- Preparation used a populated V20→V20 clone for regression/checksum validation; it did not rerun historical backfill on the source. Existing clean V1→V20 and populated V19→V20 upgrade evidence is retained from baseline; a future V21 batch must prove clean V1→V21 and populated V20→V21 separately.

## Blocked

- BILL-DEC-001…006 gates are RESOLVED by explicit locked user decisions and verified 6G implementation. Details: docs/invoice-rating-v1-contract.md. Single-invoice consumers were adapted together with forward billing schema; no hidden tax/default or historical reclassification.
- RATE-DEC-001…007 are CONFIRMED; initial BLOCKED_BY_BUSINESS_DECISION is resolved. User confirmed V1 FLAT/PER_MILE linehaul plus INDEX_BASED_MPG FSC. Other methods are explicitly deferred, not pending decisions for this release.
- Load-level planned/actual mileage/attribution sources remain absent or unqualified in V20. This is a required fail-closed outcome for those bases, not permission to infer Trip allocations; CONTRACT_MILES must record effective agreement provenance.
- Pickup business DATE semantics confirmed and adapter implemented. Historical NULL dates intentionally reject rating until explicit evidenced remediation; no automatic backfill.
- Phase 7 dependency, business, implementation and verification gates PASS. All OPT-DEC-001…010 locked. No Rating/Billing/OPT choice reopened. Source qualification and explicit policy publication are runtime configuration requirements.
- Phase 8 semantic gate RESOLVED: all required FLEET V1 choices CONFIRMED/LOCKED, implementation and final whole-system gates COMPLETE at V35. No business decision blocker. Unsupported health UNAVAILABLE already approved.
- Production statutory/provider adapters remain unconfigured as previously documented; no prior phase is reopened by this preparation.

## Business Decisions

Additional BILL-DEC-001…006 CONFIRMED/LOCKED: docs/invoice-rating-v1-contract.md. BILL-DEC-005 allows explicit multiple issued partial-credit evidence IDs proving full subtotal/tax reversal before rebill. BILL-DEC-006 rejects stale revenue at approve/lock and requires explicit recalculate/reapprove. No remaining requested billing choice is assumed or pending.

OPT-DEC-001…010 CONFIRMED/LOCKED: trusted sources, explicit allowlists, 72h horizon, per-source freshness/units; exact four curves, .35/.30/.25/.10 weights, DECIMAL128/HALF_EVEN with scale6 utilities/weights and scale8 contributions/score, DENSE_RANK. Complete candidate ESTIMATE/APPROVED costs and accepted pre-tax revenue. Accept assigns existing Trip only, no dispatch. Exact contracts: docs/optimization-policy-decisions.md.

Canonical register: [docs/rating-policy-decisions.md](docs/rating-policy-decisions.md). User/business owner confirmed all seven on 2026-10-04.

| ID | Status | Decision |
|---|---|---|
| RATE-DEC-001 | CONFIRMED | Smaller explicit priority wins, winning tie ambiguous; requested pickup business LocalDate, inclusive effective dates; exact matching/non-null dimensions and null wildcards |
| RATE-DEC-002 | CONFIRMED | Component/contract-specific CONTRACT/PLANNED_LOAD/ACTUAL_LOADED/ACTUAL_ALL miles; explicit load attribution and provenance; MINIMUM_CHARGE NOT_APPLICABLE; canonical MILE |
| RATE-DEC-003 | CONFIRMED | EIA weekly on-highway ULSD USD/gallon including taxes; explicit supported region; latest observation <= pricingDate; explicit maxIndexAgeDays and stale/unavailable errors; accepted input freezes revisions |
| RATE-DEC-004 | CONFIRMED | Positive contractMpg/nonnegative baseFuelPrice on versioned Rate/FSC policy; explicit units/currency; no fallback |
| RATE-DEC-005 | CONFIRMED | RatingPolicyV1 DECIMAL128; FSC unit rate 6/HALF_UP, approved currency boundaries HALF_UP, subtotal sums rounded lines, min/max before rounding, no pre-tier mileage rounding |
| RATE-DEC-006 | CONFIRMED | Preview ephemeral; acceptance immutable; invoice references accepted snapshot; append-only superseding corrections; issued history protected |
| RATE-DEC-007 | CONFIRMED | PRIMARY uniqueness per tenant/load/customer/currency, linked supplemental/credit/rebill; operation-scoped key + normalized-input hash replay/conflict |

## Next Task

- Approved backend Phases 0–8 and final gates COMPLETE. Hand off verification artifacts; no repeated RATE/BILL/OPT/FLEET question. Source/provider production publication/capture and deployment/release review are separate scope and not performed.
- Preserve all unrelated/staged work. Do not edit V1–V35 or source historical backfills; new migration V36+ only, no return to completed phases absent a proven defect.
