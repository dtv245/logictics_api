# Exception handling remediation — bàn giao

Ngày 2026-10-13. Người dùng đã duyệt kế hoạch. Đã sửa backend/frontend và kiểm tra trong workspace; chưa deploy, merge hoặc publish PR.

Contract: [HTTP error contract](http-error-contract.md). Kế hoạch: [exception remediation plan](exception-handling-remediation-plan.md).

## Kết quả sửa

| Nhóm | Kết quả |
|---|---|
| 1. Handler / security / request ID | Đúng status HTTP; phân biệt validation input/output; 401/403 cùng envelope; request ID kiểm tra ký tự/độ dài, header/meta/MDC thống nhất, dọn MDC; 5xx không lộ message/SQL/provider detail; giữ domain 422 và optimistic locking |
| 2. Pagination / orderBy / báo cáo | Helper phân trang dùng chung; allowlist sort và mapping Load customerId; month/year được kiểm tra; frontend sửa URL/state/current/pageSize, giữ local pagination cho bảng không có controlled current |
| 3. Lark | Phân biệt app credential 502, user credential 401, response sai 502, unavailable 503, timeout 504; rate limit 503 + Retry-After; HTTP error JSON code cũng được phân loại; không giả Base list/delete lỗi là thành công |
| 4. Database | SQLSTATE 23505/23503 ->409, 23502/23514 không xác định ->500, 40001/40P01 ->503 sau rollback; map constraint sang field theo resource/operation; giữ payment idempotency code; không query DB hoặc automatic retry trong handler |

Frontend bổ sung code/params, bản dịch EN/VI, field errors và Retry-After cho safe read retries. Failure envelope mới luôn có root params (kể cả {}); envelope cũ vẫn giữ thông báo chi tiết làm fallback. Success shape được giữ.

## Checklist trước khi code

- [x] Đọc yêu cầu và flow backend/frontend; xác minh Spring Boot 4.1.1, MVC 7.0.9, Java 21.
- [x] GitNexus impact trước sửa; báo HIGH/CRITICAL, đối chiếu source khi UNKNOWN.
- [x] Lưu các thay đổi có sẵn; giữ tenant/role/permission và command identity.
- [x] Test characterization và test tái hiện trước các nhóm sửa.
- [x] Thống nhất chính sách frontend/backend cho phân trang và code/params.

## Kiểm tra đã thực hiện

| Kiểm tra | Kết quả / giới hạn |
|---|---|
| Backend full Maven clean verify | **636 test, 0 failure, 0 error, 0 skip**, 107 suite; gồm **227 test PostgreSQL** / 25 suite |
| Wrapper scripts/verify_backend_regression.py | **Chưa đạt gate** sau khi Maven chạy thành công: yêu cầu MessagingAuthorizationPostgresTest tối thiểu 5 nhưng suite không có trong source checkout. Không hạ baseline hoặc coi gate đã đạt |
| Frontend full Vitest | **1150 pass / 4 fail**, 173 file. Bốn lỗi tái hiện trên baseline trước sửa; sau run full, bổ sung một assertion contract notification và chạy focused 26/26 pass |
| Frontend typecheck / production build | Đạt; build còn cảnh báo chunk size hiện có |
| Frontend lint | Changed-file lint đạt. Whole-repo **15 lỗi**, cùng 15 lỗi trên baseline độc lập; nằm ngoài file sửa của đợt này |
| Playwright | **10/10 pass**: desktop/mobile URL page=0/NaN và pageSize=NaN, request/UI đồng bộ, chuyển trang; hiển thị 401/403/503 qua real Refine/Table/HTTP adapters với endpoint stub |
| PostgreSQL fixtures | Unique/FK/not-null/check metadata thật; concurrent unique insert; serialization/deadlock rollback; kiểm tra không commit một phần/ghi trùng trong fixture. Chỉ dùng PostgreSQL disposable riêng |
| Patch series | Cả 4 nhóm backend và 3 nhóm frontend apply-check đạt trong detached checkout. Backend từng nhóm compile + focused tests đạt; frontend từng nhóm typecheck + focused tests đạt |
| GitNexus | Reindex hai repo, đọc đủ structured detect_changes: backend 260 symbol / 550 process; frontend 149 symbol / 44 process. **partial=false, truncated=false**, risk CRITICAL. Counts bao gồm các thay đổi có sẵn/concurrent trong workspace, không phải chỉ phần của đợt này |
| Diff hygiene | git diff --check đạt ở cả hai repo |

### Lỗi frontend có sẵn

- App.test: locale/login button.
- AppSider và AppResponsiveShell: label navigation đã được thay đổi trước đợt sửa.
- localeKeys: thiếu settlements.noPoliciesFound và settlements.noSettlementsFound.
- Lint: 15 lỗi trong các file ai-dispatch, optimization, FleetReportPage và test ngoài phạm vi.

