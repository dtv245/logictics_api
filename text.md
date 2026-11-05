# LogisticsX — Bộ ngữ cảnh Phân tích & Thiết kế hệ thống

> **Mục đích:** tài liệu ngữ cảnh (context) để viết chương "Phân tích và thiết kế hệ thống" cho báo cáo tốt nghiệp.
> **Thời điểm khảo sát:** 2026-11-03.
> **Phạm vi khảo sát:** repo backend `logictics_api`, repo frontend `logictics-app`, hồ sơ `docs/bao-cao-uml/`, `docs/report-context/`, `docs/docs/`, `docs/ai-dispatch/` (worktree), migration Flyway, ma trận phân quyền frontend.
> **Nguyên tắc:** chỉ ghi nhận nội dung có bằng chứng trong mã nguồn/tài liệu; phần chưa đủ căn cứ đánh dấu `[CẦN BỔ SUNG]`; không suy diễn chức năng từ tên thư mục; không đưa giá trị bí mật (`.env`, token, mật khẩu, API key) vào báo cáo.

---

## 0. Cách dùng tài liệu này

1. **Đọc mục 1** để biết nguồn nào tin được, nguồn nào đã cũ.
2. **Chốt mục 3 (danh mục chức năng)** trước — mọi đặc tả, sơ đồ, bảng dữ liệu đều phụ thuộc danh mục này.
3. **Dùng mục 4** làm khung mục lục chương.
4. **Dùng mục 5** để đánh số hình/bảng ngay từ đầu, tránh trùng số.
5. **Dùng mục 6** để kiểm tra từng đoạn viết có căn cứ không.
6. **Đọc mục 7** trước khi khẳng định "đã hoàn thiện".

---

## 1. Kết luận khảo sát và cảnh báo nguồn

### 1.1. Hiện trạng hồ sơ có sẵn

| Nguồn | Nội dung thực tế | Dùng được cho báo cáo? |
|---|---|---|
| `docs/bao-cao-uml/` | Chỉ bao phủ module Load (UC001–UC008), 24 sơ đồ; README tự ghi "chưa hoàn thành" | **Không** dùng làm bản cuối; có thể tham khảo cách trình bày |
| `docs/report-context/` | 10 file, ảnh chụp 20-10-2026, HEAD `2bc3944`; số liệu đã cũ (47 controller/200 handler/44 migration) | Dùng làm **bản nháp**, phải cập nhật số liệu |
| `docs/docs/` | Tài liệu thời .NET/Angular, không khớp mã hiện tại | **Không** dùng |
| `docs/ai-dispatch/` | Chỉ có trong worktree `/home/vumoi/logictics_api_complete`, branch `ai-dispatch/complete` | Dùng nếu báo cáo mô tả bản AI đầy đủ |
| `postman/` | Bộ collection chia Part1–Part3 | Tham khảo endpoint, không thay thế source |
| `README.md` | Có số liệu lệch với `pom.xml` và migration | Đọc có chọn lọc |

### 1.2. Mâu thuẫn tài liệu — mã nguồn (phải nêu trong báo cáo, không chọn im lặng)

| Nội dung | Tài liệu ghi | Mã nguồn thực tế | Xử lý đề xuất |
|---|---|---|---|
| Nền tảng backend | `docs/docs/architecture/overview.md`: .NET 10 | Spring Boot 4.1.1, Java 21 (`pom.xml`) | Bỏ tài liệu .NET; lấy `pom.xml` làm chuẩn |
| Frontend | Tài liệu cũ: Angular | React 18 + TypeScript 6 + Refine 4 + AntD 5 | Lấy `package.json` làm chuẩn |
| Phiên bản Spring Boot | README: 4.1.0 | `pom.xml`: 4.1.1 | Ghi 4.1.1 |
| Flyway | README: disabled | `application.yml`: enabled | Ghi enabled |
| Số bảng | README: 50+ | 53 migration tenant V1–V53 | Ghi theo migration, kèm ngày đếm |
| Số endpoint | Tài liệu cũ: 175/200 | 280 handler annotation | Sinh lại bảng từ source |
| Số controller | Tài liệu cũ: 47 | 66 class `*Controller` | Sinh lại |
| Số test | README: 47 class | 151 file `*Test.java` | Sinh lại |
| Giao diện vận hành | Tài liệu cũ: `/dashboard`, `/operations` | Đã chuyển sang `/tms`, `/tms/dispatch`, `/tms/approvals`, `/tms/shipments/:id` | Lấy `AppRouter.tsx` làm chuẩn |
| AI dispatch | Main workspace chỉ có bản một phần | Bản đầy đủ P0.5–P4 ở branch `ai-dispatch/complete` | **Phải chọn một phiên bản** và ghi rõ |

### 1.3. Phương pháp khảo sát và con số đã kiểm

Đếm tĩnh trên source (không phải introspection DB đang chạy):

| Hạng mục | Cách đếm | Kết quả |
|---|---|---:|
| Controller class | `*Controller.java` trong `src/main/java` | 66 |
| Handler method | annotation `@GetMapping/@PostMapping/@PutMapping/@DeleteMapping` | 280 (127 GET, 105 POST, 26 PUT, 22 DELETE) |
| Entity class | gói `entity` | 63 |
| Migration tenant | `db/migration/tenant/V*.sql` | 53 (V1–V53) |
| Test class backend | `*Test.java` | 151 |
| Trang/route frontend | `AppRouter.tsx` + `routes.ts` | ~30 route nghiệp vụ + luồng TMS |

> Trong báo cáo nên ghi: "số liệu đếm tĩnh trên mã nguồn ngày 2026-11-03", không ghi "hệ thống production có X bảng/Y API".

---

## 2. PART 1 — TỔNG QUAN DỰ ÁN

### 2.1. Sản phẩm

**LogisticsX** — hệ thống quản lý và điều phối vận tải (TMS), gồm:

- **Backend REST API** phục vụ toàn bộ nghiệp vụ.
- **Web application (SPA)** cho người dùng vận hành, kế toán, nhân sự lương và tài xế.
- **Tích hợp ngoài:** Lark (đăng nhập, Base, webhook sự kiện), HOS/ELD (adapter), optimization provider, Anthropic (giải thích đề xuất AI).

Không có mobile app trong source hiện tại. Không có customer portal / public tracking / upload chứng từ.

### 2.2. Công nghệ

| Lớp | Công nghệ | Căn cứ |
|---|---|---|
| Backend | Java 21, Spring Boot 4.1.1, Spring MVC | `pom.xml`; `spring-boot-starter-webmvc` |
| Bảo mật | Spring Security, OAuth2 Resource Server, JJWT | `pom.xml`; `SecurityConfig.java` |
| Dữ liệu | Spring Data JPA/Hibernate (`ddl-auto: validate`), Flyway, PostgreSQL | `application.yml`; `db/migration/tenant/V1–V53` |
| Validation | Jakarta Bean Validation (`@Valid`) | `pom.xml`; các `Create*Request` |
| API docs | SpringDoc OpenAPI / Swagger UI | `pom.xml`; `SecurityConfig` permit `/v3/api-docs` |
| Frontend | React 18.3, TypeScript 6, Vite 8 | `logictics-app/package.json` |
| UI framework | Ant Design 5, Refine 4, React Router 6 | `package.json` |
| Data fetching | TanStack Query 4, axios | `package.json` |
| i18n | i18next, react-i18next (vi mặc định) | `main.tsx`, `i18nProvider` |
| Test | JUnit/Spring Boot Test/REST Assured/Testcontainers; Vitest/Playwright | `pom.xml`; `package.json` |
| Triển khai local | Docker Compose: API + PostgreSQL 16 + Redis 7 | `docker-compose.yml` |

### 2.3. Kiến trúc

Monolith phân lớp, stateless, REST:

```text
Browser (React SPA)
   │ HTTPS + Bearer JWT
   ▼
Spring Security (LarkAuthenticationFilter, TenantJwtClaimFilter)
   ▼
Controller → DTO/Validation → Service → Repository (JPA) → PostgreSQL
                                   │
                                   ├─ Integration: Lark / HOS / Optimization
                                   └─ AI: Dispatch, Gap, LLM (explanation-only)
```

- **Tenant-aware:** `TenantContext` + `TenantRoutingDataSource` (database-per-tenant), mặc định `tenancy.enabled=false`.
- **Migration:** Flyway, schema `tenant` và `registry`.
- **Frontend** là repo riêng, gọi REST qua axios/Refine data provider.
- **Không có microservice, không có message broker** trong source hiện tại.

### 2.4. Quy mô source

| Hạng mục | Số lượng |
|---|---:|
| Controller classes | 66 |
| Handler method | 280 |
| Entity classes | 63 |
| Migration tenant | 53 (V1–V53) |
| Test class backend | 151 |
| Route frontend nghiệp vụ | ~30 + luồng TMS |

### 2.5. Các module nghiệp vụ

1. Xác thực & phiên (Lark OAuth → JWT nội bộ)
2. Quản trị & phân quyền (vai trò tenant)
3. Danh mục & đội xe (khách hàng, nhân viên, tài xế, xe, terminal, kiểm tra)
4. Hồ sơ tài xế (giấy phép, sức khỏe, chứng chỉ, hợp đồng, bảo hiểm, người phụ thuộc, ngân hàng, vi phạm, tai nạn, trạng thái vận hành, self-service)
5. Load (đơn vận chuyển) & timeline
6. Trip & thực thi điểm dừng
7. AI dispatch, gap detection, optimization
8. Định giá (rating) & chính sách giá
9. Hóa đơn & thanh toán
10. Chi phí lô hàng, phụ phí, duyệt chi phí
11. Lương tài xế & quyết toán (settlement, payroll, payslip, payment)
12. Lịch sử đội xe (status event, mileage attribution, policy)
13. Báo cáo (vận hành, tài chính, đội xe, lợi nhuận, xếp hạng tài xế)
14. Thông báo & tài liệu
15. Tích hợp Lark (Base sync, event webhook)
16. Tiện ích hệ thống (health, build info)

