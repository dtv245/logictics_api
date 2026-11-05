# Kế hoạch mở rộng hồ sơ tài xế

**Trạng thái:** Slice 0–7 hoàn tất; toàn bộ backend Driver Profile đã qua cổng `./mvnw test`.

**Ngày khảo sát:** 2026-10-30

**Phạm vi:** Backend `logictics_api`; triển khai tiếp theo theo thứ tự Slice 1–7 trong tài liệu này.

## 1. Mục tiêu và nguyên tắc

- Bổ sung hồ sơ vận hành, giấy tờ và thông tin nhân sự cho tài xế Việt Nam.
- Giữ nguyên tên, kiểu và ý nghĩa các trường hiện có của `EmployeeView`, `CreateEmployeeRequest` và các route `/api/drivers`; field mới chỉ được thêm theo hướng tương thích ngược.
- Tài xế tiếp cận dữ liệu của chính mình qua `/api/driver/me/...`; mọi truy vấn self phải lấy `employeeId` từ principal đã xác thực trong tenant hiện tại, không nhận `driverId` tùy ý từ client.
- Mask số định danh, mã số thuế, mã BHXH/BHYT và số tài khoản trong View thông thường. Chỉ ADMIN có endpoint riêng trả số đầy đủ. Không đưa giá trị nhạy cảm vào log, lỗi hoặc Postman environment.
- Controller chỉ xử lý HTTP; kiểm tra nghiệp vụ và transaction đặt trong service; không trả JPA entity; mọi list dùng `Pageable`, giới hạn page size, whitelist sort và có thứ tự ổn định.
- Dùng Flyway migration dạng SQL; không sửa migration đã áp dụng; sau mỗi migration phải để Hibernate `ddl-auto: validate` xác nhận mapping.

## 2. Khảo sát repository hiện tại

| Chủ đề | Bằng chứng trong working tree | Hệ quả thiết kế |
|---|---|---|
| Stack | `pom.xml`: Spring Boot 4.1.1, Java 21, JPA, Validation, Flyway, MapStruct, Lombok, Testcontainers PostgreSQL | Không cần thêm thư viện cho các slice đã nêu. |
| Migration | `application.yml`: Flyway ở `classpath:db/migration/tenant`; `ddl-auto: validate`. Có `V1`–`V44` trong cây hiện tại; `V41`–`V44` đang là file chưa được Git theo dõi. Tenant migration service chạy cùng location cho từng datasource tenant. | Bản kế hoạch tạm dự kiến bắt đầu ở V45; kiểm tra lại danh sách migration ngay trước mỗi slice để tránh đụng thay đổi khác. Không sửa V1 hoặc migration đã có. |
| Tenant/schema | `TenantRoutingDataSource` chọn datasource theo tenant; entity nghiệp vụ hiện dùng `schema = "public"` trong từng database tenant. | Bảng hồ sơ đặt trong database tenant hiện hành, FK không đi chéo tenant. |
| ID và audit | ID entity là UUID. `BaseAuditableEntity` có `created_at`, `created_by`, `last_modified_at`, `last_modified_by`; JPA auditing dùng UTC và principal. Không có soft-delete chung. | Entity mới dùng UUID và kế thừa audit base. Lịch sử cần giữ phải được vô hiệu hóa/đóng trạng thái, không tự giả định có soft delete. |
| Driver hiện có | `DriverController`/`DriverService` làm việc với `Employee`; role `DRIVER` được backend ép khi tạo/cập nhật. Working tree có thay đổi chưa commit ở các file này; phải giữ nguyên và mở diff trước khi sửa. | Không viết lại API cũ. Các endpoint profile mới kiểm tra role DRIVER của employee mục tiêu. |
| Phân quyền | `/api/drivers/**` hiện chỉ cho `ADMIN`, `DISPATCHER`; `/api/driver/me/payslips` yêu cầu authenticated. `CurrentUserService` ánh xạ email principal sang employee trong tenant. | Thêm matcher self trước matcher quản trị; self service xác minh role `DRIVER`, và mọi thao tác self chỉ dùng employee của principal. ADMIN xem dữ liệu nhạy cảm đầy đủ qua route riêng; DISPATCHER chỉ thấy bản mask. |
| Lỗi | `ApiException` cùng `GlobalExceptionHandler` chuẩn hóa validation 400, forbidden 403, not found 404 và conflict 409; vi phạm unique constraint có mapping chung. | Validation DTO cho lỗi đầu vào; service ném exception hiện có; unique index là bảo vệ cuối trước race condition và được đổi thành 409. |
| Phân trang | `PaginationValidator` dùng mặc định 1-based, page size 20, tối đa 100; có stable sort helper. `QuerySortPolicy` whitelist tên field. `/api/drivers` đang trả `ApiResponse<PagedResponse<EmployeeView>>`. | API list mới dùng convention 1-based và envelope hiện hành; thêm sort whitelist theo resource, không nối trực tiếp `orderBy` vào query. |
| Bằng lái | `DriverLicense` và bảng `driver_licenses` đã có từ baseline V1: license number/class, endorsements, country/region, ngày cấp/hết hạn, hạn khám sức khỏe legacy, status, một `document_id`, reminder fields và unique `(employee_id, license_number, issuing_country)`. | Slice 1 mở rộng đúng entity/bảng này, giữ `expires_at` và cột legacy. API dùng `expiryDate` nhưng giữ kiểu timestamp của cột cũ; kiểm tra bắt buộc expiry sau issue. `license_number` được mã hóa AES-GCM và tìm trùng bằng HMAC blind index. |
| Tài liệu | `Document.type` là text; chưa có `expiryDate`. `DocumentController` có GET list/detail và DELETE; list lọc được theo `employeeId`. `DocumentView` có `employeeId`, `blobPath`, `blobContainer`. Không tìm thấy endpoint tạo/upload tài liệu trong controller hiện tại. | V45 thêm `expiry_date DATE`; type tiếp tục là text, không có PostgreSQL enum. Slice 1 chỉ chấp nhận loại phù hợp với tài liệu liên kết (GPLX/DRIVER_LICENSE, HEALTH_CHECK, CERTIFICATION). Dùng document UUID/FK hiện có, không tạo luồng upload mới. |
| Phân công/xe | `TripDriverAssignment` giữ lịch sử bằng `effectiveTo`; `Truck` đã có `mainDriver` và `secondaryDriver`. | `currentTripId` lấy từ assignment còn hiệu lực và trip đang chạy; không lưu bản sao. `defaultVehicleId` ưu tiên suy ra từ truck-driver association, chỉ lưu riêng nếu xác nhận đó là “xe mặc định” độc lập. |
| Vi phạm/tai nạn | Đã có `HosViolation`, `DriverBehaviorEvent`, `AccidentReport`; `AccidentReport` đã liên kết driver, trip, status, type, ngày, chi phí ước tính. Hai entity HOS/behavior là dữ liệu từ provider; `AccidentReport` đã có audit base. | Tái sử dụng `AccidentReport` cho API accidents. Không gộp raw telemetry/HOS thành vi phạm đã xác nhận; chỉ tạo `driver_violations` cho biên bản vi phạm nghiệp vụ còn thiếu. |
| Notification/scheduler | Không thấy `@EnableScheduling`/`@Scheduled`. `Notification` hiện không có recipient/employee ID; `NotificationService` chỉ list/detail/mark-read, chưa có lệnh tạo notification. | Slice 7 chỉ ghi log INFO cho hạn 30/7 ngày như yêu cầu fallback; log dùng record ID/type/days, tuyệt đối không chứa số giấy tờ hay tên. Chưa tuyên bố có notification cá nhân. |
| Postman | Có collection tổng và ba collection Part; `scripts/build_full_suite.py` tạo lại collection tổng và Part 1–3. | Slice liên quan cập nhật generator/collection nguồn rồi build lại; dùng environment variables hiện có, không ghi bearer token vào collection. Newman là cổng xác minh của từng slice. |
| Worktree | Có nhiều thay đổi chưa commit, gồm `DriverController`, `SecurityConfig`, helper phân trang, Postman, migration V41–V44, `.ai-workflow/PROJECT_MEMORY.md` và các file khác. | Không reset/checkout/ghi đè file ngoài phạm vi. Mọi lần chỉnh file đang dirty phải xem diff trước; không commit/push. |

