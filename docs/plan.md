# Kế hoạch Triển khai LogisticsX TMS Execution & Workflow System

> **Tầm nhìn dự án**: Chuyển đổi LogisticsX từ hệ thống **CRUD dữ liệu nền** thành một **Hệ thống Quản lý Vận tải (TMS) hoàn chỉnh chuẩn doanh nghiệp**, lấy cảm hứng từ các nền tảng hàng đầu ngành vận tải: **Oracle OTM**, **SAP TM**, **Samsara**, **Rose Rocket** và tuân thủ các tiêu chuẩn vận tải **FMCSA (HOS/ELD)**.

---

## I. Tổng quan Kiến trúc & Nguyên lý cốt lõi

### 1. Bổ sung lớp Execution / State Machine ở giữa
Hệ thống hiện tại đã có cấu trúc dữ liệu nền (50 JPA Entities trong `sql.md`), tuy nhiên các thao tác chủ yếu là ghi đè thuộc tính trực tiếp. Mục tiêu là bổ sung lớp **Execution Engine** với 3 nguyên tắc:
1. **Status Machine nghiêm ngặt**: Không cập nhật trạng thái tùy tiện; mọi chuyển đổi phải tuân theo ma trận trạng thái hợp lệ.
2. **Event Sourcing / Timeline History**: Mọi sự kiện phát sinh trong đời sống đơn hàng (`LoadEvent`, `TripEvent`) đều được ghi nhận bất biến (immutable), lưu kèm thời gian, tọa độ GPS, nguồn phát sinh (Driver App, Dispatcher, GPS, Geofence, ELD, System) và người thực hiện.
3. **Phân tách ranh giới nghiệp vụ (Bounded Contexts)**:
   - Tách biệt **Load Status** (vòng đời thương mại), **Trip Status** (vòng đời chuyến đi vật lý) và **Stop Status** (tại từng điểm kho).
   - Tách biệt **Driver Settlement** (tính lương theo chuyến) và **Payroll Run** (chốt kỳ lương chính thức và khóa sổ).
   - Tách biệt **Accessorial Charge** cho khách hàng với **Accessorial Pay** trả cho tài xế.

```
┌────────────────────────────────────────────────────────────────────────┐
│                        LOGISTICSX TMS EXECUTION                        │
└────────────────────────────────────────────────────────────────────────┘
    │
    ├── 1. Order Management (Load, Stop, Event History, Public Tracking)
    ├── 2. Dispatch Board (Candidate Matcher, HOS Gate, Live Assignment)
    ├── 3. Fleet Live Map & Telematics (GPS, Geofence, Diagnostics, WebSocket)
    ├── 4. Driver Portal (My Day, Arrived, Departed, Exception Report)
    ├── 5. Documents & Accessorials (POD, BOL, Receipts, Detention/Layover)
    ├── 6. Driver Settlement (Pay Policies: Mile, Load, %, Additions/Deductions)
    ├── 7. Payroll Runs (Pay Period, Payslip Generation, Locked Records)
    ├── 8. Fleet Maintenance & Fuel (DVIR -> Work Order, Fuel MPG Tracking)
    ├── 9. Financials & Profitability (P&L by Load/Lane/Truck, AR Aging)
    └── 10. Operations Center & Lark Integration (KPIs, Bot Alerts, Lark Base)
```

---

## II. Lộ trình Triển khai 10 Giai đoạn (Phased Roadmap)

---

### GIAI ĐOẠN 1: Order Lifecycle, LoadEvent & Public Customer Tracking
> **Mục tiêu**: Nâng cấp module `Loads` từ CRUD thành Trung tâm Điều phối Đơn hàng (Order Center) với lịch sử Timeline chi tiết và liên kết tra cứu công khai.

#### 1. Cơ sở dữ liệu (Flyway Migration `V5__load_execution_events.sql`)
* Mở rộng bảng `loads`:
  * Cột `current_status`: `DRAFT`, `BOOKED`, `PLANNED`, `DISPATCHED`, `IN_TRANSIT`, `DELIVERED`, `COMPLETED`, `CANCELLED`.
  * Cột `eta`: `OffsetDateTime`.
  * Cột `progress_percentage`: `Double`.
  * Cột `total_miles`: `Double`.
