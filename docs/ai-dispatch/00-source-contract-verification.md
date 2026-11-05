# AI Dispatch — xác minh nguồn và contract (BE-01/02/03/05/17)

Ngày xác minh: 2026-10-24. Phạm vi là backend tại repo `logictics_api`; không sửa frontend.

## Bảo toàn working tree

Working tree trước khi làm đã dirty trên `main...origin/main [ahead 4]`. Có thay đổi tracked và untracked ở `.env.example`, `application.yml`, `SecurityConfig.java`, các entity/repository AI, toàn bộ `src/main/java/.../ai/`, V41–V44 và tài liệu. Những thay đổi đó được giữ nguyên. Lượt này chỉ thêm hai class cấu hình và test riêng của BE-09 cùng tài liệu dưới `docs/ai-dispatch/`; không sửa file hiện hữu đang dirty.

GitNexus MCP `query/context/impact` không đọc được index do database version 42 không tương thích runner storage version 40. CLI `impact AiConfiguration --direction upstream` chạy được nhưng trả `risk=UNKNOWN`, không có caller được resolve và có cảnh báo không được coi số 0 là all-clear. Source search cho thấy `AiConfiguration` chỉ enable `AiProperties`; `AiProperties` đang được dùng bởi `AiDispatchService`, `AiGapService` và `LoadEventDebouncer`. Hai symbol này không bị sửa; BE-09 được triển khai bằng class cấu hình mới, không thay đổi bean/config hiện tại.

## Tài liệu đầu vào

| Tài liệu được yêu cầu | Kết quả tìm trong repo | Bằng chứng liên quan |
|---|---|---|
| `API_CONTRACT.md` | KHÔNG TÌM THẤY dưới tên này. | Có `docs/ai-gap-dispatch-api-contract.md`; đây là tài liệu “Thực Tế vs v0” chứa route `/api/ai/...`, legacy semantics và các ví dụ. Không xem là contract đích của API Dispatcher mới. |
| `00-survey-be.md` | KHÔNG TÌM THẤY. | Package, service, test và convention được đọc trực tiếp từ source. |
| PostgreSQL DDL đính kèm | KHÔNG TÌM THẤY dưới tên DDL riêng. | Có DDL trong migration: V1 baseline, V24 snapshots, V30 optimization runs, V32 qualified inputs, V33 acceptance guard, V44 AI gaps/runs. V44 và một số AI source files hiện untracked trong working tree. |
| Tài liệu Optimization | TÌM THẤY. | `docs/optimization-v1-domain-contract.md`; `docs/optimization-policy-decisions.md` ghi OPT-DEC-001…010 là CONFIRMED/LOCKED. Nội dung docs chỉ dùng cùng kiểm tra source, không thay source runtime. |
| Tài liệu design/API AI cũ | TÌM THẤY, nhưng không phải contract đích. | `docs/ai-gap-dispatch-design.md`, `docs/ai-gap-dispatch-api-contract.md`; cả hai đang untracked trong working tree và không bị sửa. |

Các lệnh inventory dùng: `rg --files --hidden -g '!**/.git/**' | rg -i 'api.?contract|survey.?be|postgres.*ddl|ddl.*postgres'`; `rg --files src/main/resources/db/migration/tenant`; `git status --short --branch`.

## BE-01 — nguồn cấu trúc và convention

- Maven là build system; `pom.xml` khai báo Spring Boot 4.1.1 và Java 21. Source nằm dưới `src/main/java/com/company/logicstic/`; tests dưới `src/test/java/com/company/logicstic/`.
- Flyway tenant migrations được cấu hình ở `classpath:db/migration/tenant`; `TenantMigrationService` chạy migration riêng cho từng tenant.
- Chưa thấy Spring Data Redis starter/client trong `pom.xml` hoặc `src/main/java`; Redis service có trong compose ở port đã khai báo trong file.
- `json-schema-validator` hiện chỉ có scope test. Runtime tool registry có schema dạng Map nhưng chưa có validator JSON Schema trong `src/main`.
- AI packages hiện có `ai/config`, `ai/dispatch`, `ai/dispatch/tool`, `ai/dispatch/dto`, `ai/gap`, `ai/llm`, `ai/event`.
- AI tests hiện có ở `src/test/java/com/company/logicstic/ai/`, gồm dispatch service, tool registry, prompt/security, gap, event, controller/tenant và OpenAPI tests.
- `application.yml` dùng các biến môi trường `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`; danh sách key env được xem mà không in giá trị. `.env.example` hiện chưa liệt kê các biến AI provider đang được tham chiếu trong `application.yml`.
- `application.yml` có legacy `app.ai.dispatch-enabled` với expression fallback bật; không thay đổi giá trị/flag này trong lượt xác minh. `ai.dispatch.auto-enabled` chưa có trong source/config cũ; BE-09 mới đặt default false.