## 3. Mô hình dữ liệu dự kiến

Các tên bảng mới bên dưới dùng dạng số nhiều theo schema hiện tại (`driver_licenses`, `driver_hos_statuses`, `documents`). Ngoại lệ `driver_licenses` được giữ nguyên vì đã tồn tại. Tất cả FK tài xế dùng `employee_id` trỏ đến `employees.id`; entity mới kế thừa audit base.

### Slice 1 — Bằng lái, khám sức khỏe, chứng chỉ

- **Mở rộng `driver_licenses`:** giữ `license_number` nhưng mã hóa AES-256-GCM; thêm HMAC blind index theo employee/country để kiểm tra trùng mà không lưu số rõ, `issued_place`, `remaining_points`, `front_image_document_id`, `back_image_document_id`. Giữ `document_id` làm liên kết scan legacy. API View biểu diễn `active` từ `status` hiện có. `licenseClass` là String tự do để chấp nhận hạng cũ/mới. Điểm bằng nullable, nếu có thì 0–12.
- **`driver_health_checks`:** `id`, `employee_id`, `checked_date`, `expiry_date`, `facility`, `conclusion`, `document_id`, `status`, audit fields. Lưu từng lần khám; không thay lịch sử bằng một cột hạn duy nhất.
- **`driver_certifications`:** `id`, `employee_id`, `type`, `name`, `issued_date`, `expiry_date`, `issuer`, `document_id`, `status`, audit fields. `type` có nhóm nghiệp vụ vận tải/an toàn/hàng nguy hiểm/khác, nhưng lưu code string để mở rộng.
- Giữ `medical_cert_expires_at` cũ trên license để tương thích dữ liệu; thông tin sức khỏe mới lấy từ `driver_health_checks`, không đồng bộ hai nguồn tự động.
- V45 thêm `documents.expiry_date` kiểu DATE. `Document.type` vẫn là text; các code nghiệp vụ hiện tại/dự kiến gồm `CCCD`, `GPLX`, `HEALTH_CHECK`, `CONTRACT`, `RESIDENCE_PROOF`, `CERTIFICATION`, `DRIVER_LICENSE`, `DRIVER_LICENSE_FRONT`, `DRIVER_LICENSE_BACK`, không dùng PostgreSQL enum. Upload tài liệu chưa nằm trong scope vì module chưa có write/upload endpoint.

### Slice 2 — Thông tin cá nhân, liên hệ khẩn cấp

- **`driver_profiles`:** `employee_id` unique (1–1), `date_of_birth`, `gender`, `nationality`, `marital_status`, `education_level`, mã CCCD/MST mã hóa application-layer cùng HMAC blind index unique, ngày/nơi cấp CCCD, `avatar_url`, `permanent_address`, `temporary_address`, `hometown`, audit fields.
- Không thêm `householdBookNumber`: sổ hộ khẩu giấy đã bỏ và yêu cầu hiện tại đã chuyển sang thường trú/tạm trú.
- **`driver_emergency_contacts`:** `employee_id`, `full_name`, `relationship`, `phone_number`, `address`, `is_primary`, `active`, audit fields; unique partial index bảo đảm tối đa một liên hệ primary đang hoạt động trên mỗi tài xế.

