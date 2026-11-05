# 00. Tổng quan LogisticsX

## Phạm vi và độ tin cậy của bản khảo sát

Tài liệu này mô tả mã nguồn hiện có trong hai thư mục repo mà phiên khảo sát đọc được: backend tại repo hiện tại và frontend tại repo sibling /home/vumoi/logictics-app. Đây là ảnh chụp working tree ngày 20-10-2026, không phải xác nhận về bản đang chạy hoặc bản đã phát hành. Backend đang ở nhánh main, HEAD 2bc3944, ahead 4 commit và có thay đổi staged/unstaged cùng file mới chưa track; frontend đang ở nhánh feat/complete-ux-review-tasks-20261020 và cũng có thay đổi chưa commit. Các file context này chỉ nên trích dẫn như mô tả snapshot. (nguồn: kết quả chỉ-đọc git status --short --branch và git rev-parse --short HEAD ngày 20-10-2026)

GitNexus được liệt kê trong AGENTS.md, nhưng truy vấn graph trong phiên này không sử dụng được do database version 42 không tương thích với storage runtime version 40. Kết quả impact trả risk UNKNOWN; vì vậy không xem danh sách caller rỗng là bằng chứng về việc một thành phần không được dùng. Các phần dưới đây được đối chiếu bằng source và tài liệu có trong repo. (nguồn: AGENTS.md:4-15; kết quả mcp GitNexus impact/query ngày 20-10-2026)

## Mô tả hệ thống

LogisticsX là hệ thống Transportation Management System (TMS) gồm REST API backend và ứng dụng web frontend. Backend hiện trong source là ứng dụng Java/Spring Boot với JPA, PostgreSQL và Flyway; frontend là SPA React/TypeScript dùng Refine, Ant Design và React Router. Tên bảng, route, API và trạng thái tính năng dưới đây được lấy từ source hiện tại, không lấy roadmap làm bằng chứng đã hoàn thành. (nguồn: README.md:1-18; pom.xml:6-28; /home/vumoi/logictics-app/package.json:1-40)

Mô hình miền dùng các khái niệm chuẩn sau: Tenant là phạm vi công ty; Load là đơn/chuyến hàng cần vận chuyển; Trip là chuyến xe vật lý gồm các Stop; Truck là phương tiện; Driver trong giao diện thường ánh xạ đến Employee có role tài xế. Một Load và một Trip là hai thực thể riêng, không dùng thay thế lẫn nhau. (nguồn: docs/plan.md:13-16; src/main/java/com/company/logicstic/entity/Load.java:35-88; src/main/java/com/company/logicstic/entity/Trip.java:29-82; src/main/java/com/company/logicstic/service/TripExecutionService.java:130-177)

### Mục tiêu và phạm vi thấy trong source

Các module có endpoint hoặc resource frontend hiện hữu gồm: đăng nhập Lark và truy vấn user hiện tại; Tenant/role; Customer, Employee/Driver, Truck, Terminal; Load, Trip, Trip Stop; chứng từ; notification; AI dispatch và AI Load Gap; optimization; pricing/rating; invoice, payment, shipment cost, profitability/reporting; settlement, payroll, payslip; Fleet history và Lark Base/event integration. Danh sách này là phạm vi code có mặt, không khẳng định từng module đã sẵn sàng cho production. (nguồn: src/main/java/com/company/logicstic/controller/LoadController.java:28-82; TripExecutionController.java:26-103; ReportController.java:37-250; AiDispatchController.java:27-240; OptimizationRunController.java:20-39; /home/vumoi/logictics-app/src/pages/resourceRegistry.ts:20-96)

Mục tiêu chi tiết trong docs/plan.md mô tả roadmap TMS gồm Order Lifecycle, Dispatch, Fleet/Telematics, Driver Portal, Documents, Settlement, Payroll, Maintenance, Financials và Operations/Lark. Các mục tiêu trong tài liệu này không tự động chứng minh module tương ứng đã được triển khai. (nguồn: docs/plan.md:1-33, 37-45)

## Multi-tenancy

Code có cơ chế database-per-tenant có điều kiện: khi bật app.tenancy.enabled, TenantJwtClaimFilter đọc claim tenant của JWT, gắn vào TenantContext và yêu cầu DataSource của tenant; TenantRoutingDataSource chọn kết nối theo tenant. TenantContext được dọn ở finally sau request. Cấu hình application.yml đặt app.tenancy.enabled mặc định false, vì vậy không thể kết luận multi-tenancy đang bật trong môi trường chạy nếu thiếu cấu hình triển khai. Registry có migration riêng. (nguồn: src/main/resources/application.yml:64-82; src/main/java/com/company/logicstic/config/TenantJwtClaimFilter.java:21-66, 69-75; TenantContext.java:7-32; TenantRoutingDataSource.java:12-34; src/main/resources/db/migration/registry/V1__create_tenant_registry.sql:1)

