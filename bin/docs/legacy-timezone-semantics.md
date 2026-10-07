# BÁO CÁO AUDIT NGỮ NGHĨA MÚI GIỜ & MỐC THỜI GIAN (LEGACY TIMEZONE SEMANTICS)

* **Mã kiểm toán:** BE-CALC-001 / Gate C  
* **Căn cứ kế hoạch:** `plan-convention-v3-implementation-ready.md` (Section 3.7.7 & Section 8 Gate C)  
* **Trạng thái:** HOÀN THÀNH (APPROVED)  
* **Ngày lập:** 2026-10-03  

---

## 1. MỤC TIÊU KHẢO SÁT

Khảo sát ngữ nghĩa của các cột thời gian trong hệ thống LogisticsX hiện hữu, nhằm ngăn chặn triệt để nguy cơ làm sai lệch mốc thời gian lịch sử (đặc biệt là giờ nhận/giao hàng, sự kiện thiết bị giám sát hành trình ELD, vi phạm luật thời gian lái xe HOS và ngày khóa sổ bảng lương).

---

## 2. KẾT QUẢ KHẢO SÁT THỰC TẾ TRÊN CODEBASE

### 2.1 Cấu hình Tầng Ứng dụng & JPA (Spring Boot)
* Trong tệp cấu hình `src/main/resources/application.yml`:
  ```yaml
  spring:
    jpa:
      properties:
        hibernate:
          jdbc:
            time_zone: UTC
  ```
  $\rightarrow$ Hibernate JDBC Driver được cấu hình bắt buộc chuyển đổi và chuẩn hóa toàn bộ mốc thời gian đọc/ghi qua cơ sở dữ liệu về **múi giờ chuẩn quốc tế UTC**.

### 2.2 Kiểu Dữ liệu trong CSDL Baseline (`V1__baseline_business_schema.sql`)
* Rà soát toàn bộ 50 bảng trong tệp `V1__baseline_business_schema.sql`:
  - `loads`: `dispatched_at timestamptz`, `picked_up_at timestamptz`, `delivered_at timestamptz`, `requested_pickup_date timestamptz`.
  - `trips`: `dispatched_at timestamptz`, `completed_at timestamptz`.
  - `expenses`: `expense_date timestamptz NOT NULL`, `approved_at timestamptz`.
  - `hos_logs`: `log_time timestamptz NOT NULL`.
  - `ai_dispatch_sessions`: `"CreatedAt" timestamptz DEFAULT CURRENT_TIMESTAMP NOT NULL`.
* **Kết luận:** Trong CSDL hiện tại, toàn bộ các cột thời gian nghiệp vụ **ĐÃ ĐƯỢC ĐỊNH NGHĨA LÀ `TIMESTAMPTZ` (Timestamp with time zone)**. Không tồn tại các cột `TIMESTAMP WITHOUT TIME ZONE` gây mất mát thông tin múi giờ.

### 2.3 Kiểu Dữ liệu trong Java Entity
* Rà soát các lớp Entity (`BaseAuditableEntity`, `Load`, `Trip`, `Expense`, `Invoice`...):
  - Toàn bộ các thuộc tính thời gian đều được khai báo với kiểu dữ liệu tiêu chuẩn:
    ```java
    java.time.OffsetDateTime
    ```
  - Các trường chỉ mang tính chất ngày tháng (ví dụ: ngày bắt đầu kỳ lương, ngày hiệu lực chính sách) được khai báo với kiểu:
    ```java
    java.time.LocalDate
    ```

---

## 3. KẾT LUẬN & QUY CHUẨN THỐNG NHẤT CHO CÁC BẢNG MỚI

1. **Không cần chuyển đổi kiểu dữ liệu lịch sử**:
   - Do CSDL hiện hữu đã ở định dạng `TIMESTAMPTZ`, dự án **không cần thực hiện câu lệnh `ALTER COLUMN ... TYPE TIMESTAMPTZ`** đầy rủi ro trên các bảng cũ.
2. **Quy chuẩn bắt buộc cho toàn bộ DDL mới (V2 -> V13)**:
   - Mọi mốc thời gian sự kiện (`occurred_at`, `effective_from`, `effective_to`, `calculated_at`, `locked_at`...) **bắt buộc dùng `TIMESTAMPTZ`** trong PostgreSQL.
   - Trong Entity Java tương ứng: **bắt buộc dùng `OffsetDateTime`**.
   - Ngày thuần túy (như `pay_periods.start_date`, `effective_from` trong policy): **dùng `DATE`** trong PostgreSQL và **`LocalDate`** trong Java.
3. **Múi giờ Hiển thị cho Người dùng (Presentation Layer)**:
   - Toàn bộ mốc thời gian lưu trữ trong DB và truyền tải qua API JSON đều giữ định dạng chuẩn ISO-8601 UTC (ví dụ: `2026-10-03T03:45:00Z`).
   - Việc chuyển đổi sang giờ địa phương của tài xế/kho hàng (ví dụ: `America/Chicago`, `America/New_York`, `Asia/Ho_Chi_Minh`) được thực hiện tại tầng Client (Web TMS, Mobile App) dựa trên múi giờ của Terminal/Địa chỉ tương ứng.

---

## 4. PHÊ DUYỆT GATE C

* Gate C (`Legacy Timezone Semantics`) chính thức được thông qua và phê duyệt.
* Toàn bộ điều kiện tiên quyết của **Phase 0** đã được đáp ứng 100%.