### Slice 3 — Hợp đồng và bảo hiểm

- **`employment_contracts`:** `employee_id`, `contract_number`, `contract_type` (`PROBATION`, `FIXED_TERM`, `INDEFINITE`), `signed_date`, `effective_date`, `expiry_date`, `document_id`, `termination_date`, `termination_reason`, `active`, audit fields. Hợp đồng vô thời hạn không có expiry date; contract hữu hạn cần expiry. Không lưu bản sao allowances trong slice này; lương/phụ cấp tiếp tục thuộc các chính sách/payroll hiện có cho đến khi có contract nghiệp vụ riêng.
- **`driver_insurances`:** `employee_id`, số BHXH/BHYT đã mã hóa AES-GCM với HMAC index (không unique vì lưu lịch sử), `registered_clinic`, `participation_start_date`, `participation_end_date`, trạng thái BHXH/BHYT/BHTN, `insured_salary`, `health_insurance_expiry_date`, `note`, `active`, audit fields. Là 1–N để giữ lịch sử.

### Slice 4 — Người phụ thuộc và tài khoản ngân hàng

- **`driver_dependents`:** `employee_id`, `full_name`, `relationship`, `date_of_birth`, CCCD/MST mã hóa AES-GCM và HMAC blind index, `deduction_start_date`, `deduction_end_date`, `active`, audit fields. FK restrict và DELETE chỉ vô hiệu hóa để giữ căn cứ giảm trừ thuế.
- **`driver_bank_accounts`:** `employee_id`, `bank_name`, số tài khoản mã hóa AES-GCM và HMAC blind index, `account_holder_name`, `is_primary`, `active`, audit fields; unique partial index bảo đảm tối đa một tài khoản primary đang hoạt động. Employee FK restrict giữ lịch sử payroll.
- Số định danh, mã số thuế và tài khoản ngân hàng mặc định trả dạng mask; cleartext chỉ nằm trong request ghi hoặc endpoint ADMIN riêng.

### Slice 5 — Trạng thái vận hành

- **`driver_operational_statuses`:** `employee_id` unique (1–1), `availability`, `last_known_latitude`, `last_known_longitude`, `last_known_at`, `leave_balance_days`, audit fields; `default_vehicle_id` là xe mặc định độc lập, nullable và FK tới `trucks` (`ON DELETE SET NULL`).
- `availability` là code mở có validation whitelist hiện tại: `AVAILABLE`, `ON_TRIP`, `OFF_DUTY`, `ON_LEAVE`, `SUSPENDED`; không thay `Employee.status`.
- Không lưu `current_trip_id`: tính từ các assignment đang hiệu lực theo thời gian; chỉ trả trip ID khi có đúng một trip hiện hành không mơ hồ. `leave_balance_days` nullable, không tự tính khi chưa có nguồn HR/leave đáng tin cậy.
- `GET/PUT /api/drivers/{driverId}/operational-status`; driver chỉ GET `/api/driver/me/operational-status` theo principal, không tự sửa trạng thái. Filter `availability` là query tùy chọn mới trên `GET /api/drivers`; giữ nguyên `EmployeeView` và hành vi route cũ khi không truyền filter.

### Slice 6 — Vi phạm và tai nạn

- **Vi phạm nghiệp vụ:** `driver_violations` lưu biên bản được xác nhận: `employee_id`, `violation_date`, `violation_type`, `description`, `fine_amount`/currency, `trip_id`, `document_id`, `resolution_status`, `active`, audit fields. Employee FK RESTRICT; trip/document FK SET NULL. `hos_violations` và `driver_behavior_events` tiếp tục là feed riêng, không nhân đôi thành biên bản.
- **Tai nạn:** dùng `accident_reports` hiện có; không tạo `driver_accidents` trùng. `documentIds` được đọc từ liên kết `documents.accident_report_id`; request nhận một `documentId` để gắn thêm tài liệu. View profile không trả chữ ký/lời khai tài xế hoặc dữ liệu cá nhân cảnh sát.
- Trip gắn vi phạm/tai nạn phải được phân công cho driver tại thời điểm sự kiện; xe tai nạn phải khớp xe của trip nếu trip có xe. Bản ghi lịch sử không bị xóa vật lý: violation DELETE đặt `active=false`, accident DELETE đổi `status=CANCELLED`; FK tai nạn/vi phạm giữ employee khỏi bị hard-delete.

### Slice 7 — Tổng hợp và cảnh báo hết hạn

- `GET /api/drivers/{driverId}/profile-summary`: counts theo nhóm; hạn gần nhất; trạng thái license/health-check/contract/health-insurance; không trả cleartext nhạy cảm.
- Tất cả View có hạn trả thêm `expiryStatus`: `VALID`, `EXPIRING_SOON`, `EXPIRED`. Mặc định cảnh báo trong 30 ngày, cấu hình qua property feature riêng; status tính trên ngày nghiệp vụ `Asia/Ho_Chi_Minh`, không lưu lại trong DB.
- `GET /api/drivers/expiring?days=30&type=` phân trang, size tối đa 100, type whitelist; ngày hiện tại và ngưỡng không được tin từ client ngoài range cho phép.
- Job chạy hàng ngày phát log INFO cho mốc 30/7 ngày đến hạn; bỏ qua hoặc chống lặp theo record/type/expiry date. Nếu trước thời điểm code có notification model nhận người dùng và create flow thật, đánh giá tích hợp lại trong slice này.

## 4. API và quyền dự kiến

### Route quản trị tài xế

Mọi list dùng 1-based `page`/`pageSize`, mặc định 20, tối đa 100; GET detail xác minh bản ghi thuộc driver trên path. POST/PUT dùng Request DTO có Bean Validation và kiểm tra ngày chéo ở service. DELETE route mới vô hiệu hóa bản ghi để giữ lịch sử.

