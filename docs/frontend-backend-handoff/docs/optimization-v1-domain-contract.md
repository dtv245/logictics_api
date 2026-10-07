# Dispatch Optimization V1 — implementation contract

Business authority: all OPT-DEC-001…010 in optimization-policy-decisions.md are
CONFIRMED/LOCKED. This file describes implementation, not new policy defaults.

## Scoring

An explicit published policy pins the exact four curves, .35/.30/.25/.10 weights
and OPT_NUMERIC_V1 DECIMAL128/HALF_EVEN rules. Utilities/weights have scale 6;
contributions and final score have scale 8. Final score sums stored contributions.
Equal scores have DENSE_RANK; UUID sorting is presentation only. The confirmedV1
factory describes the approved contract; it must never resolve a missing policy.

## Qualified evidence and hard feasibility

The candidate identity is Load + existing Trip + Driver + Truck, with the run's
Instant planning interval. Eligibility is OPT_ELIGIBILITY_V1, 72 hours from run
creation. All entity status allowlists and qualified source registrations must be
explicitly authored; the code contains no OPEN/ACTIVE candidate default.

Every input carries source type/reference/version, candidate context, evidence
reference/version, original unit/value, observedAt and explicit expiry/maxAge.
Endpoint freshness caps do not extend an earlier provider expiry. Location and
HOS use 5 minutes; traffic-aware route/ETA uses 15 minutes; other inputs require
their own source validity. Driver dynamic evidence is limited to 5 minutes.
All conversions retain original facts: 1 mile = 1.609344 km and 1 pound =
0.45359237 kg. Decimal arithmetic only; no route/geodesic approximation.

The hard gate collects safe independent rejection reasons before any scoring.
Full HOS is a port to a qualified subsystem and binds the exact simulated route
plan/version. Drive, duty, break, cycle, service and next-available checks must
all pass. Utility headroom is supplied by that full simulation, never calculated
from a cached remaining-drive number. A missing/unconfigured trusted provider
cannot be represented as a successful feasibility result.

## Contribution forecast

Revenue is the selected accepted rating subtotal before tax. Required approved
candidate-specific ESTIMATE costs: FUEL, DRIVER, TOLL; ACCESSORIAL/PERMIT when
explicitly applicable. There is no inferred conditional applicability or FX.
Cost identities are deduplicated; missing required coverage excludes the candidate.
Zero costs require ZERO_COST_CONFIRMED with actor/time/reason, in addition to
source/version/asOf/category provenance. Actual accounting/history is never edited.

## Acceptance and unfinished work

Accept assigns resources to the existing Trip; it neither creates a Trip nor
dispatches. Revalidation, atomic resource concurrency, idempotency and immutable
audit persistence are required before this phase can be marked COMPLETE.
The domain/audit/source-capture slices alone do not satisfy those gates. Clean V32
and previous-latest V31→V32 upgrade are verified with 380 reported/379 executed
tests, 116 PostgreSQL methods and one legacy skip; workflow wiring remains IN_PROGRESS.

## Authoritative candidate source capture (V32)

POST /api/optimization/qualified-inputs captures capacity/qualification with ADMIN
authority. POST /api/optimization/qualified-inputs/forecasts permits existing
ADMIN/ACCOUNTANT roles, but only FORECAST_COST inputs. Read permits existing
ADMIN/ACCOUNTANT/DISPATCHER roles. Actor is resolved from authenticated tenant employee.

Each immutable record binds explicit published policy, real Load/Trip relationship,
Driver/Truck, registered source/version, unit, as-of/validity, approval reference,
reason/code and capture actor/time. Bare legacy vehicleCapacity is never read as pounds.
Qualification requires every explicit assertion and an effective interval from a
registered AUTHORITATIVE_DB source. Omitted booleans cannot silently become false.

Forecast capture references approved ESTIMATE ledger IDs; callers cannot submit money.
The transaction locks ledger rows and freezes exact version/category/currency/amount/
approval plus explicit forecast policy version and conditional applicability.
Zero costs require an explicit reason; confirmed actor/time are captured by the command.
Deferred PostgreSQL constraints require all frozen forecast lines and ledger links
to commit together, with every required category and exact zero-cost evidence.

The caller's evidence UUID is a retry identity; equal normalized command returns the
original outcome, changed payload conflicts. Correction appends a new evidence version,
preserves candidate/kind, and concurrent branches are rejected. Prior records stay readable.
Resolution rejects superseded evidence, wrong policy/candidate/kind, expired evidence or
changed live ledger status/version/amount/currency/approval; historical payload is never
silently refreshed. No accounting row, Load business date or payroll fact is changed.

## Configured trusted dynamic/full-HOS adapter

OptimizationInputProvider supplies qualified vehicle location, driver/truck availability
and routing/ETA. HosFeasibilityService receives the exact simulated route evidence
and returns the full drive/duty/break/cycle/service/next-available assessment plus
minimum simulated headroom. The optimizer does not implement a second HOS engine.

Production transport uses explicitly configured authenticated HTTPS endpoints; no
provider URL/token default, redirect fallback or live network dependency in tests.
Configuration: OPT_DYNAMIC_ENDPOINT/TOKEN and OPT_HOS_ENDPOINT/TOKEN. Registered
source identities/versions still have to match the selected published policy.
The credentials authenticate the approved source; response assertions alone are
not a source registration. HTTP-supplied dynamic evidence must be TRUSTED_ADAPTER,
not a remotely asserted AUTHORITATIVE_DB record.

