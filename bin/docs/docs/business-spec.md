# Đặc tả nghiệp vụ — LogisticsX

**Phiên bản:** 1.0  
**Ngày lập:** 2026-07-11  
**Phạm vi:** Toàn bộ nền tảng quản lý vận tải đa tenant LogisticsX

---

## 1. Tổng quan sản phẩm

LogisticsX là nền tảng quản lý vận tải (TMS) đa tenant dành cho các công ty trucking/fleet. Hệ thống hỗ trợ vận chuyển container intermodal, vận tải xe cộ và hàng hóa (freight).

### 1.1 Các thành phần hệ thống

| Thành phần | Mô tả | Cổng |
|---|---|---|
| REST API | Backend chính, xử lý toàn bộ nghiệp vụ | 7000 |
| Identity Server | OAuth2/OIDC, quản lý xác thực người dùng | 7001 |
| Admin Portal | Giao diện quản trị super admin | 7002 |
| TMS Portal | Giao diện vận hành cho dispatcher, manager | 7003 |
| Customer Portal | Giao diện khách hàng xem shipment, hóa đơn | 7004 |
| Website | Trang marketing | 7005 |
| Driver App | Ứng dụng Kotlin Multiplatform cho tài xế | Mobile |
| MCP Server | Endpoint AI cho external AI clients | /mcp |
| Telegram Bot | Thông báo và tương tác qua Telegram | - |

### 1.2 Vai trò người dùng

| Vai trò | Mô tả |
|---|---|
| SuperAdmin | Quản trị toàn hệ thống, quản lý tenant, plan, feature |
| Admin | Quản trị trong phạm vi Admin Portal |
| Owner | Chủ công ty trucking (tenant), toàn quyền trong tenant |
| Manager | Quản lý điều hành của tenant |
| Dispatcher | Điều phối load, trip, xe |
| Driver | Tài xế nhận load, cập nhật trạng thái, POD |
| Customer | Khách hàng xem shipment, invoice, thanh toán |

---

## 2. Quản lý nghiệp vụ vận hành (Operations)

### 2.1 Quản lý Load (Lô hàng)

**Mô tả:** Load đại diện cho một shipment/freight movement từ điểm xuất phát đến điểm đích.

**Dữ liệu chính:**
- Số hiệu (`Number`), tên, loại hàng (`Type`), trạng thái (`Status`)
- Địa chỉ & tọa độ gốc/đích
- Chi phí giao hàng (`DeliveryCost`), khoảng cách (`Distance`)
- Khách hàng, xe được phân công, dispatcher phụ trách
- Hóa đơn, tài liệu, ngoại lệ, trip stops
- Nguồn load: manual, import PDF, API, load board
- Ngày pickup/delivery yêu cầu
- Thông tin hàng nguy hiểm: `IsHazmat`, `HazmatClass`, `UnNumber`
- Container và terminal xuất/nhập (tuỳ chọn)

**State machine trạng thái Load:**

```
Draft → Dispatched → PickedUp → Delivered
  ↓          ↓           ↓
Cancelled  Cancelled  Cancelled
```

| Trạng thái | Chuyển tiếp cho phép |
|---|---|
| Draft | Dispatched, Cancelled |
| Dispatched | PickedUp, Cancelled |
| PickedUp | Delivered, Cancelled |
| Delivered | (kết thúc) |
| Cancelled | (kết thúc) |

**Quy tắc timestamp:**
- `Dispatched`: gán `DispatchedAt` nếu null; reset pickup/delivery/cancel timestamps
- `PickedUp`: gán `PickedUpAt` nếu null
- `Delivered`: gán `DeliveredAt` nếu null
- `Cancelled`: gán `CancelledAt` nếu null

**Xác nhận bởi tài xế:**
- Có thể xác nhận PickUp khi: `Status == Dispatched && IsInProximity`
- Có thể xác nhận Delivery khi: `Status == PickedUp && IsInProximity`

