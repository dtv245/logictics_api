# LOGISTICSX TMS — KẾ HOẠCH CHUYỂN HÓA VÀ QUY CHUẨN CƠ SỞ DỮ LIỆU (PLAN-CONVENTION)
> **Phiên bản:** 2.0 (Schema-Aligned Enterprise Migration)  
> **Căn cứ tài liệu:** `LogisticsX_Core_Business_Data_Model_and_Calculation_Spec_v2_Schema_Aligned.md`  
> **Nền tảng công nghệ:** Spring Boot 3 / Spring Framework 6 / PostgreSQL 15+ / Hibernate JPA / Flyway Multi-Tenancy  
> **Mục tiêu:** Chuẩn hóa toàn bộ mô hình dữ liệu, thiết lập quy chuẩn kỹ thuật (conventions) và lộ trình triển khai chi tiết từng task, từng business nhằm đưa LogisticsX đạt chuẩn các hệ thống TMS hàng đầu (Oracle OTM, SAP TM, Samsara).

---

## MỤC LỤC

1. [TỔNG QUAN CHIẾN LƯỢC & NGUYÊN TẮC CHUYỂN HÓA](#1-tổng-quan-chiến-lược--nguyên-tắc-chuyển-hóa)
2. [BẢNG MA TRẬN ĐỐI SOÁT: KEEP / ALTER / NEW / DEPRECATE](#2-bảng-ma-trận-đối-soát-keep--alter--new--deprecate)
3. [HỆ THỐNG QUY CHUẨN KỸ THUẬT (ENGINEERING & DATA CONVENTIONS)](#3-hệ-thống-quy-chuẩn-kỹ-thuật-engineering--data-conventions)
   - [3.1 Quy chuẩn đặt tên (Naming Conventions)](#31-quy-chuẩn-đặt-tên-naming-conventions)
   - [3.2 Quy chuẩn kiểu dữ liệu (Data Type Conventions)](#32-quy-chuẩn-kiểu-dữ-liệu-data-type-conventions)
   - [3.3 Quy chuẩn Entity JPA & Tính bất biến (JPA & Immutability Conventions)](#33-quy-chuẩn-entity-jpa--tính-bất-biến-jpa--immutability-conventions)
   - [3.4 Quy chuẩn Toàn vẹn Tài chính & Khử sai số tiền tệ (Financial Integrity Guard)](#34-quy-chuẩn-toàn-vẹn-tài-chính--khử-sai-số-tiền-tệ-financial-integrity-guard)
   - [3.5 Quy chuẩn Quản lý Trạng thái & State Machine (Status Machine Conventions)](#35-quy-chuẩn-quản-lý-trạng-thái--state-machine-status-machine-conventions)
   - [3.6 Quy chuẩn Phản hồi Chỉ số (Metric Availability Conventions)](#36-quy-chuẩn-phản-hồi-chỉ-số-metric-availability-conventions)
   - [3.7 Quy chuẩn Quản lý Migration Flyway (Flyway Governance)](#37-quy-chuẩn-quản-lý-migration-flyway-flyway-governance)
4. [KẾ HOẠCH TRIỂN KHAI CHI TIẾT THEO TỪNG BUSINESS & TASK](#4-kế-hoạch-triển-khai-chi-tiết-theo-từng-business--task)
   - [Phase 0: Semantic Audit & Khảo sát nghiệp vụ kế thừa (BE-CALC-001)](#phase-0-semantic-audit--khảo-sát-nghiệp-vụ-kế-thừa-be-calc-001)
   - [Phase 1: Xây dựng Bộ chỉ số V1 từ CSDL hiện tại (BE-CALC-002 -> 004)](#phase-1-xây-dựng-bộ-chỉ-số-v1-từ-csdl-hiện-tại-be-calc-002---004)
   - [Phase 2: Chuẩn hóa Thực thi Vận hành & CSDL Cốt lõi (BE-CALC-005 -> 009)](#phase-2-chuẩn-hóa-thực-thi-vận-hành--csdl-cốt-lõi-be-calc-005---009)
   - [Phase 3: Sổ cái Chi phí Vận chuyển, Phụ phí & Lợi nhuận (BE-CALC-010 -> 012)](#phase-3-sổ-cái-chi-phí-vận-chuyển-phụ-phí--lợi-nhuận-be-calc-010---012)
   - [Phase 4: Quyết toán Thù lao Tài xế - Driver Settlement (BE-CALC-013 -> 014)](#phase-4-quyết-toán-thù-lao-tài-xế---driver-settlement-be-calc-013---014)
   - [Phase 5: Kỳ lương Chính thức, Phiếu lương & Chi trả (BE-CALC-015)](#phase-5-kỳ-lương-chính-thức-phiếu-lương--chi-trả-be-calc-015)
   - [Phase 6: Biểu giá Hợp đồng Khách hàng & Phụ phí Nhiên liệu (Rate Rules & FSC)](#phase-6-biểu-giá-hợp-đồng-khách-hàng--phụ-phí-nhiên-liệu-rate-rules--fsc)
   - [Phase 7: Tối ưu hóa Điều phối & Giải thuật Phân bổ Chuyến (BE-CALC-016)](#phase-7-tối-ưu-hóa-điều-phối--giải-thuật-phân-bổ-chuyến-be-calc-016)
   - [Phase 8: Giám sát Đội xe & Tỷ lệ Khai thác Lịch sử (Fleet Utilization)](#phase-8-giám-sát-đội-xe--tỷ-lệ-khai-thác-lịch-sử-fleet-utilization)
5. [LỘ TRÌNH FLYWAY MIGRATION DDL CHUẨN HÓA (V2 -> V14)](#5-lộ-trình-flyway-migration-ddl-chuẩn-hóa-v2---v14)
6. [CHIẾN LƯỢC MIGRATION AN TOÀN (EXPAND & CONTRACT PATTERN)](#6-chiến-lược-migration-an-toàn-expand--contract-pattern)
7. [MA TRẬN KIỂM THỬ (TEST MATRIX) & DEFINITION OF DONE (DoD)](#7-ma-trận-kiểm-thử-test-matrix--definition-of-done-dod)

---

## 1. TỔNG QUAN CHIẾN LƯỢC & NGUYÊN TẮC CHUYỂN HÓA

Hệ thống LogisticsX hiện tại đã sở hữu sẵn nền tảng CSDL nghiệp vụ phong phú (`V1__baseline_business_schema.sql` với hơn 50 bảng). Tuy nhiên, để đáp ứng tính toán chi phí thực tế (Costing), định giá (Rating), quyết toán thù lao tài xế (Settlement), khóa sổ lương (Payroll) và giải thuật điều phối tối ưu (Dispatch Optimization), hệ thống cần tiến hóa từ mô hình ghi đè trạng thái (state overwrite) sang mô hình **Event-driven, Snapshot-auditable và Financial Ledger**.

### 3 Nguyên tắc Chuyển hóa Cốt lõi:
1. **Reuse existing data first (Tái sử dụng tối đa CSDL hiện có)**: Không vứt bỏ hoặc tạo bảng trùng lặp nếu bảng hiện tại đang làm tốt vai trò gốc (Ví dụ: giữ nguyên `expenses`, không tạo thêm `expense_receipts`; giữ nguyên hệ thống `hos_logs`, `hos_violations`, `driver_hos_statuses`).
2. **Alter with care (Mở rộng có kiểm soát)**: Bổ sung các trường dữ liệu còn khuyết vào bảng hiện hữu để hoàn thiện ngữ nghĩa nghiệp vụ (Ví dụ: bổ sung phân loại dặm vào `trips`, bổ sung mốc thời gian bốc/dỡ vào `trip_stops`, gắn khóa ngoại định danh vào `expenses`).
3. **New only when necessary (Chỉ thêm bảng mới khi khái niệm nghiệp vụ chưa từng tồn tại)**: Tạo mới các bảng bắt buộc cho kiến trúc sổ cái và đối soát (`trip_driver_assignments`, `load_events`, `shipment_costs`, `calculation_snapshots`, `driver_pay_policies`, `settlements`, `payroll_runs`, `payslips`).
4. **Expand and Contract (An toàn tuyệt đối trong di trú dữ liệu)**: Không xóa hoặc đổi tên trường đang chạy trên Production; chuyển đổi dữ liệu qua các bước: Thêm trường mới -> Ghi đồng thời (Dual-write) -> Backfill dữ liệu lịch sử -> Đổi luồng đọc -> Đánh dấu Deprecated -> Xóa bỏ ở phiên bản sau.

---

## 2. BẢNG MA TRẬN ĐỐI SOÁT: KEEP / ALTER / NEW / DEPRECATE

| Phân nhóm nghiệp vụ | Bảng CSDL | Hành động | Lý do & Định hướng kỹ thuật |
|---|---|---|---|
| **Vận đơn & Chuyến xe** | `loads` | **KEEP** | Lưu trữ cốt lõi đơn hàng vận chuyển. Chưa đổi tên `delivery_cost_amount` khi chưa audit xong. |
| | `trips` | **ALTER** | Bổ sung `planned_distance_miles`, `actual_distance_miles`, `loaded_miles`, `empty_miles`. Giữ tạm `total_distance`. |
| | `trip_stops` | **ALTER** | Bổ sung `status`, `appointment_start`, `appointment_end`, `service_started_at`, `service_completed_at`, `departed_at` để tính Dwell/Detention. |
| | `trip_driver_assignments` | **NEW** | **Bắt buộc V1**. Lưu vết lịch sử gán tài xế vào chuyến xe theo thời gian thực. Chấm dứt việc dùng `trucks.main_driver_id` sai lệch lịch sử. |
| | `load_events` | **NEW** | **Bắt buộc V1**. Lưu vết toàn bộ dòng sự kiện vòng đời đơn hàng (Event Sourcing timeline, tọa độ GPS, nguồn phát sinh). |
| **Đội xe & Nhân sự** | `trucks` | **KEEP** | Quản lý định danh, tình trạng và trang thiết bị đầu kéo. |
| | `employees` | **KEEP** | Quản lý hồ sơ nhân sự, tài xế và mức lương cơ bản (HR base salary / hourly). |
| | `driver_licenses` | **KEEP** | Quản lý giấy phép lái xe và thời hạn pháp lý. |
| | `hos_logs`, `hos_violations`, `driver_hos_statuses` | **KEEP** | Subsystem tuân thủ HOS/ELD cực kỳ tốt hiện có; tái sử dụng trực tiếp làm đầu vào cho Dispatch Optimizer. |
| | `eld_*_mappings`, `eld_provider_configurations` | **KEEP** | Cấu hình tích hợp thiết bị giám sát hành trình ELD. |
| | `driver_behavior_events` | **KEEP** | Dữ liệu sự kiện an toàn giao thông của tài xế. |
| | `time_entries` | **KEEP** | Nguồn chấm công theo giờ cho nhân sự/tài xế hưởng lương thời gian. |
| **Tài chính & Chi phí** | `expenses` | **ALTER** | Giữ nguyên làm nguồn tài chính chi phí vận hành. Bổ sung các FK: `load_id`, `trip_id`, `employee_id`, `maintenance_record_id`, `document_id`. |
| | `maintenance_records`, `maintenance_parts` | **KEEP** | Nguồn ghi nhận chi phí bảo dưỡng xe chính thống. Ngăn chặn tính trùng (double-count) với `expenses`. |
| | `maintenance_schedules` | **KEEP** | Kế hoạch bảo trì định kỳ của phương tiện. |
| | `invoices` | **KEEP + DEPRECATE** | Giữ vai trò Hóa đơn Phải thu Khách hàng (Customer AR). **DEPRECATE** các trường liên quan đến lương tài xế (`employee_id`, `period_*`, `total_hours_worked`, `total_distance_driven`). |
| | `invoice_line_items` | **KEEP** | Phân rã chi tiết doanh thu từng mục trên hóa đơn. |
| | `payments` | **KEEP** | Lưu vết thanh toán của khách hàng (AR). Không dùng bảng này để chi trả lương tài xế. |
| | `payment_links` | **KEEP** | Cổng thanh toán trực tuyến cho khách hàng. |
| **Sổ cái & Audit mới** | `shipment_costs` | **NEW** | **Bắt buộc V1/V2**. Sổ cái chi phí chuẩn hóa cấp Load/Trip. Nhập liệu từ `expenses`, `maintenance_records`, `settlements`. |
| | `calculation_snapshots` | **NEW** | **Bắt buộc V1/V2**. Bản chụp bất biến toàn bộ công thức, input, output, chính sách tính toán (Rating, Costing, Settlement, ETA). |
| | `accessorial_charges` | **NEW** | **Bắt buộc V2**. Quản lý phụ phí phát sinh (Detention, Layover, Lumper), tách bạch 3 cột: Phí thu khách, Chi phí công ty, Thù lao tài xế. |
| **Thù lao & Lương tài xế** | `driver_pay_policies` | **NEW** | Chính sách thù lao tài xế có phiên bản và ngày hiệu lực (Per-mile, Per-load, % Doanh thu, Hourly, Phụ cấp dừng đỗ). |
| | `pay_periods` | **NEW** | Chu kỳ chốt thù lao/lương (Tuần, Nửa tháng, Tháng). |
| | `settlements` | **NEW** | Bảng quyết toán thù lao chuyến/kỳ của tài xế kèm số liệu chốt và snapshot. |
| | `settlement_lines` | **NEW** | Chi tiết các dòng thù lao, thưởng, phụ cấp, khấu trừ, hoàn ứng. |
| | `payroll_runs` | **NEW** | Đợt chạy bảng lương chính thức cấp doanh nghiệp. |
| | `payroll_run_items` | **NEW** | Khoản lương chi tiết từng nhân sự trong đợt chạy lương. |
| | `payslips` | **NEW** | Phiếu lương chính thức của tài xế (Bất biến, có snapshot JSON và liên kết file PDF). |
| | `payroll_payments` | **NEW** | Sổ cái thanh toán chi trả lương (Tích hợp Stripe Payout/Bank Transfer). |
| **Định giá & Tối ưu** | `rate_rules` | **NEW** | Ma trận định giá hợp đồng vận chuyển, biểu giá sàn/trần và phụ phí nhiên liệu FSC. |
| | `optimization_runs` | **NEW** | Lưu vết các phiên chạy thuật toán điều phối tối ưu (Solver, Objectives, Inputs, Results). |
| | `optimization_assignments` | **NEW** | Chi tiết điểm số, lý do khả thi/từ chối và gợi ý ghép chuyến của thuật toán. |
| | `vehicle_status_events` | **NEW (Optional)** | Lưu vết biến động trạng thái xe theo thời gian để tính toán Tỷ lệ khai thác xe (Utilization %). |

---

## 3. HỆ THỐNG QUY CHUẨN KỸ THUẬT (ENGINEERING & DATA CONVENTIONS)

Mọi thay đổi CSDL và viết code backend trong dự án **bắt buộc tuân thủ 100%** các quy chuẩn dưới đây:

### 3.1 Quy chuẩn đặt tên (Naming Conventions)

#### CSDL (PostgreSQL):
- **Tên bảng**: Danh từ số nhiều, chữ thường, phân cách dấu gạch dưới (`snake_case`).  
  *Ví dụ:* `trip_driver_assignments`, `calculation_snapshots`, `driver_pay_policies`.
- **Tên cột**: Chữ thường, phân cách dấu gạch dưới (`snake_case`). Không dùng dấu cách, không viết hoa lạc đà, không dùng từ khóa SQL làm tên trần (nếu có phải đặt rõ nghĩa: dùng `assignment_order` thay vì `order`, dùng `trip_number` thay vì `number`).
- **Khóa chính**: Luôn đặt tên là `id`, kiểu `UUID`.
- **Khóa ngoại**: `<tên_bảng_số_ít>_id`.  
  *Ví dụ:* `load_id`, `trip_id`, `driver_id`, `pay_period_id`.
- **Chỉ mục (Index)**:
  - B-tree thông thường: `ix_<tên_bảng>_<tên_cột_1>[_<tên_cột_2>]`.
  - Khóa duy nhất (Unique): `uq_<tên_bảng>_<tên_cột_1>[_<tên_cột_2>]`.
  - Khóa ngoại: `fk_<tên_bảng_nguồn>_<tên_bảng_đích>`.
- **Cột thời gian**: Kết thúc bằng `_at` nếu là mốc thời gian có múi giờ (`occurred_at`, `departed_at`), kết thúc bằng `_date` nếu là ngày thuần túy (`start_date`, `effective_from`).

#### Lớp Ứng dụng (Java / Spring Boot):
- **Entity**: Viết hoa lạc đà (`PascalCase`), số ít (`TripDriverAssignment`, `CalculationSnapshot`, `ShipmentCost`).
- **Repository**: `<EntityName>Repository` (`TripDriverAssignmentRepository`).
- **Service Interface**: `<BusinessArea>Service` hoặc `<Concept>Engine` (`DriverPayEngine`, `ProfitabilityService`).
- **DTOs**: `<BusinessAction>[Request|Response|Dto]` (`CalculateSettlementRequest`, `LoadProfitabilityResponse`).
- **Enum**: Viết hoa cách nhau dấu gạch dưới (`UPPER_SNAKE_CASE`), gom cụm theo domain logic.

---

### 3.2 Quy chuẩn kiểu dữ liệu (Data Type Conventions)

| Nhóm nghiệp vụ | Kiểu dữ liệu PostgreSQL | Kiểu dữ liệu Java | Quy định đặc thù |
|---|---|---|---|
| **Số tiền (Money)** | `NUMERIC(19,4)` | `java.math.BigDecimal` | **TUYỆT ĐỐI CẤM** dùng `double` hoặc `float`. Mọi số tiền luôn đi kèm cột tiền tệ `currency VARCHAR(3)` (ISO-4217: USD, VND, EUR). |
| **Đơn giá / Tỷ lệ chi tiết** | `NUMERIC(19,6)` | `java.math.BigDecimal` | Dùng cho đơn giá dặm ($/mile), tỷ giá quy đổi, trọng số tối ưu. |
| **Phần trăm (%)** | `NUMERIC(9,4)` hoặc `(9,6)` | `java.math.BigDecimal` | Lưu giá trị phần trăm thực (ví dụ 15.5% lưu `15.5000` hoặc tỉ lệ `0.155000` theo quy ước thống nhất của service). |
| **Khoảng cách (Distance)** | `NUMERIC(12,3)` | `java.math.BigDecimal` | Đơn vị đo dặm (Miles) hoặc km. Lưu chính xác đến 3 chữ số thập phân (1/1000 dặm). |
| **Tọa độ GPS** | `DOUBLE PRECISION` | `java.lang.Double` | Vĩ độ (`latitude`) và Kinh độ (`longitude`) sử dụng tọa độ WGS-84. |
| **Mốc thời gian (Timestamps)** | `TIMESTAMPTZ` | `java.time.OffsetDateTime` | Toàn bộ mốc thời gian phải có múi giờ (khuyến nghị lưu UTC tại CSDL). |
| **Ngày (Date)** | `DATE` | `java.time.LocalDate` | Dùng cho kỳ lương, ngày hiệu lực chính sách. |
| **Dữ liệu tài liệu / Payload** | `JSONB` | `java.lang.String` hoặc Record/POJO với `@JdbcTypeCode(SqlTypes.JSON)` | Dùng cho `input_json`, `result_json`, `snapshot_json`. Cho phép đánh chỉ mục GIN khi cần query sâu. |
| **Khóa chính** | `UUID` | `java.util.UUID` | Khởi tạo qua v4 UUID (`@UuidGenerator` hoặc `gen_random_uuid()`). |

---

### 3.3 Quy chuẩn Entity JPA & Tính bất biến (JPA & Immutability Conventions)

1. **Kế thừa Audit**: Mọi bảng nghiệp vụ cốt lõi đều phải kế thừa `BaseAuditableEntity` để có 4 trường kiểm toán tự động: `created_at`, `created_by`, `last_modified_at`, `last_modified_by`.
2. **Khóa Lạc quan (Optimistic Locking)**:
   - Các bảng tài chính, quyết toán, bảng lương có thao tác xét duyệt đồng thời **phải có trường `@Version private Long version;`** (`settlements`, `payroll_runs`, `shipment_costs`, `accessorial_charges`, `driver_pay_policies`).
   - Ngăn chặn triệt để trường hợp 2 kế toán viên cùng duyệt/sửa đè dữ liệu tài chính của nhau.
3. **Quy tắc Bất biến (Financial Immutability Gate)**:
   - Khi một bản ghi tài chính chuyển sang trạng thái chốt (`LOCKED`, `POSTED`, `PAID`):
     - **CẤM UPDATE TRỰC TIẾP** vào các cột số tiền, dặm, đơn giá.
     - Mọi điều chỉnh sau khi khóa sổ phải được thực hiện thông qua bản ghi điều chỉnh (Adjustment / Reversal Record) ở kỳ tiếp theo.
     - Entity hoặc Service phải ném ngoại lệ `IllegalStateException("Cannot mutate locked financial aggregate")`.
4. **Fetch Type**: Toàn bộ quan hệ `@ManyToOne` và `@OneToOne` mặc định dùng `fetch = FetchType.LAZY` để tránh N+1 Query.

---

### 3.4 Quy chuẩn Toàn vẹn Tài chính & Khử sai số tiền tệ (Financial Integrity Guard)

Mọi thao tác tài chính phải tuân thủ nghiêm ngặt các bất biến toán học sau:

#### Bất biến 1: Đối soát Hóa đơn Khách hàng (Customer Invoice Reconciliation)
$$\text{Invoice.subtotal} \equiv \sum_{i=1}^{n} \text{InvoiceLineItem}_i\text{.amount}$$
$$\text{Invoice.total} \equiv \text{Invoice.subtotal} + \text{Invoice.tax\_amount}$$
$$\text{CustomerOpenBalance} \equiv \text{Invoice.total} - \sum \text{EligibleCompletedPayments.amount}$$
> **Quy định:** Không dùng sai số $\epsilon$ (epsilon) tùy tiện cho tiền bạc. Sai lệch quá 0 đơn vị nhỏ nhất (minor unit) phải kích hoạt `InvoiceReconciliationException`.

#### Bất biến 2: Đối soát Quyết toán Lương Tài xế (Driver Settlement Reconciliation)
$$\text{GrossEarnings} \equiv \sum \text{SettlementLines (EARNING)}$$
$$\text{TotalDeductions} \equiv \sum \text{SettlementLines (DEDUCTION)}$$
$$\text{TotalReimbursements} \equiv \sum \text{SettlementLines (REIMBURSEMENT)}$$
$$\text{SettlementNet} \equiv \text{GrossEarnings} - \text{TotalDeductions} + \text{TotalReimbursements}$$
> **Quy định:** Phân lớp dòng chi phí bằng Enum tường minh `SettlementLineClass` (`EARNING`, `DEDUCTION`, `REIMBURSEMENT`), không dùng các cờ boolean chồng chéo.

#### Bất biến 3: Phòng chống Tính trùng Chi phí (Double-Count Prevention)
Một khoản chi phí sửa chữa/bảo dưỡng xe nếu đã ghi nhận trong `maintenance_records` thì bản ghi thanh toán tương ứng trong `expenses` phải được liên kết qua khóa ngoại `expenses.maintenance_record_id`.
> **Quy tắc tính toán:** Khi tổng hợp chi phí vận hành (Total Operating Cost), hệ thống chỉ được lấy giá trị từ một nguồn duy nhất (hoặc `maintenance_records` hoặc `expenses`), tuyệt đối không `SUM()` cả 2 bảng dẫn đến đội khống chi phí gấp đôi.

#### Bất biến 4: Hàng rào Tiền tệ (Currency Guard)
**TUYỆT ĐỐI CẤM** cộng các giá trị khác loại tiền tệ (ví dụ: $1,000\text{ USD} + 5,000,000\text{ VND}$).
Mọi phép tính tổng hợp nhiều dòng tiền tệ phải:
1. Lọc theo đồng tiền báo cáo (`WHERE currency = :reportCurrency`), hoặc
2. Nhóm theo đồng tiền (`GROUP BY currency`), hoặc
3. Đi qua một Động cơ Quy đổi Tỷ giá chính thức (`FxConversionService`) có lưu version và ngày áp dụng tỷ giá.

---

### 3.5 Quy chuẩn Quản lý Trạng thái & State Machine (Status Machine Conventions)

Mọi quá trình chuyển đổi trạng thái phải được định nghĩa bằng State Pattern hoặc Service kiểm soát chuyển dịch hợp lệ. Không cho phép Controller nhận trạng thái tùy ý từ Client và lưu thẳng vào CSDL.

```
Vòng đời Settlement:
DRAFT -> CALCULATED -> VALIDATION_REQUIRED -> IN_REVIEW -> APPROVED -> LOCKED -> PAYMENT_SCHEDULED -> PAID
                                                                   └──> REJECTED / VOIDED

Vòng đời Payroll Run:
DRAFT -> CALCULATING -> CALCULATED -> REVIEWED -> APPROVED -> LOCKED -> PROCESSING_PAYMENT -> PAID
                                                                 └──> REJECTED / CANCELLED

Vòng đời Trip Stop:
PENDING -> EN_ROUTE -> ARRIVED -> SERVICE_STARTED -> SERVICE_COMPLETED -> DEPARTED
```

---

### 3.6 Quy chuẩn Phản hồi Chỉ số (Metric Availability Conventions)

Khi CSDL hiện tại chưa có đủ dữ liệu thành phần (mẫu số hoặc nguồn đối soát):
- **CẤM TRẢ VỀ SỐ 0 GIẢ MẠO (Fake Zero)** cho các chỉ số quan trọng (như Profit, Margin, CPM, Loaded/Empty Ratio).
- Bắt buộc trả về DTO chuẩn hóa với trạng thái khả dụng rõ ràng:

```java
public enum MetricAvailability {
    AVAILABLE,      // Đầy đủ dữ liệu, công thức chạy 100% tin cậy
    PARTIAL,        // Chỉ tính được 1 phần (ví dụ Known Operating Cost chưa có Driver Pay)
    UNAVAILABLE,    // Thiếu dữ liệu cốt lõi (ví dụ chưa có quãng đường dặm)
    NOT_APPLICABLE  // Không áp dụng cho đối tượng này
}
```

*Ví dụ Payload JSON trả về cho API Báo cáo:*
```json
{
  "code": "LOAD_PROFITABILITY",
  "value": null,
  "availability": "PARTIAL",
  "basis": "RECORDED_REVENUE_WITHOUT_COMPLETE_ALLOCATED_COST",
  "reason": "DRIVER_SETTLEMENT_AND_MAINTENANCE_ALLOCATION_NOT_RECORDED"
}
```

---

### 3.7 Quy chuẩn Quản lý Migration Flyway (Flyway Governance)

1. **Vị trí lưu trữ**: Toàn bộ script migration của tenant đặt tại thư mục:  
   `src/main/resources/db/migration/tenant/`
2. **Quy tắc đặt tên file**:
   `V{version}__{ten_ngan_gon_mo_ta_bang_tieng_anh}.sql`  
   *Ví dụ:* `V2__create_trip_driver_assignments.sql`
3. **Tính chất Bất biến (Immutability)**:
   - File migration một khi đã merge vào nhánh chính (`main`/`develop`) hoặc đã chạy trên bất kỳ môi trường nào thì **KHÔNG ĐƯỢC PHÉP CHỈNH SỬA**.
   - Nếu có lỗi, phải viết migration mới (`V{next}__...`) để sửa chữa.
4. **Không phụ thuộc câu lệnh riêng biệt của client**: Mọi lệnh DDL phải kèm mệnh đề an toàn:
   - `CREATE TABLE IF NOT EXISTS ...`
   - `ADD COLUMN IF NOT EXISTS ...`
   - `DROP CONSTRAINT IF EXISTS ...`
   - `CREATE INDEX IF NOT EXISTS ...`

---

## 4. KẾ HOẠCH TRIỂN KHAI CHI TIẾT THEO TỪNG BUSINESS & TASK

Lộ trình được chia làm 9 Phase (từ Phase 0 đến Phase 8) bao phủ toàn bộ 16 backend tickets (`BE-CALC-001` đến `BE-CALC-016`), đảm bảo chuyển hóa từng bước mà không làm gián đoạn hệ thống hiện tại.

```mermaid
flowchart TD
    P0["Phase 0: Semantic Audit (BE-CALC-001)"] --> P1["Phase 1: Metrics V1 từ DB hiện tại (BE-CALC-002, 003, 004)"]
    P0 --> P2["Phase 2: Execution Correctness & Core DDL (BE-CALC-005, 006, 007, 008, 009)"]
    P2 --> P3["Phase 3: Sổ cái Chi phí Vận chuyển & Lợi nhuận (BE-CALC-010, 011, 012)"]
    P2 --> P4["Phase 4: Quyết toán Thù lao Tài xế - Settlement (BE-CALC-013, 014)"]
    P3 --> P5["Phase 5: Kỳ lương Chính thức & Chi trả (BE-CALC-015)"]
    P4 --> P5
    P3 --> P6["Phase 6: Biểu giá Hợp đồng & FSC"]
    P5 --> P7["Phase 7: Điều phối Tối ưu - Optimization (BE-CALC-016)"]
    P6 --> P7
    P7 --> P8["Phase 8: Tỷ lệ Khai thác Phương tiện (Utilization)"]
```

---

### Phase 0: Semantic Audit & Khảo sát nghiệp vụ kế thừa (BE-CALC-001)

#### 1. Mục tiêu kinh doanh:
Làm rõ bản chất ngữ nghĩa của các trường dữ liệu mơ hồ trong CSDL hiện hữu trước khi viết code logic tính toán. Tuyệt đối không giả định ngầm.

#### 2. Danh sách Task chi tiết:
- [ ] **Task 0.1**: Rà soát cột `loads.distance` trong mã nguồn Java hiện tại:
  - Xác minh xem giá trị này là dặm định tuyến dự kiến (planned routing), dặm báo giá khách (quoted miles) hay dặm GPS thực tế (actual miles).
  - Tạm thời gán nhãn ngữ nghĩa là `recordedLoadDistance` trong DTO báo cáo.
- [ ] **Task 0.2**: Rà soát cột `trips.total_distance`:
  - Tìm toàn bộ các hàm đang ghi dữ liệu vào trường này. Xác định nguồn phát sinh (tính từ Google Maps API, OSRM hay tài xế tự gõ).
- [ ] **Task 0.3**: Rà soát cột `loads.delivery_cost_amount` & `loads.delivery_cost_currency`:
  - Kiểm tra xem đây là doanh thu báo khách (quoted revenue), chi phí ước tính (estimated cost) hay cước trả nhà xe phụ (carrier cost).
  - **Quy tắc an toàn**: CẤM đưa trường này vào công thức tính lợi nhuận (Profit) chừng nào chưa có tài liệu xác nhận nghiệp vụ.
- [ ] **Task 0.4**: Kiểm kê toàn bộ giá trị Enum thực tế trong mã nguồn:
  - Enum `InvoiceStatus`, `InvoiceType`.
  - Enum `ExpenseCategory`, `ExpenseType`, `TruckExpenseCategory`.
  - Enum `EmployeeSalaryType` (Salary, Hourly, Mileage...).
  - Enum `TripStatus`, `LoadStatus`.
- [ ] **Đầu ra (Deliverable)**: Biên bản tài liệu hóa `docs/current-domain-semantics.md`.

---

### Phase 1: Xây dựng Bộ chỉ số V1 từ CSDL hiện tại (BE-CALC-002 -> 004)

#### 1. Mục tiêu kinh doanh:
Cung cấp ngay lập tức các báo cáo tài chính, chi phí và vận hành khả dụng từ CSDL hiện có mà chưa cần can thiệp thay đổi cấu trúc bảng lớn.

#### 2. Danh sách Task chi tiết:
- [ ] **Task 1.1 (BE-CALC-002 - Doanh thu & Công nợ Khách hàng)**:
  - Tạo service `RevenueCalculator`: Tính tổng doanh thu đơn hàng từ các hóa đơn hợp lệ (`invoices.subtotal_amount` where `load_id = :id`).
  - Tạo service `InvoiceReconciliationService`: Kiểm tra tính đúng đắn giữa `subtotal` và tổng `invoice_line_items`.
  - Tạo service `CustomerBalanceCalculator`: Tính số tiền khách đã thanh toán (`payments`) và dư nợ còn lại (`open_balance`).
  - Bổ sung `CurrencyGuard` ngăn chặn tính toán sai khác đồng tiền.
  - Endpoints:
    - `GET /api/reports/revenue`
    - `GET /api/customers/{customerId}/balance`
    - `GET /api/reports/financials/monthly`
- [ ] **Task 1.2 (BE-CALC-003 - Hiệu suất Vận hành OTD & Độ trễ)**:
  - Tạo service `OnTimeCalculator`: Đánh giá giao hàng đúng hẹn dựa trên `loads.requested_delivery_date` và `loads.delivered_at`.
  - Tạo service `TransitTimeCalculator`: Tính thời gian vận chuyển (`delivered_at - picked_up_at`).
  - Tạo service `DelayCalculator`: Thống kê số phút trễ hẹn và tỷ lệ giao trễ.
  - Tạo service `ExceptionMetricsService`: Đo lường số lượng sự cố (`load_exceptions`) và thời gian xử lý sự cố (`resolved_at - occurred_at`).
  - Endpoints:
    - `GET /api/reports/operations/on-time-delivery`
    - `GET /api/reports/operations/delays`
    - `GET /api/reports/operations/exceptions-summary`
- [ ] **Task 1.3 (BE-CALC-004 - Chi phí Vận hành Hiện hữu & Chống tính trùng)**:
  - Tạo service `ExpenseReportService`: Tổng hợp chi phí đã duyệt từ bảng `expenses` theo chủng loại (Nhiên liệu, Cầu đường, Bến bãi...).
  - Tạo service `MaintenanceReportService`: Tổng hợp chi phí sửa chữa từ `maintenance_records` (`labor_cost + parts_cost`).
  - Tạo chính sách loại trừ tính trùng (`DoubleCountPreventionPolicy`): Tạm thời ưu tiên nguồn `maintenance_records`, tự động lọc bỏ các bản ghi `expenses` mang tính chất bảo dưỡng sửa chữa nếu trùng lặp.
  - Tính toán chỉ số Chi phí Vận hành Đã biết (`KnownOperatingCost` và `KnownOperatingCPM` đi kèm cờ `driverCostIncluded: false`).
  - Endpoints:
    - `GET /api/reports/expenses`
    - `GET /api/reports/fleet/fuel`
    - `GET /api/reports/fleet/maintenance`
    - `GET /api/reports/costs/known-operating-cpm`

---

### Phase 2: Chuẩn hóa Thực thi Vận hành & CSDL Cốt lõi (BE-CALC-005 -> 009)

#### 1. Mục tiêu kinh doanh:
Tạo dựng nền móng CSDL chuẩn hóa cho việc truy vết lịch sử điều phối, phân rã quãng đường, tính toán lưu bãi (detention) và kiểm toán snapshot.

#### 2. Danh sách Task chi tiết:
- [ ] **Task 2.1 (BE-CALC-005 - Lịch sử Phân công Tài xế Chuyến xe)**:
  - Viết Flyway Migration `V2__create_trip_driver_assignments.sql`.
  - Tạo Entity `TripDriverAssignment`: Lưu quan hệ nhiều-nhiều giữa `Trip` và `Employee` (Driver) theo khoảng thời gian hiệu lực (`effective_from`, `effective_to`), vai trò (`PRIMARY`, `SECONDARY`, `TEAM`, `RELIEF`).
  - Cập nhật logic màn hình Dispatch: Khi điều phối gán tài xế vào chuyến, tạo bản ghi snapshot vào bảng này.
  - CẤM lấy `trucks.main_driver_id` để tính thù lao lịch sử.
  - Endpoints:
    - `GET /api/trips/{tripId}/drivers`
    - `POST /api/trips/{tripId}/drivers`
    - `DELETE /api/trips/{tripId}/drivers/{assignmentId}`
- [ ] **Task 2.2 (BE-CALC-006 - Phân rã Quãng đường Chuyến xe)**:
  - Viết Flyway Migration `V3__add_trip_mileage_breakdown.sql`: Thêm vào bảng `trips` các cột:
    - `planned_distance_miles NUMERIC(12,3)`
    - `actual_distance_miles NUMERIC(12,3)`
    - `loaded_miles NUMERIC(12,3)`
    - `empty_miles NUMERIC(12,3)`
  - Cập nhật Entity `Trip`.
  - Tạo service tính toán tỷ lệ dặm chạy có hàng vs dặm chạy rỗng:
    $$\text{LoadedMilePercent} = \frac{\text{loaded\_miles}}{\text{actual\_distance\_miles}} \times 100$$
    $$\text{EmptyMilePercent} = \frac{\text{empty\_miles}}{\text{actual\_distance\_miles}} \times 100$$
- [ ] **Task 2.3 (BE-CALC-007 - Mốc thời gian Thực thi Điểm dừng)**:
  - Viết Flyway Migration `V4__extend_trip_stops_execution.sql`: Thêm vào bảng `trip_stops`:
    - `status VARCHAR(40)`
    - `appointment_start TIMESTAMPTZ`, `appointment_end TIMESTAMPTZ`
    - `service_started_at TIMESTAMPTZ`, `service_completed_at TIMESTAMPTZ`, `departed_at TIMESTAMPTZ`
  - Cập nhật Entity `TripStop`.
  - Viết API cập nhật tiến độ trạm cho tài xế/điều phối:
    - `POST /api/trip-stops/{id}/arrive` -> Cập nhật `arrived_at`, chuyển status = `ARRIVED`.
    - `POST /api/trip-stops/{id}/start-service` -> Cập nhật `service_started_at`, status = `SERVICE_STARTED`.
    - `POST /api/trip-stops/{id}/complete-service` -> Cập nhật `service_completed_at`.
    - `POST /api/trip-stops/{id}/depart` -> Cập nhật `departed_at`, status = `DEPARTED`.
  - Tính thời gian chờ tại điểm (Dwell Time) = `departed_at - arrived_at`.
- [ ] **Task 2.4 (BE-CALC-008 - Gắn định danh cho Chi phí Vận hành)**:
  - Viết Flyway Migration `V5__extend_expenses_attribution.sql`: Bổ sung các cột khóa ngoại vào `expenses`:
    - `load_id UUID REFERENCES loads(id)`
    - `trip_id UUID REFERENCES trips(id)`
    - `employee_id UUID REFERENCES employees(id)`
    - `maintenance_record_id UUID REFERENCES maintenance_records(id)`
    - `document_id UUID REFERENCES documents(id)`
  - Cập nhật Entity `Expense` và Repository. Cho phép gắn trực tiếp hóa đơn dầu/vé cầu đường vào đơn hàng hoặc chuyến xe cụ thể.
- [ ] **Task 2.5 (Load Event Sourcing Timeline)**:
  - Viết Flyway Migration `V6__create_load_events.sql`: Tạo bảng `load_events` ghi nhận toàn bộ dòng sự kiện:
    - `load_id`, `trip_id`, `trip_stop_id`, `event_type`, `previous_status`, `new_status`, `occurred_at`, `latitude`, `longitude`, `source`, `actor_id`, `note`.
  - Tạo Entity `LoadEvent` và `LoadTimelineService`.
  - Endpoints:
    - `GET /api/loads/{id}/timeline`
    - `POST /api/loads/{id}/events`
- [ ] **Task 2.6 (BE-CALC-009 - Hạ tầng Snapshot Tính toán Bất biến)**:
  - Viết Flyway Migration `V7__create_calculation_snapshots.sql`: Tạo bảng `calculation_snapshots`.
  - Lưu trữ: `entity_type`, `entity_id`, `calculation_type`, `engine_name`, `engine_version`, `policy_id`, `policy_version`, `input_json`, `result_json`, `checksum`, `calculated_at`.
  - Xây dựng `CalculationSnapshotService`: Mọi kết quả tính toán chi phí, doanh thu, thù lao, ETA đều phải sinh snapshot trước khi lưu vào bảng đích.

---

### Phase 3: Sổ cái Chi phí Vận chuyển, Phụ phí & Lợi nhuận (BE-CALC-010 -> 012)

#### 1. Mục tiêu kinh doanh:
Chuyển đổi toàn bộ chi phí rời rạc thành một sổ cái chi phí chuẩn hóa cấp đơn hàng (`shipment_costs`), bóc tách phụ phí khách hàng vs thù lao tài xế, và tự động hóa tính lãi lỗ thực tế từng đơn hàng (True Profitability & P&L).

#### 2. Danh sách Task chi tiết:
- [ ] **Task 3.1 (BE-CALC-010 - Sổ cái Chi phí Vận chuyển Canonical)**:
  - Viết Flyway Migration `V8__create_shipment_costs.sql`: Tạo bảng `shipment_costs`.
  - Cột: `load_id`, `trip_id`, `truck_id`, `driver_id`, `category` (FUEL, DRIVER, TOLL, MAINTENANCE, ACCESSORIAL...), `stage` (ESTIMATE, ACCRUAL, ACTUAL, APPROVED, POSTED), `source_type` (EXPENSE, MAINTENANCE_RECORD, DRIVER_SETTLEMENT, ALLOCATION), `source_id`, `amount`, `currency`.
  - Xây dựng `ShipmentCostEngine`: Tự động đồng bộ các bản ghi `expenses` (đã duyệt) có gắn `load_id` sang `shipment_costs` với `stage = ACTUAL`.
  - Xây dựng `CostAllocator`: Phân bổ chi phí bảo dưỡng xe theo tỷ lệ dặm đơn hàng:
    $$\text{AllocatedMaintenance} = \text{LoadEligibleMiles} \times \text{TruckMaintenanceCPM}$$
- [ ] **Task 3.2 (BE-CALC-012 - Quản lý Phụ phí & Tính toán Lưu bãi/Detention)**:
  - Viết Flyway Migration `V9__create_accessorial_charges.sql`: Tạo bảng `accessorial_charges`.
  - Tách bạch 3 cột số tiền:
    - `customer_amount NUMERIC(19,4)` (Phí thu khách hàng)
    - `company_cost_amount NUMERIC(19,4)` (Chi phí thực công ty chịu)
    - `driver_pay_amount NUMERIC(19,4)` (Thù lao bồi dưỡng cho tài xế)
  - Xây dựng `DetentionCalculator`:
    - Tính thời gian chờ thực tế = `DwellMinutes = departed_at - arrived_at`.
    - Tính phút vượt định mức = $\max(0, \text{DwellMinutes} - \text{FreeMinutes})$.
    - Tính tiền detention theo Block (ví dụ mỗi block 15 hoặc 30 phút) hoặc theo giờ liên tục.
  - Endpoints:
    - `POST /api/loads/{loadId}/accessorials`
    - `PUT /api/accessorial-charges/{id}/approve`
- [ ] **Task 3.3 (BE-CALC-011 - Động cơ Tính Lợi nhuận Đơn hàng & Phân tích Sai lệch)**:
  - Xây dựng `ProfitabilityService`:
    $$\text{ActualRevenue} = \sum \text{Eligible Invoice Subtotals}$$
    $$\text{ActualCost} = \sum \text{ShipmentCosts (stage in [ACTUAL, APPROVED, POSTED])}$$
    $$\text{ContributionMargin} = \text{ActualRevenue} - \text{ActualVariableCost}$$
    $$\text{AllocatedProfit} = \text{ContributionMargin} - \text{AllocatedFixedCost}$$
    $$\text{MarginPercent} = \frac{\text{AllocatedProfit}}{\text{ActualRevenue}} \times 100\%$$
  - Tính toán các chỉ số đơn vị:
    - Doanh thu trên mỗi dặm: $\text{RevenuePerTotalMile} = \text{Revenue} / \text{actual\_total\_miles}$
    - Chi phí trên mỗi dặm: $\text{CostPerTotalMile} = \text{ActualCost} / \text{actual\_total\_miles}$
    - Điểm hòa vốn cước có hàng: $\text{BreakEvenLoadedRate} = \text{ActualCost} / \text{loaded\_miles}$
  - Phân tích sai lệch chi phí (Cost Variance) = $\text{ActualCost} - \text{EstimatedCost}$.
  - Endpoints:
    - `GET /api/loads/{id}/financial-summary`
    - `GET /api/reports/profitability/by-load`
    - `GET /api/reports/profitability/by-lane`
    - `GET /api/reports/profitability/by-truck`

---

### Phase 4: Quyết toán Thù lao Tài xế - Driver Settlement (BE-CALC-013 -> 014)

#### 1. Mục tiêu kinh doanh:
Xây dựng cơ chế tính toán thu nhập tài xế theo chuyến đi/kỳ làm việc minh bạch, tách bạch hoàn toàn khỏi Hóa đơn khách hàng (`invoices`), hỗ trợ đầy đủ các hình thức trả lương ngành vận tải (theo dặm, theo cuốc, % cước, theo giờ, tiền chờ detention).

#### 2. Danh sách Task chi tiết:
- [ ] **Task 4.1 (BE-CALC-013 - Chính sách Thù lao Tài xế có Phiên bản)**:
  - Viết Flyway Migration `V10__create_driver_pay_and_settlement_tables.sql` (Phần 1: Bảng `driver_pay_policies` và `pay_periods`).
  - Hỗ trợ các phương thức: `PER_MILE`, `PER_LOAD`, `PERCENT_REVENUE`, `HOURLY`, `DAILY`, `FLAT_RATE`.
  - Thiết lập cơ chế hiệu lực theo ngày (`effective_from`, `effective_to`) và số phiên bản (`policy_version`).
  - Khi sửa chính sách: Tạo bản ghi phiên bản mới, không sửa đè phiên bản cũ đã chốt thù lao.
  - Endpoints:
    - `GET /api/driver-pay-policies`
    - `POST /api/driver-pay-policies`
    - `POST /api/driver-pay-policies/{id}/new-version`
- [ ] **Task 4.2 (BE-CALC-014 - Bảng Quyết toán Thù lao Chuyến - Driver Settlement)**:
  - Viết Flyway Migration `V10` (Phần 2: Bảng `settlements` và `settlement_lines`).
  - Xây dựng `DriverPayEngine` & `SettlementCalculator`:
    - Quét công việc từ `trip_driver_assignments` và `time_entries` trong kỳ.
    - Áp đúng phiên bản `driver_pay_policies` tại ngày tài xế thực hiện công việc.
    - Phân bổ phụ cấp dừng đỗ, lưu bãi (`accessorial_charges.driver_pay_amount`).
    - Bù đắp hoàn ứng (`REIMBURSEMENT`) và trừ tạm ứng/phạt (`DEDUCTION`).
    - Sinh các dòng chi tiết `settlement_lines`.
    - Đối soát bất biến `SettlementNet == Gross - Deductions + Reimbursements`.
    - Lưu snapshot vào `calculation_snapshots`.
  - Triển khai quy trình xét duyệt: `DRAFT` -> `CALCULATED` -> `IN_REVIEW` -> `APPROVED` -> `LOCKED`.
  - Khi đã `LOCKED`: Tự động bắn 1 dòng chi phí nhân công vào sổ cái `shipment_costs` với `category = DRIVER`, `stage = ACTUAL`.
  - Endpoints:
    - `POST /api/driver-settlements/calculate`
    - `GET /api/driver-settlements/{id}`
    - `POST /api/driver-settlements/{id}/approve`
    - `POST /api/driver-settlements/{id}/lock`

---

### Phase 5: Kỳ lương Chính thức, Phiếu lương & Chi trả (BE-CALC-015)

#### 1. Mục tiêu kinh doanh:
Chấm dứt việc dùng `invoices` làm bảng lương. Thiết lập quy trình chốt lương định kỳ (Payroll Runs), xuất phiếu lương bất biến (Payslips) và quản lý giao dịch chi trả qua ngân hàng/Stripe.

#### 2. Danh sách Task chi tiết:
- [ ] **Task 5.1 (CSDL Bảng lương Chính thức)**:
  - Viết Flyway Migration `V11__create_payroll_and_payslip_tables.sql`:
    - Tạo `payroll_runs`: Đợt chạy lương tổng thể cho công ty theo `pay_period_id`.
    - Tạo `payroll_run_items`: Dòng tổng hợp thù lao, thuế, bảo hiểm của từng tài xế.
    - Tạo `payslips`: Phiếu lương chính thức gửi tài xế (chứa `snapshot_json` và link tài liệu PDF).
    - Tạo `payroll_payments`: Sổ cái thanh toán tiền lương tài xế.
- [ ] **Task 5.2 (Quy trình Chạy lương & Khóa sổ Bất biến - Payroll Workflow)**:
  - Xây dựng `PayrollEngine`: Gom toàn bộ các `settlements` đã ở trạng thái `APPROVED` trong kỳ vào `payroll_runs`.
  - Tính thuế thu nhập và các khoản trích nộp theo quy định pháp lý.
  - Sau khi Giám đốc/Kế toán trưởng nhấn `Approve & Lock`:
    - Đổi trạng thái `payroll_runs` sang `LOCKED`.
    - Đổi toàn bộ settlements liên quan sang `PAID` / `CLOSED`.
    - Sinh bản ghi `payslips` với nội dung đóng băng bất biến.
- [ ] **Task 5.3 (Cổng Thanh toán Thù lao & Ứng dụng Di động cho Tài xế)**:
  - Tích hợp chi trả qua `payroll_payments` (kết nối `employees.stripe_connected_account_id` hoặc chuyển khoản ngân hàng).
  - Cung cấp API chuyên biệt cho tài xế xem phiếu lương trên ứng dụng di động:
    - `GET /api/driver/me/payslips`
    - `GET /api/payslips/{id}`
    - `GET /api/payslips/{id}/pdf`
- [ ] **Task 5.4 (Kế hoạch Khai tử cột lương trên Invoices - Deprecation Plan)**:
  - Đánh dấu `@Deprecated` trên các trường của Entity `Invoice`: `employee`, `periodStart`, `periodEnd`, `totalDistanceDriven`, `totalHoursWorked`.
  - Ngắt toàn bộ code nghiệp vụ mới không đọc/ghi vào các trường này.

---

### Phase 6: Biểu giá Hợp đồng Khách hàng & Phụ phí Nhiên liệu (Rate Rules & FSC)

#### 1. Mục tiêu kinh doanh:
Xây dựng động cơ định giá tự động cho khách hàng theo hợp đồng, tự động tính phụ phí nhiên liệu biến động theo thị trường (DOE Fuel Price Index).

#### 2. Danh sách Task chi tiết:
- [ ] **Task 6.1 (Bảng Biểu giá Hợp đồng)**:
  - Viết Flyway Migration `V12__create_rate_rules.sql`: Tạo bảng `rate_rules`.
  - Hỗ trợ các phương thức: `FLAT`, `PER_MILE`, `PER_WEIGHT`, `TIERED`, `INDEX_BASED`.
  - Thiết lập giá sàn tối thiểu (Minimum Charge) và giá trần tối đa (Maximum Charge).
- [ ] **Task 6.2 (Động cơ Phụ phí Nhiên liệu - FSC Engine)**:
  - Triển khai công thức phụ phí tiêu chuẩn ngành vận tải:
    $$\text{FSCPerMile} = \frac{\max(0, \text{CurrentFuelPrice} - \text{BaseFuelPrice})}{\text{ContractMPG}}$$
    $$\text{TotalFSC} = \text{FSCPerMile} \times \text{EligibleMiles}$$
  - Tự động sinh dòng cước phụ thu vào báo giá và hóa đơn khách hàng.
  - Lưu snapshot cấu hình tính FSC tại thời điểm báo giá.

---

### Phase 7: Tối ưu hóa Điều phối & Giải thuật Phân bổ Chuyến (BE-CALC-016)

#### 1. Mục tiêu kinh doanh:
Nâng cấp thuật toán gợi ý ghép chuyến (Smart Dispatch Matcher), tích hợp đồng thời ràng buộc an toàn HOS/ELD, tính khả thi của phương tiện, chi phí chạy rỗng (Deadhead Cost) và biên lợi nhuận kỳ vọng.

#### 2. Danh sách Task chi tiết:
- [ ] **Task 7.1 (Bảng Kiểm toán Tối ưu hóa Điều phối)**:
  - Viết Flyway Migration `V13__create_optimization_audit_tables.sql`:
    - Tạo `optimization_runs`: Lưu trữ phiên chạy giải thuật, thuật toán sử dụng, tập ràng buộc và trọng số mục tiêu.
    - Tạo `optimization_assignments`: Lưu trữ danh sách ứng viên (Tài xế + Xe), xếp hạng điểm số (Rank), tính khả thi (`feasible = true/false`), lý do loại bỏ và điểm chi tiết.
- [ ] **Task 7.2 (Bộ lọc Ràng buộc Khả thi Tuyệt đối - Hard Feasibility Gate)**:
  - CẤM chấm điểm ứng viên nếu vi phạm các điều kiện an toàn:
    ```java
    boolean isFeasible = truckCapacityCompatible     // Tải trọng xe >= Khối lượng hàng
                      && equipmentTypeCompatible     // Loại thùng xe (Reefer, Dryvan...)
                      && hazmatCompatible           // Xe/Tài xế có chứng chỉ hàng nguy hiểm ADR
                      && truckAvailable              // Xe không nằm xưởng bảo dưỡng
                      && driverHosFeasible           // Giờ lái xe HOS còn lại > Thời gian chạy tới đích
                      && pickupReachableOnTime;      // Kịp giờ hẹn lấy hàng
    ```
  - Nếu `isFeasible == false`: Lưu lý do cụ thể vào `rejection_reason` (ví dụ: `HOS_CYCLE_LIMIT_EXCEEDED`).
- [ ] **Task 7.3 (Động cơ Chấm điểm Trọng số Đa mục tiêu - Multi-Objective Scoring)**:
  - Chấm điểm cho các ứng viên thỏa mãn:
    $$\text{Score} = w_1 \cdot \text{DeadheadScore} + w_2 \cdot \text{MarginScore} - w_3 \cdot \text{DelayRisk} - w_4 \cdot \text{HOSRisk}$$
  - Mọi trọng số ($w_i$) và điểm thành phần đều được lưu minh bạch vào `optimization_assignments`.
  - Dispatcher có thể xem giải trình vì sao hệ thống gợi ý tài xế A thay vì tài xế B.

---

### Phase 8: Giám sát Đội xe & Tỷ lệ Khai thác Lịch sử (Fleet Utilization)

#### 1. Mục tiêu kinh doanh:
Đo lường chính xác tỷ lệ khai thác đội xe theo thời gian lịch sử thực tế thay vì chỉ chụp ảnh trạng thái tức thời (`trucks.status`).

#### 2. Danh sách Task chi tiết:
- [ ] **Task 8.1 (CSDL Sự kiện Trạng thái Xe)**:
  - Viết Flyway Migration `V14__create_vehicle_status_events.sql` (Tùy chọn khi triển khai tính năng telemetry mở rộng):
    - Tạo bảng `vehicle_status_events`: Ghi nhận các khoảng thời gian xe ở trạng thái: `DRIVING`, `IDLE`, `LOADING`, `MAINTENANCE`, `OFFLINE`.
- [ ] **Task 8.2 (Công thức Khai thác Đội xe - Fleet Utilization Engine)**:
  $$\text{FleetUtilization\%} = \frac{\text{Tổng thời gian lăn bánh có hàng (Productive Hours)}}{\text{Tổng thời gian xe khả dụng (Available Capacity Hours)}} \times 100\%$$
  - Endpoints:
    - `GET /api/reports/fleet/utilization-history`

---

## 5. LỘ TRÌNH FLYWAY MIGRATION DDL CHUẨN HÓA (V2 -> V14)

Dưới đây là chi tiết các kịch bản DDL chuẩn hóa theo đúng tiêu chuẩn PostgreSQL, bảo đảm tương thích với baseline `V1__baseline_business_schema.sql` hiện tại.

### Migration V2: Bảng Lịch sử Phân công Tài xế
**File:** `src/main/resources/db/migration/tenant/V2__create_trip_driver_assignments.sql`
```sql
-- V2__create_trip_driver_assignments.sql
CREATE TABLE IF NOT EXISTS trip_driver_assignments (
    id UUID PRIMARY KEY,
    trip_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    assignment_type VARCHAR(30) NOT NULL DEFAULT 'PRIMARY',
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    effective_from TIMESTAMPTZ NOT NULL,
    effective_to TIMESTAMPTZ,
    planned_miles NUMERIC(12,3),
    actual_miles NUMERIC(12,3),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    last_modified_at TIMESTAMPTZ,
    last_modified_by VARCHAR(50),

    CONSTRAINT fk_trip_driver_assignments_trip 
        FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE CASCADE,
    CONSTRAINT fk_trip_driver_assignments_driver 
        FOREIGN KEY (driver_id) REFERENCES employees(id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS ix_trip_driver_assignments_trip ON trip_driver_assignments(trip_id);
CREATE INDEX IF NOT EXISTS ix_trip_driver_assignments_driver_period ON trip_driver_assignments(driver_id, effective_from, effective_to);
```

---

### Migration V3: Bổ sung Phân rã Quãng đường cho Chuyến xe
**File:** `src/main/resources/db/migration/tenant/V3__add_trip_mileage_breakdown.sql`
```sql
-- V3__add_trip_mileage_breakdown.sql
ALTER TABLE trips
    ADD COLUMN IF NOT EXISTS planned_distance_miles NUMERIC(12,3),
    ADD COLUMN IF NOT EXISTS actual_distance_miles NUMERIC(12,3),
    ADD COLUMN IF NOT EXISTS loaded_miles NUMERIC(12,3),
    ADD COLUMN IF NOT EXISTS empty_miles NUMERIC(12,3);

COMMENT ON COLUMN trips.total_distance IS 'DEPRECATED: Legacy distance value. Use actual_distance_miles or loaded_miles instead.';
```

---

### Migration V4: Bổ sung Mốc thời gian Thực thi Điểm dừng
**File:** `src/main/resources/db/migration/tenant/V4__extend_trip_stops_execution.sql`
```sql
-- V4__extend_trip_stops_execution.sql
ALTER TABLE trip_stops
    ADD COLUMN IF NOT EXISTS status VARCHAR(40) DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS appointment_start TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS appointment_end TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS service_started_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS service_completed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS departed_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS ix_trip_stops_trip_status ON trip_stops(trip_id, status);
```

---

### Migration V5: Bổ sung Liên kết Nghiệp vụ cho Chi phí
**File:** `src/main/resources/db/migration/tenant/V5__extend_expenses_attribution.sql`
```sql
-- V5__extend_expenses_attribution.sql
ALTER TABLE expenses
    ADD COLUMN IF NOT EXISTS load_id UUID,
    ADD COLUMN IF NOT EXISTS trip_id UUID,
    ADD COLUMN IF NOT EXISTS employee_id UUID,
    ADD COLUMN IF NOT EXISTS maintenance_record_id UUID,
    ADD COLUMN IF NOT EXISTS document_id UUID;

DO $$ 
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_expenses_load') THEN
        ALTER TABLE expenses ADD CONSTRAINT fk_expenses_load FOREIGN KEY (load_id) REFERENCES loads(id) ON DELETE SET NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_expenses_trip') THEN
        ALTER TABLE expenses ADD CONSTRAINT fk_expenses_trip FOREIGN KEY (trip_id) REFERENCES trips(id) ON DELETE SET NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_expenses_employee') THEN
        ALTER TABLE expenses ADD CONSTRAINT fk_expenses_employee FOREIGN KEY (employee_id) REFERENCES employees(id) ON DELETE SET NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_expenses_maintenance_record') THEN
        ALTER TABLE expenses ADD CONSTRAINT fk_expenses_maintenance_record FOREIGN KEY (maintenance_record_id) REFERENCES maintenance_records(id) ON DELETE SET NULL;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_expenses_document') THEN
        ALTER TABLE expenses ADD CONSTRAINT fk_expenses_document FOREIGN KEY (document_id) REFERENCES documents(id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS ix_expenses_load_id ON expenses(load_id);
CREATE INDEX IF NOT EXISTS ix_expenses_trip_id ON expenses(trip_id);
CREATE INDEX IF NOT EXISTS ix_expenses_maintenance_record_id ON expenses(maintenance_record_id);
```

---

### Migration V6: Tạo Bảng Dòng sự kiện Vận đơn (Load Events)
**File:** `src/main/resources/db/migration/tenant/V6__create_load_events.sql`
```sql
-- V6__create_load_events.sql
CREATE TABLE IF NOT EXISTS load_events (
    id UUID PRIMARY KEY,
    load_id UUID NOT NULL,
    trip_id UUID,
    trip_stop_id UUID,
    event_type VARCHAR(60) NOT NULL,
    previous_status VARCHAR(40),
    new_status VARCHAR(40),
    occurred_at TIMESTAMPTZ NOT NULL,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    source VARCHAR(30) NOT NULL DEFAULT 'SYSTEM',
    actor_id UUID,
    document_id UUID,
    note VARCHAR(2000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_load_events_load FOREIGN KEY (load_id) REFERENCES loads(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS ix_load_events_load_occurred ON load_events(load_id, occurred_at);
```

---

### Migration V7: Tạo Bảng Bản chụp Tính toán Bất biến (Calculation Snapshots)
**File:** `src/main/resources/db/migration/tenant/V7__create_calculation_snapshots.sql`
```sql
-- V7__create_calculation_snapshots.sql
CREATE TABLE IF NOT EXISTS calculation_snapshots (
    id UUID PRIMARY KEY,
    entity_type VARCHAR(60) NOT NULL,
    entity_id UUID NOT NULL,
    calculation_type VARCHAR(60) NOT NULL,
    engine_name VARCHAR(100) NOT NULL,
    engine_version VARCHAR(50) NOT NULL,
    policy_type VARCHAR(80),
    policy_id UUID,
    policy_version VARCHAR(50),
    input_json JSONB NOT NULL,
    result_json JSONB NOT NULL,
    currency VARCHAR(3),
    checksum VARCHAR(128),
    calculated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    calculated_by UUID,
    correlation_id VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS ix_calculation_snapshots_entity ON calculation_snapshots(entity_type, entity_id);
CREATE INDEX IF NOT EXISTS ix_calculation_snapshots_correlation ON calculation_snapshots(correlation_id);
```

---

### Migration V8: Tạo Sổ cái Chi phí Vận chuyển (Shipment Costs)
**File:** `src/main/resources/db/migration/tenant/V8__create_shipment_costs.sql`
```sql
-- V8__create_shipment_costs.sql
CREATE TABLE IF NOT EXISTS shipment_costs (
    id UUID PRIMARY KEY,
    load_id UUID NOT NULL,
    trip_id UUID,
    truck_id UUID,
    driver_id UUID,
    category VARCHAR(40) NOT NULL,
    stage VARCHAR(30) NOT NULL,
    source_type VARCHAR(40) NOT NULL,
    source_id UUID,
    allocation_method VARCHAR(40),
    quantity NUMERIC(19,6),
    unit VARCHAR(30),
    unit_rate NUMERIC(19,6),
    amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    incurred_at TIMESTAMPTZ,
    verified_at TIMESTAMPTZ,
    verified_by UUID,
    approved_at TIMESTAMPTZ,
    approved_by UUID,
    posted_at TIMESTAMPTZ,
    calculation_snapshot_id UUID,
    note VARCHAR(2000),
    version INT8 NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(50),
    last_modified_at TIMESTAMPTZ,
    last_modified_by VARCHAR(50),

    CONSTRAINT fk_shipment_costs_load FOREIGN KEY (load_id) REFERENCES loads(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS ix_shipment_costs_load_stage ON shipment_costs(load_id, stage);
CREATE INDEX IF NOT EXISTS ix_shipment_costs_source ON shipment_costs(source_type, source_id);
```

---

### Migration V9: Tạo Bảng Phụ phí Lưu bãi & Phát sinh (Accessorial Charges)
**File:** `src/main/resources/db/migration/tenant/V9__create_accessorial_charges.sql`
```sql
-- V9__create_accessorial_charges.sql
CREATE TABLE IF NOT EXISTS accessorial_charges (
    id UUID PRIMARY KEY,
    load_id UUID NOT NULL,
    trip_id UUID,
    trip_stop_id UUID,
    type VARCHAR(40) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_APPROVAL',
    quantity NUMERIC(19,6),
    unit VARCHAR(30),
    rate NUMERIC(19,6),
    free_quantity NUMERIC(19,6) DEFAULT 0,
    customer_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    company_cost_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    driver_pay_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    currency VARCHAR(3) NOT NULL,
    occurred_at TIMESTAMPTZ,
    approved_at TIMESTAMPTZ,
    approved_by UUID,
    document_id UUID,
    calculation_snapshot_id UUID,
    note VARCHAR(2000),
    version INT8 NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_accessorial_charges_load FOREIGN KEY (load_id) REFERENCES loads(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS ix_accessorial_charges_load ON accessorial_charges(load_id);
```

---

### Migration V10: Tạo CSDL Chính sách Thù lao & Quyết toán Lương Tài xế
**File:** `src/main/resources/db/migration/tenant/V10__create_driver_pay_and_settlement_tables.sql`
```sql
-- V10__create_driver_pay_and_settlement_tables.sql

-- 1. Bảng kỳ tính thù lao/lương
CREATE TABLE IF NOT EXISTS pay_periods (
    id UUID PRIMARY KEY,
    period_code VARCHAR(50) NOT NULL UNIQUE,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    payment_date DATE,
    status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_pay_periods_dates CHECK (start_date <= end_date)
);

-- 2. Bảng chính sách thù lao tài xế
CREATE TABLE IF NOT EXISTS driver_pay_policies (
    id UUID PRIMARY KEY,
    policy_code VARCHAR(80) NOT NULL,
    name VARCHAR(200) NOT NULL,
    driver_id UUID,
    pay_method VARCHAR(40) NOT NULL,
    per_mile_rate NUMERIC(19,6),
    per_load_rate NUMERIC(19,4),
    hourly_rate NUMERIC(19,6),
    daily_rate NUMERIC(19,4),
    flat_rate NUMERIC(19,4),
    revenue_percentage NUMERIC(9,6),
    mileage_basis VARCHAR(40),
    revenue_basis VARCHAR(50),
    detention_rate NUMERIC(19,6),
    detention_free_minutes INTEGER DEFAULT 120,
    detention_block_minutes INTEGER DEFAULT 15,
    layover_rate NUMERIC(19,4),
    stop_pay_rate NUMERIC(19,4),
    currency VARCHAR(3) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    policy_version INTEGER NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_driver_pay_policies_version UNIQUE (policy_code, policy_version)
);

-- 3. Bảng quyết toán thù lao chuyến/kỳ
CREATE TABLE IF NOT EXISTS settlements (
    id UUID PRIMARY KEY,
    settlement_number VARCHAR(60) NOT NULL UNIQUE,
    driver_id UUID NOT NULL,
    pay_period_id UUID NOT NULL,
    pay_policy_id UUID NOT NULL,
    pay_policy_version INTEGER NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    currency VARCHAR(3) NOT NULL,
    mileage_pay NUMERIC(19,4) NOT NULL DEFAULT 0,
    load_pay NUMERIC(19,4) NOT NULL DEFAULT 0,
    percentage_pay NUMERIC(19,4) NOT NULL DEFAULT 0,
    hourly_pay NUMERIC(19,4) NOT NULL DEFAULT 0,
    accessorial_pay NUMERIC(19,4) NOT NULL DEFAULT 0,
    bonus_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    reimbursement_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    deduction_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    gross_earnings NUMERIC(19,4) NOT NULL DEFAULT 0,
    settlement_net NUMERIC(19,4) NOT NULL DEFAULT 0,
    eligible_miles NUMERIC(19,3),
    loaded_miles NUMERIC(19,3),
    empty_miles NUMERIC(19,3),
    eligible_hours NUMERIC(19,3),
    calculation_snapshot_id UUID NOT NULL,
    calculated_at TIMESTAMPTZ,
    reviewed_at TIMESTAMPTZ,
    reviewed_by UUID,
    approved_at TIMESTAMPTZ,
    approved_by UUID,
    locked_at TIMESTAMPTZ,
    locked_by UUID,
    paid_at TIMESTAMPTZ,
    version INT8 NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_settlements_driver_period UNIQUE (driver_id, pay_period_id),
    CONSTRAINT fk_settlements_driver FOREIGN KEY (driver_id) REFERENCES employees(id),
    CONSTRAINT fk_settlements_period FOREIGN KEY (pay_period_id) REFERENCES pay_periods(id)
);

-- 4. Bảng chi tiết các dòng thù lao
CREATE TABLE IF NOT EXISTS settlement_lines (
    id UUID PRIMARY KEY,
    settlement_id UUID NOT NULL,
    line_type VARCHAR(50) NOT NULL,
    load_id UUID,
    trip_id UUID,
    accessorial_charge_id UUID,
    expense_id UUID,
    description VARCHAR(300) NOT NULL,
    quantity NUMERIC(19,6),
    unit VARCHAR(30),
    rate NUMERIC(19,6),
    amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    taxable BOOLEAN DEFAULT TRUE,
    line_class VARCHAR(30) NOT NULL, -- EARNING, DEDUCTION, REIMBURSEMENT
    source_type VARCHAR(40),
    source_id UUID,
    calculation_snapshot_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_settlement_lines_settlement FOREIGN KEY (settlement_id) REFERENCES settlements(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS ix_settlement_lines_settlement ON settlement_lines(settlement_id);
```

---

### Migration V11: Tạo CSDL Đợt chạy Lương, Phiếu lương Bất biến & Chi trả
**File:** `src/main/resources/db/migration/tenant/V11__create_payroll_and_payslip_tables.sql`
```sql
-- V11__create_payroll_and_payslip_tables.sql

-- 1. Bảng đợt chạy bảng lương
CREATE TABLE IF NOT EXISTS payroll_runs (
    id UUID PRIMARY KEY,
    payroll_number VARCHAR(60) NOT NULL UNIQUE,
    pay_period_id UUID NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'DRAFT',
    currency VARCHAR(3) NOT NULL,
    driver_count INTEGER NOT NULL DEFAULT 0,
    gross_pay_total NUMERIC(19,4) NOT NULL DEFAULT 0,
    tax_total NUMERIC(19,4) NOT NULL DEFAULT 0,
    deduction_total NUMERIC(19,4) NOT NULL DEFAULT 0,
    reimbursement_total NUMERIC(19,4) NOT NULL DEFAULT 0,
    net_pay_total NUMERIC(19,4) NOT NULL DEFAULT 0,
    calculation_snapshot_id UUID,
    calculated_at TIMESTAMPTZ,
    reviewed_at TIMESTAMPTZ,
    reviewed_by UUID,
    approved_at TIMESTAMPTZ,
    approved_by UUID,
    locked_at TIMESTAMPTZ,
    locked_by UUID,
    paid_at TIMESTAMPTZ,
    version INT8 NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payroll_runs_period FOREIGN KEY (pay_period_id) REFERENCES pay_periods(id)
);

-- 2. Dòng thù lao từng nhân sự trong đợt chạy lương
CREATE TABLE IF NOT EXISTS payroll_run_items (
    id UUID PRIMARY KEY,
    payroll_run_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    settlement_id UUID NOT NULL,
    gross_pay NUMERIC(19,4) NOT NULL,
    tax_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    deduction_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    reimbursement_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    net_pay NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'CALCULATED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_payroll_run_items_driver UNIQUE (payroll_run_id, driver_id),
    CONSTRAINT fk_payroll_run_items_run FOREIGN KEY (payroll_run_id) REFERENCES payroll_runs(id) ON DELETE CASCADE,
    CONSTRAINT fk_payroll_run_items_settlement FOREIGN KEY (settlement_id) REFERENCES settlements(id)
);

-- 3. Bảng phiếu lương chính thức bất biến
CREATE TABLE IF NOT EXISTS payslips (
    id UUID PRIMARY KEY,
    payslip_number VARCHAR(60) NOT NULL UNIQUE,
    payroll_run_id UUID NOT NULL,
    payroll_run_item_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    pay_period_id UUID NOT NULL,
    currency VARCHAR(3) NOT NULL,
    gross_pay NUMERIC(19,4) NOT NULL,
    tax_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    deduction_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    reimbursement_amount NUMERIC(19,4) NOT NULL DEFAULT 0,
    net_pay NUMERIC(19,4) NOT NULL,
    document_id UUID,
    snapshot_json JSONB NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payslips_driver FOREIGN KEY (driver_id) REFERENCES employees(id),
    CONSTRAINT fk_payslips_run FOREIGN KEY (payroll_run_id) REFERENCES payroll_runs(id)
);

-- 4. Bảng sổ cái thanh toán chi trả lương
CREATE TABLE IF NOT EXISTS payroll_payments (
    id UUID PRIMARY KEY,
    payroll_run_item_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    amount NUMERIC(19,4) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    payment_method VARCHAR(40),
    provider VARCHAR(50),
    provider_reference VARCHAR(200),
    scheduled_at TIMESTAMPTZ,
    paid_at TIMESTAMPTZ,
    failure_code VARCHAR(80),
    failure_message VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payroll_payments_item FOREIGN KEY (payroll_run_item_id) REFERENCES payroll_run_items(id)
);

CREATE INDEX IF NOT EXISTS ix_payroll_payments_driver ON payroll_payments(driver_id);
```

---

### Migration V12: Tạo Bảng Định giá Hợp đồng (Rate Rules)
**File:** `src/main/resources/db/migration/tenant/V12__create_rate_rules.sql`
```sql
-- V12__create_rate_rules.sql
CREATE TABLE IF NOT EXISTS rate_rules (
    id UUID PRIMARY KEY,
    rule_code VARCHAR(80) NOT NULL,
    customer_id UUID,
    method VARCHAR(40) NOT NULL,
    base_rate NUMERIC(19,6) NOT NULL,
    minimum_charge NUMERIC(19,4),
    maximum_charge NUMERIC(19,4),
    currency VARCHAR(3) NOT NULL,
    effective_from DATE NOT NULL,
    effective_to DATE,
    version INTEGER NOT NULL DEFAULT 1,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_rate_rules_customer FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS ix_rate_rules_customer ON rate_rules(customer_id, active);
```

---

### Migration V13: Tạo Bảng Kiểm toán Điều phối Tối ưu (Optimization Audit)
**File:** `src/main/resources/db/migration/tenant/V13__create_optimization_audit_tables.sql`
```sql
-- V13__create_optimization_audit_tables.sql
CREATE TABLE IF NOT EXISTS optimization_runs (
    id UUID PRIMARY KEY,
    run_type VARCHAR(50) NOT NULL,
    status VARCHAR(40) NOT NULL,
    solver_name VARCHAR(100) NOT NULL,
    solver_version VARCHAR(50),
    objective_type VARCHAR(50) NOT NULL,
    objective_value NUMERIC(19,6),
    load_count INTEGER NOT NULL DEFAULT 0,
    driver_count INTEGER NOT NULL DEFAULT 0,
    truck_count INTEGER NOT NULL DEFAULT 0,
    assigned_count INTEGER NOT NULL DEFAULT 0,
    unassigned_count INTEGER NOT NULL DEFAULT 0,
    input_json JSONB NOT NULL,
    constraint_json JSONB NOT NULL,
    weight_json JSONB,
    result_json JSONB,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    duration_ms BIGINT,
    triggered_by UUID,
    failure_code VARCHAR(80),
    failure_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS optimization_assignments (
    id UUID PRIMARY KEY,
    optimization_run_id UUID NOT NULL,
    load_id UUID NOT NULL,
    trip_id UUID,
    driver_id UUID,
    truck_id UUID,
    rank INTEGER,
    deadhead_miles NUMERIC(12,3),
    estimated_total_miles NUMERIC(12,3),
    estimated_cost NUMERIC(19,4),
    estimated_revenue NUMERIC(19,4),
    estimated_profit NUMERIC(19,4),
    estimated_margin_percent NUMERIC(9,4),
    eta_pickup TIMESTAMPTZ,
    eta_delivery TIMESTAMPTZ,
    hos_remaining_minutes INTEGER,
    late_risk_score NUMERIC(10,6),
    cost_score NUMERIC(10,6),
    margin_score NUMERIC(10,6),
    hos_risk_score NUMERIC(10,6),
    final_score NUMERIC(10,6),
    feasible BOOLEAN NOT NULL,
    rejection_reason VARCHAR(500),
    selected BOOLEAN NOT NULL DEFAULT FALSE,
    selected_by UUID,
    selected_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_optimization_assignments_run FOREIGN KEY (optimization_run_id) REFERENCES optimization_runs(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS ix_optimization_assignments_run ON optimization_assignments(optimization_run_id);
```

---

## 6. CHIẾN LƯỢC MIGRATION AN TOÀN (EXPAND & CONTRACT PATTERN)

Để bảo đảm quá trình nâng cấp CSDL không gây downtime và không làm gãy các tính năng đang vận hành của ứng dụng, toàn bộ đội ngũ bắt buộc tuân theo mô hình 4 bước:

```
[ Bước 1: Expand ]      Thêm bảng mới, thêm cột mới (Nullable). Giữ nguyên toàn bộ cấu trúc cũ.
                               ↓
[ Bước 2: Dual-Write ]   Backend mới ghi đồng thời vào cả cột cũ và cột mới. Chạy script backfill.
                               ↓
[ Bước 3: Switch Read ]  Chuyển toàn bộ các truy vấn đọc sang cột mới/bảng mới. Deprecate cột cũ.
                               ↓
[ Bước 4: Contract ]     (Sau 1-2 quý) Xóa bỏ vĩnh viễn các cột/bảng cũ đã bị deprecate.
```

### Các trường hợp cụ thể trong dự án:
1. **Trường `trips.total_distance`**:
   - *Expand*: Thêm `actual_distance_miles`, `loaded_miles`, `empty_miles`.
   - *Dual-Write*: Khi kết thúc chuyến, ghi cả `actual_distance_miles` và `total_distance`.
   - *Backfill*: Viết script cập nhật `actual_distance_miles = total_distance` cho các chuyến lịch sử có số liệu hợp lệ.
   - *Switch Read*: Toàn bộ API tính CPM, Doanh thu/Dặm chuyển sang đọc `actual_distance_miles`.
   - *Contract*: Giữ lại `total_distance` ít nhất 6 tháng trước khi xem xét DROP COLUMN.
2. **Khai tử các cột lương trên bảng `invoices`**:
   - *Tình trạng hiện tại*: `invoices` có các cột `employee_id`, `period_start`, `period_end`, `total_distance_driven`, `total_hours_worked`.
   - *Thực thi*:
     - **Không xóa ngay** các cột này để tránh làm crash các view/code cũ.
     - Tạo hệ thống mới: `settlements`, `payroll_runs`, `payslips`.
     - Ngừng phát sinh bản ghi lương mới trong `invoices`. Bảng `invoices` từ nay 100% chỉ phục vụ Khách hàng (Customer AR).

---

## 7. MA TRẬN KIỂM THỬ (TEST MATRIX) & DEFINITION OF DONE (DoD)

### 7.1 Ma trận Kiểm thử Đơn vị & Tích hợp (Test Suite Matrix)

| Phân hệ | Kịch bản kiểm thử (Test Case) | Tiêu chí kỳ vọng (Acceptance Criteria) |
|---|---|---|
| **Doanh thu (Revenue)** | Đơn hàng có 1 hóa đơn, nhiều hóa đơn | Tính tổng doanh thu chính xác theo `subtotal`. |
| | Hóa đơn khác đồng tiền (USD vs VND) | Hệ thống chặn tính tổng, ném `CurrencyMismatchException`. |
| | Hóa đơn bị lệch dòng chi tiết (`subtotal != sum(lines)`) | Ném `InvoiceReconciliationException`, không lưu. |
| | Khách thanh toán thừa (Overpayment) | Báo cáo thể hiện đúng số dư nợ âm hoặc cảnh báo. |
| **Chi phí (Expense & Maintenance)** | Chi phí đã duyệt vs Chi phí chờ duyệt | Báo cáo chi phí hoạt động chỉ gom trạng thái `APPROVED`. |
| | Chi phí sửa chữa xe có hóa đơn và phiếu bảo dưỡng | `DoubleCountPreventionPolicy` phát hiện và chỉ cộng 1 lần. |
| | Phân bổ bảo dưỡng theo dặm (`CostAllocator`) | Phân bổ chính xác theo tỷ lệ dặm chạy của đơn hàng. |
| **Thực thi (Trip & Stops)** | Lưu bãi (Detention) dưới thời gian miễn phí (< Free minutes) | Tiền detention bằng 0. |
| | Lưu bãi vượt 45 phút, định mức block 15 phút | Tính tròn 3 blocks detention và nhân đơn giá chính xác. |
| | Phân công tài xế qua các chặng thời gian | Lịch sử phân công được bảo toàn, không bị ghi đè tài xế mới. |
| **Quyết toán (Settlement)** | Công thức tính lương theo dặm, cuốc, % doanh thu | Tính đúng từng dòng `settlement_lines`. |
| | Đối soát thù lao (`Net == Gross - Deductions + Reimb.`) | Chênh lệch 0 minor units. Bất kỳ sai số nào đều chặn lưu. |
| | Sửa đổi settlement đã ở trạng thái `LOCKED` | Ném lỗi `400 Bad Request / IllegalStateException`. |
| **Bảng lương (Payroll)** | Gom nhiều settlement vào đợt chạy lương | Tổng `gross_pay_total` khớp 100% tổng các item con. |
| | Xuất phiếu lương `payslip` | Bản chụp `snapshot_json` độc lập, không bị biến động khi sửa policy sau này. |
| **Tối ưu hóa (Optimization)** | Ứng dụng vi phạm luật giờ lái xe HOS | Bị loại ngay lập tức tại Hard Feasibility Gate, lý do rõ ràng. |
| | Trọng số giải thuật | Sinh điểm số chi tiết `final_score` kèm giải trình các điểm thành phần. |

---

### 7.2 Tiêu chuẩn Hoàn thành (Definition of Done - DoD)

Một task hoặc một giai đoạn migration chỉ được nghiệm thu khi đạt đầy đủ các tiêu chuẩn sau:

- [ ] Script Flyway DDL chạy thành công trên môi trường kiểm thử (Local/Staging) từ bản trắng (clean database) và từ bản baseline nâng cấp.
- [ ] Các Entity JPA được ánh xạ đầy đủ, thiết lập khóa ngoại, ràng buộc duy nhất và FetchType.LAZY chính xác.
- [ ] Tất cả các phép tính toán tiền tệ đều dùng `BigDecimal`, có kiểm tra tiền tệ (`CurrencyGuard`) và kiểm soát làm tròn (`RoundingMode.HALF_UP`).
- [ ] Mọi aggregate tài chính đều có `@Version` kiểm soát khóa lạc quan.
- [ ] Mọi phép tính trọng yếu (Revenue, Costing, Settlement, Optimization) đều sinh bản chụp bất biến vào `calculation_snapshots`.
- [ ] Viết đầy đủ Unit Test cho tầng nghiệp vụ (đạt độ bao phủ code tối thiểu 85% cho các Engine/Service tính toán).
- [ ] Viết Integration Test kiểm tra toàn bộ luồng từ Controller -> Service -> CSDL với Testcontainers PostgreSQL.
- [ ] Không sinh ra bất kỳ lỗi hồi quy (regression) nào trên các API hiện hữu đang phục vụ khách hàng và tài xế.
- [ ] Tài liệu Swagger/OpenAPI được cập nhật đầy đủ các endpoint mới.

---
**HẾT BẢN KẾ HOẠCH & QUY CHUẨN KỸ THUẬT (PLAN-CONVENTION.MD)**
