# Kế hoạch sửa exception, validation và thông báo lỗi

Ngày: 2026-10-13. Trạng thái: **ĐÃ ĐƯỢC NGƯỜI DÙNG DUYỆT — đã triển khai và kiểm tra; các gate/PR còn thiếu được ghi trong status**.

## 1. Bằng chứng và phạm vi

- Backend: `/home/vumoi/logictics_api`; frontend: `/home/vumoi/logictics-app`.
- `pom.xml` khai báo Spring Boot **4.1.1**, Java **21**. Classpath của lần kiểm thử local sử dụng Spring Framework / Spring MVC **7.0.9**. Đây là phiên bản build local đã kiểm tra; chưa xác minh artifact đang chạy ở production.
- Review trước đã chạy 21 test hiện có, không có failure/error/skip. Probe MVC riêng trong `/tmp` xác nhận sai UUID, thiếu query parameter, sai HTTP method, sai Content-Type, phân trang sai và tháng 13 bị trả 500. Timeout Lark bị chuyển thành 401. Chưa chạy toàn bộ integration test PostgreSQL.
- GitNexus: `GlobalExceptionHandler` có risk **HIGH**, 17 phụ thuộc trực tiếp theo graph. `ResponseMeta` có risk **CRITICAL**, tham gia 110 process theo kết quả đã đọc. Spring gọi advice/controller qua framework; các kết quả UNKNOWN hoặc luồng không được graph tìm thấy không có nghĩa là ít ảnh hưởng.
- Frontend đã có `normalizePage` và `normalizePageSize` trong `src/providers/api/querySerializer.ts`: page 0 được chuyển thành 1; page size được giới hạn 1–100. Test hiện có xác nhận các quy tắc này.
- `src/table/BaseTable.tsx` truyền các giá trị từ `tableProps.pagination` vào Table mà chưa chuẩn hóa current/pageSize ở biên render. Cảnh báo Ant Design chưa chứng minh request API gửi page=0/NaN; cần test cả state, URL và request thực tế để xác định nguồn.
- Luồng Truck đang dùng `Sort.by` -> `Pageable` -> JPQL qua Spring Data. Chưa có bằng chứng SQL injection trong luồng đã kiểm tra. Vẫn ưu tiên kiểm tra mọi đường đi của orderBy và áp dụng allowlist ở backend.
- SecurityConfig đã cấu hình authentication entry point và access denied handler qua `SecurityResponses.write`; cần đồng bộ request ID và contract, không dựa vào advice để xử lý exception trong filter.
- Classpath có Micrometer Observation, chưa thấy tracing bridge/exporter hoặc cấu hình OpenTelemetry trong mã/cấu hình đã rà soát. Không đồng nhất Observation với distributed tracing.

Phạm vi sửa gồm exception HTTP, request ID/log, validation và sort, lỗi tích hợp Lark, phân loại lỗi database và frontend tiêu thụ những response này. Mỗi nhóm được review, kiểm thử và hoàn tác độc lập.

## 2. Contract phản hồi và i18n

- Sử dụng envelope hiện có: `success`, `code`, `message`, `data`, `errors`, `meta`.
- Bổ sung `params` tùy chọn ở cấp lỗi tổng và lỗi từng trường; giữ message làm fallback an toàn và giữ các mã lỗi nghiệp vụ hiện có.
- Frontend mở rộng types, parser envelope và ApiHttpError để giữ code/params; dùng resolver chung cho i18next ở notification, trạng thái lỗi và form. Logic ứng dụng dựa vào status/code, không parse câu message.
- `errors[]` có `field`, `code`, `message`, `params` khi biết trường liên quan. Field dùng tên DTO/form hoặc nested path, không phải tên bảng/constraint. Khi không đủ thông tin, field=null và hiển thị lỗi chung.
- Params chỉ chứa dữ liệu an toàn cần cho bản dịch, như tên tham số và giới hạn min/max; không trả SQL, token, app secret, rejected row hoặc giá trị nhạy cảm.
- Giữ tương thích theo hướng bổ sung: frontend mới đọc được response cũ không có params, frontend cũ vẫn có message; kiểm thử rollout frontend trước backend khi contract đòi hỏi điều đó.

## 3. Characterization trước implementation

Sau khi kế hoạch được duyệt, ghi lại response hiện tại và thêm test trước khi sửa:

- Các endpoint đại diện: danh sách/detail/create/update Truck hoặc Customer; báo cáo tháng; callback Lark; API có quyền/tenant; thanh toán/hóa đơn/payroll/rating.
- Ghi status, code, cấu trúc response, errors và header có ý nghĩa. Với thành công, ghi nhận đúng shape hiện tại của từng endpoint, gồm cả list trả PagedResponse trực tiếp.
- Timestamp, UUID tạo động và request ID được kiểm tra theo cấu trúc/quy tắc, không snapshot nguyên giá trị ngẫu nhiên.
- Phân biệt test bảo vệ hành vi hiện tại hợp lệ với test tái hiện bug. Test bug phải chứng minh trước khi sửa và pass sau khi sửa; không coi response sai hiện tại là contract cần giữ.

## 4. Đợt PR 1 — Handler, security, request ID và contract lỗi chung

### Thay đổi

- Xử lý ngoại lệ HTTP của Spring MVC 7, giữ status và header tương ứng; đưa response vào envelope chung.
- Bao phủ `NoHandlerFoundException` và `NoResourceFoundException` cho 404 trong context ứng dụng thật. Với route được bảo vệ, auth/authorization có thể trả 401/403 trước khi MVC xác định route.
- Xử lý `MethodArgumentNotValidException`, `HandlerMethodValidationException` và validation qua AOP nếu luồng thực tế có sử dụng. Validation đầu vào -> 400; validation đầu ra -> 500.
- Giữ các domain exception và status đặc thù, gồm 422, và các lỗi optimistic locking hiện có.
- Bộ lọc request ID chạy trước Spring Security và filter ứng dụng cần ghi log. Nhận ID khớp `[A-Za-z0-9._:-]{1,120}`; thiếu/sai thì tạo ID mới, không cắt rồi dùng ID bị hỏng.
- Lưu ID ở request attribute; response header `X-Request-Id` và `meta.requestId` dùng cùng giá trị. MDC có requestId; khôi phục/dọn khóa đã đặt trong finally, kể cả luồng bị từ chối sớm. Bao phủ error dispatch; nếu có async dispatch thì kiểm tra việc truyền context và dọn đúng thread.
- Nếu môi trường thực tế đã có tracer, tôn trọng traceId do tracer tạo và liên kết requestId trong log, không ghi đè traceId bằng header do client gửi.
- CORS cho phép frontend đọc X-Request-Id và Retry-After khi các header này xuất hiện.
- EntryPoint -> 401; AccessDeniedHandler -> 403; filter tenant giữ mã lỗi ranh giới tenant hiện có và dùng cùng cơ chế response/request ID.
- Phân loại exception tại filter token: token sai/hết hạn là lỗi xác thực; lỗi xử lý ngoài dự kiến có đường trả 5xx an toàn và log phù hợp.
- 4xx dự kiến: INFO hoặc WARN theo nguyên nhân, không stack trace gây nhiễu. 5xx: ERROR và stack trace đã bảo vệ thông tin nhạy cảm; SQLSTATE/constraint thuộc log nội bộ, không log nguyên row/provider response/token.
- Handler không gọi repository/database. Phân loại dựa vào exception và cấu hình tĩnh, kể cả transaction đã rollback-only.
- Frontend nhận code/params và dịch lỗi chung, lỗi trường bằng i18next; có fallback cho mã chưa được biết.

### Contract test bắt buộc

| Trường hợp | HTTP / contract dự kiến |
|---|---|
| UUID/query sai kiểu, thiếu query/header | 400; mã ổn định; errors chỉ rõ tham số khi biết |
| JSON sai cú pháp/body thiếu | 400 MALFORMED_REQUEST |
| Body sai kiểu một trường | 400 MALFORMED_REQUEST; có field khi xác định được |
| Validation body, nested field hoặc object-level | 400 VALIDATION_FAILED; đủ chi tiết |
| Method validation đầu vào | 400 VALIDATION_FAILED |
| Method validation giá trị trả về | 500 INTERNAL_ERROR |
| Anonymous/token không hợp lệ tới API bảo vệ | 401 UNAUTHORIZED; envelope và request ID |
| Đã xác thực nhưng thiếu quyền | 403 FORBIDDEN; không bị chuyển thành 500 |
| Tenant thiếu/sai/không sẵn sàng | Đúng mã và 403/503 của domain hiện có |
| Route/resource không tồn tại sau kiểm tra quyền | 404; envelope |
| Sai HTTP method | 405; giữ Allow |
| Media type không được hỗ trợ/không chấp nhận | 415/406 khi còn có thể thương lượng response; smoke test |
| Ngoại lệ file quá lớn nếu có luồng multipart | 413; smoke test, không mở thêm tính năng upload |
| Lỗi lập trình ngoài dự kiến | 500; không lộ chi tiết nội bộ |

