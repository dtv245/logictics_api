# 10. Thuật ngữ và câu hỏi bổ sung

## 10.1 Tên gọi chuẩn dùng xuyên suốt báo cáo

Tên chuẩn bám theo entity/resource code. Khi giao diện hoặc nghiệp vụ dùng tên khác, ghi cả tên đó lần đầu rồi dùng tên chuẩn trong các chương sau.

| Tên chuẩn | Diễn giải tiếng Việt thống nhất | Ghi chú đối chiếu |
|---|---|---|
| Tenant | Tổ chức/phạm vi dữ liệu độc lập; trong code là tenant ID và tenant database context | Có tenant-aware routing nhưng tenancy cấu hình mặc định tắt; không mặc định viết “SaaS đang vận hành”. (nguồn: `src/main/java/com/company/logicstic/config/TenantContext.java:7-33`, `src/main/resources/application.yml:64-82`) |
| Customer | Khách hàng thuê dịch vụ vận tải | Entity riêng, có thể liên kết với nhiều Load. (nguồn: `src/main/java/com/company/logicstic/entity/Customer.java:16-48`, `src/main/java/com/company/logicstic/entity/Load.java:77-88`) |
| Load | Đơn/nhu cầu vận chuyển mà hệ thống quản lý | Giữ nguyên tên code Load để tránh lẫn với “hàng hóa” hoặc Trip. (nguồn: `src/main/java/com/company/logicstic/entity/Load.java:19-57`) |
| Trip | Chuyến vận tải; có Truck, Driver assignment và các TripStop | Tên entity/route là Trip. (nguồn: `src/main/java/com/company/logicstic/entity/Trip.java:18-82`) |
| Truck | Xe/phương tiện vận tải | Không đồng nhất Truck với Container. (nguồn: `src/main/java/com/company/logicstic/entity/Truck.java:16-49`, `src/main/java/com/company/logicstic/entity/Container.java:27-39`) |
| Employee | Nhân viên; một số Employee được sử dụng như Driver qua role hoặc quan hệ assignment | Driver API/UI có thể trình chiếu một tập employee; hai danh từ không luôn là hai bảng riêng. (nguồn: `src/main/java/com/company/logicstic/entity/Employee.java:14-62`, `src/main/java/com/company/logicstic/entity/TripDriverAssignment.java:18`) |
| TripStop / Stop | Điểm dừng trong Trip, liên kết Trip và Load | Mỗi điểm có thứ tự, địa chỉ, status và các mốc thực hiện. (nguồn: `src/main/java/com/company/logicstic/entity/TripStop.java:28-93`) |
| Document | Metadata chứng từ/tệp và liên kết chủ thể; không mặc định là nội dung file đã upload thành công | Upload multipart chưa được chứng minh ở API hiện hành. (nguồn: `src/main/java/com/company/logicstic/entity/Document.java:35-125`, `src/main/java/com/company/logicstic/controller/DocumentController.java:24-57`) |
| Invoice | Hóa đơn/record billing trong hệ thống | Có invoice type/status và các command billing riêng. (nguồn: `src/main/java/com/company/logicstic/entity/Invoice.java:30-109`, `src/main/java/com/company/logicstic/controller/BillingController.java:17-55`) |
| Payment | Khoản thu payment gắn invoice; không gộp với khoản chi payroll payment | Payroll payment là workflow khác. (nguồn: `src/main/java/com/company/logicstic/entity/Payment.java:27-55`, `src/main/java/com/company/logicstic/entity/PayrollPayment.java:9-18`) |
| Settlement | Bảng tính/quyết toán earnings và điều chỉnh của Driver theo pay period | Hành động lock/approve/audit có semantics tài chính; không gọi nó là khoản thanh toán ngân hàng. (nguồn: `src/main/java/com/company/logicstic/entity/DriverSettlement.java:12-50`) |
| Payroll Run | Lần tổng hợp payroll gồm nhiều payroll run items | Payslip/payment có liên hệ run item theo schema. (nguồn: `src/main/resources/db/migration/tenant/V11__create_payroll_and_payslip_tables.sql:12-102`) |
| AI Dispatch | Workflow sinh candidate/decision điều phối và cho người dùng approve/reject | Gọi là “đề xuất AI Dispatch” nếu chưa có bằng chứng lệnh đã được thực thi. (nguồn: `src/main/java/com/company/logicstic/ai/dispatch/AiDispatchService.java:55-195,221-300`) |
| Optimization | Chạy policy tạo các candidate assignment và có bước accept riêng | Không đồng nhất với AI Dispatch. (nguồn: `src/main/java/com/company/logicstic/service/optimization/OptimizationApplicationService.java:30-92`, `src/main/java/com/company/logicstic/service/optimization/OptimizationAcceptanceService.java:34-84`) |
| Lark Base / Bitable | Nền tảng ngoài được tích hợp để đọc/đồng bộ record Load/Driver | Dùng “Lark Base” nhất quán, nêu “Bitable” nếu nhãn UI/API/tài liệu lịch sử gọi như vậy. (nguồn: `src/main/java/com/company/logicstic/integration/lark/base/LarkBaseService.java:8-26`, `src/main/java/com/company/logicstic/integration/lark/base/LarkSyncService.java:14-118`) |

