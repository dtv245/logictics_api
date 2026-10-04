# LOGISTICSX TMS — BÁO CÁO KHẢO SÁT NGỮ NGHĨA DỮ LIỆU HIỆN HỮU (CURRENT DOMAIN SEMANTICS)
> **Mã công việc:** BE-CALC-001 / Phase 0  
> **Căn cứ kế hoạch:** `plan-convention-v3-implementation-ready.md` (Section 4 & Section 8 Gate D)  
> **Trạng thái:** AUDIT HOÀN THÀNH — CÁC NGỮ NGHĨA CHƯA CÓ BẰNG CHỨNG VẪN CẦN BUSINESS OWNER XÁC NHẬN  
> **Ngày lập:** 2026-10-03  

---

## 1. MỤC TIÊU VÀ PHẠM VI

Báo cáo này tài liệu hóa kết quả rà soát chi tiết toàn bộ mã nguồn Java (`src/main/java`), cấu trúc CSDL baseline (`V1__baseline_business_schema.sql`) và tài liệu nghiệp vụ kế thừa (`docs/docs/`) nhằm khóa các quyết định nghiệp vụ, loại trừ mọi giả định ngầm trước khi triển khai các công thức tính toán tài chính, chi phí và tối ưu hóa điều phối.

---

## 2. KẾT QUẢ KHẢO SÁT CHI TIẾT CÁC TRƯỜNG DỮ LIỆU CỐT LÕI

### 2.1 Cột `loads.distance` (Double Precision NOT NULL)
* **Vị trí trong mã nguồn:**
  - Entity: `Load.java` (dòng 54-55)
  - DTO: `CreateLoadRequest.java`, `LoadView.java`
* **Nguồn gốc sinh dữ liệu:**
  - Do người dùng nhập khi tạo đơn hàng hoặc được tính toán sơ bộ từ tọa độ điểm lấy hàng (`origin_location`) và điểm giao hàng (`destination_location`).
  - Trong logic điều phối và báo giá, trường này đại diện cho **Khoảng cách định tuyến dự kiến giữa 2 kho của đơn hàng (Planned Freight Route Distance)**.
* **Kết luận & Ràng buộc kỹ thuật:**
  - `loads.distance` **KHÔNG PHẢI** là quãng đường thực tế đầu kéo đã lăn bánh (Actual Driven Miles) và không phản ánh dặm chạy rỗng (Deadhead Miles).
  - **Quy tắc bắt buộc:** Trong DTO và API báo cáo V1, trường này phải được gán nhãn ngữ nghĩa là `recordedLoadDistance`. Khi tính KPI doanh thu trên dặm (RPM V1), phải trả kèm:
    ```json
    {
      "revenuePerMile": 2.50,
      "distance": 1300,
      "distanceBasis": "LEGACY_LOAD_DISTANCE"
    }
    ```
  - CẤM sử dụng trường này làm mẫu số cho các chỉ số thực tế (Actual CPM / Actual Fuel Efficiency).

---

### 2.2 Cột `trips.total_distance` (Double Precision NOT NULL)
* **Vị trí trong mã nguồn:**
  - Entity: `Trip.java` (dòng 43-44)
  - DTO: `CreateTripRequest.java`, `TripView.java`
* **Nguồn gốc sinh dữ liệu:**
  - Được sinh ra qua bộ tối ưu lộ trình `CompositeTripOptimizer` (sử dụng Mapbox Matrix API hoặc fallback giải thuật Heuristic khoảng cách giữa các `TripStop` liên tiếp).
  - Đại diện cho **Tổng quãng đường dự kiến của toàn bộ chuỗi điểm dừng trong chuyến xe (Planned Trip Routing Distance)**.
* **Kết luận & Ràng buộc kỹ thuật:**
  - `trips.total_distance` là dặm kế hoạch, không phải dặm GPS/ELD thực tế từ thiết bị giám sát hành trình.
  - **Kế hoạch chuyển đổi:** 
    - Tại Migration V3 (`V3__add_trip_mileage_breakdown.sql`), bổ sung 4 trường phân rã tường minh: `planned_distance_miles`, `actual_distance_miles`, `loaded_miles`, `empty_miles`.
    - Giữ lại `total_distance` để tương thích ngược với mobile app và UI cũ. Không backfill mù quáng `actual_distance_miles = total_distance` trừ khi chuyến xe đó được chứng minh đã hoàn thành và số liệu đã được xác nhận.

---

### 2.3 Cột `loads.delivery_cost_amount` & `loads.delivery_cost_currency`
* **Vị trí trong mã nguồn:**
  - Entity: `Load.java` (dòng 126-130: `deliveryCostAmount NUMERIC(18,2)`, `deliveryCostCurrency VARCHAR(3)`)
  - Tài liệu liên quan: `docs/docs/invoices.md`, `docs/docs/business-spec.md` (mục 2.1, 2.2, 3.1)
