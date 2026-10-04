# ADDENDUM 1 cho REFACTOR_PROMPT.md

Lưu file này cạnh `REFACTOR_PROMPT.md` (ví dụ `docs/refactor/`). **Khi có mâu thuẫn, Addendum này thắng.** Nội dung dựa trên kết quả đã xác minh ở Giai đoạn 0 và 0.5.

## A1. Sự thật đã xác minh (không cần điều tra lại)

| # | Sự thật | Nguồn |
|---|---------|-------|
| 1 | Baseline trên `KAN-79-integrations-dang-nhap-bang-lark` (`f3c20d1`): `clean verify` xanh, 403/403 (367 unit + 36 IT), 0 skip. IT chạy thật bằng Testcontainers PostgreSQL 18.4, Flyway V1 đến V5. | GĐ 0.5 (b) |
| 2 | `refactor/layered-structure` = KAN-79 + 17 commit tách tầng (merge-base là HEAD của KAN-79). Đã làm: terminal, customer, trip, role, inspection, notification, document, identity, finance/invoice, finance/payment, fleet/truck, fleet/container, employee/employee, employee/driver (một phần), load, messaging/conversation, messaging/message. **Chưa verify.** Chưa làm: reporting. | GĐ 0.5 (a) |
| 3 | Bố cục của 17 commit đó: `controller`, `service` + `service/impl`, `repository`, `model`, `dto`, `mapper`. | GĐ 0.5 (a) |
| 4 | Phần reporting dở dang (46 file, 20 lỗi SpotBugs) đã lưu ở `backup/reporting-layering-wip`. | GĐ 0.5 |
| 5 | `LarkUserMapping` là **Java record (DTO), không phải JPA entity**. `LarkUserMappingServiceImpl` không truy cập DB; nó dùng `Optional<CurrentEmployeeLookupService>`. Profile `nodb` tạo mapping in-memory với role SUPERADMIN. | GĐ 0.5 (c2) |
| 6 | `SecurityConfiguration` thật sự phụ thuộc Lark: dòng 3-4 import `LarkProperties`, `LarkTokenService`; dòng 30 `@EnableConfigurationProperties({SecurityJwtProperties.class, LarkProperties.class})`; dòng 69 `permitAll` cho `"/api/auth/lark/**"`; dòng 146-174 `jwtDecoder(..., Optional<LarkTokenService>)` là composite decoder (Lark `canDecode` thì Lark decode, ngược lại OIDC). | GĐ 0.5 (c1) |
| 7 | Không có `scanBasePackages`, `@ComponentScan`, `@EntityScan`, `setPackagesToScan`, `@EnableJpaRepositories`, `META-INF`. `OpenApiConfig` không có `packagesToScan`. | GĐ 0, 0.5 |
| 8 | Redis dùng serializer gắn theo kiểu DTO, không lưu `@class`. **Không cần flush cache** vì đổi package. | `RedisCacheConfig:198-205` |
| 9 | Có **ít nhất 20** constructor expression `SELECT new com.company.logicstic.reporting.X(` trong 9 file `Reporting*Repository` (không phải 13). Grep chỉ bắt được các dòng có `new com.company...` liền nhau nên con số thật có thể cao hơn. | GĐ 0.5 (c5) |
| 10 | 2 chuỗi FQCN trong MapStruct `expression = "java(...)"`: `TripMapper` (`trip.TripStopResponse::from`) và `RoleMapper` (`role.RoleResponse.ClaimView`). | GĐ 0.5 (c5) |
| 11 | Nhiều `{@link ...}` trong Javadoc đang trỏ package **không tồn tại** (`modules.*`, `repository.*`, `service.*`, `shared.BaseAuditableEntity`). Đã hỏng từ trước. **Không sửa trong refactor này**, chỉ ghi vào "phát hiện thêm". | GĐ 0.5 (c5) |
| 12 | `TelegramChat` chỉ được nhắc trong chính file của nó, `docs/.../entity-relationships.md`, `scripts/generate_entities.py`. | GĐ 0.5 (c6) |

## A2. Quyết định (ghi đè v1)

**D1. Nhánh làm việc.** Giai đoạn 2 (Lark) làm trên nhánh mới `refactor/lark-integration` xuất phát từ `KAN-79-integrations-dang-nhap-bang-lark`. Không làm trên `refactor/layered-structure` cho tới khi nó được verify (Giai đoạn 0.6). Việc gộp hai nhánh là **Giai đoạn 2.R**, chỉ làm khi tôi duyệt.

