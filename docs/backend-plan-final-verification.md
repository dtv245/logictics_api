# Backend convention plan — final verification

Date: 2026-10-05 (Asia/Ho_Chi_Minh). Verdict: **PLAN COMPLETE — approved backend
Convention V1 scope, Phases 0–8**. This is not a production deployment/readiness,
frontend E2E or live external-provider certification.

## Authority and scope

All RATE/BILL/OPT decisions and applicable FLEET-DEC-001…009 are CONFIRMED/LOCKED.
Fleet downtime/breakdown definitions are NOT_APPLICABLE to the explicitly authorized
V1 unsupported-source UNAVAILABLE outcome. Published exact status mappings, source
registrations and qualified inputs are runtime authoring requirements, not hidden
defaults or unresolved business choices. Completed Phases 0–6 were preserved, not
reimplemented or re-audited. Current source wins over historical proposal examples.

## Final same-code migration / regression matrix

| Gate | Database / diagnostics | Result |
|---|---|---|
| Clean V1→V35 | `codex_regression_20261004183625270573`; `/tmp/logisticsx-regression-uxlpmxcs` | PASS |
| Populated previous-latest V34→V35 | `codex_regression_20261004184241080791`; `/tmp/logisticsx-regression-ndxdre_c` | PASS |
| Upgrade source | `codex_regression_20261004182505410806`, verified V34; cloned, never migrated/repaired | Unmodified |
| Flyway validate / history | Both final runs successfully validate and contain exactly V1–V35 | PASS |
| Earlier migration checksums | V1–V34 unchanged; V35 unchanged since its first application | PASS |
| Maven build / configured quality gates | Both runs execute `./mvnw -q clean verify`, compilation, full tests and executable Boot JAR repackage | PASS |

Each final run: **456 reported = 455 executed + 1 named legacy skip**, 0 failures,
0 errors; **160 PostgreSQL methods** and 295 executed non-PG unit/API/HTTP methods.
The sole skip remains `LogicsticApplicationTests.contextLoads`; no PG test is skipped.
Python verifier tests: **17 PASS**, separate from the Java totals.

Earlier per-batch proofs are retained in [progress](../plan-progress-summary.md):
V30/V31, V32 and V33 clean/previous-latest upgrade; V34 clean/V33 upgrade; V35 clean/
V34 upgrade. Applied files were not repaired, renumbered or rewritten. V31 and V35
are forward-only corrections with regression tests. Next schema version: V36+.

The regression runner only creates disposable local PostgreSQL databases/clones and
retains logs and `summary.json`; source databases are never changed. Integration
tests also retain their isolated tenant test databases; no user DB was deleted.

Checksum artifact: [migration-sha256-v35.txt](verification/migration-sha256-v35.txt).
Recheck with `sha256sum -c docs/verification/migration-sha256-v35.txt`.

## Cross-domain evidence

| Flow / invariant | Executed evidence |
|---|---|
| Load→Rating→Invoice→signed revenue | RatingPolicy 6 PG; Load business date 7; mileage 5; preview 3; snapshot 6; TaxAssessment 4; BillingPrimary 23. Exact priority/ambiguity, independent DATE without backfill, provenance, immutable acceptance, tax audit, PRIMARY uniqueness, signed billing chain, credit caps/rebill, retry/concurrency and currency guards. |
| Trip→assignment→Settlement→Payroll→real payment outcome | Existing 40 CostLedger PG methods plus calculation/payment unit suites all green. Pre-lock revenue drift rejects; explicit recalculate/reapprove; post-lock adjustment preserves settlement/payroll/payslip history. Run COMPLETED iff all items terminal; audited NO_PAYMENT_REQUIRED creates no fake payment. |
| Real load/driver/truck/HOS/accepted revenue/approved forecasts→optimization→Accept | 44 optimizer PG methods across audit, qualified inputs, run and acceptance; 76 optimizer unit/HTTP/scope methods. Hard gates before exact scoring; source freshness/unit/context, complete required forecast coverage, approved curves/weights/precision, persisted contribution reconciliation and equal dense ranks. Accept atomically assigns existing Trip only, never dispatches/creates Trip; stale/retry/concurrent resources and physical tenant isolation verified. |
| Actual truck history→fleet report/dashboard backend contract | 22 Fleet PG and 12 Fleet unit methods. Immutable versioned maps, bounded actual intervals, entry/exit, gaps/open validity, duplicates/chronological order/conflicts/corrections, productive-outside-capacity rejection, strict coverage, duration-sum ratio, explicit ZoneId/DST/numeric boundaries, proven completion attribution and excluded-mileage denominators. Read-only GET writes no snapshot/event. |
| PostgreSQL-owned invariants / tenant boundary | Actual PostgreSQL 16, Flyway, JSONB, FK/check/partial uniqueness, append-only guards and concurrent transactions; physical tenant database tests for financial, optimization and fleet flows. H2 is not substituted for any of the 160 PG methods. |

