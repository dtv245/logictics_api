# Phase 8 — Fleet utilization policy decision gate

Dependency: Convention Phases 0–7 COMPLETE. Phase 7 gate PASS at V33 on
2026-10-05 (Asia/Ho_Chi_Minh): 422 reported / 421 executed, zero failures/errors,
one legacy skip, 138 PostgreSQL methods; clean/previous-upgrade/validate/checksum/diff PASS.
Phase 8 COMPLETE at verified V35; final whole-system gates PASS.
Approved backend plan COMPLETE: [final verification](backend-plan-final-verification.md).
All required FLEET V1 choices are CONFIRMED/LOCKED. RATE/BILL/OPT decisions remain
CONFIRMED/LOCKED and are not additional questions.

This is the canonical FLEET-DEC-001…009 register. The two older fleet placeholders in
remaining-business-decision-gates.md were grouped topic headings, not confirmed
numbered policies; this register supersedes their numbering without changing any
previously confirmed business rule. Recommendations below are NOT implementation
authority until the business owner confirms them.

## Historical pre-implementation evidence — focused Phase 8 sources only

- Truck.java / trucks: current String status, no membership effective interval,
  capacity calendar or historical status. TruckService updates status directly.
- Real clean V33 PostgreSQL metadata: driver_behavior_events, eld_vehicle_mappings,
  load_events, maintenance_parts/records/schedules and payroll_payment_events exist;
  no vehicle_status_events or equivalent fleet lifecycle history table.
- DriverBehaviorEvent records isolated driver safety observations, not complete
  vehicle state transitions. EldVehicleMapping is an identity/sync mapping, not
  an operational interval feed. Optimizer evidence covers prospective 72h scopes;
  accepted recommendations are not proof of actual productive utilization.
- Trip.java: explicit actual/loaded/empty decimal MILE values from V3, dispatch/
  completion/cancellation instants; truck association is mutable and no movement-
  segment timestamps or fleet membership history are provided by that model.
  Period attribution/cross-period mileage splitting is not yet a fleet report contract.
- MaintenanceRecord: serviceDate, optional schedule FK and monetary totals, but
  no downtime start/end, planned/unplanned flag, breakdown event identity or currency.
  MaintenanceSchedule stores mutable next/last due information, not immutable due
  occurrences proving PM compliance for a historical period.
- ReportController currently exposes /api/reports/fleet/fuel and /fleet/maintenance;
  it does NOT expose /fleet/health, /fleet/utilization-history or /executive-summary.
  Existing report envelope/authorization and MetricAvailability can be reused.
- Phase 3 cost classification/source reconciliation stays authoritative; never sum
  the same maintenance event through records + expense projection + ledger.
- GitNexus graph query returned no flows; no absence inferred from that result alone.
  The focused Java/SQL/controller search and actual V33 schema inspection above
  independently establish these gaps. No completed-phase re-audit was performed.

These are V33 inspection facts, not current missing implementation: V34/V35 now
provide the approved history/provenance model and fleet report/health routes.
Missing qualified health sources still yield the approved UNAVAILABLE outcome.

## Locked rules — not questions

- Historical utilization never comes from current trucks.status or a 24h/day default.
- No synthetic history/backfill. Missing history: UNAVAILABLE with an explicit reason
  such as NO_AVAILABILITY_HISTORY. Missing/zero denominator: UNAVAILABLE, not zero.
- FleetUtilization = ProductiveEligibleDuration / AvailableCapacityDuration.
- LoadedMilesPct = loadedMiles / (loadedMiles + emptyMiles), only for valid actual
  components with positive denominator. No planned/legacy mileage substitution.
- Deadhead is not automatically 100 − loaded percentage if excluded categories exist.
- Existing MetricAvailability values: AVAILABLE, PARTIAL, UNAVAILABLE, NOT_APPLICABLE.
- Unsupported downtime/PM/cost-mile/breakdown metrics may remain UNAVAILABLE; this
  permission is already confirmed, not a new business decision to ask again.
- Read-only report/dashboard GET never creates calculation snapshots or events.
- New migration V34+ only; V1–V33 and immutable financial history remain unchanged.

## Decision register