---

## 3. PART 2 — DANH MỤC CHỨC NĂNG

### 3.1. Quy ước trạng thái

| Ký hiệu | Ý nghĩa |
|---|---|
| **API+UI** | Có controller + service + trang/route frontend được mount; **chưa nghiệm thu runtime** |
| **API** | Có backend; UI chưa mount/không có |
| **UI+API** | Luồng UI tổng hợp gọi nhiều API có sẵn |
| **Một phần** | Có một phía, bị cờ tắt, hoặc phụ thuộc provider/contract chưa xác minh |
| **Chưa có** | Không tìm thấy implementation |
| **Cần xác minh** | Có mã nhưng chưa xác định route/flag |

### 3.2. Bảng danh mục (90 dòng)

| STT | Nhóm chức năng | Mã UC | Tên Use Case | Tác nhân | Trạng thái | File căn cứ |
|---:|---|---|---|---|---|---|
| 1 | Xác thực | AUTH-01 | Đăng nhập qua Lark | Nhân viên có tài khoản Lark | API+UI | `LarkAuthController.java`, `LarkAuthService.java`; `src/pages/auth/` |
| 2 | Xác thực | AUTH-02 | Nạp identity & tenant hiện hành | Người dùng đã đăng nhập | API+UI | `CurrentUserController.java`; `GET /api/me` |
| 3 | Xác thực | AUTH-03 | Chọn tenant | Người dùng | Một phần (không có API list/switch) | `SelectTenantPage.tsx`, `useTenantList.ts` |
| 4 | Quản trị | ADM-01 | Quản lý vai trò tenant (CRUD) | ADMIN | API+UI | `RoleController.java`, `TenantRole.java` |
| 5 | Quản trị | ADM-02 | Xem build info nội bộ | ADMIN | API | `InternalBuildController.java` |
| 6 | Danh mục | MDM-01 | Quản lý khách hàng (CRUD + tra cứu) | ADMIN, ACCOUNTANT, DISPATCHER | API+UI | `CustomerController.java`; `resourceRegistry.ts` |
| 7 | Danh mục | MDM-02 | Quản lý nhân viên (CRUD) | ADMIN, DISPATCHER | API+UI | `EmployeeController.java` |
| 8 | Danh mục | MDM-03 | Danh bạ tài xế | ADMIN, DISPATCHER | API+UI | `DriverController.java` |
| 9 | Danh mục | MDM-04 | Quản lý xe (CRUD) | ADMIN, ACCOUNTANT, DISPATCHER | API+UI | `TruckController.java` |
| 10 | Danh mục | MDM-05 | Quản lý terminal (CRUD) | ADMIN, ACCOUNTANT, DISPATCHER | API+UI | `TerminalController.java` |
| 11 | Danh mục | MDM-06 | Quản lý kiểm tra xe (CRUD) | ADMIN, DISPATCHER | API | `InspectionController.java` |
| 12 | Hồ sơ tài xế | DRV-01 | Xem/cập nhật hồ sơ tài xế | ADMIN, DISPATCHER, PAYROLL | API+UI | `DriverProfileController.java`; `DriverProfilePage.tsx` |
| 13 | Hồ sơ tài xế | DRV-02 | Quản lý giấy tờ (giấy phép, chứng chỉ, sức khỏe, hợp đồng, bảo hiểm) | ADMIN, DISPATCHER, PAYROLL | API+UI | `DriverLicenseController`, `DriverCertificationController`, `DriverHealthCheckController`, `EmploymentContractController`, `DriverInsuranceController` |
| 14 | Hồ sơ tài xế | DRV-03 | Dữ liệu cá nhân & thanh toán (liên hệ khẩn cấp, người phụ thuộc, tài khoản ngân hàng) | ADMIN, DISPATCHER, PAYROLL | API+UI | `DriverEmergencyContactController`, `DriverDependentController`, `DriverBankAccountController` |
| 15 | Hồ sơ tài xế | DRV-04 | Vi phạm & tai nạn | ADMIN, DISPATCHER | API+UI | `DriverViolationController.java`, `DriverAccidentController.java` |
| 16 | Hồ sơ tài xế | DRV-05 | Cập nhật trạng thái vận hành | ADMIN, DISPATCHER | API+UI | `DriverOperationalStatusController.java` |
| 17 | Hồ sơ tài xế | DRV-06 | Tài xế tự phục vụ hồ sơ | DRIVER | API+UI | các `DriverSelf*Controller.java`; `DriverSelfProfilePage` |
| 18 | Hồ sơ tài xế | DRV-07 | Danh sách hồ sơ sắp hết hạn | ADMIN, DISPATCHER | API+UI | `DriverProfileSummaryController.java`; `/drivers/expiring` |
| 19 | Load | LOAD-01 | Tra cứu Load (phân trang, lọc) | ADMIN, ACCOUNTANT, DISPATCHER | API+UI | `LoadController.search`, `LoadService.search` |
| 20 | Load | LOAD-02 | Tạo Load | ADMIN, ACCOUNTANT, DISPATCHER | API+UI | `LoadController.create`, `LoadService.create` |
| 21 | Load | LOAD-03 | Xem chi tiết Load | ADMIN, ACCOUNTANT, DISPATCHER | API+UI | `LoadController.getById` |
| 22 | Load | LOAD-04 | Cập nhật Load | ADMIN, ACCOUNTANT, DISPATCHER | API+UI | `LoadController.update`, `LoadService.update` |
| 23 | Load | LOAD-05 | Xóa Load | ADMIN, ACCOUNTANT, DISPATCHER | API (UI cần xác minh) | `LoadController.delete` |
| 24 | Load | LOAD-06 | Xem timeline Load | ADMIN, ACCOUNTANT, DISPATCHER | API+UI | `LoadTimelineController`, `LoadTimelineService` |
| 25 | Load | LOAD-07 | Sửa ngày lấy hàng nghiệp vụ | ADMIN, ACCOUNTANT, DISPATCHER | API (UI bị tắt) | `LoadPickupBusinessDateController`, `LoadPickupBusinessDateService` |
| 26 | Load | LOAD-08 | Xem chứng từ của Load | ADMIN, ACCOUNTANT, DISPATCHER | API+UI | `DocumentController.search`; `LoadDocumentsPanel` |
| 27 | Load | LOAD-09 | Bảng điều phối TMS (tổng hợp) | DISPATCHER | UI+API | `TmsDispatchPage.tsx`, `tmsFlow.api.ts` |
| 28 | Trip | TRIP-01 | Quản lý Trip (CRUD) | ADMIN, DISPATCHER | API+UI | `TripController.java` |
| 29 | Trip | TRIP-02 | Gán/bỏ tài xế cho Trip | ADMIN, DISPATCHER | API+UI | `TripExecutionController.assignDriver/unassignDriver` |
| 30 | Trip | TRIP-03 | Xem danh sách điểm dừng | ADMIN, DISPATCHER | API+UI | `TripExecutionController.getTripStops` |
| 31 | Trip | TRIP-04 | Thực thi điểm dừng (arrive/start/complete/depart) | ADMIN, DISPATCHER | API+UI | `TripExecutionController`, `TripExecutionService` |
| 32 | AI & tối ưu | AID-01 | Kiểm tra/trả lời/bỏ qua gap | ADMIN, DISPATCHER (đọc: +ACCOUNTANT) | API+UI | `AiGapController.java` |
| 33 | AI & tối ưu | AID-02 | Chạy AI dispatch | ADMIN, DISPATCHER | API+UI | `AiDispatchController.runDispatch`, `AiDispatchService` |
| 34 | AI & tối ưu | AID-03 | Xem session/pending/trạng thái Load | ADMIN, DISPATCHER, ACCOUNTANT | API+UI | `AiDispatchController` |
| 35 | AI & tối ưu | AID-04 | Duyệt/từ chối/hủy đề xuất | ADMIN, DISPATCHER | API+UI (cờ approval mặc định tắt) | `approveDecision`, `rejectDecision`, `cancelDecision` |
| 36 | AI & tối ưu | AID-05 | Chạy lại đề xuất cho Load | ADMIN, DISPATCHER | API+UI | `AiDispatchController.rerun` |
| 37 | AI & tối ưu | AID-06 | Chạy optimization & chấp nhận candidate | ADMIN, DISPATCHER | API+UI | `OptimizationRunController`, `OptimizationAcceptanceService` |
| 38 | AI & tối ưu | AID-07 | Quản lý policy & qualified input | ADMIN, ACCOUNTANT, DISPATCHER | API | `OptimizationPolicyController`, `OptimizationQualifiedInputController` |
| 39 | Định giá | RATE-01 | Xem trước giá Load | ADMIN, ACCOUNTANT | API (UI một phần) | `RatingController.preview` |
| 40 | Định giá | RATE-02 | Chấp nhận rating snapshot | ADMIN, ACCOUNTANT | API (UI một phần) | `RatingController.accept` |
| 41 | Định giá | RATE-03 | Quản lý hợp đồng giá & phiên bản | ADMIN, ACCOUNTANT | API | `RatePolicyController` |
| 42 | Định giá | RATE-04 | Quản lý quy tắc giá & phiên bản | ADMIN, ACCOUNTANT | API | `RatePolicyController` |
| 43 | Định giá | RATE-05 | Ghi nhận mileage hợp đồng | ADMIN, ACCOUNTANT | API | `RatingMileageController` |
| 44 | Định giá | RATE-06 | Xem rating snapshot | ADMIN, ACCOUNTANT | API | `RatingController.get` |
| 45 | Hóa đơn | BILL-01 | Quản lý hóa đơn (CRUD) | ADMIN, ACCOUNTANT | API+UI | `InvoiceController.java` |
| 46 | Hóa đơn | BILL-02 | Phát hành/điều chỉnh hóa đơn (billing commands) | ADMIN, ACCOUNTANT | API+UI | `BillingController` (primary/issue/supplemental/credit/rebill/regenerate) |
| 47 | Hóa đơn | BILL-03 | Quản lý dòng hóa đơn | ADMIN, ACCOUNTANT | API | `InvoiceLineController.java` |
| 48 | Hóa đơn | BILL-04 | Ghi nhận thuế | ADMIN, ACCOUNTANT | API | `TaxAssessmentController.java` |
| 49 | Hóa đơn | BILL-05 | Vòng đời thanh toán | ADMIN, ACCOUNTANT | API+UI (command flag) | `PaymentController.java` |
| 50 | Hóa đơn | BILL-06 | Xem công nợ khách hàng | ADMIN, ACCOUNTANT, PAYROLL | API | `ReportController.getCustomerBalance` |
| 51 | Chi phí | COST-01 | Quản lý chi phí lô hàng | ADMIN, ACCOUNTANT, PAYROLL | API | `ShipmentCostController.java` |
| 52 | Chi phí | COST-02 | Đồng bộ chi phí từ expense | ADMIN, ACCOUNTANT, PAYROLL | API | `ShipmentCostController.syncExpenses` |
| 53 | Chi phí | COST-03 | Phân bổ chi phí bảo trì | ADMIN, ACCOUNTANT, PAYROLL | API | `ShipmentCostController.allocateMaintenance` |
| 54 | Chi phí | COST-04 | Kiểm tra/duyệt/void chi phí | ADMIN, ACCOUNTANT | API | `ShipmentCostController` (verify/approve/void) |
| 55 | Chi phí | COST-05 | Quản lý phụ phí (accessorial) | ADMIN, ACCOUNTANT, DISPATCHER | API+UI | `AccessorialController`, `AccessorialChargeController` |
| 56 | Chi phí | COST-06 | Tính detention | ADMIN, ACCOUNTANT, DISPATCHER | API | `AccessorialController.calculateDetention` |
| 57 | Chi phí | COST-07 | Duyệt/từ chối chi phí | ADMIN, ACCOUNTANT | API | `ExpenseApprovalController.java` |
| 58 | Chi phí | COST-08 | Xem lợi nhuận lô hàng | ADMIN, ACCOUNTANT, DISPATCHER | API+UI | `ReportController.getLoadProfitability` |
| 59 | Lương | PAY-01 | Quản lý chính sách lương tài xế | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER | API+UI | `DriverPayPolicyController` |
| 60 | Lương | PAY-02 | Tính quyết toán tài xế | cùng nhóm | API+UI | `DriverSettlementController.calculate` |
| 61 | Lương | PAY-03 | Review/approve/lock settlement | cùng nhóm | API+UI | `DriverSettlementController` |
| 62 | Lương | PAY-04 | Điều chỉnh/reversal/validation settlement | cùng nhóm | API+UI | `DriverSettlementController` |
| 63 | Lương | PAY-05 | Quản lý kỳ lương | cùng nhóm | API | `PayPeriodController` |
| 64 | Lương | PAY-06 | Cấu hình payroll | cùng nhóm | API | `PayrollConfigurationController` |
| 65 | Lương | PAY-07 | Chạy payroll (calculate/review/approve/lock) | cùng nhóm | API+UI | `PayrollRunController` |
| 66 | Lương | PAY-08 | Phát hành payslip & tải PDF | cùng nhóm | API+UI | `PayslipController` |
| 67 | Lương | PAY-09 | Lập lịch/điều phối/đối soát thanh toán lương | cùng nhóm | API | `PayrollPaymentController` |
| 68 | Lương | PAY-10 | Nhận callback từ provider lương | Provider | API | `PayrollCallbackController` |
| 69 | Lương | PAY-11 | Tài xế xem payslip của mình | DRIVER | API+UI | `PayslipController.mine`; `/profile/payslips` |
| 70 | Đội xe | FLEET-01 | Ghi nhận trạng thái xe | ADMIN, DISPATCHER | API | `FleetHistoryController.capture` |
| 71 | Đội xe | FLEET-02 | Ghi nhận mileage attribution | ADMIN, DISPATCHER | API | `FleetHistoryController.attribute` |
| 72 | Đội xe | FLEET-03 | Quản lý policy đội xe | ADMIN (publish), đọc rộng | API | `FleetHistoryController` |
| 73 | Báo cáo | RPT-01 | Báo cáo vận hành (OTD, delay, transit, exceptions) | ADMIN, ACCOUNTANT, PAYROLL, MANAGER | API+UI | `ReportController` |
| 74 | Báo cáo | RPT-02 | Báo cáo tài chính (revenue, monthly, expenses) | cùng nhóm | API+UI | `ReportController` |
| 75 | Báo cáo | RPT-03 | Báo cáo đội xe (fuel, maintenance, utilization, health) | cùng nhóm | API+UI | `ReportController` |
| 76 | Báo cáo | RPT-04 | Báo cáo lợi nhuận (load/lane/truck) | cùng nhóm | API+UI | `ReportController` |
| 77 | Báo cáo | RPT-05 | Xếp hạng tài xế | cùng nhóm | API | `ReportController.getDriverRanking` |
| 78 | Báo cáo | RPT-06 | Chi phí vận hành (known operating CPM) | cùng nhóm | API | `ReportController.getKnownOperatingCpm` |
| 79 | Báo cáo | RPT-07 | Dashboard vận hành TMS | DISPATCHER | UI+API | `TmsOperationsDashboardPage.tsx` |
| 80 | Thông báo | NOTI-01 | Danh sách thông báo | Người dùng đã đăng nhập | API+UI | `NotificationController.list` |
| 81 | Thông báo | NOTI-02 | Xem chi tiết thông báo | cùng nhóm | API+UI | `NotificationController.getById` |
| 82 | Thông báo | NOTI-03 | Đánh dấu tất cả đã đọc | cùng nhóm | API+UI | `NotificationController.markAllAsRead` |
| 83 | Tài liệu | DOC-01 | Tra cứu/xem tài liệu | ADMIN, ACCOUNTANT, DISPATCHER | API+UI | `DocumentController.search/getById` |
| 84 | Tài liệu | DOC-02 | Xóa tài liệu | cùng nhóm | API (UI cần xác minh) | `DocumentController.delete` |
| 85 | Tài liệu | DOC-03 | Upload POD/BOL | — | **Chưa có** | Không có endpoint multipart |
| 86 | Lark | LARK-01 | Đồng bộ Load/Tài xế lên Lark Base | ADMIN, DISPATCHER | API+UI | `LarkBaseController`, `LarkSyncController` |
| 87 | Lark | LARK-02 | Xem trạng thái/backfill/missing sync | ADMIN, DISPATCHER | API | `LarkSyncController` |
| 88 | Lark | LARK-03 | Nhận webhook sự kiện Lark | Lark Platform | API | `LarkEventController`, `LarkEventSecurity` |
| 89 | Hệ thống | SYS-01 | Health check | Public | API | `HealthController` |
| 90 | Hệ thống | SYS-02 | Build info | ADMIN | API | `InternalBuildController` |

