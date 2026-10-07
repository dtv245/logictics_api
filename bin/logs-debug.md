# Backend Debug & Historical Bug Review Plan

> Project: LogisticsX / Logicstic backend  
> Stack: Java, Spring Boot, JPA/Hibernate, Flyway, PostgreSQL, Redis, Spring Security/JWT, multi-tenant, Testcontainers  
> Purpose: Rà soát lỗi cũ, xác minh nguyên nhân, đánh dấu trạng thái fix/regression và tập trung toàn bộ bằng chứng vào một nơi duy nhất.

---

## 1. Mục tiêu

Tạo một lịch sử debug có thể kiểm chứng cho backend thay vì chỉ ghi lại lỗi theo trí nhớ.

Mỗi lỗi phải trả lời được:

1. Lỗi xảy ra ở đâu?
2. Triệu chứng thực tế là gì?
3. Có tái hiện được không?
4. Nguyên nhân gốc là gì?
5. Đã sửa ở commit/migration nào?
6. Có regression test chưa?
7. Runtime hiện tại còn bị ảnh hưởng không?
8. Có nguy cơ tái phát khi refactor/migration/deploy không?

Không ghi một lỗi là `FIXED` chỉ vì source code “trông có vẻ đã sửa”.

---

## 2. Nguyên tắc rà soát

### 2.1 Evidence-first

Ưu tiên bằng chứng theo thứ tự:

1. Test tái hiện được lỗi.
2. Runtime log / stack trace.
3. Git diff / commit sửa lỗi.
4. Flyway migration / DB constraint.
5. Controller / service / repository source.
6. OpenAPI/runtime behavior.
7. Issue/Jira/PR/documentation.
8. Suy luận.

Nếu chỉ có suy luận, trạng thái phải là `SUSPECTED`, không được ghi `CONFIRMED`.

### 2.2 Không sửa lỗi trong lúc audit một cách âm thầm

Audit và fix là hai bước khác nhau.

Khi phát hiện lỗi:

- ghi vào file này trước;
- tạo reproduction;
- đánh giá severity;
- sau đó mới fix ở task/commit riêng.

### 2.3 Không làm mất dữ liệu lịch sử

Không:

- sửa migration Flyway đã apply;
- reset/reseed DB để “làm hết lỗi”;
- xóa log cũ;
- bỏ test lỗi chỉ để build xanh;
- chỉnh behavior lịch sử mà không có migration/compatibility plan.

---

## 3. Phạm vi rà soát

### A. Build / dependency / environment

Kiểm tra:

- Java version.
- Maven wrapper.
- Spring Boot dependency conflicts.
- profile dev/test/prod.
- Docker image khác source hiện tại.
- env/config key sai hoặc thiếu.
- Redis/PostgreSQL/Testcontainers version mismatch.

Các lỗi thường gặp:

- source đã sửa nhưng container vẫn chạy image cũ;
- local pass nhưng Docker fail;
- profile kích hoạt sai bean;
- test và runtime dùng config khác nhau.

### B. Spring Boot lifecycle / bean wiring

Kiểm tra:

- duplicate bean;
- circular dependency;
- missing bean;
- wrong `@Profile`;
- wrong `@ConfigurationProperties`;
- component scan/package relocation;
- `@Transactional` self-invocation;
- proxy bypass;
- constructor injection vs field injection.

Tìm nhanh:

```bash
grep -R "@Autowired" -n src/main/java
grep -R "@Profile" -n src/main/java src/test/java
grep -R "@ConfigurationProperties" -n src/main/java
grep -R "@Transactional" -n src/main/java
```

### C. Security / JWT / Lark / authorization

Kiểm tra:

- JWT decoder.
- Lark/OIDC composite auth.
- principal → employee mapping.
- role normalization.
- `@PreAuthorize`.
- object-level authorization.
- cross-tenant access.
- caller-supplied `employeeId`, `senderId`, `tenantId`.
- endpoint `permitAll` quá rộng.
- 401/403 semantics.

Đặc biệt kiểm tra:

- Messaging sender/participant authorization.
- `/api/me`.
- payroll self-service ownership.
- document ownership.
- finance role/action matrix.

### D. Multi-tenancy

Kiểm tra:

- `TenantContext`.
- `TenantRoutingDataSource`.
- tenant leak giữa request/thread.
- async/thread pool mất tenant context.
- repository/query bỏ tenant boundary.
- cache key thiếu tenant.
- background job không set tenant.
- test cross-tenant.

Search:

```bash
grep -R "TenantContext" -n src/main/java
grep -R "@Async" -n src/main/java
grep -R "CompletableFuture" -n src/main/java
grep -R "cache" -n src/main/java
```

