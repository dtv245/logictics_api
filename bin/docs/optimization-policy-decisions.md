# Phase 7 — Optimization policy decision gate

Dependency gate: Phase 5 COMPLETE + Phase 6 COMPLETE, PASS on 2026-10-04.
Policy gate: CONFIRMED/LOCKED for Optimization V1; Phase 7 implementation COMPLETE.
No production optimizer, candidate
feasibility result, score, weight, cutoff or assignment outcome is fabricated.
All RATE-DEC-001…007/BILL-DEC-001…006 stay CONFIRMED/LOCKED and are not reopened.
The business owner confirmed option A for all three OPT decisions. These directions
are LOCKED: trusted adapters with missing/stale evidence rejected before scoring;
all four utilities with approved forecast sources; equal scores keep equal rank and
the dispatcher chooses. Exact source/unit/freshness/matching contracts, utility
formulas/parameters/weights/precision and the acceptance lifecycle action were
previously missing. The business owner has now answered the complete consolidated
OPT-DEC-004…010 batch below. All ten decisions are CONFIRMED/LOCKED; historical
options/recommendations later in this document are not additional open questions.
Published exact status allowlists and qualified source registrations are mandatory
runtime configuration, not implicit ACTIVE/OPEN defaults or guessed provider names.

## Decision register — all V1 choices confirmed

| ID | Status | Scope |
|---|---|---|
| OPT-DEC-001 | CONFIRMED | Trusted adapter direction; missing/stale evidence rejects before scoring. Exact eligibility/source parameters are tracked in 004/005. |
| OPT-DEC-002 | CONFIRMED | All four utilities with approved forecast sources. Exact scoring/forecast parameters are tracked in 006…009. |
| OPT-DEC-003 | CONFIRMED | Equal rank, dispatcher chooses; UUID presentation-only. Accept side effect is tracked in 010. |
| OPT-DEC-004 | CONFIRMED | OPT_ELIGIBILITY_V1 v1, 72h from createdAt, existing pre-dispatch Trip, explicit exact-literal allowlists, qualified availability, no reassignment |
| OPT-DEC-005 | CONFIRMED | OPT_SOURCE_V1 v1, per-source evidence, location/dynamic driver/HOS 5m, traffic ETA 15m, MILE/POUND, no heuristic/default source |
| OPT-DEC-006 | CONFIRMED | OPT_UTILITY_V1 v1, exact four raw definitions and piecewise-linear knots; clamp endpoints |
| OPT-DEC-007 | CONFIRMED | OPT_WEIGHT_V1 v1: margin .35, on-time .30, deadhead .25, HOS .10; exact sum=1 |
| OPT-DEC-008 | CONFIRMED | OPT_NUMERIC_V1 v1, DECIMAL128/HALF_EVEN, utility/weight scale6, contribution/score scale8, DENSE_RANK |
| OPT-DEC-009 | CONFIRMED | Candidate contribution margin; accepted pre-tax subtotal, ESTIMATE/APPROVED variable forecasts, explicit coverage/zero evidence/currency |
| OPT-DEC-010 | CONFIRMED | Assign driver/truck to existing eligible Trip only, no create/dispatch; atomic revalidation/idempotency/conflict |

CONFIRMED rows are LOCKED. Earlier direction-only replies did not confirm numerical
parameters; the later complete replies now do. The exact locked answers below
override older options/evidence describing a missing policy. No seeded hidden policy.

## Exact locked V1 contracts — business owner, 2026-10-04

### Eligibility — OPT-DEC-004

OPT_ELIGIBILITY_V1 version 1; planning horizon is 72 hours from
OptimizationRun.createdAt. No implicit timezone/date conversion or default status.
Policy stores explicit case-sensitive runtime status literals for each entity;
unknown status rejects OPTIMIZATION_STATUS_NOT_ALLOWED.

- Load: same tenant; not terminal/cancelled/delivered/completed/dispatched; explicit
  pickup business DATE, routable origin/destination, accepted RatingSnapshot,
  valid target existing Trip/context, no conflicting active assignment.
- Trip: existing only, same tenant/Load/context, pre-dispatch and not started,
  dispatched/completed/cancelled; no conflicting active assignment.
- Driver: active/eligible qualified worker, available throughout planning interval,
  no overlapping assignment, effective license/qualifications, required hazmat
  qualification and full HOS feasible. Status alone never proves availability.
- Truck: operational/available, no maintenance block or overlapping assignment,
  compatible equipment/capacity/hazmat, qualified evidence.
- V1 never automatically reassigns or invents driver-truck pairing eligibility.

### Sources — OPT-DEC-005

OPT_SOURCE_V1 version 1. Every input retains sourceType/reference/version, value,
unit, observedAt/asOf and expiresAt or maxAge. No global TTL. Missing mandatory
policy/source/version/unit/input rejects before scoring.

