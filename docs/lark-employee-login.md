# Đăng nhập Lark → Employee

## Phạm vi và nguyên nhân

Worktree đã có `LarkUserMapping`, `LarkUserMappingRepository`, truy vấn email dạng danh sách và migration V39. Implementation còn hai nhánh cho phép tiếp tục lookup email thay vì yêu cầu mapping bền vững: constructor không có repository mapping vẫn đăng nhập thành công, và danh sách email rỗng được fallback sang một truy vấn `Optional<Employee>` khác. Nhánh thứ hai làm quyết định auto-link phụ thuộc nhiều truy vấn thay vì một kết quả cardinality duy nhất. Test race trước đây chỉ giả lập lỗi unique bằng Mockito, chưa kiểm chứng rollback/reload trên PostgreSQL hoặc transaction bên ngoài.

File `application-nodb.yml` bị xóa tại thời điểm bắt đầu công việc; source không còn cấu hình loại các bean phụ thuộc JPA trong profile đó. Cấu hình `nodb` được khôi phục và kiểm tra cả ở Spring context và JAR thực thi.

## Flow và dữ liệu

1. Xác minh OAuth state; tenant đăng nhập lấy từ `LARK_DEFAULT_TENANT_ID`. Context/principal thuộc tenant khác bị từ chối trước khi gọi provider.
2. Đổi authorization code tại Lark và lấy hồ sơ từ phản hồi token; `user_info` được dùng nếu email chưa có. Access token của Lark chỉ dùng trong bộ nhớ cho lời gọi provider.
3. Tìm mapping theo `open_id` đã trim; nếu không có thì tìm theo `union_id` đã trim. Mapping có sẵn được ưu tiên kể cả email Lark thay đổi hoặc không có. Employee liên kết phải ACTIVE.
4. Nếu chưa có mapping, lấy `email` hoặc `enterprise_email` theo `effectiveEmail()`, trim và lowercase với `Locale.ROOT`. Một truy vấn `LOWER(TRIM(e.email))` trả về toàn bộ Employee trùng email và fetch role để dùng sau khi transaction đóng.
5. Chỉ auto-link khi kết quả có đúng một Employee ACTIVE và hồ sơ có ít nhất `open_id` hoặc `union_id`. Lưu mapping trước khi cấp JWT.
6. Ghi bằng `saveAndFlush` trong transaction `REQUIRES_NEW`. Nếu hai login cùng tạo mapping, unique constraint quyết định bên thắng; transaction bên thua rollback xong mới đọc lại mapping, kiểm tra ACTIVE và tiếp tục. Test thực xác nhận cả hai transaction bên ngoài vẫn commit, chỉ có một mapping.
7. JWT nội bộ lấy `employee_id`, email và role từ Employee; không tự tạo Employee hoặc role trong flow đăng nhập.

Các trường hợp không có email, không có Employee, nhiều Employee trùng email hoặc Employee không ACTIVE trả HTTP **403**, `code=LARK_EMPLOYEE_NOT_LINKED`. Mapping thiếu/inactive cũng trả cùng lỗi, không tự chuyển liên kết theo email. Nếu chưa có repository mapping hoặc thiếu cả open/union ID, login cũng fail closed. Trong `nodb`, Lark authentication bị tắt; health trả 200, route được bảo vệ trả 401 và endpoint authorize trả 503 `LARK_DISABLED`.

V39 `src/main/resources/db/migration/tenant/V39__create_lark_user_mappings.sql` được giữ nguyên: `id`, `employee_id`, `open_id`, `union_id`, `lark_user_id`, `email`, `created_at`, `updated_at`; unique riêng cho open/union ID và FK tới `employees`. Không thêm migration mới, không sửa checksum migration cũ. Không lưu access token, refresh token hoặc app secret trong bảng mapping.

## Các file

Thay đổi chính:

- `LarkAuthService.java`: bỏ singleton/email-only fallback, yêu cầu persistence và stable ID, trim JWT subject, chặn callback khi Lark bị tắt.
- `LarkUserMappingRepository.java`: transaction ghi mapping độc lập để rollback/reload khi tranh chấp unique.
- `LogisticApplication.java`, `NoDbComponentFilter.java`, `application-nodb.yml`: khởi động `nodb` với health và security, không tạo datasource/JPA/Flyway hoặc service phụ thuộc DB.
- `LarkAuthServiceTest.java`, `LarkEmployeeMappingPostgresTest.java`, `NoDbProfileTest.java`: test hành vi, callback HTTP, PostgreSQL, concurrency và profile.
- `SettledPaymentReportPostgresTest.java`, `scripts/verify_disposable_artifact.py`: bỏ assumption schema cuối V38; xác nhận tất cả migration hiện có đã apply, giữ các assertion dữ liệu tài chính và runtime.
- Tài liệu này và `.ai-workflow/PROJECT_MEMORY.md`: kết quả và bàn giao.

Đã kiểm tra, giữ implementation hiện tại: `LarkAuthenticationFilter`, `SecurityConfig`, `EmployeeRepository`, Employee entity, LarkUserMapping entity, LarkAuthClient và toàn bộ chuỗi Flyway V1–V39. Filter giữ subject/email/tenant/roles/employee_id trong principal Spring; SecurityConfig cho phép callback công khai nhưng bảo vệ API theo role/tenant.

## Kiểm thử

Test mục tiêu gồm mapping có sẵn, ưu tiên open trước union và mapping trước email, email chuẩn hóa/enterprise email, cardinality 0/1/nhiều, Employee inactive, null/empty/blank email, thiếu stable ID, repository thiếu, callback tenant mismatch, callback qua filter, race thực trên cả hai unique constraint và nâng V38 → V39 giữ nguyên Employee/checksum cũ.

Lệnh unit/profile:

```bash
./mvnw -q -Dtest=LarkAuthServiceTest,LarkAuthControllerTest,LarkSigningConfigurationTest,NoDbProfileTest test
```

Integration và toàn bộ `clean verify` dùng `scripts/run_remediation_tests.py` / `scripts/verify_backend_regression.py` trên server có label `logisticsx.disposable=true`; không dùng DB làm việc để chạy test. Integration classes được Surefire chạy theo quy tắc `*Test`, không có execution Failsafe riêng trong project POM. Những test cần `TASK_DB_URL` phải được chạy với DB disposable, không được coi skip là pass.

`pom.xml` không cấu hình Checkstyle hoặc SpotBugs. Đã chạy riêng plugin có sẵn:

```bash
./mvnw -q org.apache.maven.plugins:maven-checkstyle-plugin:3.3.1:check
./mvnw -q com.github.spotbugs:spotbugs-maven-plugin:4.8.2.0:check -Dspotbugs.maxHeap=512
```

Hai lệnh này FAIL ở phạm vi toàn repository: 22.212 lỗi Checkstyle trên 460 file theo bộ luật mặc định, 491 finding SpotBugs. Báo cáo XML và tổng hợp được giữ tại `/tmp/lark-static-evidence/`. Không tuyên bố lint/static analysis đạt; không đổi luật hoặc sửa hàng loạt file ngoài phạm vi để che kết quả. Không commit.

Kết quả cuối cùng: Maven `clean verify` chạy **593 test, gồm 228 PostgreSQL**, không failure/error/skip; **26 test Python PASS**. Lần full đầu có bốn lỗi fixture hardcode V38, đã sửa và chạy lại đạt. Kiểm tra artifact sau lần full phát hiện build metadata mặc định `UNKNOWN/UNVERIFIED`; đã đóng gói lại cùng source vừa test với identity tường minh, không sửa source và không chạy lại các test đã đạt. JAR mới và runtime thực đã được xác minh khớp source/migration.

Evidence:

- Targeted 51 test PASS: `/tmp/logisticsx-remediation-tests-on3kzg2a/maven.log`.
- Full suite: `/tmp/logisticsx-regression-vx7jrvm_/maven.log`, `target/surefire-reports/`.
- Report tổng hợp/manifest đóng gói cùng source: `/tmp/logisticsx-lark-repackage-auj_j_au/summary.json`, `artifact-manifest.json`.
- JAR thực thi: `/tmp/logisticsx-artifact-2a7r1_10/summary.json`. PostgreSQL apply/validate V1–V39 và JPA startup PASS; OpenAPI 140 paths, identity khớp, anonymous protected 401; `nodb` health 200/protected 401/không persistence; enabled auth thiếu signing key bị từ chối lúc startup.
- GitNexus impact: repository HIGH, login service LOW; detect_changes toàn dirty worktree CRITICAL. Index có giới hạn discovery của execution flows và git diff không bao quát file untracked; không coi graph là bằng chứng không có ảnh hưởng. Không commit/stage.