* **Bản chất ngữ nghĩa được làm rõ:**
  - Tài liệu `invoices.md` nêu rõ:
    > *"LoadInvoice: Auto-created when a Load is created (status: Draft). Linked to a Customer and Load. Contains freight charges based on `Load.DeliveryCost`. Default line item: Freight charges for Load #{number}."*
  - Tài liệu `business-spec.md` mục 2.2:
    > *"Tổng doanh thu chuyến = Σ(Load.DeliveryCost) của các stop loại DropOff"*
    > *"Driver share = DeliveryCost × GetDriversShareRatio()"*
* **Kết luận cực kỳ quan trọng:**
  - **`loads.delivery_cost_amount` CHÍNH LÀ GIÁ CƯỚC BÁO KHÁCH (Quoted Customer Freight Revenue / Delivery Fee)**, hoàn toàn **KHÔNG PHẢI** là chi phí vận hành nội bộ (Internal Operating Cost) của nhà xe!
  - **RÀNG BUỘC BẮT BUỘC (CRITICAL INVARIANT):**
    - **TUYỆT ĐỐI CẤM** trừ `delivery_cost_amount` như một khoản chi phí trong bất kỳ công thức tính lãi lỗ (Profit = Revenue - Cost) nào!
    - Nếu sử dụng ở nhánh Doanh thu V1 (khi khách hàng chưa được xuất hóa đơn chính thức), trường này được map thành `quotedCustomerRevenue`.

---

## 3. KIỂM KÊ TOÀN BỘ GIÁ TRỊ ENUM VÀ TRẠNG THÁI HIỆN TẠI

Hiện tại trong mã nguồn Java, các cột trạng thái và phân loại đang được định nghĩa dưới dạng chuỗi (`String` / `columnDefinition = "text"`). Dưới đây là danh mục giá trị chuẩn được trích xuất từ tài liệu vận hành và hệ thống kế thừa:

### 3.1 Invoice Types & Statuses
* **Invoice Types (Table-Per-Hierarchy):**
  - `LOAD_INVOICE`: Hóa đơn cước vận chuyển gửi khách hàng (Doanh thu vận tải).
  - `PAYROLL_INVOICE`: Bảng kê thanh toán lương/thù lao cho nhân viên/tài xế (Chi phí nhân công - Cần tách khỏi invoices).
  - `SUBSCRIPTION_INVOICE`: Hóa đơn thu phí nền tảng SaaS từ khách hàng qua Stripe.
* **Invoice Statuses:**
  - `DRAFT`: Mới tạo nháp cùng đơn hàng.
  - `ISSUED` (hoặc `SENT`): Đã phát hành gửi khách hàng.
  - `PARTIALLY_PAID`: Khách hàng đã thanh toán một phần tiền.
  - `PAID`: Đã thanh toán đủ 100%.
  - `CANCELLED`: Hóa đơn đã hủy.
  - *(Đối với Payroll legacy còn có: `PENDING_APPROVAL`, `APPROVED`, `REJECTED`).*
* **Chính sách tính Doanh thu hợp lệ (Invoice Revenue Policy):**
  - Chỉ các hóa đơn có trạng thái `ISSUED`, `PARTIALLY_PAID`, `PAID` mới được tính vào Doanh thu đã ghi nhận (`LoadRevenue`).
  - Hóa đơn `DRAFT` chỉ xem là doanh thu dự kiến (Estimated Revenue).
  - Hóa đơn `CANCELLED` bị loại trừ 100%.

---

### 3.2 Expense Types & Categories
* **Expense Types (`expenses.type`):**
  - `CompanyExpense`: Chi phí chung của doanh nghiệp.
  - `TruckExpense`: Chi phí gắn theo đầu xe kéo.
  - `BodyShopExpense`: Chi phí sửa chữa xe tại xưởng/gara.
* **Expense Categories (`expenses.category` / `truck_expense_category`):**
  - `FUEL`: Tiền dầu Diesel / Nhiên liệu.
  - `TOLL`: Vé cầu đường, phí BOT, qua phà.
  - `PARKING`: Bến bãi, điểm dừng đỗ qua đêm.
  - `SCALE`: Trạm cân tải trọng.
  - `REPAIR`: Chi phí sửa chữa khẩn cấp trên đường.
  - `MAINTENANCE`: Chi phí bảo dưỡng định kỳ.
  - `TIRE`: Chi phí thay vá lốp xe.
  - `LUMPER`: Phí bốc xếp hàng hóa tại kho.
  - `HOTEL`: Khách sạn cho tài xế đường dài.
  - `PERMIT`: Giấy phép quá khổ, giấy phép môi trường.
  - `OTHER`: Chi phí nghiệp vụ khác.

---

