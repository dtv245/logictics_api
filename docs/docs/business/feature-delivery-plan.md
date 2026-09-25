# Kế hoạch bàn giao tính năng Logistics API

## 1. Mục đích

Tài liệu này là bản đồ thực thi cho quá trình đối chiếu tài liệu nghiệp vụ với mã nguồn Spring hiện tại và bàn giao từng lát cắt tính năng có thể kiểm chứng. Tài liệu không thay thế đặc tả chi tiết; trạng thái `đã có trong tài liệu`, `đã có bảng/entity`, `đã có API` và `đã được kiểm thử` luôn được phân biệt.

## 2. Thứ tự ưu tiên nguồn

Khi các tài liệu mâu thuẫn, dùng thứ tự sau:

1. `docs/docs/project-specification.vi.md` cho mã yêu cầu và truy vết BR/FR/US/UC/TC.
2. `docs/docs/business-spec.md` cho quy tắc, công thức và state machine.
3. `docs/docs/ai-dispatch.md`, `docs/docs/invoices.md`, `docs/docs/customer-payments.md` và `docs/docs/stripe-connect.md` cho luồng chuyên đề.
4. `docs/docs/development/engineering-conventions.md`, `docs/docs/frontend-context.md`, Java source, Flyway và runtime OpenAPI cho contract Spring hiện hành.
5. `docs/docs/features.md` và tài liệu .NET cũ chỉ làm inventory/target reference khi chưa được source Spring xác nhận.

## 3. Kết quả sản phẩm cần đạt

Lát cắt MVP phải chứng minh được chuỗi:

`Create Load → Dispatch Trip → Driver pickup/delivery → POD → Issue Invoice → Customer payment`

Mỗi bước chỉ được đánh dấu hoàn tất khi có đủ business rule, API/data contract, authorization/tenant isolation, validation, automated test và bằng chứng build. Marketing claim hoặc sự tồn tại của schema/entity không phải bằng chứng hoàn tất.

## 4. Người dùng và phạm vi

Các actor hiện được ghi nhận là SuperAdmin, Admin, Owner, Manager, Dispatcher, Driver và Customer. Finance/Payroll cùng Compliance/Safety là actor nghiệp vụ chưa có role mapping chính thức.

Phạm vi ưu tiên:

- Nền tảng tenant, JWT/RBAC và feature/subscription gate.
- Master data phục vụ điều phối: customer, employee/driver/license, truck và terminal.
- Luồng doanh thu Load, Trip, Driver execution, document/POD, invoice và payment.
- Audit, notification, dashboard vận hành cơ bản và các release gate.

Chưa đưa vào MVP nếu chưa có quyết định mới: autonomous AI, load-board automation, MCP write tools, payroll/compliance đầy đủ, marketing CMS, regional legal claims và scale/DR guarantee.

## 5. Trình tự bàn giao

1. Chốt requirement blockers: launch market, role-permission matrix, canonical units, finance contract, mobile offline/idempotency và NFR pháp lý.
2. FR-01 Auth/Tenant → FR-02 Provision/RBAC/Invitation → FR-14 Subscription/Feature gating.
3. FR-03 Customer → Employee/Driver/License → Truck/Terminal.
4. FR-04 Create Load → eligibility → dispatch.
5. FR-05 Trip → FR-06 Driver pickup/delivery → FR-11 POD/notification/tracking.
6. FR-08 Invoice → Stripe Connect → customer partial/full payment → idempotent webhook.
7. FR-13 dashboard cơ bản, audit/security, backup/restore, UAT và production review.
8. Phase sau MVP: FR-07, FR-09, FR-10, phần nâng cao của FR-11/13, rồi FR-12/15.

## 6. Quy ước trạng thái