Per-suite PG counts are enforced independently: 40 baseline + 6 policy + 7 date +
5 mileage + 3 preview + 6 snapshot + 4 tax + 23 billing + 4×11 optimizer + 22 fleet
= 160. Extra unit tests cannot hide a missing PG domain suite. The incoming minimum
302/301 Java and 94 PG baseline is preserved and increased, not reduced.

## Runtime API and build confirmation

The final executable `target/logicstic-1.0.0.jar` was started on loopback only using
the disposable verified V35 database, JPA validation and Flyway validation enabled,
tenancy/external Lark integrations disabled for this local check. Actual
`GET /v3/api-docs` returned OpenAPI **3.1.0**, **134 paths**, **283 schemas**; all 16
required rating/billing/optimizer/fleet run, acceptance, capture and report operations
were present. The verification process was stopped after the read-only check; no
financial command, external transfer or production deployment occurred.

Real application-context HTTP integration tests verify existing roles, authenticated
tenant actors, errors and response envelopes; OpenAPI discovery is not claimed to
replace those authorization/ownership tests. The executable manifest uses Boot's
JarLauncher and `com.company.logicstic.LogisticApplication` (Java 21 / Boot 4.1.0).

No Checkstyle, SpotBugs, ArchUnit, PMD or Failsafe gate is configured in the active
root Maven build/CI. None is falsely reported as executed or silently disabled.
Coverage percentage is not invented; branch/reconciliation/immutability/concurrency
tests supply the actual correctness evidence. Graph impact remains UNKNOWN because
of the documented graph storage/runtime mismatch; source caller checks and the full
regression suite were used, never an unsupported graph all-clear.

## Approved availability and operational boundaries

- Missing/unqualified historical dates, miles, provider inputs, forecasts or source
  coverage fail closed with explicit domain reason; no financial/forecast/KPI zero
  is manufactured. Rating/optimizer/Fleet have independent explicit numeric policies.
- Fleet health returns null/UNAVAILABLE for unproven downtime intervals, historical
  PM due occurrences, complete qualified maintenance cost coverage and breakdown
  classification. A qualified subset of Phase 3 ledger projections does not prove
  complete historical coverage. No every-ticket breakdown, guessed currency or
  records+expense+ledger double counting is introduced.
- No old TIMESTAMPTZ→DATE, current truck status→availability, mutable truck FK→history,
  synthetic event, 24h calendar, multi-load mileage split or planned→actual backfill.
- Snapshot acceptance, signed invoicing and resource acceptance remain idempotent;
  immutable accepted/locked/issued history is not mutated by later policies/reports.
- No production TODO/FIXME exists under `src/main/java`; verifier `return 0` is its
  successful process exit code, not a domain placeholder. No new hidden rate, MPG,
  index, route speed, weight, status or financial rounding default was introduced.
- Qualified production endpoint configuration, source publication/capture, provider
  credentials, deployment/rollback/security/performance release review are separate
  operational work. No automatic policy seed, remote account write, commit or
  production migration is authorized/claimed by this backend completion.

## Final housekeeping

`git diff --check`: PASS, no warning/error. Earlier migration SHA verification: PASS.
Workflow memory validator: PASS; prior role handoffs retained unchanged. Unrelated
dirty/staged user work is preserved. No unresolved required Rating/Billing/
Optimization/Fleet V1 business decision or backend implementation gate remains.

Reproduce: `python3 scripts/verify_backend_regression.py`, then
`python3 scripts/verify_backend_regression.py --upgrade-from <verified-disposable-V34-db>`;
`python3 -m unittest discover -s scripts/tests -v`; checksum command above;
`git diff --check`. The approved convention backend plan is complete; broad
production-readiness remains a separate verdict.