* Bảng mới `load_events`:
  * `id` (UUID PK), `load_id` (UUID FK), `trip_id` (UUID FK nullable), `stop_id` (UUID FK nullable).
  * `event_type` (VARCHAR): `BOOKED`, `DISPATCHED`, `ARRIVED_PICKUP`, `LOADING_STARTED`, `PICKED_UP`, `DEPARTED_PICKUP`, `IN_TRANSIT`, `ARRIVED_DELIVERY`, `UNLOADING_STARTED`, `DELIVERED`, `POD_RECEIVED`, `INVOICED`, `DELAY_REPORTED`, `EXCEPTION_RAISED`.
  * `previous_status`, `new_status`.
  * `occurred_at` (TIMESTAMPTZ), `latitude` (DOUBLE), `longitude` (DOUBLE).
  * `source` (VARCHAR): `DRIVER_APP`, `DISPATCHER`, `GPS`, `GEOFENCE`, `SYSTEM`, `ELD`.
  * `actor_id` (UUID nullable), `actor_name` (VARCHAR), `note` (TEXT), `document_id` (UUID nullable).
* Bảng mới `load_tracking_tokens`:
  * `id` (UUID PK), `load_id` (UUID FK), `token` (VARCHAR unique), `expires_at` (TIMESTAMPTZ), `is_active` (BOOLEAN).

#### 2. Lớp Backend (Java / Spring Boot)
* **Model**: Entity `LoadEvent`, `LoadTrackingToken`.
* **State Machine Service**: `LoadWorkflowService`
  * Kiểm soát chuyển đổi trạng thái hợp lệ. Ví dụ: Không thể chuyển sang `IN_TRANSIT` nếu chưa qua `PICKED_UP`.
  * Khi trạng thái thay đổi: Tự động ghi 1 dòng vào `load_events` và tính toán lại `progress_percentage`.
* **Timeline Generator**: Xây dựng DTO `LoadTimelineResponse` gồm các mốc thời gian hoàn thành (Completed Milestones) và các mốc dự kiến kế tiếp (Upcoming Milestones).
* **Sanitized Public Tracking API**: Trả về dữ liệu an toàn cho khách hàng, ẩn thông tin nhạy cảm của tài xế và cước phí nội bộ.

#### 3. Danh sách Endpoints
* `GET /api/loads/{id}/timeline` — Lấy toàn bộ timeline và milestone sự kiện.
* `GET /api/loads/{id}/events` — Lấy danh sách raw events có phân trang và lọc theo nguồn phát sinh.
* `POST /api/loads/{id}/events` — Ghi nhận sự kiện thủ công từ Dispatcher.
* `POST /api/loads/{id}/exceptions` — Ghi nhận ngoại lệ/sự cố (delay, hỏng hóc, tắc đường).
* `POST /api/loads/{id}/tracking-links` — Sinh đường dẫn tra cứu công khai cho khách hàng.
* `GET /api/public/tracking/{token}` — Endpoint công khai không cần đăng nhập cho khách hàng theo dõi.

---

### GIAI ĐOẠN 2: Dispatch Board & Smart Candidate Matcher
> **Mục tiêu**: Xây dựng Bàn Điều phối Tập trung (Dispatch Control Board) cho phép điều phối viên ghép đơn hàng, tài xế và đầu kéo tối ưu.

#### 1. Cơ sở dữ liệu (Flyway Migration `V6__dispatch_assignments.sql`)
* Bảng `trips` bổ sung:
  * `status`: `PLANNED`, `ASSIGNED`, `STARTED`, `AT_PICKUP`, `IN_TRANSIT`, `AT_DELIVERY`, `COMPLETED`, `CANCELLED`.
  * `total_deadhead_miles` (dặm chạy rỗng), `total_loaded_miles` (dặm chở hàng).
* Bảng `trip_stops` bổ sung:
  * `status`: `PENDING`, `EN_ROUTE`, `ARRIVED`, `SERVICE_STARTED`, `COMPLETED`, `SKIPPED`.
  * `stop_type`: `PICKUP`, `DELIVERY`, `REST_STOP`, `FUEL_STOP`.
  * `sequence_order` (INT), `estimated_arrival`, `actual_arrival`, `actual_departure`.

#### 2. Thuật toán Candidate Matcher (`DispatchCandidateService`)
* Khi điều phối viên chọn 1 Load: Hệ thống quét toàn bộ danh sách xe và tài xế trong bán kính cho phép.
* Tiêu chí chấm điểm (Scoring Matrix):
  1. **Availability**: Xe và tài xế đang rảnh (`status = ACTIVE`, không bị gán vào chuyến nào trùng giờ).
  2. **Deadhead Distance**: Khoảng cách từ vị trí GPS hiện tại của xe đến điểm Pickup của đơn hàng.
  3. **HOS Remaining Gate**: Số giờ lái xe còn lại trong ngày (dựa vào `DriverHosStatus`, đảm bảo > thời gian cần thiết để chạy đến đích).
  4. **Equipment Compatibility**: Loại xe (`Truck.type` như Dry Van, Reefer, Flatbed) có phù hợp với `Load.type` và cờ `isHazmat` không.

