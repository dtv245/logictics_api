# Backend remediation verification

Status: BACKEND_IMPLEMENTED_AND_LOCALLY_VERIFIED; RELEASE_COMPLETION_GATE_NOT_MET.
The user-provided replacement plan governs this work.
Historical claims in `logs-debug.md` and the original plan are retained as history;
they do not certify the current source or running image.

## Baseline, 2026-10-06

- Source: main, `0c795f89b7005484abcd0aff3ea13aa9cdeb1fdb`, dirty workspace.
- Private preservation archive and full file/hash manifest: `/tmp/logisticsx-remediation-baseline-lrvbsgns`.
- Dedicated PostgreSQL 16 server: `codex-remediation-d1f23c084d65`, loopback port 32768. Working PostgreSQL and application containers untouched.
- V1–V35 historical SHA-256 manifest: PASS. V36/V37 are preserved exactly.
- First full run: `/tmp/logisticsx-regression-qyrzsil9/maven.log`, 501 executed, zero skipped, one failure and four errors. The previously skipped PostgreSQL suites exposed Load creation/audit ID loss after JPA merge, and a forecast route shadowed by an ADMIN matcher.
- Repairs: use the managed result of `saveAndFlush` during Load creation; constrain the qualified-input ADMIN matcher to its authoring route. Targeted 18-test PostgreSQL rerun: PASS, `/tmp/logisticsx-remediation-tests-bkc1zexm/maven.log`.
- Report verifier: zero skips required; CurrentUser PostgreSQL suite added to retained minimum coverage. Python report-gate tests: 17 PASS.
- Added PostgreSQL full-context/Flyway/schema validation and populated V35/V36/V37 upgrade verification. Testcontainers PostgreSQL 16 is used when no disposable TASK_DB connection is supplied.
- Full repaired baseline: PASS, `/tmp/logisticsx-regression-v9gc2a3b/summary.json`: 503 executed, 168 PostgreSQL, zero skipped/failures/errors; clean V1→V37 and populated V35/V36/V37 upgrades retain stored checksums and data.
- Runtime read-only confirmation: unchanged container `8347964b0323…`, image `sha256:577b13bd…`, anonymous Payment GET 200; source/runtime drift remains confirmed.

## Release gates

No commits, application restart, production migration, Docker application rebuild or deployment have been performed. Runtime alignment and frontend auth/key/version readiness remain separate release gates. Checkstyle, SpotBugs, ArchUnit and Failsafe remain NOT CURRENTLY CONFIGURED.

## Phase 1 / 2 evidence

- Phase 1 full security gate: PASS, `/tmp/logisticsx-regression-vtmw_zi5/summary.json`, 511 executed / 173 PostgreSQL, zero skips/failures/errors. Protected anonymous requests return normalized 401; wrong roles 403. Method/path public allowances, method security, configured-origin CORS, expired/malformed JWT and two-tenant fail-closed routing exercised. Unsigned public provider callback still rejects 403 through its verifier.
- Phase 2 targeted membership/selector/spoof/removal/two-physical-tenant tests: PASS, `/tmp/logisticsx-remediation-tests-o81xtn2m/maven.log`, 12 methods.
- BUG-BE-NEW-002 signing-key fix approved by user and implemented; test-only signing key is excluded from runtime resources. Full authorization/signing-key gate PASS, `/tmp/logisticsx-regression-tb4amqsi/summary.json`: 520 executed / 178 PostgreSQL, zero skips/failures/errors.

## Phase 3 evidence