**D2. Quy ước bố cục: theo nhánh layered-structure, ghi đè v1 mục 6.2.**
- `model/` thay cho `domain/` (entity và enum).
- Implementation đặt ở `service/impl/`, không để cạnh interface.
- Còn lại giữ nguyên: `controller`, `service`, `repository`, `dto`, `mapper`, `event`.
- Module chỉ có entity (không controller/service): chỉ tạo `model/` và, nếu có, `repository/`.

**D3. `TelegramChat` chuyển sang `integration/telegram`** (Giai đoạn 4). Trước khi chuyển: kiểm tra `scripts/generate_entities.py` và `scripts/scan_codebase.py` có gắn đường dẫn package cũ không, chỉ báo cáo.

**D4. Lark và `SecurityConfiguration`: dùng port (Giai đoạn 2.C).** Phương án "whitelist ngoại lệ" và phương án "chỉ chuyển bean cấu hình" đều bị loại vì `jwtDecoder` phụ thuộc thật vào `LarkTokenService`.

**D5. `ArchitectureTest`:** chấp nhận đề xuất thêm `platform`, `integration` vào `NON_FEATURE_PACKAGES`, **nhưng** giữ các tên cũ (`tenant`, `security`, `cache`, `config`) chỉ trong thời gian chuyển tiếp và **bắt buộc xóa ở bước cuối của Giai đoạn 3**. Chứng minh "tập class không đổi" phải bằng số liệu, không bằng lý luận (xem A3.6).

**D6. JPQL FQCN:** xử lý ở Giai đoạn 5 khi tới `reporting`. Số lượng `SELECT new com.company.logicstic.` trước và sau phải bằng nhau, và `ReportingRepositoryIT` cùng `ApiFunctionalIT` phải chạy (không skip). Spring Data kiểm tra `@Query` lúc khởi động nên sai FQCN sẽ làm context không lên; đây là lỗi "ồn" miễn là IT thực sự chạy.

## A3. Sửa Giai đoạn 2 (ghi đè v1)

### A3.1 Sửa lỗi lệnh grep trong v1
Grep `security\.lark|security/lark` trong v1 **sai**: nó khớp cả property prefix (báo cáo nói prefix là `app.security.lark`) và sẽ báo nhầm.
- Trước khi làm, lấy **nguyên văn** dòng `@ConfigurationProperties(prefix = "...")` của `LarkProperties`. Nếu báo cáo trước ghi `lark.*` còn báo cáo này ghi `app.security.lark.*`, phải nói rõ cái nào đúng.
- **Prefix property, tên biến môi trường, key trong YAML giữ nguyên tuyệt đối.**
- Grep kiểm tra dùng: `com\.company\.logicstic\.security\.lark` và `security/lark` (đường dẫn thư mục). Kết quả phải là 0 dòng. Các dòng khác chứa `security.lark` do property key được **liệt kê để tôi xem**, không sửa.
- Lệnh `sed` chỉ thay FQCN `com.company.logicstic.security.lark`, tuyệt đối không thay chuỗi ngắn hơn.

### A3.2 Snapshot (Bước 2.0): sửa để không thể "pass giả"
- Chạy với **cấu hình của `ApiFunctionalIT`** (Testcontainers), không dùng `nodb`, để metamodel và `JwtDecoder` thật sự được nạp. Nêu rõ profile đang active. Nếu `ApiFunctionalIT` chạy với `dev-auth` thì bean `jwtDecoder` (`@Profile("!dev-auth")`) không tồn tại; khi đó báo cáo và đề xuất cách chụp riêng cho decoder.
- Thêm assertion: `beans`, `routes`, `entities` đều **không rỗng**. Nếu rỗng thì test phải fail (nếu không, diff rỗng sẽ là kết quả giả).
- Ghi thêm vào snapshot: số bean kiểu `LarkTokenService`, có hay không bean `JwtDecoder`, tên các bean kiểu `LarkProperties` (kèm giá trị prefix).
- In số lượng từng nhóm ở đầu file snapshot để so nhanh trước/sau.