| Trạng thái | Ý nghĩa |
|---|---|
| `VERIFIED` | Contract và hành vi trong phạm vi đã có automated evidence chạy thành công. |
| `PARTIAL` | Có một phần source/API/test nhưng chưa thỏa toàn bộ acceptance gate của FR. |
| `SCHEMA_ONLY` | Chủ yếu mới có bảng/entity; chưa có application/API flow hoàn chỉnh. |
| `NOT_IMPLEMENTED` | Chưa tìm thấy implementation Spring phù hợp. |
| `BLOCKED_DECISION` | Không nên build tiếp trước khi product/business chốt semantics. |

## 7. Release gate chung cho từng lát cắt

- Requirement ID và business rule có liên kết hai chiều tới code/API/test.
- Tenant isolation và authorization được kiểm tra phía server.
- Mutation validation không để lại trạng thái dở dang; lỗi dùng envelope hiện hành.
- Migration tương thích và có rollback/forward-fix strategy nếu thay đổi dữ liệu.
- Test liên quan, `./mvnw test` và quality gates chạy thành công.
- GitNexus impact được kiểm tra trước khi sửa symbol; change impact được kiểm tra sau khi sửa.
- Handoff của vai trò được append vào `.ai-workflow/PROJECT_MEMORY.md` và validator trả `OK`.

## 8. Quyết định còn mở

| ID | Quyết định cần chốt | Tác động |
|---|---|---|
| `BLK-001` | Launch market/pilot và vertical đầu tiên | Tax, HOS, fields, currency, intermodal scope. |
| `BLK-002` | Role-permission matrix, gồm Finance và Compliance | API authorization và acceptance tests. |
| `BLK-003` | Canonical distance/weight/volume/temperature units | Payroll, HOS, reports và AI scoring. |
| `BLK-004` | Invoice lifecycle, payment API/token, currency/refund/tax rules | FR-08 contract và data migration. |
| `BLK-005` | Mobile offline/idempotency và status semantics | FR-06 API, retry và conflict handling. |
| `BLK-006` | SLA, RPO/RTO, retention, MFA và legal constraints | Production readiness và infrastructure. |

## 9. Baseline kiểm thử

Ngày 2026-08-14, `./mvnw test` chạy thành công 155 test, không failure/error/skip; Checkstyle, Spotless và SpotBugs cũng thành công. Đây là baseline của phần hiện có, không chứng minh các feature còn thiếu đã hoàn tất.

Sau SLICE-001 ngày 2026-08-15, `./mvnw test` chạy 163 test và `./mvnw verify` chạy thêm 28 integration test với PostgreSQL/Flyway, REST API và Redis; tất cả đều thành công. Các con số này chỉ xác nhận lát cắt và hồi quy hiện tại.

Sau SLICE-002 ngày 2026-08-15, 14 test tập trung và toàn bộ 177 unit/architecture test đều đạt; `./mvnw verify` tiếp tục đạt 28 integration test. Luồng HTTP/event/PostgreSQL tạo invoice `draft`, dispatch Load và đọc lại invoice `issued` đã chạy thành công.

Sau SLICE-003 ngày 2026-08-15, 21 test tập trung, 191 unit/architecture test và 28 integration test đều đạt. Luồng A/B/C trên một PostgreSQL chứng minh actor mismatch `403`, participant/nonparticipant `404`, unread `0/1` và mark-read `1→0`; security scan có 0 finding và code review `LOW — APPROVE`. E2E hai tenant/database chưa có và vẫn thuộc `RSK-003`.

## 10. Ma trận nghiệp vụ và implementation Spring

