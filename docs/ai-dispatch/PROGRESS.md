# AI Dispatch backend — tiến độ xác minh

Ngày cập nhật: 2026-10-24

| Ticket | Trạng thái | Kết quả | Blocker / tiếp theo |
|---|---|---|---|
| BE-01 | DONE | Xác nhận tài liệu đầu vào thiếu; inventory source, route, config, migrations và dirty worktree ghi tại `00-source-contract-verification.md`. | Exact API contract cần được đặt trong repo trước REST tickets. |
| BE-02 | DONE | Engine create/get/revalidate/accept hiện có; không cần gateway stub. Run yêu cầu policy/target/source IDs tường minh. | Tool tự resolve policy/evidence và xử lý candidate đồng hạng cần quyết định/mapper được duyệt trước BE-23/25. |
| BE-03 | BLOCKED | Source audit xác nhận Flyway dùng credential từng tenant từ registry; role thực tế và grants catalog không có trong source. | Cần đối chiếu role/table privileges trên các DB tenant bằng catalog read-only hoặc DDL/deployment record; BE-04 chờ role có quyền GRANT. |
| BE-05 | DONE | Trong repo, hai trigger chỉ xuất hiện trong script có header TEST-ONLY; không nằm trong Flyway main migrations. | DB nào ngoài repo đã cài script thì chưa xác minh; cleanup chỉ local/test. |
| BE-09 | DONE | Typed `ai.dispatch.*` properties và binding test; auto mặc định false, không có provider/model/key mặc định. `./mvnw -Dtest=AiDispatchPropertiesTest test` PASS (2 tests); `./mvnw -DskipTests package` PASS. | Pricing schema chưa được định nghĩa; BE-16 phải hiểu missing rate là 0 và `costEstimated=false`. Runtime AI service chưa được nối sang config mới. |
| BE-17 | DONE | JSON Schema validator hiện chỉ test-scope; runtime whitelist/validator chưa có. | BE-18 cần chọn validator production hiện hữu hoặc dependency sau impact/review. |

## Bằng chứng kiểm thử

- Trước BE-09: không chạy test/build trong giai đoạn đọc source.
- Sau BE-09: `./mvnw -Dtest=AiDispatchPropertiesTest test` PASS (2/2); `./mvnw -DskipTests package` PASS.
- Lint: `NOT RUN`; chưa thấy lint/checkstyle/spotless/PMD Maven plugin/script trong `pom.xml`.
- `git diff --check`: FAIL ở hai file đã dirty từ trước (`LarkBaseService.java:28`, `database-failures-unique-allowlist.properties:83`) do dòng trống cuối file; không chỉnh vì ngoài phạm vi và là thay đổi chưa commit của người dùng.
- Compile phát warning MapStruct cho mapper hiện hữu, cùng deprecation/unchecked warnings; không sửa file ngoài phạm vi.
- GitNexus MCP context/impact không chạy được do database index version mismatch (v42 vs runner v40). GitNexus CLI impact `AiConfiguration`: `risk=UNKNOWN`, không có caller graph; source search đã xác nhận references. Không sửa class này.

## Next

1. Xác nhận role và quyền DB để mở BE-04.
2. Có thể bắt đầu BE-10/BE-11 và implementation BE-18 sau khi chọn validator runtime.
3. BE-23/25 chờ policy/evidence selection và quy tắc tie; REST tickets chờ API_CONTRACT chính thức.