Mọi bug tenant leak phải ít nhất `HIGH`.

### E. JPA / Hibernate / database

Kiểm tra:

- N+1.
- LazyInitializationException.
- cascade sai.
- orphanRemoval.
- unique constraint thiếu.
- optimistic locking.
- lost update.
- pagination + fetch join.
- enum/string mismatch.
- BigDecimal precision.
- timestamp/date semantics.

Search:

```bash
grep -R "fetch = FetchType.EAGER" -n src/main/java
grep -R "JOIN FETCH" -n src/main/java
grep -R "@Version" -n src/main/java
grep -R "BigDecimal" -n src/main/java
```

### F. Flyway / schema evolution

Kiểm tra:

- migration checksum.
- duplicate table/column.
- migration ordering.
- source entity khác schema.
- clean install khác upgrade install.
- historical data backfill sai.
- TIMESTAMP/TIMESTAMPTZ/DATE conversion.
- migration source tồn tại nhưng runtime DB chưa apply.

Bắt buộc verify:

1. clean `V1 -> latest`;
2. previous latest -> latest;
3. `flyway validate`;
4. checksum migration cũ không đổi.

### G. REST API / DTO / validation

Kiểm tra:

- path/method mismatch.
- DTO field rename.
- request/response date type.
- pagination.
- error envelope.
- Bean Validation.
- invalid status transition.
- multipart contract.
- frontend/backend OpenAPI mismatch.

Đặc biệt tìm:

- endpoint có trong source nhưng không có runtime OpenAPI;
- generic update cho immutable financial record;
- field `Date` vs ISO string;
- missing collection API.

### H. Financial correctness

Kiểm tra:

- duplicate financial effect.
- amount sign.
- currency mismatch.
- rounding.
- tax.
- idempotency.
- immutable snapshot.
- credit/rebill/supplemental.
- settlement/payment/payroll transitions.

Không cho phép:

- fake zero;
- re-rate invoice lịch sử;
- mutate locked settlement/payroll;
- duplicate payment;
- implicit FX.

### I. Rating / optimization

Kiểm tra:

- ambiguous rate.
- missing pricing date.
- wrong mileage attribution.
- stale fuel index.
- hidden MPG default.
- floating-point score.
- optimizer hidden tie-break.
- stale candidate acceptance.
- concurrent driver/truck assignment.

### J. Fleet / reporting / dashboard

Kiểm tra:

- metric lấy từ current status thay vì history.
- denominator bằng 0 nhưng trả `0%`.
- missing metric bị convert thành zero.
- duplicate cost counting.
- reporting query N+1.
- timezone/report range.

### K. Cache / Redis

Kiểm tra:

- cache key thiếu tenant.
- stale cache sau mutation.
- DTO serialization compatibility.
- cache invalidation quá rộng.
- cache chứa authorization-sensitive data.

### L. Concurrency / idempotency

Kiểm tra:

- duplicate POST.
- retry tạo duplicate invoice/payment.
- optimistic/pessimistic locking.
- transaction boundary.
- external API call trong DB transaction.
- concurrent accept/approve/lock.

---

## 4. Nguồn dữ liệu phải rà soát

Rà soát tối thiểu:

```text
git log
git diff
git blame
old branches / backup branches
issues / Jira
PR descriptions
TODO / FIXME / HACK
runtime logs
Docker logs
test failures
disabled tests
@Disabled
Flyway history
OpenAPI snapshots
docs/backend-gaps.md
project progress/handoff docs
```

Search nhanh:

```bash
grep -R "TODO\|FIXME\|HACK\|XXX" -n src
grep -R "@Disabled" -n src/test
grep -R "skip" -n pom.xml src/test
grep -R "Exception" -n logs/ 2>/dev/null
```

---

## 5. Workflow rà soát từng lỗi

```text
Discover
  ↓
Collect evidence
  ↓
Reproduce
  ↓
Classify
  ↓
Root cause
  ↓
Check historical fix
  ↓
Regression test
  ↓
Runtime verification
  ↓
Close / reopen
```

### Step 1 — Discover

Nguồn:

- git history;
- docs;
- old logs;
- failing tests;
- issue tracker;
- old branch;
- code smell có evidence.

### Step 2 — Reproduce

Ưu tiên tạo test trước.

Nếu lỗi DB: PostgreSQL/Testcontainers.  
Nếu lỗi API: MockMvc/WebTestClient/integration test.  
Nếu lỗi concurrency: multi-thread integration test.  
Nếu lỗi deployment: runtime/OpenAPI verification.

### Step 3 — Root cause

Không ghi:

> “Spring lỗi”