| Input | Qualified source / unit | Explicit freshness |
|---|---|---|
| Vehicle location | Trusted ELD/GPS/location adapter; WGS84 coordinates | 5 minutes; missing observedAt/stale: VEHICLE_LOCATION_STALE |
| Driver availability | Authoritative assignment/availability, as-of run | External/dynamic evidence 5 minutes |
| Truck availability | Assignment + maintenance + operational state, as-of run | Source-specific validity/expiry required; no invented TTL |
| HOS | Full HosFeasibilityService/trusted HOS simulation | 5 minutes, no timestamp-less cached remaining-hours shortcut |
| Routing distance | Trusted routing; canonical MILE | Qualified version/asOf/expiry or maxAge; no invented default |
| ETA | Trusted route/ETA, Instant/minutes | Traffic-aware forecast 15 minutes; ETA_FORECAST_STALE |
| Capacity/cargo | Provenanced canonical POUND (lb) | Source validity; bare numeric rejects CAPACITY_EVIDENCE_UNAVAILABLE |
| Equipment/hazmat/qualification | Authoritative DB effective/versioned facts | Must cover selected interval; no inferred qualification |

km→MILE and kg→POUND conversions are explicit and preserve original value/unit,
normalized value and source/version. Never Haversine or distance/fixed-speed ETA.

### Utilities — OPT-DEC-006

OPT_UTILITY_V1 version 1. All utilities [0,1], higher is better. Linear interpolation
between knots; outside bounds clamp to nearest endpoint. Never candidate-set min/max.

| Utility | Raw definition | Exact knots (raw → utility) |
|---|---|---|
| DEADHEAD | deadheadMiles / candidateLoadedMiles | 0→1; .10→.90; .25→.70; .50→.40; 1→0 |
| MARGIN | (expectedRevenue − expectedVariableCost) / expectedRevenue | 0→0; .05→.25; .10→.50; .20→.80; .30→1 |
| ON_TIME | pickupAppointmentStart − predictedArrivalAtPickup, minutes | 0→0; 30→.25; 60→.50; 120→.80; 180→1 |
| HOS | minimumHosHeadroomRatio after full simulated route/service plan | 0→0; .05→.25; .10→.50; .20→.80; .30→1 |

candidateLoadedMiles<=0: DEADHEAD_UTILITY_UNAVAILABLE; expectedRevenue<=0:
MARGIN_UTILITY_UNAVAILABLE; no authoritative appointment/ETA:
ON_TIME_UTILITY_UNAVAILABLE; no simulated full-HOS headroom evidence:
HOS_UTILITY_EVIDENCE_REQUIRED. Headroom never replaces the hard HOS gate.
Persist raw value/unit, curve code/version, exact knots and normalized utility.

### Weights — OPT-DEC-007

OPT_WEIGHT_V1 version 1: MARGIN=.350000, ON_TIME=.300000, DEADHEAD=.250000,
HOS=.100000. All four mandatory, nonnegative and exact sum 1.000000; no tolerance.
Missing/negative/wrong sum: OPTIMIZATION_WEIGHT_POLICY_INVALID, no run.
Historical run freezes exact weights/code/version. These user-confirmed values are
an explicit named V1 policy, not a global fallback or equal-weight seed.

### Numeric / ranks — OPT-DEC-008

OPT_NUMERIC_V1 version 1; BigDecimal with MathContext.DECIMAL128 intermediates.
Optimizer rounding HALF_EVEN, independent of Rating's HALF_UP.
Normalized utility scale6; weight scale6; contribution=utility×weight rounded scale8;
finalScore=sum(persisted contributions), scale8. Exact invariant sum=finalScore.
Reads never recompute from raw inputs at another precision.
Authoritative equal score uses scale8 BigDecimal.compareTo==0, no epsilon.
DENSE_RANK: equal .90000000 scores rank1, next .85000000 rank2. UUID sorts display
only; dispatcher explicitly chooses an equal-rank candidate.

### Forecast contribution margin — OPT-DEC-009

Candidate-specific contribution margin only, not allocated/full accounting profit.
Expected revenue is accepted RatingSnapshot subtotal before tax, including its
accepted linehaul/FSC/approved pre-dispatch accessorials, never preview or tax.
Only candidate-attributed VARIABLE forecasts with cost_basis=ESTIMATE and
status=APPROVED qualify. DRAFT/VOIDED, ACTUAL or historical POSTED costs do not.
Every source pins Load/Trip and Driver/Truck where relevant plus source/policy versions.

- Required: FUEL, DRIVER, TOLL.
- Conditionally required when applicable: ACCESSORIAL, PERMIT.
- Excluded: INSURANCE, allocated MAINTENANCE, fixed overhead, non-attributable OTHER.
  Maintenance remains a hard gate, not an inferred forecast cost.