| FR | Năng lực | Trạng thái | Bằng chứng hiện có | Khoảng trống chính | Bước kế tiếp |
|---|---|---|---|---|---|
| FR-01 | Auth và tenant context | `PARTIAL` | `SecurityConfiguration`, tenant routing/provisioning/migration, JWT/RBAC tests, `GET /api/me` và messaging actor binding đã verify | Không login/logout/refresh, API key hoặc `X-Tenant`; tenancy mặc định tắt; chưa có messaging E2E hai tenant | Dùng current-user mapping cho document attribution/authorization và bổ sung tenant-enabled E2E theo từng endpoint. |
| FR-02 | Tenant, user, role, invitation | `PARTIAL` | Role CRUD/claims; registry/provisioning services | Không user/invitation/tenant admin API; master subscription/user model chưa port | Chốt role matrix và thiết kế tenant/user boundary. |
| FR-03 | Customer, employee/driver/license, truck, terminal | `PARTIAL` | Customer và Terminal CRUD được test tốt; Employee CRUD/Driver view và Truck CRUD có sẵn | License chỉ data; truck chưa bắt buộc main driver/chống gán trùng/VIN | Hoàn thiện từng master-data slice theo dependency. |
| FR-04 | Load management | `PARTIAL` | Load CRUD/search, dispatch/pick-up/deliver/cancel, state-machine/driver-share tests, dispatch→invoice E2E và SLICE-005 bounded actor/assignment authorization đã verify | Chưa proximity/trusted location, eligibility, PDF import, tracking/public link/load-board hoặc tenant-enabled two-database proof | Chốt trusted location/proximity và các load execution gaps trong lát cắt riêng. |
| FR-05 | Trip planning/execution | `PARTIAL` | Trip CRUD/stops/dispatch/complete/cancel và workflow test | Không `MarkStopArrived`, `InTransit`, optimizer/fallback hoặc load cascade | Chốt lifecycle mới trước khi đổi enum/state. |
| FR-06 | Driver mobile execution | `PARTIAL` | Driver projection, Load lifecycle, current-user mapping và SLICE-005 server-owned actor/assignment gate đã verify | Chưa GPS/proximity, offline/idempotency, TripStop/POD flow hoàn chỉnh hoặc cross-tenant production proof | Thiết kế trusted tracking/proximity và mobile retry/POD như lát cắt riêng. |
| FR-07 | Container và terminal | `PARTIAL` | Terminal CRUD hoàn chỉnh; Container entity/repository/internal lookup | Không Container controller/CRUD/state transition API | Chốt intermodal có thuộc pilot/MVP không. |
| FR-08 | Invoice, payment và Stripe | `PARTIAL` | Invoice/Payment CRUD; canonical/legacy Draft được dispatch listener chuyển thành lowercase `issued`; domain/listener/E2E tests đạt | Generic status CRUD/search vẫn raw/case-sensitive; không line-item/actions/reconciliation/payment link/Stripe/Connect/webhook | Chốt finance lifecycle/locking/idempotency trước public payment và global status normalization. |
| FR-09 | Time, payroll, expense, tax | `SCHEMA_ONLY` | Một số entity và tenant schema | Không application/API workflow; enum/công thức tài liệu mâu thuẫn | Chốt payroll/tax semantics, xếp Phase 2. |
| FR-10 | ELD/HOS, safety, maintenance | `PARTIAL` | Inspection CRUD/defects được test; nhiều entity/schema | DVIR, accident, HOS/ELD, behavior, maintenance chưa có application/API | Chốt provider/region, xếp Phase 2. |
| FR-11 | Documents, messaging, notification, tracking | `PARTIAL` | Messaging principal/participant scoping và TC-018 đã verify; REST documents/notifications và `GET /api/me` có sẵn | Legacy messaging actor IDs còn là required assertions; notification tenant-wide; document attribution/access/upload policy yếu; không realtime/tracking | Dùng current-user mapping cho document attribution và chốt document relation/access + MIME/size/storage policy trước khi sửa upload. |
| FR-12 | Load board, AI dispatch, MCP | `SCHEMA_ONLY` | AI/load-board/API-key entities và tables | Không repositories/services/controllers/workflow/MCP transport | Phase 3 sau feature gate, telemetry và HITL guardrail. |
| FR-13 | Dashboard và reports | `NOT_IMPLEMENTED` | Không tìm thấy application/API slice | Thiếu KPI queries, currency/timezone rules và authorization | Dashboard vận hành cơ bản sau dữ liệu MVP. |
| FR-14 | Subscription và feature gating | `NOT_IMPLEMENTED` | Không có Spring application/API implementation | Không plan/subscription/usage/webhook/server gate | Thiết kế sau tenant/user boundary; cần trước paid production. |
| FR-15 | Privacy và marketing | `NOT_IMPLEMENTED` | Tenant schema không tạo thành workflow Spring | Không consent/export/delete/legal hold/contact/demo/blog API | Chốt launch-market legal baseline; thực hiện sau MVP core. |