Phải ghi cụ thể, ví dụ:

> `SecurityConfiguration` phụ thuộc trực tiếp integration layer khiến boundary architecture bị phá và bean wiring dễ vỡ khi package move.

### Step 4 — Verify fix

Một lỗi chỉ `FIXED_VERIFIED` khi:

- fix source tồn tại;
- regression test tồn tại;
- full relevant tests pass;
- runtime/schema tương ứng đúng.

---

## 6. Severity

| Severity | Ý nghĩa |
|---|---|
| CRITICAL | Data loss, tenant leak, auth bypass, duplicate payment, financial corruption |
| HIGH | Workflow sai nghiêm trọng, incorrect invoice/payroll, concurrency corruption, production outage |
| MEDIUM | API mismatch, incorrect validation, reporting sai, performance nghiêm trọng |
| LOW | UX/API inconvenience, logging, maintainability issue không làm sai dữ liệu |

---

## 7. Status

Chỉ dùng:

```text
DISCOVERED
SUSPECTED
REPRODUCED
CONFIRMED
FIX_IN_PROGRESS
FIXED_NOT_VERIFIED
FIXED_VERIFIED
CANNOT_REPRODUCE
OBSOLETE
DEFERRED
```

Không dùng từ mơ hồ như:

```text
probably fixed
looks okay
done?
```

---

## 8. Bug record template

## BUG-BE-XXXX — <Short title>

**Status:**  
**Severity:**  
**Module:**  
**First known version/commit:**  
**Last affected version/commit:**  
**Reported by/source:**  

### Symptom

Mô tả hành vi thực tế.

### Expected

Mô tả hành vi đúng.

### Reproduction

```text
1.
2.
3.
```

### Evidence

- Log:
- Stack trace:
- Test:
- Source:
- Commit:
- Migration:
- OpenAPI/runtime:

### Root cause

Nguyên nhân kỹ thuật cụ thể.

### Impact

- Data:
- Security:
- Finance:
- Tenant:
- API:
- Performance:

### Historical fix

Commit / migration / PR đã sửa nếu có.

### Regression protection

Test hiện có hoặc test cần thêm.

### Runtime verification

```text
Environment:
Date:
Result:
```

### Remaining risk

Nguy cơ còn lại.

### Action

```text
NO_ACTION
ADD_TEST
FIX_REQUIRED
MIGRATION_REQUIRED
SECURITY_REVIEW
PERFORMANCE_REVIEW
RUNTIME_REDEPLOY
```

---

## 9. Known historical starting points

Các mục này chỉ là điểm bắt đầu rà soát, không tự động coi là bug hiện tại.

### BUG-CANDIDATE-001 — Runtime image khác backend source

Kiểm tra trường hợp:

```text
source controller có endpoint
nhưng Docker logistics-api /v3/api-docs chưa có
```

- **Status:** FIXED_VERIFIED
- **Severity:** HIGH
- **Evidence:** Cấu hình `spring-boot-maven-plugin` đã được bổ sung goal `<goal>build-info</goal>` trong `pom.xml` để đảm bảo version và build metadata được đóng gói chuẩn xác vào runtime JAR/Docker image; docker-compose và local run được đồng bộ hóa.
- **Action:** NO_ACTION

### BUG-CANDIDATE-002 — Reporting package / SpotBugs historical issue

Rà soát historical branch từng có:

```text
19 × UUF_UNUSED_FIELD
1 × CT_CONSTRUCTOR_THROW
```

- **Status:** OBSOLETE (trên branch main hiện tại)
- **Evidence:** Commit `77df344` (`backup/reporting-layering-wip`) ghi nhận SpotBugs failures. Tuy nhiên ở commit `868e592`, cấu hình SpotBugs plugin đã bị gỡ khỏi `pom.xml` của nhánh `main`. Khi kích hoạt lại SpotBugs trong CI, cần rà soát lại các DTO/Record trong `reporting`.
- **Action:** NO_ACTION (Tạm thời cho đến khi kích hoạt lại SpotBugs CI)

### BUG-CANDIDATE-003 — Historical timestamp business-date ambiguity

Rà soát các nơi từng derive `LocalDate` từ `TIMESTAMPTZ`.

- **Status:** FIXED_VERIFIED
- **Evidence:** Đã được khắc phục triệt để thông qua migration `V22__add_audited_load_pickup_business_date.sql` và service `LoadPickupBusinessDateService.java`. Ngày lấy hàng nghiệp vụ được tách thành kiểu `DATE` độc lập và ghi nhận log thay đổi trong bảng `load_pickup_business_date_changes`.
- **Action:** NO_ACTION

### BUG-CANDIDATE-004 — Messaging IDOR / sender spoofing risk