#### 3. Danh sách Endpoints
* `GET /api/dispatch/board` — Tổng quan 3 cột: Đơn chưa phân công, Tài xế sẵn sàng, Xe khả dụng.
* `GET /api/dispatch/unassigned-loads` — Danh sách đơn đang ở trạng thái `BOOKED`/`PLANNED`.
* `GET /api/dispatch/available-drivers` — Danh sách tài xế rảnh kèm giờ HOS còn lại.
* `GET /api/dispatch/available-trucks` — Danh sách đầu kéo rảnh kèm vị trí hiện tại.
* `GET /api/dispatch/load/{loadId}/candidates` — Gợi ý top tài xế & xe phù hợp nhất kèm khoảng cách deadhead.
* `POST /api/dispatch/assign` — Gán Load cho Driver + Truck + Container $\rightarrow$ Chuyển Load sang `DISPATCHED`.
* `POST /api/dispatch/reassign` — Đổi xe/tài xế khẩn cấp khi gặp sự cố.
* `POST /api/dispatch/unassign` — Hủy phân công, đưa đơn trở lại hàng đợi.

---

### GIAI ĐOẠN 3: Fleet Live Map & Telematics (GPS, Geofence, WebSocket)
> **Mục tiêu**: Giám sát vị trí toàn bộ đội xe trên bản đồ thời gian thực, tự động nhận diện xe ra/vào trạm kho qua Geofence.

#### 1. Cơ sở dữ liệu (Flyway Migration `V7__fleet_geofences_telematics.sql`)
* Bảng `geofences`:
  * `id` (UUID PK), `name` (VARCHAR), `code` (VARCHAR), `terminal_id` (UUID FK nullable), `customer_id` (UUID FK nullable).
  * `center_latitude` (DOUBLE), `center_longitude` (DOUBLE), `radius_meters` (DOUBLE).
  * `polygon_coordinates_json` (TEXT nullable), `type` (WAREHOUSE, TERMINAL, FUEL_STATION, REST_AREA).
* Bảng `geofence_events`:
  * `id` (UUID PK), `geofence_id` (UUID FK), `truck_id` (UUID FK), `driver_id` (UUID FK), `load_id` (UUID FK nullable).
  * `event_type`: `ENTER`, `EXIT`, `DWELL_EXCEEDED`.
  * `occurred_at` (TIMESTAMPTZ), `duration_minutes` (INT).
* Mở rộng bảng `trucks`:
  * `current_latitude`, `current_longitude`, `current_speed`, `current_heading`.
  * `odometer_reading`, `fuel_level_percentage`, `engine_state` (ON, OFF, IDLE).
  * `last_telematics_at` (TIMESTAMPTZ).

#### 2. Xử lý Geofence & WebSocket Realtime
* **Geofence Detector (`GeofenceService`)**:
  * Khi nhận tọa độ GPS từ xe: Tính khoảng cách theo công thức Haversine tới các Geofence lân cận.
  * Nếu xe đi vào Geofence của điểm Pickup $\rightarrow$ Tự động sinh sự kiện `ARRIVED_PICKUP`, chuyển Stop sang `ARRIVED`.
  * Nếu xe rời Geofence $\rightarrow$ Tự động sinh sự kiện `DEPARTED_PICKUP`, chuyển Load sang `IN_TRANSIT`.
* **WebSocket Channel (Spring STOMP)**:
  * `/topic/fleet/locations`: Bắn tọa độ cập nhật của toàn bộ đội xe cho màn hình Fleet Live Map.
  * `/topic/load/{loadId}/tracking`: Bắn vị trí riêng cho đơn hàng đang theo dõi.

#### 3. Danh sách Endpoints
* `GET /api/fleet/vehicles/live` — Danh sách trạng thái xe live (Driving, Idle, Stopped, Offline, Maintenance).
* `GET /api/fleet/vehicles/{truckId}/location` — Tọa độ tức thời và thông số động cơ.
* `GET /api/fleet/vehicles/{truckId}/history` — Lịch sử lộ trình di chuyển theo khung giờ.
* `GET /api/fleet/utilization` — Tỷ lệ khai thác xe (Fleet Utilization %).
* `GET /api/geofences` — Danh sách các vùng địa lý kho bãi.
* `POST /api/geofences` — Tạo Geofence mới (tâm bán kính hoặc đa giác polygon).
* `GET /api/geofences/{id}/events` — Lịch sử xe ra vào khu vực.

---

### GIAI ĐOẠN 4: Driver Portal & Mobile Execution
> **Mục tiêu**: Xây dựng giao diện và bộ API chuyên biệt cho Tài xế (Driver Context), hỗ trợ quy trình bốc/giao hàng và báo cáo thực địa.

