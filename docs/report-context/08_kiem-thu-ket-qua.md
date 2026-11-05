# 08. Kiểm thử và kết quả

## 8.1 Các loại kiểm thử có trong source

| Phần | Framework/công cụ | Đo được từ repo | Cách chạy được khai báo | Kết quả phiên khảo sát |
|---|---|---:|---|---|
| Backend Java | Spring Boot Test/JUnit Jupiter, Mockito; có REST Assured, H2 và Testcontainers PostgreSQL trong dependency | 138 file `.java` trong `src/test/java`; 129 file có hậu tố `Test.java` | `./mvnw test`; CI còn có Python verifier và runner PostgreSQL cô lập | NOT RUN |
| Frontend unit/component | Vitest 2.1.8, Testing Library, jsdom | 207 file có `.test.` trong tên dưới `src` | `npm run test` → `vitest run` | NOT RUN |
| Frontend browser E2E | Playwright Test 1.64.0 | 13 file `*.spec.ts/tsx` trong `e2e` | `npm run test:e2e` → `playwright test` | NOT RUN |

Đếm file không đồng nghĩa số test case/assertion; con số test case tổng: KHÔNG TÌM THẤY TRONG CODE theo một phép đếm đã chuẩn hóa. Các số file được đo bằng pathlib trên thư mục source tại thời điểm khảo sát. Dependency và script nằm trong manifest; backend README có số 47 lớp test, nhưng con số đó không khớp working tree hiện tại. (nguồn: `pom.xml:119-165`, `README.md:292-303`, `/home/vumoi/logictics-app/package.json:6-16,51-71`, `src/test/java`, `/home/vumoi/logictics-app/src`, `/home/vumoi/logictics-app/e2e`)

### Lệnh đếm đã dùng

Lệnh chỉ duyệt tên file, không chạy test/build:

```bash
python3 -c 'import pathlib; root=pathlib.Path("src/test/java"); fe=pathlib.Path("/home/vumoi/logictics-app/src"); e2e=pathlib.Path("/home/vumoi/logictics-app/e2e"); print(sum(1 for p in root.rglob("*.java"))); print(sum(1 for p in root.rglob("*.java") if p.name.endswith("Test.java"))); print(sum(1 for p in fe.rglob("*") if p.is_file() and ".test." in p.name)); print(sum(1 for p in e2e.rglob("*") if p.is_file() and p.suffix in (".ts",".tsx") and ".spec." in p.name))'
```

Lệnh đo trên trả lần lượt `138`, `129`, `207`, `13`. Các file fixture/spec helper không cùng hậu tố có thể không được tính là test file.

Lệnh đo file source và LOC ước lượng tại working tree (bỏ dòng rỗng và dòng bắt đầu bằng `//`, `/*`, `*`, `*/`; đây không phải parser ngôn ngữ):

```bash
python3 -c 'import pathlib; b=pathlib.Path("src/main/java"); t=pathlib.Path("src/test/java"); f=pathlib.Path("/home/vumoi/logictics-app/src"); q=pathlib.Path("/home/vumoi/logictics-app/e2e"); fs=lambda r,e=None:[p for p in r.rglob("*") if p.is_file() and (e is None or p.suffix in e)]; loc=lambda ps:sum(1 for p in ps for x in p.read_text(errors="ignore").splitlines() if x.strip() and not x.lstrip().startswith(("//","/*","*","*/"))); bp=fs(b,{".java"}); bt=fs(t,{".java"}); ff=fs(f,{".java",".ts",".tsx",".js",".jsx"}); print(len(bp),loc(bp),len(bt),sum(p.name.endswith("Test.java") for p in bt),loc(bt),len(ff),loc(ff),sum(p.suffix==".tsx" for p in fs(f/"pages",{".tsx"})),sum(".test." in p.name for p in fs(f)),sum(p.suffix in {".ts",".tsx"} and ".spec." in p.name for p in fs(q)))'
```

Output lần lượt là `519 24896 138 129 13117 794 78108 100 207 13`; đây là LOC ước lượng, không phải số dòng executable chính xác.

Lệnh đếm tên bảng được khai báo/xóa trong migration tenant và bảng registry:

```bash
python3 -c 'import pathlib,re; root=pathlib.Path("src/main/resources/db/migration/tenant"); rx=re.compile(r"CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(?:\w+\.)?([\w\"]+)",re.I); dx=re.compile(r"DROP\s+TABLE\s+(?:IF\s+EXISTS\s+)?(?:\w+\.)?([\w\"]+)",re.I); content=lambda p:re.sub(r"--[^\n]*","",p.read_text()); created=set().union(*(set(rx.findall(content(p))) for p in root.glob("V*.sql"))); dropped=set().union(*(set(dx.findall(content(p))) for p in root.glob("V*.sql"))); reg=pathlib.Path("src/main/resources/db/migration/registry/V1__create_tenant_registry.sql").read_text(); rt=set(rx.findall(reg)); current=created-dropped; print(len(created),len(dropped),len(current),len(rt),len(current|rt))'
```

Output `102 4 98 1 99`. Đây là tên bảng theo migration source, không phải schema introspection database đang chạy.

Backend unit/integration test source có JUnit annotations và PostgreSQL/Testcontainers suites; ví dụ `AiDispatchServiceTest`, `TenantRoutingDataSourceTests`, `OptimizationAcceptancePostgresTest`, `LarkEmployeeMappingPostgresTest`. Frontend có component/data-truth tests và E2E specs về keyboard focus, responsive route states, reduced motion, contrast, dashboard panels. Sự tồn tại của test source không phải kết quả PASS. (nguồn: `src/test/java/com/company/logicstic/ai/dispatch/AiDispatchServiceTest.java:57`, `src/test/java/com/company/logicstic/config/TenantRoutingDataSourceTests.java:25`, `src/test/java/com/company/logicstic/integration/OptimizationAcceptancePostgresTest.java:60-70`, `src/test/java/com/company/logicstic/integration/LarkEmployeeMappingPostgresTest.java:95-112`, `/home/vumoi/logictics-app/e2e/keyboard-focus-overlays.spec.ts:21-35`, `/home/vumoi/logictics-app/e2e/reduced-motion.spec.ts:3-17`, `/home/vumoi/logictics-app/e2e/contrast-runtime.spec.ts:31-45`)

## 8.2 Test case đề xuất

Các dòng dưới mang nhãn [ĐỀ XUẤT], chưa khẳng định đã có automation tương ứng. Kỳ vọng nghiệp vụ phải được đối chiếu service/contract và permission; test nên chạy trên test tenant/database cô lập, không dùng dữ liệu production.

| Mã | UC | Kiểu | Bước chính | Kết quả mong đợi |
|---|---|---|---|---|
| [ĐỀ XUẤT] TC-01 | UC-01 | API/auth | Gọi Lark callback với state không hợp lệ hoặc hết hạn | Flow từ chối callback; không tạo login result hợp lệ. |
| [ĐỀ XUẤT] TC-02 | UC-02 | API/tenant | Token tenant A gọi request khi context đã bind tenant B | Request bị từ chối theo mismatch; context được clear sau request. |
| [ĐỀ XUẤT] TC-03 | UC-03 | UI/API | Tạo Customer thiếu name hoặc email sai định dạng | Validation chỉ ra trường lỗi; không ghi record. |
| [ĐỀ XUẤT] TC-04 | UC-04 | UI/API | Tạo Load thiếu address/customer bắt buộc | UI validation/API validation ngăn gửi hoặc trả validation envelope; không tạo Load. |
| [ĐỀ XUẤT] TC-05 | UC-05 | API/timeline | Đọc timeline Load không có event; sau đó đọc Load có event | Empty state rõ ràng; event được sắp xếp theo thời điểm theo contract. |
| [ĐỀ XUẤT] TC-06 | UC-06 | API/state | Thực hiện Stop transition bỏ qua bước (ví dụ PENDING → DEPARTED) | Từ chối chuyển state; status và timestamps không bị cập nhật một phần. |
| [ĐỀ XUẤT] TC-07 | UC-07 | API/AI | Sinh recommendation rồi approve decision stale/đã xử lý | Conflict được trả tường minh; không lặp side effect. |
| [ĐỀ XUẤT] TC-08 | UC-08 | API/optimization | Accept candidate với fingerprint/tenant không khớp; gửi lại cùng idempotency key | Mismatch/conflict an toàn; replay idempotent theo contract. |
| [ĐỀ XUẤT] TC-09 | UC-09 | Permission/UI | User thiếu quyền gọi Documents list/detail/delete | Backend từ chối; UI không hiển thị dữ liệu riêng của tenant khác. |
| [ĐỀ XUẤT] TC-10 | UC-10 | UI/API | Đánh dấu notifications đọc khi danh sách rỗng và khi có unread | Kết quả phản ánh số record cập nhật; empty không sinh record giả. |
| [ĐỀ XUẤT] TC-11 | UC-11 | API/report | Gọi metric có trạng thái unavailable/NOT_IMPLEMENTED | Metric hiển thị unavailable với reason; UI không thay bằng số mẫu. |
| [ĐỀ XUẤT] TC-12 | UC-13 | API/financial | Issue/credit/rebill invoice không đủ điều kiện hoặc lịch sử đã khóa | Giữ nguyên financial chain; trả lỗi có code; không bypass guard. |
| [ĐỀ XUẤT] TC-13 | UC-14 | API/idempotency | Lặp payment command với cùng key và đổi payload | Replay/conflict đúng semantics; không ghi hai lần. |
| [ĐỀ XUẤT] TC-14 | UC-13/14 | API/financial | Payroll callback duplicate, settlement đã lock, hoặc payment chưa được xác nhận | Không nhân đôi payment; không suy ra PAID từ payslip/lock; audit giữ nguyên. |
| [ĐỀ XUẤT] TC-15 | UC-11 | Browser/a11y | Mở Operations ở 320/390/768/1024/1440 px; đi qua menu, modal và widget bằng keyboard | Không tràn ngoài chủ đích; focus rõ; dialog/CTA dùng được; không vi phạm reduced motion. |