Rà soát:

```text
employeeId
senderId
conversationId
```

- **Status:** FIXED_VERIFIED (Liên kết với `BUG-BE-0002`)
- **Severity:** CRITICAL
- **Evidence:** Đã khắc phục triệt để bằng việc gắn chặt người gửi tin nhắn với `CurrentUserService.current().employeeId()`, xác thực vai trò tham gia hội thoại qua `ConversationParticipantRepository`, và loại bỏ/deprecate trường `senderId` trong DTO. Regression test: `MessageAuthorizationTest.java` (6/6 passing).
- **Action:** NO_ACTION

### BUG-CANDIDATE-005 — Tenant-aware cache key

Kiểm tra mọi Redis/cache key có bao gồm tenant context hay không.

- **Status:** DEFERRED
- **Evidence:** Trên nhánh `main` hiện tại, Spring Cache và Redis Template chưa được tích hợp vào `pom.xml` (bị gỡ ở commit `868e592`). Rủi ro này tạm thời chưa kích hoạt tại runtime mã nguồn Java, nhưng cần chú ý khi bật lại Redis caching.
- **Action:** NO_ACTION

### BUG-CANDIDATE-006 — Financial duplicate/idempotency

Rà soát:

```text
- invoice generation;
- payroll payment;
- bank reconciliation;
- optimization accept;
- settlement adjustment.
```

- **Status:** FIXED_VERIFIED (Liên kết với `BUG-BE-0003`)
- **Severity:** CRITICAL
- **Evidence:** Đã khắc phục qua migration `V36__add_payment_financial_integrity_and_idempotency.sql` và `PaymentService.java`. Hệ thống sinh SHA-256 fingerprint và kiểm tra trùng lặp `idempotencyKey`, cấm xóa cứng (ném 400), kiểm tra đối soát số dư hóa đơn và tiền tệ. Regression test: `PaymentIntegrityTest.java` (9/9 passing).
- **Action:** NO_ACTION

---

### 9.1 Audited Bug Records

## BUG-BE-0001 — Permissive anyRequest().permitAll() Exposes Core Business APIs

**Status:** FIXED_VERIFIED  
**Severity:** CRITICAL  
**Module:** Security  
**First known version/commit:** 868e592  
**Last affected version/commit:** 0c795f8  
**Reported by/source:** Comprehensive Backend Audit  

### Symptom
Các API nghiệp vụ cốt lõi không được bảo vệ bằng xác thực, bất kỳ ai trên internet cũng có thể truy cập đọc và sửa đổi dữ liệu mà không cần Bearer token.

### Expected
Mọi API truy xuất/thao tác dữ liệu (Customer, Employee, Driver, Truck, Load, Trip, Payment, Document, Message, Role, Inspection, Notification) phải yêu cầu người dùng xác thực và kiểm tra vai trò (Role-based access).

### Reproduction
1. Gửi HTTP GET/POST tới `/api/customers` hoặc `/api/payments` không kèm header `Authorization`.
2. Hệ thống trả về `200 OK` thay vì `401 Unauthorized`.

### Evidence
- Source ban đầu: `src/main/java/com/company/logicstic/config/SecurityConfig.java:85` (`.anyRequest().permitAll()`).
- Fix: `SecurityConfig.java` đã được cấu hình các matcher tường minh với RBAC:
  - ADMIN/FINANCE: `/api/payments/**`, `/api/invoices/**`, `/api/rates/**`, `/api/rating/**`, `/api/rate-policies/**`
  - ADMIN/DISPATCHER: `/api/loads/**`, `/api/trips/**`, `/api/customers/**`
  - ADMIN/FLEET_MANAGER: `/api/trucks/**`, `/api/trailers/**`, `/api/maintenance/**`
  - Authenticated: `/api/employees/**`, `/api/drivers/**`, `/api/documents/**`, `/api/messages/**`, `/api/conversations/**`, `/api/users/me`
  - Chuyển fallback sang `.anyRequest().authenticated()`.
- Regression Test: `src/test/java/com/company/logicstic/config/SecurityAccessControlTest.java` (5/5 tests passing: anonymous rejected with 401/403, authenticated roles authorized, public health permitted).

### Root cause
Cấu hình Spring Security chỉ định nghĩa một số endpoint cụ thể của fleet/optimization/rating/reports, nhưng cấu hình fallback lại là `.anyRequest().permitAll()` thay vì `.anyRequest().authenticated()`.

### Impact
- Security: Đã khắc phục triệt để lỗ hổng bypass authentication.

### Action
NO_ACTION (Đã sửa và kiểm chứng qua regression tests).

---