## 10.2 Từ viết tắt

| Viết tắt | Mở rộng | Cách dùng trong context |
|---|---|---|
| AI | Artificial Intelligence | Nêu rõ là candidate/ranking, không gọi thành tự động quyết định toàn bộ dispatch nếu cần con người approve. (nguồn: `src/main/java/com/company/logicstic/ai/dispatch/AiDispatchService.java:55-195`) |
| API | Application Programming Interface | Endpoint HTTP/REST của backend. |
| CI | Continuous Integration | Workflow tự động kiểm tra source. (nguồn: `.github/workflows/backend-remediation.yml:1-25`) |
| CRUD | Create, Read, Update, Delete | Phải phân biệt resource có đủ bốn thao tác với read-only/command-specific API. (nguồn: `/home/vumoi/logictics-app/src/pages/resourceRegistry.ts:20-96,120-153`) |
| CPM | Cost Per Mile | Xuất hiện trong API known-operating-CPM; đơn vị/định nghĩa nghiệp vụ phải xác nhận theo công thức report. (nguồn: `src/main/java/com/company/logicstic/controller/ReportController.java:203-214`, `/home/vumoi/logictics-app/src/features/dashboard/api.ts:62-79`) |
| DTO | Data Transfer Object | Request/response type giữa controller và client. |
| DVIR | Driver Vehicle Inspection Report | Nhóm bảng/page kiểm tra phương tiện. (nguồn: `src/main/resources/db/migration/tenant/V1__baseline_business_schema.sql:959-998`, `/home/vumoi/logictics-app/src/pages/index.ts:253-255`) |
| ELD | Electronic Logging Device | Có provider config/mapping, nhưng mức tích hợp live phải xác minh. (nguồn: `src/main/resources/db/migration/tenant/V1__baseline_business_schema.sql:102-123,530-567`) |
| ERD | Entity Relationship Diagram | Sơ đồ quan hệ bảng trong Chương 2.4. |
| HOS | Hours of Service | Có HOS logs/status/violations. Chuẩn ngành/quốc gia áp dụng cần người dùng xác nhận. (nguồn: `src/main/resources/db/migration/tenant/V1__baseline_business_schema.sql:356-399,506-530`) |
| HTTT | Hệ thống thông tin | Thuật ngữ môn học; cần viện dẫn giáo trình/nguồn học thuật bên ngoài repo. [CẦN NGƯỜI DÙNG BỔ SUNG] |
| HTTP | Hypertext Transfer Protocol | Giao thức các controller REST. |
| JPA | Jakarta Persistence API | ORM mapping dùng trong backend. (nguồn: `pom.xml:58-78`) |
| JWT | JSON Web Token | Token identity; tenant claim được đọc từ JWT khi tenancy bật. (nguồn: `src/main/java/com/company/logicstic/config/TenantJwtClaimFilter.java:69-74`) |
| KPI | Key Performance Indicator | Chỉ số hiệu suất; phải ghi nguồn dữ liệu, thời gian, trạng thái live/mock. (nguồn: `/home/vumoi/logictics-app/src/features/dashboard/components/OperationsDashboardView.tsx:53-103`) |
| LLM | Large Language Model | Backend cấu hình provider/model LLM tùy môi trường cho AI flow; không đồng nghĩa model đã khả dụng trong từng lần chạy. (nguồn: `src/main/resources/application.yml:36-47`) |
| OTD | On-Time Delivery | Tên KPI trong endpoint `on-time-delivery`; cách tính cần đối chiếu ReportService/contract và tài liệu nghiệp vụ. (nguồn: `/home/vumoi/logictics-app/src/features/dashboard/api.ts:68-70`) |
| OAuth2 | Open Authorization 2.0 | Chuẩn authorization; repo có Lark OAuth/login flow; cách gọi giao thức phải đối chiếu cấu hình/provider. (nguồn: `src/main/java/com/company/logicstic/integration/lark/auth/LarkAuthController.java:17-60`) |
| ORM | Object-Relational Mapping | Mapping JPA entity tới PostgreSQL schema. (nguồn: `pom.xml:58-78`) |
| PK / FK | Primary Key / Foreign Key | Khóa chính/khóa ngoại trong DDL. |
| POD | Proof of Delivery | Thuật ngữ nghiệp vụ thường dùng cho chứng từ giao hàng; chưa tìm thấy loại upload API theo contract hiện tại. [CẦN XÁC MINH] |
| RBAC | Role-Based Access Control | Backend dùng role matchers/tenant roles; frontend permission chủ yếu lọc UI. (nguồn: `src/main/java/com/company/logicstic/config/SecurityConfig.java:44-129`, `/home/vumoi/logictics-app/src/providers/permissions/roleMatrix.ts:1-57`) |
| REST | Representational State Transfer | Kiểu API HTTP được triển khai bằng Spring MVC controller mappings. |
| SaaS | Software as a Service | Mô hình sản phẩm/delivery; chưa có bằng chứng production SaaS deployment chỉ từ source. |
| TMS | Transportation Management System | Hệ thống quản lý vận tải; mô tả trong README. (nguồn: `README.md:1-3`) |
| Trip | Trip (chuyến vận tải) | Giữ tên model nhất quán xuyên báo cáo. |
| UI/UX | User Interface / User Experience | Giao diện/trải nghiệm sử dụng. |
| UML | Unified Modeling Language | Ngôn ngữ mô hình hóa; sơ đồ cần bám execution flow thật. |
| UUID | Universally Unique Identifier | Kiểu ID phổ biến của entity; không áp dụng đồng nhất cho mọi bảng (tenant_registry dùng tenant_id). (nguồn: `src/main/java/com/company/logicstic/entity/Load.java:37-41`, `src/main/resources/db/migration/registry/V1__create_tenant_registry.sql:1-7`) |

