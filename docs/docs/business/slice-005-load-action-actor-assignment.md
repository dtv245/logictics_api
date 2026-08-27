# SLICE-005: Load action actor, assignment and proximity contract

- **Status:** `VERIFIED_BOUNDED_ACCEPTED`
- **Date:** 2026-08-23
- **Role:** Product Owner
- **Requirements:** FR-04, FR-06, BR-02, BR-03, BR-07, US-05, US-07, API-LOAD-05, API-DRIVER-02, TC-005, TC-006, TC-007

## 1. Objective

Xác định hợp đồng nghiệp vụ cho `POST /api/loads/{id}/pick-up` và
`POST /api/loads/{id}/deliver` trước khi sửa code. Lát cắt này không triển khai
proximity/GPS hoặc Trip stop workflow; nó chỉ làm rõ ai được phép xác nhận và
điều kiện nào phải được kiểm tra tại server.

## 2. Evidence hierarchy

### Business target

- `docs/docs/project-specification.vi.md` FR-04 mô tả pickup/delivery là hành động
  của Driver, yêu cầu state + proximity; BR-03 nói pickup từ Dispatched và delivery
  từ PickedUp chỉ được chấp nhận khi gần điểm.
- Cùng tài liệu mô tả US-07 là Driver xem trip được gán và xác nhận stop; TC-007
  yêu cầu từ chối driver ở xa và không mutation.
- `docs/docs/architecture/domain-model.md` mô tả `Load.UpdateProximity(true)` là
  tín hiệu cho nút xác nhận của driver, nhưng Spring hiện không có method/API này.

### Current Spring contract

- `frontend-context.md` hiện khai báo cả pickup và deliver cho **mọi tenant role**;
  `SecurityConfiguration` bảo vệ hai route bằng `TENANT_ROLES` gồm
  `SUPERADMIN`, `OWNER`, `MANAGER`, `DISPATCHER`, `DRIVER`.
- `LoadController` nhận chỉ `id`; không nhận actor, coordinates, device proof hay
  idempotency key.
- `LoadServiceImpl.pickUp/deliver` chỉ load entity, gọi state method và save.
  Không có current-user lookup, assigned-driver check, proximity check,
  eligibility/license/HOS/hazmat check hoặc audit actor.
- `Load` có `assignedTruck`, `assignedDispatcher` và `isInProximity`, nhưng
  `isInProximity` là field của request tạo/cập nhật load và không phải bằng chứng
  vị trí do server xác minh. Driver hiện được nhận diện qua role claim
  `permission:update_trip_status` trong driver picker; không có relation từ JWT
  employee tới action.
- Test hiện có chỉ chứng minh state machine và timestamp; `ApiFunctionalIT` chạy
  dispatch → pick-up → deliver bằng một authenticated fixture, không chứng minh
  assignment, actor mismatch, proximity hoặc tenant-isolated denial.

## 3. Contract conflict

| Topic | Business target | Current API/source | Consequence |
|---|---|---|---|
| Actor | Assigned Driver hoặc authorized operator | Mọi tenant role | Không thể triển khai authorization mà không thay đổi policy |
| Assignment | Driver phải thuộc load/trip/truck context | Load chỉ giữ assigned truck + dispatcher | Cần chọn relation chuẩn và cách xử lý secondary driver |
| Proximity | Server chặn khi chưa gần stop | `isInProximity` client-supplied; không có GPS/tracking source | Không an toàn nếu dùng field hiện tại làm proof |
| Trip coupling | Pickup/delivery có thể cập nhật TripStop | Load endpoint độc lập; Trip chỉ draft/dispatched/completed | Cần tách load-only slice khỏi trip arrival semantics |
| Bypass | Tài liệu có “authorized operator” nhưng không nêu role/permission | Operations role có dispatch/cancel; tenant role có pickup/deliver | Cần Product Owner chốt bypass và audit |

## 4. Proposed stories and acceptance criteria

### US-05A — Authorized operator action

**As a** Dispatcher/Manager/Owner,
**I want** a documented operator permission for exceptional load status updates,
**so that** office staff can recover a driver exception without silently acting
as an arbitrary driver.

### US-07A — Assigned driver action

**As a** Driver,
**I want** to confirm pickup/delivery only for a load assigned to me through the
approved truck/trip relation,
**so that** another driver cannot change shipment state.

### Minimum acceptance criteria after policy approval

1. Given a load not in the required state, when pickup/delivery is called, then
   the existing `INVALID_STATE_TRANSITION` response remains unchanged and no
   timestamp is written.
2. Given a Driver whose JWT employee is not the approved assigned actor, when the
   action is called, then the API returns the approved denial (`403` or
   indistinguishable `404`) and the Load remains unchanged.
3. Given the approved assigned Driver, when the action is called, then status and
   timestamp update atomically and the response uses canonical lowercase status.
4. Given an operator bypass role, when the action is called, then the bypass is
   allowed only if the approved permission is present and an actor/audit record is
   written; role name alone is not sufficient.
5. Given a request without server-verifiable proximity, when proximity is a
   required rule, then the action is rejected or remains unavailable; a client
   boolean cannot satisfy the rule.