- Ten actual-controller PostgreSQL financial scenarios PASS in `/tmp/logisticsx-remediation-tests-sd8gix1a/maven.log`: sequential/concurrent replay, meaningful-input conflicts, competing invoice reservations, metadata/cancel preservation, SQL history guards, atomic rollback, stale persistence context, independent tenant keys, and populated V37 legacy upgrade. Full financial gate PASS, `/tmp/logisticsx-regression-pdlhbokc/summary.json`: 530 executed / 188 PostgreSQL, zero skips/failures/errors.
- V38 applied to disposable fixtures and frozen: SHA-256 `427c73b9741af59ff4ce1b5cfd42a42126b9860320a5ee07d99989d42823951e`.
- Financial upgrade fixtures now retain legacy invoice/payment identity and amounts across V35/V36/V37 upgrades, without manufactured audit events or status/hash reclassification.
- Additional finding: existing shared JPA audit columns accept at most 50 characters while the auditor stores principal names (email). A longer test email reproduced rejection. Fixture names now fit the current schema; broad audit-identity redesign remains a separate candidate, not a silently changed financial convention.

## Client inventory

Read-only discovery found the adjacent `/home/vumoi/logictics-app` client. Its API interceptor attaches bearer tokens, but the inspected core type/form/provider contracts contain no `expectedVersion` or Payment `idempotencyKey`. Frontend readiness is unverified and remains an enforcement rollout gate. No frontend files changed.

## Phase 4 evidence

- Separate flat Load/Trip/Truck update DTOs require a nonnegative `expectedVersion`; responses expose `version`. The server compares the caller version and flushes updates before mapping. Load's pessimistic lock and pickup-date command/audit remain intact.
- Named graph analysis plus SQL literal audit identified one production JDBC core writer: optimization acceptance now increments Trip version while holding its existing resource locks. No new version column or repeated V37 migration.
- Targeted 22 PostgreSQL methods PASS, `/tmp/logisticsx-remediation-tests-ub79y3t9/maven.log`: sequential stale forms, actual overlapping Trip HTTP transactions, independent persistence contexts for all three tables, HTTP validation and role/tenant precedence, retained pickup-date chain, and optimization assignment/version behavior. Full populated V37→V38 gate PASS, `/tmp/logisticsx-regression-secvpt_7/summary.json`: 534 executed / 192 PostgreSQL, zero skips/failures/errors.

## Phase 5 evidence

- Behavioral Load/Trip paging/detail/actual-controller tests PASS, `/tmp/logisticsx-remediation-tests-dpdynbdo/maven.log`. Diverse distinct relations and nullable assignments, 25-record fixtures, full 5/20-row pages, totals/sort/last-page behavior, closed service transaction and detached serialization tested with Hibernate statistics.
- At most two selects per full page; one per detail; serialization produces zero additional statements. Existing transactions and to-one graphs retained; OSIV stays disabled. No production fetch rewrite justified.
- Full clean V38 gate PASS, `/tmp/logisticsx-regression-q_fys9sm/summary.json`: 537 executed / 195 PostgreSQL, zero skips/failures/errors.

## Phase 6 evidence

- All JSON body controllers have `@Valid`; raw signed payroll callback bytes remain unchanged. Added cascaded billing generation/tax/credit lines, optimization candidate/source/weight/forecast records, rating and pickup-date provenance, and conditional Lark callback validation. Service checks retained; optional rating selectors remain optional.
- Targeted actual HTTP/controller/callback tests PASS, `/tmp/logisticsx-remediation-tests-js30zlro/maven.log`, eight methods including five PostgreSQL methods. Nineteen command routes verify missing fields, plus nested paths, negative credit/weight, malformed JSON, 401/403 precedence, request IDs, no-write counts, domain currency 422, optional selectors and provider-error callback compatibility.
- Missing primitive policy versions are rejected during JSON conversion as normalized `MALFORMED_REQUEST` 400; constructible incomplete DTOs return `VALIDATION_FAILED` 400 with field paths. Full gate PASS, `/tmp/logisticsx-regression-dof3sm32/summary.json`: 542 executed / 200 PostgreSQL, zero skips/failures/errors.
- Phase 7 preparation reproduced the current NoDB startup failure: packaged application cannot instantiate LarkAuthService without EmployeeRepository (`/tmp/logisticsx-nodb-before.log`). This is part of BUG-BE-0007 profile verification; running application unchanged.

## Phase 7 / 8 evidence