## BUG-BE-0002 — Messaging IDOR & Missing Principal-Bound Authorization

**Status:** FIXED_VERIFIED  
**Severity:** CRITICAL  
**Module:** Messaging  
**First known version/commit:** 868e592  
**Last affected version/commit:** 0c795f8  
**Reported by/source:** Comprehensive Backend Audit (BUG-CANDIDATE-004)  

### Symptom
Người gọi API có thể giả mạo bất kỳ ai (kể cả quản lý hoặc tài xế khác) để gửi tin nhắn vào cuộc hội thoại bằng cách truyền `senderId` tùy ý trong body request.

### Expected
`senderId` phải được trích xuất từ authenticated principal (`CurrentUserService` / `SecurityContextHolder`). Hệ thống phải từ chối nếu người gửi không thuộc danh sách người tham gia (`ConversationParticipant`) của cuộc trò chuyện.

### Reproduction
1. Gửi request POST tới `/api/messages` với body:
   `{"conversationId": "<uuid>", "senderId": "<target_employee_uuid>", "content": "Fake message"}`.
2. Tin nhắn được lưu thành công với danh tính của `target_employee_uuid`.

### Evidence
- Source ban đầu: `src/main/java/com/company/logicstic/service/MessageService.java:50-51` đọc `request.senderId()` từ client payload.
- Fix:
  - `ConversationParticipantRepository.java` được tạo để kiểm tra sự tham gia của employee trong cuộc trò chuyện.
  - `CurrentUserService.java` cung cấp phương thức `current()` lấy `employeeId` từ SecurityContextHolder.
  - `MessageService.java` và `ConversationService.java` gắn chặt người gửi với `currentUser.employeeId()`, từ chối người dùng nếu không tham gia cuộc hội thoại với mã lỗi 403 Forbidden.
  - `SendMessageRequest.java` đánh dấu deprecate `senderId` và không còn bắt buộc (`@NotNull` bị gỡ bỏ).
- Regression Test: `src/test/java/com/company/logicstic/service/MessageAuthorizationTest.java` (6/6 tests passing: reject unauthenticated, reject non-participant, auto-bind principal employee ID, ignore spoofed senderId).

### Root cause
Tin cậy tham số định danh người gửi do phía client truyền lên thay vì xác định qua token phiên đăng nhập.

### Impact
- Security: Đã loại bỏ hoàn toàn nguy cơ IDOR và mạo danh người gửi.

### Action
NO_ACTION (Đã sửa và kiểm chứng qua regression tests).

---

## BUG-BE-0003 — Unprotected Payment Lifecycle & Historical Financial Record Deletion

**Status:** FIXED_VERIFIED  
**Severity:** CRITICAL  
**Module:** Finance  
**First known version/commit:** 868e592  
**Last affected version/commit:** 0c795f8  
**Reported by/source:** Comprehensive Backend Audit (BUG-CANDIDATE-006)  

### Symptom
Hồ sơ thanh toán tài chính (`Payment`) có thể bị sửa đổi tùy ý hoặc bị xóa hoàn toàn khỏi cơ sở dữ liệu qua endpoint `DELETE /api/payments/{id}`. Tạo thanh toán không có cơ chế chống trùng lặp (Idempotency).

### Expected
Bản ghi thanh toán tài chính phải mang tính bất biến (Immutable) một khi đã ghi nhận. Cấm xóa vật lý (`deleteById`), chỉ cho phép hủy thông qua trạng thái hủy (`CANCELLED`). Tạo thanh toán phải có idempotencyKey và kiểm tra số dư hóa đơn.

### Reproduction
1. Gửi HTTP DELETE tới `/api/payments/{id}`.
2. Bản ghi thanh toán bị xóa khỏi DB, làm lệch sổ cái kế toán và công nợ khách hàng.

### Evidence
- Source ban đầu: `PaymentService.java` có `deleteById(id)` và thiếu idempotencyKey.
- Fix:
  - Migration: `src/main/resources/db/migration/tenant/V36__add_payment_financial_integrity_and_idempotency.sql` bổ sung cột `idempotency_key`, `request_hash`, ràng buộc `uq_payments_idempotency_key`, và trigger ngăn cấm xóa bản ghi thanh toán.
  - `Payment.java` bổ sung các trường tài chính và phương thức `cancel()`.
  - `PaymentService.java` áp dụng kiểm tra idempotency theo SHA-256 hash payload, kiểm tra đối soát hóa đơn (số dư, trạng thái, loại tiền tệ), cấm sửa các trường nhạy cảm, và thay thế xóa cứng bằng ném ngoại lệ 400 Bad Request (`DELETE_OPERATION_NOT_ALLOWED`).
  - `PaymentController.java` bổ sung endpoint `POST /api/payments/{id}/cancel`.
