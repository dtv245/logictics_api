# Khảo sát backend cho AI Dispatch

## Phạm vi và cách đọc

Đây là khảo sát source và cấu hình hiện có tại thời điểm kiểm tra; không phải đặc tả API mới. Không sửa source hoặc migration trong lượt khảo sát này. `git status --short` cho thấy working tree đã có nhiều thay đổi chưa commit, gồm các file AI Dispatch, migration V44 và tài liệu trong `docs/ai-dispatch/`; các thay đổi đó được giữ nguyên. File khảo sát này chưa tồn tại trước khi tạo.

Giá trị mật khẩu, token, API key và URL kết nối đầy đủ không được ghi vào tài liệu. Quyền DB được kiểm tra bằng truy vấn catalog chỉ đọc, sử dụng cấu hình local bên trong tiến trình; đầu ra chỉ gồm tên role, tên bảng, owner và quyền.

## 1. Cấu trúc package, quy ước tên và persistence

Code chính nằm dưới `src/main/java/com/company/logicstic/`. Các package đang dùng:

| Trách nhiệm | Package / ví dụ có trong source |
|---|---|
| REST controller | `controller` (`TerminalController`, `OptimizationRunController`, `TripController`); AI đặt theo domain như `ai.dispatch.AiDispatchController`, `ai.gap.AiGapController` |
| Business service | `service` (`LoadService`, `TripService`) và `service.optimization` (`OptimizationApplicationService`, `OptimizationAcceptanceService`); AI nằm dưới `ai.dispatch` và `ai.gap` |
| Repository | `repository` (`TerminalRepository`, `AiDispatchSessionRepository`, `OptimizationAuditRepository`) |
| Entity | `entity` (`Load`, `Trip`, `AiDispatchSession`, `AiDispatchDecision`) |
| Request/response DTO | `dto.<module>`; tên thường dùng hậu tố `Request`, `View`; AI dùng `AiDispatchDtos` và các record trong đó |
| Mapper | `mapper` (`LoadMapper`, `TripMapper`, `InvoiceMapper`, `PaymentMapper`) |
| Cross-cutting/integration | `config`, `exception`, `common`, `integration.<provider/domain>` |

Đếm file bằng `rg --files src/main/java/com/company/logicstic | rg 'Repository\.java$' | wc -l` (tương tự `Controller`, `Service`, `Mapper`) cho kết quả: 53 repository, 47 controller, 68 service và 5 mapper file. Đây là số file theo tên, không phải số bean runtime.

Persistence là kết hợp JPA và JDBC, không phải một kiểu duy nhất:

- Entity nghiệp vụ và AI được ánh xạ JPA bằng `jakarta.persistence.@Entity`, `@Table`, `@Column`; ví dụ `Load`, `Trip`, `AiDispatchSession`, `AiDispatchDecision` trong `entity`.
- Spring Data JPA dùng `JpaRepository`; ví dụ `TerminalRepository`, `AiDispatchSessionRepository`, `AiDispatchDecisionRepository`.
- Một số phần cần SQL PostgreSQL trực tiếp dùng `JdbcTemplate`; ví dụ `OptimizationAuditRepository`, `OptimizationCandidateRepository`, `OptimizationAcceptanceRepository`, `TenantRegistryService`.
- Vì vậy câu trả lời cho “entity dùng JPA hay JDBC” là **entity dùng JPA; một số repository/service khác truy cập JDBC trực tiếp**. Bằng chứng: các class nêu trên và dependency `spring-boot-starter-data-jpa` trong `pom.xml`.

## 2. Jackson và HTTP client

`pom.xml` kế thừa Spring Boot `4.1.1`; version hiệu lực được đối chiếu bằng lệnh `./mvnw dependency:tree -Dincludes=tools.jackson.core:jackson-databind,com.fasterxml.jackson.core:jackson-annotations`:

- Jackson 3 `tools.jackson.core:jackson-databind:3.1.5` — nhiều class dùng `tools.jackson.databind.ObjectMapper`, ví dụ `AiDispatchService`, `OptimizationApplicationService`.
- Jackson 2 `com.fasterxml.jackson.core:jackson-databind:2.21.5`, annotations `2.21` — xuất hiện qua `jjwt-jackson:0.12.6` và trong một số annotation import tương thích cũ như `com.fasterxml.jackson.annotation.JsonProperty` (`PageInfo`, `ApiResponse`). Đây là hai namespace/version cùng xuất hiện trong dependency tree.

HTTP client được tìm thấy:

- `RestTemplate`: `LarkConfiguration` cung cấp bean; được dùng bởi `LarkAuthClient`, `LarkBaseClient`, `LarkSyncGateway`.
- JDK `java.net.http.HttpClient`: dùng bởi `TrustedOptimizationHttpAdapter` và `EiaFuelIndexProvider`.
- Import/implementation `org.springframework.web.client.RestClient` và `org.springframework.web.reactive.function.client.WebClient`: **KHÔNG TÌM THẤY**. Các import `RestClientException`/`RestClientResponseException` là exception của Spring, không phải bằng chứng dùng `RestClient` làm client.

## 3. Authentication, permission và tenant

### Authentication và role

- `SecurityConfig` bật `@EnableWebSecurity` và `@EnableMethodSecurity`, cài `LarkAuthenticationFilter`, và đặt `anyRequest().authenticated()` làm fallback. `LarkAuthenticationFilter` đọc `Authorization: Bearer`, gọi `LarkAuthService.validateInternalToken`, kiểm tra `sub`, `email`, `tenant`, đọc claim `roles`, rồi ánh xạ role thành authority `ROLE_<role>`.
- `LarkAuthService.createInternalToken` ghi claims `sub`, `email`, `tenant`, `roles`, `name`, và tùy chọn `employee_id`; token được ký bằng `jwtSecretKey`. `LarkAuthService.validateInternalToken` xác minh chữ ký bằng cùng key. Cấu hình issuer/TTL nằm trong `LarkProperties` và `application.yml`.
- `SecurityConfig` gắn các role hiện hữu theo route, ví dụ `/api/optimization/**` cho `ADMIN`/`DISPATCHER`, `/api/loads/**` cho `ADMIN`/`ACCOUNTANT`/`DISPATCHER`.
- **Không tìm thấy** authority riêng `ai-dispatch:view`, `ai-dispatch:run`, `ai-dispatch:approve`, cũng không thấy `@PreAuthorize` trên `AiDispatchController`/`AiGapController`. Theo cấu hình hiện tại các route AI không khớp rule role cụ thể và rơi vào `anyRequest().authenticated()`. `@PreAuthorize` có dùng ở một số controller khác, ví dụ `RatingController`.

Nguồn: `config/SecurityConfig.java` (`SecurityConfig`), `integration/lark/auth/LarkAuthenticationFilter.java` (`LarkAuthenticationFilter`), `integration/lark/auth/LarkAuthService.java` (`createInternalToken`, `validateInternalToken`), `ai/dispatch/AiDispatchController.java` (`AiDispatchController`), `controller/RatingController.java`.

### Tenant và cách điền `tenant_id`

- Khi tenancy bật, `TenantJwtClaimFilter` lấy claim `tenant` từ JWT, từ chối thiếu tenant hoặc không khớp tenant đã bind, đặt `TenantContext`, rồi gọi `TenantDataSourceService.ensureTenantDataSource`. Filter chỉ được đăng ký khi `app.tenancy.enabled=true`; `TenantRoutingDataSource` chọn DataSource theo `TenantContext`.
- `TenantDataSourceService` lấy URL, username và mật khẩu mã hóa từ `TenantRegistryService`/`tenant_registry`, giải mã mật khẩu qua `TenantCredentialCipher`, rồi tạo Hikari pool cho tenant. `TenantMigrationService` chạy Flyway theo từng tenant.
- Trong AI controller hiện có, `AiDispatchController.resolveTenantId()` và `AiGapController` lấy `TenantContext.getTenantId()`; nếu chưa có thì fallback sang `LarkProperties.defaultTenantId()` (cấu hình mặc định `local-development`). ID này được truyền rõ qua tham số service, không tự lấy từ tên DB.
- `AiDispatchService.runDispatch` gán `tenantId` vào `AiDispatchSession` và `AiDispatchDecision`; `AiGapService.evaluateLoad` gán vào `AiLoadGap` và `AiRun`. `AiLoadGapHistory` liên kết tới gap, không có cột `tenant_id` trực tiếp trong entity/migration. Các repository AI có truy vấn lọc `tenantId`, ví dụ `findByTenantId...`.
- Cấu hình local hiện tại có `TENANCY_ENABLED=false`; khi đó dùng DataSource cấu hình bằng `DB_URL`/`DB_USERNAME`, không đi qua tenant router. Giá trị này chỉ mô tả local `.env` tại lần kiểm tra, không chứng minh cấu hình staging/production.