Baseline được dựng ở `/tmp/logictics-front-error-baseline`, HEAD 4c19ed8 cộng đúng diff ban đầu. Các test lỗi chạy lại ở đó để phân biệt với hồi quy. Một hồi quy local-table pagination do implementation ban đầu đã được phát hiện, sửa và kiểm tra lại.

### Bằng chứng tái hiện trước sửa

- Characterization list/detail Truck đạt trên hành vi hợp lệ ban đầu.
- HTTP contract có 9 lỗi trước sửa; pagination có 5; Lark có 3; database classifier có 5.
- Rà cuối phát hiện HTTP 400 của Lark chứa user code bị phân loại sai; test mới thất bại trước fix rồi đạt.
- Tenant mismatch được dọn trước khi ghi response; test mô phỏng client disconnect chứng minh cleanup kể cả IOException.

## Gói review / PR / rollback

[README các gói patch](/tmp/exception-remediation-review/README.md) và [manifest](/tmp/exception-remediation-review/manifest.json).

- 01: handler/security/request ID + frontend DTO/parser/i18n.
- 02: pagination/sort/report validation + table/URL/browser contracts.
- 03: Lark + frontend safe read backoff.
- 04: database/concurrency + contract docs; field support dùng nền tảng frontend ở nhóm 01.

Các gói được tạo bằng index tạm, không thay đổi HEAD/index của workspace và không commit phần việc có sẵn. Shared DTO/helpers là dependency của nhóm sau: review/apply theo thứ tự; rollback consumer trước khi rollback nền tảng. Không coi việc revert nền tảng ngoài thứ tự là độc lập an toàn.

Một session khác thêm apiErrorNotification và sửa hooks/modals/notification trong lúc triển khai. Đã giữ nguyên phần đó; main patch series không gom các thay đổi ấy. Patch bổ sung `01-frontend-concurrent-notification-integration.patch` chỉ chứa phần tích hợp code/params của đợt này và test, áp dụng trên notification helper của session đó. Phải đưa dependency notification này vào PR/frontend rollout. Current workspace đã dùng đầy đủ integration.

**Chưa mở draft PR:** gh authentication token không hợp lệ. Các gói local đã sẵn sàng; chưa có thao tác remote. Sau khi authentication được khôi phục, có thể tạo PR theo nhóm, frontend trước backend. Không deploy khi legacy gate chưa được giải quyết.

## Bảo toàn workspace

- Diff ban đầu lưu ở `/tmp/exception-remediation-baseline`.
- Ba file Fleet backend giữ đúng byte nội dung ban đầu; SecurityConfig giữ role matcher đã sửa trước đó, chỉ thêm exposed headers.
- AppSider, appNavigation, ResourceFormFields, resourceForms giữ đúng nội dung ban đầu; deletion .env.test và thay đổi navigation locales được giữ.
- Không dùng database làm việc để chạy fixture.

## Checklist sau khi code

- [x] Code/params và EN/VI; fallback cho envelope cũ; không hardcode thông báo mới vào UI.
- [x] Không đổi business rule tenant/permission/idempotency; không tự retry write.
- [x] Unit/MVC/security/PostgreSQL/browser/typecheck/build và changed-file lint đã chạy.
- [x] Kiểm tra desktop/mobile; field errors và đổi locale qua focused tests.
- [x] Rà diff/graph và kiểm tra từng patch trên checkout riêng.
- [ ] Whole-repo frontend test/lint xanh: còn các lỗi baseline nêu trên.
- [ ] Legacy backend wrapper gate đạt: còn thiếu suite Messaging trong checkout.
- [ ] Draft PR được publish: còn thiếu gh authentication hợp lệ.
- [ ] Chưa xác minh production artifact, Lark credentials thật, tracing bridge và hạ tầng triển khai.

## Log/artifact local

- Backend: `/tmp/remediation-backend-full.log`, `/tmp/logisticsx-regression-5blpw2jw/maven.log`; XML trong target/surefire-reports.
- Frontend: `/tmp/remediation-frontend-full-final.log`, `/tmp/remediation-frontend-baseline.log`, `/tmp/remediation-notification-final.log`, `/tmp/remediation-frontend-build-final.log`.
- Lint: `/tmp/remediation-lint-changed-final.log`, `/tmp/remediation-lint.log`, `/tmp/remediation-frontend-lint-baseline.log`.
- Browser: `/tmp/remediation-playwright-final.log`.
- Graph: `/tmp/remediation-backend-gitnexus-complete.json`, `/tmp/remediation-frontend-gitnexus-complete.json`.
- Patch compile/test: `/tmp/exception-remediation-review/*-verification.log` và `*-patch-verification.json`.