## BE-02 — Optimization integration map

Engine đã tồn tại trong source, không cần `OptimizationGateway` stub:

- `OptimizationApplicationService.create(CreateRequest, actor, correlationId)`, `get(UUID)` và `revalidate(...)` là service thật.
- `CreateRequest` yêu cầu idempotency key, `policyId`, targets có load/trip/rating snapshot/pickup stop, danh sách driver/truck và `sourceSelections` gắn scope; scope quá 200 candidates bị từ chối, không truncate.
- `OptimizationAcceptanceService.accept(runId, candidateId, AcceptRequest, actor)` kiểm tenant, feasible candidate, fingerprint, revalidate và ghi accept nguyên tử/idempotent. `AcceptRequest` bắt buộc idempotency key và SHA-256 input fingerprint.
- Migration V30/V32/V33 tạo audit, qualified inputs và acceptance guard; V33 phát SQLSTATE `23514`. V24/V30/V32/V33 bảo vệ dữ liệu/audit append-only bằng trigger.
- Policy service chỉ publish policy tường minh và get theo ID; controller không có list endpoint. Qualified input controller có capture/get theo ID, không có list endpoint. Run yêu cầu `policyId` và source selection IDs cụ thể.
- Tài liệu policy ghi source registrations, status allowlists và published policy là explicit; không có policy mặc định/seed. Không tìm thấy insert seed policy trong migrations.
- Existing Optimization API paths có thật là `/api/optimization/runs`, `/api/optimization/runs/{id}`, `.../accept`, `/api/optimization/policies` và `/api/optimization/qualified-inputs` (kèm `/forecasts`); đây không phải API contract AI Dispatcher.
- **Không đủ bằng chứng để tự chọn policy/evidence cho `run_optimization`**: source không có list/resolve mặc định; cần server-side mapping an toàn hoặc quyết định cấu hình per tenant. Không yêu cầu AI tự đặt ID.
- Conflict cần xử lý trước `propose_assignment`: user instruction hiện tại yêu cầu engine quyết định resource và AI chỉ đề xuất; policy doc OPT-DEC-003 ghi equal-score candidates giữ cùng rank và dispatcher chọn. Không cho LLM chọn một candidate trong nhóm đồng hạng.

## BE-03 — database role và migration credentials

- Khi tenant mode bật, `TenantMigrationService` lấy active tenant registry records rồi tạo temporary datasource để chạy Flyway.
- `TenantDataSourceService.buildTemporaryDataSource()` dùng `TenantRegistryRecord.dbUsername()` cùng password giải mã từ credential registry; cùng builder được dùng để tạo pool tenant runtime. Repo không khai báo migration principal riêng.
- Registry schema lưu `db_username` và encrypted `db_password`; tài liệu này không đọc/in database URL hoặc password.
- Không tìm thấy `GRANT`, `REVOKE`, `OWNER TO` hay migration đổi owner trong tenant/registry migration.
- Không có kết nối DB runtime trong lượt này nên owner/grants thực tế không xác minh được. Dữ kiện “owner postgres, chỉ GRANT postgres” hiện là dữ kiện người dùng cung cấp, chưa được đối chiếu bằng DDL đính kèm hoặc catalog truy vấn.
- Kết luận: BE-03 **BLOCKED** ở bước xác minh role/grants thực tế; source-side audit đã ghi lại. BE-04 permission migration cũng **BLOCKED** cho đến khi biết role tenant runtime và principal có quyền GRANT trên từng DB tenant.

## BE-05 — test trigger scope