**Tác động đến Invoice:**
- Khi Load chuyển sang `Dispatched`, nếu Invoice đang `Draft` → Invoice chuyển sang `Issued`

**Tính phần tài xế (Driver Share):**
```
Driver share = DeliveryCost × GetDriversShareRatio()
Ratio = tổng Salary.Amount của main/secondary driver có SalaryType = ShareOfGross
```

---

### 2.2 Quản lý Trip (Chuyến đi)

**Mô tả:** Trip gồm nhiều TripStop (điểm pickup và drop-off của nhiều load), được phân công cho một xe.

**Dữ liệu chính:**
- Số hiệu, tên, trạng thái
- Tổng khoảng cách (`TotalDistance`)
- Xe (`Truck`), danh sách stop có thứ tự
- Timestamps: `DispatchedAt`, `CompletedAt`, `CancelledAt`

**Tính toán Trip:**
```
Tổng doanh thu = Σ(Load.DeliveryCost) của stop loại DropOff
Phần tài xế = Σ(Load.CalcDriverShare()) của stop loại DropOff
```

**Vòng đời Trip:**
- `Draft → Dispatched`: set `DispatchedAt`, phát `TripDispatchedEvent`
- `Cancel`: chỉ cho phép khi chưa completed; set trip Cancelled, clear stop arrival, cancel tất cả loads trong trip
- `MarkStopArrived`:
  - Pickup stop → load chuyển `PickedUp`
  - DropOff stop → load chuyển `Delivered`
  - Tất cả drop-off delivered → trip `Completed`
  - Còn pickup chưa delivered → trip `InTransit`

**Tối ưu hóa route:**
- Ưu tiên Mapbox Matrix API (`CompositeTripOptimizer`)
- Fallback: `HeuristicTripOptimizer` (nearest-neighbor, pickup trước drop-off)
- Tổng khoảng cách tính theo `GeoPoint.DistanceTo` giữa các stop liên tiếp

---

### 2.3 Quản lý Truck (Đội xe)

**Dữ liệu chính:**
- Số xe, loại, trạng thái
- Make/Model/Year/VIN/Biển số
- Tải trọng (cho car hauler)
- Địa chỉ/vị trí hiện tại
- Tài xế chính, tài xế phụ
- Thiết bị ADR và nhãn hazmat US
- Loads, trips, tài liệu

**Quy tắc:**
- Xe phải có tài xế chính khi tạo
- Một tài xế không được gán trùng sang nhiều xe cùng lúc
- Driver share ratio = tổng Salary của driver kiểu ShareOfGross (0 ≤ ratio ≤ 1)
- VIN decode qua NHTSA API

---

### 2.4 Container & Terminal (Intermodal)

**Container (ISO 6346):**
- Số ISO, loại, seal, booking reference, BOL
- Cờ laden/empty, tổng trọng lượng
- Terminal hiện tại
- Timestamps: loaded/delivered/returned

**State machine Container:**

| Trạng thái | Chuyển tiếp cho phép |
|---|---|
| Empty | Loaded, AtPort, Returned |
| AtPort | Loaded, InTransit, Empty |
| Loaded | InTransit, AtPort |
| InTransit | Delivered, AtPort |
| Delivered | Empty, Returned |
| Returned | Empty |

**Terminal:**
- Loại: Sea port, Rail terminal, Depot, Air cargo, Border crossing
- UN/LOCODE, mã quốc gia ISO 3166-1, địa chỉ

---

### 2.5 Tracking (Theo dõi)

**Internal (nội bộ):**
- Driver gửi vị trí qua SignalR hub
- Hiển thị trên bản đồ Mapbox

**Public tracking link:**
- Link chia sẻ cho khách hàng không cần đăng nhập
- Cho phép xem trạng thái shipment, tài liệu công khai
- Ghi nhận lượt truy cập, có thể thu hồi (revoke)

---