| Slice | Route mới |
|---|---|
| 1 | `/api/drivers/{driverId}/licenses[/{id}]`; `/health-checks[/{id}]`; `/certifications[/{id}]` — GET list/detail, POST, PUT, DELETE/inactivate |
| 2 | `/api/drivers/{driverId}/profile` — GET/PUT; `/emergency-contacts[/{id}]` — CRUD/inactivate |
| 3 | `/contracts[/{id}]`; `/insurances[/{id}]` — CRUD/inactivate |
| 4 | `/dependents[/{id}]`; `/bank-accounts[/{id}]` — CRUD/inactivate |
| 5 | `/api/drivers/{driverId}/operational-status` — GET/PUT; thêm query `availability` vào danh sách driver |
| 6 | `/violations[/{id}]`; `/accidents[/{id}]` — GET list/detail, POST, PUT/resolve/inactivate theo lifecycle |
| 7 | `/api/drivers/{driverId}/profile-summary`; `/api/drivers/expiring?days=&type=&page=&pageSize=` |

### Route self-service

- Thêm GET dưới `/api/driver/me/...` cho profile, licenses, health checks, certifications, contracts, insurances, dependents, bank accounts và emergency contacts; self chỉ thấy dữ liệu của employee gắn principal.
- Bank account self-view chỉ trả ngân hàng, chủ tài khoản và số đã mask; không có endpoint tự đổi tài khoản trong phạm vi này.
- Không nhận `driverId` trong self-service route. Nếu employee mapping thiếu hoặc user không có role DRIVER, trả lỗi theo exception/security contract hiện có.
- Slice 1 đã thêm GET `/api/driver/me/licenses`, `/health-checks` và `/certifications`; driver A không thể truy cập hồ sơ driver B qua route quản trị.
- Slice 4 thêm GET `/api/driver/me/dependents` và `/bank-accounts`; các mã định danh trong dependent view và số tài khoản luôn được mask. Driver không có route sửa thông tin payroll/thuế hoặc tài khoản ngân hàng.
- Slice 5 thêm GET `/api/driver/me/operational-status`; driver không thể ghi trạng thái hay truy cập driver khác.

### Quyền

| Nhóm | Quyền dự kiến |
|---|---|
| ADMIN | CRUD hồ sơ; cleartext nhạy cảm qua endpoint riêng; sensitive read được audit bằng actor/record IDs, không log giá trị. |
| DISPATCHER | Đọc/quản lý license, certification, availability và trạng thái vận hành dạng mask; không được đọc CCCD/MST/BHXH/BHYT/bank cleartext. |
| PAYROLL/PAYROLL_MANAGER | Đọc/quản lý hợp đồng, bảo hiểm, dependents và bank theo nhu cầu payroll; dữ liệu số vẫn mask trừ ADMIN. |
| DRIVER | GET dữ liệu của chính mình qua `/api/driver/me/...`; không được truyền ID tài xế khác hoặc sửa thông tin HR/compliance trong scope này. |

Các route ADMIN cleartext là endpoint riêng theo nhóm dữ liệu (không có cờ `includeSensitive` trên View dùng chung). Response đặt `Cache-Control: no-store`; response, exception và log không chứa giá trị đầu vào.

## 5. Quy tắc validation, dữ liệu và hiệu năng

- Ngày cấp phải không sau ngày hết hạn; ngày khám/chứng chỉ/hợp đồng phải hợp lệ; kết thúc hợp đồng/kỳ bảo hiểm không trước ngày bắt đầu; hạn khấu trừ người phụ thuộc phải hợp lệ.
- Chuẩn hóa số điện thoại/mã trước unique check; vẫn dựa vào unique index và xử lý 409 để chống ghi đồng thời. Không dùng số CCCD/mã bảo hiểm cleartext làm sort key.
- FK tới employee/document/trip/truck được kiểm tra tồn tại và cùng tenant; document gắn giấy tờ tài xế phải cùng employee và đúng document type.
- Index tối thiểu: `(employee_id, created_at/id)` cho list theo driver; index ngày hết hạn cho expiring; unique partial index cho primary contact/account; giữ unique index GPLX hiện có. Chỉ thêm index dựa trên query thực tế.
- Repository dùng `Pageable`, projection hoặc entity graph cho ToOne; không fetch-join collection cùng page; service map View trong transaction; tránh N+1 và truy vấn theo từng dòng.
- Các View ngày lịch mới dùng `LocalDate`/SQL `DATE`; event/audit dùng `OffsetDateTime`. Riêng `driver_licenses` tiếp tục giữ kiểu timestamp legacy cho tới khi kiểm tra dữ liệu và thống nhất conversion.
- Schema mới không đặt SQL enum cho loại giấy phép/chứng chỉ/tài liệu. ContractType có tập code đã nêu; các status/availability dùng string + validation.

## 6. Kế hoạch migration

| Migration dự kiến | Thay đổi |
|---|---|
| V45 (đã thêm) | Mở rộng `driver_licenses` và `documents`; tạo `driver_health_checks`, `driver_certifications`; FK restrict giữ lịch sử, document FK set null, check/index constraints. |
| V46 (đã thêm) | `driver_profiles`, `driver_emergency_contacts`; unique employee, unique HMAC CCCD/MST và partial primary index. Profile/liên hệ cá nhân cascade khi xóa employee; bảng compliance giữ FK restrict theo lựa chọn 2B. |
| V47 (đã thêm) | `employment_contracts`, `driver_insurances`; chỉ mục số bảo hiểm không unique để hỗ trợ lịch sử. |
| V48 (đã thêm) | `driver_dependents`, `driver_bank_accounts`; audit fields, FK RESTRICT, encrypted identifier columns/HMAC indexes, deduction date check và partial unique primary-account index. |
| V49 (đã thêm) | `driver_operational_statuses`, unique employee, FK truck, check availability/location/leave balance và index availability. |
| V50 (đã thêm) | `driver_violations`; check mức phạt/tiền tệ/trạng thái, FK employee RESTRICT, trip/document SET NULL và index theo driver/ngày. Không đổi `accident_reports` hiện hữu. |
| V51 | Chỉ thêm nếu scheduler cần bảng chống gửi lặp; nếu chỉ INFO log thì không tạo bảng notification mới. |