6. Given two tenants with colliding Load/Employee UUIDs, when one tenant's actor
   calls the other tenant's action, then routing/authorization prevents lookup or
   mutation in the other database.
7. Given a retry of the same accepted action, then the terminal/state-machine
   contract is deterministic and no duplicate event/timestamp is created.

## 5. Options requiring Product Owner decision

### Option A — Assignment gate first (recommended bounded slice)

- Bind the actor from JWT current Employee.
- Permit only the assigned truck's main/secondary driver, plus an explicitly
  approved operator bypass permission.
- Keep proximity out of this slice and mark the endpoint as not yet satisfying
  BR-03/TC-007 until a trusted location source exists.
- Add negative/positive tests and preserve the current route/request shape.

### Option B — Full driver confirmation

- Add a dedicated driver confirmation contract carrying stop/location evidence,
  server-side proximity calculation, assignment and trip-stop checks.
- Requires tracking/location source, threshold/units, stale timestamp policy,
  offline/idempotency semantics and likely new API/schema work.
- This is not a safe next slice while BLK-003/BLK-005 and tracking are unresolved.

### Option C — Keep current broad role behavior

- Document that any tenant role may perform load-only pickup/delivery.
- Explicitly reject BR-03, US-07 and TC-007 as not implemented, and add audit/risk
  ownership before release.
- This minimizes code change but does not satisfy the stated Driver execution
  product requirement.

## 6. Recommendation and blockers

Recommend **Option A** as the smallest implementation-ready slice. It isolates
server-owned actor/assignment authorization from the unresolved GPS/tracking
design. It must not claim proximity compliance or full FR-06 completion.

Product Owner decision on 2026-08-23: **Option A is accepted**.

The accepted business boundary is:

1. the actor is resolved from the authenticated JWT/current Employee;
2. a Driver may act only when they are the assigned truck's main or secondary
   driver;
3. an authorized operator bypass is allowed only through an explicit permission
   that Architecture must map to the existing authorization model; role name alone
   is not sufficient;
4. proximity is deferred until a trusted tracking/location source and threshold
   policy exist; `isInProximity` must not be treated as proof; and
5. TripStop arrival, GPS, offline retry and idempotency remain separate slices.

The exact permission/claim name for operator bypass is an architecture concern and
must be frozen in ADR-005 before code changes. This is not permission to retain the
current broad tenant-role behavior.

The bounded implementation is now complete and reviewed. The implementation
keeps the accepted business boundary: no proximity/GPS proof is claimed, and
the operator path is not inferred from a role name alone.

## 8. Implementation and review evidence

- Exactly three production files changed: `LoadController`, `LoadService` and
  `LoadServiceImpl`. The controller resolves the JWT/current Employee; the
  service authorizes assigned main/secondary drivers or the tenant-local
  `permission=load.confirm_status` claim before the state transition and save.
- Focused controller/service tests pass `10/10`. They cover main and secondary
  drivers, explicit permission bypass, unrelated/null/unmapped actors, missing
  driver assignment, invalid state and no-save denial behavior.
- Full Maven unit/architecture tests pass `208/208` with no failures, errors or
  skips. Testcontainers PostgreSQL `ApiFunctionalIT` passes `21/21`, including
  mismatched-driver `403`, assigned-driver pickup/delivery and deterministic
  repeated-delivery state/timestamp behavior.
- Security diff scan reviewed 27 changed-file inventory items and found `0`
  reportable findings. Independent code review verdict is `LOW — APPROVE`.
- Coverage remains intentionally `PARTIAL`: the API fixture uses one PostgreSQL
  database with tenancy disabled and synthetic JWTs, so it does not prove
  colliding IDs across two tenant databases or live decoder behavior.

## 9. Verification and residual plan

- Preserve the ADR-005 exact three-production-file boundary and its recorded
  LOW/partial GitNexus impacts; cumulative dirty-worktree detection is not an
  isolated-slice risk score.
- Keep the focused, full-unit and PostgreSQL API evidence above as the bounded
  acceptance record.
- Add tenant-enabled two-database/colliding-ID and live-decoder evidence before
  making a cross-tenant production claim.
- Design trusted tracking/proximity, TripStop, offline/idempotency and durable
  actor-audit behavior as separate approved slices; do not retrofit them here.

## 10. Product acceptance

Product Owner accepts this slice as `VERIFIED_BOUNDED_ACCEPTED` on 2026-08-23
under DEC-025. The accepted result is limited to server-owned JWT/current
Employee binding, assigned truck main/secondary driver authorization, explicit
tenant-local `load.confirm_status` bypass, fail-closed denial before state
mutation/save, unchanged route/envelope/state-machine behavior and the measured
10/10, 208/208, 21/21, security-0, Code Review `LOW — APPROVE` evidence.

This acceptance does not close FR-06, BR-03, TC-007, proximity/tracking, durable
actor audit, offline/idempotency, broad load-read authorization or tenant-enabled
two-database/live-decoder production proof. Project readiness remains `NOT READY`.