### 3.3. Chức năng không có trong source (không được mô tả như đã hoàn thiện)

- Public tracking bằng token
- Customer portal
- Driver Portal đầy đủ
- GPS/ELD ingestion tự động
- Upload chứng từ (multipart)
- Gửi thông báo tự động/push
- Chữ ký số hóa đơn
- Multi-currency nâng cao

---

## 4. PART 3 — CẤU TRÚC CHƯƠNG

Đề xuất đặt thành **Chương 3 — Phân tích và thiết kế hệ thống** (sau Chương 1 giới thiệu, Chương 2 cơ sở lý thuyết/công nghệ). Cấu trúc thỏa yêu cầu: mỗi nhóm nghiệp vụ có đủ 5 bước Use Case → chức năng → đặc tả → Activity → Sequence.

### 4.1. Quy ước chung áp dụng cho toàn chương

Mỗi nhóm nghiệp vụ `3.4.x` có đúng 5 tiểu mục:

1. **3.4.x.1. Biểu đồ Use Case của nhóm** — actor, UC, quan hệ include/extend nếu có.
2. **3.4.x.2. Danh sách chức năng** — bảng: mã UC, tên, actor, API, màn hình, trạng thái, căn cứ.
3. **3.4.x.3. Đặc tả chi tiết từng Use Case** — mẫu: mã, tên, actor chính/phụ, mục tiêu, tiền điều kiện, điều kiện kích hoạt, luồng chính, luồng thay thế, luồng ngoại lệ, hậu điều kiện, quy tắc nghiệp vụ.
4. **3.4.x.4. Biểu đồ hoạt động** — cho UC trọng yếu; UC CRUD cùng mẫu dùng chung một sơ đồ, ghi rõ "áp dụng cho…".
5. **3.4.x.5. Biểu đồ tuần tự** — tương ứng 1-1 với Activity; thể hiện Actor → Boundary (page) → Controller → Service → Repository → DB.