| ID | Decision | Status | Required answer |
|---|---|---|---|
| FLEET-DEC-001 | Available Capacity definition | CONFIRMED | A: versioned explicit capacity windows/mappings; no implicit 24h denominator |
| FLEET-DEC-002 | Productive time definition | CONFIRMED | A: explicit versioned activity mapping, not all DRIVING |
| FLEET-DEC-003 | Excluded/unavailable statuses | CONFIRMED | A: authored mappings before publication, unknown/gap unavailable, no implicit literals |
| FLEET-DEC-004 | Unplanned downtime definition | NOT_APPLICABLE | None for V1 UNAVAILABLE outcome: qualified interval source absent |
| FLEET-DEC-005 | Breakdown event definition | NOT_APPLICABLE | None for V1 UNAVAILABLE outcome: explicit breakdown classification absent |
| FLEET-DEC-006 | Interval validity model | CONFIRMED | A: explicit validity-bounded intervals, no indefinite last-known state |
| FLEET-DEC-007 | Mileage attribution model | CONFIRMED | A: proven completion attribution, historical unproven truck attribution unavailable |
| FLEET-DEC-008 | Reporting details | CONFIRMED | A: strict coverage, [from,to), explicit ZoneId, duration sums, DECIMAL128/ratio8/percent2 HALF_EVEN |
| FLEET-DEC-009 | Conflict/reconciliation | CONFIRMED | A: explicit conflicts unavailable, normalized identity retry, chronological reconstruction, append-only correction, no clipping |

## Received choices — CONFIRMED / LOCKED

The business owner explicitly selected all four A options after the first batch:
versioned explicit capacity windows/mappings; explicit versioned activity mapping;
explicit validity-bounded intervals; proven completion attribution. These directions
are locked and are not asked again. Required mappings/sources are explicit policy
authoring inputs, not seeded global status or productive defaults.

The first batch also requested additional detailed values that the option labels do
not specify: reporting precision/rounding/partial coverage/aggregation and interval
conflict/correction/outside-capacity reconciliation. They are isolated as 008/009,
with one exact follow-up batch. The business owner also selected A for BOTH 008/009;
their exact A contracts below are now CONFIRMED/LOCKED, no remaining Fleet V1 question.
The sections below preserve the original
options as evidence; their A direction is superseded by this confirmed receipt.

NOT_APPLICABLE in 004/005 means definition/calculation is not applicable to V1's
already-approved unsupported-source outcome. It does not label trucks healthy,
erase the metrics or authorize zero. Future AVAILABLE metrics require new approved
source definitions and tests. PM and maintenance cost/mile likewise remain
UNAVAILABLE where historical due/currency/deduplicated cost attribution is unqualified.

## FLEET-DEC-001 — Available Capacity (CONFIRMED A)

- **Decision:** Which real membership/capacity intervals form the denominator?
- **Status:** CONFIRMED / LOCKED: A, versioned explicit capacity windows/mappings.
- **Options:** A. Versioned fleet policy with audited membership entry/exit and explicit
  per-truck capacity/service calendar windows. B. Explicitly approved full in-fleet
  clock time with a specified exclusion mapping. No implicit 24h interpretation.
- **Existing evidence:** Truck has no membership effective dates or capacity calendar.
  CreatedAt/current status/optimizer availability cannot reconstruct old capacity.
- **Required business answer:** A received, do not ask again. Membership/capacity
  source/actor/windows/exclusions/policy identity are mandatory authored fields,
  never defaults. Remaining report-zone/coverage details are isolated in 008.
- **Recommended:** A, keeping independently evidenced membership and capacity windows;
  prevents counting a nonexistent historical denominator. Recommendation is not a rule.
- **Code impact:** Eligible interval intersection and explainable denominator/coverage.
- **DB impact:** Minimal append-only membership/capacity evidence if no qualified source
  exists; no truck CreatedAt/status backfill.
- **API impact:** Audited capture/reference and report policy identity/denominator.
- **Test impact:** Entry/exit, planned unavailable, partial/missing calendar, zero denominator.

## FLEET-DEC-002 — Productive time (CONFIRMED A)

- **Decision:** Which evidenced activities contribute productive eligible duration?
- **Status:** CONFIRMED / LOCKED: A, explicit versioned activity mapping.
- **Options:** A. Versioned explicit activity mapping with auditable actual Load/Trip/
  movement or service references. B. Business supplies another precise event-state
  definition, including any eligible empty driving/idle/service activities.
- **Existing evidence:** Trip dispatch/complete and stop timestamps are not a complete
  driving/idle/loading history; no approved productive mapping exists.
- **Required business answer:** A received, do not ask again. Exact activity/source/
  evidence mappings must be authored before publication, without seed/default.
  Outside-capacity reconciliation is the remaining detailed item in 009.
- **Recommended:** A with explicit actual references, not all DRIVING automatically.
- **Code impact:** Separate productive classifier and eligible interval reconciliation.
- **DB impact:** Preserve actual activity/source/reference and approved policy version.
- **API impact:** Explain numerator/eligible activity and data gaps.
- **Test impact:** Fully/partially productive, loaded versus empty, service/idle, conflicting evidence.

## FLEET-DEC-003 — State/exclusion mapping (CONFIRMED A)

- **Decision:** Exact lifecycle vocabulary and denominator/productive/excluded mapping.
- **Status:** CONFIRMED / LOCKED: A, explicit versioned mappings before publication.
- **Options:** A. Explicit versioned state/activity mappings, unknown state fail closed
  as unavailable history. B. Fixed V1 vocabulary/mapping supplied by business now.
