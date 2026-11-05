# 02. Use Case theo source hiện tại

## 1. Phạm vi

Danh mục UC dưới đây được rút ra từ API controller, UI route và tài liệu contract trong hai repo. UC là mục tiêu người dùng, không phải phép đếm API. Các path được ghi nguyên theo mapping source; API có cùng tên nhưng không có màn hình tương ứng được đánh dấu rõ. Authentication backend hiện có Lark OAuth; route UI có chọn Tenant nhưng không tìm thấy API list tenant trong controller inventory. (nguồn: src/main/java/com/company/logicstic/integration/lark/auth/LarkAuthController.java:17-60; /home/vumoi/logictics-app/src/router/AppRouter.tsx:161-187; src/main/java/com/company/logicstic/controller)

## 2. Danh mục Use Case

| Mã UC | Tên Use Case | Tác nhân chính | Module | Trạng thái code |
|---|---|---|---|---|
| UC-01 | Đăng nhập LogisticsX qua Lark | Nhân viên có tài khoản Lark được liên kết | Authentication | MỚI LÀM MỘT PHẦN |
| UC-02 | Nạp thông tin người dùng/tenant hiện hành | Người dùng đã đăng nhập | Tenant/Security | MỚI LÀM MỘT PHẦN |
| UC-03 | Tra cứu và quản lý khách hàng | ADMIN, ACCOUNTANT, DISPATCHER | Customer | ĐÃ HOÀN THIỆN ở phạm vi CRUD API |
| UC-04 | Tạo và cập nhật Load | ADMIN, ACCOUNTANT, DISPATCHER | Load | ĐÃ HOÀN THIỆN ở phạm vi CRUD API |
| UC-05 | Xem timeline và lịch sử Load | Người dùng vận hành có quyền đọc Load | Load Execution | MỚI LÀM MỘT PHẦN |
| UC-06 | Tạo Trip, gán Driver, thực hiện Stop | ADMIN, DISPATCHER | Trip Execution | ĐÃ HOÀN THIỆN ở phạm vi endpoint |
| UC-07 | Sinh và xử lý đề xuất AI Dispatch | Dispatcher/nhân viên được cấp quyền | AI Dispatch | MỚI LÀM MỘT PHẦN |
| UC-08 | Chạy Optimization và chấp nhận candidate | ADMIN, DISPATCHER | Optimization | MỚI LÀM MỘT PHẦN |
| UC-09 | Tra cứu Documents | ADMIN, ACCOUNTANT, DISPATCHER | Documents | MỚI LÀM MỘT PHẦN |
| UC-10 | Đọc và đánh dấu Notification | Người dùng đã đăng nhập | Notifications | ĐÃ HOÀN THIỆN ở phạm vi API hiện có |
| UC-11 | Xem báo cáo hoạt động và tài chính | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER | Reporting | MỚI LÀM MỘT PHẦN |
| UC-12 | Định giá Load và lưu rating snapshot | ADMIN, ACCOUNTANT | Rating | ĐÃ HOÀN THIỆN ở phạm vi API hiện có |
| UC-13 | Phát hành/điều chỉnh invoice và payment | ADMIN, ACCOUNTANT | Billing/Payments | MỚI LÀM MỘT PHẦN |
| UC-14 | Tính, review, approve và lock settlement/payroll | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER; Driver đọc payslip của mình theo API | Settlement/Payroll | MỚI LÀM MỘT PHẦN |
| UC-15 | Ghi Fleet event / mileage attribution | ADMIN, DISPATCHER; quyền đọc Fleet rộng hơn | Fleet | ĐÃ HOÀN THIỆN ở phạm vi command API |
| UC-16 | Đồng bộ Load/Driver với Lark Base | ADMIN, DISPATCHER | Lark Base | MỚI LÀM MỘT PHẦN |
| UC-17 | Tiếp nhận Lark event webhook | Lark platform | Lark Events | MỚI LÀM MỘT PHẦN |
| UC-18 | Quản lý vai trò trong tenant | ADMIN | Roles | ĐÃ HOÀN THIỆN ở phạm vi CRUD API |
| UC-19 | Xem trang Map / route visualization | Dispatcher | Map | MỚI LÀM MỘT PHẦN |
| UC-20 | Tải lên POD/BOL cho Load | Driver/Dispatcher | Documents | CHƯA CÓ trong API hiện tại |
| UC-21 | Tra cứu công khai trạng thái Load bằng token | Customer | Public Tracking | CHƯA CÓ trong API hiện tại |
| UC-22 | Xem Driver Portal | Driver | Driver Portal | CHƯA CÓ trong route/API hiện tại |
| UC-23 | Nhận GPS/ELD và cập nhật vị trí tự động | Driver/Telematics provider | Telematics | CHƯA CÓ trong API hiện tại |
| UC-24 | Quản lý Employee/Driver, Truck và Terminal | ADMIN, DISPATCHER | Master Data | ĐÃ HOÀN THIỆN ở phạm vi CRUD/tra cứu API |