Mỗi dòng có assertion status, code, shape, request ID và header liên quan. Test request ID thiếu/sai/quá dài, ID hợp lệ được giữ, không chèn log, không rò MDC sang request tiếp theo. 406/413/415 chỉ test đủ cho handler; trọng tâm là 400/401/403/404/500.

## 5. Đợt PR 2 — Phân trang, orderBy và kiểm tra tham số báo cáo; sửa frontend cùng đợt

### Quyết định phân trang

Chọn chuẩn hóa số nguyên ngoài giới hạn để phù hợp normalizer frontend hiện có, thay cho đề xuất cũ từ chối mọi giá trị bằng 400:

| API danh sách bắt đầu từ trang 1 | Backend / frontend dự kiến |
|---|---|
| Thiếu page/pageSize | page=1, pageSize=20 |
| page là số nguyên <=0 | Chuẩn hóa thành 1 |
| pageSize là số nguyên <=0 | Chuẩn hóa thành 1, phù hợp helper frontend hiện có |
| pageSize >100 | Giới hạn về 100 |
| URL/UI state NaN/Infinity/không hợp lệ | Frontend đưa về page=1/pageSize=20 trước render và gửi request |
| Request trực tiếp chứa page=NaN, abc, số thập phân hoặc vượt kiểu số hỗ trợ | Backend 400, chỉ rõ tham số |

- API đối soát payroll hiện bắt đầu từ trang 0 (`openCases` và frontend `page+1` khi hiển thị). Lập inventory và kiểm thử phép chuyển đổi này riêng, không áp dụng quy tắc trang 1 lên API trang 0.
- Chuẩn hóa state của Refine, tham số URL, current/pageSize của Ant Design và query gửi API theo cùng quy tắc; kiểm tra thay đổi filter, reload, back/forward, tổng kết quả bằng 0 và đổi pageSize.
- Tạo helper/policy sort dùng chung; mỗi resource khai báo allowlist hoặc map public field -> thuộc tính entity được phép. Tên trường không được phép -> 400 với field=orderBy. Giá trị thiếu dùng default của resource; giá trị whitespace hoặc expression không hợp lệ bị từ chối.
- Đối chiếu allowlist backend với resource definitions frontend; kiểm tra truy vấn native/dynamic nếu có, loại bỏ đường ghép tên sort từ input không được ánh xạ.
- Kiểm tra month/year tại đầu vào của báo cáo trước khi tạo ngày; tháng 13 -> 400 với field=month.

### Kiểm thử / triển khai

- Test cùng tập dữ liệu đầu vào ở helper frontend, state bảng, request serializer và backend.
- Test mọi trường sort được UI hỗ trợ, sort không cho phép, whitespace và payload chứa biểu thức.
- E2E các trang danh sách đại diện: không có cảnh báo current không dương; network query và UI dùng cùng trang/kích thước; URL không hợp lệ vẫn hiển thị đúng.
- Bàn giao PR frontend/backend đi cùng đợt; chuẩn bị frontend trước và xác nhận tương thích trước triển khai backend. Có thể hoàn tác từng repo vì backend vẫn chuẩn hóa numeric pagination.

## 6. Đợt PR 3 — Lark: lỗi người dùng, cấu hình hệ thống và upstream

| Nguyên nhân | HTTP / mã đề xuất |
|---|---|
| Authorization code/user token bị xác nhận không hợp lệ | 401 / mã xác thực ổn định |
| Thiếu cấu hình app ở server | 500 LARK_CONFIG_ERROR |
| Lark từ chối app ID/app secret của hệ thống | 502 LARK_APP_AUTH_FAILED |
| Phản hồi Lark sai/không đủ contract | 502 LARK_UPSTREAM_INVALID_RESPONSE |
| Không kết nối được/tạm thời không có dịch vụ | 503 LARK_UNAVAILABLE |
| Upstream timeout | 504 LARK_TIMEOUT |
| Lark HTTP 429 hoặc mã rate limit được xác định theo contract provider | 503 LARK_RATE_LIMITED; Retry-After đã kiểm tra |

- Phân loại theo endpoint app/user, HTTP status và code Lark, không gộp mọi upstream 401 thành người dùng sai token.
- Chuẩn hóa Retry-After từ provider nếu hợp lệ; dùng khoảng chờ mặc định được cấu hình nếu provider thiếu; không chuyển nguyên header tùy ý ra client.
- Response dùng code/params và message an toàn; log nội bộ có context đã che dữ liệu nhạy cảm.
- Frontend hiển thị đúng bản dịch; 502/503/504 không kích hoạt đăng xuất hoặc vòng refresh token; tôn trọng Retry-After.
- Kiểm thử app credential bị từ chối, user code/token sai, response rỗng, timeout, connect failure, upstream 5xx và 429. Không tự retry callback đổi code/token đã sử dụng; kiểm tra retry đang tồn tại theo contract provider.