**Quy ước đánh dấu mức độ:**

| Ký hiệu | Ý nghĩa |
|---|---|
| ★ | UC trọng yếu — bắt buộc Activity + Sequence riêng |
| ● | UC CRUD cùng mẫu — dùng sơ đồ chung, chỉ khác entity/endpoint |
| ○ | UC phụ trợ — chỉ bảng chức năng, không cần sơ đồ |

### 4.2. Mục lục chương

#### 3.1. Khảo sát và mô tả bài toán

| Mục | Nội dung | Căn cứ | Hình/Bảng |
|---|---|---|---|
| 3.1.1 | Giới thiệu bài toán: bối cảnh vận tải đường bộ, nhu cầu quản lý đơn–chuyến–xe–tài xế–chi phí–lương trong một hệ thống | `README.md`, `docs/docs/business-spec.md`, `docs/nghiep-vu-logisticsx.vi.md` | — |
| 3.1.2 | Quy trình nghiệp vụ hiện tại: tiếp nhận Load → phân công xe/tài xế → Trip/Stop → chứng từ → định giá → hóa đơn → thanh toán → quyết toán/lương → báo cáo | `Load`, `Trip`, `TripStop`, `Invoice`, `DriverSettlement`; `docs/report-context/03_luong-nghiep-vu.md` | Bảng 3.1; Hình 3.1 |
| 3.1.3 | Hạn chế cách làm hiện tại | **Chỉ trình bày nếu có nguồn từ đơn vị thực tập**; nếu không → `[CẦN BỔ SUNG]` | Bảng 3.2 (nếu có) |
| 3.1.4 | Đề xuất hệ thống LogisticsX | `README.md`, `SecurityConfig`, `docs/ai-dispatch/ARCHITECTURE.md` | Hình 3.2 |
| 3.1.5 | Phạm vi và giới hạn | PHẦN 2 | Bảng 3.2 |

#### 3.2. Phân tích yêu cầu hệ thống

| Mục | Nội dung | Căn cứ | Hình/Bảng |
|---|---|---|---|
| 3.2.1 | Yêu cầu chức năng | PHẦN 2; `docs/report-context/01_yeu-cau-va-tac-nhan.md` (đã kiểm tra lại) | Bảng 3.3 |
| 3.2.2 | Yêu cầu phi chức năng: bảo mật JWT/RBAC, tenant isolation, validation, error envelope, pagination, i18n, container hóa, testability | `SecurityConfig`, `GlobalExceptionHandler`, `PagedResponse`, `pom.xml`, `Dockerfile` | Bảng 3.4 |
| 3.2.3 | Quy tắc nghiệp vụ: trạng thái Load/Trip/Stop, điều kiện gán xe, điều kiện duyệt AI, điều kiện chốt lương, ràng buộc tiền tệ | `LoadService`, `TripExecutionService`, `OptimizationAcceptanceService`, `DriverSettlementService`, migration constraints | Bảng 3.5 |
| 3.2.4 | Phạm vi & giới hạn: đã có / một phần / chưa có | PHẦN 2 | Bảng 3.6 |

> Không tự đặt ngưỡng nghiệp vụ. Chỗ nào chưa có quyết định ghi `[CẦN BỔ SUNG]`.

#### 3.3. Xác định tác nhân và Use Case

| Mục | Nội dung | Căn cứ | Hình/Bảng |
|---|---|---|---|
| 3.3.1 | Danh sách Actor: 6 actor con người + 3 actor hệ thống | `SecurityConfig`, `LarkAuthService`, `LarkEventController`, `PayrollCallbackController` | Bảng 3.7 |
| 3.3.2 | Mô tả Actor: vai trò, mục tiêu, module được truy cập | như trên | Bảng 3.8 |
| 3.3.3 | Danh sách Use Case + mức ưu tiên | PHẦN 2 | Bảng 3.9 |
| 3.3.4 | Phân nhóm Use Case (16 nhóm) | PHẦN 2 | Bảng 3.10 |
| 3.3.5 | Biểu đồ Use Case tổng quát: actor ↔ nhóm UC (không vẽ từng UC con) | Bảng 3.7–3.10 | Hình 3.3 |

**Ghi chú bắt buộc trong 3.3.2:**
- `SUPERADMIN` / `OWNER` / `MANAGER` chỉ có trong ma trận frontend, **không phải role backend**.
- `AUDITOR` chỉ có trong test AI, **không có trong production**.
- Backend là nguồn quyết định phân quyền cuối cùng.

#### 3.4. Phân tích chi tiết theo nhóm nghiệp vụ

##### 3.4.1. Nhóm Xác thực và phiên

- **3.4.1.1** UC nhóm: AUTH-01 ★, AUTH-02 ★, AUTH-03 ○ → **Hình 3.4**
- **3.4.1.2** Bảng 3.11
- **3.4.1.3** Đặc tả AUTH-01, AUTH-02
- **3.4.1.4** **Hình 3.5** Activity đăng nhập Lark
- **3.4.1.5** **Hình 3.6** Sequence đăng nhập Lark (LoginPage → LarkAuthController → LarkAuthService → Lark Open API → EmployeeRepository → JWT → /api/me)
- Căn cứ: `LarkAuthController.java`, `LarkAuthService.java`, `LarkAuthenticationFilter.java`, `CurrentUserController.java`; frontend `LoginPage`, `LarkCallbackPage`, `SelectTenantPage`.

##### 3.4.2. Nhóm Quản trị và phân quyền

- **3.4.2.1** UC nhóm: ADM-01 ●, ADM-02 ○ → **Hình 3.7**
- **3.4.2.2** Bảng 3.12
- **3.4.2.3** Đặc tả ADM-01
- **3.4.2.4** **Hình 3.8** Activity CRUD vai trò
- **3.4.2.5** **Hình 3.9** Sequence CRUD vai trò
- Căn cứ: `RoleController.java`, `TenantRole.java`, `TenantRoleClaim.java`, `SecurityConfig`.

##### 3.4.3. Nhóm Danh mục và đội xe

- **3.4.3.1** UC nhóm: MDM-01…MDM-06 → **Hình 3.10**
- **3.4.3.2** Bảng 3.13
- **3.4.3.3** Đặc tả MDM-01 (đại diện CRUD); MDM-06 đặc tả riêng nếu UI được bật
- **3.4.3.4** **Hình 3.11** Activity CRUD chung (áp dụng MDM-01…05)
- **3.4.3.5** **Hình 3.12** Sequence CRUD chung
- Căn cứ: `CustomerController`, `EmployeeController`, `DriverController`, `TruckController`, `TerminalController`, `InspectionController`; `resourceRegistry.ts`.

##### 3.4.4. Nhóm Hồ sơ tài xế

- **3.4.4.1** UC nhóm: DRV-01…DRV-07 → **Hình 3.13**
- **3.4.4.2** Bảng 3.14
- **3.4.4.3** Đặc tả DRV-01, DRV-02, DRV-06
- **3.4.4.4** **Hình 3.14** Activity cập nhật hồ sơ; **Hình 3.15** Activity CRUD giấy tờ (mẫu chung cho DRV-02/03/04)
- **3.4.4.5** **Hình 3.16** Sequence cập nhật hồ sơ; **Hình 3.17** Sequence CRUD giấy tờ
- Căn cứ: các `Driver*Controller`, `DriverProfile.java`, `DriverLicense.java`…; `DriverProfilePage.tsx`, `driverProfileRoutes.ts`, `driver-profile.api.ts`; migration V45–V50.

##### 3.4.5. Nhóm Quản lý Load

- **3.4.5.1** UC nhóm: LOAD-01…LOAD-09 → **Hình 3.18**
- **3.4.5.2** Bảng 3.15
- **3.4.5.3** Đặc tả LOAD-01…LOAD-08
- **3.4.5.4** **Hình 3.19** Activity tạo Load; **Hình 3.20** Activity cập nhật; **Hình 3.21** Activity timeline; **Hình 3.22** Activity xóa
- **3.4.5.5** **Hình 3.23** Sequence tạo; **Hình 3.24** Sequence cập nhật; **Hình 3.25** Sequence timeline; **Hình 3.26** Sequence xóa
- Căn cứ: `LoadController`, `LoadService`, `LoadRepository`, `LoadTimelineController`, `LoadTimelineService`, `LoadPickupBusinessDateController`, `DocumentController`; frontend `Load*` pages, `TmsDispatchPage`.
- **Lưu ý:** LOAD-04/05/07 phải ghi rõ trạng thái UI (sửa/xóa có thể bị lọc bởi capability; sửa ngày bị cờ tắt).

##### 3.4.6. Nhóm Chuyến đi và thực thi

- **3.4.6.1** UC nhóm: TRIP-01…TRIP-04 → **Hình 3.27**
- **3.4.6.2** Bảng 3.16
- **3.4.6.3** Đặc tả TRIP-02, TRIP-04
- **3.4.6.4** **Hình 3.28** Activity gán tài xế; **Hình 3.29** Activity thực thi stop
- **3.4.6.5** **Hình 3.30** Sequence gán tài xế; **Hình 3.31** Sequence thực thi stop
- Căn cứ: `TripController`, `TripExecutionController`, `TripExecutionService`, `TripStop`; migration V2, V4, V8.

##### 3.4.7. Nhóm Điều phối AI và tối ưu