Bản nháp gọi hệ thống là multi-tenant SaaS. Phần hỗ trợ tenant trong code có bằng chứng; SaaS đang phục vụ khách hàng thật, quy mô tenant, billing SaaS hay vận hành production thì KHÔNG TÌM THẤY TRONG CODE. (nguồn: src/main/java/com/company/logicstic/config/TenantDataSourceConfiguration.java:18-57; KHÔNG TÌM THẤY TRONG CODE)

## Công nghệ được xác minh

| Thành phần | Phiên bản khai báo / bằng chứng | Vai trò trong source | Căn cứ chọn |
|---|---|---|---|
| Java | 21 | Ngôn ngữ backend | [SUY LUẬN] Phù hợp runtime Spring hiện tại; repo không ghi quyết định chọn Java. |
| Spring Boot | 4.1.1 ở parent Maven | Khởi tạo và cấu hình backend | [SUY LUẬN] Repo xác nhận đang dùng, không ghi lý do lựa chọn. |
| Spring Web MVC, Validation, Spring Security OAuth2 Resource Server | Phiên bản theo Spring Boot BOM | REST, validation request, security filter | [SUY LUẬN] Vai trò suy ra từ dependency và config. |
| Spring Data JPA / Hibernate | Theo BOM | ORM và persistence | [SUY LUẬN] Vai trò suy ra từ dependency. |
| PostgreSQL | Driver JDBC khai báo; docker-compose dùng postgres:16-alpine | Cơ sở dữ liệu chính trong cấu hình đang thấy | [SUY LUẬN] Lý do chọn không được ghi. |
| Flyway | Theo BOM, Flyway PostgreSQL | Versioned SQL migrations | [SUY LUẬN] Vai trò suy ra từ migration và dependency. |
| JJWT | 0.12.6 | Ký và xác minh JWT nội bộ Lark login | Repo có ghi phiên bản, không nêu rationale. |
| MapStruct | 1.6.3; Lombok theo BOM | Mapping Entity/DTO và giảm boilerplate | [SUY LUẬN] |
| SpringDoc OpenAPI | 3.1.1 | Sinh tài liệu OpenAPI/Swagger UI | Repo có ghi phiên bản, không nêu rationale. |
| Apache PDFBox | 3.0.8 | Có dependency phục vụ PDF | [SUY LUẬN] Chức năng PDF cụ thể cần đối chiếu PayslipPdfRenderer. |
| React | ^18.3.1 | UI web | [SUY LUẬN] Lý do chọn không được ghi trong docs/plan.md. |
| TypeScript | ~6.0.2 | Kiểu tĩnh frontend | [SUY LUẬN] |
| Ant Design | ^5.29.3; @refinedev/antd ^5.47.0 | Component UI và adapter Refine | [SUY LUẬN] |
| Refine Core | ^4.58.0; React Router provider ^4.6.2 | Resource/admin framework và router integration | [SUY LUẬN] |
| React Router DOM | ^6.30.3 | SPA navigation | [SUY LUẬN] |
| TanStack Query | ^4.41.0 | Cache/query layer dùng cùng Refine | [SUY LUẬN] |
| Zustand | ^5.0.15 | Client state | [SUY LUẬN] |
| Leaflet / MapLibre GL / deck.gl | ^1.9.4 / ^3.6.2 / ^8.9.36 | Thư viện bản đồ/visualization có trong dependency | Việc cài dependency không chứng minh dữ liệu vị trí live. |
| Vitest / Playwright | ^2.1.8 / 1.64.0 | Unit/component và browser E2E test tooling | [SUY LUẬN] |

(nguồn: pom.xml:6-29, 33-124, 146-164; /home/vumoi/logictics-app/package.json:1-73; docs/plan.md:382-418)

## Quy trình phát triển thấy trong repo

docs/plan.md mô tả trình tự sprint khuyến nghị 1–7; đây là kế hoạch, không phải biên bản chứng minh team đã chạy đúng Agile/Scrum. KHÔNG TÌM THẤY TRONG CODE bằng chứng về sprint đã diễn ra, backlog tool, ceremony, phân công nhóm hoặc UAT thực tế. Repo frontend có workflow CI cấu hình bằng GitHub Actions; điều đó chỉ chứng minh có file workflow, không chứng minh đã chạy thành công. (nguồn: docs/plan.md:382-418; /home/vumoi/logictics-app/.github/workflows/main.yml:1-35)

## Trạng thái kiểm chứng một số tính năng trọng tâm

Các trạng thái này mô tả mức hỗ trợ trong source snapshot, không phải nghiệm thu UAT.

