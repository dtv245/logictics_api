# 05. API

## 5.1 Nguồn và giới hạn đếm

Base path chính trong các mapping nghiệp vụ là /api; host/base URL thay đổi theo môi trường. Compose cấu hình API container ở cổng 8080, nhưng đây không phải bằng chứng production URL. (nguồn: docker-compose.yml:34-70; ví dụ src/main/java/com/company/logicstic/controller/LoadController.java:28-82)

Static scan trên 47 lớp `*Controller.java` hiện có: 200 handler method, 203 khai báo HTTP method/path khi mở rộng annotation nhiều path, 201 cặp method-path duy nhất. Hai cặp trùng nhau là POST /api/loads/{loadId}/accessorials và PUT /api/accessorial-charges/{id}/approve. Đây là phép đếm annotation source, chưa khởi động ứng dụng để xác nhận runtime route registry. Mỗi mapping trong danh mục §5.4 có source line để truy vết. Lệnh liệt kê mapping annotation và lệnh đối chiếu số dòng endpoint/cặp HTTP path trong danh mục:

```bash
rg -n '@(RequestMapping|GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping)' src/main/java --glob '*Controller.java'
```

Sau khi lập danh mục, dùng lệnh sau để đếm số dòng endpoint và số cặp method/path duy nhất trong bảng:

```bash
python3 -c 'import pathlib,collections; p=pathlib.Path("docs/report-context/05_api.md"); rows=[x for x in p.read_text().splitlines() if x.startswith(("| GET |","| POST |","| PUT |","| PATCH |","| DELETE |"))]; pairs=[(r.strip("|").split("|")[0].strip(),r.strip("|").split("|")[1].strip()) for r in rows]; print(len(rows),len(set(pairs)),[(k,v) for k,v in collections.Counter(pairs).items() if v>1])'
```

Kết quả đối chiếu là `203`, `201` và hai cặp trùng nêu trên. Đây là phép kiểm tra tính nhất quán của danh mục source scan, không phải introspection route runtime.

Snapshot OpenAPI có 140 paths/176 operations, được dựng từ JAR local ngày 2026-10-06; deployment_verified=false, source commit khác và source dirty. Không dùng con số đó thay cho route source hiện tại. Bản nháp 175 endpoint: KHÔNG XÁC MINH ĐƯỢC từ runtime trong phiên khảo sát. (nguồn: docs/frontend/openapi-provenance.json:2-16; docs/frontend/api-operation-index.md:1-7)

## 5.2 Xác thực, envelope, lỗi, pagination

- SecurityConfig permit một số health, docs, Lark login/callback/event và payroll callback theo method/path; các route còn lại yêu cầu authentication theo matcher hoặc fallback. Callback/webhook có kiểm tra bổ sung theo flow tương ứng. (nguồn: src/main/java/com/company/logicstic/config/SecurityConfig.java:34-50,55-129)
- ApiResponse gồm success, code, message, data, errors, meta và params tùy điều kiện. Meta chứa timestamp, path và requestId. (nguồn: src/main/java/com/company/logicstic/dto/ApiResponse.java:7-50; src/main/java/com/company/logicstic/dto/ResponseMeta.java:14-25)
- ApiError chứa field, code, message, params; GlobalExceptionHandler chuyển một số lỗi domain/transport sang envelope. Security entrypoint/denied-handler có đường trả response riêng. (nguồn: src/main/java/com/company/logicstic/dto/ApiError.java:3-11; src/main/java/com/company/logicstic/exception/GlobalExceptionHandler.java:23-45; src/main/java/com/company/logicstic/config/SecurityConfig.java:39-43)
- Nhiều list API nhận page (mặc định 1), pageSize (thường 20), orderBy và descending; một số dùng sort/validator riêng. Không có một quy ước cho mọi route. (nguồn: src/main/java/com/company/logicstic/controller/LoadController.java:37-51; src/main/java/com/company/logicstic/common/PaginationValidator.java:44-45; src/main/java/com/company/logicstic/ai/dispatch/AiDispatchController.java:52-61)
- Hai DTO pagination tồn tại với quy ước khác nhau: PagedResponse.currentPage là 1-based; PageInfo lấy số trang 0-based của Spring. Cần xem response DTO theo từng endpoint. (nguồn: src/main/java/com/company/logicstic/dto/PagedResponse.java:7-21; src/main/java/com/company/logicstic/dto/PageInfo.java:16-47)
- Khi bật tenancy, filter lấy claim tenant trong JWT; application.yml mặc định app.tenancy.enabled=false. Không thấy base URL production cố định trong cấu hình ứng dụng. (nguồn: src/main/java/com/company/logicstic/config/TenantJwtClaimFilter.java:21-25,69-74; src/main/resources/application.yml:64-82)
- Runtime issuer, host và token thực tế: KHÔNG TÌM THẤY TRONG CODE. Không trích giá trị secret từ cấu hình.

## 5.3 Quyền route khai báo

Bảng mapping bên dưới diễn giải các matcher trong SecurityConfig; các kiểm tra nghiệp vụ bổ sung có thể nằm trong service/controller. Spring role matcher được liệt kê theo tên role trong code. (nguồn: src/main/java/com/company/logicstic/config/SecurityConfig.java:44-129)