Source SHA: `493b1945bdadde6b71a7d525fac19974aafc1db3275ad22e09e98fbcb8ddc975`. JAR SHA: `6970994765a207289f0d37322c3a1e483605e3d1441769f0718b1a824399a689`. Đây là bằng chứng local; container backend đang chạy không được rebuild/restart/deploy trong công việc này. OAuth với provider Lark thật chưa được thực hiện vì cần tương tác đăng nhập/consent của chủ tài khoản.

## Test đăng nhập Lark thật

1. Dùng backend build có thay đổi này và DB tenant đã apply V39. Thiết lập `LARK_ENABLED=true`, `LARK_APP_ID`, `LARK_APP_SECRET`, `LARK_JWT_SECRET` tối thiểu 32 byte, `LARK_DEFAULT_TENANT_ID`, `LARK_REDIRECT_URI` và CORS origins phù hợp. Secret được cung cấp qua môi trường, không lưu trong mapping.
2. Trong Lark Developer Console, đăng ký redirect URI đúng nguyên văn giá trị backend cấu hình; publish/authorize app cho người dùng cần đăng nhập và cấp quyền lấy thông tin email nếu app yêu cầu. Hồ sơ provider phải trả email khớp Employee cho lần link đầu tiên. Tham khảo endpoint [Lark user_info](https://github.com/larksuite/feishu/blob/master/feishu/api_oauth.py) trong SDK chính thức.
3. Gọi `GET /api/auth/lark/authorize?returnTo=/`, mở `data.authorizationUrl`, đăng nhập Lark và chấp thuận. Hoặc mở `GET /api/auth/lark/login?returnTo=/` để redirect.
4. Frontend tại redirect URI nhận `code` và `state`, rồi POST JSON `{"code":"<code từ Lark>","state":"<state gốc>"}` tới `/api/auth/lark/callback`. Backend hiện nhận callback bằng POST; frontend xử lý GET redirect từ Lark. Không thay state hoặc gửi tenant selector để đổi tenant đăng nhập.
5. Thành công: HTTP 200, `code=LARK_AUTHENTICATED`, `data.employeeId` đúng Employee, role lấy từ DB, JWT có claim employee/tenant. Dùng `data.accessToken` làm Bearer để kiểm tra API được cấp quyền, ví dụ `/api/roles` với ADMIN. Không ghi token vào log hoặc tài liệu.
6. Kiểm tra mapping vừa tạo bằng truy vấn có tham số email trong DB tenant; lần login sau phải giữ `employee_id` và không thêm mapping trùng. Test lần hai với email provider thay đổi nên thực hiện bằng fixture hoặc tài khoản thử nghiệm.

```sql
SELECT m.id, m.employee_id, m.open_id, m.union_id, m.lark_user_id,
       m.email, m.created_at, m.updated_at, e.status, r.name AS role
FROM lark_user_mappings m
JOIN employees e ON e.id = m.employee_id
LEFT JOIN tenant_roles r ON r.id = e.role_id
WHERE lower(trim(e.email)) = :'employee_email';
```

Tài khoản được người dùng xác nhận: `vuwin24442@gmail.com`. Role cao nhất được các matcher hiện tại hỗ trợ là `ADMIN`, không có cơ chế SUPERADMIN/role hierarchy. Việc tạo Employee/cấp ADMIN theo yêu cầu này là thao tác DB riêng một lần; không seed email vào migration, không hardcode tài khoản trong code đăng nhập, không giả lập open_id hoặc mapping. Mapping chỉ được tạo khi Lark thật trả về hồ sơ phù hợp.

Đã tạo và đọc lại xác nhận tại DB `us_logisticsx`: Employee ACTIVE, role ADMIN. Thao tác có transaction, kiểm tra email/role không mơ hồ và ghi audit `explicit-user-request`. Employee mới có tên tạm `Lark Administrator`, cấu hình salary bắt buộc khởi tạo `HOURLY`, `0 USD`; cần cập nhật hồ sơ/lương trước khi sử dụng payroll. Chưa tạo mapping thay cho đăng nhập Lark thật.
