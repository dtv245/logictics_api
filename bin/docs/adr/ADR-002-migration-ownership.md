# ADR-002: QUYỀN SỞ HỮU DI TRÚ CSDL & THỐNG NHẤT FLYWAY (MIGRATION OWNERSHIP GATE)

* **Trạng thái:** ACCEPTED (ĐÃ PHÊ DUYỆT)
* **Ngày quyết định:** 2026-10-03
* **Người quyết định:** Kiến trúc sư hệ thống LogisticsX TMS
* **Căn cứ kế hoạch:** `plan-convention-v3-implementation-ready.md` (Section 3.7.1, Section 8 Gate A, BE-MIG-000)

---

## 1. BỐI CẢNH (CONTEXT)

Trong cấu trúc CSDL hiện tại (`V1__baseline_business_schema.sql`), bảng đầu tiên xuất hiện là:
```sql
CREATE TABLE "__EFMigrationsHistory" (
    migration_id varchar(150) NOT NULL,
    product_version varchar(32) NOT NULL,
    CONSTRAINT pk___ef_migrations_history PRIMARY KEY (migration_id)
);
```
Điều này phản ánh lịch sử dự án: LogisticsX ban đầu được phát triển trên nền tảng .NET / Entity Framework Core. Khi chuyển dịch sang Java Spring Boot 3, schema toàn thể đã được dump lại thành tệp SQL baseline `V1__baseline_business_schema.sql`.

Theo yêu cầu của Gate A (`BE-MIG-000`), dự án **tuyệt đối không được để hai migration engine đồng thời có quyền mutate cùng một schema**. Cần xác định dứt khoát công cụ nào là chủ sở hữu (Schema Owner) duy nhất kể từ phiên bản này.

---

## 2. QUYẾT ĐỊNH KIẾN TRÚC (DECISION)

1. **Chủ sở hữu Schema Độc quyền**:
   - **Spring Boot Flyway** là công cụ di trú và quản lý cấu trúc CSDL **duy nhất và tối cao** của dự án LogisticsX.
   - Toàn bộ các công cụ di trú cũ (bao gồm .NET EF Core Migrator, lệnh CLI dotnet ef, hoặc script thủ công bên ngoài) **bị vô hiệu hóa hoàn toàn (DEPRECATED & DISABLED)** trên mọi môi trường (Development, Staging, Production).
2. **Điểm mốc Baseline (Flyway Baseline Anchor)**:
   - Tệp `V1__baseline_business_schema.sql` đặt tại `src/main/resources/db/migration/tenant/` được xác nhận là **Version 1 (V1 Baseline)**.
   - Bảng `__EFMigrationsHistory` được giữ lại chỉ như một bảng dữ liệu lịch sử thụ động, không tham gia vào bất kỳ cơ chế kiểm tra nào của hệ thống mới.
3. **Quy tắc Đánh số Phiên bản Tiếp theo**:
   - Mọi thay đổi CSDL nghiệp vụ mới bắt buộc phải tuân theo thứ tự liên tục, bắt đầu từ:
     - `V2__create_trip_driver_assignments.sql`
     - `V3__add_trip_mileage_breakdown.sql`
     - `V4__extend_trip_stops_execution.sql`
     - ... đến `V13__create_optimization_audit_tables.sql`.
   - Vị trí lưu trữ: `src/main/resources/db/migration/tenant/`.

---

## 3. NGUYÊN TẮC THI HÀNH FLYWAY (GOVERNANCE RULES)

* **Nguyên tắc Bất biến (Immutability)**:
  - Một file migration một khi đã commit vào Git và chạy trên bất kỳ môi trường nào thì **CẤM SỬA ĐỔI**.
  - Nếu cần điều chỉnh, phải tạo migration kế tiếp `V{next}__...`.
* **Nguyên tắc Thất bại Ngay (Fail-Fast Policy)**:
  - Mọi câu lệnh DDL trong migration versioned phải mô tả chính xác trạng thái mong đợi. Không dùng bừa bãi mệnh đề `IF NOT EXISTS` hoặc `IF EXISTS` để che giấu tình trạng phân mảnh (schema drift) giữa các tenant.
  - Đường ống CI/CD trước khi triển khai phải chạy lệnh `flyway validate` để đối soát checksum.
* **Cơ chế Khởi tạo và Nâng cấp**:
  - *Môi trường kiểm thử tự động / Tenant mới*: Khởi tạo database trắng $\rightarrow$ Flyway chạy tuần tự từ `V1` đến `V{latest}`.
  - *Tenant đã có dữ liệu sẵn*: Flyway nhận diện `V1` đã áp dụng và chỉ chạy nối tiếp từ `V2` trở đi.

---

## 4. KẾT LUẬN & ĐIỀU KIỆN TIÊN QUYẾT (EXIT CRITERIA)

* Gate A (`BE-MIG-000`) chính thức hoàn thành và đóng lại.
* Đội ngũ phát triển được phép tiến hành viết và áp dụng các tệp migration `V2` đến `V13` theo lộ trình đã đề ra trong `plan-convention-v3-implementation-ready.md`.