Nguồn: `config/TenantJwtClaimFilter.java`, `config/TenantContext.java`, `config/TenantRoutingDataSource.java`, `config/TenantMigrationBootstrapConfiguration.java`, `service/TenantDataSourceService.java`, `service/TenantRegistryService.java`, `service/TenantMigrationService.java`, `ai/dispatch/AiDispatchController.java`, `ai/dispatch/AiDispatchService.java`, `ai/gap/AiGapController.java`, `ai/gap/AiGapService.java`, `entity/AiLoadGapHistory.java`, `application.yml`.

## 4. Envelope lỗi, phân trang và lọc/sắp xếp `/api/terminals`

### Response/error

Response chuẩn là `ApiResponse<T>` với `success`, `code`, `message`, `data`, `errors`, `meta`, và `params`. `meta` (`ResponseMeta`) gồm `timestamp`, `path`, `requestId`; `ApiError` gồm `field`, `code`, `message`, `params`. `GlobalExceptionHandler` ánh xạ lỗi validation thành HTTP 400/code `VALIDATION_FAILED`, auth thành 401/`UNAUTHORIZED`, permission thành 403/`FORBIDDEN`, lỗi API theo `ApiException`, và lỗi không dự kiến thành 500/`INTERNAL_ERROR`. Response lỗi có `X-Request-Id` và `Cache-Control: no-store`.

Nguồn: `dto/ApiResponse.java` (`ApiResponse`), `dto/ResponseMeta.java` (`ResponseMeta`), `dto/ApiError.java` (`ApiError`), `exception/GlobalExceptionHandler.java` (`GlobalExceptionHandler`), `config/SecurityResponses.java` (`SecurityResponses`).

### Endpoint `GET /api/terminals`

`TerminalController.search` nhận:

| Tham số | Mặc định / cách xử lý |
|---|---|
| `search` | Không bắt buộc; tìm không phân biệt hoa thường theo `name`, `code`, `addressCity` |
| `type` | Không bắt buộc; so khớp `type` |
| `countryCode` | Không bắt buộc; so khớp không phân biệt hoa thường |
| `page` | Mặc định 1, quy ước 1-based; dưới 1 chuẩn hóa về 1 |
| `pageSize` | Mặc định 20, kẹp trong khoảng 1–100 |
| `orderBy` | Mặc định `name`; chỉ nhận field allowlist: `id`, `name`, `code`, `countryCode`, `type`, `addressCity` |
| `descending` | Mặc định `false` |

`TerminalService.search` áp dụng `QuerySortPolicy`, `PaginationValidator`, sau đó gọi `TerminalRepository.search`. Sort field ngoài allowlist trả 400 `INVALID_SORT_FIELD`; không ghép tên cột tự do vào query. `PagedResponse` trả `items`, `totalItems`, `totalPages`, `currentPage` (1-based), `pageSize`.

Nguồn: `controller/TerminalController.java` (`TerminalController.search`), `service/TerminalService.java` (`search`), `repository/TerminalRepository.java` (`search`), `common/QuerySortPolicy.java`, `common/PaginationValidator.java`, `common/Constants.java`, `dto/PagedResponse.java`.

## 5. Giá trị status/mode/type tìm được trong code

Không tìm thấy Java enum đóng cho `Load.status`, `Truck.status`, `Trip.status`, `AiDispatchSession.mode/status`, `AiDispatchDecision.type/status`. Các entity ánh xạ thành `String`; các migration tương ứng dùng `text` và không có CHECK constraint giới hạn các giá trị này. Vì vậy các chuỗi dưới đây là **literal đã thấy trong source/test**, không phải danh sách enum đầy đủ hoặc bảo đảm toàn bộ dữ liệu DB.