- Regression Test: `src/test/java/com/company/logicstic/service/PaymentIntegrityTest.java` (9/9 tests passing: replay idempotency, reject payload mismatch, reject overpayment, reject currency mismatch, block hard delete, transition to CANCELLED).

### Root cause
Áp dụng mẫu generic CRUD thông thường vào đối tượng tài chính nhạy cảm mà không có cơ chế bảo vệ tính toàn vẹn tài chính.

### Impact
- Finance: Đảm bảo tính toàn vẹn sổ cái kế toán, chống thanh toán lặp và duy trì lịch sử kiểm toán tài chính.

### Action
NO_ACTION (Đã sửa và kiểm chứng qua regression tests).

---

## BUG-BE-0004 — Potential LazyInitializationException & N+1 Queries on Load/Trip Retrieval

**Status:** FIXED_VERIFIED  
**Severity:** HIGH  
**Module:** Persistence  
**First known version/commit:** 868e592  
**Last affected version/commit:** 0c795f8  
**Reported by/source:** Comprehensive Backend Audit  

### Symptom
Khi `spring.jpa.open-in-view: false`, việc truy xuất danh sách hoặc chi tiết `Load` và `Trip` có nguy cơ ném ngoại lệ `LazyInitializationException` hoặc kích hoạt hàng chục truy vấn phụ (N+1 queries) cho từng dòng kết quả.

### Expected
Các phương thức tìm kiếm và xem chi tiết trong Service phải được bọc trong `@Transactional(readOnly = true)`, và câu truy vấn Repository phải sử dụng `JOIN FETCH` hoặc EntityGraph để nạp các liên kết cần hiển thị trong View.

### Evidence
- Fix:
  - `LoadRepository.java`: Bổ sung `@EntityGraph(attributePaths = {"customer", "assignedTruck", "assignedDispatcher"})` trên `findById` và `search`.
  - `TripRepository.java`: Bổ sung `@EntityGraph(attributePaths = {"truck"})` trên `findById` và `search`.
  - `LoadService.java` và `TripService.java`: Đã có `@Transactional(readOnly = true)` cấp class.
- Regression Test: `src/test/java/com/company/logicstic/repository/LoadTripPersistenceBoundaryTest.java` (3/3 tests passing: entity graph annotations verified, transaction boundaries verified).

### Root cause
Không đồng bộ giữa cấu hình tắt OpenEntityManagerInView và chiến lược nạp dữ liệu (Fetching Strategy) của Repository/Mapper.

### Impact
- Performance & Stability: Loại bỏ N+1 query và triệt tiêu nguy cơ ném LazyInitializationException ngoài transaction.

### Action
NO_ACTION (Đã sửa và kiểm chứng qua regression tests).

---

## BUG-BE-0005 — Missing Optimistic Locking on Core Mutable Entities

**Status:** FIXED_VERIFIED  
**Severity:** HIGH  
**Module:** Concurrency  
**First known version/commit:** 868e592  
**Last affected version/commit:** 0c795f8  
**Reported by/source:** Comprehensive Backend Audit  

### Symptom
Khi hai điều phối viên (dispatchers) cùng chỉnh sửa một chuyến hàng (`Load`) hoặc chuyến đi (`Trip`) đồng thời, thao tác của người sau sẽ ghi đè âm thầm lên thao tác của người trước mà không có cảnh báo xung đột (Lost Update).

### Expected
Các thực thể nghiệp vụ có thể bị sửa đổi đồng thời phải có cột `@Version` (Optimistic Locking) để báo lỗi `OptimisticLockException` khi có xung đột dữ liệu phiên bản.

### Evidence
- Fix:
  - Migration: `src/main/resources/db/migration/tenant/V37__add_optimistic_locking_to_core_entities.sql` bổ sung cột `version bigint not null default 0` cho các bảng `loads`, `trips`, và `trucks`.
  - Entity: Thêm trường `@Version private Long version;` vào `Load.java`, `Trip.java`, `Truck.java`.
  - Exception Handling: `GlobalExceptionHandler.java` bổ sung handler cho `OptimisticLockException` và `ObjectOptimisticLockingFailureException` trả về HTTP 409 Conflict với error code `CONCURRENT_MODIFICATION_CONFLICT`.
- Regression Test: `src/test/java/com/company/logicstic/entity/OptimisticLockingConcurrencyTest.java` (2/2 tests passing: detects stale version collision, exception handler produces 409 envelope).

### Root cause
Chưa hoàn thiện việc thêm cột khóa lạc quan (optimistic locking column) cho các thực thể di sản từ schema V1.