Đây là số migration dự kiến theo working tree hiện tại, không phải đặt chỗ cố định. Trước khi tạo từng file, chạy `rg --files src/main/resources/db/migration/tenant` và xác nhận chưa có phiên bản cao hơn trong dirty changes. Mỗi migration cần kiểm tra upgrade từ tenant schema có dữ liệu; không sửa checksum migration đã phát hành.

## 7. Test, Postman và phase gate

Mỗi Slice 1–7 hoàn tất một vertical slice gồm migration/entity/repository/DTO/mapper/service/controller/security/test/Postman/docs; khi `./mvnw test` xanh thì tự chuyển slice kế tiếp.

| Slice | Test trọng tâm bổ sung |
|---|---|
| 1 | GPLX trùng số trong tenant → 409; ngày cấp/hết hạn; hạng cũ/mới; health-check/cert CRUD; document FK/type/employee; 401/403/404; mask GPLX; Postgres migration/FK/unique. |
| 2 | Một profile trên mỗi employee; ngày sinh tương lai; emergency contact chỉ một primary kể cả concurrent; mask CCCD/MST; ADMIN endpoint đầy đủ; driver A không đọc/sửa driver B. |
| 3 | Contract type và các mốc ngày; lịch sử bảo hiểm; mask BHXH/BHYT; PAYROLL/ADMIN/DRIVER ownership; employee/doc not found. |
| 4 | Phụ thuộc/kỳ giảm trừ; chỉ một bank primary; mask ID/MST/account; cleartext chỉ ADMIN endpoint; race/unique constraints. **Đã xanh:** 2 service unit tests + 10 PostgreSQL/Flyway/MockMvc integration tests. |
| 5 | Availability whitelist/filter; `currentTripId` suy ra assignment; không lưu trip kép; driver self read-only; page/sort/query count. |
| 6 | Violation/accident lifecycle; liên kết trip/document; tái sử dụng AccidentReport; phân biệt HOS feed; tenant/driver ownership. |
| 7 | Ngưỡng 30/7 ngày và biên 0/30/31 ngày; type whitelist; phân trang; summary không N+1; scheduler log không có PII và chống lặp. |

Trong mỗi slice, chạy test unit service, MockMvc/API permission cases (200/201/400/401/403/404/409), test integration PostgreSQL cho constraints/migrations và test mask. Sau đó chạy `./mvnw test`, Newman collection và cập nhật docs. Không chạy Maven/Newman trong Slice 0 vì chưa có code nghiệp vụ thay đổi.

Postman được cập nhật trong `scripts/build_full_suite.py` và collections Part tương ứng, sau đó build lại `LogisticsX.postman_collection.json` + Part 1–3; dùng biến environment (không lưu secret/token), capture UUID từ response, thêm negative/security cases và xác minh bằng Newman.

Trước mọi source edit, chạy GitNexus `impact` theo AGENTS.md và cảnh báo HIGH/CRITICAL; `UNKNOWN`/lỗi tool chưa phải kết quả an toàn. Sau thay đổi, chạy `detect_changes --scope all` trước bất kỳ commit nào; không commit/push trong nhiệm vụ này.

## 8. Cơ sở pháp lý cần đối chiếu khi implement