## 7. Đợt PR 4 — Database và lỗi đồng thời

Phân loại dựa vào SQLSTATE/metadata PostgreSQL, exception Spring và allowlist ánh xạ constraint -> field/domain. Handler không truy vấn database.

| SQLSTATE / exception | Chính sách |
|---|---|
| 23505 unique_violation | 409; code ổn định; errors theo field DTO nếu có mapping |
| 23503 foreign_key_violation | 409; mapped field khi biết; delete bị tham chiếu có thể là lỗi chung |
| 23502 not_null_violation / 23514 check_violation | Chỉ chuyển thành 4xx khi constraint có ánh xạ nghiệp vụ rõ; trường hợp lỗi backend/không xác định -> 500 |
| 40001 serialization_failure / 40P01 deadlock_detected | 503 TRANSACTION_RETRY_REQUIRED sau rollback; đánh dấu khả năng thử lại theo contract, giữ idempotency key |
| OptimisticLockException / ObjectOptimisticLockingFailureException / stale ExpectedVersionGuard | 409 VERSION_CONFLICT; errors=[]; params.conflictType=VERSION; params.legacyCode=CONCURRENT_MODIFICATION_CONFLICT |
| DataIntegrityViolationException không xác định được nguyên nhân | 500 INTERNAL_ERROR; log chẩn đoán an toàn |

- Mapping tĩnh theo constraint và operation; composite unique có thể sinh lỗi cho nhiều field. Constraint không được map thì trả lỗi chung, không đoán từ câu SQL/message.
- Mã miền hiện có, đặc biệt payment idempotency và immutable financial history, có ưu tiên so với lỗi database chung.
- Frontend hiển thị lỗi unique/FK dưới trường khi có mapping, giữ draft và không dựa vào message để quyết định hành vi.
- Handler chỉ báo lỗi giao dịch có thể thử lại. Chưa thêm automatic retry cho write: chỉ triển khai retry tại service/transaction boundary khi đã chứng minh rollback, idempotency và không lặp side effect ngoài database. Không retry trong transaction đã hỏng.
- Test PostgreSQL thật trong database riêng: duplicate unique (kể cả race), FK, not-null/check có/không có mapping, optimistic locking; tạo deadlock/serialization fixture có kiểm soát và kiểm tra không ghi trùng/không commit một phần.

## 8. Cách chia PR, hoàn tác và điều kiện hoàn thành

- Bốn đợt theo thứ tự trên. Mỗi đợt có PR backend và PR frontend tương ứng khi cần; không tạo một PR trộn hai repo. Characterization và contract tests nằm cùng PR bảo vệ hành vi của nhóm đó.
- Mỗi PR có description nêu trigger, response trước/sau, contract bổ sung và evidence; có thể revert độc lập. Phần params là tùy chọn và message fallback giúp frontend/backend ở hai phiên bản vẫn tương thích.
- Trước khi sửa từng symbol: GitNexus impact, báo HIGH/CRITICAL, xác minh UNKNOWN bằng source/text khi cần. Trước commit: detect-changes; partial/truncated không được xem là sạch.
- Backend: targeted contract tests và hồi quy auth/tenant/payment/invoice/payroll/rating; integration PostgreSQL cho nhóm database.
- Frontend: test serializer/envelope/error adapter/i18n/form/table, typecheck và kiểm tra trang danh sách; E2E tập trung vào URL/state/network và 401/403/503.
- Không lấy “test xanh” chung làm tiêu chí duy nhất: từng dòng contract phải có test tương ứng; thay đổi status/code/shape ngoài phạm vi phải được giải thích hoặc sửa trước bàn giao.
- Bàn giao kết quả theo từng đợt: file thay đổi, contract mới, test pass/fail/skip, phần chưa xác minh và cách hoàn tác.

## 9. Tài liệu đối chiếu

- Spring MVC validation: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html
- Spring 7.0.9 NoResourceFoundException: https://docs.spring.io/spring-framework/docs/7.0.9/javadoc-api/org/springframework/web/servlet/resource/NoResourceFoundException.html
- Spring 7.0.9 HandlerMethodValidationException: https://docs.spring.io/spring-framework/docs/7.0.9/javadoc-api/org/springframework/web/method/annotation/HandlerMethodValidationException.html
- PostgreSQL SQLSTATE: https://www.postgresql.org/docs/current/errcodes-appendix.html
- Spring Data JPA sorting: https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html