- Added actual NoDB context coverage, separate registry/two-tenant startup with Boot Flyway enabled, fail-closed JWT datasource registration, and stop-on-failure migration batches. Missing/explicit/disabled signing-key property binding is tested. NoDB excludes persistence and domain beans; its health endpoint reports liveness without database readiness certification.
- A final default-query HTTP regression exposed PostgreSQL `lower(bytea)` on omitted Load/Trip search. Explicitly typing those nullable HQL parameters corrected both paths. Four behavioral persistence tests and three tenant-startup tests PASS, `/tmp/logisticsx-remediation-tests-r2ohk7eu/maven.log`. Fetch graphs/query bounds/OSIV remain intact. The corresponding Customer default-list defect is a separately recorded finding.
- Full clean V1→V38 gate: `/tmp/logisticsx-regression-7luf4e52/summary.json`, **551 reported and executed / 204 PostgreSQL / zero failures, errors or skips**. Initial artifact checking assumed the older BOOT-INF metadata layout; the verifier now supports the actual Boot 4 root META-INF layout and rejects missing/ambiguous metadata. Unchanged source hashes, all reports, schema history and packaging were revalidated without altering the JAR.
- Full populated V37→V38 clone gate: `/tmp/logisticsx-regression-f4xkwruh/summary.json`, **551 executed / 204 PostgreSQL / zero failures, errors or skips**. The source database was not migrated. Retained V35/V36/V37 populated upgrade fixtures preserve financial data and stored migration checksums; Hibernate schema validation and Flyway validation PASS.
- Python report/artifact/OpenAPI verifier regressions: **26 PASS**. Missing operations/key/version contracts, schema drift and server normalization are tested. CI now requires the isolated PostgreSQL zero-skip gate; remote GitHub execution has not been performed.
- The final executable JAR was copied to private diagnostics and started only against a new dedicated PostgreSQL database. `/tmp/logisticsx-artifact-k1lthwt3/summary.json`: packaged build identity, actual ADMIN endpoint, anonymous Payment/Customer/Message/internal build 401, required cancel/delete and mandatory key/version OpenAPI contracts, Flyway V1–V38, JPA validation, NoDB startup without a key and missing-key enabled-auth startup rejection all PASS. This is `LOCAL_JAR`, `deployment_verified: false`.
- Final source SHA-256: `d6985dde38ede21d47e7001f8692a78f49217f53f66a7ffeee5b99b04d2a7700`. Final JAR SHA-256: `7a7a83e1b69d6e4dfa45613c62deb2a0a1fcb210c286cdab916051c7e0304fbd`. Runtime build ID: `remediation-b5b4c5d2a16542289e2e78f39710ccdf`, dirty marker true. Canonical OpenAPI SHA-256 (servers normalized): `2d10d31b8c7a013fa10aca92b5cf3411cadf8dcd5a1ca88f540201b3771e7774`, 140 paths. Clean and upgrade builds share the verified source hash and canonical contract; their distinct build IDs/JAR hashes are recorded separately.
- V1–V35 historical SHA manifest PASS; V36/V37 frozen hashes match the plan; V38 remains unchanged after application. Full current manifest: [migration-sha256-v38.txt](migration-sha256-v38.txt).
- Fresh GitNexus index: 13,457 nodes / 35,025 edges / 864 flows. Full structured tracked-diff analysis: 477 symbols, 327 affected flows, CRITICAL risk, no partial/truncated result. Whole-flow discovery itself reports dropped candidates/branches; untracked additions are outside `git diff` mapping. These are explicit graph limits, not an all-clear. Actual security/domain/API/PostgreSQL regressions cover the known affected workflows. No commit performed.
- Private dirty-work manifest comparison: all 961 original snapshot entries still exist; 937 byte-identical, 24 intentionally edited within remediation scope (including the appended historical audit update). Original archive, full Git status and diagnostic resources are retained. No reset/clean of user files, broad staging, frontend edit, working DB write, production secret change, application image rebuild/restart or deployment occurred.
- Final read-only check confirms the original running container/image/start timestamp are unchanged and anonymous Payment remains 200. Source fixes do not close that deployed security exposure.