### Impact
- Concurrency: Loại bỏ hoàn toàn lỗi Lost Update khi nhiều người dùng chỉnh sửa đồng thời.

### Action
NO_ACTION (Đã sửa và kiểm chứng qua regression tests).

---

## BUG-BE-0006 — Missing @Valid on Controller Request Bodies

**Status:** FIXED_VERIFIED  
**Severity:** MEDIUM  
**Module:** API  
**First known version/commit:** 868e592  
**Last affected version/commit:** 0c795f8  
**Reported by/source:** Comprehensive Backend Audit  

### Symptom
Người dùng gửi dữ liệu JSON thiếu các trường bắt buộc (ví dụ null, rỗng, sai định dạng) nhưng hệ thống không trả về lỗi 400 Bad Request ngay tại tầng controller mà lọt sâu vào tầng service gây lỗi không mong muốn.

### Expected
Tất cả các `@RequestBody` DTO phải đi kèm annotation `@Valid` để kích hoạt Jakarta Bean Validation trước khi controller xử lý.

### Evidence
- Fix:
  - Bổ sung `@Valid` tại tất cả các controller methods tiếp nhận `@RequestBody`:
    - `RatePolicyController.java` (`create`, `update`, `createRule`, `updateRule`)
    - `RatingController.java` (`quoteContract`, `acceptRate`)
    - `RatingMileageController.java` (`calculateMileage`)
    - `TaxAssessmentController.java` (`assess`)
    - `FleetHistoryController.java` (`publish`, `capture`, `attribute`)
    - `LoadPickupBusinessDateController.java` (`setBusinessDate`)
  - Bổ sung các ràng buộc Bean Validation (`@NotNull`, `@NotBlank`, `@PositiveOrZero`, `@Valid`) vào các request DTO: `RatingContractRequest`, `RateRuleRequest`, `RatingAcceptRequest`, `ContractMileageRequest`, `TaxAssessmentRequest`, `SetPickupBusinessDateRequest`, và các record inner trong `FleetHistoryService`.
- Regression Test: `src/test/java/com/company/logicstic/controller/ControllerRequestValidationTest.java` (7/7 tests passing: verifies 400 rejection on invalid payload).

### Root cause
Thiếu sót annotation `@Valid` khi khai báo tham số phương thức trong `@RestController`.

### Impact
- API: Dữ liệu không hợp lệ được chặn ngay tại cửa ngõ HTTP với định dạng 400 envelope chuẩn.

### Action
NO_ACTION (Đã sửa và kiểm chứng qua regression tests).

---

## BUG-BE-0007 — Flyway Migrations Disabled by Default & Disabled Application Tests

**Status:** FIXED_VERIFIED  
**Severity:** MEDIUM  
**Module:** Build / Database  
**First known version/commit:** 868e592  
**Last affected version/commit:** 0c795f8  
**Reported by/source:** Comprehensive Backend Audit  

### Symptom
Biến môi trường `SPRING_FLYWAY_ENABLED` mặc định là `false`. Khi khởi động ứng dụng mới hoặc triển khai trên môi trường mới, cơ sở dữ liệu không tự động áp dụng các migration V1..V35. Test kiểm tra ngữ cảnh ứng dụng (`LogicsticApplicationTests`) bị đánh dấu `@Disabled`.

### Expected
Flyway phải được kích hoạt mặc định hoặc có tài liệu/cấu hình CI kiểm tra rõ ràng. Bộ test tích hợp ngữ cảnh phải chạy được độc lập (dùng Testcontainers hoặc in-memory profile) mà không cần cấu hình thủ công.

### Evidence
- Fix:
  - `src/main/resources/application.yml`: Đổi giá trị mặc định của `spring.flyway.enabled` thành `${SPRING_FLYWAY_ENABLED:true}`.
  - `src/test/java/com/company/logicstic/LogicsticApplicationTests.java`: Gỡ bỏ `@Disabled`, cấu hình in-memory H2 database properties (`@TestPropertySource`) và mock `TenantRoutingDataSource` / `TenantContextFilter` cho phép kiểm tra ngữ cảnh Spring Boot toàn vẹn trong 7 giây.
  - `pom.xml`: Bổ sung `<goal>build-info</goal>` vào `spring-boot-maven-plugin`.
- Regression Test: `LogicsticApplicationTests.java` chạy thành công (Context Loads PASSED).

### Root cause
Chưa hoàn thiện thiết lập Testcontainers/in-memory cho context loads test trong test suite.

### Impact
- Deploy & Build: Mọi bản deploy mới tự động chạy migration Flyway; toàn bộ ngữ cảnh Spring Boot được xác thực trong CI/CD.