- `scripts/seed_trip_stops.sql` tự ghi rõ “TEST-ONLY SEED & SIMULATION TRIGGERS FOR ... API AUTOMATION”. Hai trigger được tạo tại script này; không thấy tên trigger trong Flyway main migrations.
- `trg_trip_auto_seed_stops` chạy AFTER INSERT trips, tự cập nhật `completed_at`/mileage và tạo stops/cost/expense/document liên quan; dữ liệu đó có thể làm acceptance guard từ chối trip đã hoàn tất.
- `trg_trip_driver_assign_auto_fill` gán mileage/effective date nếu field trống.
- Source search không tìm thấy nơi khác cài script; việc một DB ngoài repo đã chạy script vẫn không thể xác minh. Chỉ cleanup trong test/local. Không tạo migration production và không tắt acceptance guard.

## BE-17 — JSON schema và data whitelist

- JSON Schema validator hiện chỉ ở dependency test; production classpath chưa có validator được tìm thấy.
- `AiDispatchToolRegistry` dùng tool cũ (`get_unassigned_loads`, `get_available_trucks`, HOS/distance/metrics/suggestion), log nguyên params, query `findAll()` và có dữ liệu HOS, mileage, cost hardcoded. `get_available_trucks` trả `driverName`.
- `PromptBuilder` có regex redaction phone/email/SSN và bọc bằng `UNTRUSTED_CUSTOMER_DATA`, nhưng chưa dùng `<untrusted>…</untrusted>`; không loại tên/địa chỉ theo whitelist tổng quát.
- Chưa có whitelist field riêng cho LLM hoặc argument JSON Schema validator runtime. Cần BE-18 xác định dependency dùng production; BE-20/21 phải dùng allowlisted DTO không PII.

## Tình trạng AI Dispatch thực tế

- Session/decision entity và repository, gap/history/run entity/repository đã hiện diện trong working tree. `AiDispatchSession.number` ánh xạ read-only (`insertable=false`, `updatable=false`); mode/status/type là `String`/TEXT.
- V44 tạo tenant_id cho AI tables/session/decision; mọi query `ai_*` mới phải tenant-filter. `TenantContext` là ThreadLocal; controller hiện fallback sang default tenant khi context vắng mặt. Async worker mới phải bind/clear tenant tường minh.
- `AiDispatchService.runDispatch` hiện `@Transactional`, tự lọc trucks, dùng `calculateCandidateScore` với fallback/constant inputs và gọi `LlmClient` đồng bộ. `AiDispatchService` không gọi OptimizationApplicationService.
- `DeterministicFakeLlmProvider` hiện là production `@Component`; chưa tìm thấy real Claude/OpenAI-compatible adapter.
- Approve hiện gọi `LoadService.assignTruck` trực tiếp; chưa dùng `OptimizationAcceptanceService`.
- Hiện chưa thấy Redis client, session lock/heartbeat, agent loop, provider router, quota service hoặc quyền `ai-dispatch:view|run|approve`.

## BE-09 đã thực hiện

Đã thêm typed binding `ai.dispatch.*` trong class mới `AiDispatchProperties`, đăng ký bằng `AiDispatchConfiguration`. `auto-enabled` mặc định false; giới hạn chưa có giá trị giữ null; provider/model/key không có default; pricing giữ cấu trúc map nguyên trạng cho tới khi schema pricing được xác nhận. `toString()` che API key, base URL và pricing. Không thay `app.ai`, `application.yml` hoặc `.env.example` đang dirty. Cấu hình mới chưa được nối vào `AiDispatchService`; chưa tuyên bố tính năng AUTO/LLM đã được triển khai. Test/package evidence được ghi trong `PROGRESS.md` sau khi chạy.

## Kết luận kiểm chứng

- **Đã xác minh:** code có engine run/accept; scoring policy và weights có bản quyết định khóa; DDL migrations relevant hiện có; seed script khai báo test-only; tenant migration dùng credential theo tenant registry.
- **Chưa xác minh:** exact REST contract, DDL attachment riêng, DB owner/grant thực tế, các seed trigger đã được cài ở DB nào, provider production credentials, policy/source IDs runtime.
- **Không được tự quyết:** default policy/evidence resolver, xử lý đồng hạng candidate, role mapping và quota thresholds, quyền cấp GRANT, trigger cleanup ngoài local/test.