- **3.4.7.1** UC nhóm: AID-01…AID-07 → **Hình 3.32**
- **3.4.7.2** Bảng 3.17
- **3.4.7.3** Đặc tả AID-01, AID-02, AID-04, AID-06
- **3.4.7.4** **Hình 3.33** Activity chạy AI dispatch; **Hình 3.34** Activity duyệt/từ chối; **Hình 3.35** Activity optimization accept
- **3.4.7.5** **Hình 3.36** Sequence chạy AI; **Hình 3.37** Sequence duyệt; **Hình 3.38** Sequence optimization accept
- Căn cứ: `AiDispatchController`, `AiDispatchService`, `AiGapController`, `OptimizationRunController`, `OptimizationApplicationService`, `OptimizationAcceptanceService`; `docs/ai-dispatch/`.
- **Bắt buộc nêu:** Autonomous mode bị khóa; approval mặc định tắt; chỉ `OptimizationAcceptanceService.accept` ghi assignment; AI chỉ giải thích, không xếp hạng lại.

##### 3.4.8. Nhóm Định giá

- **3.4.8.1** UC nhóm: RATE-01…RATE-06 → **Hình 3.39**
- **3.4.8.2** Bảng 3.18
- **3.4.8.3** Đặc tả RATE-01, RATE-02
- **3.4.8.4** **Hình 3.40** Activity preview/accept
- **3.4.8.5** **Hình 3.41** Sequence preview/accept
- Căn cứ: `RatingController`, `RatePolicyController`, `RatingMileageController`; migration V21, V24; `docs/rating-engine-v1.md`.

##### 3.4.9. Nhóm Hóa đơn và thanh toán

- **3.4.9.1** UC nhóm: BILL-01…BILL-06 → **Hình 3.42**
- **3.4.9.2** Bảng 3.19
- **3.4.9.3** Đặc tả BILL-02, BILL-05
- **3.4.9.4** **Hình 3.43** Activity billing command
- **3.4.9.5** **Hình 3.44** Sequence billing command
- Căn cứ: `InvoiceController`, `BillingController`, `PaymentController`, `TaxAssessmentController`; `docs/payment-command-contracts.md`, `docs/invoice-rating-v1-contract.md`.

##### 3.4.10. Nhóm Chi phí và phụ phí

- **3.4.10.1** UC nhóm: COST-01…COST-08 → **Hình 3.45**
- **3.4.10.2** Bảng 3.20
- **3.4.10.3** Đặc tả COST-04, COST-05
- **3.4.10.4** **Hình 3.46** Activity duyệt chi phí
- **3.4.10.5** **Hình 3.47** Sequence duyệt chi phí
- Căn cứ: `ShipmentCostController`, `AccessorialController`, `AccessorialChargeController`, `ExpenseApprovalController`; `docs/cost-ledger-contracts.md`, `docs/accessorial-contracts.md`.

##### 3.4.11. Nhóm Lương và quyết toán

- **3.4.11.1** UC nhóm: PAY-01…PAY-11 → **Hình 3.48**
- **3.4.11.2** Bảng 3.21
- **3.4.11.3** Đặc tả PAY-02, PAY-03, PAY-07, PAY-08
- **3.4.11.4** **Hình 3.49** Activity settlement; **Hình 3.50** Activity payroll run
- **3.4.11.5** **Hình 3.51** Sequence settlement; **Hình 3.52** Sequence payroll
- Căn cứ: `DriverSettlementController`, `PayrollRunController`, `PayslipController`, `PayrollPaymentController`, `PayrollCallbackController`; `docs/payroll-contracts.md`, `docs/settlement-contracts.md`; migration V10, V11, V18, V29.

##### 3.4.12. Nhóm Lịch sử đội xe

- **3.4.12.1** UC nhóm: FLEET-01…FLEET-03 → **Hình 3.53**
- **3.4.12.2** Bảng 3.22
- **3.4.12.3** Đặc tả FLEET-01
- **3.4.12.4** **Hình 3.54** Activity ghi nhận fleet event
- **3.4.12.5** **Hình 3.55** Sequence ghi nhận fleet event
- Căn cứ: `FleetHistoryController`; migration V34; `docs/fleet-history-v1-contract.md`.

##### 3.4.13. Nhóm Báo cáo

- **3.4.13.1** UC nhóm: RPT-01…RPT-07 → **Hình 3.56**
- **3.4.13.2** Bảng 3.23
- **3.4.13.3** Đặc tả RPT-01, RPT-04
- **3.4.13.4** **Hình 3.57** Activity truy vấn báo cáo
- **3.4.13.5** **Hình 3.58** Sequence truy vấn báo cáo
- Căn cứ: `ReportController`; `ProfitabilityPage`, `FleetReportPage`, `TmsOperationsDashboardPage`; `docs/reporting-contracts.md`, `docs/profitability-contracts.md`.

##### 3.4.14. Nhóm Thông báo và tài liệu

- **3.4.14.1** UC nhóm: NOTI-01…03, DOC-01…02 → **Hình 3.59**
- **3.4.14.2** Bảng 3.24
- **3.4.14.3** Đặc tả NOTI-01, DOC-01
- **3.4.14.4** **Hình 3.60** Activity đọc thông báo
- **3.4.14.5** **Hình 3.61** Sequence đọc thông báo
- Căn cứ: `NotificationController`, `NotificationService`, `DocumentController`, `DocumentService`.

##### 3.4.15. Nhóm Tích hợp Lark

- **3.4.15.1** UC nhóm: LARK-01…03 → **Hình 3.62**
- **3.4.15.2** Bảng 3.25
- **3.4.15.3** Đặc tả LARK-01, LARK-03
- **3.4.15.4** **Hình 3.63** Activity nhận webhook
- **3.4.15.5** **Hình 3.64** Sequence nhận webhook
- Căn cứ: `LarkBaseController`, `LarkSyncController`, `LarkSyncService`, `LarkEventController`, `LarkEventSecurity`, `LarkEventDispatcher`; `docs/lark-employee-login.md`, `docs/lark-base-schema-proposal.md`.

##### 3.4.16. Nhóm Tiện ích hệ thống

- **3.4.16.1** Không cần UC diagram (SYS-01/02 là hạ tầng)
- **3.4.16.2** Bảng 3.26
- **3.4.16.3** Không đặc tả UC; mô tả endpoint trong 3.9
- **3.4.16.4** Không cần Activity
- **3.4.16.5** Không cần Sequence
- Căn cứ: `HealthController`, `InternalBuildController`.

#### 3.5. Thiết kế kiến trúc hệ thống

| Mục | Nội dung | Hình/Bảng |
|---|---|---|
| 3.5.1 | Kiến trúc tổng thể: Browser → SPA → API → Security → Controller/Service/Repository → PostgreSQL; nhánh tích hợp Lark/HOS/Optimization/LLM | **Hình 3.65** |
| 3.5.2 | Các thành phần phần mềm | Bảng 3.27 |
| 3.5.3 | Luồng dữ liệu: request/response envelope, JWT, tenant routing | **Hình 3.66** |
| 3.5.4 | Quan hệ thành phần | Bảng 3.28 |

Căn cứ: `pom.xml`, `SecurityConfig`, `docker-compose.yml`, `Dockerfile`, `ApiResponse`, `TenantRoutingDataSource`, frontend `App.tsx`, `dataProvider.ts`.

#### 3.6. Thiết kế lớp

| Mục | Nội dung | Hình/Bảng |
|---|---|---|
| 3.6.1 | Danh sách lớp (entity, service, repository, controller) | Bảng 3.29 |
| 3.6.2 | Biểu đồ lớp lõi: Customer–Load–Trip–TripStop–TripDriverAssignment–Truck–Employee–TenantRole | **Hình 3.67** |
| 3.6.3 | Quan hệ và bội số | Bảng 3.30 |
| 3.6.4 | Kế thừa: chỉ ghi `BaseAuditableEntity` nếu các entity thực sự kế thừa | — |

Căn cứ: `entity/*.java`.

#### 3.7. Thiết kế cơ sở dữ liệu

| Mục | Nội dung | Hình/Bảng |
|---|---|---|
| 3.7.1 | ERD tổng thể | **Hình 3.68** |
| 3.7.2 | ERD cụm Load/Trip | **Hình 3.69** |
| 3.7.3 | ERD cụm Tài chính | **Hình 3.70** |
| 3.7.4 | ERD cụm Lương | **Hình 3.71** |
| 3.7.5 | ERD cụm Đội xe | **Hình 3.72** |
| 3.7.6 | ERD cụm AI/Lark | **Hình 3.73** |
| 3.7.7 | Từ điển dữ liệu (25–30 bảng trọng yếu) | Bảng 3.31 |
| 3.7.8 | Quy ước dữ liệu | Bảng 3.32 |

Căn cứ: `db/migration/tenant/V1–V53`; entity JPA.

#### 3.8. Thiết kế trạng thái

Chỉ lập cho đối tượng có vòng đời thực tế:

| Mục | Đối tượng | Hình | Căn cứ |
|---|---|---|---|
| 3.8.1 | TripStop | **Hình 3.74** | `TripExecutionService`, `STATE_TripStop` |
| 3.8.2 | DriverSettlement | **Hình 3.75** | `DriverSettlementService` |
| 3.8.3 | PayrollRun | **Hình 3.76** | `PayrollRunController`, `PayrollRunService` |
| 3.8.4 | AI Dispatch Decision | **Hình 3.77** | `AiDispatchService`, `AiDispatchDecision` |
| 3.8.5 | Load | Chỉ lập nếu chứng minh được state machine; nếu không ghi "status là chuỗi, chưa đủ căn cứ" | — |