### 2.6 Time Entry (Chấm công)

**Dữ liệu:** Nhân viên, ngày, giờ bắt đầu/kết thúc, loại (regular/overtime/PTO).  
**Tính toán:** `TotalHours = (EndTime - StartTime).TotalHours`

---

### 2.7 Maintenance (Bảo dưỡng)

**MaintenanceSchedule:**
- Xe, loại bảo dưỡng, loại chu kỳ (mileage/days/engine hours)
- Thông số lần bảo dưỡng gần nhất và lần tiếp theo
- `IsOverdue` = NextDueDate < UtcNow
- `IsDueSoon` = NextDueDate ≤ UtcNow + 7 ngày

**MaintenanceRecord:**
- Xe, schedule tham chiếu (tuỳ chọn)
- Ngày service, odometer, giờ máy
- Nhà cung cấp, số hóa đơn
- Chi phí nhân công, phụ tùng, tổng chi phí
- Công việc thực hiện, phụ tùng sử dụng, tài liệu đính kèm

---

## 3. Nghiệp vụ Tài chính (Financial)

### 3.1 Invoice (Hóa đơn)

**Loại hóa đơn (Table-Per-Hierarchy):**
- `LoadInvoice`: hóa đơn vận chuyển hàng
- `PayrollInvoice`: thanh toán lương nhân viên
- `SubscriptionInvoice`: phí subscription nền tảng

**Trạng thái hóa đơn:**
- Draft → Issued/Sent → PartiallyPaid / Paid → Cancelled
- Payroll thêm: PendingApproval → Approved/Rejected

**Dòng mục (Line Item):**
- Mô tả, loại, số tiền, số lượng, thứ tự
- `Total = Amount × Quantity`
- TaxRatePercent, TaxAmount, TaxCode

**Tính tổng hóa đơn:**

| Loại thuế | Công thức |
|---|---|
| Exclusive | Subtotal = Σ(Amount×Qty); TaxTotal = Σ(TaxAmount); Total = Subtotal + TaxTotal |
| Inclusive | Net mỗi dòng = Total/(1+Tax%/100); Subtotal = Σ(net); Total = Σ(Total); TaxTotal = Total − Subtotal |
| ReverseCharge | TaxAmount = 0; Subtotal = Σ(Total); TaxTotal = 0; Total = Subtotal |

Tất cả làm tròn 2 chữ số thập phân.

**Áp dụng thanh toán:**
```
paid = Σ(Payments.Amount)
if paid >= Total → Status = Paid
else → Status = PartiallyPaid
```

---

### 3.2 Thuế (Tax)

**Provider:**
- Stripe Tax (production, cấu hình `Tax:Provider = stripe`)
- Manual fallback

**Thứ tự ưu tiên manual tax:**
1. TenantTaxRate khớp country/region khách hàng
2. EU VAT standard nếu tenant EU và khách hàng EU/EEA/UK
3. US state sales tax nếu tenant US và khách hàng US
4. Mặc định country VAT/GST khác
5. Zero rate nếu không khớp

**EU Reverse Charge áp dụng khi:**
- Tenant region EU
- Seller country là EU member
- Buyer country là EU member
- Seller ≠ Buyer country
- Khách hàng có VAT ID

---

### 3.3 Payment & Payment Link

**Payment:** Số tiền, trạng thái, tenant, địa chỉ thanh toán, Stripe payment intent.

**Payment Link:**
- Token ngẫu nhiên, expires at, cờ active
- Đếm lượt truy cập
- Hợp lệ khi: `IsActive && UtcNow < ExpiresAt`

**Public payment flow:**
1. Khách hàng mở `/pay/{tenantId}/{token}`
2. API xác thực tenant, token, expiration, revoked, invoice payable
3. Frontend hiển thị invoice details + Stripe Elements
4. Backend tạo checkout/payment intent qua Stripe Connect
5. Thanh toán thành công → cập nhật invoice status

