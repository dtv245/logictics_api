# Fleet history / reporting V1

Authority: all FLEET decisions in fleet-utilization-policy-decisions.md are
CONFIRMED/LOCKED, including exact 008/009 A. This is an implementation contract,
not a new policy decision. Phase 0–7 and immutable financial facts remain unchanged.

## Published policy and source capture

An ADMIN publishes /api/fleet/policies with explicit code/version, approval reference,
nonempty membershipStates (literal→in/out Boolean), capacityStates (literal→eligible
Boolean), activityStates (literal→PRODUCTIVE/NON_PRODUCTIVE/EXCLUDED/UNAVAILABLE),
and registered source type/reference/version identities. No status, source or activity
map is seeded. Same code/version/input retries return original audit; changes require
a new version. Earlier version/history is not reinterpreted by later publication.

ADMIN/DISPATCHER authenticated tenant employees capture /api/fleet/status-events.
Membership, capacity and activity are separate evidenced interval families, not
current Truck.status projections. Every interval carries occurredAt, explicit
validUntil, source identity/version/event ID, reason code/context and server actor/time.
Initial unknown state requires real evidence, never a manufactured IN_FLEET event.
To prove entry/exit across a report period, the source must supply evidenced OUT/IN/OUT
coverage; a single late IN interval cannot pretend the preceding period is known.

Unknown state is retained as UNAVAILABLE evidence; it is not silently mapped to a
known activity or removed. PRODUCTIVE requires actual Trip/Load/execution references.
Reports cannot claim future actual utilization. The source's validity can bound an
open current observation, but no interval continues past that bound.

Same registered source event ID + same normalized command returns original actor/
time/value. Changed input conflicts. Corrections append a new source event identity
with supersedesEventId and actor/reason; same policy/truck/family, one active chain
without branching. No event is updated/deleted. Historical report recalculation may
reflect explicit correction, never an automatic current-status backfill.

## Interval aggregation

PostgreSQL projects only selected policy/truck/period intervals and ignores explicitly
superseded evidence. It sweeps the union of actual bounded event endpoints, clips to
the requested half-open Instant period, classifies spans and aggregates durations.
Java receives aggregate coverage, not every company's entire event history.

Contradictory same-time/overlap facts do not acquire a winner from insertion order.
They make coverage unavailable until audited correction. Duplicate corroborating
same-state intervals are unioned once, not added as duplicate productive time.
Capacity is eligible only within proved membership; mapped excluded activity removes
its span from eligible capacity. Required capacity/activity gaps, unqualified state,
or productive evidence outside proved membership/capacity suppress the whole ratio.
Known durations and per-truck gap/conflict/event coverage remain explainable.

FLEET_REPORTING_V1 version1: SUM(productive eligible seconds) / SUM(capacity seconds),
BigDecimal DECIMAL128, authoritative ratio scale8 HALF_EVEN, percentage×100 scale2
HALF_EVEN. Not an average of truck ratios. Strict missing coverage/zero denominator
returns UNAVAILABLE with null value and explicit reason, never 0%. Calendar requests
require explicit ZoneId; start-of-day conversion handles DST with that supplied zone,
not JVM/DB timezone. Instant boundaries require PostgreSQL microsecond precision.

## Actual completion mileage attribution

ADMIN/DISPATCHER captures /api/fleet/mileage-attributions using explicit proven truck,
actual Trip completion, actual loaded/empty/operational miles, source/version/event
identity and reason. The command matches real actual Trip fields under a lock;
never uses plannedDistance/legacy totalDistance or derives the truck from a mutable
current FK. The approved source proves the truck attribution. Snapshot amounts/
completion/source/actor remain immutable even if current Trip fields later change.

One original attribution per Trip; retry/conflict and single-chain audited correction
guards prevent duplicate miles. A report counts the active attribution once in the
period containing completion, with [from,to) boundaries; no proportional cross-period
split. Missing historical attribution is UNAVAILABLE, not current-truck backfill.
Unattributed completed Trips conservatively suppress mileage when the actual fleet
scope cannot be proved; the report does not pretend the current FK establishes scope.

Loaded percent uses loaded/(loaded+empty). Deadhead uses empty/actual operational
miles, not 100−loaded if other actual mileage is excluded. Explicit zero actual miles
may be captured as a real audited fact, but zero denominator is unavailable.
NUMERIC(12,3) inputs are exact; more precision rejects instead of silently rounding.

## Reporting / health

ReportController exposes /api/reports/fleet/utilization-history and /fleet/health,
using existing report roles/envelope. Explicit policyId and distinct truckIds scope,
maximum 200 trucks (technical work bound), plus from/to Instants OR firstDate/
exclusiveLastDate/businessZoneId. Missing scope never defaults to current fleet.
The response carries policy versions, period, coverage/known durations, utilization,
actual mileage metrics and health availability. Read-only repeatable-read transaction;
no snapshot/event writes on dashboard GET. Existing SLF4J records policy, correlation,
duration, availability/reason; no second metrics framework.

Current maintenance schema does not prove downtime intervals, historical PM due
occurrences, complete currency-qualified maintenance coverage or breakdown classification.
Phase 3 ledger can contain qualified expense projections; that does not establish
complete historical maintenance-cost coverage for every reporting truck/period, and
legacy maintenance records still have no currency. No sum of unlike/unreconciled
sources is substituted for a complete health KPI.
Those four metrics therefore return the already-authorized UNAVAILABLE/null outcome
with explicit reasons. No ticket-as-breakdown, guessed maintenance currency, fake
zero or double counting records + expense + ledger. No /executive-summary existed
to claim extended; the fleet report response supplies the backend dashboard contract.

## Phase 8 verification checkpoint — COMPLETE

V34 clean and populated V33→V34 PASS at 454/453 with 158 PG. Self-review correction
V35 guards coherent owned Trip/Load execution context and actual capture time, with
two additional PG cases. Clean V1→V35 and populated V34→V35 PASS through Maven clean
verify (including executable JAR/repackage): 456 reported/455 executed, zero failures/
errors, one legacy skip, 160 PG (22 Fleet) and 295 non-PG (12 Fleet units). Seventeen
verifier tests PASS; Flyway validate PASS; earlier SHA unchanged, applied V34/V35
unchanged, diff clean. Phase 8 COMPLETE; final same-code clean/previous-upgrade,
runtime OpenAPI and all backend gates also PASS. Approved backend PLAN COMPLETE;
exact diagnostics and operational boundaries: [final verification](backend-plan-final-verification.md).

Existing TripMileageRatioCalculator retains its explicitly labelled legacy per-Trip
share of actual operational total. Fleet's LOADED_MILES_PERCENT is the independently
approved category-share loaded/(loaded+empty); its basis is explicit, not a silent
reinterpretation of the older operational-total metric.
