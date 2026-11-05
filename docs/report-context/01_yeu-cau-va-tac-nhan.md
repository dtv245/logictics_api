# 01. Yêu cầu chức năng, phi chức năng và tác nhân

## 1. Quy tắc đọc trạng thái

- **ĐÃ HOÀN THIỆN**: có luồng xử lý backend/frontend nhìn thấy được ở phạm vi nêu trong yêu cầu; không đồng nghĩa đã nghiệm thu production.
- **MỚI LÀM MỘT PHẦN**: chỉ có một phần API/UI, phụ thuộc contract/provider, bị khóa, hoặc có gaps được tài liệu hóa.
- **CHƯA CÓ**: không tìm thấy implementation tương ứng trong source đã khảo sát.
- Các thao tác chỉ được hiển thị trong roadmap không được xem là chức năng hiện hữu.

## 2. Tác nhân và vai trò

Backend phát hành JWT nội bộ sau callback Lark, lấy role từ role của Employee; filter xác minh Bearer token và chuyển roles thành Spring authorities. Tenant claim cũng được đưa vào security context. Frontend có ma trận quyền riêng để điều khiển route/action; tài liệu code nói rõ backend vẫn là nơi quyết định authorization cuối cùng. (nguồn: src/main/java/com/company/logicstic/integration/lark/auth/LarkAuthService.java:179-220; LarkAuthenticationFilter.java:40-88; /home/vumoi/logictics-app/src/providers/permissions/roleMatrix.ts:1-6)

### Vai trò có thể chứng minh

| Tác nhân / role | Bằng chứng | Phạm vi thực tế thấy được |
|---|---|---|
| Người dùng đã xác thực | Spring Security yêu cầu xác thực mặc định cho route chưa có matcher cụ thể | Cần JWT hợp lệ; quyền chi tiết còn phụ thuộc route matcher. |
| ADMIN | matcher backend cho roles, internal API, và nhiều module | Vai trò quản trị được dùng trực tiếp bởi SecurityConfig. |
| ACCOUNTANT | matcher cho invoices, payments, rating, reports, settlement/payroll; có một số quyền đọc/duyệt nghiệp vụ | Có quyền các route tài chính được liệt kê, không suy ra quyền ngoài các matcher. |
| DISPATCHER | matcher cho Fleet operations, optimization, accessorial, employee/driver, customer/truck/load/trip/document/terminal/inspection và Lark Base | Vai trò vận hành/điều phối trong backend. |
| PAYROLL | matcher báo cáo, payroll/settlement và Fleet read | Có nhóm quyền payroll/report theo cấu hình backend. |
| PAYROLL_MANAGER | xuất hiện cùng PAYROLL ở matcher báo cáo/payroll | Có nhóm quyền backend tương tự PAYROLL tại các path được khai báo. |
| DRIVER | có thể là Employee role được token phát hành; route payslip self-service hiện hữu | Không thấy một Driver Portal API đầy đủ trong route source đã khảo sát. |
| SUPERADMIN, OWNER, MANAGER | được khai báo trong ma trận JWT frontend cho một số resource/action | KHÔNG TÌM THẤY TRONG CODE matcher backend tương ứng theo role này trong SecurityConfig hiện tại. Không xem đây là vai trò backend đã xác nhận. |
| Customer User | có entity CustomerUser trong backend | KHÔNG TÌM THẤY TRONG CODE luồng đăng nhập/API portal khách hàng tương ứng. |
| Tenant Role | có TenantRole và TenantRoleClaim cùng API /api/roles | Là dữ liệu role/claim quản lý trong tenant; không mặc định đồng nhất với role JWT ở trên. |

(nguồn: src/main/java/com/company/logicstic/config/SecurityConfig.java:44-129; LarkAuthService.java:193-205; src/main/java/com/company/logicstic/entity/TenantRole.java:18-41; src/main/java/com/company/logicstic/entity/TenantRoleClaim.java:16-42; src/main/java/com/company/logicstic/controller/RoleController.java:30-75; /home/vumoi/logictics-app/src/providers/permissions/roleMatrix.ts:19-57; /home/vumoi/logictics-app/src/types/roles.types.ts:10-22)

### Ma trận quyền theo nhóm route backend