**Thanh toán từng phần (Partial payment):**
```
amountDue = invoice.Total − Σ(paid)
paymentAmount = request.Amount ?? amountDue
0 < paymentAmount ≤ amountDue
```

---

### 3.4 Stripe Connect

- Mỗi tenant có Stripe Connected Account
- Customer payments → tài khoản trucking company qua destination charges
- Payroll payout → tài khoản Stripe của employee
- Hỗ trợ: SEPA/iDEAL/Bacs/ACH tuỳ theo quốc gia

---

### 3.5 Payroll (Lương)

**Loại lương:**

| Loại | Công thức |
|---|---|
| Weekly | CountWeeks(start, end) × Salary |
| Monthly | CountMonths(start, end) × Salary |
| ShareOfGross | Σ(DeliveryCost) × Salary (ratio 0–1) |
| RatePerDistance | Σ(Distance) × Salary (rate per unit) |
| Hourly | Σ(TimeEntry.TotalHours) × Salary |

**Workflow payroll:**
- Draft → SubmitForApproval → PendingApproval → Approved → Paid
- Hoặc: PendingApproval → Rejected

**Background jobs:**
- Monthly payroll cho `Monthly` và `ShareOfGross`
- Weekly payroll cho `Weekly`
- Skip nếu đã có payroll overlap trong period

---

### 3.6 Expenses (Chi phí)

**Loại chi phí:**
- `CompanyExpense` (chi phí công ty)
- `TruckExpense` (chi phí theo xe)
- `BodyShopExpense` (chi phí sửa chữa)

**Thống kê:** Tổng theo loại/category, theo tháng, top 10 xe theo chi phí.

---

## 4. AI Dispatch & MCP

### 4.1 AI Dispatch (Điều phối AI)

**Mô tả:** AI agent tự động chọn load-truck assignment dựa trên:
- Loads chưa được phân công
- Trucks sẵn sàng và vị trí hiện tại
- HOS status của tài xế
- Hazmat/License/ADR eligibility
- Revenue per mile và deadhead ratio
- Load board opportunities

**Hai chế độ hoạt động:**

| Chế độ | Mô tả |
|---|---|
| Human-in-the-loop | AI tạo suggestion, dispatcher approve/reject/replan |
| Autonomous | AI execute quyết định ngay |

**Kiểm tra đủ điều kiện điều phối:**

Hard blocks (chặn cứng):
- Xe không tồn tại / Load không tồn tại
- Xe không có tài xế / Tài xế không tồn tại
- Không có license phù hợp / License hết hạn
- Medical certificate hết hạn
- Thiếu Hazmat endorsement (US: Hazmat; EU/non-US: ADR)
- Xe không có ADR cert / cert hết hạn

Warnings:
- Medical certificate sắp hết hạn trong 7 ngày

### 4.2 AI Tools

| Tool | Mô tả |
|---|---|
| `get_unassigned_loads` | Lấy danh sách loads chưa phân công |
| `get_available_trucks` | Lấy danh sách xe sẵn sàng |
| `get_driver_hos_status` | HOS status của tài xế |
| `check_hos_feasibility` | Kiểm tra khả thi HOS cho một load |
| `batch_check_hos_feasibility` | Kiểm tra hàng loạt |
| `calculate_distance` | Tính khoảng cách ước lượng |
| `calculate_assignment_metrics` | Tính revenue per mile, deadhead ratio |
| `optimize_trip_stops` | Tối ưu hóa thứ tự stop |
| `search_loadboard` | Tìm kiếm load board |
| `assign_load_to_truck` | Phân công load cho xe |
| `create_trip` | Tạo trip |
| `dispatch_trip` | Điều phối trip |
| `book_loadboard_load` | Đặt load từ load board |
| `preview_tax_calculation` | Xem trước tính thuế |

### 4.3 Quota AI

