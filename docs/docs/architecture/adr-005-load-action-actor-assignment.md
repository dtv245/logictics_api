# ADR-005: Principal-bound load pickup/delivery actions

- **Status:** `Implemented, independently reviewed and bounded-accepted` — 2026-08-23
- **Decision owner:** Product Owner / Software Architect
- **Scope:** `POST /api/loads/{id}/pick-up` and `POST /api/loads/{id}/deliver`
- **Requirements:** FR-04, FR-06, BR-02, BR-03, BR-07, US-05, US-07, API-LOAD-05, API-DRIVER-02, TC-005, TC-006, TC-007

## Context

The current Spring routes allow every tenant role to call pickup/delivery and
`LoadServiceImpl` performs only a state transition. The business target requires
the authenticated assigned Driver (or an explicitly authorized operator) and
eventually a proximity check. `Load` already stores an assigned Truck with main and
secondary Driver relations, but no trusted location source exists. The
`isInProximity` request field is client-controlled and cannot authorize an action.

Product Owner accepted Option A: add server-owned actor/assignment authorization
now, defer proximity/tracking, and keep TripStop/offline/idempotency semantics out of
this slice.

## Decision

1. `LoadController` receives the authenticated `JwtAuthenticationToken` and resolves
   the tenant-local Employee ID through the existing `CurrentUserService`.
2. The controller passes that server-resolved ID to the load service; the request
   path remains `/api/loads/{id}/pick-up` or `/api/loads/{id}/deliver` and has no
   client actor field.
3. `LoadServiceImpl` authorizes before calling `Load.pickUp()`/`Load.deliver()`:
   - the actor is allowed when their Employee ID matches the assigned Truck's
     `mainDriver` or `secondaryDriver`;
   - an operator bypass is allowed only when the tenant-local Employee role has an
     explicit permission claim `load.confirm_status`; role membership alone is not
     sufficient;
   - missing Employee, missing assigned Truck, non-matching Driver and missing
     permission fail closed with `ACCESS_DENIED` and do not save the Load.
4. Authorization is performed inside the existing service transaction after the
   Load is loaded and before the state transition. The state machine remains the
   source of truth for invalid state and timestamp behavior.
5. The existing API envelope and status-transition errors remain unchanged. A
   denied actor uses the existing access-denied handler; no resource existence
   detail is added.
6. No proximity decision is made in this slice. `isInProximity`, Truck current
   coordinates and client-supplied location are not authorization inputs.

## Permission mapping

`load.confirm_status` is a tenant-role permission claim, not a Spring route role.
The implementation must read it from the tenant-local Employee/role data using the
existing Employee service boundary. If the repository cannot safely expose role
claims through that boundary, stop and hand back to Architecture rather than using
`SecurityConfiguration` role arrays as a bypass.

## Data and transaction behavior

- No migration or new table is needed; `loads.assigned_truck_id`, trucks' main and
  secondary driver FKs, Employee role claims and the existing Load timestamps are
  sufficient.
- Authorization failure must occur before `save` and before any event publication.
- Successful pickup/delivery uses the existing `@Transactional` service method and
  saves exactly once after the entity transition.
- This slice does not add an audit table/event. Existing request/exception logging is
  retained; a durable actor audit is a follow-up if required by the NFR baseline.

## API compatibility

- No request DTO or response field changes.
- No new endpoint.
- Existing unauthenticated/route role checks remain; service authorization is the
  record-level gate.
- Existing clients that call the endpoint as an unassigned tenant role will now
  receive access denied. This is an intentional contract correction under DEC-024.

## Acceptance criteria

1. Assigned main Driver can pick up a dispatched Load and deliver a picked-up Load.
2. Assigned secondary Driver can perform the same actions.
3. An unrelated Driver receives access denied and the Load status/timestamps remain
   unchanged.
4. An Employee with `permission=load.confirm_status` can perform an operator bypass;
   an Employee without it cannot bypass assignment.
5. Missing current Employee mapping, missing assigned Truck and null driver links
   fail closed without persistence.
6. Draft/delivered/cancelled invalid transitions still return
   `INVALID_STATE_TRANSITION` and do not mutate.
7. The action does not inspect or trust `isInProximity`; proximity remains an
   explicit unimplemented requirement until a tracking slice is approved.
8. Real PostgreSQL tests cover matching/mismatching actors and tenant-local lookup;
   a tenant-enabled two-database test is required before claiming cross-tenant
   production proof.

## Authorized implementation scope

Expected production files:

1. `src/main/java/com/company/logicstic/modules/load/controller/LoadController.java`
2. `src/main/java/com/company/logicstic/modules/load/service/LoadService.java`
3. `src/main/java/com/company/logicstic/modules/load/service/impl/LoadServiceImpl.java`

Expected tests are a focused load controller/service authorization test and the
existing `ApiFunctionalIT` load lifecycle section. Any Employee repository/service
change, security matcher change, entity change or migration requires a fresh
impact report and Architecture handback.

## Impact evidence

GitNexus pre-edit impact was run on `LoadController.pickUp`,
`LoadController.deliver`, `LoadService.pickUp`, `LoadService.deliver` and
`LoadService` interface. The indexed graph returned LOW with zero mapped upstream
callers/processes for the methods, but marked results partial/stale relative to
untracked worktree history. `CurrentUserService.requireCurrentEmployeeId` was not
present in the index. Manual source search found the controller as the production
route caller and existing `EmployeeService.getEntityById` use in
`LoadServiceImpl`; this limitation must be carried into code review.

## Explicit exclusions

- proximity/GPS/geofencing, threshold/units and stale-location policy;
- TripStop arrival, trip state cascade and optimizer behavior;
- Driver HOS/license/hazmat eligibility;
- offline retry/idempotency and durable actor audit;
- broad GET/search/detail record authorization and tenant-enabled two-database E2E.

## Verification plan

Completed evidence:

- Focused `LoadControllerTest` and `LoadActionAuthorizationTest`: `10/10` pass;
  Checkstyle, Spotless and SpotBugs pass.
- Full `./mvnw test`: `208/208` pass with no failures, errors or skips.
- PostgreSQL Testcontainers `ApiFunctionalIT`: `21/21` pass, including a
  mismatched employee denial before the assigned-driver lifecycle and a repeated
  delivery that preserves the original timestamp.
- `gitnexus_detect_changes(unstaged)` reports cumulative `CRITICAL` across the
  dirty worktree; pre-edit impacts for the SLICE-005 targets were `LOW/partial`,
  and the exact three-production-file scope was manually reconciled.
- Bounded Codex Security diff scan: `0` reportable findings across 27 inventory
  items; coverage is `PARTIAL` because tenancy-disabled one-database fixtures do
  not prove two-tenant collisions or live JWT decoder behavior.
- Independent code review verdict: `LOW — APPROVE`, with no blocking
  correctness/API/authorization/transaction finding.

The slice is approved only for actor/assignment authorization. Proximity,
TripStop, offline/idempotency, durable audit, broad load-read authorization and
tenant-enabled two-database evidence remain explicit follow-ups.

## Product acceptance

Product Owner accepted the bounded outcome on 2026-08-23 under DEC-025. This ADR
is evidence that the actor/assignment boundary is implemented and reviewed; it is
not evidence that trusted proximity, complete FR-06, BR-03/TC-007 or cross-tenant
production isolation is complete.