#### 1. Bộ API "My Day" cho Tài xế
* Tài xế chỉ thao tác trên các chuyến đi được phân công cho chính họ (xác thực qua JWT Token của Driver).
* Các nút bấm hành động tuần tự:
  1. `[BẮT ĐẦU CHUYẾN]` $\rightarrow$ `POST /api/driver/trips/{id}/start`
  2. `[ĐÃ ĐẾN ĐIỂM BỐC]` $\rightarrow$ `POST /api/driver/stops/{id}/arrive`
  3. `[BỐC HÀNG XONG]` $\rightarrow$ `POST /api/driver/stops/{id}/complete` (gửi kèm ảnh kiểm hàng)
  4. `[ĐÃ ĐẾN ĐIỂM TRẢ]` $\rightarrow$ `POST /api/driver/stops/{id}/arrive`
  5. `[GIAO HÀNG & KÝ NHẬN]` $\rightarrow$ `POST /api/driver/stops/{id}/complete` (gửi kèm chữ ký và POD)

#### 2. Danh sách Endpoints
* `GET /api/driver/me` — Thông tin tài xế, ca làm việc, trạng thái HOS.
* `GET /api/driver/me/current-trip` — Chuyến đi đang thực hiện (lộ trình, điểm dừng kế tiếp, hàng hóa, yêu cầu đặc biệt).
* `GET /api/driver/me/assignments` — Danh sách các chuyến được giao trong tuần.
* `POST /api/driver/trips/{id}/start` — Bắt đầu khởi hành.
* `POST /api/driver/stops/{id}/arrive` — Xác nhận đã tới điểm dừng.
* `POST /api/driver/stops/{id}/complete` — Hoàn thành điểm dừng.
* `POST /api/driver/loads/{id}/condition-report` — Chụp ảnh báo cáo hàng lỗi/hư hỏng trước khi nhận.

---

### GIAI ĐOẠN 5: Quản lý Chứng từ (POD, BOL) & Phụ phí Accessorial Charges
> **Mục tiêu**: Số hóa chứng từ giao nhận hàng hóa (Paperless Freight Documents) và hạch toán minh bạch các khoản phụ phí phát sinh.

#### 1. Cơ sở dữ liệu (Flyway Migration `V8__documents_and_accessorials.sql`)
* Bảng `load_accessorial_charges`:
  * `id` (UUID PK), `load_id` (UUID FK).
  * `type` (VARCHAR): `DETENTION`, `LAYOVER`, `LUMPER`, `TONU` (Truck Order Not Used), `EXTRA_STOP`, `DRIVER_ASSIST`, `REDELIVERY`, `STORAGE`, `TOLL`, `HAZMAT_FEE`.
  * `customer_charge` (NUMERIC): Số tiền tính cho khách hàng.
  * `driver_pay` (NUMERIC): Số tiền trả thưởng cho tài xế.
  * `company_margin` (NUMERIC): Chênh lệch lợi nhuận của công ty (`customer_charge - driver_pay`).
  * `quantity` (DOUBLE), `unit_rate` (NUMERIC), `total_amount` (NUMERIC).
  * `status`: `PENDING_APPROVAL`, `APPROVED`, `REJECTED`, `INVOICED`.
  * `notes` (TEXT), `supporting_document_id` (UUID FK nullable).
* Mở rộng bảng `documents`:
  * `document_category`: `BOL`, `POD`, `RATE_CONFIRMATION`, `LUMPER_RECEIPT`, `SCALE_TICKET`, `FUEL_RECEIPT`, `TOLL_RECEIPT`.
  * `file_url`, `file_size`, `mime_type`, `signature_data_json`.

#### 2. Danh sách Endpoints
* `POST /api/loads/{id}/pod` — Upload biên bản giao nhận hàng (Proof of Delivery) kèm chữ ký số.
* `POST /api/loads/{id}/bol` — Upload vận đơn (Bill of Lading).
* `GET /api/loads/{id}/documents` — Danh sách chứng từ gắn với đơn hàng.
* `POST /api/loads/{id}/accessorials` — Khai báo phụ phí phát sinh (ví dụ xe chờ bốc hàng quá 2 tiếng $\rightarrow$ Detention).
* `PUT /api/loads/{id}/accessorials/{accessorialId}/approve` — Điều phối viên/Kế toán duyệt phụ phí để tính vào hóa đơn.

---

### GIAI ĐOẠN 6: Driver Settlement Engine (Quyết toán lương theo chuyến)
> **Mục tiêu**: Xây dựng bộ công cụ tính toán thu nhập tài xế theo từng chuyến đi, hỗ trợ đa dạng chính sách trả lương của ngành vận tải.