> Giá trị trạng thái phải đọc đúng hằng số/enum trong mã; không tự đặt tên.

#### 3.9. Thiết kế API

| Mục | Nội dung | Bảng |
|---|---|---|
| 3.9.1 | Nguyên tắc REST: base path `/api`, JSON, `ApiResponse` envelope, HTTP status, pagination, idempotency key cho command | Bảng 3.33 |
| 3.9.2 | Xác thực & phân quyền: public endpoint, role matcher, fallback authenticated | Bảng 3.34 |
| 3.9.3 | Danh mục endpoint theo nhóm | Bảng 3.35–3.50 |
| 3.9.4 | Mã lỗi nghiệp vụ | Bảng 3.51 |
| 3.9.5 | Ví dụ request/response (5–8 ví dụ tiêu biểu) | Bảng 3.52 |

Căn cứ: 280 handler annotation; `ApiResponse`, `GlobalExceptionHandler`, `PaginationValidator`, `SecurityConfig`. **Phải sinh lại bảng endpoint từ source**, không dùng bảng 200 handler cũ.

#### 3.10. Thiết kế giao diện

| Mục | Nội dung | Hình |
|---|---|---|
| 3.10.1 | Sơ đồ điều hướng | **Hình 3.78** |
| 3.10.2 | Giao diện đăng nhập | **Hình 3.79** |
| 3.10.3 | Dashboard vận hành TMS | **Hình 3.80** |
| 3.10.4 | Bảng điều phối | **Hình 3.81** |
| 3.10.5 | Chi tiết shipment/Load | **Hình 3.82** |
| 3.10.6 | Danh mục & hồ sơ tài xế | **Hình 3.83–3.84** |
| 3.10.7 | Tài chính & lương | **Hình 3.85–3.86** |
| 3.10.8 | Báo cáo | **Hình 3.87** |
| 3.10.9 | Form nhập liệu tiêu biểu | **Hình 3.88** |

Căn cứ: `AppRouter.tsx`, `routes.ts`, `resourceRegistry.ts`, `roleMatrix.ts`.

> Ảnh phải chụp từ môi trường test với dữ liệu fixture hoặc dữ liệu đã ẩn danh; ghi rõ "dữ liệu mẫu" nếu dùng preview.

#### 3.11. Tổng kết chương

- Tóm tắt kết quả phân tích/thiết kế.
- Bảng truy vết UC → Activity → Sequence → API → bảng dữ liệu.
- Nêu giới hạn: chưa nghiệm thu runtime, chưa có môi trường production, các điểm `[CẦN BỔ SUNG]`.

---

## 5. PART 4 — DANH MỤC HÌNH VÀ BẢNG

### 5.1. Bảng

| Mã | Tên bảng | Mục |
|---|---|---|
| Bảng 3.1 | Quy trình nghiệp vụ tổng quát | 3.1.2 |
| Bảng 3.2 | Phạm vi và giới hạn hệ thống | 3.1.5 |
| Bảng 3.3 | Yêu cầu chức năng | 3.2.1 |
| Bảng 3.4 | Yêu cầu phi chức năng | 3.2.2 |
| Bảng 3.5 | Quy tắc nghiệp vụ | 3.2.3 |
| Bảng 3.6 | Ma trận trạng thái chức năng | 3.2.4 |
| Bảng 3.7 | Danh sách tác nhân | 3.3.1 |
| Bảng 3.8 | Mô tả tác nhân và quyền | 3.3.2 |
| Bảng 3.9 | Danh mục Use Case | 3.3.3 |
| Bảng 3.10 | Phân nhóm Use Case | 3.3.4 |
| Bảng 3.11 | Chức năng nhóm Xác thực | 3.4.1.2 |
| Bảng 3.12 | Chức năng nhóm Quản trị | 3.4.2.2 |
| Bảng 3.13 | Chức năng nhóm Danh mục | 3.4.3.2 |
| Bảng 3.14 | Chức năng nhóm Hồ sơ tài xế | 3.4.4.2 |
| Bảng 3.15 | Chức năng nhóm Load | 3.4.5.2 |
| Bảng 3.16 | Chức năng nhóm Trip | 3.4.6.2 |
| Bảng 3.17 | Chức năng nhóm AI/Tối ưu | 3.4.7.2 |
| Bảng 3.18 | Chức năng nhóm Định giá | 3.4.8.2 |
| Bảng 3.19 | Chức năng nhóm Hóa đơn | 3.4.9.2 |
| Bảng 3.20 | Chức năng nhóm Chi phí | 3.4.10.2 |
| Bảng 3.21 | Chức năng nhóm Lương | 3.4.11.2 |
| Bảng 3.22 | Chức năng nhóm Đội xe | 3.4.12.2 |
| Bảng 3.23 | Chức năng nhóm Báo cáo | 3.4.13.2 |
| Bảng 3.24 | Chức năng nhóm Thông báo/Tài liệu | 3.4.14.2 |
| Bảng 3.25 | Chức năng nhóm Lark | 3.4.15.2 |
| Bảng 3.26 | Chức năng tiện ích | 3.4.16.2 |
| Bảng 3.27 | Thành phần phần mềm | 3.5.2 |
| Bảng 3.28 | Quan hệ giữa thành phần | 3.5.4 |
| Bảng 3.29 | Danh sách lớp | 3.6.1 |
| Bảng 3.30 | Quan hệ và bội số lớp | 3.6.3 |
| Bảng 3.31 | Từ điển dữ liệu | 3.7.7 |
| Bảng 3.32 | Quy ước dữ liệu | 3.7.8 |
| Bảng 3.33 | Nguyên tắc REST API | 3.9.1 |
| Bảng 3.34 | Ma trận phân quyền endpoint | 3.9.2 |
| Bảng 3.35–3.50 | Danh mục endpoint theo nhóm | 3.9.3 |
| Bảng 3.51 | Mã lỗi | 3.9.4 |
| Bảng 3.52 | Ví dụ request/response | 3.9.5 |
| Bảng 3.53 | Truy vết UC → sơ đồ → API → bảng | 3.11 |

### 5.2. Hình — nhóm bắt buộc (★)

| Mã | Tên hình | Mục |
|---|---|---|
| Hình 3.1 | Quy trình nghiệp vụ tổng quát | 3.1.2 |
| Hình 3.2 | Kiến trúc tổng thể | 3.1.4 |
| Hình 3.3 | Use Case tổng quát | 3.3.5 |
| Hình 3.4 | Use Case nhóm Xác thực | 3.4.1.1 |
| Hình 3.5 | Activity đăng nhập Lark | 3.4.1.4 |
| Hình 3.6 | Sequence đăng nhập Lark | 3.4.1.5 |
| Hình 3.10 | Use Case nhóm Danh mục | 3.4.3.1 |
| Hình 3.11 | Activity CRUD chung | 3.4.3.4 |
| Hình 3.12 | Sequence CRUD chung | 3.4.3.5 |
| Hình 3.13 | Use Case nhóm Hồ sơ tài xế | 3.4.4.1 |
| Hình 3.14 | Activity cập nhật hồ sơ | 3.4.4.4 |
| Hình 3.16 | Sequence cập nhật hồ sơ | 3.4.4.5 |
| Hình 3.18 | Use Case nhóm Load | 3.4.5.1 |
| Hình 3.19 | Activity tạo Load | 3.4.5.4 |
| Hình 3.20 | Activity cập nhật Load | 3.4.5.4 |
| Hình 3.21 | Activity timeline | 3.4.5.4 |
| Hình 3.23 | Sequence tạo Load | 3.4.5.5 |
| Hình 3.24 | Sequence cập nhật Load | 3.4.5.5 |
| Hình 3.25 | Sequence timeline | 3.4.5.5 |
| Hình 3.27 | Use Case nhóm Trip | 3.4.6.1 |
| Hình 3.29 | Activity thực thi stop | 3.4.6.4 |
| Hình 3.31 | Sequence thực thi stop | 3.4.6.5 |
| Hình 3.32 | Use Case nhóm AI/Tối ưu | 3.4.7.1 |
| Hình 3.33 | Activity chạy AI dispatch | 3.4.7.4 |
| Hình 3.34 | Activity duyệt/từ chối | 3.4.7.4 |
| Hình 3.35 | Activity optimization accept | 3.4.7.4 |
| Hình 3.36 | Sequence chạy AI | 3.4.7.5 |
| Hình 3.37 | Sequence duyệt | 3.4.7.5 |
| Hình 3.38 | Sequence optimization accept | 3.4.7.5 |
| Hình 3.39 | Use Case nhóm Định giá | 3.4.8.1 |
| Hình 3.40 | Activity preview/accept | 3.4.8.4 |
| Hình 3.41 | Sequence preview/accept | 3.4.8.5 |
| Hình 3.42 | Use Case nhóm Hóa đơn | 3.4.9.1 |
| Hình 3.43 | Activity billing command | 3.4.9.4 |
| Hình 3.44 | Sequence billing command | 3.4.9.5 |
| Hình 3.45 | Use Case nhóm Chi phí | 3.4.10.1 |
| Hình 3.46 | Activity duyệt chi phí | 3.4.10.4 |
| Hình 3.47 | Sequence duyệt chi phí | 3.4.10.5 |
| Hình 3.48 | Use Case nhóm Lương | 3.4.11.1 |
| Hình 3.49 | Activity settlement | 3.4.11.4 |
| Hình 3.50 | Activity payroll run | 3.4.11.4 |
| Hình 3.51 | Sequence settlement | 3.4.11.5 |
| Hình 3.52 | Sequence payroll | 3.4.11.5 |
| Hình 3.56 | Use Case nhóm Báo cáo | 3.4.13.1 |
| Hình 3.58 | Sequence truy vấn báo cáo | 3.4.13.5 |
| Hình 3.62 | Use Case nhóm Lark | 3.4.15.1 |
| Hình 3.63 | Activity nhận webhook | 3.4.15.4 |
| Hình 3.64 | Sequence nhận webhook | 3.4.15.5 |
| Hình 3.65 | Component diagram | 3.5.1 |
| Hình 3.66 | Deployment diagram local | 3.5.3 |
| Hình 3.67 | Class diagram lõi | 3.6.2 |
| Hình 3.68 | ERD tổng thể | 3.7.1 |
| Hình 3.69 | ERD Load/Trip | 3.7.2 |
| Hình 3.70 | ERD Tài chính | 3.7.3 |
| Hình 3.71 | ERD Lương | 3.7.4 |
| Hình 3.74 | State TripStop | 3.8.1 |
| Hình 3.75 | State Settlement | 3.8.2 |
| Hình 3.76 | State PayrollRun | 3.8.3 |
| Hình 3.77 | State AI Decision | 3.8.4 |
| Hình 3.78 | Sơ đồ điều hướng | 3.10.1 |
| Hình 3.79 | Giao diện đăng nhập | 3.10.2 |
| Hình 3.80 | Dashboard TMS | 3.10.3 |
| Hình 3.81 | Bảng điều phối | 3.10.4 |
| Hình 3.82 | Chi tiết shipment/Load | 3.10.5 |
| Hình 3.88 | Form tạo Load + validation | 3.10.9 |