| Trường | Literal có bằng chứng trong source | Kết luận |
|---|---|---|
| `loads.status` | `OPEN` (fixture test); `ASSIGNED` (`LoadService.assignTruck`) | Enum tập đóng: **KHÔNG TÌM THẤY**. `CreateLoadRequest.status` là `String` bắt buộc, mapper nhận giá trị từ request. |
| `trucks.status` | `ACTIVE` (fixture); `AVAILABLE` (lọc xe AI); `MAINTENANCE` (guard approve AI); `MAINTENANCE_BLOCKED` và `UNAVAILABLE_NEW_STATE` (test stale optimization) | Enum tập đóng: **KHÔNG TÌM THẤY**. `CreateTruckRequest.status` là `String` bắt buộc. |
| `trips.status` | `PLANNED` (fixture và acceptance test); `COMPLETED` (fixture các test tài chính) | Enum tập đóng: **KHÔNG TÌM THẤY**. `CreateTripRequest.status` là `String` bắt buộc. Engine acceptance còn kiểm tra các timestamp `dispatched_at`, `completed_at`, `cancelled_at`, không chỉ dựa vào status. |
| `ai_dispatch_sessions.mode` | Persist `HumanInTheLoop`; `Autonomous` bị `AiDispatchService.runDispatch` từ chối với `AUTONOMOUS_MODE_LOCKED` | Enum Java/DB: **KHÔNG TÌM THẤY**. DTO comment cũng mô tả `HumanInTheLoop` và `Autonomous` (locked). |
| `ai_dispatch_sessions.status` | Persist `COMPLETED`; cancel chuyển thành `CANCELLED` | Enum Java/DB: **KHÔNG TÌM THẤY**. |
| `ai_dispatch_decisions.type` | Persist `ASSIGN_LOAD_TRUCK` | Enum Java/DB: **KHÔNG TÌM THẤY**. |
| `ai_dispatch_decisions.status` | `Suggested`, `Failed`, `Executed`, `Rejected` | Enum Java/DB: **KHÔNG TÌM THẤY**; capitalization trong source như trên. |

Nguồn: `entity/Load.java`, `dto/load/CreateLoadRequest.java`, `service/LoadService.java`, `entity/Truck.java`, `dto/truck/CreateTruckRequest.java`, `ai/dispatch/AiDispatchService.java`, `entity/Trip.java`, `dto/trip/CreateTripRequest.java`, `entity/AiDispatchSession.java`, `entity/AiDispatchDecision.java`, `ai/dispatch/dto/AiDispatchDtos.java`, `src/test/java/com/company/logicstic/integration/OptimizationAcceptancePostgresTest.java`, `src/test/java/com/company/logicstic/integration/OptimizationRunPostgresTest.java`, `src/main/resources/db/migration/tenant/V1__baseline_business_schema.sql`.

## 6. Optimization engine, idempotency và gán tài nguyên

### Tạo optimization run

1. `POST /api/optimization/runs` vào `OptimizationRunController.create`.
2. Controller lấy employee actor từ danh tính đã xác thực rồi gọi `OptimizationApplicationService.create`.
3. `CreateRequest` yêu cầu caller gửi `idempotencyKey`, `policyId`, targets, driver IDs, truck IDs và source selections; code không tự tạo key cho request. Service chuẩn hóa và hash scope, kiểm tra replay, tính feasibility/score, lưu `Run` qua `OptimizationAuditRepository.insert(Run)` vào `optimization_runs`, rồi lưu candidate qua `insert(Candidate)` vào `optimization_assignments`. Mỗi run có planning window 72 giờ và trần 200 candidates.

Nguồn: `controller/OptimizationRunController.java` (`create`), `service/optimization/OptimizationApplicationService.java` (`CreateRequest`, `create`, `normalize`), `repository/OptimizationAuditRepository.java` (`insert(Run)`, `insert(Candidate)`), `db/migration/tenant/V30__create_immutable_optimization_policy_and_run_audit.sql`.