Một số viết tắt như POD, HOS, CPM có thể có nghĩa chuyên ngành theo pháp lý/quy trình doanh nghiệp; phần định nghĩa nghiệp vụ dùng trong báo cáo phải được người hướng dẫn xác nhận, không suy ra từ tên field/API. [CẦN NGƯỜI DÙNG BỔ SUNG]

## 10.3 Câu hỏi cần người dùng/đơn vị thực tập bổ sung

### Thông tin đơn vị và bối cảnh

1. Tên pháp lý, tên giao dịch, lịch sử hình thành, quy mô, lĩnh vực và cơ cấu của Công ty Ship/CETA là gì? Có tài liệu công khai hoặc tài liệu nội bộ nào được phép trích dẫn không? [CẦN NGƯỜI DÙNG BỔ SUNG]
2. LogisticsX là sản phẩm của doanh nghiệp, đồ án nội bộ, prototype hay tên dùng trong môi trường thực tập? Tên công ty trên báo cáo có được phép công khai không? [CẦN NGƯỜI DÙNG BỔ SUNG]
3. Thời gian thực tập, đơn vị/phòng ban tiếp nhận, người hướng dẫn và vị trí công việc cần ghi thế nào? [CẦN NGƯỜI DÙNG BỔ SUNG]
4. Doanh nghiệp đang vận hành loại hình vận tải nào, thị trường/quốc gia nào, phương tiện và đối tác nào? Source có HOS/ELD/ADR fields nhưng không chứng minh các chuẩn đó đang được áp dụng ở doanh nghiệp. [CẦN NGƯỜI DÙNG BỔ SUNG]

### Công việc cá nhân và quy trình thực tế

5. Phần code/thiết kế/test nào do sinh viên trực tiếp thực hiện, phần nào do team có sẵn? Cần commit/PR/ticket/tài liệu thời gian nào làm bằng chứng cá nhân? [CẦN NGƯỜI DÙNG BỔ SUNG]
6. Quy trình quản lý vận tải “thủ công” trước hệ thống thực tế đang dùng sổ tay, Excel, email, phần mềm khác hay quy trình kết hợp? Ai phê duyệt và các điểm tắc nghẽn nào đã được quan sát/phỏng vấn? [CẦN NGƯỜI DÙNG BỔ SUNG]
7. Có số liệu baseline và sau triển khai (thời gian lập chuyến, lỗi điều phối, tỷ lệ đúng hạn, chi phí nhiên liệu) từ nguồn được phép công bố không? Không dùng sample KPI từ preview làm kết quả doanh nghiệp. [CẦN NGƯỜI DÙNG BỔ SUNG]
8. Team thật sự dùng Agile/Scrum, có sprint/planning/review hay chỉ có roadmap trong docs? Ai dùng công cụ ticket/version-control và lịch release nào? Source chỉ có kế hoạch sprint, không chứng minh quy trình đã chạy. [CẦN NGƯỜI DÙNG BỔ SUNG]