#### 1. Cơ sở dữ liệu (Flyway Migration `V9__driver_settlement.sql`)
* Bảng `driver_pay_policies`:
  * `id` (UUID PK), `driver_id` (UUID FK), `name` (VARCHAR).
  * `pay_type` (VARCHAR): `PER_MILE`, `PER_LOAD`, `PERCENT_REVENUE`, `HOURLY`, `FLAT_RATE`.
  * `loaded_mile_rate` (NUMERIC): Đơn giá dặm có hàng (ví dụ $0.65/mile).
  * `empty_mile_rate` (NUMERIC): Đơn giá dặm chạy rỗng (ví dụ $0.40/mile).
  * `revenue_percent` (NUMERIC): Phần trăm cước nhận được (ví dụ 25%).
  * `hourly_rate` (NUMERIC), `detention_hourly_rate` (NUMERIC), `layover_day_rate` (NUMERIC).
  * `effective_from` (DATE), `effective_to` (DATE).
* Bảng `driver_settlements`:
  * `id` (UUID PK), `driver_id` (UUID FK), `load_id` (UUID FK), `trip_id` (UUID FK nullable).
  * `settlement_number` (VARCHAR unique), `period_id` (UUID FK nullable).
  * `base_pay` (NUMERIC), `loaded_miles` (DOUBLE), `empty_miles` (DOUBLE).
  * `accessorial_pay` (NUMERIC): Tiền detention, layover, bốc xếp.
  * `reimbursements` (NUMERIC): Hoàn trả tiền tài xế ứng trước (vé cầu đường, cân xe, sửa chữa nhỏ).
  * `deductions` (NUMERIC): Các khoản khấu trừ (tạm ứng, vi phạm, bảo hiểm).
  * `net_settlement_pay` (NUMERIC): Số tiền thực nhận chuyến này.
  * `status`: `DRAFT`, `CALCULATED`, `IN_REVIEW`, `APPROVED`, `LOCKED`, `PAID`, `VOID`.
* Bảng `driver_settlement_lines`:
  * `id` (UUID PK), `settlement_id` (UUID FK), `line_type` (BASE, DETENTION, LAYOVER, TOLL_REIMBURSEMENT, ADVANCE_DEDUCTION).
  * `description` (TEXT), `quantity` (DOUBLE), `rate` (NUMERIC), `amount` (NUMERIC).

#### 2. Settlement Calculation Service (`DriverSettlementService`)
* Ngay khi đơn hàng hoàn thành (`DELIVERED` và có `POD`): Hệ thống tự động kích hoạt tính toán nháp (Draft Settlement).
* Công thức tính:
  $$\text{Net Pay} = (\text{Miles} \times \text{Rate}) + \text{Accessorials} + \text{Reimbursements} - \text{Deductions}$$

#### 3. Danh sách Endpoints
* `GET /api/driver-pay-policies` — Xem chính sách lương áp dụng.
* `POST /api/driver-pay-policies` — Thiết lập chính sách lương cho tài xế.
* `POST /api/driver-settlements/calculate/{loadId}` — Tính thử bảng kê lương cho 1 chuyến hàng.
* `GET /api/driver-settlements` — Danh sách các bảng quyết toán theo tài xế/thời gian.
* `GET /api/driver-settlements/{id}` — Chi tiết từng dòng tính lương của chuyến.
* `POST /api/driver-settlements/{id}/approve` — Quản lý duyệt số tiền chuyến.
* `GET /api/drivers/{driverId}/earnings/summary` — Tóm tắt thu nhập trong tuần/tháng của tài xế.

---

### GIAI ĐOẠN 7: Payroll Runs, Payslips & Immutability (Chốt kỳ lương)
> **Mục tiêu**: Tập hợp các settlement theo kỳ (tuần/tháng), xuất phiếu lương chính thức (Payslip) và khóa bất biến sổ sách lương.

#### 1. Cơ sở dữ liệu (Flyway Migration `V10__driver_payroll_runs.sql`)
* Bảng `pay_periods`:
  * `id` (UUID PK), `name` (VARCHAR) (ví dụ: `Week 38 - Sep 2026`).
  * `start_date` (DATE), `end_date` (DATE), `pay_date` (DATE).
  * `status`: `OPEN`, `PROCESSING`, `LOCKED`, `PAID`.
* Bảng `payroll_runs`:
  * `id` (UUID PK), `pay_period_id` (UUID FK), `run_number` (VARCHAR).
  * `total_drivers` (INT), `total_gross_pay` (NUMERIC), `total_deductions` (NUMERIC), `total_net_pay` (NUMERIC).
  * `executed_at` (TIMESTAMPTZ), `executed_by` (UUID FK).