### Accept candidate

`POST /api/optimization/runs/{id}/assignments/{candidateId}/accept` gọi `OptimizationAcceptanceService.accept` qua `OptimizationRunController.accept`. `AcceptRequest` yêu cầu caller gửi `idempotencyKey` và `expectedInputFingerprint`; key được kiểm tra/replay và ghi vào `optimization_accept_commands`. Không tìm thấy backend code sinh tự động idempotency key; API caller phải cung cấp.

Service xác minh actor, run/candidate, feasibility, fingerprint, planning window và revalidate dữ liệu; sau đó khóa Load/Trip/Employee/Truck và các hàng liên quan. `OptimizationAcceptanceRepository.assign`:

- cập nhật `trips.truck_id` và tăng `trips.version`;
- tái sử dụng driver assignment đang khớp hoặc insert `trip_driver_assignments` với `assignment_type='PRIMARY'`;
- sau đó ghi acceptance và command audit bất biến.

**Optimization accept không cập nhật `loads.assigned_truck_id`/`loads.status`, và không đặt `trips.dispatched_at`.** Guard DB trong `V33__guard_atomic_optimization_acceptance.sql` yêu cầu trip chưa dispatch/completed/cancelled và chặn candidate/claim sai.

Nguồn: `controller/OptimizationRunController.java` (`accept`), `service/optimization/OptimizationAcceptanceService.java` (`AcceptRequest`, `accept`, `replay`), `repository/OptimizationAcceptanceRepository.java` (`assign`, `recordCommand`), `db/migration/tenant/V33__guard_atomic_optimization_acceptance.sql`.

### Quan hệ với AI Dispatch hiện tại

Trong source hiện tại, `AiDispatchService.runDispatch` **không gọi** `OptimizationApplicationService.create`; nó tự lọc `truckRepository.findAll()`, gọi `calculateCandidateScore` nội bộ, rồi lưu session/decision AI. `AiDispatchService.approveDecision` không gọi optimization accept; nó gọi `LoadService.assignTruck`, phương thức đặt `loads.assigned_truck_id` và status `ASSIGNED`. Vì vậy luồng AI hiện tại và Optimization engine là hai đường riêng.

`TripExecutionService` có gán/bỏ gán driver và cập nhật các trạng thái stop; `TripService` tạo/cập nhật Trip. **KHÔNG TÌM THẤY** service/controller method trong source đặt `trips.dispatched_at` hoặc một endpoint “dispatch trip” riêng. Do đó không có bằng chứng bước hậu-accept nào đồng bộ `Load.assigned_truck_id` hoặc dispatch Trip sau optimization accept.

Nguồn: `ai/dispatch/AiDispatchService.java` (`runDispatch`, `calculateCandidateScore`, `approveDecision`), `service/LoadService.java` (`assignTruck`), `service/TripExecutionService.java`, `service/TripService.java`, `controller/OptimizationRunController.java`.

## 7. Redis và Flyway migration

- `docker-compose.yml` khai báo service `redis`, image `redis:7-alpine`, container `logistics-redis`, port `6379:6379`, volume `redisdata:/data`. Service `api` nhận biến host/port Redis từ Compose.
- `pom.xml` không khai báo Spring Data Redis/Lettuce/Jedis starter; không tìm thấy `RedisTemplate`, `StringRedisTemplate`, Redis client, hay `spring.data.redis.*` trong source/config. Kết luận: **Redis container được khai báo, nhưng backend hiện chưa có bằng chứng kết nối hoặc dùng Redis**.
- Flyway location mặc định là `classpath:db/migration/tenant`; `TenantMigrationService` chạy migration trên từng DB tenant khi tenancy bật. Quy ước tên hiện dùng `V<number>__<snake_case_description>.sql`, ví dụ `V44__create_ai_gap_tables.sql`. Version cao nhất trong source là V44; version tuần tự chưa có trong source là **V45**. Các file trong `obsolete/` có hậu tố `.sql.disabled`, không phải migration đang được Flyway nạp theo naming pattern.