| Tính năng | Trạng thái | Căn cứ và giới hạn |
|---|---|---|
| AI dispatch | MỚI LÀM MỘT PHẦN | Có API run/session/approve/reject và service phối hợp candidate scoring, có thể gọi LLM để rerank và có fallback; phụ thuộc cấu hình/provider, code mới nằm trong working tree chưa commit. (nguồn: src/main/java/com/company/logicstic/ai/dispatch/AiDispatchController.java:27-222; AiDispatchService.java:55-132, 221-289; src/main/resources/application.yml:36-47) |
| Optimization | MỚI LÀM MỘT PHẦN | Có run, score, feasibility, audit và accept có kiểm tra freshness/idempotency; input provider cho routing/ELD/availability là cổng tích hợp, tính sẵn có ngoài môi trường không được xác minh. (nguồn: OptimizationRunController.java:20-39; OptimizationApplicationService.java:30-92; OptimizationAcceptanceService.java:34-84; OptimizationInputProvider.java:7-8) |
| Bản đồ | MỚI LÀM MỘT PHẦN | Frontend có trang map và route map visualization; KHÔNG TÌM THẤY TRONG CODE backend một luồng telemetry/WebSocket live tương ứng ở route inventory đã khảo sát. (nguồn: /home/vumoi/logictics-app/src/pages/map/MapPage.tsx:6-14; /home/vumoi/logictics-app/src/components/dashboard/NetworkRouteMap.tsx:21-42; KHÔNG TÌM THẤY TRONG CODE) |
| Theo dõi vị trí trực tiếp | CHƯA CÓ | KHÔNG TÌM THẤY TRONG CODE luồng nhận GPS/ELD liên tục hoặc API WebSocket vị trí. Có lịch sử/mileage attribution cho phương tiện nhưng đó không tự chứng minh location tracking live. (nguồn: src/main/java/com/company/logicstic/controller/FleetHistoryController.java:17-23; KHÔNG TÌM THẤY TRONG CODE) |
| Notifications | MỚI LÀM MỘT PHẦN | Có API đọc danh sách/chi tiết và mark-all-read; không thấy lệnh gửi notification trong controller này và chưa xác minh trigger tự động. (nguồn: NotificationController.java:21-50; NotificationService.java:17-38; KHÔNG TÌM THẤY TRONG CODE) |
| Báo cáo phân tích | MỚI LÀM MỘT PHẦN | Có nhiều REST report endpoints; trang Executive Dashboard bị khóa bởi EXECUTIVE_CONTRACT_CONFIRMED=false. Executive-summary contract và một số profitability dimensions chưa được xác nhận; không lấy điều này để phủ nhận các report route độc lập. (nguồn: ReportController.java:37-222; /home/vumoi/logictics-app/src/features/executive/config/executive.contract.ts:1-5; /home/vumoi/logictics-app/docs/backend-gaps.md:20,25,47,234-246) |
| Documents | MỚI LÀM MỘT PHẦN | Có list/detail/delete; frontend chủ động bỏ create/edit generic vì chưa có multipart upload adapter. (nguồn: DocumentController.java:24-55; /home/vumoi/logictics-app/src/pages/resourceRegistry.ts:120-127) |
| Lark | MỚI LÀM MỘT PHẦN | Có OAuth/login, đồng bộ Load/Driver sang Base và event inbox/dispatcher; source Lark sync/event mới có file chưa commit. KHÔNG TÌM THẤY TRONG CODE endpoint bot broadcast/interactive card tương ứng kế hoạch. (nguồn: LarkAuthController.java:17-60; LarkBaseController.java:17-52; src/main/java/com/company/logicstic/integration/lark/base/LarkSyncController.java:12-43; LarkEventController.java:15-49; docs/plan.md:349-361; git status backend ngày 20-10-2026) |

## Dữ liệu và giới hạn diễn giải

Code/API không chứng minh dữ liệu runtime nào đang là LIVE. JSON examples, test resources, synthetic fixtures hoặc dữ liệu do seeder tạo cần được gắn rõ DEMO/FIXTURE; không dùng chúng làm thống kê hoạt động doanh nghiệp. API snapshot trong docs/frontend/api-operation-index.md được tạo từ JAR snapshot ngày 06-10-2026, có deployment_verified=false, nên không dùng thay route registry của source đang dirty ngày 20-10-2026. (nguồn: docs/frontend/api-operation-index.md:1-7; docs/frontend/openapi-provenance.json:1-19)

Các thông tin về doanh nghiệp thực tập, hệ thống trước khi số hóa, số liệu ngành logistics, kết quả vận hành thực tế và phần việc cá nhân của sinh viên đều [CẦN NGƯỜI DÙNG BỔ SUNG].