* Bảng `payslips`:
  * `id` (UUID PK), `payroll_run_id` (UUID FK), `driver_id` (UUID FK).
  * `gross_pay` (NUMERIC), `tax_deductions` (NUMERIC), `other_deductions` (NUMERIC), `reimbursements` (NUMERIC), `net_pay` (NUMERIC).
  * `status`: `DRAFT`, `APPROVED`, `LOCKED`, `PAID`.
  * `payslip_pdf_url` (TEXT).
* Bảng `payroll_adjustments` (Dành cho việc điều chỉnh sau khi đã khóa sổ):
  * `id` (UUID PK), `payslip_id` (UUID FK), `amount` (NUMERIC), `reason` (TEXT), `approved_by` (UUID FK).

#### 2. Quy tắc Bất biến (Immutability Gate)
* Sau khi Payroll Run chuyển sang trạng thái `LOCKED`:
  * Toàn bộ các bảng settlement liên quan được đóng băng (`settlement.status = LOCKED`).
  * Mọi API sửa/xóa bảng lương cũ đều bị từ chối (`400 BAD_REQUEST - Payroll is LOCKED`).
  * Mọi sai lệch chỉ được xử lý bằng cách tạo một bản ghi `PayrollAdjustment` ở kỳ tiếp theo.

#### 3. Danh sách Endpoints
* `GET /api/pay-periods` — Danh sách các kỳ tính lương.
* `POST /api/pay-periods` — Mở kỳ tính lương mới.
* `POST /api/payroll/runs/execute` — Chạy batch gom toàn bộ settlement được duyệt vào Payroll Run.
* `GET /api/payslips` — Danh sách phiếu lương theo kỳ.
* `GET /api/payslips/{id}` — Xem chi tiết bảng phân rã phiếu lương.
* `POST /api/payslips/{id}/lock` — Khóa phiếu lương và chốt sổ.
* `GET /api/payslips/{id}/pdf` — Xuất phiếu lương dạng PDF gửi tài xế.

---

### GIAI ĐOẠN 8: Quản lý Bảo dưỡng phương tiện (DVIR $\rightarrow$ Work Order) & Chi phí Nhiên liệu
> **Mục tiêu**: Tự động hóa chu trình bảo trì xe từ biên bản lỗi của tài xế và quản lý định mức tiêu hao nhiên liệu (MPG).

#### 1. Quy trình Chuyển giao DVIR $\rightarrow$ Lệnh sửa chữa (Work Order)
```text
Biên bản DVIR của tài xế phát hiện: "Hỏng phanh / Brake Defect"
                  ↓
Hệ thống tự động sinh `MaintenanceIssue`
                  ↓
Tạo lệnh sửa chữa `WorkOrder`
                  ↓
Đổi trạng thái Truck: `status = MAINTENANCE` (Khóa xe khỏi Dispatch Board)
                  ↓
Gara sửa xong & nghiệm thu phụ tùng (`MaintenancePart`)
                  ↓
Đóng WorkOrder: Đổi trạng thái Truck: `status = ACTIVE` (Mở lại cho Dispatch)
```

#### 2. Quản lý Nhiên liệu (Fuel Management)
* Ghi nhận từng giao dịch bơm dầu: Số gallon, đơn giá, tổng tiền, số công tơ mét (odometer), vị trí cây xăng, ảnh hóa đơn.
* Tính toán chỉ số:
  * **MPG (Miles Per Gallon)** = Số dặm đã chạy / Số gallon dầu.
  * **Fuel Cost / Mile** = Chi phí nhiên liệu trung bình trên mỗi dặm.

#### 3. Danh sách Endpoints
* `GET /api/maintenance/dashboard` — Tình trạng bảo trì đội xe, số xe đang nằm xưởng.
* `GET /api/maintenance/work-orders` — Danh sách các lệnh sửa chữa đang tiến hành.
* `POST /api/maintenance/work-orders` — Tạo lệnh sửa chữa mới cho xe.
* `PUT /api/maintenance/work-orders/{id}/complete` — Nghiệm thu sửa chữa và giải phóng xe.
* `POST /api/fuel/transactions` — Nhập hóa đơn đổ xăng/dầu.
* `GET /api/reports/fuel-efficiency` — Báo cáo hiệu suất tiêu hao nhiên liệu theo xe/tài xế.

---

### GIAI ĐOẠN 9: Động cơ Lợi nhuận (Profitability) & Tuổi nợ Khách hàng (AR Aging)
> **Mục tiêu**: Hạch toán lãi/lỗ chi tiết từng chuyến hàng và kiểm soát rủi ro dòng tiền từ khách hàng.