```
weeklyQuota = plan.WeeklyAiRequestQuota (null = unlimited)
usedThisWeek = Σ(session.RequestCost) trong tuần hiện tại
isOverQuota = usedThisWeek >= weeklyQuota
```

**Request cost multiplier theo model:**

| Model | Cost |
|---|---:|
| deepseek-v4-flash, deepseek-v4-pro, gpt-5.4-mini, claude-haiku-4-5 | 1 |
| gpt-5.4, claude-sonnet-4-6 | 5 |
| claude-opus-4-8 | 10 |

---

## 5. Compliance & Safety

### 5.1 ELD/HOS

**ELD Providers:** Samsara, Motive, Geotab, TT ELD, Demo

**HOS Limits (FMCSA):**
- Max driving/ngày: 11h; On-duty: 14h
- Max driving/tuần: 60h; Cycle: 70h
- Nghỉ: 10h/ngày, 34h restart; Break sau 8h lái

**HOS Limits (EU 561/2006):**
- Max driving/ngày: 9h; On-duty: 13h
- Max driving/tuần: 56h; Hai tuần: 90h
- Nghỉ: 11h/ngày, 45h/tuần; Break sau 4.5h liên tục

---

### 5.2 DVIR (Driver Vehicle Inspection Report)

- Báo cáo trước/sau chuyến
- Defect theo severity/category
- Chữ ký tài xế, review/resolve flow
- Đính kèm ảnh/tài liệu

---

### 5.3 Accident & Driver Behavior

**Accident report:** Tài xế, xe, thời gian/địa điểm, severity, bên thứ ba, chứng nhân, ảnh.  
**Driver behavior event:** Loại sự kiện, mức nghiêm trọng, thời gian/địa điểm, hành động khắc phục.

---

### 5.4 Privacy / GDPR

- Consent records
- Data export/deletion requests
- Retention/export expiry jobs
- Cookie banner và tenant privacy settings
- Admin data-requests page

---

## 6. Documents, Communication, Notifications

### 6.1 Documents

**Loại tài liệu:** POD, POL, BOL, Truck documents, Employee documents.  
**Lưu trữ:** Azure Blob, Cloudflare R2, Local filesystem (fallback).  
**PDF:** Invoice, Payroll pay stub, BOL/POD.  
**PDF Import:** Extract từ PDF bằng PdfPig + LLM → tạo load draft.

---

### 6.2 Messaging (Nhắn tin)

- Direct chat: dispatcher ↔ driver ↔ customer
- Tenant-wide chat, Load-specific thread
- Read receipts, unread count
- Real-time qua SignalR

---

### 6.3 Notifications (Thông báo)

**Kênh:** In-app, Firebase push, Email (Resend), Telegram.

**Sự kiện kích hoạt thông báo:**
- Load assigned/updated/removed/proximity
- Trip dispatched/assigned/completed
- Payroll submitted/approved/rejected/paid
- Document uploaded/deleted
- Driver license sắp hết hạn

---

## 7. Identity, Roles, Subscription, Feature Flags

### 7.1 Invitations

- Employee/customer user invitation theo tenant
- App admin invitations
- Validate token, cancel/resend, expiry service

### 7.2 Tenants

**Create tenant flow:**
1. Insert tenant record vào master DB
2. Generate connection string
3. Tạo tenant database
4. Apply tenant migrations
5. Seed roles/permissions/default settings
6. Seed owner user
7. Cập nhật connection string

### 7.3 Subscription Plans

**Billing model:**
```
monthly = plan.Price + trucksCount × plan.PerTruckPrice
```

**Plan fields:** Tier, price, per-truck price, Stripe IDs, AI quota, max trucks, billing interval, plan features.

### 7.4 Feature Flags

- Attribute `[RequiresFeature]` trên request type
- Pipeline `FeatureCheckBehaviour` kiểm tra tenant feature config
- Admin có thể override feature per-tenant
- Bật/tắt AI dispatch theo plan/tenant

---

## 8. Reports & Dashboard