Nguồn: `docker-compose.yml`, `pom.xml`, `src/main/resources/application.yml`, `service/TenantMigrationService.java`, danh sách `src/main/resources/db/migration/tenant/V*.sql`.

## 8. DB user và quyền thực tế trên bảng

### Cấu hình và giới hạn xác minh

`application.yml` dùng `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` cho datasource mặc định. Local `.env` đang có `TENANCY_ENABLED=false`, nên lần kiểm tra này kết nối datasource đơn bằng `DB_URL`/`DB_USERNAME`; môi trường khác có thể bật tenant routing và lấy credential riêng từ `tenant_registry`.

Kết nối PostgreSQL theo datasource local thành công. `SELECT current_user, session_user` trả về `logistics | logistics`. Role `logistics` là owner các bảng đang tồn tại dưới đây; `has_table_privilege` trả `true` cho tất cả quyền được liệt kê. Không suy ra đây là quyền của production hay các DB tenant khác.

`.env.example` khai báo username mẫu `postgres`, trong khi role của kết nối local thực tế là `logistics`; file example không phản ánh danh tính DB của kết nối vừa kiểm tra.

| Bảng trong yêu cầu | Kết quả trên DB hiện kết nối |
|---|---|
| `ai_dispatch_sessions` | Có; owner `logistics`; `SELECT, INSERT, UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER` đều có quyền (`true`) |
| `ai_dispatch_decisions` | Có; owner `logistics`; toàn bộ quyền trên đều `true` |
| `ai_load_gaps` | **Không tồn tại trong `public`** trên DB hiện kết nối; không có quyền bảng để báo cáo |
| `ai_load_gap_history` | **Không tồn tại trong `public`** trên DB hiện kết nối; không có quyền bảng để báo cáo |
| `ai_runs` | **Không tồn tại trong `public`** trên DB hiện kết nối; không có quyền bảng để báo cáo |
| `optimization_*` | Các bảng hiện có: `optimization_accept_commands`, `optimization_acceptances`, `optimization_assignments`, `optimization_policy_versions`, `optimization_qualified_forecast_costs`, `optimization_qualified_inputs`, `optimization_runs`. Tất cả owner `logistics`; toàn bộ quyền trên đều `true`. |
| `accepted_rating_snapshots` | Có; owner `logistics`; toàn bộ quyền trên đều `true` |

Source migration `V44__create_ai_gap_tables.sql` tạo `ai_load_gaps`, `ai_load_gap_history`, `ai_runs`, trong khi ba bảng này vắng khỏi catalog của DB local đang kết nối. **[SUY LUẬN TỪ ĐỐI CHIẾU]** Schema của DB local đang kết nối chưa phản ánh phần V44 hiện có trong working tree; trạng thái deploy/migrate của các DB khác không xác minh được từ kết quả này.

SQL catalog đã dùng (chỉ đọc; kết quả chỉ xuất tên role/bảng/quyền):

```sql
SELECT current_user, session_user;

WITH requested(table_name) AS (
  VALUES ('ai_dispatch_sessions'), ('ai_dispatch_decisions'), ('ai_load_gaps'),
         ('ai_load_gap_history'), ('ai_runs'), ('accepted_rating_snapshots')
), targets AS (
  SELECT c.oid, c.relname, pg_get_userbyid(c.relowner) AS owner
  FROM pg_class c
  JOIN pg_namespace n ON n.oid = c.relnamespace
  WHERE n.nspname = 'public'
    AND c.relkind IN ('r', 'p')
    AND (c.relname IN (SELECT table_name FROM requested)
         OR c.relname LIKE 'optimization\_%' ESCAPE '\')
)
SELECT relname, owner,
       has_table_privilege(current_user, oid, 'SELECT'),
       has_table_privilege(current_user, oid, 'INSERT'),
       has_table_privilege(current_user, oid, 'UPDATE'),
       has_table_privilege(current_user, oid, 'DELETE'),
       has_table_privilege(current_user, oid, 'TRUNCATE'),
       has_table_privilege(current_user, oid, 'REFERENCES'),
       has_table_privilege(current_user, oid, 'TRIGGER')
FROM targets
ORDER BY relname;
```