### 5.3. Hình — nhóm bổ sung (●/○)

Hình 3.7–3.9 (quản trị), 3.15/3.17 (giấy tờ tài xế), 3.22/3.26 (xóa Load), 3.28/3.30 (gán tài xế), 3.53–3.55 (đội xe), 3.57 (activity báo cáo), 3.59–3.61 (thông báo/tài liệu), 3.72–3.73 (ERD đội xe/AI-Lark), 3.83–3.87 (các màn hình còn lại).

> Tổng cộng khoảng 88 hình và 53 bảng. Nếu báo cáo yêu cầu gọn hơn, cắt nhóm "Bổ sung" trước; **không cắt** các hình ★ ở trên.

---

## 6. PART 5 — BẢNG ĐỐI CHIẾU

| Nội dung báo cáo | File dự án làm căn cứ | Trạng thái thông tin | Ghi chú |
|---|---|---|---|
| Loại hệ thống, công nghệ backend | `pom.xml`, `src/main/java` | Đã kiểm tra | Spring Boot 4.1.1, Java 21 |
| Công nghệ frontend | `logictics-app/package.json` | Đã kiểm tra | React 18, TS 6, Refine 4, AntD 5 |
| Kiến trúc phân lớp | `controller/`, `service/`, `repository/`, `entity/` | Đã kiểm tra | Monolith REST |
| Xác thực Lark → JWT | `LarkAuthController`, `LarkAuthService`, `LarkAuthenticationFilter` | Đã kiểm tra mã | Chưa chạy với tài khoản Lark thật |
| Phân quyền | `SecurityConfig.java` | Đã kiểm tra mã | 6 role backend; audit lại từng matcher |
| 6 tác nhân con người | `SecurityConfig`, `LarkAuthService` | Đã kiểm tra | Không có AUDITOR trong production |
| Actor hệ thống ngoài | `LarkEventController`, `PayrollCallbackController`, `HosFeasibilityService`, `TrustedOptimizationHttpAdapter` | Đã kiểm tra mã | Chưa xác minh provider thật |
| Danh mục UC | 66 controller + frontend routes | Đã kiểm tra một phần | PHẦN 2 là danh mục đề xuất, cần duyệt lại từng UC |
| Luồng Load | `LoadController`, `LoadService`, `LoadTimelineController` | Đã kiểm tra mã | Có hồ sơ UC001–UC008 cũ |
| Luồng Trip/Stop | `TripExecutionController`, `TripExecutionService` | Đã kiểm tra mã | State TripStop đã có sơ đồ cũ |
| Luồng AI dispatch | `AiDispatchController`, `AiDispatchService` | Đã kiểm tra mã | Bản main chưa đầy đủ; bản worktree có P0.5–P4 |
| Luồng optimization | `OptimizationApplicationService`, `OptimizationAcceptanceService` | Đã kiểm tra mã | Chưa xác minh provider route/HOS thật |
| Luồng định giá | `RatingController`, `RatePolicyController` | Đã kiểm tra mã | Có contract V21/V24 |
| Luồng hóa đơn/thanh toán | `InvoiceController`, `BillingController`, `PaymentController` | Đã kiểm tra mã | Một số command phụ thuộc contract |
| Luồng lương | `DriverSettlementController`, `PayrollRunController`, `PayslipController` | Đã kiểm tra mã | Chưa chứng minh đã trả lương thật |
| Báo cáo | `ReportController` | Đã kiểm tra mã | Có endpoint mới (driver ranking, CPM) |
| Tích hợp Lark Base | `LarkBaseController`, `LarkSyncController`, `LarkEventController` | Đã kiểm tra mã | Chưa chạy với tenant Lark thật |
| Hồ sơ tài xế | các `Driver*Controller`, `driver-profile.api.ts` | Đã kiểm tra mã | Module mới, chưa có trong hồ sơ cũ |
| CSDL | `db/migration/tenant/V1–V53` | Đã kiểm tra tên bảng/cột chính | Chưa introspect DB đang chạy |
| ERD | Entity JPA + DDL migration | Đã kiểm tra một phần | Không suy đoán FK ngoài DDL |
| API inventory | 280 handler annotation | Đã đếm tĩnh | Chưa introspection runtime |
| Mã lỗi | `GlobalExceptionHandler`, service `ApiException` | Đã kiểm tra một phần | Cần lập bảng đầy đủ |
| Giao diện | `AppRouter.tsx`, `routes.ts`, `resourceRegistry.ts` | Đã kiểm tra mã | Luồng TMS mới; ảnh chưa chụp |
| Quy trình nghiệp vụ | `docs/report-context/03_luong-nghiep-vu.md` | Tài liệu cũ | Cần cập nhật theo TMS flow |
| Tài liệu kiến trúc | `docs/docs/architecture/overview.md` | **Mâu thuẫn** | Ghi .NET/Angular; không dùng |
| Số bảng CSDL | Migration V1–V53 | Đã đếm tĩnh | Không khẳng định số bảng DB thật |
| Số endpoint | 280 annotation | Đã đếm tĩnh | 66 controller |

---

## 7. PART 6 — THÔNG TIN CÒN THIẾU

1. **`quanly.docx` không tìm thấy** trong cả hai repository. Không thể đối chiếu yêu cầu gốc của giảng viên/đơn vị. `[CẦN BỔ SUNG]`

2. **Chọn phiên bản AI dispatch để mô tả:** main workspace (một phần, chưa có V54/V55, chưa có Anthropic client) hay branch `ai-dispatch/complete` (P0.5–P4, có approval gate, real provider, 150 test AI). Báo cáo phải nhất quán một phiên bản.

3. **Môi trường thực tế:** chưa có bằng chứng chạy production/staging; chưa có URL, topology, TLS, secrets manager. Không được mô tả "đã triển khai".

4. **Xác thực Lark thật:** chưa có tài khoản/tenant Lark để kiểm chứng end-to-end. Chỉ có mã và test.

5. **Provider HOS/ELD, routing, forecast cost, giá LLM, quota:** đều là quyết định nghiệp vụ chưa có (BLOCKERS B1–B7). Không được bịa ngưỡng/giá.

6. **Quy trình nghiệp vụ hiện tại của đơn vị:** chưa có mô tả từ đơn vị thực tập; mục 3.1.2–3.1.3 cần bổ sung.

7. **Ảnh giao diện:** chưa có bộ ảnh chụp chuẩn cho báo cáo; cần chụp từ môi trường test với dữ liệu fixture.

8. **Trạng thái runtime từng chức năng:** danh mục PHẦN 2 là phân tích tĩnh; chưa có UAT/kiểm thử thủ công từng UC.

9. **Role `SUPERADMIN/OWNER/MANAGER`:** có trong ma trận frontend nhưng không có trong `SecurityConfig`; cần quyết định có đưa vào báo cáo như "legacy/không dùng" hay loại bỏ.

10. **`AUDITOR`:** chỉ có trong test AI, không có trong production; không đưa vào sơ đồ actor chính thức.

11. **Customer portal / public tracking / GPS live / upload POD-BOL:** không có source; nếu báo cáo cần, phải ghi "định hướng phát triển", không phải chức năng đã có.

12. **Số liệu bảng/endpoint/test:** nên ghi kèm phương pháp đếm (annotation source, migration source) và ngày khảo sát; tránh tuyên bố "production có X bảng/Y endpoint".

13. **Mâu thuẫn tài liệu:** README ghi Spring Boot 4.1.0 vs `pom.xml` 4.1.1; README ghi Flyway disabled vs `application.yml` enabled; `docs/docs/architecture` ghi .NET/Angular. Cần chọn source-of-truth và ghi chú trong báo cáo.

14. **Phân quyền frontend vs backend:** frontend `roleMatrix.ts` fail-closed nhưng có role không tồn tại ở backend; cần nêu rõ backend là nguồn quyết định cuối cùng.

15. **API inventory đầy đủ:** cần sinh lại bảng 280 handler từ source hiện tại (không dùng bảng 200 handler cũ trong `docs/report-context/05_api.md`).

