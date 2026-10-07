# ADR-001: LỰA CHỌN MÔ HÌNH CÔ LẬP ĐA THUÊ BAO (TENANT ISOLATION ARCHITECTURE)

* **Trạng thái:** ACCEPTED (ĐÃ PHÊ DUYỆT)
* **Ngày quyết định:** 2026-10-03
* **Người quyết định:** Kiến trúc sư hệ thống LogisticsX TMS
* **Căn cứ kế hoạch:** `plan-convention-v3-implementation-ready.md` (Section 3.7.2 & Section 8 Gate B)

---

## 1. BỐI CẢNH (CONTEXT)

Hệ thống LogisticsX TMS phục vụ nhiều công ty vận tải (tenants) đồng thời. Dữ liệu vận tải bao gồm các thông tin nhạy cảm: cước phí hợp đồng khách hàng, định vị GPS đội xe theo thời gian thực, bảng lương và số tài khoản ngân hàng của tài xế.

Theo quy chuẩn kỹ thuật tại `plan-convention-v3-implementation-ready.md`, trước khi viết thêm bất kỳ bảng CSDL mới nào (từ V2 trở đi), dự án bắt buộc phải chốt chính thức một trong ba mô hình cô lập dữ liệu (Tenant Isolation):
1. `DATABASE_PER_TENANT` (Mỗi tenant sở hữu một database riêng biệt).
2. `SCHEMA_PER_TENANT` (Các tenant chung database nhưng tách PostgreSQL Schema riêng).
3. `SHARED_SCHEMA_WITH_TENANT_ID` (Chung schema, phân biệt qua cột `tenant_id` trên mọi bảng).

---

## 2. QUYẾT ĐỊNH KIẾN TRÚC (DECISION)

Chúng tôi quyết định duy trì và chuẩn hóa mô hình **`DATABASE_PER_TENANT`** cho toàn bộ hệ sinh thái LogisticsX TMS.

### Chi tiết kiến trúc:
1. **Master Database (Cơ sở dữ liệu quản trị trung tâm)**:
   - Chứa bảng `tenant_registry` (`V1__create_tenant_registry.sql`) lưu trữ: `tenant_id`, `db_url`, `db_username`, `db_password`, `status`.
   - Chứa thông tin tài khoản SuperAdmin, gói cước SaaS (Subscriptions/Plans) và API Keys toàn cục.
2. **Tenant Database (Cơ sở dữ liệu vận hành riêng biệt của từng công ty)**:
   - Mỗi công ty khách hàng khi đăng ký sẽ được tự động cấp phát một database PostgreSQL độc lập (Ví dụ: `logisticsx_tenant_acme`, `logisticsx_tenant_swift`).
   - Database này chứa toàn bộ hơn 50 bảng nghiệp vụ vận hành: `loads`, `trips`, `trucks`, `employees`, `invoices`, `expenses`, `hos_*`...
   - Toàn bộ các bảng mới theo kế hoạch chuyển hóa (`trip_driver_assignments`, `load_events`, `shipment_costs`, `calculation_snapshots`, `driver_pay_policies`, `settlements`, `payroll_runs`, `payslips`) **ĐỀU ĐƯỢC TẠO TRỰC TIẾP TRONG DATABASE CỦA TENANT ĐÓ**.

---

## 3. LÝ DO LỰA CHỌN (RATIONALE)

* **Bảo mật & Cô lập Tuyệt đối (Hard Isolation)**:
  - Loại bỏ hoàn toàn nguy cơ rò rỉ dữ liệu chéo giữa các công ty trucking cạnh tranh nhau do sơ suất thiếu mệnh đề `WHERE tenant_id = ?` trong câu truy vấn SQL/JPA.
* **Tuân thủ Pháp lý (GDPR & Data Sovereignty)**:
  - Cho phép đặt cơ sở dữ liệu của từng tenant tại các vùng địa lý khác nhau (ví dụ: tenant châu Âu đặt DB tại Frankfurt theo chuẩn GDPR; tenant Mỹ đặt DB tại US East).
* **Sao lưu & Phục hồi Độc lập (Per-tenant Backup & Restore)**:
  - Khi một tenant muốn phục hồi dữ liệu về thời điểm 3 ngày trước, đội ngũ kỹ thuật có thể restore riêng database của tenant đó mà không ảnh hưởng tới hàng trăm tenant khác.
* **Tương thích hoàn toàn với Codebase hiện tại**:
  - Mã nguồn Spring Boot hiện tại đã có sẵn `TenantRegistryService`, `TenantDataSourceService` (quản lý connection pool động HikariCP) và `TenantMigrationService` (quản lý chạy migration Flyway theo từng tenant).

---

## 4. HỆ QUẢ ĐỐI VỚI THIẾT KẾ CSDL VÀ CODEBASE (CONSEQUENCES)

1. **Quy chuẩn Thiết kế DDL (DDL Conventions)**:
   - **KHÔNG CẦN THÊM CỘT `tenant_id`** vào các bảng nghiệp vụ mới (`trip_driver_assignments`, `shipment_costs`, `settlements`...). Sự cô lập đã được đảm bảo ở cấp độ vật lý (database level).
   - Các ràng buộc duy nhất (`UNIQUE`) chỉ cần xét trong phạm vi nội bộ bảng đó (ví dụ: `settlement_number` chỉ cần unique trong DB của tenant).
2. **Quy chuẩn Chạy Migration Flyway**:
   - Mọi migration nghiệp vụ từ `V2` đến `V13` nằm tại thư mục:  
     `src/main/resources/db/migration/tenant/`
   - Dịch vụ `TenantMigrationService` sẽ lặp qua toàn bộ các tenant đang ở trạng thái `ACTIVE` trong `tenant_registry` để thực thi lệnh `Flyway.migrate()`.
   - **Xử lý sự cố lỗi migration**: Nếu migration thất bại ở tenant thứ $k$, tiến trình sẽ dừng lại, ghi log rõ ràng danh sách các tenant đã migrate thành công (`migratedTenants`) và danh sách các tenant đang chờ (`pendingTenants`). Áp dụng nguyên tắc roll-forward để khắc phục lỗi.