### A3.3 Bước 2.0b: test đặc tả cho composite `jwtDecoder` (trước khi đụng vào `SecurityConfiguration`)
Kiểm tra `SecurityConfigurationTest` và `LarkTokenServiceTest` đã phủ hai nhánh chưa: (i) token mà `canDecode` trả true thì decode qua Lark; (ii) token khác thì rơi xuống OIDC decoder. Nếu **chưa phủ đủ**, viết test đặc tả (characterization test) mô tả hành vi hiện tại **trước** khi refactor, commit riêng, phải xanh trên baseline.

### A3.4 Thứ tự Giai đoạn 2 mới
1. **2.0** Snapshot trước. **2.0b** Test đặc tả nếu thiếu.
2. **2.A** Chỉ đổi package `security.lark` sang `integration.lark` (một commit, cả main và test).
3. **2.B** Tách thư mục con `config/client/auth/mapping` (một commit).
4. **2.C** Tách phụ thuộc `SecurityConfiguration` vào Lark bằng port (một commit, chi tiết bên dưới).
5. **2.D** Thêm luật ArchUnit (sau 2.C, không cần ngoại lệ).
6. **2.E** Kiểm tra chạy thật, dọn dẹp, cập nhật docs.
7. **2.R** (chỉ khi tôi duyệt) Đưa `refactor/layered-structure` lên trên kết quả này.

Sau mỗi bước: `./mvnw clean verify` xanh, grep A3.1 = 0 dòng, snapshot diff rỗng (trừ tên bean sinh theo FQCN), báo cáo, dừng chờ tôi.

### A3.5 Chi tiết 2.C: port cho composite decoder
Mục tiêu: `SecurityConfiguration` không còn import bất kỳ class nào của `integration.lark`, và **hành vi giữ nguyên**.
1. Tạo interface trong package `com.company.logicstic.security` (sẽ đi cùng `SecurityConfiguration` sang `platform.security` ở Giai đoạn 3), ví dụ `ExternalJwtDecoder` với `canDecode(String)` và `decode(String)`. **Chữ ký (kiểu trả về, exception) phải khớp đúng chữ ký hiện có của `LarkTokenService`**. Không đổi hành vi của hai method này.
2. `LarkTokenService implements ExternalJwtDecoder`. Không sửa thân method.
3. Trong `SecurityConfiguration.jwtDecoder`: thay tham số `Optional<LarkTokenService>` bằng `List<ExternalJwtDecoder>` (rỗng nếu không có bean). Logic: duyệt danh sách, decoder đầu tiên có `canDecode(token)` thì dùng, không có thì OIDC. Hiện chỉ có một implementation nên hành vi không đổi.
4. Chuyển `LarkProperties.class` khỏi `@EnableConfigurationProperties` của `SecurityConfiguration`, sang một `@Configuration` mới `integration.lark.config.LarkConfiguration` (`@EnableConfigurationProperties(LarkProperties.class)`). **Trước khi làm**, nêu nguyên văn cách `LarkTokenService` và `LarkClientImpl` đang được tạo bean (annotation, `@ConditionalOn...`, `@Profile`) và giữ nguyên các điều kiện đó. Nếu điều kiện phụ thuộc vào thứ nằm trong `SecurityConfiguration`, dừng và hỏi.
5. Dòng `permitAll` `"/api/auth/lark/**"` là chuỗi, không phải phụ thuộc class: **giữ nguyên**, ghi vào "phát hiện thêm" như một điểm ghép nối bằng chuỗi.
6. Kiểm chứng: test đặc tả A3.3 vẫn xanh; snapshot diff rỗng; `JwtDecoder` vẫn có mặt và vẫn ưu tiên Lark.

### A3.6 Luật ArchUnit (2.D) và chứng minh cho `ArchitectureTest`
- Thêm hai luật agent đề xuất (`PLATFORM_AND_SHARED_MUST_NOT_DEPEND_ON_INTEGRATION`, `BUSINESS_MODULES_MUST_NOT_DEPEND_ON_INTEGRATION`), viết theo đúng phong cách của `ArchitectureTest` hiện có.
- Nếu luật báo vi phạm từ **class test** (test import Lark) hoặc từ `devtools`, báo cáo chính xác, không tự thêm ngoại lệ.
- Trước và sau khi sửa `NON_FEATURE_PACKAGES`, tạm in ra tập `featureOf()` và số class theo từng feature. Hai lần in phải **giống hệt** nhau. Đính kèm hai bảng đó vào báo cáo.