---

## PHỤ LỤC A — Ma trận phân quyền backend

Nguồn: `SecurityConfig.java`. Nguyên tắc: matcher cụ thể đứng trước, fallback `authenticated()`.

### A.1. Public (không cần JWT)

| Endpoint | Ghi chú |
|---|---|
| `GET /actuator/health`, `/health`, `/api/health` | Health check |
| `/v3/api-docs/**`, `/swagger-ui/**` | OpenAPI |
| `GET /api/auth/lark/authorize`, `GET /api/auth/lark/login` | Bắt đầu OAuth |
| `POST /api/auth/lark/callback` | Đổi code lấy JWT |
| `/api/payroll/provider-callbacks/{provider}` | Callback provider lương |
| `POST /api/lark/events` | Webhook Lark |

### A.2. Theo nhóm endpoint

| Nhóm endpoint | Role được phép |
|---|---|
| `/api/lark/base/**` | ADMIN, DISPATCHER |
| `/api/roles/**`, `/api/internal/**` | ADMIN |
| `POST /api/fleet/policies/**`, `POST /api/optimization/policies/**`, `POST /api/optimization/qualified-inputs` | ADMIN |
| `/api/fleet/**` | ADMIN, ACCOUNTANT, DISPATCHER, PAYROLL, PAYROLL_MANAGER |
| `POST` fleet status-events / mileage-attributions | ADMIN, DISPATCHER |
| `/api/optimization/**` | ADMIN, DISPATCHER |
| `GET` optimization qualified-inputs | ADMIN, ACCOUNTANT, DISPATCHER |
| `POST` optimization forecast | ADMIN, ACCOUNTANT |
| `GET /api/ai/**` | ADMIN, DISPATCHER, ACCOUNTANT |
| Các method khác `/api/ai/**` | ADMIN, DISPATCHER |
| Invoices / payments / rating | ADMIN, ACCOUNTANT |
| Reports / customer balance | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER |
| Payroll / settlements / pay-periods / driver-pay-policies / load costs | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER |
| Employees / drivers / trips / trip-stops / inspections | ADMIN, DISPATCHER |
| Customers / trucks / loads / documents / terminals | ADMIN, ACCOUNTANT, DISPATCHER |
| `/api/driver/me/*` | DRIVER |
| Payslips | authenticated |
| Notifications | authenticated |
| Fallback | authenticated |

---

## PHỤ LỤC B — Danh sách tác nhân

### B.1. Tác nhân con người (backend)

| Actor | Vai trò nghiệp vụ | Module chính | Căn cứ |
|---|---|---|---|
| ADMIN | Quản trị hệ thống, cấu hình, phân quyền | Toàn bộ, đặc quyền policy/role | `SecurityConfig`, `RoleController` |
| DISPATCHER | Điều phối vận tải | Load, Trip, AI dispatch, optimization, fleet | `SecurityConfig`, `TmsDispatchPage` |
| ACCOUNTANT | Kế toán | Hóa đơn, thanh toán, định giá, chi phí, báo cáo tài chính | `SecurityConfig`, `InvoiceController` |
| PAYROLL | Nhân sự lương | Quyết toán, payroll, payslip, hồ sơ tài xế | `SecurityConfig`, `PayrollRunController` |
| PAYROLL_MANAGER | Quản lý lương | Phê duyệt/chốt lương | `SecurityConfig` |
| DRIVER | Tài xế | Hồ sơ cá nhân, payslip | `/api/driver/me/*`, `PayslipController.mine` |

Mỗi `Employee` có đúng một `TenantRole`; JWT chứa `List.of(roleName)` (`LarkAuthService`).

### B.2. Tác nhân hệ thống

| Actor | Vai trò | Căn cứ |
|---|---|---|
| Lark Platform | Gửi webhook sự kiện, cung cấp Base, xác thực OAuth | `LarkEventController`, `LarkBaseController` |
| Payroll Provider | Gửi callback trạng thái thanh toán lương | `PayrollCallbackController` |
| HOS/ELD Provider | Cung cấp dữ liệu giờ lái (adapter, chưa xác minh thật) | `HosFeasibilityService` |
| Optimization Provider | Cung cấp candidate/route (adapter, chưa xác minh thật) | `TrustedOptimizationHttpAdapter` |
| Anthropic API | Sinh giải thích đề xuất (chỉ khi cấu hình) | `AnthropicLlmClient` (branch AI) |

### B.3. Actor không hợp lệ

- `SUPERADMIN`, `OWNER`, `MANAGER`: chỉ có trong `roleMatrix.ts` frontend, không có authority backend.
- `AUDITOR`: chỉ có trong test AI.

---

## PHỤ LỤC C — Mô-đun và package backend

```text
com.company.logicstic
├── ai
│   ├── dispatch        # AiDispatchController/Service, decision, session
│   ├── gap             # AiGapController/Service
│   ├── optimization    # OptimizationRunController, ApplicationService, AcceptanceService
│   └── llm             # LlmClient, AnthropicLlmClient, validator
├── auth                # Lark OAuth, JWT
├── common              # ApiResponse, exception, pagination, tenant context
├── config              # SecurityConfig, OpenAPI, Jackson
├── controller          # REST controllers
├── dto                 # request/response
├── entity              # JPA entities
├── integration
│   ├── lark            # auth, base, event
│   ├── hos             # ELD adapter
│   └── optimization    # provider adapter
├── repository          # Spring Data repositories
├── service             # business services
└── validation          # custom validators
```

---

## PHỤ LỤC D — Trạng thái AI dispatch (hai phiên bản)

### D.1. Main workspace `/home/vumoi/logictics_api`

- Có: `AiDispatchController`, `AiDispatchService`, `OptimizationAcceptanceService`, cờ `ai.dispatch.approval-enabled`, `UnconfiguredLlmClient`.
- Chưa có: migration V54/V55, Anthropic client thật, quota/cost provenance.

### D.2. Worktree `/home/vumoi/logictics_api_complete`, branch `ai-dispatch/complete`

| Hạng mục | Trạng thái |
|---|---|
| Approval gate | Đã có; mặc định tắt; `503 AI_APPROVAL_DISABLED` |
| Autonomous mode | Khóa cứng; `400 AUTONOMOUS_MODE_LOCKED` |
| Assignment path | Chỉ `OptimizationAcceptanceService.accept` gọi `LoadService.assignTruck` |
| Provenance | V54 (optimization link, idempotency key, fingerprint) |
| Usage/cost/quota | V55 (measured flags, price version, quota) |
| LLM provider | `AnthropicLlmClient`, raw JDK HttpClient, `POST /v1/messages`, explanation-only |
| Validator | Exact-permutation, giữ ranking engine nếu output sai |
| Cost | Chỉ tính từ price table cấu hình; thiếu dữ liệu → `null`, không bịa 0 |
| Quota | Opt-in, mặc định không giới hạn, không chặn assignment |
| Tests | 150 test AI pass; 48 test PostgreSQL thật; full suite 992 test |
| Chưa xác minh | Anthropic thật, ELD, routing, Lark production, UI E2E |

**Kết luận bắt buộc ghi trong báo cáo:** "đã triển khai và kiểm thử trong môi trường kiểm soát", **không** ghi "hoạt động end-to-end".

### D.3. Blocker nghiệp vụ (B1–B9)

| Mã | Nội dung | Hành vi tạm thời |
|---|---|---|
| B1 | ELD/HOS authority và rule set | Fail closed, `HOS_UNVERIFIED` |
| B2 | Mapping giấy phép/y tế/hazmat/ADR | Từ chối candidate không xác minh được |
| B3 | Ngữ nghĩa `vehicle_capacity`, nguồn trọng lượng | Chênh lệch → chặn để review |
| B4 | Routing endpoint cho xe/hazmat | `cannot verify` |
| B5 | Nguồn forecast cost | Bỏ hạng tử chi phí, đánh dấu score không đầy đủ |
| B6 | Giá LLM, quota, overage | `null` + cờ; không bịa giá |
| B7 | Phiên bản optimization policy được duyệt | Chỉ dry-run/suggestion |
| B8 | Nguồn driver candidacy | Chỉ dùng `trucks.main_driver_id` |
| B9 | Workflow đổi/hủy xe sau khi duyệt | `409 ACCEPTED_ASSIGNMENT_IMMUTABLE` |

---

## PHỤ LỤC E — Checklist trước khi nộp báo cáo

- [ ] Mọi UC trong chương đều có trong bảng danh mục, và ngược lại.
- [ ] Mỗi nhóm nghiệp vụ có đủ 5 tiểu mục (UC diagram, danh sách, đặc tả, Activity, Sequence).
- [ ] Không có Activity/Sequence "mồ côi" — mỗi hình ghi rõ thuộc UC nào.
- [ ] Số hình/bảng không trùng, khớp giữa thân bài và danh mục.
- [ ] Không có endpoint/bảng dữ liệu nào xuất hiện trong báo cáo mà không có trong source.
- [ ] Các chức năng "chưa có" được ghi đúng là chưa có, không mô tả như đã làm.
- [ ] Không có giá trị bí mật, token, mật khẩu, API key trong báo cáo hoặc ảnh chụp.
- [ ] Các mâu thuẫn tài liệu đã được nêu và có cách xử lý.
- [ ] Phần AI dispatch ghi rõ phiên bản và mức độ kiểm chứng.
- [ ] Không khẳng định "production", "E2E", "HA", "SLA" khi chưa có bằng chứng.

---

*Hết tài liệu ngữ cảnh.*