Lệnh chạy là `psql -X -A -t -F '|' -v ON_ERROR_STOP=1 -c '<SQL trên>'`, kết nối bằng biến datasource local đã đọc trong tiến trình; giá trị credential không được in. CTE chỉ liệt kê bảng tồn tại nên các tên không có dòng kết quả được đối chiếu là vắng khỏi `public`. Quyền DB `true` không đồng nghĩa thao tác UPDATE/DELETE luôn được phép: trigger append-only của optimization vẫn có thể từ chối DML.

## 9. Test, Postman/Newman và lệnh chuẩn

- Maven wrapper ở `./mvnw`; `pom.xml` có Spring Boot test starter, Spring MVC test, JPA test, `spring-security-test`, Testcontainers PostgreSQL, H2 và REST Assured.
- Có 130 file test Java khớp `*Test.java`, trong đó 30 file tên `*PostgresTest.java` theo lệnh đếm. Nhiều integration test chỉ bật khi `TASK_DB_URL` khớp `jdbc:postgresql:.*codex_.*`; ví dụ `OptimizationAcceptancePostgresTest`.
- Test dùng H2 tồn tại; `AiOpenApiTest` khai báo `jdbc:h2:mem:...;MODE=PostgreSQL`. HTTP test dùng MockMvc; ví dụ `TerminalServiceTest`, `AiOpenApiTest`, `OptimizationAcceptancePostgresTest`.
- Testcontainers PostgreSQL được khởi tạo trong `RemediationContextPostgresTest` (`PostgreSQLContainer("postgres:16-alpine")`). Docker socket hiện tại không cho truy cập từ sandbox, nên không chạy test container trong khảo sát này.
- Postman collections hiện có: `postman/LogisticsX.postman_collection.json`, `LogisticsX_Part1.postman_collection.json`, `LogisticsX_Part2.postman_collection.json`, `LogisticsX_Part3.postman_collection.json`, cùng `LogisticsX.postman_environment.json`. Các collection hiện tại chưa được xác minh là có folder AI Dispatch.
- Không tìm thấy `package.json`; `command -v newman` không tìm thấy executable Newman. Không có bằng chứng script Maven gọi Newman.
- README ghi lệnh chuẩn `./mvnw test` và `./mvnw clean package`. README còn ghi “47 test classes”, trong khi đếm file `*Test.java` hiện tại là 130; **[SUY LUẬN]** con số README đã cũ hoặc đang dùng cách đếm khác.
- Không chạy unit/integration test hoặc build trong lượt khảo sát. Lệnh `./mvnw dependency:tree ...` chạy thành công để xác định phiên bản Jackson; đây không phải test.

Nguồn: `pom.xml`, `README.md` (mục Build và Test), `src/test/java/com/company/logicstic/ai/AiOpenApiTest.java`, `src/test/java/com/company/logicstic/integration/RemediationContextPostgresTest.java`, `src/test/java/com/company/logicstic/integration/OptimizationAcceptancePostgresTest.java`, `src/test/java/com/company/logicstic/service/TerminalServiceTest.java`, `postman/`.

## 10. Kiểm tra quan hệ test với hai trigger

Tìm kiếm trong `src/test`, `postman`, `scripts` xác nhận:

- **KHÔNG TÌM THẤY** test Java hoặc Postman item nào nhắc trực tiếp tên `trg_trip_auto_seed_stops` hay `trg_trip_driver_assign_auto_fill`.
- Hai trigger chỉ được tạo trong `scripts/seed_trip_stops.sql`; header script tự mô tả là test-only và nói mục đích hỗ trợ API automation/Newman. Không tìm thấy cấu hình Maven/Flyway gọi script này; file không nằm dưới Flyway migration location.
- Các test tối ưu tự tạo fixture, không cần trigger để seed stop: `OptimizationAuditPostgresTest.fixture()` insert Trip rồi insert pickup stop trực tiếp; `OptimizationRunPostgresTest.fixture()` lấy stop đó bằng SQL. `OptimizationAcceptancePostgresTest.acceptAtomicallyAssignsExistingTripWithoutDispatchFakeEndOrFinancialSideEffects()` xác nhận Trip giữ `PLANNED`, `dispatched_at` null; logic acceptance yêu cầu `completed_at` null. Nếu trigger auto-seed được cài trong DB test, nó cập nhật `completed_at`, nên các fixture/acceptance này có thể bị chặn hoặc sai dữ liệu. Đây là **xung đột khi trigger hiện diện**, không phải bằng chứng test phụ thuộc vào trigger.
- `CostLedgerPostgresTest.mileageSettlementPersistsSelectedBasisInputsAndMissingActualCreatesNoFinancialRows()` chủ động insert assignment không có `actual_miles`, rồi kỳ vọng `MILEAGE_VALIDATION_REQUIRED`. Nếu trigger auto-fill đặt `actual_miles` theo planned miles hoặc 450, test này không còn kiểm tra cùng điều kiện.