- Missing required/conditional category: FORECAST_COST_INCOMPLETE, excluded from rank.
- Zero cost requires explicit ZERO_COST_CONFIRMED audit: category/source/version/
  asOf/reasonCode. Missing evidence is not zero.
- Revenue and all costs must share currency; OPTIMIZATION_CURRENCY_MISMATCH, no FX.

ExpectedVariableCost sums the required approved candidate costs;
ExpectedContributionMargin=ExpectedRevenue−ExpectedVariableCost;
MarginPct=ExpectedContributionMargin/ExpectedRevenue. Snapshot ratingSnapshotId,
forecastCostIds, required categories/zero evidence/currency/revenue/cost/margin.

### Accept — OPT-DEC-010

Option B CONFIRMED: assign driver and truck to the specified existing eligible Trip;
do not create Trip or dispatch. Eligibility/pre-dispatch/no-conflict rules above
remain mandatory. Revalidate all qualified evidence, HOS and assignments before
atomic mutation. Changed material state rejects 409 OPTIMIZATION_CANDIDATE_STALE;
do not silently rerun/select another candidate. Same accepted assignment retry returns
the existing result; another candidate for an accepted run conflicts
OPTIMIZATION_ALREADY_ACCEPTED. No implicit replacement/reassignment semantic.

## Historical evidence / alternatives (superseded where answers above apply)

## Locked architecture (not questions)

- Real Loads/Drivers/Trucks/Assignments/Availability only; no demo candidates.
- Hard feasibility before scoring; infeasible candidates cannot win.
- Full HOS service considers drive/duty/break/cycle/service/next availability.
- Utilities in [0,1], higher is better; weights nonnegative and sum exactly 1.
  The older plan's unspecified tolerance is not an approved exception.
- Persist raw value/unit/source, normalization, weight, contribution, final score
  and policy version; never only a final score.
- Dispatcher acceptance is transactional/idempotent; revalidate load/driver/truck/
  HOS/availability and reject stale/conflicting state rather than overwrite it.
- V1–V29 remain immutable. New schema after approved contract is V30+.

## Implementation evidence and impacts

All OPT-DEC-001…010 have Status CONFIRMED/LOCKED. Required business answers
have been received; no option in a prior checkpoint remains an open question.
The exact contracts above govern source registration and policy authoring.

Existing evidence before implementation: entity statuses are String/text; bare
Truck.vehicleCapacity has no proven unit; legacy coordinates have no observation
time; DriverHosStatus is not a complete HOS simulation. Existing assignments are
historical facts, not future availability proof. Accepted ratings and approved
estimate ledger entries are reusable financial facts, not guessed forecasts.
The legacy C# AI-dispatch document is not Java runtime authority.

Code impact: scoped real candidates; qualified adapters and full HOS port;
aggregate hard-rejection reasons before scoring; explicit conversions/freshness;
four explainable curves, exact weights/precision/dense ranks; existing-Trip atomic
assignment with no dispatch and complete stale-evidence revalidation.

DB impact: forward V30+ policy/input/candidate audit and acceptance concurrency
protections. Stable identities, feasibility, scores and rank are columns; versioned
explanation can be JSONB. All V1–V29 checksums remain immutable.

API impact: explicit published policy/source identities, exact status allowlists,
candidate scope and existing target Trip; authorized run/view/accept, full rejection
and score explanation. No default policy or arbitrary user-provided feasible flag.

Test impact: eligibility and all feasibility failures, source context/unit/freshness,
coverage/zero evidence/currency, curve knots/interpolation/clamp/rounding, weights,
score reconciliation/ties, immutable audit, stale/retry/concurrent acceptance and
physical tenant isolation on PostgreSQL.

## Current implementation / next

Phase 7 COMPLETE: approved policy/source/hard-HOS/forecast/scoring, authenticated
HTTPS adapters, scoped real generation, immutable run/view and atomic existing-Trip
Accept. V30–V33 add immutable audit/qualified inputs/acceptance and resource guards.
No Trip creation/dispatch or financial mutation. Same-key replay, drift conflict,
current-source stale checks, concurrent Load/Driver/Truck protection and physical
PostgreSQL tenant isolation verified.
Clean V1→V33 and populated V32→V33 + Flyway validate PASS: 422 reported/421 executed,
zero failure/error, one legacy skip, 138 PG methods. Optimizer units/HTTP/scope=76;
optimizer PG methods=44; Python verifier=16 PASS. All earlier migration SHA unchanged,
V33 unchanged after application, git diff --check clean. No configured production
provider/deployment claimed; explicit approved source endpoints and published policy
remain runtime requirements. V34+ was next at the Phase 7 checkpoint; Phase 8 is now
COMPLETE at verified V35. Final approved backend plan gates PASS, next schema V36+:
[final verification](backend-plan-final-verification.md). No policy choice is reopened.
The verified incoming baseline is 302 reported/301 executed, one legacy skip,
94 real PostgreSQL methods and V29 clean/upgrade/validate PASS.