Bằng chứng về quyền endpoint nằm trong SecurityConfig; role ở frontend chỉ điều khiển route/action UX và không thay thế authorization backend. Các UC gắn với Roadmap nhưng không tìm thấy endpoint phải được giữ ở trạng thái chưa có. (nguồn: src/main/java/com/company/logicstic/config/SecurityConfig.java:44-129; /home/vumoi/logictics-app/src/providers/permissions/roleMatrix.ts:1-6, 24-57; docs/plan.md:68-75, 110-133, 349-361)

## 3. Đặc tả UC trọng yếu

### UC-01 — Đăng nhập qua Lark

- **Tác nhân:** Nhân viên có tài khoản Lark đã liên kết với Employee.
- **Tiền điều kiện:** Lark integration bật và cấu hình hợp lệ; người dùng khởi tạo authorize hoặc login.
- **Hậu điều kiện thành công:** Backend xác thực state, đổi code, tìm Employee, phát JWT Bearer chứa subject/email/tenant/role.
- **Luồng chính:** UI mở /login → bắt đầu Lark authorize → Lark callback → POST /api/auth/lark/callback → LarkAuthService xác minh state/code → ánh xạ Employee → tạo JWT → UI nạp identity bằng GET /api/me.
- **Ngoại lệ:** State thiếu/sai/hết hạn trả lỗi; người dùng từ chối cấp quyền; tài khoản Lark không liên kết Employee; tenant không sẵn sàng.
- **Endpoint:** GET /api/auth/lark/authorize; GET /api/auth/lark/login; POST /api/auth/lark/callback; GET /api/me.
- **Màn hình:** /login, /auth/callback, shell sau xác thực.
- **Trạng thái:** MỚI LÀM MỘT PHẦN; chưa xác minh tài khoản Lark thật hoặc môi trường production.
- **Nguồn:** src/main/java/com/company/logicstic/integration/lark/auth/LarkAuthController.java:17-60; LarkAuthService.java:114-220; LarkAuthenticationFilter.java:40-94; /home/vumoi/logictics-app/src/router/AppRouter.tsx:161-173; /home/vumoi/logictics-app/src/App.tsx:100-138.

### UC-02 — Nạp identity và tenant context

- **Tác nhân:** Người dùng đã đăng nhập.
- **Tiền điều kiện:** JWT hợp lệ.
- **Hậu điều kiện:** Identity endpoint trả thông tin user/tenant; nếu multi-tenancy bật, claim tenant được gắn vào request context trước khi truy cập tenant database.
- **Luồng chính:** ProtectedRoute xác nhận session → UI gọi GET /api/me → TenantGuard bảo vệ layout → TenantJwtClaimFilter đọc claim tenant → TenantContext → TenantRoutingDataSource. Khi người dùng yêu cầu đổi tenant, frontend khởi chạy lại xác thực qua Identity Provider để token mới mang tenant được cấp; không gửi tenant header hay gọi switch endpoint riêng.
- **Ngoại lệ:** Thiếu tenant claim, tenant identity mismatch, inactive tenant hoặc tenant database không khả dụng trả 403/503.
- **Endpoint/màn hình:** GET /api/me; /select-tenant route hiện có.
- **Giới hạn:** KHÔNG TÌM THẤY TRONG CODE backend API riêng để liệt kê hoặc chuyển Tenant. Backend `/api/me` trả identity hiện tại; frontend hiện dựng danh sách tenant từ claim hiện hành và đổi tenant bằng xác thực lại qua Identity Provider. Màn hình này không chứng minh có danh sách nhiều tenant hay môi trường multi-tenant đang bật; `app.tenancy.enabled` mặc định false.
- **Nguồn:** CurrentUserController.java:24-38; CurrentUserResponse.java:10-24; TenantJwtClaimFilter.java:21-66; TenantContext.java:7-32; TenantRoutingDataSource.java:21-34; application.yml:64-82; /home/vumoi/logictics-app/src/router/AppRouter.tsx:170-174; /home/vumoi/logictics-app/src/pages/tenant/SelectTenantPage.tsx:13-30; /home/vumoi/logictics-app/src/hooks/useTenantList.ts:15-28; /home/vumoi/logictics-app/src/hooks/useSwitchTenant.ts:23-65.