Machine-readable results: [backend-remediation-final-evidence.json](backend-remediation-final-evidence.json).
Client contracts, rollout/rollback and separate findings: [remediation-release-gates.md](remediation-release-gates.md).

## 2026-10-07 authentication review checkpoint

Review reproduced four failures in eleven targeted tests: a unit signed
identity/tenant mismatch and three actual PostgreSQL HTTP bootstrap/tenant-state
failures. Callback employee/role lookup now uses the server-configured active
tenant, rejects conflicting bound/principal tenants before external calls,
fetches the role inside persistence, and restores request context. Cached
datasources recheck active registry membership; registry failures return a
sanitized 503. Client tenant selectors cannot choose the login tenant.

Clean V1→V38 and populated V38 clone regressions each passed 556 executed Java
tests / 207 PostgreSQL methods / zero failures, errors or skips. Retained suites
also exercise populated V35/V36/V37 upgrades to V38. Python: 26 PASS. The exact
packaged local JAR passed PostgreSQL/Flyway/JPA/build/OpenAPI/security, NoDB and
missing-key startup checks. Dated checkpoint:
[backend-remediation-review-evidence-20261007.json](backend-remediation-review-evidence-20261007.json).

The earlier private October 6 diagnostics disappeared after an external
environment restart; their recorded JSON/handoff evidence remains historical.
Fresh preserved-work snapshot and labelled disposable PostgreSQL resources are
recorded in the checkpoint. Read-only working-runtime inspection confirms the
same old image, V35, 138 paths and anonymous Payment 200. Its container restart
timestamp changed externally; this task did not restart it. See
[runtime review](remediation-runtime-review-20261007.json).

The user subsequently approved the Customer default-list and SETTLED reporting
follow-ups in [reviewed proposals](backend-follow-up-proposals.md). Their seven
new PostgreSQL tests reproduced six failures before source changes. Their final
verification follows. This dated authentication checkpoint does not certify
later source edits.

## 2026-10-07 approved follow-ups and final review gate

- Customer name/email search parameters now have explicit JPQL string casts.
  Actual HTTP verifies omitted/empty/case-insensitive name/email searches,
  status filters, sorted pagination/totals and two physical tenant databases.
- Both paid/open-balance classifiers include case-insensitive SETTLED. A valid
  USD 100 invoice with a USD 40 SETTLED payment now reports paid 40 and open 60.
  Existing successful statuses, unpaid PENDING/CANCELLED/VOID, currency rejection
  and role/tenant boundaries are retained. Valid reconciled legacy invoice lines
  and payment rows are inserted at V37, upgraded to V38 and reported without
  rewriting their status, amount, identity, hash or fabricating audit events.
- Targeted retained/new tests: 46 PASS. Same-source full clean V1→V38 and
  populated V38 clone→V38 runs each passed 563 reported/executed Java tests,
  including 214 PostgreSQL methods, zero failures/errors/skips. Retained full
  suites also verify populated V35/V36/V37→V38 upgrade fixtures. These explicit
  fixture paths are distinct from the latest full V38 clone run.
- Python verifier regressions: 26 PASS. Historical V1–V35 and frozen V1–V38 SHA
  manifests, Flyway stored checksums, Hibernate validation and whitespace PASS.
  No migration change was needed for either newly approved follow-up.
- The verified immutable local JAR passed packaged identity, required OpenAPI,
  anonymous protected 401, real PostgreSQL/Flyway/JPA, NoDB without a key and
  missing-enabled-auth-key rejection. It is `LOCAL_JAR`, deployment false.
  Source SHA-256: `b69beb469fda434309c8b3f8f9d7135142872c9c563ffbc890d08e191d3b4795`.
  Executed JAR SHA-256: `aa685a2f8e159f99a44ef1d85a27a491cbafabaec9aa933780469c2bde2b40c2`.
  Canonical OpenAPI remains 140 paths / SHA-256
  `2d10d31b8c7a013fa10aca92b5cf3411cadf8dcd5a1ca88f540201b3771e7774`.
  Clean/clone artifact identities and private copies are recorded separately.