### A3.7 Ghi chú bảo mật (chỉ báo cáo, không sửa)
Xác nhận `application-prod.yml` và `application-staging.yml` **không** kích hoạt profile `nodb` (vì `nodb` cấp SUPERADMIN in-memory). Trích dòng liên quan.

## A4. Sửa Giai đoạn 4

- Trước khi chuyển `EldProviderConfiguration`, `EldVehicleMapping`, `EldDriverMapping`, `LoadBoardConfiguration`: grep mọi tham chiếu **từ module nghiệp vụ** (Truck, Driver, Load...) tới các class này (association JPA, service, repository). Nếu module nghiệp vụ đang tham chiếu chúng thì **không chuyển**, báo cáo (sẽ vi phạm luật `business khong import integration`).
- `LoadBoardListing`: **không chuyển ở Giai đoạn 4** (bản đồ trước ghi `load/domain` là trộn giai đoạn và sai quy ước). Nó giữ nguyên vị trí; layering thuộc Giai đoạn 5.

## A5. Sửa Giai đoạn 5

- 17 module đã tách tầng ở `refactor/layered-structure`: chỉ cần **verify và rà lại** khi hợp nhất (2.R), không làm lại.
- Còn phải làm: `reporting`, các module chỉ có entity (`fleet/dvir`, `fleet/maintenance`, `employee/hos`, `employee/timeentry`, `document/accident`, `identity/apikey`, `ai/dispatch`, `load/{board,tracking,exception,event}`) chỉ cần `model/`.
- `reporting`: record nào xuất hiện trong `SELECT new ...` thì đặt ở `reporting/projection/`; `*Response` ở `dto/`; enum và value object ở `model/`; `Reporting*Repository` ở `repository/` (ngoại lệ chỉ-đọc đi chéo module, ghi rõ trong luật); calculator ở `calculator/`.
- Trước khi làm `reporting`, điều tra nguyên nhân 20 lỗi SpotBugs ở nhánh WIP (Giai đoạn 0.6 b). **Không** xử lý bằng cách sửa `spotbugs-exclude.xml`.

## A6. Yêu cầu về bằng chứng (bổ sung mục 5.4 của v1)

Mọi khẳng định về **loại** của một class (record, entity, `@Component`, có điều kiện hay không) phải kèm `file:dòng` và trích nguyên văn khai báo. Báo cáo Giai đoạn 0 từng ghi `LarkUserMapping` là "entity JPA" trong khi thực tế là record. Không suy từ tên class.

## A7. Giai đoạn 0.6 (chỉ đọc, làm trước Giai đoạn 2)

Không tạo nhánh làm việc, không sửa file nào trong repo chính. Dùng `git worktree` cho mọi lệnh build.

1. **Verify `refactor/layered-structure` đã commit:** `git worktree add ../logicstic-layered b975666` rồi `./mvnw clean verify`. Báo số test pass/fail/skip, lỗi checkstyle/SpotBugs nếu có, và IT nào bị bỏ qua.
2. **Nguyên nhân 20 lỗi SpotBugs** ở `backup/reporting-layering-wip`: detector nào, lớp nào, và `spotbugs-exclude.xml` có mục nào gắn với tên class/package đầy đủ không. Chỉ báo cáo.
3. Trích nguyên văn: `@ConfigurationProperties` của `LarkProperties` và `SecurityJwtProperties`; khai báo lớp và mọi annotation điều kiện của `LarkTokenService` và `LarkClientImpl`; chữ ký `canDecode`/`decode`; những test trong `SecurityConfigurationTest` và `LarkTokenServiceTest` phủ nhánh composite decoder.
4. Đếm lại `SELECT new com.company.logicstic` trên KAN-79 bằng grep có cờ nhiều dòng (để bắt cả trường hợp `new` xuống dòng), liệt kê theo file.
5. Nêu profile/annotation mà `ApiFunctionalIT` đang dùng (có `dev-auth` không). Chạy `LarkWiringSnapshotTest` (bản sửa theo A3.2) trong worktree KAN-79, đính kèm số lượng beans/routes/entities. Xác nhận cả ba đều không rỗng.
6. A3.7: trích dòng `nodb` trong `application-prod.yml` và `application-staging.yml`.
7. **Dừng**, báo cáo theo mẫu mục 9 của v1.