#### 1. Động cơ tính Lãi gộp chuyến hàng (P&L per Load)
* Doanh thu (Revenue): Tiền cước chính + Phụ phí khách trả (`accessorial_charges`).
* Chi phí trực tiếp (Direct Cost):
  * Lương tài xế chuyến này (`driver_settlement.net_pay`).
  * Tiền nhiên liệu tiêu thụ cho cung đường.
  * Phí cầu đường, vé phà, bến bãi.
  * Khấu hao bảo dưỡng dự kiến (`maintenance allocation per mile`).
* Lợi nhuận gộp (Gross Profit) = $\text{Revenue} - \text{Direct Costs}$.
* Biên lợi nhuận (Gross Margin %) = $(\text{Gross Profit} / \text{Revenue}) \times 100\%$.

#### 2. Phân tích Tuổi nợ (Accounts Receivable Aging)
* Phân loại toàn bộ hóa đơn chưa thanh toán theo các nhóm thời gian quá hạn (Aging Buckets):
  * `CURRENT`: Chưa đến hạn thanh toán.
  * `1 - 30 DAYS`: Quá hạn từ 1 đến 30 ngày.
  * `31 - 60 DAYS`: Quá hạn 31 đến 60 ngày.
  * `61 - 90 DAYS`: Quá hạn 61 đến 90 ngày.
  * `90+ DAYS`: Nợ xấu, cần cảnh báo khóa tạo đơn mới.

#### 3. Danh sách Endpoints
* `GET /api/reports/profitability/by-load` — Bảng phân tích lãi lỗ từng đơn hàng.
* `GET /api/reports/profitability/by-lane` — Tuyến đường nào mang lại lợi nhuận cao nhất (ví dụ: Chicago $\rightarrow$ Dallas).
* `GET /api/reports/profitability/by-truck` — Hiệu quả sinh lời của từng đầu kéo.
* `GET /api/reports/profitability/by-customer` — Xếp hạng khách hàng theo giá trị mang lại.
* `GET /api/reports/receivables/aging` — Báo cáo tuổi nợ tổng thể và chi tiết theo khách hàng.
* `GET /api/customers/{id}/statement` — Bản sao kê công nợ gửi khách hàng đối soát.

---

### GIAI ĐOẠN 10: Company Operations Center & Tự động hóa qua Lark
> **Mục tiêu**: Xây dựng Trung tâm Điều hành Doanh nghiệp (Operations Center) gom toàn bộ chỉ số KPI sống, đồng thời kết nối chặt chẽ với nền tảng Lark (Bot Alerts + Lark Base Bitable).

#### 1. Company Operations Center (Single Pane of Glass)
* Gom toàn bộ dữ liệu thời gian thực hiển thị trên 1 màn hình lớn:
  * **Today Operations**: Số đơn đang chạy, số đơn giao hôm nay, tỷ lệ đúng hẹn DIFOT.
  * **Fleet Status**: Số xe đang lái, số xe đang đỗ, số xe bảo dưỡng.
  * **Financial Live**: Doanh thu trong ngày, ước tính chi phí, lợi nhuận gộp tức thời.
  * **Cảnh báo khẩn cấp (Live Alerts)**: Đơn hàng bị trễ > 30 phút, xe gặp sự cố động cơ, tài xế sắp hết giờ lái HOS.

#### 2. Tự động hóa thông minh với Lark
* **Lark Bot Notifications**:
  * Khi Dispatcher gán đơn: Bot Lark gửi thẻ tin nhắn tương tác (Interactive Card) cho tài xế kèm nút *"Chấp nhận"* hoặc *"Từ chối"*.
  * Khi giao hàng xong (POD uploaded): Bot tự động thông báo vào nhóm chat điều hành: *"Đơn hàng #1001 đã giao thành công tại Dallas"*.
  * Cảnh báo tự động: Khi xe đi vào/ra Geofence hoặc phát sinh ngoại lệ trên đường.
* **Lark Base (Bitable) Real-time Sync**:
  * Đồng bộ tự động các bảng: Bảng `Loads`, Bảng `Drivers`, Bảng `Invoices` để lãnh đạo theo dõi trực tiếp trên app Lark điện thoại mà không cần vào web TMS.

#### 3. Danh sách Endpoints
* `GET /api/company/operations/live` — Số liệu thống kê thời gian thực của trung tâm điều hành.
* `GET /api/reports/executive-summary` — Báo cáo tóm tắt dành cho ban giám đốc.
* `GET /api/reports/operations/on-time-delivery` — Báo cáo chỉ số DIFOT (Delivery In-Full, On-Time).
* `POST /api/lark/bot/broadcast-alert` — Bắn tin nhắn cảnh báo vận hành vào group chat Lark.