Các guard ví dụ tương ứng có trong service hiện tại: tenant mismatch, TripStop transition validation, AI decision revalidation, optimization acceptance validation, payment idempotency/financial guard và immutable settlement/payroll history. Test case ở bảng là đề xuất bổ sung hoặc xác minh lại, không báo trạng thái test đã đạt. (nguồn: `src/main/java/com/company/logicstic/config/TenantJwtClaimFilter.java:38-66`, `src/main/java/com/company/logicstic/service/TripStopService.java:92-148`, `src/main/java/com/company/logicstic/ai/dispatch/AiDispatchService.java:221-300`, `src/main/java/com/company/logicstic/service/optimization/OptimizationAcceptanceService.java:34-84`, `src/main/java/com/company/logicstic/service/payroll/payment/PayrollPaymentSchedulingService.java:26-43`)

## 8.3 Số liệu đo từ source

| Chỉ số | Kết quả hiện tại | Phương pháp / giới hạn |
|---|---:|---|
| Bảng theo migration | 99 tính registry + migration history; 98 bảng ứng dụng nếu bỏ `__EFMigrationsHistory` | Parse 44 tenant migrations, bỏ comment và trừ bốn bảng được drop ở V40, cộng registry. Đây không phải live DB introspection. |
| Endpoint mapping backend | 200 handler methods; 203 declarations mở rộng path; 201 cặp HTTP method-path duy nhất | Static annotation scan, chưa xác nhận route runtime; có 2 cặp duplicate. |
| Controller files | 47 `*Controller.java` | Path scan bằng pathlib/rg. |
| Entity class files | 69 class file có `@Entity` | Count annotation trong backend Java source, không đồng nghĩa số table vì có view/schema variants. |
| Backend Java production | 519 `.java` files; 24,896 dòng nonblank không bắt đầu comment | Ước lượng bằng pathlib; loại blank và line-leading `//`, `/*`, `*`, `*/`; không thay LOC parser. |
| Backend test source | 138 `.java`; 129 `*Test.java`; 13,117 dòng ước lượng theo cùng cách | Không phải số test cases. |
| Frontend pages | 100 `.tsx` dưới `src/pages` | File count; includes CRUD wrappers. |
| Frontend TypeScript/TSX source | 794 `.ts/.tsx/.js/.jsx`; 78,108 dòng ước lượng theo cùng cách | Count cả test/helper files dưới src. |
| Frontend test source | 207 filenames chứa `.test.`; 13 Playwright `*.spec.ts/tsx` | File count, không phải test case count. |