Ghi chú bằng chứng:

- Spring hiện có 16 production controllers và khoảng 81 mapped handler methods sau SLICE-001.
- Tenant Flyway V1 tạo khoảng 50 bảng, nhưng nhiều bảng chỉ là schema chuyển đổi từ hệ thống cũ.
- `docs/docs/frontend-context.md` và source Java là baseline API hiện hành; tài liệu .NET/SignalR/MCP không phải bằng chứng implementation.
- Defect `LoadDispatchedInvoiceListener` tìm TitleCase trong khi fixture dùng lowercase đã được SLICE-002 sửa và xác minh; generic invoice status contract vẫn chưa được chuẩn hóa.

## 11. Lát cắt đầu tiên: current-user mapping

**Trạng thái:** `VERIFIED` ngày 2026-08-15.

### Mục tiêu

Thêm `GET /api/me` để ánh xạ JWT đã xác thực (`sub`, `email`, `tenant`, role/roles) sang employee trong tenant hiện tại. Lát cắt thuộc FR-01/FR-02 và là dependency cho driver, messaging, notification và document attribution.

### Acceptance criteria

1. Request không có Bearer JWT hợp lệ trả `401` theo envelope hiện hành.
2. Request hợp lệ trả `200` với subject, email, tenant ID, normalized roles và `employeeId` nếu email khớp employee trong tenant đang route.
3. Identity hợp lệ nhưng chưa có employee vẫn trả identity với `employeeId = null`; endpoint không biến account platform/Owner thành lỗi giả.
4. Client không được truyền hoặc chọn `employeeId`; server chỉ lookup bằng claim đã xác thực.
5. Không đọc tenant khác; khi tenancy bật, lookup dùng routed tenant database đã được JWT tenant filter thiết lập.
6. Email claim trống vẫn trả identity theo subject nhưng không lookup employee; không được query bằng chuỗi rỗng.
7. Controller, service và security tests chứng minh các nhánh có mapping, không mapping và unauthenticated.
8. Không mở rộng `EmployeeService` dùng chung nếu một lookup service riêng đáp ứng được yêu cầu.

### Kết quả thực hiện

- `GET /api/me` trả `ApiResponse<CurrentUserResponse>` gồm `subject`, nullable `email`, `tenantId`, danh sách role đã chuẩn hóa và nullable `employeeId`.
- Sáu file production mới giữ boundary identity → public employee lookup → repository; không sửa `EmployeeService`, `SecurityConfiguration`, entity hoặc Flyway.
- Ba test class với 8 test tập trung chứng minh mapping, không mapping, missing email/no-query, role normalization và unauthenticated `401`.
- Hồi quy đạt 163 unit/architecture test và 28 integration test; Checkstyle, Spotless, SpotBugs và JAR packaging đều đạt.
- Security diff scan phủ 9/9 file và bốn bề mặt, không có finding hoặc deferred item. Code review xác nhận patch đúng 9 file/381 dòng thêm và không có defect.
- Contract và quyết định kiến trúc nằm tại `docs/docs/architecture/adr-001-current-user-endpoint.md`; lịch sử vai trò nằm tại `.ai-workflow/PROJECT_MEMORY.md`.

### Ngoài phạm vi lát cắt

- Không tự thêm login/logout/refresh hoặc sửa IdentityServer ngoài repo.
- Chưa sửa messaging/notification authorization trong cùng thay đổi.
- Chưa thêm `X-Tenant`, MCP API key hoặc subscription gate khi precedence và threat model chưa được duyệt.

## 12. Lát cắt thứ hai: Load dispatch issue draft invoice

**Trạng thái:** `VERIFIED` ngày 2026-08-15.