| Nhóm | Quyền |
|---|---|
| Health/OpenAPI docs; Lark authorize/login/callback/events; payroll provider callback | Public theo method/path; callback/webhook có xác thực flow riêng |
| /api/lark/base/** | ADMIN, DISPATCHER |
| /api/roles/**, /api/internal/** | ADMIN |
| POST fleet policy; POST optimization policy; POST qualified-input | ADMIN |
| POST fleet status-events/mileage-attributions | ADMIN, DISPATCHER |
| fleet còn lại | ADMIN, ACCOUNTANT, DISPATCHER, PAYROLL, PAYROLL_MANAGER |
| qualified-input forecasts POST | ADMIN, ACCOUNTANT |
| qualified-input GET | ADMIN, ACCOUNTANT, DISPATCHER |
| optimization còn lại | ADMIN, DISPATCHER |
| invoices, payments, rating | ADMIN, ACCOUNTANT |
| reports và customer balance | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER |
| payroll, pay period, driver-pay-policy, settlements, load costs | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER |
| employees, drivers, trips, trip-stops, inspections | ADMIN, DISPATCHER |
| customers, trucks, loads, documents, terminals | ADMIN, ACCOUNTANT, DISPATCHER |
| me, notifications, payslips | Authenticated |
| Route không khớp matcher cụ thể | Authenticated (fallback) |

## 5.4 Danh mục mapping controller

Mỗi row là một path được khai báo trên annotation controller trong working tree hiện tại. Tên handler giúp nhận diện thao tác và tra source; đây không thay thế đặc tả nghiệp vụ. Cột quyền đối chiếu matcher §5.3. Dòng nguồn chỉ vào file và dòng annotation mapping.

| Method | URL | Controller@method (tên handler) | Permission/middleware | Nguồn |
|---|---|---|---|---|
| GET | /actuator/health | HealthController@health | Public matcher; callback/webhook có kiểm tra riêng nếu áp dụng | src/main/java/com/company/logicstic/controller/HealthController.java:23 |
| PUT | /api/accessorial-charges/{id}/approve | AccessorialChargeController@approve | ADMIN, ACCOUNTANT | src/main/java/com/company/logicstic/controller/AccessorialChargeController.java:21 |
| PUT | /api/accessorial-charges/{id}/approve | AccessorialController@approveAccessorial | ADMIN, ACCOUNTANT | src/main/java/com/company/logicstic/controller/AccessorialController.java:62 |
| GET | /api/ai/config | AiDispatchController@getConfig | Authenticated (fallback) | src/main/java/com/company/logicstic/ai/dispatch/AiDispatchController.java:124 |
| POST | /api/ai/dispatch/decisions/{id}/approve | AiDispatchController@approveDecision | Authenticated (fallback) | src/main/java/com/company/logicstic/ai/dispatch/AiDispatchController.java:81 |
| POST | /api/ai/dispatch/decisions/{id}/reject | AiDispatchController@rejectDecision | Authenticated (fallback) | src/main/java/com/company/logicstic/ai/dispatch/AiDispatchController.java:97 |
| GET | /api/ai/dispatch/pending | AiDispatchController@getPending | Authenticated (fallback) | src/main/java/com/company/logicstic/ai/dispatch/AiDispatchController.java:73 |
| POST | /api/ai/dispatch/runs | AiDispatchController@runDispatch | Authenticated (fallback) | src/main/java/com/company/logicstic/ai/dispatch/AiDispatchController.java:37 |
| GET | /api/ai/dispatch/sessions | AiDispatchController@listSessions | Authenticated (fallback) | src/main/java/com/company/logicstic/ai/dispatch/AiDispatchController.java:51 |
| GET | /api/ai/dispatch/sessions/{id} | AiDispatchController@getSession | Authenticated (fallback) | src/main/java/com/company/logicstic/ai/dispatch/AiDispatchController.java:64 |
| POST | /api/ai/dispatch/sessions/{id}/cancel | AiDispatchController@cancelSession | Authenticated (fallback) | src/main/java/com/company/logicstic/ai/dispatch/AiDispatchController.java:112 |
| GET | /api/ai/features | AiDispatchController@getFeatures | Authenticated (fallback) | src/main/java/com/company/logicstic/ai/dispatch/AiDispatchController.java:143 |
| GET | /api/ai/gaps/ | AiGapController@getGaps | Authenticated (fallback) | src/main/java/com/company/logicstic/ai/gap/AiGapController.java:28 |
| POST | /api/ai/gaps/check | AiGapController@checkGaps | Authenticated (fallback) | src/main/java/com/company/logicstic/ai/gap/AiGapController.java:42 |
| POST | /api/ai/gaps/{id}/answer | AiGapController@answerGap | Authenticated (fallback) | src/main/java/com/company/logicstic/ai/gap/AiGapController.java:83 |
| POST | /api/ai/gaps/{id}/dismiss | AiGapController@dismissGap | Authenticated (fallback) | src/main/java/com/company/logicstic/ai/gap/AiGapController.java:68 |
| POST | /api/ai/loads/overview | AiDispatchController@getOverview | Authenticated (fallback) | src/main/java/com/company/logicstic/ai/dispatch/AiDispatchController.java:173 |
| POST | /api/ai/loads/{loadId}/rerun | AiDispatchController@rerun | Authenticated (fallback) | src/main/java/com/company/logicstic/ai/dispatch/AiDispatchController.java:182 |
| GET | /api/ai/loads/{loadId}/status | AiDispatchController@getLoadStatus | Authenticated (fallback) | src/main/java/com/company/logicstic/ai/dispatch/AiDispatchController.java:164 |
| GET | /api/ai/runs | AiDispatchController@getRuns | Authenticated (fallback) | src/main/java/com/company/logicstic/ai/dispatch/AiDispatchController.java:197 |
| GET | /api/auth/lark/authorize | LarkAuthController@getAuthorizeUrl | Public matcher; callback/webhook có kiểm tra riêng nếu áp dụng | src/main/java/com/company/logicstic/integration/lark/auth/LarkAuthController.java:27 |
| POST | /api/auth/lark/callback | LarkAuthController@handleCallback | Public matcher; callback/webhook có kiểm tra riêng nếu áp dụng | src/main/java/com/company/logicstic/integration/lark/auth/LarkAuthController.java:46 |
| GET | /api/auth/lark/login | LarkAuthController@redirectToLark | Public matcher; callback/webhook có kiểm tra riêng nếu áp dụng | src/main/java/com/company/logicstic/integration/lark/auth/LarkAuthController.java:37 |
| GET | /api/customers/ | CustomerController@search | ADMIN, ACCOUNTANT, DISPATCHER | src/main/java/com/company/logicstic/controller/CustomerController.java:36 |
| POST | /api/customers/ | CustomerController@create | ADMIN, ACCOUNTANT, DISPATCHER | src/main/java/com/company/logicstic/controller/CustomerController.java:56 |
| GET | /api/customers/{customerId}/balance | ReportController@getCustomerBalance | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER | src/main/java/com/company/logicstic/controller/ReportController.java:71 |
| DELETE | /api/customers/{id} | CustomerController@delete | ADMIN, ACCOUNTANT, DISPATCHER | src/main/java/com/company/logicstic/controller/CustomerController.java:75 |
| GET | /api/customers/{id} | CustomerController@getById | ADMIN, ACCOUNTANT, DISPATCHER | src/main/java/com/company/logicstic/controller/CustomerController.java:50 |
| PUT | /api/customers/{id} | CustomerController@update | ADMIN, ACCOUNTANT, DISPATCHER | src/main/java/com/company/logicstic/controller/CustomerController.java:65 |
| GET | /api/documents/ | DocumentController@search | ADMIN, ACCOUNTANT, DISPATCHER | src/main/java/com/company/logicstic/controller/DocumentController.java:30 |
| DELETE | /api/documents/{id} | DocumentController@delete | ADMIN, ACCOUNTANT, DISPATCHER | src/main/java/com/company/logicstic/controller/DocumentController.java:53 |
| GET | /api/documents/{id} | DocumentController@getById | ADMIN, ACCOUNTANT, DISPATCHER | src/main/java/com/company/logicstic/controller/DocumentController.java:47 |
| GET | /api/driver-pay-policies/ | DriverPayPolicyController@list | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER (provider callback có permit riêng) | src/main/java/com/company/logicstic/controller/DriverPayPolicyController.java:22 |
| POST | /api/driver-pay-policies/ | DriverPayPolicyController@create | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER (provider callback có permit riêng) | src/main/java/com/company/logicstic/controller/DriverPayPolicyController.java:34 |
| GET | /api/driver-pay-policies/{id} | DriverPayPolicyController@get | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER (provider callback có permit riêng) | src/main/java/com/company/logicstic/controller/DriverPayPolicyController.java:28 |
| POST | /api/driver-pay-policies/{id}/new-version | DriverPayPolicyController@newVersion | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER (provider callback có permit riêng) | src/main/java/com/company/logicstic/controller/DriverPayPolicyController.java:43 |
| GET | /api/driver-settlements/ | DriverSettlementController@list | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER (provider callback có permit riêng) | src/main/java/com/company/logicstic/controller/DriverSettlementController.java:44 |
| POST | /api/driver-settlements/calculate | DriverSettlementController@calculate | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER (provider callback có permit riêng) | src/main/java/com/company/logicstic/controller/DriverSettlementController.java:56 |
| GET | /api/driver-settlements/{id} | DriverSettlementController@get | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER (provider callback có permit riêng) | src/main/java/com/company/logicstic/controller/DriverSettlementController.java:65 |
| POST | /api/driver-settlements/{id}/adjustments | DriverSettlementController@adjustment | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER (provider callback có permit riêng) | src/main/java/com/company/logicstic/controller/DriverSettlementController.java:127 |
| POST | /api/driver-settlements/{id}/approve | DriverSettlementController@approve | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER (provider callback có permit riêng) | src/main/java/com/company/logicstic/controller/DriverSettlementController.java:84 |
| POST | /api/driver-settlements/{id}/billing-adjustments | DriverSettlementController@billingAdjustment | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER (provider callback có permit riêng) | src/main/java/com/company/logicstic/controller/DriverSettlementController.java:37 |
| POST | /api/driver-settlements/{id}/lock | DriverSettlementController@lock | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER (provider callback có permit riêng) | src/main/java/com/company/logicstic/controller/DriverSettlementController.java:94 |
| POST | /api/driver-settlements/{id}/recalculate-revenue | DriverSettlementController@recalculateRevenue | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER (provider callback có permit riêng) | src/main/java/com/company/logicstic/controller/DriverSettlementController.java:30 |
| POST | /api/driver-settlements/{id}/require-validation | DriverSettlementController@requireValidation | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER (provider callback có permit riêng) | src/main/java/com/company/logicstic/controller/DriverSettlementController.java:106 |
| POST | /api/driver-settlements/{id}/resolve-validation | DriverSettlementController@resolveValidation | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER (provider callback có permit riêng) | src/main/java/com/company/logicstic/controller/DriverSettlementController.java:117 |
| POST | /api/driver-settlements/{id}/reversal | DriverSettlementController@reversal | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER (provider callback có permit riêng) | src/main/java/com/company/logicstic/controller/DriverSettlementController.java:137 |
| POST | /api/driver-settlements/{id}/submit-review | DriverSettlementController@submitForReview | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER (provider callback có permit riêng) | src/main/java/com/company/logicstic/controller/DriverSettlementController.java:74 |
| GET | /api/driver/me/payslips | PayslipController@mine | Authenticated | src/main/java/com/company/logicstic/controller/PayslipController.java:16 |
| GET | /api/drivers/ | DriverController@search | ADMIN, DISPATCHER | src/main/java/com/company/logicstic/controller/DriverController.java:29 |
| GET | /api/drivers/{id} | DriverController@getById | ADMIN, DISPATCHER | src/main/java/com/company/logicstic/controller/DriverController.java:43 |
| GET | /api/employees/ | EmployeeController@search | ADMIN, DISPATCHER | src/main/java/com/company/logicstic/controller/EmployeeController.java:36 |
| POST | /api/employees/ | EmployeeController@create | ADMIN, DISPATCHER | src/main/java/com/company/logicstic/controller/EmployeeController.java:57 |
| DELETE | /api/employees/{id} | EmployeeController@delete | ADMIN, DISPATCHER | src/main/java/com/company/logicstic/controller/EmployeeController.java:76 |
| GET | /api/employees/{id} | EmployeeController@getById | ADMIN, DISPATCHER | src/main/java/com/company/logicstic/controller/EmployeeController.java:51 |
| PUT | /api/employees/{id} | EmployeeController@update | ADMIN, DISPATCHER | src/main/java/com/company/logicstic/controller/EmployeeController.java:66 |
| POST | /api/expenses/{id}/approve | ExpenseApprovalController@approve | ADMIN, ACCOUNTANT | src/main/java/com/company/logicstic/controller/ExpenseApprovalController.java:18 |
| POST | /api/fleet/mileage-attributions | FleetHistoryController@attribute | ADMIN, DISPATCHER | src/main/java/com/company/logicstic/controller/FleetHistoryController.java:23 |
| GET | /api/fleet/policies | FleetHistoryController@policies | ADMIN, ACCOUNTANT, DISPATCHER, PAYROLL, PAYROLL_MANAGER | src/main/java/com/company/logicstic/controller/FleetHistoryController.java:20 |
| POST | /api/fleet/policies | FleetHistoryController@publish | ADMIN | src/main/java/com/company/logicstic/controller/FleetHistoryController.java:19 |
| GET | /api/fleet/policies/{id} | FleetHistoryController@policy | ADMIN, ACCOUNTANT, DISPATCHER, PAYROLL, PAYROLL_MANAGER | src/main/java/com/company/logicstic/controller/FleetHistoryController.java:21 |
| POST | /api/fleet/status-events | FleetHistoryController@capture | ADMIN, DISPATCHER | src/main/java/com/company/logicstic/controller/FleetHistoryController.java:22 |
| GET | /api/health | HealthController@health | Public matcher; callback/webhook có kiểm tra riêng nếu áp dụng | src/main/java/com/company/logicstic/controller/HealthController.java:23 |
| GET | /api/inspections/ | InspectionController@search | ADMIN, DISPATCHER | src/main/java/com/company/logicstic/controller/InspectionController.java:36 |
| POST | /api/inspections/ | InspectionController@create | ADMIN, DISPATCHER | src/main/java/com/company/logicstic/controller/InspectionController.java:56 |
| DELETE | /api/inspections/{id} | InspectionController@delete | ADMIN, DISPATCHER | src/main/java/com/company/logicstic/controller/InspectionController.java:75 |
| GET | /api/inspections/{id} | InspectionController@getById | ADMIN, DISPATCHER | src/main/java/com/company/logicstic/controller/InspectionController.java:50 |
| PUT | /api/inspections/{id} | InspectionController@update | ADMIN, DISPATCHER | src/main/java/com/company/logicstic/controller/InspectionController.java:65 |
| GET | /api/internal/build | InternalBuildController@build | ADMIN | src/main/java/com/company/logicstic/controller/InternalBuildController.java:21 |
| GET | /api/invoices/ | InvoiceController@search | ADMIN, ACCOUNTANT | src/main/java/com/company/logicstic/controller/InvoiceController.java:36 |
| POST | /api/invoices/ | InvoiceController@create | ADMIN, ACCOUNTANT | src/main/java/com/company/logicstic/controller/InvoiceController.java:58 |
| POST | /api/invoices/billing/primary | BillingController@primary | ADMIN, ACCOUNTANT | src/main/java/com/company/logicstic/controller/BillingController.java:25 |
| GET | /api/invoices/billing/{id} | BillingController@get | ADMIN, ACCOUNTANT | src/main/java/com/company/logicstic/controller/BillingController.java:40 |
| POST | /api/invoices/billing/{id}/credit | BillingController@credit | ADMIN, ACCOUNTANT | src/main/java/com/company/logicstic/controller/BillingController.java:48 |
| POST | /api/invoices/billing/{id}/issue | BillingController@issue | ADMIN, ACCOUNTANT | src/main/java/com/company/logicstic/controller/BillingController.java:36 |
| POST | /api/invoices/billing/{id}/rebill | BillingController@rebill | ADMIN, ACCOUNTANT | src/main/java/com/company/logicstic/controller/BillingController.java:52 |
| POST | /api/invoices/billing/{id}/regenerate | BillingController@regenerate | ADMIN, ACCOUNTANT | src/main/java/com/company/logicstic/controller/BillingController.java:32 |
| POST | /api/invoices/billing/{id}/supplemental | BillingController@supplemental | ADMIN, ACCOUNTANT | src/main/java/com/company/logicstic/controller/BillingController.java:44 |
| POST | /api/invoices/tax-assessments/ | TaxAssessmentController@capture | ADMIN, ACCOUNTANT | src/main/java/com/company/logicstic/controller/TaxAssessmentController.java:20 |
| GET | /api/invoices/tax-assessments/{id} | TaxAssessmentController@get | ADMIN, ACCOUNTANT | src/main/java/com/company/logicstic/controller/TaxAssessmentController.java:27 |
| DELETE | /api/invoices/{id} | InvoiceController@delete | ADMIN, ACCOUNTANT | src/main/java/com/company/logicstic/controller/InvoiceController.java:77 |
| GET | /api/invoices/{id} | InvoiceController@getById | ADMIN, ACCOUNTANT | src/main/java/com/company/logicstic/controller/InvoiceController.java:52 |
| PUT | /api/invoices/{id} | InvoiceController@update | ADMIN, ACCOUNTANT | src/main/java/com/company/logicstic/controller/InvoiceController.java:67 |
| GET | /api/lark/base/drivers/{id}/sync | LarkSyncController@driverStatus | ADMIN, DISPATCHER | src/main/java/com/company/logicstic/integration/lark/base/LarkSyncController.java:25 |
| POST | /api/lark/base/drivers/{id}/sync | LarkSyncController@syncDriver | ADMIN, DISPATCHER | src/main/java/com/company/logicstic/integration/lark/base/LarkSyncController.java:29 |
| GET | /api/lark/base/loads | LarkBaseController@listSyncedLoads | ADMIN, DISPATCHER | src/main/java/com/company/logicstic/integration/lark/base/LarkBaseController.java:43 |
| GET | /api/lark/base/loads/{id}/sync | LarkSyncController@loadStatus | ADMIN, DISPATCHER | src/main/java/com/company/logicstic/integration/lark/base/LarkSyncController.java:16 |
| POST | /api/lark/base/loads/{id}/sync | LarkSyncController@syncLoad | ADMIN, DISPATCHER | src/main/java/com/company/logicstic/integration/lark/base/LarkSyncController.java:20 |
| GET | /api/lark/base/missing | LarkSyncController@missing | ADMIN, DISPATCHER | src/main/java/com/company/logicstic/integration/lark/base/LarkSyncController.java:34 |
| POST | /api/lark/base/sync/driver/{employeeId} | LarkBaseController@syncDriver | ADMIN, DISPATCHER | src/main/java/com/company/logicstic/integration/lark/base/LarkBaseController.java:34 |
| POST | /api/lark/base/sync/load/{loadId} | LarkBaseController@syncLoad | ADMIN, DISPATCHER | src/main/java/com/company/logicstic/integration/lark/base/LarkBaseController.java:25 |
| POST | /api/lark/base/{domain}/backfill | LarkSyncController@backfill | ADMIN, DISPATCHER | src/main/java/com/company/logicstic/integration/lark/base/LarkSyncController.java:40 |
| POST | /api/lark/events | LarkEventController@receive | Public matcher; callback/webhook có kiểm tra riêng nếu áp dụng | src/main/java/com/company/logicstic/integration/lark/event/LarkEventController.java:25 |
| GET | /api/loads/ | LoadController@search | ADMIN, ACCOUNTANT, DISPATCHER | src/main/java/com/company/logicstic/controller/LoadController.java:37 |
| POST | /api/loads/ | LoadController@create | ADMIN, ACCOUNTANT, DISPATCHER | src/main/java/com/company/logicstic/controller/LoadController.java:60 |
| DELETE | /api/loads/{id} | LoadController@delete | ADMIN, ACCOUNTANT, DISPATCHER | src/main/java/com/company/logicstic/controller/LoadController.java:79 |
| GET | /api/loads/{id} | LoadController@getById | ADMIN, ACCOUNTANT, DISPATCHER | src/main/java/com/company/logicstic/controller/LoadController.java:54 |
| PUT | /api/loads/{id} | LoadController@update | ADMIN, ACCOUNTANT, DISPATCHER | src/main/java/com/company/logicstic/controller/LoadController.java:69 |
| POST | /api/loads/{id}/rating/accept | RatingController@accept | ADMIN, ACCOUNTANT | src/main/java/com/company/logicstic/controller/RatingController.java:31 |
| POST | /api/loads/{id}/rating/contract-mileage | RatingMileageController@capture | ADMIN, ACCOUNTANT | src/main/java/com/company/logicstic/controller/RatingMileageController.java:21 |
| POST | /api/loads/{id}/rating/preview | RatingController@preview | ADMIN, ACCOUNTANT | src/main/java/com/company/logicstic/controller/RatingController.java:25 |
| POST | /api/loads/{id}/requested-pickup-business-date | LoadPickupBusinessDateController@remediate | ADMIN, ACCOUNTANT, DISPATCHER | src/main/java/com/company/logicstic/controller/LoadPickupBusinessDateController.java:16 |
| GET | /api/loads/{loadId}/accessorials | AccessorialController@getAccessorials | ADMIN, ACCOUNTANT, DISPATCHER | src/main/java/com/company/logicstic/controller/AccessorialController.java:43 |
| POST | /api/loads/{loadId}/accessorials | AccessorialChargeController@create | ADMIN, ACCOUNTANT, DISPATCHER | src/main/java/com/company/logicstic/controller/AccessorialChargeController.java:17 |
| POST | /api/loads/{loadId}/accessorials | AccessorialController@createAccessorial | ADMIN, ACCOUNTANT, DISPATCHER | src/main/java/com/company/logicstic/controller/AccessorialController.java:33 |
| GET | /api/loads/{loadId}/costs/ | ShipmentCostController@getCosts | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER (provider callback có permit riêng) | src/main/java/com/company/logicstic/controller/ShipmentCostController.java:63 |
| POST | /api/loads/{loadId}/costs/ | ShipmentCostController@createCost | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER (provider callback có permit riêng) | src/main/java/com/company/logicstic/controller/ShipmentCostController.java:33 |
| POST | /api/loads/{loadId}/costs/allocate-maintenance | ShipmentCostController@allocateMaintenance | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER (provider callback có permit riêng) | src/main/java/com/company/logicstic/controller/ShipmentCostController.java:52 |
| POST | /api/loads/{loadId}/costs/sync-expenses | ShipmentCostController@syncExpenses | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER (provider callback có permit riêng) | src/main/java/com/company/logicstic/controller/ShipmentCostController.java:43 |
| GET | /api/loads/{loadId}/financial-summary | ReportController@getLoadProfitability | ADMIN, ACCOUNTANT, DISPATCHER | src/main/java/com/company/logicstic/controller/ReportController.java:215 |
| GET | /api/loads/{loadId}/timeline | LoadTimelineController@getTimeline | ADMIN, ACCOUNTANT, DISPATCHER | src/main/java/com/company/logicstic/controller/LoadTimelineController.java:25 |
| GET | /api/me | CurrentUserController@current | Authenticated | src/main/java/com/company/logicstic/controller/CurrentUserController.java:34 |
| GET | /api/notifications/ | NotificationController@list | Authenticated | src/main/java/com/company/logicstic/controller/NotificationController.java:30 |
| POST | /api/notifications/mark-all-read | NotificationController@markAllAsRead | Authenticated | src/main/java/com/company/logicstic/controller/NotificationController.java:46 |
| GET | /api/notifications/{id} | NotificationController@getById | Authenticated | src/main/java/com/company/logicstic/controller/NotificationController.java:40 |
| POST | /api/optimization/policies/ | OptimizationPolicyController@publish | ADMIN | src/main/java/com/company/logicstic/controller/OptimizationPolicyController.java:21 |
| GET | /api/optimization/policies/{id} | OptimizationPolicyController@get | ADMIN, DISPATCHER | src/main/java/com/company/logicstic/controller/OptimizationPolicyController.java:27 |
| POST | /api/optimization/qualified-inputs/ | OptimizationQualifiedInputController@capture | ADMIN, DISPATCHER | src/main/java/com/company/logicstic/controller/OptimizationQualifiedInputController.java:23 |
| POST | /api/optimization/qualified-inputs/forecasts | OptimizationQualifiedInputController@forecast | ADMIN, ACCOUNTANT | src/main/java/com/company/logicstic/controller/OptimizationQualifiedInputController.java:27 |
| GET | /api/optimization/qualified-inputs/{id} | OptimizationQualifiedInputController@get | ADMIN, ACCOUNTANT, DISPATCHER | src/main/java/com/company/logicstic/controller/OptimizationQualifiedInputController.java:32 |
| POST | /api/optimization/runs/ | OptimizationRunController@create | ADMIN, DISPATCHER | src/main/java/com/company/logicstic/controller/OptimizationRunController.java:25 |
| GET | /api/optimization/runs/{id} | OptimizationRunController@get | ADMIN, DISPATCHER | src/main/java/com/company/logicstic/controller/OptimizationRunController.java:31 |
| POST | /api/optimization/runs/{id}/assignments/{candidateId}/accept | OptimizationRunController@accept | ADMIN, DISPATCHER | src/main/java/com/company/logicstic/controller/OptimizationRunController.java:35 |
| GET | /api/pay-periods/ | PayPeriodController@list | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER (provider callback có permit riêng) | src/main/java/com/company/logicstic/controller/PayPeriodController.java:25 |
| POST | /api/pay-periods/ | PayPeriodController@create | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER (provider callback có permit riêng) | src/main/java/com/company/logicstic/controller/PayPeriodController.java:30 |
| GET | /api/payments/ | PaymentController@search | ADMIN, ACCOUNTANT | src/main/java/com/company/logicstic/controller/PaymentController.java:37 |
| POST | /api/payments/ | PaymentController@create | ADMIN, ACCOUNTANT | src/main/java/com/company/logicstic/controller/PaymentController.java:57 |
| DELETE | /api/payments/{id} | PaymentController@delete | ADMIN, ACCOUNTANT | src/main/java/com/company/logicstic/controller/PaymentController.java:86 |
| GET | /api/payments/{id} | PaymentController@getById | ADMIN, ACCOUNTANT | src/main/java/com/company/logicstic/controller/PaymentController.java:51 |
| PUT | /api/payments/{id} | PaymentController@update | ADMIN, ACCOUNTANT | src/main/java/com/company/logicstic/controller/PaymentController.java:66 |
| POST | /api/payments/{id}/cancel | PaymentController@cancel | ADMIN, ACCOUNTANT | src/main/java/com/company/logicstic/controller/PaymentController.java:76 |
| POST | /api/payroll/configuration/employees/{id}/profiles | PayrollConfigurationController@profile | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER (provider callback có permit riêng) | src/main/java/com/company/logicstic/controller/PayrollConfigurationController.java:13 |
| POST | /api/payroll/configuration/policy-versions | PayrollConfigurationController@policy | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER (provider callback có permit riêng) | src/main/java/com/company/logicstic/controller/PayrollConfigurationController.java:14 |
| PUT | /api/payroll/configuration/tenant-default | PayrollConfigurationController@tenant | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER (provider callback có permit riêng) | src/main/java/com/company/logicstic/controller/PayrollConfigurationController.java:12 |
| POST | /api/payroll/items/{id}/no-payment-required | PayrollPaymentController@markNoPaymentRequired | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER (provider callback có permit riêng) | src/main/java/com/company/logicstic/controller/PayrollPaymentController.java:21 |
| GET | /api/payroll/items/{id}/payments | PayrollPaymentController@list | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER (provider callback có permit riêng) | src/main/java/com/company/logicstic/controller/PayrollPaymentController.java:20 |
| POST | /api/payroll/items/{id}/payments | PayrollPaymentController@schedule | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER (provider callback có permit riêng) | src/main/java/com/company/logicstic/controller/PayrollPaymentController.java:19 |
| POST | /api/payroll/payments/{id}/dispatch | PayrollPaymentController@dispatch | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER (provider callback có permit riêng) | src/main/java/com/company/logicstic/controller/PayrollPaymentController.java:25 |
| POST | /api/payroll/payments/{id}/reconcile-bank | PayrollPaymentController@reconcileBank | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER (provider callback có permit riêng) | src/main/java/com/company/logicstic/controller/PayrollPaymentController.java:30 |
| POST | /api/payroll/provider-callbacks/{provider} | PayrollCallbackController@receive | Public matcher; callback/webhook có kiểm tra riêng nếu áp dụng | src/main/java/com/company/logicstic/controller/PayrollCallbackController.java:10 |
| GET | /api/payroll/reconciliation-cases | PayrollPaymentController@openReconciliationCases | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER (provider callback có permit riêng) | src/main/java/com/company/logicstic/controller/PayrollPaymentController.java:26 |
| POST | /api/payroll/runs/calculate | PayrollRunController@calculate | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER (provider callback có permit riêng) | src/main/java/com/company/logicstic/controller/PayrollRunController.java:17 |
| GET | /api/payroll/runs/{id} | PayrollRunController@get | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER (provider callback có permit riêng) | src/main/java/com/company/logicstic/controller/PayrollRunController.java:19 |
| POST | /api/payroll/runs/{id}/approve | PayrollRunController@approve | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER (provider callback có permit riêng) | src/main/java/com/company/logicstic/controller/PayrollRunController.java:21 |
| POST | /api/payroll/runs/{id}/lock | PayrollRunController@lock | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER (provider callback có permit riêng) | src/main/java/com/company/logicstic/controller/PayrollRunController.java:22 |
| POST | /api/payroll/runs/{id}/payslips | PayslipController@issue | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER (provider callback có permit riêng) | src/main/java/com/company/logicstic/controller/PayslipController.java:15 |
| POST | /api/payroll/runs/{id}/recalculate | PayrollRunController@recalculate | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER (provider callback có permit riêng) | src/main/java/com/company/logicstic/controller/PayrollRunController.java:18 |
| POST | /api/payroll/runs/{id}/submit-review | PayrollRunController@review | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER (provider callback có permit riêng) | src/main/java/com/company/logicstic/controller/PayrollRunController.java:20 |
| GET | /api/payslips/{id} | PayslipController@get | Authenticated | src/main/java/com/company/logicstic/controller/PayslipController.java:17 |
| GET | /api/payslips/{id}/pdf | PayslipController@pdf | Authenticated | src/main/java/com/company/logicstic/controller/PayslipController.java:18 |
| POST | /api/rating/contracts | RatePolicyController@createContract | ADMIN, ACCOUNTANT | src/main/java/com/company/logicstic/controller/RatePolicyController.java:25 |
| POST | /api/rating/contracts/{id}/versions | RatePolicyController@contractVersion | ADMIN, ACCOUNTANT | src/main/java/com/company/logicstic/controller/RatePolicyController.java:30 |
| GET | /api/rating/contracts/{id}/versions/{version} | RatePolicyController@getContract | ADMIN, ACCOUNTANT | src/main/java/com/company/logicstic/controller/RatePolicyController.java:35 |
| POST | /api/rating/rules | RatePolicyController@createRule | ADMIN, ACCOUNTANT | src/main/java/com/company/logicstic/controller/RatePolicyController.java:39 |
| POST | /api/rating/rules/{id}/versions | RatePolicyController@ruleVersion | ADMIN, ACCOUNTANT | src/main/java/com/company/logicstic/controller/RatePolicyController.java:44 |
| GET | /api/rating/rules/{id}/versions/{version} | RatePolicyController@getRule | ADMIN, ACCOUNTANT | src/main/java/com/company/logicstic/controller/RatePolicyController.java:49 |
| GET | /api/rating/snapshots/{id} | RatingController@get | ADMIN, ACCOUNTANT | src/main/java/com/company/logicstic/controller/RatingController.java:39 |
| GET | /api/reports/costs/known-operating-cpm | ReportController@getKnownOperatingCpm | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER | src/main/java/com/company/logicstic/controller/ReportController.java:203 |
| GET | /api/reports/drivers/ranking | ReportController@getDriverRanking | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER | src/main/java/com/company/logicstic/controller/ReportController.java:238 |
| GET | /api/reports/expenses | ReportController@getExpensesReport | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER | src/main/java/com/company/logicstic/controller/ReportController.java:166 |
| GET | /api/reports/financials/monthly | ReportController@getMonthlyFinancials | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER | src/main/java/com/company/logicstic/controller/ReportController.java:81 |
| GET | /api/reports/fleet/fuel | ReportController@getFuelReport | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER | src/main/java/com/company/logicstic/controller/ReportController.java:178 |
| GET | /api/reports/fleet/health | ReportController@getFleetHistory | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER | src/main/java/com/company/logicstic/controller/ReportController.java:49 |
| GET | /api/reports/fleet/maintenance | ReportController@getMaintenanceReport | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER | src/main/java/com/company/logicstic/controller/ReportController.java:190 |
| GET | /api/reports/fleet/utilization-history | ReportController@getFleetHistory | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER | src/main/java/com/company/logicstic/controller/ReportController.java:49 |
| GET | /api/reports/operations/delays | ReportController@getDelayReport | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER | src/main/java/com/company/logicstic/controller/ReportController.java:132 |
| GET | /api/reports/operations/exceptions-summary | ReportController@getExceptionsSummary | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER | src/main/java/com/company/logicstic/controller/ReportController.java:153 |
| GET | /api/reports/operations/on-time-delivery | ReportController@getOtdReport | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER | src/main/java/com/company/logicstic/controller/ReportController.java:121 |
| GET | /api/reports/operations/transit-time | ReportController@getTransitTimeReport | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER | src/main/java/com/company/logicstic/controller/ReportController.java:143 |
| GET | /api/reports/profitability/by-lane | ReportController@profitabilityByLane | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER | src/main/java/com/company/logicstic/controller/ReportController.java:228 |
| GET | /api/reports/profitability/by-load | ReportController@profitabilityByLoad | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER | src/main/java/com/company/logicstic/controller/ReportController.java:220 |
| GET | /api/reports/profitability/by-truck | ReportController@profitabilityByTruck | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER | src/main/java/com/company/logicstic/controller/ReportController.java:233 |
| GET | /api/reports/revenue | ReportController@getLoadRevenue | ADMIN, ACCOUNTANT, PAYROLL, PAYROLL_MANAGER | src/main/java/com/company/logicstic/controller/ReportController.java:61 |
| GET | /api/roles/ | RoleController@list | ADMIN | src/main/java/com/company/logicstic/controller/RoleController.java:36 |
| POST | /api/roles/ | RoleController@create | ADMIN | src/main/java/com/company/logicstic/controller/RoleController.java:52 |
| DELETE | /api/roles/{id} | RoleController@delete | ADMIN | src/main/java/com/company/logicstic/controller/RoleController.java:71 |
| GET | /api/roles/{id} | RoleController@getById | ADMIN | src/main/java/com/company/logicstic/controller/RoleController.java:46 |
| PUT | /api/roles/{id} | RoleController@update | ADMIN | src/main/java/com/company/logicstic/controller/RoleController.java:61 |
| GET | /api/terminals/ | TerminalController@search | ADMIN, ACCOUNTANT, DISPATCHER | src/main/java/com/company/logicstic/controller/TerminalController.java:36 |
| POST | /api/terminals/ | TerminalController@create | ADMIN, ACCOUNTANT, DISPATCHER | src/main/java/com/company/logicstic/controller/TerminalController.java:57 |
| DELETE | /api/terminals/{id} | TerminalController@delete | ADMIN, ACCOUNTANT, DISPATCHER | src/main/java/com/company/logicstic/controller/TerminalController.java:76 |
| GET | /api/terminals/{id} | TerminalController@getById | ADMIN, ACCOUNTANT, DISPATCHER | src/main/java/com/company/logicstic/controller/TerminalController.java:51 |
| PUT | /api/terminals/{id} | TerminalController@update | ADMIN, ACCOUNTANT, DISPATCHER | src/main/java/com/company/logicstic/controller/TerminalController.java:66 |
| POST | /api/trip-stops/{id}/arrive | TripExecutionController@arriveStop | ADMIN, DISPATCHER | src/main/java/com/company/logicstic/controller/TripExecutionController.java:70 |
| POST | /api/trip-stops/{id}/complete-service | TripExecutionController@completeStopService | ADMIN, DISPATCHER | src/main/java/com/company/logicstic/controller/TripExecutionController.java:88 |
| POST | /api/trip-stops/{id}/depart | TripExecutionController@departStop | ADMIN, DISPATCHER | src/main/java/com/company/logicstic/controller/TripExecutionController.java:97 |
| POST | /api/trip-stops/{id}/start-service | TripExecutionController@startStopService | ADMIN, DISPATCHER | src/main/java/com/company/logicstic/controller/TripExecutionController.java:79 |
| POST | /api/trip-stops/{stopId}/calculate-detention | AccessorialController@calculateDetention | ADMIN, ACCOUNTANT, DISPATCHER | src/main/java/com/company/logicstic/controller/AccessorialController.java:52 |
| GET | /api/trips/ | TripController@search | ADMIN, DISPATCHER | src/main/java/com/company/logicstic/controller/TripController.java:37 |
| POST | /api/trips/ | TripController@create | ADMIN, DISPATCHER | src/main/java/com/company/logicstic/controller/TripController.java:58 |
| DELETE | /api/trips/{id} | TripController@delete | ADMIN, DISPATCHER | src/main/java/com/company/logicstic/controller/TripController.java:77 |
| GET | /api/trips/{id} | TripController@getById | ADMIN, DISPATCHER | src/main/java/com/company/logicstic/controller/TripController.java:52 |
| PUT | /api/trips/{id} | TripController@update | ADMIN, DISPATCHER | src/main/java/com/company/logicstic/controller/TripController.java:67 |
| GET | /api/trips/{tripId}/drivers | TripExecutionController@getTripDrivers | ADMIN, DISPATCHER | src/main/java/com/company/logicstic/controller/TripExecutionController.java:52 |
| POST | /api/trips/{tripId}/drivers | TripExecutionController@assignDriver | ADMIN, DISPATCHER | src/main/java/com/company/logicstic/controller/TripExecutionController.java:32 |
| POST | /api/trips/{tripId}/drivers/{assignmentId}/unassign | TripExecutionController@unassignDriver | ADMIN, DISPATCHER | src/main/java/com/company/logicstic/controller/TripExecutionController.java:42 |
| GET | /api/trips/{tripId}/stops | TripExecutionController@getTripStops | ADMIN, DISPATCHER | src/main/java/com/company/logicstic/controller/TripExecutionController.java:61 |
| GET | /api/trucks/ | TruckController@search | ADMIN, ACCOUNTANT, DISPATCHER | src/main/java/com/company/logicstic/controller/TruckController.java:37 |
| POST | /api/trucks/ | TruckController@create | ADMIN, ACCOUNTANT, DISPATCHER | src/main/java/com/company/logicstic/controller/TruckController.java:58 |
| DELETE | /api/trucks/{id} | TruckController@delete | ADMIN, ACCOUNTANT, DISPATCHER | src/main/java/com/company/logicstic/controller/TruckController.java:77 |
| GET | /api/trucks/{id} | TruckController@getById | ADMIN, ACCOUNTANT, DISPATCHER | src/main/java/com/company/logicstic/controller/TruckController.java:52 |
| PUT | /api/trucks/{id} | TruckController@update | ADMIN, ACCOUNTANT, DISPATCHER | src/main/java/com/company/logicstic/controller/TruckController.java:67 |
| GET | /health | HealthController@health | Public matcher; callback/webhook có kiểm tra riêng nếu áp dụng | src/main/java/com/company/logicstic/controller/HealthController.java:23 |

## 5.5 Ví dụ request/response

Các JSON dưới đây là skeleton minh họa dựa trên DTO/handler, không phải dữ liệu live. Placeholder ID/field phải thay theo tenant và dữ liệu thật; ví dụ không chứng minh endpoint thành công trên runtime.

### 1. Current user

~~~http
GET /api/me
Authorization: Bearer <access-token>
~~~

~~~json
{"success":true,"code":"OK","message":"Request completed successfully","data":{},"errors":[],"meta":{"timestamp":"<ISO-8601>","path":"/api/me","requestId":"<request-id>"}}
~~~

Handler wrap current identity trong ApiResponse. (nguồn: src/main/java/com/company/logicstic/controller/CurrentUserController.java:34-38; src/main/java/com/company/logicstic/dto/ApiResponse.java:7-36)

### 2. Load list

~~~http
GET /api/loads?page=1&pageSize=20&orderBy=name&descending=false
~~~

~~~json
{"success":true,"code":"OK","message":"Request completed successfully","data":{"items":[],"totalItems":0,"totalPages":0,"currentPage":1,"pageSize":20},"errors":[],"meta":{"timestamp":"<ISO-8601>","path":"/api/loads","requestId":"<request-id>"}}
~~~

Pagination keys lấy từ PagedResponse. (nguồn: src/main/java/com/company/logicstic/controller/LoadController.java:37-51; src/main/java/com/company/logicstic/dto/PagedResponse.java:7-21)

### 3. Customer create request

~~~http
POST /api/customers
Content-Type: application/json
~~~

~~~json
{"name":"<name>","email":"<email>","status":"<status>","isVatExempt":false,"addressCity":"<city>","addressCountry":"<country>"}
~~~

Các trường khác của CreateCustomerRequest có thể bỏ trống; email có @Email, name/status có @NotBlank, isVatExempt có @NotNull. (nguồn: src/main/java/com/company/logicstic/dto/customer/CreateCustomerRequest.java:7-21; src/main/java/com/company/logicstic/controller/CustomerController.java:56-63)

### 4. Create Load — các field bắt buộc

~~~json
{"name":"<name>","type":"<type>","status":"<status>","distance":1.0,"isInProximity":false,"customerId":"<uuid>","source":"<source>","isHazmat":false,"deliveryCostAmount":1.00,"deliveryCostCurrency":"<currency>","originAddressLine1":"<line>","originAddressCity":"<city>","originAddressState":"<state>","originAddressZipCode":"<zip>","originAddressCountry":"<country>","originLocationLatitude":0.0,"originLocationLongitude":0.0,"destinationAddressLine1":"<line>","destinationAddressCity":"<city>","destinationAddressState":"<state>","destinationAddressZipCode":"<zip>","destinationAddressCountry":"<country>","destinationLocationLatitude":0.0,"destinationLocationLongitude":0.0}
~~~

Các chuỗi trong dấu ngoặc nhọn là placeholders, không phải giá trị có thể gửi nguyên xi. (nguồn: src/main/java/com/company/logicstic/dto/load/CreateLoadRequest.java:11-54; src/main/java/com/company/logicstic/controller/LoadController.java:60-67)

### 5. Create Trip

~~~json
{"name":"<name>","totalDistance":1.0,"status":"<status>","truckId":"<uuid>"}
~~~

Handler trả ApiResponse<TripView> với HTTP 201 khi service thành công. (nguồn: src/main/java/com/company/logicstic/dto/trip/CreateTripRequest.java:8-13; src/main/java/com/company/logicstic/controller/TripController.java:58-65)

### 6. Notification list response

~~~json
{"success":true,"code":"OK","message":"Request completed successfully","data":{"items":[],"totalItems":0,"totalPages":0,"currentPage":1,"pageSize":20},"errors":[],"meta":{"timestamp":"<ISO-8601>","path":"/api/notifications","requestId":"<request-id>"}}
~~~

Page defaults and envelope derive from controller/DTO. (nguồn: src/main/java/com/company/logicstic/controller/NotificationController.java:30-38; src/main/java/com/company/logicstic/dto/PagedResponse.java:7-21)

### 7. Mark notifications read response

~~~json
{"success":true,"code":"OK","message":"Notifications marked as read","data":0,"errors":[],"meta":{"timestamp":"<ISO-8601>","path":"/api/notifications/mark-all-read","requestId":"<request-id>"}}
~~~

data là số notification được service cập nhật; 0 chỉ giá trị mẫu. (nguồn: src/main/java/com/company/logicstic/controller/NotificationController.java:46-50)

### 8. Error envelope

~~~json
{"success":false,"code":"<error-code>","message":"<message>","data":null,"errors":[{"field":"name","code":"<validation-code>","message":"<field-message>","params":{}}],"meta":{"timestamp":"<ISO-8601>","path":"<request-path>","requestId":"<request-id>"},"params":{}}
~~~

Error code/message phụ thuộc exception thực tế. (nguồn: src/main/java/com/company/logicstic/dto/ApiResponse.java:7-24; src/main/java/com/company/logicstic/dto/ApiError.java:3-11; src/main/java/com/company/logicstic/exception/GlobalExceptionHandler.java:23-45)

### 9. Load timeline

~~~http
GET /api/loads/{loadId}/timeline
~~~

Response DTO là LoadTimelineResponse bọc trong ApiResponse; chi tiết trường cần đọc DTO tại route. (nguồn: src/main/java/com/company/logicstic/controller/LoadTimelineController.java:19)

### 10. AI dispatch

~~~http
POST /api/ai/dispatch/runs
Content-Type: application/json
~~~

Request/response types được khai báo tại controller; body fields không được suy đoán ở đây. (nguồn: src/main/java/com/company/logicstic/ai/dispatch/AiDispatchController.java:27-61)

## 5.6 Kết luận đối chiếu số endpoint

| Nguồn đếm | Số liệu | Giới hạn |
|---|---:|---|
| Annotation source hiện tại | 200 handlers / 203 path declarations / 201 unique method-path pairs | Hai cặp trùng; chưa xác thực runtime |
| OpenAPI snapshot local ngày 2026-10-06 | 140 paths / 176 operations | deployment_verified=false, source commit cũ |
| Bản nháp | 175 endpoint | KHÔNG XÁC MINH ĐƯỢC |