Các số Java/TypeScript được lấy từ repository source tại branch/working tree khảo sát. Lệnh đếm file dùng pathlib; dòng lệnh LOC được thực hiện bằng cách duyệt file có extension tương ứng, đếm line không rỗng và bỏ line comment-leading như mô tả trong cột. Số endpoint và bảng được giải thích tại §5.1 và §4.1. (nguồn: `src/main/java`, `src/test/java`, `src/main/resources/db/migration/tenant`, `src/main/resources/db/migration/registry`, `/home/vumoi/logictics-app/src`, `/home/vumoi/logictics-app/e2e`)

## 8.4 Tình trạng kết quả chạy thực tế phiên này

| Bài kiểm tra | Trạng thái | Lý do |
|---|---|---|
| Backend Maven test/JUnit | NOT RUN | Phiên này được giới hạn khảo sát source và tạo tài liệu; một số suite cần database PostgreSQL/Testcontainers. Không thực thi lệnh có tác dụng runtime. |
| Frontend Vitest | NOT RUN | Chỉ thống kê manifest/source, không chạy npm/Vitest. |
| Frontend Playwright | NOT RUN | Không khởi động browser/dev server; config có webServer và chạy song song mặc định ngoài CI. |
| Lint/typecheck/build | NOT RUN | Không cần để xác thực tài liệu tĩnh; không tuyên bố PASS. |

Lệnh CI được khai báo trong workflow là Python verifier và regression runner PostgreSQL 16; đây là cấu hình pipeline, không phải kết quả chạy của phiên khảo sát. (nguồn: `.github/workflows/backend-remediation.yml:1-25`, `/home/vumoi/logictics-app/playwright.config.ts:1-30`)

## 8.5 Hạn chế và hướng phát triển có bằng chứng

| Hạn chế hiện thấy | Bằng chứng | Hướng đề xuất |
|---|---|---|
| Tenant routing có implementation nhưng cấu hình mặc định tắt | `TENANCY_ENABLED:false` | Ghi rõ môi trường nào bật; kiểm thử tenant isolation trên registry và hai tenant DB riêng trước khi mô tả SaaS live. |
| Dashboard Executive/report metrics có unavailable/NOT_IMPLEMENTED; frontend contract flag false | Executive flag false; report code/docs gap | Chốt đúng response contract và nguồn dữ liệu cho từng KPI; không thay unavailable bằng fixture. |
| Document upload chưa có backend multipart contract; frontend upload flag false | backend-gaps BE-020; documents API flag | Bổ sung contract/storage/ownership/auth trước khi mở upload. |
| Payment create/edit chưa được runtime xác minh; payments command flag false | paymentCommands constant false; resource capability gated | Xác nhận runtime/backend command, idempotency, authorization rồi mới mở thao tác ghi. |
| Map full page chưa có route mount và hiển thị thông báo chưa có dữ liệu điểm/tuyến | MapPage có placeholder; AppRouter không mount | Cần nguồn positions/geocoding/route contract và route access chính thức. |
| Notifications có API đọc/mark-read nhưng không thấy sender tự động đã xác minh | NotificationController/Service chỉ chứng minh đọc và mark-read; không có kết luận sender | Mô tả như notification inbox; xác nhận event producers/delivery channel nếu báo cáo nói tự động. |
| Lark integration source mới/dirty gồm Base sync/event inbox; production credentials/runtime chưa được xác nhận | working tree uncommitted; config vars optional | Xác minh tenant/base/table mapping và runtime sync receipts/audit bằng môi trường tích hợp được phép. |
| Docs backend gaps và architecture overview không đồng bộ source ở một số điểm | `docs/docs/architecture/overview.md`; frontend `docs/backend-gaps.md` | Dùng source version/date làm chuẩn; người quản lý dự án xác nhận doc lịch sử và runtime deployment thực tế. |

Tài liệu backend gap được tìm thấy ở sibling frontend repo, không có file `docs/backend-gaps.md` tại backend path. Các blocker nêu trong đó là trạng thái tài liệu/handoff tại thời điểm ghi, phải kiểm tra lại source/runtime trước khi chép vào báo cáo. (nguồn: `/home/vumoi/logictics-app/docs/backend-gaps.md:13-23,51`, `/home/vumoi/logictics-app/src/features/documents/api/documents.api.ts:5-8`, `/home/vumoi/logictics-app/src/features/payments/api/paymentCommands.ts:10-15`, `/home/vumoi/logictics-app/src/features/executive/config/executive.contract.ts:1-5`, `/home/vumoi/logictics-app/src/pages/map/MapPage.tsx:6-14`)