| Module / path | Roles được nêu tường minh trong backend | Ghi chú |
|---|---|---|
| /api/roles/**, /api/internal/** | ADMIN | Route quản trị. |
| /api/fleet/** | ADMIN, ACCOUNTANT, DISPATCHER, PAYROLL, PAYROLL_MANAGER; POST status-events/mileage-attributions giới hạn ADMIN, DISPATCHER | Quyền POST cụ thể được matcher đặt trước. |
| /api/optimization/** | ADMIN, DISPATCHER; một số qualified-inputs GET và forecast POST có matcher khác | Đọc kỹ method matcher cho từng route. |
| /api/invoices/**, /api/payments/**, /api/rating/** | ADMIN, ACCOUNTANT | Các route rating trên Load cũng có rule riêng. |
| /api/expenses/*/approve, /api/accessorial-charges/*/approve | ADMIN, ACCOUNTANT | Quyền duyệt. |
| /api/reports/** và các path report cụ thể | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER | Các báo cáo vận hành và tài chính đều trong matcher này. |
| /api/driver-pay-policies/**, /api/driver-settlements/**, /api/pay-periods/**, /api/payroll/** | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER | Payroll controller còn có kiểm tra actor/state ở service. |
| /api/employees/**, /api/drivers/** | ADMIN, DISPATCHER | Employee/driver management. |
| /api/customers/**, /api/trucks/** | ADMIN, ACCOUNTANT, DISPATCHER | Master data. |
| /api/loads/** | ADMIN, ACCOUNTANT, DISPATCHER | Bao gồm update/delete; validation nghiệp vụ ở controller/service. |
| /api/trips/**, /api/trip-stops/** | ADMIN, DISPATCHER | Các hành động stop cũng dùng cùng rule path. |
| /api/documents/**, /api/terminals/** | ADMIN, ACCOUNTANT, DISPATCHER | Không có quyền upload documents tương ứng trong API hiện có. |
| /api/inspections/** | ADMIN, DISPATCHER | Inspection CRUD. |
| /api/lark/base/** | ADMIN, DISPATCHER | Lark webhook /api/lark/events được cấu hình riêng là public tại security filter, sau đó tự xác minh chữ ký. |
| Những path không khớp matcher | authenticated | Fallback yêu cầu đăng nhập nhưng không đặt role cụ thể tại SecurityConfig. |

(nguồn: src/main/java/com/company/logicstic/config/SecurityConfig.java:44-129)

Frontend cũng dùng ROLE_MATRIX để ẩn/chặn action theo JWT roles, và fail-closed với resource/action không được mô tả. Đây là bảo vệ UX; không thay thế kiểm tra backend. Một vài giá trị legacy như OWNER/MANAGER/SUPERADMIN có trong frontend matrix nhưng không nằm trong danh sách role matcher ở backend. (nguồn: /home/vumoi/logictics-app/src/providers/permissions/roleMatrix.ts:24-57, 67-95; /home/vumoi/logictics-app/src/providers/permissions/jwtRoles.ts:44-66)

## 3. Yêu cầu chức năng trích xuất từ code

| ID | Yêu cầu trong phạm vi code | Trạng thái | Bằng chứng / giới hạn |
|---|---|---|---|
| FR-01 | Đăng nhập người dùng qua Lark OAuth, đổi authorization code lấy thông tin Lark và cấp JWT nội bộ | MỚI LÀM MỘT PHẦN | Có authorize/login/callback, kiểm tra state và phát token; chỉ xác minh integration với Lark qua code/test, không xác minh tenant/provider chạy thật. (nguồn: LarkAuthController.java:17-60; LarkAuthService.java:96-153, 160-220) |
| FR-02 | Gắn tenant context và định tuyến truy cập tenant database | MỚI LÀM MỘT PHẦN | Database-per-tenant có điều kiện; tenancy mặc định false. (nguồn: application.yml:64-82; TenantJwtClaimFilter.java:21-66; TenantRoutingDataSource.java:12-34) |
| FR-03 | Tra cứu, tạo, sửa, xóa Load | ĐÃ HOÀN THIỆN ở phạm vi CRUD API | Có GET collection/detail, POST, PUT, DELETE; chưa khẳng định UI/UAT production. (nguồn: LoadController.java:28-82; LoadService.java:21-149) |
| FR-04 | Tạo lịch sử/timeline cho Load và đọc timeline | MỚI LÀM MỘT PHẦN | Có LoadTimelineController/service và bảng load_events; không thấy mọi sự kiện trong plan đều đã tích hợp. (nguồn: LoadTimelineController.java:19-39; LoadTimelineService.java:25-60; V6__create_load_events.sql:2-22; docs/plan.md:41-75) |
| FR-05 | Quản lý Trip, gán/bỏ Driver, đọc Stops và cập nhật trạng thái thực thi Stop | ĐÃ HOÀN THIỆN ở phạm vi endpoint hiện diện | Có Trip CRUD và các hành động assign/unassign/arrive/start/complete/depart. Không suy ra GPS automation. (nguồn: TripController.java:28-77; TripExecutionController.java:26-103; TripExecutionService.java:130-177) |
| FR-06 | Điều phối thủ công Load, Driver và Truck | MỚI LÀM MỘT PHẦN | CRUD và AI dispatch có, nhưng dispatch board/candidate matcher theo toàn bộ tiêu chí trong docs/plan không hiện diện dưới endpoint tương ứng. (nguồn: docs/plan.md:78-107; AiDispatchController.java:27-240; KHÔNG TÌM THẤY TRONG CODE dispatch board API như roadmap) |
| FR-07 | Gợi ý AI dispatch, lưu session/decision, cho người dùng approve/reject | MỚI LÀM MỘT PHẦN | Có run, decision và approval commands; LLM/provider và trạng thái dry-run phụ thuộc cấu hình. Source đang có thay đổi chưa commit. (nguồn: AiDispatchController.java:37-160; AiDispatchService.java:55-132, 221-289; application.yml:36-47) |
| FR-08 | Chạy optimization, xếp hạng candidate, xác nhận assignment | MỚI LÀM MỘT PHẦN | Có hard feasibility, scoring, persisted audit và accept với revalidation; provider route/HOS/availability phải được cấu hình. (nguồn: OptimizationApplicationService.java:30-92, 112-145; OptimizationAcceptanceService.java:34-84; OptimizationInputProvider.java:7-8) |
| FR-09 | Quản lý Customer, Employee, Driver, Truck, Terminal và Inspection | ĐÃ HOÀN THIỆN ở phạm vi API CRUD/tra cứu được khai báo | Controller hiện có collection/detail/create/update/delete ở các resource tương ứng; role và validation vẫn áp dụng. (nguồn: CustomerController.java:30-80; EmployeeController.java:30-80; TruckController.java:30-80; TerminalController.java:30-80; InspectionController.java:30-80) |
| FR-10 | Liệt kê, xem và xóa Document; upload chứng từ | MỚI LÀM MỘT PHẦN | Backend chỉ có search/detail/delete; frontend resource registry tắt create/edit do thiếu multipart adapter. (nguồn: DocumentController.java:24-55; resourceRegistry.ts:120-127) |
| FR-11 | Xem notification, xem chi tiết và mark-all-read | ĐÃ HOÀN THIỆN ở phạm vi API đọc/đánh dấu đã đọc | Không chứng minh tự động gửi hoặc push notification. (nguồn: NotificationController.java:21-50; NotificationService.java:17-38) |
| FR-12 | Xem report hoạt động, tài chính, Fleet và profitability | MỚI LÀM MỘT PHẦN | ReportController có nhiều endpoint; Executive Dashboard contract vẫn false; exact executive-summary/report compatibility và một số profitability dimensions còn là gap riêng. (nguồn: ReportController.java:37-222; executive.contract.ts:1-5; /home/vumoi/logictics-app/docs/backend-gaps.md:20,25,47,234-246) |
| FR-13 | Rating Load, phiên bản hóa rate policy và chấp nhận snapshot | ĐÃ HOÀN THIỆN ở phạm vi API/command hiện tại | Có preview/accept/snapshot, contract/rule versions; không đồng nghĩa đối chiếu mọi nghiệp vụ thương mại của doanh nghiệp. (nguồn: RatingController.java:20-44; RatePolicyController.java:18-55; V21__create_versioned_customer_rate_policies.sql:3) |
| FR-14 | Tạo/đọc/sửa/hủy Payment và ghi nhận command history | MỚI LÀM MỘT PHẦN | Có create/read/update/cancel command nhưng kênh payment phải tuân contract; không có manual payment completion hoặc bằng chứng gateway production. (nguồn: PaymentController.java:28-95; docs/payment-command-contracts.md:1-15; /home/vumoi/logictics-app/docs/backend-gaps.md:25,208-212) |
| FR-15 | Tính toán, review, approve, lock và điều chỉnh Driver Settlement | MỚI LÀM MỘT PHẦN | Các command calculate/review/approve/lock hiện diện; roadmap rộng hơn như reject, generic recalculate hoặc settlement payment alias không được khẳng định có. (nguồn: DriverSettlementController.java:20-148; /home/vumoi/logictics-app/docs/backend-gaps.md:16,244) |
| FR-16 | Tính toán/review/approve/lock Payroll Run, sinh Payslip và xử lý payment command | MỚI LÀM MỘT PHẦN | Run calculate/detail/workflow và payslip artifact có; collection/configuration reads và self-service payment-status contract còn thiếu; issuance không đồng nghĩa PAID. (nguồn: PayrollRunController.java:9-28; PayslipController.java:7-24; /home/vumoi/logictics-app/docs/backend-gaps.md:17-18,210-216,245) |
| FR-17 | Ghi Fleet policy, status event và mileage attribution | ĐÃ HOÀN THIỆN ở phạm vi command API | Có endpoint/persistence; không phải ingestion vị trí GPS live. (nguồn: FleetHistoryController.java:16-24; V34__capture_immutable_fleet_history_and_mileage_attribution.sql:2) |
| FR-18 | Đồng bộ Load/Driver với Lark Base, xem tình trạng và xử lý event callback | MỚI LÀM MỘT PHẦN | Có sync state/event inbox trong working tree; không tìm thấy Lark Bot broadcast API theo roadmap và chưa xác minh tài khoản Lark thật. (nguồn: LarkBaseController.java:17-52; src/main/java/com/company/logicstic/integration/lark/base/LarkSyncController.java:12-43; LarkEventController.java:15-49; docs/plan.md:349-361) |
| FR-19 | Customer portal/public Load tracking và GPS tracking token | CHƯA CÓ | Có plan nêu tracking token/public endpoint; KHÔNG TÌM THẤY TRONG CODE controller route public tracking tương ứng. (nguồn: docs/plan.md:68-75; KHÔNG TÌM THẤY TRONG CODE) |

## 4. Yêu cầu phi chức năng có bằng chứng

| ID | Yêu cầu / cơ chế | Bằng chứng trong repo |
|---|---|---|
| NFR-01 Authentication | Bearer JWT nội bộ; Lark OAuth callback; endpoint public được liệt kê rõ, phần còn lại yêu cầu authenticated | SecurityConfig.java:33-56, 128-132; LarkAuthenticationFilter.java:40-94 |
| NFR-02 Authorization | Path matchers theo role; frontend access policy fail-closed cho action không biết | SecurityConfig.java:60-129; /home/vumoi/logictics-app/src/providers/permissions/roleMatrix.ts:67-95 |
| NFR-03 Tenant isolation | Tenant claim lấy từ identity, mismatch trả 403; routed datasource bắt buộc có tenant context; conditional feature mặc định tắt | TenantJwtClaimFilter.java:39-66; TenantRoutingDataSource.java:21-34; application.yml:64-82 |
| NFR-04 Input validation | Spring Validation dependency và controller dùng @Valid; DTO có constraint annotations | pom.xml:46-49; ví dụ src/main/java/com/company/logicstic/controller/LoadController.java:60-75 |
| NFR-05 Error envelope | Security failures tạo JSON response; exception handlers chuyển lỗi domain/validation thành API error response | SecurityConfig.java:39-43; src/main/java/com/company/logicstic/config/SecurityResponses.java:1-40; src/main/java/com/company/logicstic/exception/GlobalExceptionHandler.java:23-45 |
| NFR-06 Pagination | PagedResponse dùng cho các collection search; frontend query serializer/provider kiểm soát query whitelist | src/main/java/com/company/logicstic/dto/PagedResponse.java:7-21; src/main/java/com/company/logicstic/service/LoadService.java:55-67; /home/vumoi/logictics-app/src/providers/dataProvider.ts:1-6 |
| NFR-07 Localization | Frontend khởi tạo locale vi, fallback en; dùng i18next và react-i18next | /home/vumoi/logictics-app/src/main.tsx:21-24; /home/vumoi/logictics-app/src/providers/i18nProvider.ts:13-24 |
| NFR-08 Deployment container | Docker multi-stage Java 21 build/runtime, non-root user, healthcheck; compose có API/PostgreSQL/Redis | Dockerfile:3-76; docker-compose.yml:1-74 |
| NFR-09 Integration-testability | Maven khai báo Spring Boot Test, Spring Security Test, H2, REST Assured và Testcontainers PostgreSQL | pom.xml:119-164 |
| NFR-10 Client/server query cache | TanStack Query được khai báo và Refine được cấu hình React Query client | /home/vumoi/logictics-app/package.json:20-40; /home/vumoi/logictics-app/src/App.tsx:179-190 |

Không đưa các thuộc tính “high availability”, “encryption at rest”, “SLA”, “backup policy”, “WCAG conformance”, hay “production uptime” vào báo cáo như yêu cầu đã đạt vì KHÔNG TÌM THẤY TRONG CODE bằng chứng nghiệm thu cho các nội dung đó.