Vì vậy: **KHÔNG TÌM THẤY test hiện có nào phụ thuộc vào side effect của hai trigger**. Có bằng chứng một số test tối ưu và test payroll yêu cầu trigger không được cài trong DB dùng để chạy chúng. Postman/Newman có thể được thiết kế để dùng script seed theo comment, nhưng mối liên kết thực thi giữa collection và `scripts/seed_trip_stops.sql` **KHÔNG TÌM THẤY** trong repo.

Nguồn: `scripts/seed_trip_stops.sql`, `src/test/java/com/company/logicstic/integration/OptimizationAuditPostgresTest.java` (`fixture`), `OptimizationRunPostgresTest.java` (`fixture`), `OptimizationAcceptancePostgresTest.java` (accept test), `CostLedgerPostgresTest.java` (mileage test).

## ĐIỂM CHƯA RÕ

1. **Phạm vi cài trigger test-only:** `scripts/seed_trip_stops.sql` tự ghi TEST-ONLY, nhưng cần PO/DevOps xác nhận DB nào được phép cài script và đảm bảo không áp dụng vào môi trường dùng integration acceptance/payroll tests hoặc production.
2. **Quyền AI Dispatcher:** cần Security/PO quyết định role nào được xem, chạy, duyệt/từ chối AI. Source hiện tại chỉ yêu cầu authenticated user cho route AI và chưa có authority `ai-dispatch:*`. Không tự suy ra rằng mọi `DISPATCHER` đều được phép approve.
3. **Contract nghiệp vụ giữa Optimization accept và dispatch:** source chỉ gán Truck/Driver cho Trip khi accept; không đồng bộ `loads.assigned_truck_id` và không đặt `dispatched_at`. PO/BA cần chốt liệu đây là đúng workflow hay cần một lệnh dispatch riêng sau accept. Không được tự đồng bộ hai state machine.
4. **Danh sách status hợp lệ:** các trường Load/Truck/Trip và AI session/decision là `String`/`text`, không có enum/check đầy đủ. BA/PO cần xác nhận danh sách canonical và quy tắc chuyển trạng thái nếu muốn làm enum/API validation.
5. **DB V44 trên các môi trường:** DB local hiện kết nối chưa có `ai_load_gaps`, `ai_load_gap_history`, `ai_runs`, trong khi source có V44. Cần chủ môi trường xác nhận migration V44 đã/được phép chạy trên từng tenant DB và quyền migration user; lần khảo sát này không kiểm tra các DB khác.
6. **Tenant runtime ở môi trường đích:** local `.env` đang tắt tenancy; nếu staging/production bật tenancy, cần xác minh username/owner/grants trên từng DB tenant, không dùng kết quả role `logistics` của DB local để kết luận cho các tenant DB.
7. **Tích hợp AI với engine optimization:** source AI hiện tại tự scoring và approve qua `LoadService.assignTruck`, còn optimization run/accept là endpoint/service riêng. PO/BA cần xác nhận AI Dispatcher mới sẽ gọi `OptimizationApplicationService`/`OptimizationAcceptanceService` hay giữ workflow riêng; source hiện tại chưa chứng minh lựa chọn đó.
8. **Test Postman/Newman cho AI:** có collections nhưng không có Newman executable/package script và chưa xác minh folder AI Dispatch. Cần xác nhận pipeline/toolchain dùng để chạy collection trước khi coi API test là điều kiện nghiệm thu.