SLICE-002 truy vết FR-04/FR-08 và sửa đúng quy tắc Load dispatch → issue invoice Draft hiện hữu. Bản vá gồm bốn file production và ba file test, không đổi API/DTO/schema/DataSeeder:

- canonical `draft` và legacy `Draft` chuyển thành lowercase `issued`;
- không có invoice, non-Draft và replay tuần tự đều là no-op;
- 14 test tập trung, 177 unit/architecture và 28 integration test đạt;
- HTTP/event/PostgreSQL persistence đã được chứng minh trong `ApiFunctionalIT`;
- security review có 0 finding; code review `LOW — APPROVE`; GitNexus báo LOW và 0 affected process.

FR-04 và FR-08 vẫn `PARTIAL`. Fault-injection rollback/concurrent-update coverage được ghi thành `RSK-005`; public payment, tax, Stripe, reconciliation và global invoice lifecycle vẫn bị chặn bởi `BLK-004`.

Product Owner chấp nhận lát cắt theo `DEC-012`: bằng chứng transaction hiện là structural plus successful E2E, còn fault-injection và concurrent-update không được tuyên bố đã kiểm thử.

## 13. Lát cắt thứ ba: principal-bound messaging authorization

`SLICE-003` truy vết FR-01/FR-06/FR-11: dùng current-user mapping để loại bỏ sender/reader identity do client tự chọn trong messaging.

**Trạng thái:** `VERIFIED` ngày 2026-08-15.

Phạm vi đã chốt tại `docs/docs/business/slice-003-messaging-principal-scope.md`: giữ request shape cũ trong một compatibility phase nhưng mọi asserted actor ID phải khớp current employee; conversation detail/message list/send/mark-read participant-scoped; create luôn thêm current employee. Customer chat, implicit tenant-wide membership, realtime và load-thread eligibility policy không nằm trong lát cắt này.

Kết quả:

- đúng 8 file production và 6 file test của ADR-003; không đổi DTO, mapper, security matcher, schema, DataSeeder hoặc wire contract;
- 21 focused, 191 unit/architecture và 28 integration test PASS;
- A/B/C chứng minh cùng database: mismatch `403`, nonparticipant/SuperAdmin `404`, sender/recipient unread `0/1`, mark-read `1→0`;
- sealed security scan 0 finding, 7 surface; code review `LOW — APPROVE`, không có blocking finding;
- `RSK-006` được giảm thiểu cho REST surface hiện tại.

Giới hạn bắt buộc: AC11 chỉ `PARTIAL` vì chưa có tenant-enabled two-database messaging E2E. Repeated mark-read không tạo receipt trùng nhưng vẫn cập nhật `lastReadAt`. Generic service methods, concurrent mark-read/membership changes, N+1 và unbounded receipt processing là follow-up, không được mô tả là đã giải quyết.

## 14. Lát cắt thứ tư: principal-bound document upload attribution

`SLICE-004` truy vết FR-01/FR-06/FR-11 cho endpoint multipart hiện có. Business contract tại `docs/docs/business/slice-004-document-principal-attribution.md` đã chốt lát cắt nhỏ nhất không bị quyết định sản phẩm cản trở:

- `uploadedById` được giữ required trong một compatibility phase nhưng chỉ là assertion phải khớp current Employee từ JWT;
- persisted uploader và blob-key prefix luôn lấy từ current Employee do server xác định;
- identity không map Employee hoặc assertion giả mạo trả `403` trước mọi document/blob mutation;
- tên file rỗng, traversal/directory hoặc chứa C0/DEL control character bị từ chối trước storage;
- wire contract, role matcher và các endpoint search/detail/download/delete được giữ nguyên.