- Fresh full graph: 13,523 nodes / 35,249 edges / 864 flows. Structured tracked
  change analysis: 497 symbols / 328 affected flows / 159 files, CRITICAL risk,
  no partial/truncated result. Whole-flow discovery drops 611 entry candidates,
  1,992 callees and nine budget-limited walks; untracked additions are outside
  `git diff` mapping. Those limits do not establish absence of other workflows.
- Backend source is ready for review. Remote CI, production key provisioning,
  frontend key/version/auth readiness and a reviewed secure image/tenant rollout
  remain unverified. Failsafe, Checkstyle, SpotBugs and ArchUnit remain
  NOT CURRENTLY CONFIGURED. Shared audit principal length and opaque Payment
  UUID/client provenance remain separately scoped findings.

Latest machine evidence:
[backend-remediation-followup-evidence-20261007.json](backend-remediation-followup-evidence-20261007.json).
Earlier dated checkpoints remain unchanged. No commit, frontend edit, working DB
write, production secret change, application image rebuild/restart or deployment
occurred. All original preserved-work snapshot entries remain present; final
byte comparison is recorded in the machine evidence.

## Bug state and completion gate

| Record | Backend evidence | Overall current status |
|---|---|---|
| BUG-BE-0001 | Actual JWT, endpoint/role/CORS/tenant 401/403 regressions and executable anonymous 401 | FIXED_NOT_VERIFIED for deployed runtime; existing anonymous Payment exposure open |
| BUG-BE-0002 / CANDIDATE-004 | Mapped identity, membership, self selectors, spoof, removal and tenant-chat role regressions | FIXED_NOT_VERIFIED pending aligned rollout |
| BUG-BE-0003 / CANDIDATE-006 | Mandatory key/invoice, serialization, replay/conflicts, reservations, lifecycle/audit and SQL guards | FIXED_NOT_VERIFIED pending schema/client/runtime gates |
| BUG-BE-0004 | Real closed-boundary/query-count/pagination/default-search verification | FIXED_NOT_VERIFIED pending aligned runtime read verification |
| BUG-BE-0005 | Required expectedVersion, actual concurrent API/stale persistence contexts and JDBC version advancement | FIXED_NOT_VERIFIED pending client and runtime gates |
| BUG-BE-0006 | Actual HTTP/nested validation, errors/requestId, authorization precedence, no-write regressions | FIXED_NOT_VERIFIED pending aligned runtime |
| BUG-BE-0007-A / NEW-001 | PostgreSQL/Flyway/JPA clean/upgrade/context and zero-skip CI configuration | Backend locally verified; remote CI execution unverified |
| BUG-BE-0007-B / CANDIDATE-001 | Packaged identity and disposable executable verified; release mismatch guards tested | OPEN deployed runtime drift; no application rollout performed |
| BUG-BE-NEW-002 | User-approved default removal, key checks, disabled-auth and actual startup regressions | FIXED_NOT_VERIFIED for existing runtime key/image |
| BUG-BE-NEW-004 | User-approved SETTLED paid/open-balance correction; real HTTP, currency, role/tenant and legacy V37→V38 history preservation | FIXED_NOT_VERIFIED pending aligned runtime |
| Customer default-list follow-up | User-approved nullable query casts; actual PostgreSQL default/search/pagination/tenant tests | FIXED_NOT_VERIFIED pending aligned runtime |

Backend implementation and local verification are complete. The plan's final
release completion gate is **NOT MET**: existing runtime exposure/drift, frontend
key/version/auth readiness, secret provisioning, reviewed artifact rollout and
separate candidate audits remain outstanding. No production risk acceptance or
deployed FIXED_VERIFIED status is inferred. Historical audit entries are retained.