### Kiến trúc và triển khai

9. Bản nháp Laravel/PHP/MySQL thuộc hệ thống khác hay sai phiên bản? Cần chủ sản phẩm xác nhận stack chính thức của đề tài là Spring Boot/PostgreSQL backend + React frontend hay stack khác. [CẦN NGƯỜI DÙNG BỔ SUNG]
10. Có frontend/backend branch/release chính thức, environment UAT/production và OpenAPI snapshot đúng runtime cần dùng để chụp màn hình/đánh giá không? [CẦN NGƯỜI DÙNG BỔ SUNG]
11. Tenancy được bật trong môi trường nào? Tenant nào được phép đưa vào báo cáo? Cần xác nhận cơ chế ẩn danh/đồng ý sử dụng dữ liệu; không gửi credential. [CẦN NGƯỜI DÙNG BỔ SUNG]
12. Lark Base, AI provider, map provider, telematics/ELD và storage nào đang được cấu hình và kiểm thử ngoài source? Nêu phiên bản/contract, không gửi secret/token. [CẦN NGƯỜI DÙNG BỔ SUNG]
13. Tên API/UI, thuật ngữ nội bộ, role thực tế có khác tên trong code không? Nếu có, cung cấp bảng mapping được phê duyệt để thống nhất chương 2 và 3. [CẦN NGƯỜI DÙNG BỔ SUNG]

### Tài liệu tham khảo cần bổ sung

Danh sách dưới là nguồn chính thức gợi ý để đối chiếu định nghĩa framework/chuẩn, không phải tài liệu chứng minh hệ thống CETA đã dùng stack đó. Gắn nhãn [CẦN XÁC MINH] để chọn đúng edition/version tương ứng ngày triển khai và quy định trích dẫn của Trường Đại học Điện Lực:

- [CẦN XÁC MINH] Laravel 12.x official documentation — chỉ dùng nếu báo cáo cần giải thích hoặc đối chiếu tuyên bố Laravel trong bản nháp; source backend hiện không phải Laravel: [Laravel 12 docs](https://laravel.com/framework/docs/12.x/installation).
- [CẦN XÁC MINH] React official Learn/Reference — dùng cho khái niệm component/hooks trong frontend: [React Learn](https://react.dev/learn), [React Reference](https://react.dev/reference/react).
- [CẦN XÁC MINH] Ant Design official React docs — chọn version 5 docs tương ứng dependency `antd` 5.x thay vì bản docs đang hiển thị version mới hơn: [Ant Design React Introduction](https://ant.design/docs/react/introduce/?locale=en).
- [CẦN XÁC MINH] Refine official migration documentation — dùng phần phân biệt Refine 4 và Refine 5; dependency source hiện là `@refinedev/core` v4: [Refine 4.x to 5.x migration guide](https://github.com/refinedev/refine/blob/main/documentation/docs/migration-guide/4x-to-5x.md).
- [CẦN XÁC MINH] Spring Boot 4.1.1 reference — ưu tiên version tương ứng Maven parent: [Spring Boot Reference](https://docs.spring.io/spring-boot/reference/).
- [CẦN XÁC MINH] PostgreSQL documentation version — Compose dùng PostgreSQL 16; chọn manual version 16 nếu giải thích environment local: [PostgreSQL 16 documentation](https://www.postgresql.org/docs/16/).
- [CẦN XÁC MINH] OMG UML 2.5.1 formal specification — dùng khi viện dẫn khái niệm/notation UML: [OMG UML specification](https://www.omg.org/spec/UML/2.5.1).
- [CẦN XÁC MINH] Giáo trình chính thức môn Hệ thống thông tin quản lý, giáo trình Phân tích thiết kế hệ thống hướng đối tượng và quy định trích dẫn của trường/khoa: cần thông tin thư viện/giảng viên, không tự tạo tên tác giả/ấn bản. [CẦN NGƯỜI DÙNG BỔ SUNG]