- [Luật Trật tự, an toàn giao thông đường bộ 36/2024/QH15](https://vanban.chinhphu.vn/?classid=1&docid=211194&pageid=27160&typegroupid=) có hiệu lực 01-01-2025; Điều 58 quy định hệ thống điểm GPLX 12 điểm. Hạng GPLX giữ dạng String để tiếp nhận giấy phép cũ/mới và thay đổi pháp lý.
- [Thông tư 36/2024/TT-BYT](https://vanban.chinhphu.vn/?docid=211769&pageid=27160) có hiệu lực 01-01-2025, quy định tiêu chuẩn/khám sức khỏe, khám định kỳ lái xe ô tô và dữ liệu sức khỏe. Lưu kết luận/hạn theo giấy tờ thực tế, không tự suy ra kỳ hạn chung.
- [Luật Bảo vệ dữ liệu cá nhân 91/2025/QH15](https://vanban.chinhphu.vn/?classid=1&docid=214590&pageid=27160&typegroup=) có hiệu lực 01-01-2026. Trước triển khai PII, rà soát mục đích thu thập, phạm vi truy cập, lưu giữ và xử lý dữ liệu theo văn bản hiện hành.
- [Luật Bảo hiểm xã hội 41/2024/QH15](https://vanban.chinhphu.vn/?classid=1&docid=211199&orggroupid=1&pageid=27160) có hiệu lực 01-07-2025. Trạng thái và thông tin BHXH/BHYT/BHTN cần được HR/payroll xác nhận theo chính sách áp dụng; API không thay thế nghiệp vụ kê khai bảo hiểm.
- Kiểm tra lại văn bản hiệu lực tại ngày triển khai từng slice; luật/circular có thể được sửa đổi sau ngày khảo sát.

## 9. Quyết định đã chốt và kết quả Slice 1

- **PII encryption: lựa chọn 1B.** Mã GPLX được mã hóa ở application layer bằng AES-256-GCM; HMAC-SHA256 blind index phục vụ so trùng. Kết luận khám sức khỏe cũng mã hóa. Dùng các biến secret-manager/runtime `DRIVER_PII_ACTIVE_KEY_ID`, `DRIVER_PII_ENCRYPTION_KEYS` (danh sách `key-id:base64` phân tách bằng `;`) và `DRIVER_PII_INDEX_KEY`; khóa AES và index key là các khóa 32 byte độc lập. Không đặt khóa thật trong source, docs hay Postman.
- Khi xoay AES key, nạp cả key cũ và mới, chuyển `ACTIVE_KEY_ID`, giữ key cũ tới khi backfill đã mã hóa lại toàn bộ dữ liệu và xác minh. Index key ổn định để giữ unique HMAC; đổi index key cần một đợt bảo trì có kiểm soát và reindex toàn bộ trước khi bật ghi mới.
- Dữ liệu GPLX cũ được backfill sau Flyway cho datasource đơn và sau migration của từng tenant. Môi trường có bản ghi cũ phải provision đủ key trước khi rollout; không thể giải mã cleartext legacy nếu deploy thiếu cấu hình.
- **Compliance hard-delete: lựa chọn 2B.** FK `driver_licenses.employee_id` và các bảng Slice 1 dùng `ON DELETE RESTRICT`; `DELETE /api/drivers/{id}` trả 409 `DRIVER_HAS_COMPLIANCE_RECORDS` khi còn lịch sử. DELETE license/khám/chứng chỉ chỉ chuyển inactive, không xóa vật lý.
- **Slice 1 đã triển khai:** `GET/POST /api/drivers/{driverId}/licenses`, `GET/PUT/DELETE /licenses/{licenseId}`, ADMIN-only `GET /licenses/{licenseId}/sensitive`; CRUD/inactivate tương ứng cho `/health-checks` và `/certifications`. Self-service GET dùng `/api/driver/me/licenses`, `/health-checks`, `/certifications`. List dùng page 1-based, mặc định 20/tối đa 100 và sort whitelist. `DocumentView` có thêm `expiryDate` theo kiểu additive.
- Postman generator và collection tổng/Part1/Part3 đã có luồng CRUD bằng dữ liệu tổng hợp. Biến `driverAccessToken` tùy chọn phục vụ route self; không lưu số GPLX hay token trong environment. Newman chưa cài trong môi trường hiện tại; localhost API không chạy để Newman smoke test.
- Test mục tiêu Slice 1 xanh: 11 test (4 PostgreSQL/Flyway/API, 4 mã hóa, 3 service). Toàn suite xanh: 828 test, 0 failure, 0 error, 232 skipped.

### Slice 2 — trạng thái triển khai

- V46 tạo `driver_profiles` (1–1) và `driver_emergency_contacts` (1–N), audit fields, FK, unique HMAC CCCD/MST và partial unique index cho tối đa một liên hệ chính đang hoạt động.
- Mã CCCD/MST lưu AES-256-GCM với cùng bộ khóa managed của Slice 1, AAD tách theo loại trường, HMAC-SHA256 tenant-wide để kiểm tra trùng. View thường che số; chỉ ADMIN gọi `GET /api/drivers/{driverId}/profile/sensitive`, có `Cache-Control: no-store`. Mask trả về có thể gửi lại trong PUT để giữ nguyên giá trị cũ; omission/null cũng giữ PII hiện có. Không cho phép đổi giá trị bằng chuỗi mask khác.
- Thêm `GET/PUT /api/drivers/{driverId}/profile` (PUT upsert); self chỉ đọc tại `GET /api/driver/me/profile`. Thêm paged CRUD/inactivate `/api/drivers/{driverId}/emergency-contacts`; list chỉ gồm contact active. Tài xế tự đọc tại `GET /api/driver/me/emergency-contacts`; thao tác ghi chỉ qua route quản trị và khóa employee để tuần tự hóa cập nhật primary.
- Quyền: ADMIN/DISPATCHER quản lý route driver; ADMIN-only cleartext profile; DRIVER chỉ đọc route `/api/driver/me/...` dựa trên employee ánh xạ từ principal. Tài khoản DRIVER không được truy cập route driver khác.
- Quyết định xóa: profile và liên hệ khẩn cấp là dữ liệu cá nhân, FK dùng `ON DELETE CASCADE`; compliance records GPLX/khám/chứng chỉ vẫn restrict theo 2B để giữ lịch sử.
- Postman generator thêm upsert/get profile, ADMIN sensitive view, emergency contact CRUD và self GET tùy chọn. CCCD/MST synthetic được tạo trong local Postman variables mỗi request, không lưu vào environment.
- Slice 2 kiểm thử: 10 unit test, 6 integration test PostgreSQL/Flyway/API đều xanh; toàn bộ `./mvnw test`: 836 test, 0 failure, 0 error, 232 skipped. `git diff --check` vẫn báo một blank line EOF có sẵn trong `LarkBaseService.java`, file ngoài slice này nên được giữ nguyên.
- Newman chưa cài; API local không hoạt động để chạy collection. Generator chạy thành công và JSON của collection tổng, environment, Part 1–3 đều parse hợp lệ.

### Slice 3 — trạng thái triển khai

- V47 tạo `employment_contracts` và `driver_insurances`. FK cả hai bảng tới employee dùng RESTRICT theo lựa chọn 2B; hợp đồng có document FK SET NULL, service chỉ chấp nhận tài liệu CONTRACT/EMPLOYMENT_CONTRACT thuộc cùng tài xế. Employee delete service kiểm tra thêm hai bảng và trả cùng lỗi compliance 409.
- `GET/POST /api/drivers/{driverId}/contracts`, `GET/PUT/DELETE /contracts/{contractId}`; DELETE chỉ vô hiệu hóa. Quy tắc: số hợp đồng unique tenant-wide, signed date không sau effective date, hợp đồng vô thời hạn không có expiry, loại hữu hạn cần expiry, termination cần ngày không trước effective date và lý do.
- `GET/POST /api/drivers/{driverId}/insurances`, `GET/PUT/DELETE /insurances/{insuranceId}`; DELETE chỉ vô hiệu hóa. Số BHXH/BHYT mã hóa application-layer và HMAC index không unique để cho phép nhiều kỳ lịch sử. View thường và driver self che số; chỉ ADMIN có GET `/insurances/{insuranceId}/sensitive` với no-store.
- Driver self read-only qua `GET /api/driver/me/contracts` và `GET /api/driver/me/insurances`. Quyền quản lý: ADMIN/PAYROLL/PAYROLL_MANAGER; route sensitive chỉ ADMIN; DRIVER chỉ xem employee lấy từ principal.
- Postman bổ sung CRUD hợp đồng/bảo hiểm và self GET. Số bảo hiểm synthetic sinh trong local variables mỗi lần tạo, không lưu trong environment.
- Slice 3 kiểm thử: 4 service unit test, 8 PostgreSQL/Flyway/MockMvc integration test; toàn bộ ./mvnw test xanh: 842 test, 0 failure, 0 error, 232 skipped. Migration unique-index registry PostgreSQL xác nhận mapping contractNumber. Postman generator thành công và 5 file JSON hợp lệ; Newman chưa cài.

### Slice 4 — trạng thái triển khai

- V48 tạo `driver_dependents` và `driver_bank_accounts`, các bảng đều kế thừa audit fields, FK `ON DELETE RESTRICT`; migration có check kỳ giảm trừ và partial unique index cho tối đa một tài khoản ngân hàng chính đang hoạt động.
- `GET/POST /api/drivers/{driverId}/dependents`, `GET/PUT/DELETE /dependents/{dependentId}`; CCCD/MST người phụ thuộc mã hóa AES-256-GCM với HMAC blind index. Kỳ giảm trừ yêu cầu ngày bắt đầu nếu có ngày kết thúc; kết thúc không trước ngày bắt đầu. DELETE vô hiệu hóa bản ghi.
- `GET/POST /api/drivers/{driverId}/bank-accounts`, `GET/PUT/DELETE /bank-accounts/{accountId}`; số tài khoản mã hóa AES-256-GCM và có HMAC index không unique để lưu lịch sử. Khi đặt tài khoản mới làm primary, service khóa dòng employee và bỏ primary cũ trước khi lưu; DB partial unique index bảo vệ race condition. DELETE vô hiệu hóa bản ghi.
- View thường và self-service chỉ trả số CCCD/MST/tài khoản dạng `****1234`; ADMIN có endpoint riêng `GET /dependents/{id}/sensitive` và `/bank-accounts/{id}/sensitive` với `Cache-Control: no-store`. PAYROLL/PAYROLL_MANAGER được quản lý/đọc dữ liệu đã mask; DISPATCHER bị từ chối. Driver chỉ đọc `/api/driver/me/dependents` và `/api/driver/me/bank-accounts` của chính principal.
- Nếu tài xế còn dependent hoặc bank-account history, `DELETE /api/drivers/{id}` tiếp tục trả 409 `DRIVER_HAS_COMPLIANCE_RECORDS`; FK RESTRICT bảo vệ cả đường xóa employee trực tiếp. Đây là phần bổ sung vào quyết định 2B đã chốt.
- Slice 4 kiểm thử: 2 service unit tests và 10 PostgreSQL/Flyway/MockMvc integration tests xanh; toàn bộ `./mvnw test`: 847 tests, 0 failures, 0 errors, 232 skipped. Postman generator tạo collection tổng/Part 1–3 và environment, cả 5 JSON parse hợp lệ. Newman chưa cài nên chưa chạy collection.

### Slice 5 — trạng thái triển khai

- V49 tạo `driver_operational_statuses` riêng khỏi `Employee.status`, 1–1 với employee, lưu `availability`, xe mặc định tùy chọn, tọa độ/thời điểm cập nhật vị trí và số ngày phép nullable; audit fields, check constraints và index availability.
- Thêm `GET/PUT /api/drivers/{driverId}/operational-status` cho ADMIN/DISPATCHER; `GET /api/driver/me/operational-status` chỉ đọc theo principal. `currentTripId` được suy ra từ assignment đang hiệu lực và chỉ trả về khi không mơ hồ, không lưu trùng trong bảng.
- Thêm filter tùy chọn `availability` cho `GET /api/drivers`; nhánh có filter dùng truy vấn trang hóa riêng và vẫn trả `EmployeeView` không đổi. Availability whitelist gồm `AVAILABLE`, `ON_TRIP`, `OFF_DUTY`, `ON_LEAVE`, `SUSPENDED`; page size/sort theo convention hiện hành.
- Postman generator thêm upsert/get, filter availability và self GET tùy chọn. Không lưu token hoặc PII trong collection.
- Slice 5 test tập trung xanh (18 tests); toàn bộ `./mvnw test`: 852 tests, 0 failures, 0 errors, 232 skipped. Kiểm tra unique-index registry bao phủ constraint operational status. Postman generator/build JSON hợp lệ; Newman chưa cài. `git diff --check` chỉ báo blank line EOF đã có từ trước ở `LarkBaseService.java`, giữ nguyên file ngoài slice.

### Slice 6 — trạng thái triển khai

- V50 tạo `driver_violations` cho vi phạm đã được xác nhận; `hos_violations` và `driver_behavior_events` không bị dùng làm dữ liệu biên bản. Bảng có audit fields, check fine amount/currency/status, index driver/date, employee FK RESTRICT, trip/document FK SET NULL.
- Thêm paged CRUD/inactivate `/api/drivers/{driverId}/violations`: detail/list luôn kiểm tra employee sở hữu; trip phải được gán cho driver tại thời điểm vi phạm; document phải cùng driver và loại `DRIVER_VIOLATION` hoặc `VIOLATION_REPORT`. DELETE đặt `active=false`; không xóa lịch sử.
- Thêm paged CRUD/cancel `/api/drivers/{driverId}/accidents` trên entity/table `AccidentReport`/`accident_reports` đã tồn tại. Không sửa entity/feed rủi ro cao, không tạo bảng tai nạn trùng; trip phải thuộc driver tại thời điểm tai nạn, xe khớp trip nếu trip có xe. Tài liệu gắn qua `Document.accidentReport`; View trả danh sách document IDs đã liên kết, không lộ chữ ký/lời khai/cảnh sát. DELETE chỉ đổi status thành `CANCELLED`; FK restrict hiện hữu giữ accident history.
- Quyền qua matcher `/api/drivers/**` hiện có: ADMIN/DISPATCHER; DRIVER không truy cập được route tài xế bất kỳ, các ID trong service phải thuộc driver path. Danh sách dùng `Pageable` giới hạn/whitelist sort.
- Postman folder 07 có create/list/detail/update/deactivate violation và create/list/detail/update/cancel accident theo flow sau khi trip được phân công; dữ liệu minh họa không chứa PII.
- Slice 6 test tập trung xanh: 19 tests (13 PostgreSQL/Flyway/MockMvc, 6 unit), 0 failure/error; toàn bộ `./mvnw test`: 860 tests, 0 failures, 0 errors, 232 skipped. Newman chưa cài.

### Slice 7 — tổng hợp và cảnh báo hết hạn

- Thêm `expiryStatus` vào các View có ngày hết hạn: bằng lái (cả hạn chứng nhận sức khỏe ghi trên GPLX), khám sức khỏe, chứng chỉ, hợp đồng, bảo hiểm (kể cả ADMIN sensitive view) và tài liệu. Giá trị là `VALID`, `EXPIRING_SOON`, `EXPIRED`; ngày được tính theo `Asia/Ho_Chi_Minh`, ngưỡng mặc định 30 ngày và cấu hình qua `app.driver-profile.expiry-warning-days` / `DRIVER_PROFILE_EXPIRY_WARNING_DAYS`.
- Thêm `GET /api/drivers/{driverId}/profile-summary`. Trả số lượng hồ sơ cá nhân, bằng lái, khám sức khỏe, chứng chỉ, liên hệ khẩn cấp, hợp đồng, bảo hiểm, người phụ thuộc, tài khoản ngân hàng, trạng thái vận hành, vi phạm, tai nạn và tài liệu; kèm trạng thái hiện hành của GPLX, khám sức khỏe, hợp đồng và BHYT, danh sách tối đa 50 mục sắp hết hạn. Các count/status dùng một aggregate SQL, không N+1.
- Các count đếm cả bản ghi lịch sử/inactive. Trạng thái compliance chỉ xét record active/current; nếu có nhiều expiry date thì dùng ngày hết hạn lớn nhất của các record hiện hành. Hợp đồng vô thời hạn và record không có expiry date có `hasActiveRecord=true` với `expiryStatus=null`.
- Thêm `GET /api/drivers/expiring?days=30&type=&page=1&pageSize=20`; `days` hợp lệ 1–365, page size tối đa theo convention 100, sort ổn định cố định theo hạn/người lái/loại/ID. `type` whitelist: `LICENSE`, `LICENSE_MEDICAL_CERTIFICATE`, `HEALTH_CHECK`, `CERTIFICATION`, `CONTRACT`, `HEALTH_INSURANCE`, `DOCUMENT`. Truy vấn UNION phân trang ở PostgreSQL, không tải danh sách từng bảng vào heap; không trả số giấy tờ hoặc số bảo hiểm.
- Hai endpoint kế thừa matcher `/api/drivers/**`: chỉ `ADMIN`/`DISPATCHER`; tài xế không thể đọc tổng hợp của chính mình hoặc người khác qua route quản trị.
- Không có migration ở slice này vì chỉ bổ sung trường tính toán và truy vấn tổng hợp, không lưu thêm dữ liệu. Không có cơ chế tạo notification có recipient trong module hiện tại, nên job hằng ngày chạy lúc 08:00 giờ Việt Nam, quét đúng các ngày chạm ngưỡng 30/7 và ghi log INFO chỉ gồm tenant/type/UUID/ngày còn lại. Với multi-tenant, job lấy danh sách tenant active từ registry, dùng datasource tạm riêng cho từng tenant rồi đóng pool; không phụ thuộc request ThreadLocal hoặc tăng pool tenant chạy thường trực. Cấu hình: `app.driver-profile.expiry-scan.enabled` (mặc định true), `app.driver-profile.expiry-scan-cron` (`0 0 8 * * *`) và `app.driver-profile.time-zone` (`Asia/Ho_Chi_Minh`).
- Postman folder 07 thêm request xem summary và danh sách hết hạn. `scripts/build_full_suite.py` chạy thành công; collection tổng, Part 1–3 và environment đều là JSON hợp lệ. Newman chưa cài nên chưa chạy collection qua Newman.
- Slice 7: 7 test tập trung mới (6 unit + 1 PostgreSQL/Flyway/MockMvc integration) xanh. Toàn bộ `./mvnw -q test`: **867 tests, 0 failures, 0 errors, 232 skipped**.
- `git diff --check` chỉ báo blank line EOF đã có trước trong `src/main/java/com/company/logicstic/integration/lark/base/LarkBaseService.java`; file đó không thuộc Slice 7 và được giữ nguyên.