- **Existing evidence:** Plan's DRIVING/IDLE/LOADING/MAINTENANCE/OFFLINE are proposals;
  current String literals are not approved historical events or denominator semantics.
- **Required business answer:** A received, explicit authoring required before policy
  publication; unknown/gap unavailable. Do not ask mapping direction again or invent
  literals. Whole-report coverage treatment is detailed in 008.
- **Recommended:** A with all required mappings explicit before report execution.
- **Code impact:** Classified duration/coverage and explicit availability reasons.
- **DB impact:** Policy validation and sourced lifecycle events, only after approval.
- **API impact:** Expose included/excluded/unknown duration and reason codes.
- **Test impact:** Planned/unplanned unavailable, unknown status, gaps and source mismatch.

## FLEET-DEC-004 — Unplanned downtime

- **Decision:** Evidence required before downtime can be AVAILABLE.
- **Status:** NOT_APPLICABLE to V1's approved UNAVAILABLE fallback.
- **Options:** Approved planned/unplanned start/end feed in a future extension, or keep
  UNAVAILABLE now. The latter is already authorized; no repeat question.
- **Existing evidence:** Maintenance serviceDate is not downtime duration/classification.
- **Required business answer:** None for current unsupported-source outcome.
- **Code impact:** UNAVAILABLE/null value with DOWNTIME_INTERVAL_SOURCE_UNAVAILABLE;
  no duration inferred from ticket date or status.
- **DB impact:** No guessed downtime backfill or speculative classification table.
- **API impact:** Availability/reason visible in fleet health; no fake zero.
- **Test impact:** Absent source yields unavailable, no every-maintenance-row duration.

## FLEET-DEC-005 — Breakdown event

- **Decision:** Evidence required before breakdown count/rate can be AVAILABLE.
- **Status:** NOT_APPLICABLE to V1's approved UNAVAILABLE fallback.
- **Options:** Qualified explicitly classified breakdown event identity in a future
  extension, or keep UNAVAILABLE now. The latter is already authorized.
- **Existing evidence:** No breakdown identity/classification; multiple maintenance
  records could belong to one event, and unscheduled is not automatically breakdown.
- **Required business answer:** None for current unsupported-source outcome.
- **Code impact:** UNAVAILABLE/null value with BREAKDOWN_CLASSIFICATION_UNAVAILABLE.
- **DB impact:** No fake incident records or inferred historical classifications.
- **API impact:** Explicit metric availability instead of a misleading zero rate.
- **Test impact:** Missing classification cannot become count of maintenance tickets.

## FLEET-DEC-006 — Interval validity (CONFIRMED A)

- **Decision:** Authoritative event source and deterministic historical interval semantics.
- **Status:** CONFIRMED / LOCKED: A, explicit validity-bounded intervals.
- **Options:** A. Evidence-validity bounded intervals: actual occurredAt plus explicit
  validUntil/interval-end evidence; gaps unavailable. B. State persists until the next
  actual transition, with open interval through report end only if business approves.
  Both require approved sources and append-only audited correction, not insertion order.
- **Existing evidence:** No complete vehicle lifecycle source, event sequence or feed
  validity contract. Safety events are not interchangeable with lifecycle transitions.
- **Required business answer:** A received, validity/end required; no indefinite
  state persistence. Identity/order/correction/conflict details not specified in
  the option label are isolated in 009, not a reopened validity question.
- **Recommended:** A when provider cannot prove indefinitely persistent state;
  duplicate identity+same input returns original, changed input conflicts; conflicting
  same-time facts remain unavailable until audited correction. These are proposals only.
- **Code impact:** Deterministic interval reconstruction separated from SQL aggregation.
- **DB impact:** Minimal append-only vehicle_status_events and identity/correction guards
  after confirmed contract. No synthetic initial-state/history bootstrap.
- **API impact:** Authenticated capture/import/correction and visible coverage/conflicts.
- **Test impact:** Duplicate/same timestamp/out-of-order/initial/open/overlap/correction/tenant.

## FLEET-DEC-007 — Mileage attribution (CONFIRMED A)

- **Decision:** Which actual history/miles belong to a requested reporting interval?
- **Status:** CONFIRMED / LOCKED: A, proven completion attribution.
- **Options:** A. Explicit Instant [from,to) with caller-specified business ZoneId for
  LocalDate calendar requests; mileage counted once at Trip completion with proven
  truck identity. B. Actual movement/leg-timestamp attribution across reporting periods,
  requiring a new authoritative source. C. Another exact supplied contract.