### UC-04 — Tạo Load

- **Tác nhân:** ADMIN, ACCOUNTANT hoặc DISPATCHER.
- **Tiền điều kiện:** Đã xác thực; user có role phù hợp; request đáp ứng validation DTO và quan hệ Customer/Truck/Dispatcher hợp lệ nếu có.
- **Hậu điều kiện:** Load được persist và response trả DTO; các field được backend xác nhận theo CreateLoadRequest.
- **Luồng chính:** UI /loads/create → data provider gửi POST /api/loads → LoadController kiểm tra @Valid → LoadService tạo entity → LoadRepository lưu → LoadMapper tạo view → ApiResponse.
- **Ngoại lệ:** Validation lỗi, entity liên quan không tồn tại, quyền bị từ chối, lỗi persistence.
- **Endpoint/màn hình:** GET /api/loads, GET /api/loads/{id}, POST /api/loads, PUT /api/loads/{id}, DELETE /api/loads/{id}; /loads, /loads/create, /loads/show/:id.
- **Trạng thái:** ĐÃ HOÀN THIỆN ở phạm vi CRUD API; dữ liệu live và UAT không được xác minh.
- **Nguồn:** LoadController.java:28-82; LoadService.java:21-149; /home/vumoi/logictics-app/src/constants/routes.ts:5-12, 38-65; /home/vumoi/logictics-app/src/pages/loads/create.tsx:15-25.

### UC-05 — Xem Load timeline

- **Tác nhân:** Người dùng vận hành có quyền xem Load.
- **Tiền điều kiện:** Load tồn tại trong tenant hiện hành.
- **Hậu điều kiện:** UI nhận dữ liệu timeline để trình bày lịch sử.
- **Luồng chính:** UI Load detail → GET /api/loads/{loadId}/timeline → LoadTimelineController → LoadTimelineService/LoadEvent repository → response.
- **Ngoại lệ:** Không có Load hoặc persistence lỗi.
- **Giới hạn:** Không suy ra public tracking link, raw event endpoint hay GPS event vì không tìm thấy các route tương ứng ở source inventory.
- **Màn hình:** /loads/show/:id.
- **Nguồn:** LoadTimelineController.java:19-39; LoadTimelineService.java:18-46; src/main/resources/db/migration/tenant/V6__create_load_events.sql:1-22; docs/plan.md:68-75.

### UC-06 — Gán Driver và thực hiện Trip Stop

- **Tác nhân:** ADMIN hoặc DISPATCHER.
- **Tiền điều kiện:** Trip tồn tại; Stop thuộc Trip; trạng thái hiện tại cho phép hành động.
- **Hậu điều kiện:** Assignment hoặc timestamp/status Stop được persist.
- **Luồng chính:** UI Trip detail → GET /api/trips/{tripId}/drivers và /stops → POST assign/unassign hoặc POST arrive → start-service → complete-service → depart → TripExecutionService kiểm tra trạng thái và lưu repository.
- **Ngoại lệ:** Trip/Stop không tồn tại; chuyển trạng thái sai trả Bad Request.
- **Endpoint/màn hình:** Các route /api/trips/{tripId}/drivers, /api/trips/{tripId}/stops, /api/trip-stops/{id}/arrive, /start-service, /complete-service, /depart; /trips/show/:id.
- **Trạng thái:** ĐÃ HOÀN THIỆN ở phạm vi thao tác Stop hiện có; chưa bao gồm cập nhật do GPS/geofence.
- **Nguồn:** TripExecutionController.java:26-103; TripExecutionService.java:92-148; /home/vumoi/logictics-app/src/pages/trips/show.tsx:27-40; SecurityConfig.java:117-125.

### UC-07 — AI Dispatch và phê duyệt