**Trạng thái:** `VERIFIED_BOUNDED_ACCEPTED`. Product Owner đã chấp nhận bounded outcome ngày 2026-08-22 theo DEC-023. Exact 3 production + 3 test files của ADR-004 đã được triển khai; focused controller/service đạt 9/9, full unit/architecture đạt 198/198 và integration đạt 28/28 (21 API functional + 7 Redis), không có failure/error/skip. Real PostgreSQL multipart path chứng minh forged uploader bị `403`, matching principal được persist rồi list/download/delete thành công; Checkstyle, Spotless, SpotBugs và build đều sạch. Sealed six-file security diff scan có 0 finding và independent code review kết luận `LOW — APPROVE`, không có blocking correctness/API/security finding; focused 9/9 rerun PASS. Coverage chỉ `partial` vì chưa chạy real JwtDecoder + active registry + two-database document fixture. GitNexus pre-edit impact vẫn LOW cho upload chain; post-change scan của toàn dirty worktree báo CRITICAL vì cộng dồn các slice trước, không phải scope riêng SLICE-004. Tài liệu business/ADR đã đồng bộ; project vẫn `NOT READY`, FR-11 vẫn PARTIAL và các giới hạn BLK-008/RSK-003/RSK-007 còn mở. Lát cắt tiếp theo được xếp hàng để Business Analyst phân tích read-only về actor/assignment rules cho load actions.

Không gộp relation authorization hoặc MIME/size/malware vào lát cắt này. Tài liệu nghiệp vụ chưa chốt actor-by-relation matrix, Driver assignment/bypass, size limit, MIME allowlist, scanner/quarantine, idempotency, retention và delete semantics. Các mục đó thuộc `BLK-008`; broad read/download và weak content policy vẫn là `RSK-007` HIGH. Vì vậy SLICE-004 không hoàn tất API-DOC-01/API-DOC-02, TC-017 hoặc toàn bộ FR-11.

## 15. Lát cắt thứ năm: load action actor, assignment và proximity

`SLICE-005` là lát cắt bounded cho `POST /api/loads/{id}/pick-up` và
`POST /api/loads/{id}/deliver`, truy vết FR-04/FR-06 và TC-007, đã đi qua business,
architecture, implementation, QA, security và code-review gates.

**Trạng thái:** `VERIFIED_BOUNDED_ACCEPTED` ngày 2026-08-23 theo DEC-025.

Phân tích tại `docs/docs/business/slice-005-load-action-actor-assignment.md` xác nhận
state machine hiện có và tìm thấy mâu thuẫn quan trọng: business target yêu cầu
assigned Driver + proximity, trong khi frontend contract và `SecurityConfiguration`
cho mọi tenant role gọi endpoint, còn service không resolve actor/assignment/location.
`isInProximity` hiện là client-supplied field; không được dùng như server proof.

Product Owner đã chấp nhận Option A: bind current Employee từ JWT; chỉ assigned
truck main/secondary driver được xác nhận; operator bypass phải có explicit
permission; proximity defer tới khi có trusted location source. ADR-005 đã chốt
`permission=load.confirm_status`, denial `ACCESS_DENIED` trước save và transaction
boundary; không dùng role name hoặc `isInProximity` làm proof.

ADR kiến trúc: `docs/docs/architecture/adr-005-load-action-actor-assignment.md`.

Implementation/review evidence: đúng 3 production files trong ADR-005; focused
`10/10`, full Maven unit `208/208`, PostgreSQL `ApiFunctionalIT 21/21`, security
diff scan `0` reportable findings và Code Review `LOW — APPROVE`. Functional
evidence chứng minh main/secondary driver, explicit permission, mismatch `403`,
missing/null actor/driver fail-closed và retry không đổi timestamp. Coverage vẫn
`PARTIAL` vì chưa có tenant-enabled two-database/live-decoder proof; do đó không
tuyên bố BR-03/TC-007, cross-tenant production isolation hoặc toàn bộ FR-06 hoàn tất.
Product Owner đã chấp nhận bounded outcome theo DEC-025. Lát cắt kế tiếp chỉ
được mở sau một vòng phân tích read-only mới; proximity/tracking, TripStop,
offline/idempotency, durable audit và tenant-enabled two-database proof không
được suy ra từ kết quả này.