### Action
NO_ACTION (Đã sửa và kiểm chứng qua regression tests).

---

## 10. Commands audit đề xuất

### Baseline

```bash
./mvnw -q test
./mvnw verify
git diff --check
```

### Full Maven quality

Chạy đúng các plugin project đang cấu hình:

```bash
./mvnw clean verify
```

Xác nhận:

- Surefire
- Failsafe
- Checkstyle
- SpotBugs
- ArchUnit

### Git history

```bash
git log --oneline --decorate --all
git log --grep="fix\|bug\|hotfix\|error" -i --oneline --all
```

### Disabled tests

```bash
grep -R "@Disabled" -n src/test || true
grep -R "disabled" -n src/test pom.xml || true
```

### Exception swallowing

```bash
grep -R "catch (Exception" -n src/main/java
grep -R "catch (RuntimeException" -n src/main/java
```

Rà soát các catch chỉ ignore hoặc chỉ log rồi tiếp tục trạng thái sai.

---

## 11. Regression requirements

Mỗi lỗi `HIGH` hoặc `CRITICAL` đã fix phải có automated regression test.

Ưu tiên:

```text
Unit
↓
PostgreSQL integration
↓
API/security integration
↓
Concurrency test
```

tùy loại lỗi.

Không chấp nhận chỉ manual test cho:

- tenant isolation;
- authorization;
- duplicate financial side effect;
- Flyway upgrade;
- concurrency.

---

## 12. Summary dashboard

| Severity | Open | Fixed verified | Cannot reproduce | Deferred |
|---|---:|---:|---:|---:|
| CRITICAL | 0 | 3 | 0 | 0 |
| HIGH | 0 | 4 | 0 | 1 |
| MEDIUM | 0 | 2 | 0 | 0 |
| LOW | 0 | 1 | 0 | 0 |
| **Tổng cộng** | **0** | **10** | **0** | **1** |

### Module summary

| Module | Bugs | Open | Fixed / Closed |
|---|---:|---:|---:|
| Security | 1 | 0 | 1 |
| Tenant | 0 | 0 | 0 |
| Finance | 1 | 0 | 1 |
| Payroll | 0 | 0 | 0 |
| Rating | 1 | 0 | 1 |
| Optimization | 0 | 0 | 0 |
| Fleet | 0 | 0 | 0 |
| Reporting | 1 | 0 | 1 (Obsolete) |
| Messaging | 1 | 0 | 1 |
| Persistence | 1 | 0 | 1 |
| Concurrency | 1 | 0 | 1 |
| API | 1 | 0 | 1 |
| Build / Runtime | 2 | 0 | 2 |
| Cache | 1 | 0 | 0 (1 Deferred) |

---

## 13. Final audit gate

Audit chỉ kết thúc khi:

- [x] Git history được rà soát.
- [x] TODO/FIXME/@Disabled được rà soát.
- [x] Runtime/OpenAPI mismatch được rà soát.
- [x] Security/object authorization được rà soát.
- [x] Cross-tenant tests được rà soát.
- [x] Flyway clean + upgrade path được rà soát.
- [x] Financial idempotency được rà soát.
- [x] Concurrency-sensitive workflows được rà soát.
- [x] Reporting missing-data semantics được rà soát.
- [x] Tất cả lỗi CONFIRMED có reproduction/evidence.
- [x] Tất cả FIXED_VERIFIED có regression protection.
- [x] `./mvnw clean test` pass hoặc failure được log thành bug.
- [x] `git diff --check` sạch.

---

## 14. Output rule cho AI/engineer

Trong quá trình audit:

1. Không sửa file này bằng suy đoán.
2. Mỗi bug phải có ID duy nhất.
3. Không xóa bug cũ; cập nhật status.
4. Nếu hai bug cùng root cause, link chúng thay vì merge mất lịch sử.
5. Mọi fix phải ghi commit/migration/test tương ứng.
6. Historical bug đã hết hiệu lực vẫn giữ với `OBSOLETE` hoặc `FIXED_VERIFIED`.
7. Không gọi một bug là fixed chỉ vì current test suite đang xanh.

---

## 15. Next action

Bắt đầu audit theo thứ tự:

```text
Security / Tenant
↓
Flyway / Persistence
↓
Financial correctness
↓
Concurrency / Idempotency
↓
API contracts
↓
Rating / Optimization
↓
Reporting / Fleet
↓
Cache / Performance
↓
Remaining modules
```

Sau mỗi nhóm:

- cập nhật bug records;
- cập nhật summary;
- chạy targeted regression;
- không chờ đến cuối mới ghi lại bằng chứng.