### 8.1 Dashboard KPIs

```
ActiveLoadsCount = loads có status Dispatched hoặc PickedUp
UnassignedLoadsCount = loads Draft chưa có xe
IdleTrucksCount = tổng xe − xe đang chở hàng active
OutstandingInvoiceTotal = Σ(total − paid) cho Issued/PartiallyPaid
OverdueInvoiceCount = số invoice quá hạn DueDate
PaymentsReceivedThisWeek = Σ(Payment) status Paid từ đầu tuần
TopTrucks = top 3 xe theo DeliveryCost tháng này
```

### 8.2 Loads Report

```
TotalRevenue = Σ(DeliveryCost)
AverageRevenuePerLoad = TotalRevenue / TotalLoads
DeliveryRate = DeliveredLoads / TotalLoads × 100
OnTimeDelivery = OnTimeDelivered / DeliveredLoads × 100
OnTime nếu DeliveredAt ≤ DispatchedAt + max(1, Distance/500) ngày
```

### 8.3 Financials Report

```
TotalDue = TotalInvoiced − TotalPaid
CollectionRate = TotalPaid / TotalInvoiced × 100
OverdueAmount = Σ(total − paid) quá hạn DueDate
profit = paid − expenses
profitMargin = profit / revenue × 100
```

### 8.4 Payroll Report

```
TotalOutstanding = TotalPayroll − TotalPaid
AveragePayrollAmount = TotalPayroll / payroll count
```

---

## 9. Load Board Integration

**Providers hỗ trợ:** DAT, Truckstop, 123Loadboard, Demo.

**Chức năng:**
- Tạo/cập nhật/xóa cấu hình provider
- Tìm kiếm load theo origin/destination/date/equipment/rate
- Book listing → tạo Load trong TMS
- Post truck capacity → `PostedTruck`

---

## 10. Background Jobs & Webhooks

### 10.1 Jobs

| Job | Tần suất |
|---|---|
| AI dispatch session | On-demand |
| ELD sync | Định kỳ |
| Maintenance reminder | Định kỳ |
| Payroll generation | Monthly/Weekly |
| License expiry reminder | Định kỳ |
| Load board sync | Định kỳ |
| Privacy export/deletion/retention | Định kỳ |

### 10.2 Webhooks

**Stripe:**
- `invoice.paid`
- `customer.subscription.*`
- `payment_intent.processing|succeeded|payment_failed`
- `account.updated`
- `checkout.session.completed`

**ELD:**
- `/webhooks/eld/{samsara,motive,geotab,tteld}`
- Xác thực chữ ký HMAC (Geotab)

**Thiết kế:** Signature validation → Idempotency check → Audit log → Command handler.

---

## 11. Điểm rủi ro & lưu ý nghiệp vụ

| # | Vấn đề | Tác động |
|---|---|---|
| 1 | Đơn vị `Load.Distance` chưa xác định rõ (km hay m) | Sai payroll, revenue per mile, HOS |
| 2 | EF Core lazy loading — không dùng `.Include()` | N+1 query nếu không cẩn thận |
| 3 | Handler phải tự gọi `SaveChangesAsync` | Mất data nếu quên |
| 4 | Infrastructure không được reference Application | Vi phạm Clean Architecture |
| 5 | Tax manual chỉ đúng ở mức cơ bản (US local cần Stripe Tax) | Tính thuế sai cho US local/county |
| 6 | HOS limits chỉ để hiển thị, không thay thế ELD provider | Không dùng để gating compliance |
| 7 | AI autonomous mode execute ngay | Cần test kỹ permission và audit trail |
| 8 | Public payment endpoint phải validate đầy đủ | Bảo mật thanh toán |
| 9 | Billing endpoints có bypass subscription check | Endpoint mới phải cân nhắc bypass |
| 10 | Cross-tenant analytics cần đọc master hoặc iterate tenant | Không có cross-tenant query sẵn |
