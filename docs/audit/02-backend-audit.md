# 02 — Backend audit

## 1. Kiến trúc ứng dụng

Backend là Spring Boot monolith có các lớp REST Controller → Service → Repository → JPA Entity, DTO/Mapper ở biên transport. Có common API envelope/pagination, validation, request ID, exception mapping, OpenAPI và một số command service riêng cho workflow tài chính. JPA đặt `ddl-auto: validate`, `open-in-view: false`, timezone JDBC là UTC trong [`application.yml`](../../src/main/resources/application.yml#L12).

Các domain có mức độ độc lập về service/entity, nhưng vẫn cùng một ứng dụng và persistence unit; không thấy service boundary/deployment boundary riêng. Có hai flow calculation engine liên quan shipment cost (package `service/cost` và `service/calculation`), nên thay đổi cost/profit cần đối chiếu projection và tests của cả hai.

## 2. Entities và schema

Schema tenant bắt đầu từ `V1__baseline_business_schema.sql` và hiện có migration đánh số đến V53 trong `src/main/resources/db/migration/tenant/`; có migration `obsolete/` bị `.disabled`, không phải migration được Flyway thực thi.

Quan hệ chính đọc được từ entity annotations:

- `Load` bắt buộc gắn `Customer`; tùy chọn `Truck`, dispatcher (`Employee`), `Container`, origin/destination `Terminal`; có tracking links/exceptions. Có optimistic version. ([`Load.java`](../../src/main/java/com/company/logicstic/entity/Load.java#L19), [`V1`](../../src/main/resources/db/migration/tenant/V1__baseline_business_schema.sql#L1))
- `Trip` gắn tùy chọn `Truck`, chứa `TripStop` và `TripDriverAssignment`; stop gắn cả `Trip` và `Load`; assignment gắn `Trip` và `Employee`/driver. ([`Trip.java`](../../src/main/java/com/company/logicstic/entity/Trip.java#L18), [`TripStop.java`](../../src/main/java/com/company/logicstic/entity/TripStop.java#L16), [`TripDriverAssignment.java`](../../src/main/java/com/company/logicstic/entity/TripDriverAssignment.java#L21))
- `ShipmentCost` có thể quy chiếu Load/Trip/Truck/Driver và calculation snapshot. `Invoice` gắn Load/Customer/Employee và invoice lines. Settlement gắn driver, pay period, policy, snapshot; payroll run có items, mỗi item có thể liên kết nhiều settlement. ([`ShipmentCost.java`](../../src/main/java/com/company/logicstic/entity/ShipmentCost.java#L22), [`DriverSettlement.java`](../../src/main/java/com/company/logicstic/entity/DriverSettlement.java#L11), [`PayrollRunItem.java`](../../src/main/java/com/company/logicstic/entity/PayrollRunItem.java#L12))
- Nhiều entity nghiệp vụ có `@Version` và migration bảo vệ immutable/audit history, đặc biệt billing, cost, settlement, payroll và optimization.

Có nhiều `@Table(schema="public")`; trong mô hình database-per-tenant, cùng schema `public` được tái sử dụng trong từng database tenant.

## 3. Authentication, authorization và RBAC

Backend có Lark OAuth authorization/callback, tạo JWT nội bộ, xác thực bearer token và map authorities; `/api/me` đối chiếu subject/tenant/role với employee của tenant. Cấu hình URL-level policy nằm ở [`SecurityConfig.java`](../../src/main/java/com/company/logicstic/config/SecurityConfig.java#L33), bật method security, CSRF disabled cho API, CORS chỉ nhận explicit origins. Lark callback được permit riêng; AI, invoice, payroll, core ops, documents, reports, fleet và Lark Base có nhóm role riêng.

**Khoảng trống OIDC cần ưu tiên xác minh:** frontend có OIDC gateway, kiểm tra access token bằng remote JWK Set rồi gửi access token đó cho API. Trong source backend, `SecurityConfig` đăng ký `LarkAuthenticationFilter` cho bearer authentication và có thể thêm `TenantJwtClaimFilter`; không thấy cấu hình `oauth2ResourceServer`/`JwtDecoder`. Lark filter đưa bearer token qua `LarkAuthService.validateInternalToken()`, parser dùng signing key HMAC nội bộ. Cũng không thấy luồng đổi OIDC access token thành JWT nội bộ. Vì vậy source hiện chứng minh được luồng Lark callback → internal JWT, nhưng chưa chứng minh được OIDC bearer token frontend được backend tin cậy; cần kiểm tra issuer/algorithm/bridge của deployment trước khi coi OIDC login hoạt động end-to-end. ([`LarkAuthenticationFilter.java`](../../src/main/java/com/company/logicstic/integration/lark/auth/LarkAuthenticationFilter.java#L40), [`LarkAuthService.java`](../../src/main/java/com/company/logicstic/integration/lark/auth/LarkAuthService.java#L375), [`SecurityConfig.java`](../../src/main/java/com/company/logicstic/config/SecurityConfig.java#L33))

Role authorization thực tế dùng `hasRole`/Spring authorities. Entity/API `/api/roles` quản lý tenant roles, nhưng đó là domain role store; JWT role claim là biên bảo mật. Frontend cũng có role matrix fail-closed, song đó chỉ điều khiển UX, không thay backend authorization.

Đăng nhập Lark lấy tenant từ cấu hình `defaultTenantId` trong [`LarkAuthService.java`](../../src/main/java/com/company/logicstic/integration/lark/auth/LarkAuthService.java#L354); callback không nhận tenant tùy chọn từ UI. Vì vậy luồng Lark hiện gắn với một tenant mặc định, trong khi OIDC access token bên ngoài có thể mang tenant claim khác.

## 4. Multi-tenancy và datasource routing

Khi `app.tenancy.enabled=true`, `TenantDataSourceConfiguration` tạo registry datasource riêng và `TenantRoutingDataSource` làm datasource chính. `TenantJwtClaimFilter` lấy claim `tenant`, yêu cầu claim tồn tại, kiểm tra mismatch và nạp pool chỉ sau khi registry xác nhận tenant ACTIVE. Router không lenient-fallback và không có default tenant datasource. Tenant context được clear trong `finally`. ([`TenantDataSourceConfiguration.java`](../../src/main/java/com/company/logicstic/config/TenantDataSourceConfiguration.java#L17), [`TenantRoutingDataSource.java`](../../src/main/java/com/company/logicstic/config/TenantRoutingDataSource.java#L15), [`TenantJwtClaimFilter.java`](../../src/main/java/com/company/logicstic/config/TenantJwtClaimFilter.java#L25))

**Giới hạn trong source hiện tại:** tenancy mặc định `false`; Compose không thiết lập registry URL/encryption key. `TenantProvisioningService` có thể tạo database, migrate rồi insert registry record nhưng không tìm thấy REST controller để gọi provisioning; registry table được tạo bằng `CREATE TABLE IF NOT EXISTS` trong `@PostConstruct`, không thuộc versioned Flyway. Provisioning cũng không có transaction xuyên database; lỗi giữa create/migrate/register có thể để lại database chưa đăng ký. Xem [`TenantProvisioningService.java`](../../src/main/java/com/company/logicstic/service/TenantProvisioningService.java#L28) và [`TenantRegistryService.java`](../../src/main/java/com/company/logicstic/service/TenantRegistryService.java#L31).

## 5. Trạng thái domain backend

| Domain | Đã thấy trong source | Giới hạn chính |
|---|---|---|
| Load | CRUD/search, relation resolution, version guard, assigned truck, pickup business-date audit, event publish/timeline. [`LoadService`](../../src/main/java/com/company/logicstic/service/LoadService.java#L57) | Status là string và cập nhật qua CRUD update; service có assign truck nhưng không thấy command API riêng cho toàn bộ dispatch/pickup/delivery/cancel lifecycle. Không nên coi UI status là state machine đã hoàn tất. |
| Trip / assignment | Trip CRUD; assign/unassign driver; assignment unassign được soft-close; stop actions ARRIVED → SERVICE_STARTED → SERVICE_COMPLETED → DEPARTED có kiểm tra trạng thái. [`TripExecutionService`](../../src/main/java/com/company/logicstic/service/TripExecutionService.java#L39) | Trip-level lifecycle (dispatch/complete/cancel) không xuất hiện như command riêng trong `TripController`; status được mang qua create/update. |
| Fleet / driver | Truck/driver/employee CRUD; driver profile, license, health, certification, insurance, contracts, bank accounts, violation, operational status; fleet history policy/events/mileage attribution. | Một số domain có entity/repository/service nhưng thiếu controller tương ứng (xem matrix); fleet-report UI route không được mount. |
| Trip execution | Stop/driver assignment endpoints và audited stop event fields. | Không thấy GPS/ELD provider ingestion hoặc tracking stream trong route/controller inventory. |
| Documents | Document metadata search/get/delete, quan hệ với load/employee/truck/stop/accident/DVIR/maintenance. | `DocumentController` không có upload/download route; download path trong FE không có handler backend. [`DocumentController`](../../src/main/java/com/company/logicstic/controller/DocumentController.java#L20) |
| Cost ledger | `ShipmentCost` create/verify/approve/void; trạng thái được kiểm tra; actual cost chỉ được tính sau approval; expense/maintenance projection. [`ShipmentCostEngine`](../../src/main/java/com/company/logicstic/service/cost/ShipmentCostEngine.java#L50) | Expense có approval/rejection endpoint nhưng không thấy CRUD controller. Chi phí hệ thống có source-projection contract; không nên tạo generic mutation thay thế command đó. |
| Profitability / billing | Reports by load/lane/truck, load financial summary, rate policy/snapshot, primary/supplemental/credit/rebill invoice commands, tax assessments, payment commands. | Kết quả runtime phụ thuộc migration, financial input và trạng thái approved cost; chưa xác minh bằng dữ liệu. |
| Settlement / payroll | Pay period, driver pay policy versioning, settlement calculate/review/approve/lock/adjust/reverse, payroll runs/items, payment scheduling/dispatch/callback/reconciliation, payslip PDF. | Đây là command-heavy API; không phải CRUD collection tổng quát. Provider ngân hàng/chi trả còn phụ thuộc cấu hình hoặc adapter. |
| AI Dispatch / gap | Run/session/decision/pending/approve/reject/cancel, load overview/rerun; gap check/dismiss/answer; tenant-scoped persistence. | `AI_DISPATCH_APPROVAL_ENABLED` mặc định false; provider/model/API key không khớp mặc định Compose; gọi model thực tế cần cấu hình provider. |
| Optimization | Policy, qualified inputs, run/get/accept, audit/idempotency/acceptance guard. | Backend endpoint tồn tại; frontend page/API chưa được route trong runtime. |
| Lark | OAuth, Lark Base load/driver sync/backfill, event webhook receipts/inbox/poll/retry và sync audit. | Tích hợp external phụ thuộc app credentials/table mapping; `.env.example` để trống phần field mapping cần cho writes. Chưa xác minh kết nối Lark thật. |

## 6. API và quyền truy cập

Các route được chia theo controller, không phải mọi entity đều có HTTP API. Ví dụ có `Container`, `MaintenanceRecord`, `HosLog`, `DvirReport`, `Expense` entities, nhưng route inventory không có CRUD controller tổng quát tương ứng. Có `InspectionController` tại `/api/inspections`; điều đó không tự tạo ra `/api/dvir`.

`ExpenseApprovalController` chỉ cung cấp approve/reject; `DocumentController` chỉ list/get/delete; `NotificationController` có list/get/mark-all-read. Đây là các trường hợp frontend resource CRUD mặc định không được suy ra.

## 7. Migrations và tests

Có 53 migration tenant từ baseline V1 tới V53; locations mặc định là `classpath:db/migration/tenant`. Khi tenancy bật, Boot Flyway migration bị thay bằng runner migrate từng active tenant; batch dừng startup khi một tenant migration fail. Khi tenancy tắt, Flyway theo cấu hình datasource đơn. Không có migration nào được chạy trong audit.

Trong `src/test/java` có 161 Java test source và 31 file tên chứa `Postgres`/`Integration`; đây là số lượng file, không phải kết quả test. Có unit/service, API/OpenAPI/security contract và một số PostgreSQL/Testcontainers integration test. Không chạy test theo yêu cầu chỉ đọc.