---

## III. Ma trận Phân quyền Vai trò (RBAC Security Matrix)

| Module / Nhóm chức năng | SuperAdmin | Owner | Manager | Dispatcher | Driver | Customer User |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| **Load CRUD & Báo giá** | ✅ | ✅ | ✅ | ✅ | Chỉ xem đơn gán | Chỉ xem đơn của mình |
| **Dispatch Board & Phân công** | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| **Bốc/Giao hàng & Upload POD** | ✅ | ✅ | ✅ | ✅ | ✅ (Chính mình) | ❌ |
| **Tra cứu Tracking công khai** | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ (Qua token) |
| **Fleet Live Map & Geofence** | ✅ | ✅ | ✅ | ✅ | ❌ | ❌ |
| **Duyệt Accessorials (Detention...)** | ✅ | ✅ | ✅ | ❌ | ❌ (Chỉ gửi) | ❌ |
| **Duyệt Settlement Lương chuyến** | ✅ | ✅ | ✅ | ❌ | Chỉ xem của mình | ❌ |
| **Chạy Payroll & Khóa kỳ lương** | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| **Quản lý Bảo dưỡng xe (Work Order)**| ✅ | ✅ | ✅ | ❌ | Chỉ nộp DVIR | ❌ |
| **Báo cáo Doanh thu & Lãi lỗ (P&L)** | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |

---

## IV. Trình tự Triển khai Khuyến nghị (Sprint Planning)

```text
SPRINT 1 (Nền tảng Thực thi)
  ├── 1. Tạo bảng `load_events` và mở rộng `loads`, `trips`, `trip_stops`
  ├── 2. Triển khai `LoadWorkflowService` (State Machine từ BOOKED -> DELIVERED)
  └── 3. API Timeline, Event History và Public Tracking Link

SPRINT 2 (Điều phối & Thực địa)
  ├── 1. Dispatch Board APIs: Unassigned Loads, Driver/Truck Availability
  ├── 2. Thuật toán Candidate Matcher (khoảng cách + HOS hours)
  └── 3. Driver Portal Mobile APIs: Start Trip, Arrive/Complete Stops

SPRINT 3 (Chứng từ & Phụ phí)
  ├── 1. Upload & Xác thực chứng từ giao nhận (POD/BOL kèm chữ ký)
  ├── 2. Hạch toán Accessorial Charges (Detention, Layover, Lumper)
  └── 3. Tích hợp tự động sync POD sang Lark Base

SPRINT 4 (Quyết toán Tài xế)
  ├── 1. Thiết lập chính sách lương tài xế `driver_pay_policies`
  ├── 2. Động cơ tính toán lương chuyến `DriverSettlementService`
  └── 3. API xem tóm tắt thu nhập tài xế

SPRINT 5 (Kỳ lương & Bất biến)
  ├── 1. Quản lý kỳ lương `pay_periods` & chạy batch `payroll_runs`
  ├── 2. Cơ chế khóa sổ bất biến (`LOCKED`) và `PayrollAdjustment`
  └── 3. Xuất phiếu lương Payslip

SPRINT 6 (Bảo trì & Nhiên liệu)
  ├── 1. Pipeline tự động: DVIR Defect -> Maintenance Work Order
  └── 2. Quản lý giao dịch nhiên liệu và tính toán chỉ số MPG

SPRINT 7 (Báo cáo & Trung tâm Điều hành)
  ├── 1. Báo cáo lãi lỗ theo chuyến / tuyến đường / xe
  ├── 2. Báo cáo tuổi nợ khách hàng (AR Aging)
  └── 3. Màn hình Company Operations Live Dashboard
```

---

## V. Tiêu chuẩn Kỹ thuật & Cam kết Chất lượng
1. **Kiến trúc mã nguồn**: Tuân thủ nghiêm ngặt quy tắc `Controller -> Service -> Repository -> Entity`, không gọi chéo repository giữa các module.
2. **Migration an toàn**: Toàn bộ thay đổi cơ sở dữ liệu được đánh số tuần tự qua Flyway trong `db/migration/tenant`, không bao giờ dùng `ddl-auto: update`.
3. **Hiệu năng & Đồng thời**:
   - Sử dụng Redis Cache cho các dữ liệu ít biến động (Terminal, Pay Policy).
   - Khóa lạc quan (Optimistic Locking `@Version`) trên các thực thể nhạy cảm như `Load`, `Trip` và `DriverSettlement` để ngăn xung đột điều phối đồng thời.
4. **Kiểm thử tự động**: Mỗi Phase phải đi kèm đầy đủ Unit Test (MockMvc/Mockito) và Integration Test với coverage nghiệp vụ tối thiểu 85%.