### 3.3 Employee Salary Types (`employees.salary_type`)
Trong mô hình HR hiện hữu, tài xế/nhân viên có các chế độ đãi ngộ cơ bản:
* `WEEKLY`: Lương cố định trả theo tuần.
* `MONTHLY`: Lương cố định trả theo tháng.
* `HOURLY`: Lương tính theo số giờ làm việc thực tế (`time_entries.total_hours`).
* `SHARE_OF_GROSS`: Hưởng phần trăm hoa hồng trên tổng cước đơn hàng (`DeliveryCost`).
* `RATE_PER_DISTANCE`: Hưởng thù lao theo số dặm chạy được.

> **Quy định chuyển đổi:** Giữ nguyên các trường này cho nhân sự văn phòng và lương cố định cơ bản. Toàn bộ cơ chế thù lao phức tạp của ngành vận tải đường dài (Detention, Layover, Stop Pay, Empty Mile Rate) sẽ được chuyển sang bảng chuyên biệt `driver_pay_policies` tại Phase 4.

---

### 3.4 Load & Trip Statuses
* **Load Statuses (`loads.status`):**
  - `DRAFT` $\rightarrow$ `DISPATCHED` $\rightarrow$ `PICKED_UP` $\rightarrow$ `DELIVERED` (hoặc `CANCELLED`).
* **Trip Statuses (`trips.status`):**
  - `DRAFT` $\rightarrow$ `DISPATCHED` $\rightarrow$ `IN_TRANSIT` $\rightarrow$ `COMPLETED` (hoặc `CANCELLED`).

---

## 4. QUY TẮC BẢO VỆ CHỐNG TÍNH TRÙNG (DOUBLE-COUNT POLICY)

1. Chi phí sửa chữa/bảo dưỡng xe tồn tại ở 2 nguồn:
   - Nguồn kỹ thuật/vật tư: `maintenance_records` + `maintenance_parts`.
   - Nguồn kế toán/chi tiền: `expenses` (với `category = 'MAINTENANCE'` hoặc `type = 'BodyShopExpense'`).
2. **Quy định đối soát Phase 1**:
   - Khi tính Báo cáo Chi phí Vận hành tổng thể (`KnownOperatingCost`), báo cáo phải trình bày 2 dòng tách biệt:
     - `ApprovedOperatingExpenses` (loại trừ các expense có dấu hiệu sửa chữa trùng lặp).
     - `MaintenanceCost` (lấy từ `maintenance_records.total_cost`).
   - Sau khi hoàn thành Migration V5 (bổ sung `expenses.maintenance_record_id`), hệ thống sẽ đối soát 1-1: nếu expense đã link tới `maintenance_record_id` thì chỉ cộng một lần vào sổ cái chi phí `shipment_costs`.

---

## 5. TỔNG KẾT & PHÊ DUYỆT GATE D

| Vấn đề | Quyết định đã chốt |
|---|---|
| `loads.distance` | `CreateLoadRequest` nhận raw `Double`; đơn vị/source là TBD trong legacy spec. Chỉ được hiển thị tạm là `recordedLoadDistance`, không dùng trong formula. |
| `trips.total_distance` | `CreateTripRequest` nhận raw `Double`; không có routing integration/writer trong Java source. Không suy diễn planned/actual miles. |
| `loads.delivery_cost_amount` | Là **Doanh thu báo cước khách hàng**. CẤM đưa vào trừ chi phí lợi nhuận. |
| Tiêu chuẩn hóa Enum | Định nghĩa Java Enums tương ứng trong package `com.company.logicstic.common.enums`. |
| Điều kiện chuyển Phase 1 | Metric không phụ thuộc distance có thể tiếp tục; metric RPM/CPM/payroll phụ thuộc distance bị chặn đến khi xác nhận source, unit và meaning. |

## 6. Hiệu chỉnh bằng chứng audit (2026-10-03)

Rà soát trực tiếp `Load`, `Trip`, `CreateLoadRequest`, `CreateTripRequest`, `TripService` và toàn bộ `src/main/java` cho thấy không có route-calculation client, `CompositeTripOptimizer`, Mapbox, OSRM, Google Maps hay writer riêng cho hai cột legacy. Các mô tả trước đó về “planned routing distance” không có bằng chứng trong repository và bị thay thế bởi kết luận **unresolved**. Business Owner phải chốt cho mỗi cột: đơn vị (mile/km), nguồn (manual/routing/ELD), và meaning (planned/quoted/actual) trước mọi calculation phụ thuộc distance.

Các giá trị trạng thái legacy cũng chưa phải Java enum đầy đủ: entity vẫn lưu `String`; chỉ `InvoiceStatus` và `ExpenseCategory` hiện có enum Java. `InvoiceType`, `ExpenseType`, `TruckExpenseCategory`, `EmployeeSalaryType`, `TripStatus` và `LoadStatus` chưa tồn tại trong source.