- **Tác nhân:** Dispatcher/người dùng có identity map tới Employee; người phê duyệt là actor được backend kiểm tra.
- **Tiền điều kiện:** AI dispatch bật cho tenant, Load/đầu vào đủ điều kiện, provider configuration đáp ứng nếu cần.
- **Hậu điều kiện:** Run/session/decision được lưu; chỉ lệnh approve/reject mới xác nhận quyết định người dùng. Không giả định approve đồng nghĩa một assignment nếu service không thực hiện nhánh đó.
- **Luồng chính:** UI AI Dispatch → POST /api/ai/dispatch/runs → service lấy/sắp candidate và có thể yêu cầu LLM rerank → GET pending/session/run → POST approve hoặc reject.
- **Ngoại lệ:** Feature bị tắt, input thiếu, provider/LLM lỗi (có fallback), session/decision không còn ở trạng thái hợp lệ.
- **Endpoint/màn hình:** /api/ai/dispatch/runs; /sessions; /pending; /decisions/{id}/approve; /decisions/{id}/reject; UI /ai-dispatch, /ai-dispatch/:id.
- **Trạng thái:** MỚI LÀM MỘT PHẦN; source đang dirty/uncommitted và provider thật chưa được xác minh.
- **Nguồn:** AiDispatchController.java:27; AiDispatchService.java:55-132, 221-289; /home/vumoi/logictics-app/src/hooks/ai/useLoadAiDispatch.ts:22-45; /home/vumoi/logictics-app/src/router/AppRouter.tsx:182-187.

### UC-08 — Chạy Optimization và accept candidate

- **Tác nhân:** ADMIN hoặc DISPATCHER.
- **Tiền điều kiện:** Policy và scope hợp lệ; dữ liệu Load/Trip/Driver/Truck cùng tenant; route/HOS/availability/cost evidence đầy đủ.
- **Hậu điều kiện:** Optimization run và candidate evidence được audit; accept chỉ thực hiện nếu candidate còn feasible và input fingerprint chưa stale.
- **Luồng chính:** UI Optimization → POST /api/optimization/runs → OptimizationApplicationService resolve qualified inputs + feasibility + scoring → GET run → POST assignment candidate accept với idempotency key.
- **Ngoại lệ:** Thiếu evidence; candidate infeasible/stale; trùng idempotency key nhưng body khác; run đã accepted candidate khác.
- **Màn hình:** /optimization, /optimization/runs/:id.
- **Trạng thái:** MỚI LÀM MỘT PHẦN; cần provider integration và xác nhận dữ liệu đầu vào runtime.
- **Nguồn:** OptimizationRunController.java:20-39; OptimizationApplicationService.java:30-92, 112-145; OptimizationAcceptanceService.java:34-84; /home/vumoi/logictics-app/src/features/optimization/hooks/useOptimizationActions.ts:14-24; /home/vumoi/logictics-app/src/router/AppRouter.tsx:182-184.

### UC-11 — Xem báo cáo

- **Tác nhân:** ADMIN, ACCOUNTANT, PAYROLL hoặc PAYROLL_MANAGER.
- **Tiền điều kiện:** User có role báo cáo và query period/currency hợp lệ theo từng endpoint.
- **Hậu điều kiện:** API trả report DTO; một số metric có trạng thái unavailable thay vì tính giả.
- **Luồng chính:** UI report → gọi endpoint report tương ứng → controller gọi calculation/report service → response.
- **Ngoại lệ:** Thiếu currency/date, validation failure, report service trả unavailable reason hoặc data error.
- **Màn hình:** Operations dashboard, Fleet report, Profitability.
- **Giới hạn:** Dashboard executive bị contract flag tắt; đừng lấy placeholder KPI trong UI làm dữ liệu live.
- **Nguồn:** ReportController.java:37-250; SecurityConfig.java:103-109; /home/vumoi/logictics-app/src/features/executive/config/executive.contract.ts:1-5; AppRouter.tsx:177-190.

### UC-13 — Quản lý vòng đời invoice và payment