Requests and responses bind exact tenant scope, candidate/planning interval and
authoritative pickup appointment. Multi-tenant operation requires the bound tenant;
single-tenant operation requires explicit OPT_SINGLE_TENANT_SCOPE, never a default.
Original route units are preserved and normalized explicitly; inconsistent supplied
normalization is rejected. Full-HOS evidence must reference the same simulated plan.
Domain freshness checks still enforce source-specific validity and caps; retrieval
does not extend expired evidence. Missing/malformed/non-success provider responses
fail closed without URI/body/token/error-cause disclosure.

12 deterministic local HTTP/tenant tests and full clean V32 regression PASS:
392 reported/391 executed, 116 PG methods, one legacy skip. No schema change in this
adapter slice; V32 clean/upgrade/validate evidence above remains valid. A configured
production provider deployment and the run/accept workflow are not claimed complete.

## Scoped run/view workflow

POST /api/optimization/runs and GET /api/optimization/runs/{id} use existing
ADMIN/DISPATCHER roles and the existing API envelope. Creation supplies an explicit
published policy, accepted snapshot, existing Trip and authoritative pickup stop
for each Load target, explicit Driver/Truck sets and candidate-bound source IDs.
Selections outside the scope or duplicate identities reject; missing selections
produce unscored rejected candidates. The API rejects scopes above 200 combinations
as an explicit technical work bound; it never truncates, samples or creates demo rows.

The deterministic Cartesian candidate set is projected in one scoped PostgreSQL
query over actual entities, pickup/context/instant history and current assignments.
Qualified source rows, supersession and live ledger are batch reads, not per-candidate
repository lookups. Independent pickup business DATE remains required; an appointment
instant is never converted to manufacture that date. Authoritative appointment instants
must fall in the requested 72h planning horizon.

Routing/availability/full-HOS calls occur outside DB transactions. Hard feasibility
and complete approved forecast coverage precede scoring. Before the short atomic audit
commit, current DB state, source supersession, ledger and proof freshness are rechecked;
changed state becomes an unscored rejection, never an unapproved recalculation.
Run request snapshots include explicit tenant and all selections. Feasible candidates
freeze full raw/evidence/forecast/score explanation, state/material fingerprints and
dense rank; rejected ones retain reasons and NULL score/rank. Candidate IDs only identify
the audited combination; they do not break score ties.

Normalized request-key replay returns original audit/result without provider calls;
payload drift conflicts. Concurrent same-key calculations persist only one outcome.
Full clean V32 regression PASS: 411 reported/410 executed, 127 PG methods, one legacy
skip; eight new scope units and 11 real PG run/financial/source/concurrency/API cases.
That schema-free slice retained V32 migration gates/checksums. The subsequent
Accept/resource/physical-tenant completion evidence follows.

## Atomic existing-Trip acceptance — Phase 7 COMPLETE

POST /api/optimization/runs/{id}/assignments/{candidateId}/accept requires the
explicit idempotencyKey and expectedInputFingerprint of the audited candidate.
Existing ADMIN/DISPATCHER authorization and authenticated tenant employee apply.
Same key/input returns the immutable outcome before provider calls; changed input
conflicts. A new key for the same selected candidate records an immutable alias to
the original actor/time/outcome. A different candidate after selection conflicts
OPTIMIZATION_ALREADY_ACCEPTED; there is no replacement/reassignment command.

Full qualified routing/availability/HOS and current DB/ledger/source revalidation
occurs without a long DB transaction. Material drift rejects 409
OPTIMIZATION_CANDIDATE_STALE; never silently reruns/rerates/accepts another candidate.
A short transaction acquires consistent Load/Trip/Driver/Truck, stop/assignment,
ledger and source locks, rechecks current state/supersession/freshness and writes
only DB-owned assignment, acceptance and command facts atomically.

Existing Trip truck is set; an unambiguous matching driver assignment is reused
without rewriting history, otherwise a real open PRIMARY assignment is appended
using the established execution convention. No Trip creation, dispatch, status
change, synthetic 72h earning end or financial aggregate mutation. Real unassignment
may soft-close the assignment without rewriting immutable acceptance history.

V33 protects immutable acceptance/command history, unique run/candidate/Load selection
and overlapping resource claims. Legacy assignment/truck writes participate in
serialization and cannot take optimizer-owned active resources. Feasible candidate,
context, approved score/policy, actual assigned resources and accepted actor/time are
linked through PostgreSQL guards, not UI-only checks. External provider calls are
outside the write transaction; no outbox exists/was invented for this DB-only command.

Eleven new acceptance PostgreSQL methods cover success/retry/alias/drift, stale
driver/truck/HOS/ledger/source, concurrent Load/Driver/Truck claims, existing assignment
reuse, legacy SQL guards/actual soft-close, authorization and two real physical
PostgreSQL tenant databases. Clean V1→V33 and populated V32→V33 + Flyway validate PASS:
422 reported/421 executed, zero failures/errors, one legacy skip, 138 PG methods.
16 verifier tests PASS; V1–V32 SHA unchanged; V33 unchanged after first application;
diff clean. All Phase 7 gates PASS; Phase 8 is the next source/policy gate.
Production provider credentials/deployment remain explicit separate requirements.