- **Existing evidence:** Existing report parameters are optional OffsetDateTime, but
  old endpoint inclusivity/UTC convention does not approve new fleet semantics. Trip
  loaded/empty totals have no segment timestamps and current truck FK is mutable.
- **Required business answer:** A received, count actual Trip mileage once at proven
  completion with immutable truck attribution; unproven history unavailable, never
  current-FK backfill. This attribution choice is not asked again. Period/zone,
  coverage/aggregation/precision details are isolated in 008; no rounding inferred.
- **Recommended:** Explicit half-open Instants; immutable sourced completion attribution
  only if approved, otherwise miles unavailable rather than guessing period/truck.
  Aggregate proven durations, expose coverage, avoid a ratio claiming complete history.
- **Code impact:** Period clipping, mileage attribution, aggregation/availability/numeric rules.
- **DB impact:** Proven immutable attribution if needed, never historical current-FK backfill.
- **API impact:** Explicit period/policy/coverage/numerator/denominator with existing envelope.
  Reuse existing fleet report conventions; /fleet/health or executive endpoint currently
  absent, so do not claim they were extended. No report GET writes.
- **Test impact:** Adjacent/cross-zone/DST boundaries, cross-period Trip, changed truck,
  gaps/partial fleet, loaded/empty/zero miles, no tenant leakage or dashboard writes.

## FLEET-DEC-008 — Remaining reporting detail (exact follow-up question 1)

- **Decision:** Period/zone, aggregation, incomplete coverage and authoritative numbers.
- **Status:** CONFIRMED / LOCKED: exact option A received from business owner.
- **Options:** A. FLEET_REPORTING_V1 v1: Instant [from,to), explicit ZoneId for LocalDate
  requests; sum productive / sum capacity, not mean per-truck ratios. Missing required
  scope membership/capacity/activity coverage makes overall ratio UNAVAILABLE, with
  known durations/coverage explained. BigDecimal DECIMAL128; ratio scale8 HALF_EVEN;
  percentage=ratio×100 scale2 HALF_EVEN, separately approved Fleet policy.
  B. Covered-only PARTIAL ratio with explicit alternative numeric policy supplied.
- **Existing evidence:** No Fleet reporting numeric/coverage contract; choosing completion
  attribution does not specify either. Existing Rating and optimizer rounding are scoped.
- **Required business answer:** Received A; do not ask again. Implement its exact values.
- **Recommended:** A for a metric that never claims incomplete history is complete.
- **Code impact:** Deterministic report calculator, period clipping and coverage status.
- **DB impact:** Aggregate only proven eligible intervals/attribution, no history backfill.
- **API impact:** Explicit period/policy/coverage/known numerator/denominator, null unavailable.
- **Test impact:** Partial scope, zero/gaps, adjacent/DST boundaries and exact rounding.

## FLEET-DEC-009 — Remaining reconciliation detail (exact follow-up question 2)

- **Decision:** Identity/order/conflict/correction and productive evidence outside capacity.
- **Status:** CONFIRMED / LOCKED: exact option A received from business owner.
- **Options:** A. Same registered source event ID + normalized input returns original;
  different input conflicts. Out-of-order ordered by occurredAt, never insertion order.
  Contradictory same-time/overlap facts make the interval unavailable until audited
  append-only superseding correction; no hidden winner. Missing initial/expired validity
  is unavailable; open end limited by explicit validUntil. Productive outside proved
  membership/capacity makes metric unavailable with SOURCE_CAPACITY_CONFLICT, not
  silent clipping. Authenticated audited registered/versioned sources only, no backfill.
  B. Exact alternative reconciliation supplied by business.
- **Existing evidence:** No established fleet event identity/order/correction contract;
  bounded interval selection does not itself approve a conflicting-event tie-break.
- **Required business answer:** Received A; no repeat question about conflict,
  validity, capacity, productive mapping or completion attribution.
- **Recommended:** A, preserving ambiguous facts/audit instead of fabricating one truth.
- **Code impact:** Event reconstruction/conflict and source/capacity reconciliation.
- **DB impact:** V34+ append-only identity/correction/history guards after confirmation.
- **API impact:** Audited capture/correction and explicit conflict/unavailable reasons.
- **Test impact:** Retry/drift/same-time/overlap/out-of-order/initial/open/correction/tenant.

## Safe work completed / next

All four direction A answers and exact detailed 008/009 A answers received/locked.
Approved immutable policy/source/history/completion-attribution, scoped SQL strict
report and authenticated API implemented with V34/V35, no guessed mapping/history.
Clean V1→V35 and populated V34→V35 + validate/Maven verify/repackage PASS:
456 reported/455 executed, one legacy skip, 160 PG, 17 verifier tests; checksums/diff
clean. Contract/test matrix: fleet-history-v1-contract.md. Phase 8 COMPLETE;
final cross-domain/full-system confirmation remains before PLAN COMPLETE.