- **Tác nhân:** ADMIN, ACCOUNTANT.
- **Tiền điều kiện:** Invoice/Load/rating snapshot phù hợp, currency nhất quán, request command hợp lệ.
- **Hậu điều kiện:** Billing command được ghi nhận và trạng thái/snapshot được trả về theo contract.
- **Luồng chính:** UI invoices/billing → POST primary billing → đọc invoice → issue/supplemental/credit/rebill theo command endpoint → tạo/đọc/cancel payment theo PaymentController.
- **Ngoại lệ:** State conflict, currency/amount mismatch, idempotency key reuse hoặc command provider chưa được cấu hình.
- **Màn hình:** /invoices, /payments; Billing command UI có thể tách khỏi CRUD page.
- **Trạng thái:** MỚI LÀM MỘT PHẦN; các gap về manual payment completion và external settlement cần được giữ nguyên.
- **Nguồn:** BillingController.java:17-56; PaymentController.java:28-95; SecurityConfig.java:81-89; docs/payment-command-contracts.md:1-15; /home/vumoi/logictics-app/docs/backend-gaps.md:25,208-212.

### UC-14 — Settlement và Payroll

- **Tác nhân:** ACCOUNTANT, PAYROLL, PAYROLL_MANAGER, ADMIN; Driver đọc payslip theo self-service endpoint.
- **Tiền điều kiện:** Pay policy/profile/period phù hợp; trạng thái cho phép command.
- **Hậu điều kiện:** Settlement/PayrollRun state và lịch sử tài chính được persist; payslip là artifact snapshot.
- **Luồng chính:** calculate settlement → submit-review → approve → lock; calculate payroll run → review → approve → lock → issue payslip; payment command là luồng riêng.
- **Ngoại lệ:** State locked, nguồn policy/profile thiếu, reconciliation case, provider không hỗ trợ manual completion.
- **Endpoint/màn hình:** /api/driver-settlements/**; /api/payroll/runs/**; /api/payroll/runs/{id}/payslips; /api/driver/me/payslips; UI /settlements, /payroll, /profile/payslips.
- **Trạng thái:** MỚI LÀM MỘT PHẦN theo gaps được ghi nhận; không suy ra lương đã trả.
- **Nguồn:** DriverSettlementController.java:20-148; PayrollRunController.java:9-28; PayslipController.java:7-24; /home/vumoi/logictics-app/docs/backend-gaps.md:16-18,210-216,244-245.

### UC-16 — Đồng bộ Load/Driver sang Lark Base

- **Tác nhân:** ADMIN/DISPATCHER; Lark Base là hệ thống ngoài.
- **Tiền điều kiện:** Cấu hình mapping/table/app Lark hợp lệ; entity trong tenant tồn tại; không có conflict unmanaged record.
- **Hậu điều kiện:** Link, sync state, hash và audit được lưu; lỗi provider được trả về thành trạng thái lỗi/conflict, không ghi thành công giả.
- **Luồng chính:** UI/API yêu cầu sync → LarkSyncController → LarkSyncService tạo projection và đọc/ghi qua gateway → lưu trạng thái/audit.
- **Ngoại lệ:** Mapping không khớp, bản ghi remote missing, conflict, permission provider lỗi hoặc app/table config thiếu.
- **Giới hạn:** Không tìm thấy endpoint Bot broadcast/interactive action theo roadmap.
- **Trạng thái:** MỚI LÀM MỘT PHẦN; implementation event/sync trong working tree chưa commit.
- **Nguồn:** LarkSyncController.java:20-85; LarkSyncService.java:14-118; LarkBaseService.java:8-26; LarkEventController.java:15-49; docs/plan.md:349-361.

### UC-09 — Tra cứu Document

- **Tác nhân:** ADMIN, ACCOUNTANT, DISPATCHER.
- **Tiền điều kiện:** Đã xác thực; query filter phù hợp.
- **Hậu điều kiện:** Người dùng xem metadata/document record hoặc xóa theo quyền.
- **Luồng chính:** UI Documents → GET /api/documents với filter/page → DocumentController → DocumentService → repository.
- **Ngoại lệ:** Document không tồn tại hoặc backend trả forbidden.
- **Giới hạn:** Upload không có API contract/controller hiện hữu; không giả định có thể gửi POD/BOL.
- **Nguồn:** DocumentController.java:24-55; DocumentService.java:17-50; /home/vumoi/logictics-app/src/pages/resourceRegistry.ts:120-127.

### UC-03, UC-10, UC-12, UC-15, UC-17–UC-24 — Các use case còn lại

Các UC có API hỗ trợ một phần được ghi ở danh mục; riêng màn hình/thao tác chưa có route hoặc contract không được mô tả như luồng hoàn chỉnh. UC-03 Customer có CRUD API; UC-10 Notification có API đọc/mark-read; UC-12 rating có preview/accept snapshot; UC-15 Fleet có event/mileage command; UC-17 webhook có inbox/event handler; UC-18 Roles có CRUD API; UC-24 bao quát CRUD/tra cứu master data Employee/Driver, Truck, Terminal. UC-19 Map chỉ có trang source chưa mount và placeholder; UC-20 upload, UC-21 public tracking, UC-22 Driver Portal và UC-23 GPS/ELD automation chưa có contract/route hoàn chỉnh được tìm thấy.

- **Endpoint/màn hình:** Customer `/api/customers`; notification `/api/notifications`; rating `/api/rating/**`; Fleet `/api/fleet/**`; Lark webhook `/api/lark/events/**`; Roles `/api/roles`; master data `/api/employees`, `/api/drivers`, `/api/trucks`, `/api/terminals`. Các màn hình Map và các UC CHƯA CÓ không được xem là luồng người dùng đã hoàn thành.
- **Nguồn:** CustomerController.java:30-80; NotificationController.java:21-50; RatingController.java:20-44; FleetHistoryController.java:16-24; LarkEventController.java:15-49; RoleController.java:30-75; EmployeeController.java:30-80; TruckController.java:30-80; TerminalController.java:30-80; `/home/vumoi/logictics-app/src/pages/map/MapPage.tsx:6-14`; `KHÔNG TÌM THẤY TRONG CODE`.

## 4. Sơ đồ Use Case

    @startuml
    left to right direction
    actor "Nhân viên vận hành" as Ops
    actor "ADMIN" as Admin
    actor "ACCOUNTANT" as Accountant
    actor "DISPATCHER" as Dispatcher
    actor "PAYROLL / PAYROLL_MANAGER" as Payroll
    actor "DRIVER" as Driver
    actor "Customer" as Customer
    actor "Lark Platform" as Lark

    rectangle "LogisticsX TMS" {
      usecase "Đăng nhập Lark" as UC1
      usecase "Nạp identity / tenant context" as UC2
      usecase "Quản lý Customer" as UC3
      usecase "Quản lý Load" as UC4
      usecase "Xem Load timeline" as UC5
      usecase "Quản lý Trip và Stops" as UC6
      usecase "Chạy / duyệt AI Dispatch" as UC7
      usecase "Chạy Optimization" as UC8
      usecase "Tra cứu Document" as UC9
      usecase "Đọc Notification" as UC10
      usecase "Xem report" as UC11
      usecase "Rating Load" as UC12
      usecase "Invoice / Payment" as UC13
      usecase "Settlement / Payroll" as UC14
      usecase "Fleet event / mileage" as UC15
      usecase "Đồng bộ Lark Base" as UC16
      usecase "Nhận Lark webhook" as UC17
      usecase "Quản lý role" as UC18
      usecase "Xem Map" as UC19
      usecase "Upload POD/BOL" as UC20
      usecase "Public Load tracking" as UC21
      usecase "Driver Portal" as UC22
      usecase "GPS / ELD tracking" as UC23
      usecase "Quản lý master data" as UC24
    }

    Ops --> UC1
    Dispatcher --> UC3
    Dispatcher --> UC4
    Dispatcher --> UC5
    Dispatcher --> UC6
    Dispatcher --> UC7
    Dispatcher --> UC8
    Accountant --> UC9
    Ops --> UC10
    Accountant --> UC11
    Payroll --> UC11
    Accountant --> UC12
    Accountant --> UC13
    Payroll --> UC14
    Driver --> UC14
    Dispatcher --> UC15
    Admin --> UC16
    Dispatcher --> UC16
    Lark --> UC17
    Admin --> UC18
    Customer --> UC21
    Driver --> UC22
    Driver --> UC23
    Dispatcher --> UC24
    Dispatcher --> UC19
    @enduml

UC-06 và UC-07 có bước approve/accept riêng vì backend lưu suggestion/run và áp dụng lại kiểm tra điều kiện trước khi ghi. Public tracking, GPS automation và upload POD/BOL chưa được đưa vào luồng hoàn thành. (nguồn: AiDispatchService.java:221-289; OptimizationAcceptanceService.java:34-84; KHÔNG TÌM THẤY TRONG CODE public tracking/upload API)

