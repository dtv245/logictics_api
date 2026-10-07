# PROJECT SPECIFICATION — LOGISTICSX

**Phiên bản:** 1.0 (Reverse-engineered baseline)  
**Ngày:** 2026-07-16  
**Ngôn ngữ:** Tiếng Việt  
**Đối tượng sử dụng:** Business Analyst, Product Manager, UI/UX Designer, Developer, Tester, Solution Architect, Project Manager  
**Nguồn:** `CLAUDE.md`, `.claude/feature-map.md`, tài liệu trong `docs/`, domain model, controller, application handler và giao diện hiện có trong repository.

> **Quy ước:** `[CẦN XÁC NHẬN]` là thông tin chưa có bằng chứng hoặc cần quyết định nghiệp vụ; `[GIẢ ĐỊNH]` là điều kiện tạm dùng để hoàn thiện luồng; `[ĐỀ XUẤT]` là khuyến nghị chưa thuộc baseline đã xác nhận. Tài liệu mô tả hệ thống hiện có, không mặc nhiên cam kết mọi chức năng là phạm vi của một đợt phát hành cụ thể.

---

# 1. Executive Summary

LogisticsX là nền tảng Transportation Management System (TMS) đa tenant cho doanh nghiệp vận tải đường bộ, hỗ trợ freight, intermodal container và vehicle transport. Sản phẩm kết nối quy trình từ tiếp nhận load, lập và điều phối trip, theo dõi tài xế/xe, chứng từ giao nhận, hóa đơn/thanh toán, payroll/expense, compliance ELD/HOS và báo cáo. Hệ thống có TMS Portal, Customer Portal, Admin Portal, website, ứng dụng tài xế Kotlin Multiplatform, REST API, Identity Server, MCP Server và Telegram Bot.

Giá trị kinh doanh cốt lõi là hợp nhất dữ liệu vận hành và tài chính theo từng công ty vận tải, giảm thao tác thủ công giữa dispatcher–driver–customer, tăng khả năng truy vết và hỗ trợ điều phối bằng AI có human-in-the-loop. Kiến trúc dùng master database cho dữ liệu nền tảng và database riêng cho từng tenant để cô lập dữ liệu vận hành.

Các thông tin chưa thể chốt từ repository gồm ngân sách, kế hoạch thương mại, thời hạn dự án, SLA, quy mô tải mục tiêu, thị trường pháp lý chính thức, chính sách retention và KPI baseline. Những phần này không nên được dùng làm cam kết hợp đồng trước khi stakeholder xác nhận.

---

# 2. Project Overview

## 2.1 Thông tin dự án

| Thuộc tính | Nội dung |
|---|---|
| Tên dự án | LogisticsX |
| Loại hệ thống | SaaS TMS/Fleet Management đa tenant |
| Lĩnh vực | Vận tải đường bộ, trucking, freight, intermodal container, vehicle transport |
| Người dùng | SuperAdmin, Admin, Owner, Manager, Dispatcher, Driver, Customer |
| Nền tảng | Web đa portal; mobile Android/iOS qua Kotlin Multiplatform; REST API; MCP |
| Phạm vi địa lý | Có cấu hình cho Hoa Kỳ và châu Âu; phạm vi thương mại/pháp lý chính thức `[CẦN XÁC NHẬN]` |
| Công nghệ | .NET 10, ASP.NET Core, EF Core, PostgreSQL 18, Angular 21, Kotlin/Compose Multiplatform, SignalR, Hangfire, Docker, Nginx |
| Thời gian dự án | `[CẦN XÁC NHẬN]` |
| Ngân sách | `[CẦN XÁC NHẬN]` |
| Mục tiêu kinh doanh | Cung cấp nền tảng subscription cho công ty vận tải; số hóa vận hành; hỗ trợ thu phí theo plan và số xe |
| Mục tiêu sản phẩm | Một nguồn dữ liệu thống nhất cho load–trip–fleet–driver–customer–finance–compliance |
| Vấn đề giải quyết | Dữ liệu phân tán, điều phối thủ công, thiếu real-time visibility, khó truy vết chứng từ/chi phí/compliance |

## 2.2 Bối cảnh và hiện trạng

`[GIẢ ĐỊNH]` Trước khi dùng LogisticsX, doanh nghiệp quản lý load, lịch xe, tài xế, chứng từ và hóa đơn qua spreadsheet, điện thoại, email và nhiều hệ thống nhà cung cấp. Repository không chứa khảo sát hiện trạng của khách hàng cụ thể nên AS-IS dưới đây là baseline cần phỏng vấn xác nhận.

| Vấn đề | Nguyên nhân khả dĩ | Tác động |
|---|---|---|
| Khó biết load/xe/tài xế đang ở trạng thái nào | Dữ liệu nằm ở nhiều kênh, cập nhật thủ công | Điều phối chậm, bỏ lỡ SLA, customer phải hỏi thủ công |
| Sai lệch giữa vận hành và tài chính | Trạng thái giao hàng, POD, invoice và payment không liên kết | Chậm phát hành hóa đơn, khó đối soát |
| Không kiểm soát HOS/giấy phép khi phân công | Dữ liệu ELD và hồ sơ nhân sự tách rời | Rủi ro compliance và an toàn |
| Khó đánh giá hiệu quả đội xe | Thiếu dashboard và dữ liệu chuẩn hóa | Quyết định dựa trên cảm tính |
| Khó mở rộng nhiều khách hàng doanh nghiệp | Thiếu tenant isolation, RBAC, plan gating | Rủi ro dữ liệu và vận hành SaaS |

Cơ hội: tự động hóa các bước có quy tắc rõ; cung cấp self-service cho customer; đưa dữ liệu real-time vào quyết định điều phối; khai thác AI nhưng giữ quyền phê duyệt của con người; bán sản phẩm theo subscription tier.

## 2.3 Mục tiêu

| Nhóm | Mục tiêu | Chỉ số đo lường |
|---|---|---|
| Ngắn hạn | Chuẩn hóa vòng đời load–trip–delivery–invoice | ≥95% bản ghi bắt buộc hợp lệ; 100% transition bị kiểm soát |
| Người dùng | Dispatcher nhìn được load chưa gán và xe sẵn sàng | Thời gian tìm/gán load `[CẦN XÁC NHẬN baseline/target]` |
| Người dùng | Driver nhận chuyến, cập nhật pickup/delivery và POD trên mobile | Tỷ lệ chuyến cập nhật qua app `[CẦN XÁC NHẬN]` |
| Kinh doanh | Thu phí subscription và per-truck | MRR, churn, conversion `[CẦN XÁC NHẬN target]` |
| Tài chính | Rút ngắn thời gian từ delivery đến invoice/payment | DSO và cycle time `[CẦN XÁC NHẬN target]` |
| Compliance | Chặn assignment không đủ giấy phép/hazmat/ADR | 100% assignment qua eligibility check; số vi phạm |
| Dài hạn | Mở rộng provider, vùng và tenant mà không phá cô lập dữ liệu | Tỷ lệ provisioning thành công; lỗi cross-tenant bằng 0 |
| Kỹ thuật | API, job và integration có logging/audit/retry | SLO, MTTR, retry success `[CẦN XÁC NHẬN]` |

---

# 3. Project Scope

| Nhóm | Nội dung |
|---|---|
| In Scope baseline | Identity/OIDC và RBAC; tenant/subscription/feature flag; customer/employee/driver/truck; load/trip/container/terminal; tracking; document/POD/BOL; messaging/notification; invoice/payment/Stripe Connect/payroll/expense/tax; ELD/HOS/DVIR/accident/maintenance; load board; AI dispatch/MCP; dashboard/report; privacy/GDPR; admin/marketing |
| Out of Scope | Warehouse Management, inventory/stock, procurement, maritime/air execution, native accounting ledger, customs declaration, carrier marketplace settlement `[GIẢ ĐỊNH theo code hiện có]` |
| Future Scope | `[ĐỀ XUẤT]` OCR chứng từ ngoài load PDF, predictive maintenance, configurable workflow engine, advanced route constraints, cross-tenant benchmark analytics |
| Constraints | Database-per-tenant; EF lazy loading; handler tự `SaveChangesAsync`; Application không phụ thuộc Infrastructure; provider ngoài có quota/availability; regional rules khác nhau |
| Dependencies | Stripe, Stripe Connect/Tax; Mapbox; Resend; Firebase; Azure Blob/R2; NHTSA; ELD providers; load-board providers; LLM providers; reCAPTCHA |

**Ranh giới phát hành:** Repository không chỉ ra release plan đã phê duyệt. MoSCoW trong tài liệu là `[ĐỀ XUẤT]` cho baseline sản phẩm, không phải cam kết scope.

---

# 4. Stakeholder Analysis

| Stakeholder | Vai trò | Mục tiêu | Quyền lợi | Trách nhiệm | Ảnh hưởng |
|---|---|---|---|---|---|
| Product Owner/Sponsor | Quyết định sản phẩm | ROI, adoption, compliance | Ưu tiên roadmap | Chốt scope/KPI/budget | Rất cao |
| Platform SuperAdmin | Vận hành SaaS | Tenant/plan ổn định | Quản trị toàn hệ thống | Provision, support, feature override | Rất cao |
| Tenant Owner | Chủ doanh nghiệp | Hiệu quả và lợi nhuận | Toàn quyền tenant | User, billing, policy | Cao |
| Manager | Quản lý vận hành | KPI, fleet, finance | Xem/phê duyệt trong tenant | Giám sát, xử lý ngoại lệ | Cao |
| Dispatcher | Điều phối | Gán đúng load/xe/driver | Dữ liệu vận hành real-time | Lập trip, dispatch, xử lý thay đổi | Cao |
| Driver | Thực thi chuyến | Thông tin rõ, thao tác nhanh | Mobile, navigation, POD | Vị trí, trạng thái, chứng từ | Cao |
| Customer/Shipper | Chủ hàng | Theo dõi và thanh toán | Self-service shipment/invoice | Cung cấp thông tin, thanh toán | Trung bình |
| Finance/Payroll | Kế toán `[CẦN XÁC NHẬN role mapping]` | Invoice, payment, payroll đúng | Báo cáo/đối soát | Phê duyệt, xuất chứng từ | Cao |
| Compliance/Safety | An toàn `[CẦN XÁC NHẬN role mapping]` | HOS/DVIR/incident đúng | Hồ sơ/audit | Review/resolve | Cao |
| Dev/QA/DevOps | Delivery | Hệ thống đúng và vận hành được | Contract rõ | Build, test, deploy, monitor | Cao |
| Provider bên thứ ba | Integration | Giao dịch/API hợp lệ | Traffic/revenue | Availability, webhook | Trung bình |

---

# 5. User Roles

## 5.1 Role và quyền sử dụng

| Role | Nhu cầu/mục tiêu | Chức năng chính | Hạn chế chính |
|---|---|---|---|
| SuperAdmin | Quản trị platform | Tenant, plan, feature, admin, AI settings, impersonation, marketing | Không mặc nhiên thao tác dữ liệu tenant nếu chưa impersonate/authorize |
| Admin | Hỗ trợ Admin Portal | Nghiệp vụ quản trị được gán | Không có toàn quyền mặc định như SuperAdmin `[CẦN XÁC NHẬN permission set]` |
| Owner | Điều hành tenant | Toàn bộ operations, finance, settings, user | Không quản trị tenant khác/platform |
| Manager | Giám sát | Operations, reports, approval | Quyền billing/user/feature tùy claim `[CẦN XÁC NHẬN]` |
| Dispatcher | Điều phối | Load, trip, truck view, tracking, chat, AI suggestions | Không quản trị platform; finance nhạy cảm tùy permission |
| Driver | Thực thi | Trips/loads được gán, proximity/status, location, POD/BOL, DVIR, message | Không xem dữ liệu đội xe/customer không liên quan |
| Customer | Self-service | Shipment, tracking, documents, invoice, payment | Chỉ dữ liệu customer được liên kết |

Quyền thực tế dùng role kết hợp permission/claim; tên role không được dùng thay cho authorization check ở các hành động nhạy cảm.

## 5.2 Persona

| Persona | Hồ sơ | Hành vi | Pain point | Motivation/Kỳ vọng | Tình huống phổ biến |
|---|---|---|---|---|---|
| Dispatcher vận hành | `[CẦN XÁC NHẬN tuổi]`; công nghệ trung bình–khá | Desktop, nhiều màn hình, phản ứng theo sự kiện | Thiếu dữ liệu real-time; gán trùng; đổi kế hoạch | Ra quyết định nhanh, ít lỗi | Xem unassigned loads → kiểm tra eligibility → tạo/dispatch trip |
| Driver | `[CẦN XÁC NHẬN tuổi]`; mobile-first | Dùng khi đang ở hiện trường; mạng không ổn định | Form dài, upload chậm, quyền vị trí | Ít chạm, hướng dẫn rõ, offline-safe | Xem stop → navigation → xác nhận pickup/delivery → chụp POD |
| Tenant Owner/Manager | Desktop/mobile web; xem KPI | Theo dõi dashboard và ngoại lệ | Số liệu không thống nhất | Một nguồn dữ liệu và audit | Xem utilization, overdue invoices, compliance |
| Customer | Công nghệ đa dạng | Truy cập portal hoặc public link | Phải gọi hỏi trạng thái/chứng từ | Tự tra cứu và thanh toán an toàn | Mở shipment/invoice, tải POD, trả tiền |
| SuperAdmin | Kỹ thuật/vận hành SaaS | Admin Portal, xử lý ticket | Provision/config sai ảnh hưởng nhiều tenant | Cô lập, quan sát, rollback | Tạo tenant, plan, feature override, impersonate có audit |

---

# 6. Business Process

## 6.1 AS-IS `[GIẢ ĐỊNH — cần workshop xác nhận]`

| Bước | Người thực hiện | Hoạt động | Input | Output | Vấn đề |
|---:|---|---|---|---|---|
| 1 | Sales/Dispatcher | Nhận booking qua email/điện thoại/PDF | Thông tin shipment | Dòng spreadsheet/ticket | Thiếu trường, nhập lại |
| 2 | Dispatcher | Tìm xe/tài xế | Lịch, vị trí, trao đổi | Assignment | Không kiểm tra conflict/HOS tập trung |
| 3 | Dispatcher | Gửi lệnh vận chuyển | Điện thoại/chat | Driver nhận thông tin | Khó chứng minh đã nhận |
| 4 | Driver | Pickup/delivery, báo trạng thái | Lệnh, vị trí | Tin nhắn/cuộc gọi | Cập nhật trễ, không chuẩn hóa |
| 5 | Driver/Office | Gửi POD/BOL | Ảnh/file | Chứng từ | Thất lạc, khó liên kết load |
| 6 | Finance | Lập invoice/payroll | Delivery, rate, hours | Invoice/payroll | Đối soát thủ công |
| 7 | Manager | Tổng hợp báo cáo | Nhiều nguồn | KPI | Chậm và sai khác |

## 6.2 TO-BE

| Bước | Actor | Hệ thống xử lý | Tự động hóa/kiểm soát |
|---:|---|---|---|
| 1 | Dispatcher/API/Load board | Tạo/import load Draft, validate customer/address/cargo | PDF extraction tạo draft, không tự dispatch |
| 2 | Dispatcher/AI | Lọc load chưa gán, xe khả dụng, HOS/license/hazmat | Hard block và warning; AI cần approve ở HITL |
| 3 | Dispatcher | Tạo trip, tối ưu stops, dispatch | Kiểm soát pickup trước drop-off; phát event/notification |
| 4 | Driver | Nhận trip, gửi vị trí/proximity, xác nhận stop | Pickup/delivery chỉ khi đúng trạng thái và proximity |
| 5 | Driver | Chụp POD/BOL/DVIR | Upload blob, gắn entity, audit |
| 6 | System/Finance | Cập nhật load/trip; issue invoice; nhận payment | Webhook idempotency; partial payment; status calculation |
| 7 | Manager | Xem dashboard/reports | KPI từ dữ liệu chuẩn hóa; filter/export `[export cần xác nhận]` |

Các bước bị loại bỏ: nhập lại cùng dữ liệu ở nhiều bảng; gọi điện chỉ để hỏi trạng thái; đối soát POD với load bằng tên file. Các bước vẫn cần người xác nhận: AI decision trong HITL, payroll/expense approval, DVIR/accident review, các exception nghiệp vụ.

---

# 7. Business Rules

| Mã | Tên quy tắc | Mô tả/Điều kiện | Kết quả | Ngoại lệ |
|---|---|---|---|---|
| BR-01 | Tenant isolation | Request tenant xác định theo MCP API key → `X-Tenant` → JWT claim | Chỉ dùng DB tenant tương ứng | Public endpoint dùng tenant/token riêng; thiếu tenant từ chối |
| BR-02 | Load transition | Draft→Dispatched→PickedUp→Delivered; Draft/Dispatched/PickedUp có thể Cancelled | Set timestamp tương ứng | Delivered/Cancelled là kết thúc |
| BR-03 | Driver confirm | Pickup khi Dispatched và gần điểm; Delivery khi PickedUp và gần điểm | Cập nhật trạng thái | Không proximity hoặc sai state: từ chối |
| BR-04 | Trip stop | Pickup arrival làm load PickedUp; DropOff làm Delivered | Tất cả drop-off xong → Completed | Cancel trip chưa completed sẽ cancel loads |
| BR-05 | Stop order | Pickup phải đứng trước drop-off của cùng load | Route hợp lệ | Optimizer fallback vẫn phải giữ constraint |
| BR-06 | Truck driver | Tạo xe phải có main driver; driver không gán đồng thời nhiều xe | Assignment duy nhất | `[CẦN XÁC NHẬN]` temporal assignment/history |
| BR-07 | Dispatch eligibility | License/medical/hazmat/ADR phải phù hợp và còn hạn | Block hoặc warning | Medical sắp hết hạn 7 ngày chỉ warning |
| BR-08 | Container state | Chỉ transition theo state machine đã định nghĩa | Cập nhật state/event | Transition ngoài danh sách bị từ chối |
| BR-09 | Invoice total | Exclusive/Inclusive/ReverseCharge; làm tròn 2 decimals | Subtotal, tax, total | Provider tax lỗi dùng fallback `[CẦN XÁC NHẬN policy]` |
| BR-10 | Payment allocation | 0 < amount ≤ amount due; tổng paid quyết định Partial/Paid | Cập nhật payment/invoice | Duplicate webhook không được ghi hai lần |
| BR-11 | Payment link | Active, chưa hết hạn, chưa revoke, invoice payable | Cho phép checkout | Sai tenant/token không làm lộ invoice |
| BR-12 | Payroll | Công thức theo Weekly/Monthly/ShareOfGross/Distance/Hourly | Tạo payroll invoice | Không tạo period overlap |
| BR-13 | Subscription price | Base fee + truck count × per-truck fee | Monthly charge | Currency/tax/rounding `[CẦN XÁC NHẬN]` |
| BR-14 | Feature resolution | Plan/default + tenant config + admin override | Cho/chặn request | Thứ tự override chính xác `[CẦN XÁC NHẬN bằng test contract]` |
| BR-15 | AI quota | Usage tuần so với plan quota; null = unlimited | Cho/chặn run | Overage billing behavior `[CẦN XÁC NHẬN]` |
| BR-16 | AI HITL | Suggestion phải approve trước execute | Decision audit | Autonomous execute trực tiếp nếu được cấu hình/quyền |
| BR-17 | Public tracking | Link hợp lệ, chưa revoke; chỉ public documents | Hiển thị shipment giới hạn | Không lộ PII/internal notes |
| BR-18 | Invitation | Token hợp lệ, chưa hết hạn/cancel/accept | Tạo access phù hợp type/tenant | Email trùng và membership cũ: `[CẦN XÁC NHẬN]` |
| BR-19 | Audit | Ghi actor, timestamp, result và before/after với hành động nhạy cảm | Có khả năng truy vết | Không ghi secret/token/raw payment data |
| BR-20 | Soft delete | Entity nghiệp vụ ưu tiên giữ lịch sử | Ẩn khỏi active query | Quy tắc theo entity `[CẦN XÁC NHẬN]` |

---

# 8. Functional Decomposition

| Module | Feature | Sub-feature/User action/System action | Actor | MoSCoW | Giai đoạn |
|---|---|---|---|---|---|
| Identity | Authentication | Login/OIDC/token/logout; validate session | Tất cả | Must | MVP |
| Identity | RBAC | Role/permission/invitation/user access | Admin/Owner | Must | MVP |
| Platform | Tenant | Provision DB, settings, logo, quota | SuperAdmin/Owner | Must | MVP |
| Platform | Subscription | Plan, billing, feature gating | SuperAdmin/Owner | Must | MVP |
| Operations | Customer/Employee/Truck | CRUD, assignment, VIN decode | Owner/Manager/Dispatcher | Must | MVP |
| Operations | Load | Create/import/search/update/dispatch/cancel/eligibility | Dispatcher | Must | MVP |
| Operations | Trip | Create/optimize/dispatch/arrive/cancel/timeline | Dispatcher/Driver | Must | MVP |
| Intermodal | Container/Terminal | CRUD, link load, status | Dispatcher | Should | Phase 2 |
| Driver | Mobile execution | Trips, navigation, status, location, POD/BOL | Driver | Must | MVP |
| Finance | Invoice/Payment | Calculate, issue, link, partial pay, webhook | Finance/Customer | Must | MVP |
| Finance | Payroll/Expense/Tax | Calculate, approve, record, report | Manager/Finance | Should | Phase 2 |
| Compliance | ELD/HOS | Provider config, map, sync, limits | Manager/Dispatcher | Should | Phase 2 |
| Safety | DVIR/Accident/Inspection | Create, submit, review, resolve | Driver/Manager | Should | Phase 2 |
| Fleet | Maintenance | Schedule, record, reminder | Manager | Should | Phase 2 |
| Communication | Documents/Messaging/Notification | Upload/download/chat/push/email | Tất cả theo phạm vi | Must | MVP |
| Visibility | Tracking | Live map/public link | Dispatcher/Customer | Must | MVP |
| Integrations | Load board | Configure/search/book/post truck | Dispatcher | Could | Phase 3 |
| AI | AI Dispatch/MCP | Run, suggest, approve/reject/replan, external tools | Dispatcher/API client | Could | Phase 3 |
| Analytics | Dashboard/Reports | KPI/filter/export | Owner/Manager | Should | Phase 2 |
| Privacy | GDPR requests | Consent/export/delete/admin review | User/Admin | Should | Theo thị trường |
| Marketing | Website/Blog/Contact/Demo | Publish, collect lead | Visitor/Admin | Could | Phase 3 |

---

# 9. Functional Specification

## 9.1 Chuẩn lỗi, audit và notification áp dụng chung

- Validation error: không thay đổi dữ liệu; trả lỗi theo trường, không dùng thông báo mơ hồ.
- Unauthorized/expired token: HTTP 401; frontend chuyển login và không tự replay mutation nếu không bảo đảm idempotency.
- Forbidden: HTTP 403; không tiết lộ dữ liệu tồn tại hay không nếu khác tenant.
- Not found: HTTP 404 trong đúng scope tenant; duplicate/conflict: HTTP 409; business validation: 422.
- Provider timeout/unavailable: 503 hoặc lỗi domain đã chuẩn hóa; ghi correlation ID; retry chỉ với thao tác idempotent.
- Audit tối thiểu cho mutation: actor/user ID, tenant, UTC timestamp, action, entity/type/id, before/after hoặc changed fields, IP, user agent/device, correlation ID, result; redact secret, token, card/PII nhạy cảm.
- Notification chỉ gửi sau khi transaction nghiệp vụ thành công; duplicate event không được gửi lặp ngoài chủ ý.

## FR-01. Xác thực và thiết lập ngữ cảnh tenant

**Thông tin chung:** Module Identity; actor mọi người dùng/API client; Must Have; kích hoạt khi login hoặc gọi endpoint bảo vệ. Mục tiêu là xác minh danh tính, quyền và tenant trước khi truy cập dữ liệu.

**Pre-condition:** tài khoản active; portal/client cấu hình đúng Identity Server; tenant tồn tại với user tenant. **Post-condition:** có access token hợp lệ và request được gắn đúng tenant, hoặc bị từ chối mà không đọc dữ liệu.

| Bước | Actor Action | System Response |
|---:|---|---|
| 1 | Mở portal/app hoặc gọi API | Kiểm tra session/token |
| 2 | Nhập credential/thực hiện OIDC | Identity Server xác thực và phát token/claim |
| 3 | Gọi API | API validate signature, issuer, audience, expiry |
| 4 | Yêu cầu tài nguyên tenant | Resolve tenant theo API key MCP → header → JWT claim |
| 5 | Thực hiện chức năng | Kiểm tra permission/feature/subscription rồi mới handler |

**Alternative:** invitation acceptance tạo/ghép account; MCP dùng API key hash; public tracking/payment dùng scoped token. **Exception:** sai credential, lockout, expired token, tenant mismatch, disabled account, feature/plan bị khóa.

| Input | Kiểu | Bắt buộc | Validation |
|---|---|---:|---|
| Email/username | string | Có với password flow | Chuẩn hóa; format email nếu dùng email |
| Password | secret | Có với password flow | Policy `[CẦN XÁC NHẬN]` |
| Tenant identifier | claim/header/token | Tùy flow | Phải map đúng tenant active |
| API key | string | MCP | Format `logsx_{tenantId}_{random}`; chỉ lưu hash |

**Permission:** endpoint public chỉ được đánh dấu rõ; endpoint tenant yêu cầu authenticated + permission; SuperAdmin cross-tenant phải dùng flow được audit. **Notification:** email account/invitation khi có sự kiện tương ứng; không gửi password/token thô. **Status:** account lifecycle `[CẦN XÁC NHẬN]`.

**Acceptance Criteria:**

1. Given token và tenant hợp lệ, when gọi endpoint được cấp quyền, then request dùng đúng tenant DB.
2. Given token hết hạn, when gọi endpoint bảo vệ, then trả 401 và không mutation.
3. Given user thiếu permission, when gọi endpoint, then trả 403 và không lộ payload.
4. Given header tenant khác JWT mà user không có access, when gọi API, then từ chối và ghi security log.
5. Given lỗi Identity Server, when login, then hiển thị lỗi có thể hành động và không tạo session giả.

## FR-02. Quản lý tenant, user, role, invitation và feature

**Actor:** SuperAdmin (platform), Owner/authorized admin (tenant). **Mục tiêu:** provision tenant, mời người dùng và giới hạn quyền/tính năng. **Trigger:** tạo tenant, invitation hoặc cập nhật role/feature.

| Bước | Actor Action | System Response |
|---:|---|---|
| 1 | SuperAdmin nhập thông tin tenant/owner/region | Validate tên/slug/email/region |
| 2 | Xác nhận tạo | Ghi master tenant, tạo connection string/DB, migrate, seed role/settings/owner |
| 3 | Owner tạo invitation | Tạo token có type/tenant/expiry; gửi email |
| 4 | Người nhận accept | Validate token/email/status; cấp access/role |
| 5 | Admin đổi feature/permission | Resolve plan/default/tenant override; audit thay đổi |

**Alternative:** resend/cancel invitation; upload logo; impersonation; reset quota. **Exception:** slug/email trùng, migration thất bại, token hết hạn, plan không cấp feature, thao tác vào tenant khác.

| Input chính | Validation |
|---|---|
| Tenant name/slug | Bắt buộc; slug lowercase/unique |
| Region/currency/time zone | Enum/support list; thay đổi sau provisioning `[CẦN XÁC NHẬN]` |
| Owner email | Email hợp lệ, uniqueness/multi-tenant access theo policy |
| Role/permissions | Chỉ giá trị catalog; không tự nâng quyền vượt actor |
| Feature config | Feature tồn tại; override có actor đủ quyền |

**Status flow invitation:** Pending → Accepted hoặc Cancelled/Expired. **Audit:** provisioning step/result, role/feature before-after, impersonation reason/session. **Notification:** welcome/invitation/resend/cancel theo template.

**Acceptance Criteria:**

1. Given tenant data hợp lệ, when tạo tenant, then DB được migrate/seed và owner có thể đăng nhập.
2. Given slug đã tồn tại, when tạo tenant, then trả 409 và không tạo DB thứ hai.
3. Given invitation expired/cancelled, when accept, then từ chối và không cấp access.
4. Given tenant admin cố gán platform role, when submit, then trả 403/422.
5. Given provisioning thất bại giữa chừng, when transaction/workflow kết thúc, then trạng thái lỗi có thể retry/cleanup và không coi tenant là active `[ĐỀ XUẤT]`.

## FR-03. Quản lý master data vận hành

**Phạm vi:** Customer, customer user, employee/driver/license, truck, terminal. **Actor:** Owner/Manager/Dispatcher theo permission. **Mục tiêu:** cung cấp dữ liệu hợp lệ cho load/trip/compliance/finance.

| Bước | Actor Action | System Response |
|---:|---|---|
| 1 | Mở danh sách và filter | Trả dữ liệu tenant có paging/filter |
| 2 | Chọn tạo/sửa | Hiển thị form theo entity/region |
| 3 | Nhập dữ liệu | Validate client và server; address dùng cấu trúc chuẩn |
| 4 | Lưu | Kiểm tra duplicate/relation; ghi entity/audit |
| 5 | Xóa/khóa | Kiểm tra dependency; soft delete/disable theo policy |

| Entity | Field chính | Rule quan trọng |
|---|---|---|
| Customer | name, status, contact, billing address, tax/VAT ID | Customer active mới dùng cho load mới `[CẦN XÁC NHẬN]` |
| Employee | name, email, role, salary/type, status | Salary ratio 0..1 với ShareOfGross |
| DriverLicense | jurisdiction, class, endorsements, issued/expiry | Expiry và hazmat/ADR dùng eligibility |
| Truck | number, type, status, VIN, plate, capacity, main/secondary driver | Main driver bắt buộc; driver không gán trùng |
| Terminal | name, UN/LOCODE, country, type, address | Code unique trong tenant; format chuẩn |

**Alternative:** decode VIN bằng NHTSA; customer có nhiều customer users. **Exception:** VIN/provider lỗi vẫn cho nhập thủ công `[GIẢ ĐỊNH]`; entity đang được load/trip tham chiếu không xóa cứng.

**Acceptance Criteria:**

1. Given dữ liệu customer hợp lệ, when tạo, then chỉ xuất hiện trong tenant hiện tại.
2. Given truck thiếu main driver, when lưu, then trả validation error.
3. Given driver đã gán xe khác, when gán lần nữa, then trả conflict.
4. Given user thiếu quyền xóa, when gọi delete, then 403 và entity giữ nguyên.
5. Given VIN service lỗi, when decode, then thông báo provider unavailable và không ghi dữ liệu sai.

## FR-04. Quản lý Load

**Actor:** Dispatcher/Manager; Driver và Customer chỉ xem/cập nhật phần được cấp. **Priority:** Must. **Trigger:** booking mới, import PDF, load board booking hoặc API. **Mục tiêu:** quản lý shipment xuyên suốt từ Draft đến Delivered/Cancelled.

**Pre-condition:** tenant active; customer hợp lệ; address đầy đủ; actor có permission. **Post-condition:** load lưu với number duy nhất, state hợp lệ, liên kết customer/truck/container/document và timestamps nhất quán.

| Bước | Actor Action | System Response |
|---:|---|---|
| 1 | Chọn Create hoặc Import PDF | Hiển thị form hoặc extract thành draft preview |
| 2 | Nhập/xác nhận customer, origin, destination, cargo, dates, rate | Validate field, address, hazmat và dependency |
| 3 | Lưu Draft | Sinh ID/number, source và audit |
| 4 | Chọn truck/dispatcher/container | Kiểm tra availability/eligibility/conflict |
| 5 | Dispatch | Draft→Dispatched; set `DispatchedAt`; issue draft invoice nếu có |
| 6 | Driver xác nhận pickup/delivery | Kiểm tra state + proximity; cập nhật timestamp/event |

**Alternative:** search/filter/unassigned; update Draft; set/change container; eligibility preview; cancel. **Exception:** PDF không đọc được, duplicate external listing, customer/truck không tồn tại, hazmat data thiếu, invalid transition, concurrent update.

| Trường | Kiểu | Bắt buộc | Độ dài/Validation | Default |
|---|---|---:|---|---|
| Name | string | Có | max `[CẦN XÁC NHẬN từ validator]` | — |
| Type | enum | Có | catalog LoadType | — |
| CustomerId | UUID | Có | thuộc tenant, active | — |
| Origin/DestinationAddress | object | Có | country/state/city/address; server AddressValidator | — |
| DeliveryCost | money | Có | ≥0; currency tenant | 0 |
| Distance | number | `[CẦN XÁC NHẬN]` | ≥0; đơn vị canonical `[CẦN XÁC NHẬN]` | 0 |
| RequestedPickup/DeliveryDate | datetime | Tùy | delivery ≥ pickup | null |
| IsHazmat | bool | Có | true yêu cầu class/UN number | false |
| ContainerId/Terminal IDs | UUID | Không | cùng tenant | null |

**Status flow:** Draft→Dispatched/Cancelled; Dispatched→PickedUp/Cancelled; PickedUp→Delivered/Cancelled. Actor chuyển: Dispatcher dispatch/cancel; Driver/authorized operator pickup/delivery theo proximity. **Notification:** assignment/update/proximity/delivery cho driver, dispatcher, customer theo preference. **Audit:** source, assignment, transition, before/after.

**Acceptance Criteria:**

1. Given form hợp lệ, when tạo load, then load Draft có number duy nhất và audit.
2. Given delivery date trước pickup, when lưu, then 422 theo trường.
3. Given actor thiếu `Load.Create`, when POST, then 403.
4. Given cùng external listing đã book, when book lại, then 409 và không tạo load trùng.
5. Given Draft hợp lệ và truck đủ điều kiện, when dispatch, then status/timestamp/invoice/event cập nhật nhất quán.
6. Given load Delivered, when cancel/update state, then transition bị từ chối.
7. Given server/provider import lỗi, when import PDF, then không tạo load hoàn chỉnh và trả correlation ID.

## FR-05. Lập và thực thi Trip

**Actor:** Dispatcher/Manager/Driver. **Mục tiêu:** gom nhiều load thành chuỗi pickup/drop-off khả thi và theo dõi thực thi. **Pre-condition:** loads tồn tại, chưa ở terminal state, truck/driver phù hợp.

| Bước | Actor Action | System Response |
|---:|---|---|
| 1 | Chọn truck và loads | Kiểm tra availability, overlap, eligibility |
| 2 | Tạo stops | Tạo cặp pickup/drop-off theo load |
| 3 | Optimize | Gọi Mapbox Matrix; fallback nearest-neighbor |
| 4 | Review route | Hiển thị order, distance, warning |
| 5 | Dispatch trip | Set DispatchedAt, event/notification |
| 6 | Driver mark arrived | Pickup/DropOff cập nhật load; tính trạng thái trip |

**Alternative:** manual reorder hợp lệ; timeline; cancel trước Completed. **Exception:** route provider timeout; stop/drop-off trước pickup; duplicate load; truck conflict; arrive sai stop/quyền.

| Input | Validation |
|---|---|
| TruckId | cùng tenant, usable, driver hợp lệ |
| LoadIds | ít nhất 1; không duplicate; state phù hợp |
| Stops | order 1-based unique; address hợp lệ; pickup trước drop-off |
| Optimization option | provider/catalog hợp lệ |

**Status:** Draft→Dispatched→InTransit→Completed; non-completed→Cancelled. **Output:** Trip detail, optimized stops, total distance/revenue/driver share, timeline. **Audit/notification:** dispatch/cancel/arrival/completion và route source/fallback.

**Acceptance Criteria:**

1. Given loads/truck hợp lệ, when tạo, then trip Draft có đủ stop pairs.
2. Given drop-off trước pickup, when save/optimize, then từ chối hoặc sửa mà vẫn giữ constraint.
3. Given thiếu quyền dispatch, when dispatch, then 403.
4. Given load đã thuộc active trip, when thêm lần nữa, then conflict.
5. Given Mapbox timeout, when optimize, then dùng heuristic và đánh dấu nguồn fallback.
6. Given tất cả drop-off arrived, when xử lý stop cuối, then trip Completed và loads Delivered.

## FR-06. Driver Mobile Execution

**Actor:** Driver. **Mục tiêu:** nhận chuyến, điều hướng, cập nhật vị trí/trạng thái và chứng từ tại hiện trường. **Pre-condition:** login, driver liên kết employee/truck/trip; permission location/camera khi cần.

| Bước | Actor Action | System Response |
|---:|---|---|
| 1 | Mở Trips/Dashboard | Tải active trip/load được gán |
| 2 | Chọn stop/navigation | Mở map route phù hợp platform |
| 3 | Cho phép location | Gửi vị trí qua SignalR; theo dõi proximity |
| 4 | Xác nhận pickup/delivery | Validate state/proximity, cập nhật và thông báo |
| 5 | Chụp POD/BOL/DVIR | Validate file/form, upload, liên kết record |

**Alternative:** xem past loads/stats/messages/licenses/privacy/settings. **Exception:** offline, revoked permission, token expiry, upload gián đoạn, assignment bị đổi. `[CẦN XÁC NHẬN]` offline queue/conflict policy.

| Input | Validation |
|---|---|
| Location | lat -90..90, lon -180..180, timestamp không quá cũ `[CẦN XÁC NHẬN threshold]` |
| Device token | non-empty/provider format |
| Status confirmation | load được gán; state/proximity hợp lệ |
| POD photo/signature | MIME/size/malware policy; load phù hợp |

**Acceptance Criteria:**

1. Given driver có active trip, when mở app, then chỉ thấy dữ liệu được gán.
2. Given chưa ở proximity, when confirm pickup, then bị chặn với lý do rõ.
3. Given driver khác, when truy cập load ID, then 403/404.
4. Given upload file trùng retry, when gửi cùng idempotency key, then không tạo hai POD `[ĐỀ XUẤT]`.
5. Given mất mạng, when gửi vị trí/POD, then app hiển thị trạng thái chưa đồng bộ; hành vi retry `[CẦN XÁC NHẬN]`.

## FR-07. Container và Terminal

**Actor:** Dispatcher/Manager. **Mục tiêu:** theo dõi container ISO 6346 độc lập với từng load và vị trí terminal. **Input:** container number, ISO type, seal, booking/BOL, laden, gross weight, current terminal.

**Main flow:** tạo terminal → tạo/scan container → validate ISO number/type → link vào load/terminals → chuyển state theo movement → lưu timestamps/event. **Exception:** number/code trùng, invalid ISO/check digit `[CẦN XÁC NHẬN implementation]`, invalid transition, cross-tenant link.

| Hiện tại | Hành động | Tiếp theo |
|---|---|---|
| Empty | load/at port/return | Loaded/AtPort/Returned |
| AtPort | load/depart/empty | Loaded/InTransit/Empty |
| Loaded | depart/return port | InTransit/AtPort |
| InTransit | deliver/at port | Delivered/AtPort |
| Delivered | empty/return | Empty/Returned |
| Returned | reuse | Empty |

**Acceptance Criteria:**

1. Given ISO number hợp lệ và unique, when tạo, then container ở state khởi tạo hợp lệ.
2. Given number trùng, when tạo, then 409.
3. Given user thiếu permission, when đổi state, then 403.
4. Given Empty, when chuyển thẳng Delivered, then 422.
5. Given persistence lỗi, when đổi state, then state/timestamp/event không cập nhật một phần.

## FR-08. Invoice, Payment và Public Payment Link

**Actor:** Owner/Manager/Finance `[CẦN XÁC NHẬN role]`, Customer, webhook Stripe. **Priority:** Must. **Mục tiêu:** tính đúng số phải thu, nhận partial/full payment an toàn và đối soát trạng thái.

| Bước | Actor Action | System Response |
|---:|---|---|
| 1 | Tạo/nhận invoice từ load/payroll/subscription | Tạo Draft và line items |
| 2 | Preview tax/issue/send | Tính subtotal/tax/total; set status/due date |
| 3 | Tạo payment link | Sinh random token, expiry, active; chỉ hiển thị token đúng lúc |
| 4 | Customer mở public link | Validate tenant/token/expiry/invoice payable; hiển thị amount due |
| 5 | Customer thanh toán | Tạo intent/checkout qua Stripe Connect; không nhận card raw |
| 6 | Stripe webhook | Verify signature/idempotency; ghi payment; recalc invoice |

**Alternative:** payment record thủ công; partial payment; cancel link/invoice; download PDF. **Exception:** amount ≤0 hoặc >due; currency mismatch; token invalid; payment failed; webhook duplicate/out-of-order; Connect account not ready.

| Input | Kiểu | Validation |
|---|---|---|
| Invoice type | enum | Load/Payroll/Subscription và relation tương ứng |
| Line item | description, amount, qty, tax | qty>0; amount rule `[CẦN XÁC NHẬN credit line]`; tax code hợp lệ |
| DueDate | date | ≥ issued date `[GIẢ ĐỊNH]` |
| Payment amount | money | 0 < amount ≤ amount due; currency bằng invoice |
| Link expiry | UTC datetime | tương lai; max lifetime `[CẦN XÁC NHẬN]` |

**Status:** Draft→Issued/Sent→PartiallyPaid/Paid; có thể Cancelled khi hợp lệ. Payment: provider-specific Processing→Paid/Failed/Cancelled `[CẦN XÁC NHẬN enum exact]`. **Audit:** không log token đầy đủ/card; lưu Stripe IDs, event ID, amount, actor/result. **Notification:** invoice issued, payment success/failure, receipt.

**Acceptance Criteria:**

1. Given valid line items/tax, when issue, then totals làm tròn 2 decimals và status Issued.
2. Given amount lớn hơn amount due, when pay, then 422 và không tạo intent.
3. Given public link expired/revoked, when mở, then không hiển thị invoice details.
4. Given webhook event đã xử lý, when nhận lại, then trả success idempotent và không ghi payment trùng.
5. Given Stripe unavailable, when checkout, then invoice không bị Paid và customer nhận lỗi retryable.
6. Given partial payment hợp lệ, when webhook success, then invoice PartiallyPaid và amount due giảm đúng.

## FR-09. Payroll, Time Entry, Expense và Tax

**Actor:** Employee/Driver, Manager/Finance. **Mục tiêu:** tính compensation/chi phí/thuế có phê duyệt và truy vết.

| Feature | Main flow | Rule |
|---|---|---|
| Time Entry | Create/update/delete work period | End > start; TotalHours tính từ interval |
| Payroll | Generate draft → submit → approve/reject → pay | Công thức theo SalaryType; không overlap period |
| Expense | Create type + receipt → approve/reject | Company/Truck/BodyShop; truck bắt buộc với truck expense |
| Tax | Preview/calculate theo jurisdiction/provider | Stripe Tax production; manual precedence/reverse charge |

**Status payroll:** Draft→PendingApproval→Approved→Paid; PendingApproval→Rejected. **Exception:** time overlap, missing salary, negative expense, duplicate receipt/invoice, tax provider unavailable, approver tự phê duyệt `[CẦN XÁC NHẬN segregation-of-duties]`.

**Acceptance Criteria:**

1. Given hourly employee và valid time entries, when generate payroll, then amount = tổng giờ × rate.
2. Given period overlap payroll cũ, when generate, then không tạo duplicate.
3. Given non-approver, when approve expense/payroll, then 403.
4. Given receipt MIME/size không hợp lệ, when upload, then 422 và không lưu blob.
5. Given tax provider lỗi, when calculate, then fallback/stop đúng policy `[CẦN XÁC NHẬN]` và ghi source.

## FR-10. Compliance, Safety và Maintenance

**Actor:** Driver, Manager, Compliance/Safety `[CẦN XÁC NHẬN mapping]`. **Mục tiêu:** đồng bộ HOS, thu thập inspection/incident và theo dõi bảo dưỡng.

| Capability | Flow | Output |
|---|---|---|
| ELD/HOS | Configure provider → map driver/vehicle → sync/webhook → normalize rule set | Driver HOS status/log/violations |
| DVIR | Driver create defects/photos/signature → submit → review → dismiss/reject/resolve | Inspection audit record |
| Accident | Create details/third parties/witnesses/photos → submit → review → resolve | Incident record |
| Vehicle condition | Create report/parts/damage | Condition history |
| Maintenance | Schedule by mileage/days/hours → reminder → service record/parts/cost | Due/overdue/service history |

**Rules:** region chọn FMCSA hoặc EU 561/2006; HOS display không thay thế provider compliance; Geotab webhook có HMAC; due soon ≤7 ngày theo baseline; submitted report không sửa tự do `[GIẢ ĐỊNH]`.

**Acceptance Criteria:**

1. Given provider credential hợp lệ, when sync, then data được map đúng tenant/driver và stamp rule set.
2. Given webhook signature sai, when nhận, then 401/403, không mutation và security log.
3. Given DVIR thiếu signature/required inspection, when submit, then 422.
4. Given user không phải reviewer, when review accident/DVIR, then 403.
5. Given maintenance due date đã qua, when query, then record `IsOverdue=true` và reminder không gửi lặp ngoài policy.
6. Given provider timeout, when sync, then ghi failure/retry mà không xóa last known HOS.

## FR-11. Document, Messaging, Notification và Tracking

**Actor:** người dùng tenant/customer/driver theo relation; public viewer với scoped link. **Mục tiêu:** chia sẻ chứng từ và trạng thái an toàn, real-time.

| Feature | Main flow | Validation |
|---|---|---|
| Document | upload metadata/file → malware/type/size check → blob → link entity → download/delete | owner/entity/tenant; MIME whitelist; size `[CẦN XÁC NHẬN]` |
| POD/BOL | driver capture photo/signature/data → upload/link load | assigned load, delivery context |
| Messaging | open direct/tenant/load conversation → send → SignalR → read receipt | participant authorization |
| Notification | domain event → preference/channel → send → delivery status | dedupe/retry; no sensitive data in push |
| Tracking | driver location SignalR; public token view/revoke/access count | scoped fields/documents only |

**Acceptance Criteria:**

1. Given authorized participant, when send message, then recipients nhận real-time và unread count tăng.
2. Given non-participant, when đọc conversation, then 403/404.
3. Given public document flag false, when dùng tracking link, then document không xuất hiện.
4. Given upload executable/oversize, when upload, then từ chối trước khi công khai.
5. Given notification provider lỗi, when send, then nghiệp vụ chính vẫn committed và notification retry/failed có quan sát.
6. Given tracking link revoked, when truy cập, then không trả shipment detail.

## FR-12. Load Board, AI Dispatch và MCP

**Actor:** Dispatcher/Manager/API-key client. **Mục tiêu:** tìm cơ hội vận tải và hỗ trợ/tự động hóa assignment có guardrail.

| Bước | Actor Action | System Response |
|---:|---|---|
| 1 | Configure provider/run AI | Validate feature, plan, quota, permission, credential |
| 2 | Search/retrieve context | Lấy unassigned loads, trucks, HOS, load-board listings |
| 3 | Score/check | Tính distance, revenue/distance, deadhead, eligibility |
| 4 | Propose | Tạo decision và audit timeline |
| 5 | Approve/reject/replan | HITL execute hoặc lấy rejection context chạy lại |
| 6 | Autonomous mode | Execute assignment/create/dispatch trip khi được phép |

**Alternative:** book listing thành load; post/remove truck; cancel session; MCP calls tool registry chung. **Exception:** over quota, model/provider lỗi, stale decision, load/truck changed, API key revoked, tool rate >100 req/min/key.

| Input | Validation |
|---|---|
| Mode | HumanInLoop/Autonomous; tenant/feature cho phép |
| Model | Global catalog/tier được phép |
| Decision ID | Pending, same tenant/session, chưa stale |
| Rejection reason | `[ĐỀ XUẤT]` bắt buộc, giới hạn độ dài |
| MCP API key | active, hashed lookup, tenant-bound |

**Status:** Session Pending/Running→Completed/Failed/Cancelled `[CẦN XÁC NHẬN exact enum]`; Decision Pending→Approved/Rejected/Executed/Failed tùy enum. **Audit:** prompt/model/provider/token/cost/tool calls/results/decision actor; redact secret và PII không cần thiết.

**Acceptance Criteria:**

1. Given HITL run hợp lệ, when AI đề xuất, then assignment chưa thay đổi trước approve.
2. Given decision stale vì load đã gán, when approve, then revalidate và từ chối an toàn.
3. Given quota hết, when run, then không gọi LLM và trả lý do/quota status.
4. Given API key của tenant A, when query tenant B, then bị từ chối và ghi audit.
5. Given provider timeout, when run, then session Failed/retryable, không execute một phần không rõ trạng thái.
6. Given autonomous mode thiếu explicit permission/feature, when run, then bị chặn.

## FR-13. Dashboard và Reports

**Actor:** Owner/Manager/authorized Dispatcher/Finance. **Mục tiêu:** đo hoạt động, doanh thu, collection, expense, payroll, driver/truck/customer performance.

**Input:** date range, status, customer, truck, driver, currency/time zone. **Output:** KPI, chart/table, paging; Excel/PDF `[CẦN XÁC NHẬN endpoint hiện có]`.

**Công thức baseline:** ActiveLoads = Dispatched+PickedUp; Unassigned = Draft không truck; Outstanding = issued/partial total-paid; CollectionRate = paid/invoiced; DeliveryRate = delivered/total; profit = paid-expenses. Công thức on-time hiện dùng `DeliveredAt ≤ DispatchedAt + max(1, Distance/500) ngày`, cần Product xác nhận vì không dùng requested delivery date.

**Acceptance Criteria:**

1. Given date range hợp lệ, when query, then tất cả KPI dùng cùng timezone/cutoff đã công bố.
2. Given không có dữ liệu, when mở report, then hiển thị empty state và giá trị 0, không chia cho 0.
3. Given user thiếu finance permission, when mở financial report, then 403/ẩn navigation.
4. Given filter customer thuộc tenant khác, when query, then không trả dữ liệu.
5. Given query lỗi/timeout, when tải dashboard, then từng widget có error/retry state và correlation ID.

## FR-14. Subscription, Billing và Feature Gating

**Actor:** SuperAdmin, Owner. **Mục tiêu:** quản lý plan, subscription, quota và chỉ cho dùng capability được mua/cấp.

**Main flow:** SuperAdmin cấu hình plan/features/pricing → tenant subscribe/onboard → Stripe event cập nhật subscription → middleware/behavior resolve feature → usage/quota được ghi → upgrade/downgrade/cancel. **Rule:** monthly = base + truck count × per-truck; billing endpoints cần bypass hợp lý để tenant bị khóa vẫn thanh toán được.

**Acceptance Criteria:**

1. Given subscription active và feature enabled, when gọi feature, then request tiếp tục.
2. Given plan không có feature, when gọi trực tiếp API, then bị chặn dù UI bị bypass.
3. Given Stripe webhook duplicate, when xử lý, then subscription không chuyển lặp/sai.
4. Given non-SuperAdmin, when sửa plan/default feature, then 403.
5. Given subscription expired, when mở billing recovery endpoint, then vẫn có đường thanh toán an toàn nhưng operations bị policy chặn.

## FR-15. Privacy/GDPR và Marketing Administration

**Actor:** Data subject, Admin, visitor, content admin. **Mục tiêu:** quản lý consent/export/delete; thu lead và quản trị blog/contact/demo.

**Main flow privacy:** record consent → user request export/delete → validate identity → admin review/job process → cung cấp export có expiry hoặc xóa/anonymize theo retention. **Main flow marketing:** visitor submit form qua captcha → admin view/update/delete; admin CRUD/publish/unpublish blog.

**Exception:** legal hold, retention conflict, duplicate request, captcha fail, export expired. Chính sách xóa/anonymize và thời hạn phản hồi `[CẦN XÁC NHẬN pháp lý]`.

**Acceptance Criteria:**

1. Given user authenticated, when request export, then request gắn đúng identity/tenant và không chứa tenant khác.
2. Given pending deletion, when user cancel trong thời hạn cho phép, then status chuyển Cancelled.
3. Given legal hold, when process deletion, then không xóa trái policy và ghi reason `[CẦN XÁC NHẬN]`.
4. Given visitor captcha fail, when submit contact/demo, then không tạo record.
5. Given content admin thiếu permission, when publish blog, then 403.

---

# 10. User Stories

| ID | User Story | Business Value | Priority | Dependency | Acceptance Criteria tham chiếu | Estimate |
|---|---|---|---|---|---|---|
| US-01 | As a user, I want to authenticate securely, so that I can access only authorized tenant data. | Cô lập và bảo mật | Must | Identity Server/RBAC | FR-01 AC1–5 | `[CẦN XÁC NHẬN]` |
| US-02 | As a SuperAdmin, I want to provision a tenant, so that a new company has an isolated workspace. | Onboarding SaaS | Must | Master DB/migrator/email | FR-02 AC1–5 | `[CẦN XÁC NHẬN]` |
| US-03 | As an Owner, I want to invite employees/customers with roles, so that they can self-onboard. | Giảm vận hành hỗ trợ | Must | Email/RBAC | FR-02 AC2–4 | `[CẦN XÁC NHẬN]` |
| US-04 | As a Dispatcher, I want to maintain customers, drivers and trucks, so that assignments use current data. | Dữ liệu chuẩn | Must | FR-01/02 | FR-03 AC1–5 | `[CẦN XÁC NHẬN]` |
| US-05 | As a Dispatcher, I want to create/import and dispatch loads, so that shipments are controlled end-to-end. | Core revenue flow | Must | Customer/truck | FR-04 AC1–7 | `[CẦN XÁC NHẬN]` |
| US-06 | As a Dispatcher, I want to optimize multi-stop trips, so that routes remain feasible and efficient. | Tối ưu chi phí | Must | Loads/Mapbox | FR-05 AC1–6 | `[CẦN XÁC NHẬN]` |
| US-07 | As a Driver, I want to view assigned trips and confirm stops, so that operations see real-time progress. | Visibility | Must | Mobile/location | FR-06 AC1–5 | `[CẦN XÁC NHẬN]` |
| US-08 | As a Driver, I want to capture POD/BOL/DVIR, so that delivery and compliance evidence is centralized. | Billing/compliance | Must | Storage/camera | FR-06/10/11 | `[CẦN XÁC NHẬN]` |
| US-09 | As a Dispatcher, I want to track containers and terminals, so that intermodal movements are traceable. | Intermodal support | Should | Load | FR-07 AC1–5 | `[CẦN XÁC NHẬN]` |
| US-10 | As a Finance user, I want to issue invoices and collect partial/full payments, so that receivables are accurate. | Cash flow | Must | Stripe/Tax | FR-08 AC1–6 | `[CẦN XÁC NHẬN]` |
| US-11 | As a Customer, I want to pay through an expiring link, so that I can pay without portal friction. | Conversion | Must | Public payment | FR-08 AC2–5 | `[CẦN XÁC NHẬN]` |
| US-12 | As a Manager, I want to approve payroll and expenses, so that spend is controlled. | Financial control | Should | Time entries | FR-09 AC1–5 | `[CẦN XÁC NHẬN]` |
| US-13 | As a Compliance Manager, I want synchronized HOS and reviewable DVIR, so that unsafe dispatch is prevented. | Safety/legal | Should | ELD provider | FR-10 AC1–6 | `[CẦN XÁC NHẬN]` |
| US-14 | As a tenant user, I want real-time chat/notification, so that operational changes are acknowledged quickly. | Coordination | Must | SignalR/providers | FR-11 AC1–6 | `[CẦN XÁC NHẬN]` |
| US-15 | As a Customer, I want a scoped tracking link, so that I can self-serve status and public documents. | Customer experience | Must | Tracking token | FR-11 AC3/6 | `[CẦN XÁC NHẬN]` |
| US-16 | As a Dispatcher, I want AI suggestions with approval, so that I can evaluate faster without losing control. | Productivity | Could | LLM/HOS/feature/quota | FR-12 AC1–6 | `[CẦN XÁC NHẬN]` |
| US-17 | As an Owner, I want dashboards and reports, so that I can manage fleet and cash performance. | Decision support | Should | Read models/data quality | FR-13 AC1–5 | `[CẦN XÁC NHẬN]` |
| US-18 | As an Owner, I want plan/feature state to be transparent, so that I can purchase or recover access. | Monetization | Must | Stripe billing | FR-14 AC1–5 | `[CẦN XÁC NHẬN]` |
| US-19 | As a data subject, I want export/deletion controls, so that my privacy rights can be exercised. | Compliance/trust | Should | Privacy jobs/storage | FR-15 AC1–3 | `[CẦN XÁC NHẬN]` |

---

# 11. Use Cases

## 11.1 Danh sách

| Use Case ID | Use Case Name | Actor | Module | Priority |
|---|---|---|---|---|
| UC-01 | Login và resolve tenant | User/API client | Identity | Must |
| UC-02 | Provision tenant và mời user | SuperAdmin/Owner | Platform | Must |
| UC-03 | Quản lý master data | Manager/Dispatcher | Operations | Must |
| UC-04 | Tạo/import và dispatch load | Dispatcher | Load | Must |
| UC-05 | Tạo/optimize/dispatch trip | Dispatcher | Trip | Must |
| UC-06 | Driver thực thi stop và POD | Driver | Mobile | Must |
| UC-07 | Theo dõi container | Dispatcher | Intermodal | Should |
| UC-08 | Issue invoice và customer pay | Finance/Customer/Stripe | Finance | Must |
| UC-09 | Generate/approve payroll/expense | Manager/Finance | Finance | Should |
| UC-10 | Sync HOS và review safety report | Driver/Compliance/provider | Compliance | Should |
| UC-11 | Chat/notify/public tracking | Tenant users/Customer | Communication | Must |
| UC-12 | AI dispatch decision | Dispatcher/AI | AI | Could |
| UC-13 | Xem dashboard/report | Owner/Manager | Analytics | Should |
| UC-14 | Manage subscription/feature | SuperAdmin/Owner/Stripe | Platform | Must |
| UC-15 | Request privacy export/delete | User/Admin | Privacy | Should |

## 11.2 Chi tiết Use Case

| ID | Description/Primary Actor | Pre-condition & Trigger | Main Flow | Alternative/Exception | Post-condition | Liên kết |
|---|---|---|---|---|---|---|
| UC-01 | Xác thực và chọn đúng tenant; User | Account active; mở app/API | Authenticate→token→resolve tenant→authorize | Expired/forbidden/mismatch | Session hợp lệ hoặc no-access | BR-01, FR-01, US-01 |
| UC-02 | Tạo workspace cô lập; SuperAdmin | Có tenant data; nhấn Create | master record→DB→migration→seed→owner→invite | Duplicate/provision failure/token expiry | Tenant usable hoặc failure state | BR-18/19, FR-02, US-02/03 |
| UC-03 | CRUD customer/employee/truck; Dispatcher | Auth/permission | list→form→validate→save→audit | duplicate/dependency/provider VIN fail | Master data current | BR-06/07, FR-03, US-04 |
| UC-04 | Quản lý shipment; Dispatcher | Customer/address hợp lệ | create/import→draft→assign→eligibility→dispatch | cancel/import fail/conflict | Load state/timestamps consistent | BR-02/03/07, FR-04, US-05 |
| UC-05 | Lập multi-stop trip; Dispatcher | Load/truck available | select→stops→optimize→review→dispatch→arrivals | manual route/fallback/cancel | Trip/load states aligned | BR-04/05, FR-05, US-06 |
| UC-06 | Thực thi mobile; Driver | Assigned trip | view→navigate→location→confirm→capture doc | offline/permission/token/upload fail | Progress/doc visible | BR-03/17, FR-06/11, US-07/08 |
| UC-07 | Intermodal lifecycle; Dispatcher | Container/terminal data | create→link→transition→audit | duplicate/invalid state | Container history current | BR-08, FR-07, US-09 |
| UC-08 | Thu tiền; Finance/Customer | Payable invoice | calculate→issue→link→checkout→webhook→recalc | partial/fail/duplicate/expired | Payment/invoice reconciled | BR-09/10/11, FR-08, US-10/11 |
| UC-09 | Chi lương/expense; Finance | Period/data complete | calculate/create→submit→approve→pay | reject/overlap/invalid tax | Approved financial record | BR-12, FR-09, US-12 |
| UC-10 | Compliance/safety; Driver/Reviewer | Provider/config or inspection | sync/create→submit→review→resolve | HMAC fail/timeout/reject | Auditable status | BR-07/19, FR-10, US-13 |
| UC-11 | Trao đổi/visibility; User | Relation/link valid | open→send/upload/view→receipt/access count | revoked/nonparticipant/provider fail | Message/doc/status shared safely | BR-17/19, FR-11, US-14/15 |
| UC-12 | AI assignment; Dispatcher | Feature/quota/context | run→tools→proposal→approve/reject→execute | autonomous/replan/stale/provider fail | Audited decision/outcome | BR-15/16, FR-12, US-16 |
| UC-13 | Analytics; Manager | Data/permission | filter→aggregate→render/export | empty/timeout/forbidden | KPI snapshot | FR-13, US-17 |
| UC-14 | Monetization; Admin/Owner | Plan/provider ready | subscribe→webhook→feature resolution→usage | past due/cancel/duplicate | Access matches subscription | BR-13/14/15, FR-14, US-18 |
| UC-15 | Privacy rights; User | Identity verified | request→review/job→export/delete | cancel/legal hold/expiry | Request result audited | BR-19/20, FR-15, US-19 |

---

# 12. UI/UX Specification

## 12.1 Danh sách màn hình cấp feature

| Screen ID | Màn hình | Actor | Mục đích | Chức năng chính |
|---|---|---|---|---|
| SCR-A01 | Admin Dashboard/Tenants | SuperAdmin | Vận hành SaaS | Tenant list/detail/create/quota/impersonate |
| SCR-A02 | Plans/Features/AI Settings/Admins | SuperAdmin | Cấu hình platform | CRUD plan, default/tenant feature, model, app admin |
| SCR-A03 | Blog/Contact/Demo/Privacy requests | Admin | Marketing/privacy | Publish, review lead/request |
| SCR-T01 | TMS Dashboard | Owner/Manager/Dispatcher | Tổng quan | KPI, map, alerts |
| SCR-T02 | Loads List/Detail/Form/Import | Dispatcher | Shipment | Search, CRUD, eligibility, dispatch, timeline/doc |
| SCR-T03 | Trips List/Detail/Form | Dispatcher | Chuyến | Stops, optimize, dispatch, cancel, timeline |
| SCR-T04 | Fleet/Employees/Customers | Manager/Dispatcher | Master data | List/detail/form/status/license/VIN |
| SCR-T05 | Containers/Terminals | Dispatcher | Intermodal | CRUD/link/state |
| SCR-T06 | Invoice/Payments/Payroll/Expense/Tax | Finance/Manager | Tài chính | Issue, approve, payment, PDF, report |
| SCR-T07 | ELD/Safety/Maintenance | Manager/Compliance | Compliance | Provider/map/sync/review/schedule |
| SCR-T08 | AI Dispatch/Load Board | Dispatcher | Tối ưu/tìm load | Sessions/timeline/decisions/search/book/post |
| SCR-T09 | Messages/Notifications/Documents | Tenant users | Cộng tác | Conversation, receipt, upload/download |
| SCR-T10 | Reports/Settings/API Keys | Owner/Manager | Analytics/config | Filter/KPI/company/integration/security |
| SCR-C01 | Customer Dashboard/Shipments | Customer | Self-service | Track shipment/history |
| SCR-C02 | Invoices/Payments/Documents | Customer | Tài chính/chứng từ | View/download/pay |
| SCR-P01 | Public Tracking | Link holder | Shipment visibility | Limited status/public docs |
| SCR-P02 | Public Payment | Link holder | Thanh toán | Invoice summary/Stripe Elements/result |
| SCR-D01 | Driver Login/Dashboard/Trips | Driver | Thực thi | Login, active/past trip/load |
| SCR-D02 | Trip/Load Detail/Navigation | Driver | Stop execution | Route, call/map, proximity/status |
| SCR-D03 | POD/BOL/DVIR/Condition | Driver | Bằng chứng | Camera, signature, defects |
| SCR-D04 | Messages/Stats/Account/Licenses/Privacy/Settings | Driver | Hỗ trợ cá nhân | Chat, KPI, profile, privacy/preferences |

## 12.2 Screen specification dùng chung

| Trạng thái | Yêu cầu |
|---|---|
| Loading | Skeleton/spinner theo vùng; khóa submit lặp; giữ filter |
| Empty | Nêu nguyên nhân và CTA nếu có quyền; không hiển thị lỗi như empty |
| Error | Thông báo ngắn, retry, correlation ID; không lộ exception/raw provider response |
| Success | Toast/banner và state mới; tránh toast cho auto-refresh thường xuyên |
| Permission | Ẩn navigation/CTA và backend vẫn chặn; trang direct URL trả access denied |
| Responsive | Web hỗ trợ desktop/tablet; breakpoint/device chính `[CẦN XÁC NHẬN]`; mobile driver ưu tiên một tay |
| Accessibility | `[ĐỀ XUẤT]` WCAG 2.2 AA: keyboard, focus, label, contrast, screen reader, reduced motion |
| Forms | Inline validation; server errors map field; address dùng component chuẩn theo country/state |
| Lists | Server paging/filter/sort; URL giữ query khi phù hợp; timezone/currency hiển thị rõ |
| Destructive action | Confirm với entity/context; nêu tác động; không dùng hard delete nếu có dependency |

## 12.3 Đặc tả màn hình trọng yếu

### SCR-T02 — Load Detail/Form

- Thành phần: header number/status; customer; origin/destination map; cargo/hazmat; dates; pricing; truck/dispatcher/container; timeline; documents; invoice; eligibility panel.
- CTA theo state/permission: Save Draft, Dispatch, Cancel, Set Container, Import, Download/Upload document.
- Điều kiện: Dispatch chỉ hiện/enable khi Draft và required fields/eligibility hợp lệ; Driver action không hiển thị ở TMS nếu không có permission.
- Error/empty: load not found hoặc tenant mismatch không hiển thị dữ liệu cached; import preview nêu field chưa chắc chắn.

### SCR-T03 — Trip Detail

- Thành phần: status/truck/driver, ordered stops, route map, distance/revenue, timeline.
- Drag/reorder chỉ cho Draft; luôn khóa pickup trước drop-off; hiển thị nguồn route Mapbox/heuristic.
- Dispatch yêu cầu confirm; Completed/Cancelled read-only trừ note/document được phép.

### SCR-P02 — Public Payment

- Chỉ hiển thị company identity tối thiểu, invoice number/amount/due và Stripe-hosted fields.
- Không lưu card data trong app; disable double submit; có Processing/Success/Failed/Expired states.
- URL/token không đưa vào analytics/referrer/log; `[ĐỀ XUẤT]` `Referrer-Policy: no-referrer`.

### SCR-D02/D03 — Driver execution

- CTA lớn, ít thao tác; hiển thị next stop và trạng thái đồng bộ.
- Xin quyền location/camera đúng thời điểm, giải thích mục đích; không chặn phần xem khi quyền chưa cần.
- Upload có progress/retry; không báo thành công trước server acknowledgement.

## 12.4 Navigation Flow

| Từ | Hành động | Đến | Điều kiện/chặn |
|---|---|---|---|
| Login | Auth success | Portal home theo role | Tenant/role hợp lệ; sai role chặn |
| TMS Dashboard | Click KPI load | Loads list có filter | Permission Load.View |
| Loads list | Create/select | Load form/detail | Create/View permission |
| Load detail | Create trip/dispatch | Trip form hoặc state mới | Eligibility/state valid |
| Trips list | Select | Trip detail | Tenant relation |
| Driver Dashboard | Select trip/stop | Trip/Load detail | Assignment active |
| Load detail | Capture POD | POD screen | Driver + state/context |
| Customer shipment | Invoice/document | Detail/download/pay | Customer relation |
| Public link | Open | Tracking/Payment | Token active/not expired/revoked |
| Admin tenant | Impersonate | TMS tenant context | SuperAdmin + reason/audit |

Back navigation phải giữ filter/list position; mutation success về detail hoặc giữ form với status rõ; expired auth đưa login và giữ safe return URL; thiếu quyền đưa Access Denied, không vòng lặp redirect.

---

# 13. Data Specification

## 13.1 Entity catalog

| Entity | Mô tả | PK | Quan hệ chính |
|---|---|---|---|
| Tenant | Công ty SaaS | UUID | 1–1 Subscription; 1–N User/Feature/API key |
| Subscription/Plan | Gói sử dụng | UUID | Tenant–Plan; Plan–Features |
| User/Role/Invitation | Identity/access | UUID | User–Tenant/Role; Invitation–Tenant |
| Customer/CustomerUser | Chủ hàng | UUID | Customer–Loads/Users |
| Employee/DriverLicense | Nhân sự/tài xế | UUID | Employee–Truck/TimeEntry/Payroll/License |
| Truck | Phương tiện | UUID | Truck–Driver/Load/Trip/Maintenance |
| Load | Shipment aggregate | UUID | Customer, Truck, Container, Invoice, Documents, Trips |
| Trip/TripStop | Chuyến và stop có thứ tự | UUID | Trip–Truck; Stop–Load |
| Container/Terminal | Intermodal equipment/location | UUID | Container–Loads/Terminal |
| Invoice/LineItem | Phải thu/phải trả | UUID | Invoice–Load/Employee/Subscription; Payments/Links |
| Payment/PaymentLink | Giao dịch/link | UUID | Invoice/Tenant/provider IDs |
| Expense/TimeEntry | Chi phí/giờ làm | UUID | Employee/Truck/Payroll |
| HosLog/Violation/Status | ELD/HOS | UUID | Employee/provider mapping |
| Dvir/Defect/Accident/Condition | Safety report | UUID | Driver/Truck/Documents |
| MaintenanceSchedule/Record/Part | Bảo dưỡng | UUID | Truck |
| Document | TPH document metadata | UUID | Load/Truck/Employee/Delivery |
| Conversation/Message/Receipt | Chat | UUID | Participants/tenant/load |
| Notification/TrackingLink | Alert/public access | UUID | User/load/token |
| AiDispatchSession/Decision | AI audit/decision | UUID | Load/Truck/Trip |
| LoadBoardConfig/Listing/PostedTruck | Provider freight market | UUID | Tenant/provider/load/truck |
| Consent/Export/Deletion | Privacy | UUID | User/Tenant |

## 13.2 Data Dictionary cốt lõi

### Load

| Column | Data Type | Null | Default | Constraint | Description |
|---|---|---:|---|---|---|
| id | uuid | No | generated | PK | Định danh |
| number | bigint | No | sequence `[CẦN XÁC NHẬN]` | unique tenant | Số nghiệp vụ |
| name | varchar | No | — | max `[CẦN XÁC NHẬN]` | Tên shipment |
| type/status/source | enum/int | No | Draft/manual | enum/state rule | Phân loại/vòng đời/nguồn |
| customer_id | uuid | No | — | FK | Customer |
| truck_id/dispatcher_id | uuid | Yes | null | FK | Assignment |
| origin/destination_address | owned fields/json `[CẦN XÁC NHẬN physical]` | No | — | AddressValidator | Địa chỉ |
| delivery_cost/currency | decimal/string | No | 0/tenant | ≥0 | Giá cước |
| distance | double | No | 0 | ≥0; unit TBD | Khoảng cách |
| requested_pickup/delivery | timestamptz | Yes | null | chronological | Hẹn |
| dispatched/picked_up/delivered/cancelled_at | timestamptz | Yes | null | state-driven | Audit state |
| is_hazmat/hazmat_class/un_number | bool/string | Mixed | false/null | dependent | Hàng nguy hiểm |

### Trip/TripStop

| Column | Type | Null | Constraint | Description |
|---|---|---:|---|---|
| trip.id/number/status | uuid/bigint/enum | No | PK/unique/state | Trip identity |
| trip.truck_id | uuid | Yes `[CẦN XÁC NHẬN]` | FK | Xe |
| total_distance | double | No | ≥0/unit TBD | Tổng khoảng cách |
| stop.id/trip_id/load_id | uuid | No | PK/FK | Quan hệ stop |
| stop.type/order | enum/int | No | order unique/trip; >0 | Pickup/DropOff thứ tự |
| stop.address | address | No | valid | Vị trí |
| stop.arrived_at | timestamptz | Yes | state-driven | Đến điểm |

### Invoice/Payment

| Column | Type | Null | Constraint | Description |
|---|---|---:|---|---|
| invoice.id/type/status | uuid/discriminator/enum | No | PK/state | Hóa đơn TPH |
| subtotal/tax_total/total | decimal | No | 2-decimal calculation | Tổng tiền |
| currency | char(3) | No | ISO 4217 | Tiền tệ |
| issued_at/due_date | timestamptz/date | Yes | due≥issue `[GIẢ ĐỊNH]` | Kỳ hạn |
| payment.id/invoice_id | uuid | No | PK/FK | Payment |
| payment.amount/status | decimal/enum | No | >0/state | Số tiền/kết quả |
| provider_intent/event_id | string | Yes | unique khi có | Idempotency/đối soát |
| payment_link.token_hash | string | No | unique | Không lưu token thô `[ĐỀ XUẤT nếu code chưa áp dụng]` |
| expires_at/is_active/access_count | timestamptz/bool/int | No | valid/count≥0 | Link lifecycle |

### Auditable fields

Mọi `AuditableEntity`: `created_at`, `created_by`, `updated_at`, `updated_by`. Với mutation nhạy cảm cần bảng/event audit riêng có before/after, IP, device và result; các field này không đồng nghĩa với full audit log.

## 13.3 Quan hệ và lifecycle dữ liệu

- Master DB chứa tenant, subscription, global identity/config; Tenant DB chứa operations. Không FK vật lý xuyên database; reference cross-boundary phải validate bằng application service.
- Customer 1–N Load; Load N–1 optional Truck; Trip 1–N TripStop; mỗi Stop N–1 Load; Load 1–N Document/Exception.
- Invoice 1–N LineItem/Payment/PaymentLink; Employee 1–N TimeEntry/Payroll; Truck 1–N Maintenance records.
- Container có thể phục vụ nhiều load theo các leg; quan hệ lịch sử/cascade `[CẦN XÁC NHẬN schema exact]`.
- Aggregate child dùng cascade khi không có giá trị độc lập (line item/stop); dữ liệu tài chính/audit không hard delete `[ĐỀ XUẤT]`.
- Trạng thái dữ liệu cần phân biệt Active, Inactive/Locked, SoftDeleted, Expired, PendingApproval. Hard delete chỉ cho dữ liệu chưa phát sinh dependency và theo retention.

---

# 14. API Specification

## 14.1 Contract chung

- Base URL/version: `/api/...`; versioning strategy `[CẦN XÁC NHẬN]`.
- Authentication: Bearer OIDC; MCP API key; public token ở endpoint tracking/payment; webhook signature riêng.
- Tenant scope: JWT/header/API key theo BR-01. Không nhận tenant ID trong body để override context trừ endpoint platform rõ ràng.
- Content: JSON UTF-8; multipart cho upload; UTC ISO-8601; money gồm amount/currency; paging format `[CẦN XÁC NHẬN chuẩn thống nhất]`.
- Mutation cần idempotency key với payment, webhook, booking và mobile upload `[ĐỀ XUẤT]`.
- Response không trả exception stack/provider secret; mọi lỗi có `code`, `message`, `fieldErrors?`, `correlationId`.

## 14.2 Endpoint inventory và contract nghiệp vụ

| API ID | Method/Endpoint | Chức năng/Actor | Auth/Authorization | Request chính | Success |
|---|---|---|---|---|---|
| API-LOAD-01 | GET `/loads`, `/loads/{id}`, `/loads/unassigned` | Query load; tenant user | Bearer + Load.View | filter/paging/id | 200 list/detail |
| API-LOAD-02 | POST `/loads` | Create Draft; Dispatcher | Load.Create | Load DTO | 201/200 Load |
| API-LOAD-03 | PUT/DELETE `/loads/{id}` | Update/delete; Dispatcher | Load.Update/Delete | ID + DTO | 200/204 |
| API-LOAD-04 | POST `/loads/import` | PDF→draft; Dispatcher | Load.Create + AI/import feature | multipart PDF | 200 preview/draft |
| API-LOAD-05 | POST `/loads/{id}/dispatch`; GET `/{id}/eligibility`; PUT `/{id}/container` | Dispatch/check/link | Load.Dispatch/Update | IDs/options | 200 |
| API-TRIP-01 | GET/POST/PUT/DELETE `/trips...` | CRUD trip | Trip permissions | query/Trip DTO | 200/201/204 |
| API-TRIP-02 | POST `/trips/optimize`, `/{id}/dispatch`, `/{id}/cancel`, `/{id}/stops/{stopId}/arrive`; GET timeline | Workflow | Dispatch/Driver scoped | IDs/stops | 200 |
| API-DRIVER-01 | GET `/drivers/{userId}`; POST device-token | Driver profile/push | self/admin | ID/token | 200/204 |
| API-DRIVER-02 | POST `/drivers/confirm-load-status`, `/update-load-proximity` | Mobile execution | assigned driver | load/status/location | 200 |
| API-MASTER-01 | CRUD `/customers`, `/customer-users`, `/employees`, `/trucks`, `/terminals`, `/containers` | Master data | module permissions | entity DTO | 200/201/204 |
| API-VIN-01 | GET `/vins/{vin}` | Decode VIN | authenticated | VIN path | 200 decode/source |
| API-DOC-01 | GET/POST/PUT/DELETE `/documents`; GET download | Document CRUD | relation permission | metadata/file | 200/201/204/file |
| API-DOC-02 | POST `/documents/pod`, `/documents/bol` | Capture delivery docs | assigned driver | multipart/form | 200/201 |
| API-MSG-01 | GET/POST `/messages/conversations`; GET conversation; POST message; PUT read; GET unread-count | Chat | participant | IDs/content | 200/201 |
| API-TRACK-01 | GET `/tracking/{tenantId}/{token}` và `/documents[/.../download]`; GET `/tracking/load/{loadId}`; POST/DELETE `/tracking...` | Public view và link management | anonymous scoped token hoặc Load permission | tenant/token/load/document | 200/204/file |
| API-INVOICE-01 | GET/PUT/DELETE `/invoices...`; POST `/invoices/loads`, `/{id}/send`, `/preview-tax`, line-items/payment-links/manual-payment; GET PDF | Invoice | Invoice.View/Manage | DTO/IDs | 200/204/file |
| API-PAY-01 | Payment CRUD; POST `/payments/methods/setup-intent` | Payment/admin | finance/customer | amount/provider | 200/201 |
| API-PUBLICPAY-01 | GET `/public/payments/{tenantId}/{token}`; POST `/{tenantId}/{token}/checkout` | Validate invoice/create Stripe checkout | anonymous scoped tenant+token | token/amount/success-cancel URL | 200 |
| API-STRIPE-01 | Stripe Connect controller | Onboarding/status | Owner | return/refresh URLs | 200/redirect |
| API-EXP-01 | GET `/expenses`, `/stats`, `/{id}`, receipt; POST company/truck/bodyshop/receipt; PUT/DELETE; POST approve/reject | Expense | finance/approver | DTO/file/reason | 200/201/204 |
| API-TIME-01 | CRUD `/time-entries` | Timesheet | self/manager | period DTO | 200/201/204 |
| API-TAX-01 | GET `/tax/jurisdictions`; tax-rate/preview routes | Tax | finance/admin | address/items | 200 |
| API-ELD-01 | GET/POST/DELETE `/eld/providers`; driver mappings | Provider config | admin/compliance | credential/mapping | 200/201/204 |
| API-ELD-02 | GET HOS/logs/limits; POST sync | HOS | dispatcher/compliance | employee/range | 200/202 |
| API-SAFE-01 | DVIR list/detail/pending/create/submit/review/dismiss/reject | Safety | driver/reviewer | report/reason | 200/201 |
| API-SAFE-02 | Accident list/detail/create/update/submit/review/resolve | Safety | driver/reviewer | report/reason | 200/201 |
| API-SAFE-03 | Inspection/driver behavior/maintenance controllers | Safety/fleet | scoped permission | DTO/filter | 200/201/204 |
| API-LB-01 | GET/POST/DELETE `/loadboard/providers`; POST search/book; GET/POST/DELETE trucks | Load board | feature + dispatcher | filters/IDs | 200/201/204 |
| API-AI-01 | POST `/ai-dispatch/run`, `/cancel/{id}`, `/sessions/{id}/replan`; GET sessions/detail/quota/pending | AI session | feature/quota/permission | mode/options/reason | 200/202 |
| API-AI-02 | POST `/ai-dispatch/decisions/{id}/approve|reject` | Decision | approver | reason/version `[ĐỀ XUẤT]` | 200 |
| API-PLATFORM-01 | CRUD `/tenants`; logo/welcome/quotas | Tenant admin | SuperAdmin/Owner for settings | Tenant DTO/file | 200/201/204 |
| API-PLATFORM-02 | `/features/defaults`, `/features/tenant/{id}`, tenant features | Feature config | SuperAdmin | config | 200 |
| API-PLATFORM-03 | `/subscriptions`, roles, users, admins, invitations, impersonation, API keys | Access/billing | role-specific | DTO/token | 200/201/204 |
| API-REPORT-01 | Report/stat controllers | KPI/report | report permission | filters | 200 |
| API-PRIV-01 | POST/GET privacy exports/deletions/consent; admin requests | Privacy | self/admin | request/ID | 200/201/204 |
| API-MKT-01 | CRUD blog/contact/demo; publish/unpublish | Website/admin | public create; admin manage | content/form | 200/201/204 |
| API-WEBHOOK-01 | POST `/webhooks/eld/{provider}` và Stripe webhook routes | Provider event | signature + idempotency | raw body/headers | 200/204 |
| API-MCP-01 | POST `/mcp` streamable HTTP | AI tools | API key; 100 req/min/key | MCP JSON-RPC | MCP response |

> API paths chưa ghi chính xác được đánh dấu thay vì suy đoán. Trước khi freeze contract, xuất OpenAPI từ build và bổ sung schema/example cho từng operation `[CẦN XÁC NHẬN deliverable]`.

## 14.3 Ví dụ contract

**Create Load — request minh họa** (tên field cần đối chiếu OpenAPI):

```json
{
  "name": "Shipment A",
  "type": "Freight",
  "customerId": "00000000-0000-0000-0000-000000000001",
  "originAddress": {"country": "US", "state": "CA", "city": "Los Angeles", "addressLine1": "..."},
  "destinationAddress": {"country": "US", "state": "NV", "city": "Las Vegas", "addressLine1": "..."},
  "deliveryCost": {"amount": 1250.00, "currency": "USD"},
  "isHazmat": false
}
```

**Success minh họa:** `201 Created` với `id`, `number`, `status=Draft`, dữ liệu đã chuẩn hóa. **Error minh họa:**

```json
{
  "code": "validation_failed",
  "message": "Dữ liệu không hợp lệ.",
  "fieldErrors": {"destinationAddress.state": ["Trường này là bắt buộc."]},
  "correlationId": "..."
}
```

## 14.4 Error catalog

| HTTP | Error code | Nguyên nhân | Client xử lý |
|---:|---|---|---|
| 200/201/204 | — | Thành công | Render/redirect; 204 không parse body |
| 400 | invalid_request | JSON/header/path sai | Sửa request; không retry tự động |
| 401 | unauthenticated/token_expired | Thiếu/sai token | Re-auth; không loop |
| 403 | forbidden/feature_disabled | Thiếu permission/plan | Ẩn action, giải thích/contact admin |
| 404 | not_found | Không tồn tại hoặc ngoài scope | Về list; không suy luận cross-tenant |
| 409 | duplicate/conflict/stale_state | Trùng hoặc concurrent transition | Refresh, resolve conflict |
| 422 | validation_failed/business_rule_violation | Field/business rule sai | Map field/message |
| 429 | rate_limited/quota_exceeded | Vượt rate/quota | Tôn trọng Retry-After; không spam retry |
| 500 | internal_error | Lỗi không dự kiến | Show correlation ID; không lộ stack |
| 503 | provider_unavailable/service_unavailable | Provider/service tạm lỗi | Retry có backoff nếu idempotent |

---

# 15. Non-functional Requirements

## 15.1 Performance

| ID | Requirement |
|---|---|
| NFR-P01 | `[CẦN XÁC NHẬN]` p95 read API mục tiêu; `[ĐỀ XUẤT]` ≤500 ms nội bộ, không gồm provider ngoài |
| NFR-P02 | `[CẦN XÁC NHẬN]` p95 mutation; `[ĐỀ XUẤT]` ≤1 s với transaction nội bộ |
| NFR-P03 | `[CẦN XÁC NHẬN]` concurrent users/RPS/tenant count/data volume |
| NFR-P04 | Long-running import/AI/sync dùng job/202 và progress thay vì giữ HTTP quá timeout `[ĐỀ XUẤT]` |
| NFR-P05 | File max theo loại/MIME `[CẦN XÁC NHẬN]`; frontend phải validate nhưng server là nguồn chuẩn |
| NFR-P06 | Kiểm soát N+1 do EF lazy loading; report/list dùng projection/read query thích hợp |

## 15.2 Security

- OIDC/OAuth2 qua Duende; HTTPS bắt buộc; token signature/issuer/audience/expiry; refresh/revocation policy `[CẦN XÁC NHẬN]`.
- RBAC + permission/claim + tenant relation; deny by default; authorization ở server.
- Password policy, MFA, login attempt/lockout `[CẦN XÁC NHẬN]`; `[ĐỀ XUẤT]` bắt buộc MFA cho SuperAdmin/Owner.
- Encrypt in transit; database/blob/backups at rest `[CẦN XÁC NHẬN cloud config]`; secret dùng secret store, không repo/log.
- Chống SQL injection qua parameterized EF; encode output/chống XSS; CSRF cho cookie flows; CSP cho portal/public pay.
- Upload: allowlist MIME/extension, magic-byte check, size, randomized object key, malware scan, signed URL TTL `[ĐỀ XUẤT]`.
- Rate limit: MCP 100 req/min/key đã xác định; login/public/webhook/API còn lại `[CẦN XÁC NHẬN]`.
- Webhook: verify raw body signature trước parse/mutation; idempotency event ID; timestamp tolerance/replay protection.
- Audit hành động nhạy cảm; không log credential, API key, payment token, raw PII không cần thiết.

## 15.3 Availability, Backup và Recovery

| Requirement | Mức |
|---|---|
| Uptime/SLA | `[CẦN XÁC NHẬN]` |
| Planned downtime | `[CẦN XÁC NHẬN]`; phải thông báo và có migration plan |
| Backup master/tenant DB/blob | `[CẦN XÁC NHẬN]` frequency/retention/encryption |
| RPO/RTO | `[CẦN XÁC NHẬN]` |
| Restore test | `[ĐỀ XUẤT]` định kỳ và theo mẫu tenant riêng |
| Failover/DR region | `[CẦN XÁC NHẬN]` |
| Provider outage | Circuit breaker/backoff/fallback theo integration; không mất accepted command |

## 15.4 Scalability

- Database-per-tenant hỗ trợ cô lập nhưng tăng chi phí provisioning/migration/connection pool; cần automation và observability theo tenant.
- Scale stateless API/portal ngang sau load balancer; SignalR backplane `[CẦN XÁC NHẬN khi multi-instance]`.
- Cache catalog/config/tax/routing có TTL và tenant key; không cache cross-tenant key thiếu tenant.
- Background jobs cần distributed lock/idempotency; giới hạn concurrency theo provider/tenant.

## 15.5 Compatibility và Usability

- Web browser/version, OS, screen size `[CẦN XÁC NHẬN]`; `[ĐỀ XUẤT]` latest 2 Chrome/Edge/Firefox/Safari.
- Driver app Android/iOS minimum version `[CẦN XÁC NHẬN]`; tablet layout cần kiểm thử vì có store assets.
- Accessibility target `[CẦN XÁC NHẬN]`; `[ĐỀ XUẤT]` WCAG 2.2 AA.
- UI nhất quán, keyboard/focus, clear error, confirmation, empty/loading/offline state; action wording dùng động từ nghiệp vụ.

## 15.6 Maintainability/Operations

- Tuân thủ Clean Architecture, DDD/CQRS, module boundary và architecture tests hiện có.
- Structured logging với tenant/correlation (không secret); metrics/traces cho API, job, SignalR, provider/webhook.
- API versioning/deprecation, OpenAPI, migration rollback/forward strategy `[CẦN XÁC NHẬN]`.
- CI/CD GitHub Actions; quality gates: build, unit/integration/architecture/e2e/security scan `[CẦN XÁC NHẬN pipeline thực tế]`.
- Runbook cho tenant provisioning, failed webhook/job, provider outage, restore và data request.

## 15.7 Localization

- Regions hiện hỗ trợ US và Europe; currency USD/EUR; country/state label thay đổi theo country.
- Lưu UTC, hiển thị tenant/user timezone; canonical timezone và DST policy `[CẦN XÁC NHẬN]`.
- UI language/i18n catalog và ngôn ngữ phát hành `[CẦN XÁC NHẬN]`.
- Money dùng ISO 4217; distance/volume/temperature dùng canonical storage và localized display `[CẦN XÁC NHẬN canonical units]`.
- Date, phone, address, VAT/MC/EORI theo region; state/region/province vẫn bắt buộc theo convention hiện tại.

---

# 16. Integration Requirements

| Hệ thống | Mục đích/Dữ liệu | Phương thức/Auth | Limit/Cost | Lỗi/Retry/Fallback/Sync |
|---|---|---|---|---|
| Stripe | Subscription, payment intent, invoice event | REST + webhook; secret/signature | `[CẦN XÁC NHẬN contract/cost]` | Idempotency; exponential backoff; webhook reconciliation |
| Stripe Connect | Tenant onboarding/destination charge/payout | OAuth/account API/webhook | Theo country/account | Không checkout nếu account chưa ready; status sync |
| Stripe Tax | Tax calculation | API key/REST | Theo Stripe | Manual tax fallback theo rule; lưu source/result |
| Mapbox | Map, geocode, Matrix route | API token/REST | Provider quota/cost | Retry idempotent; heuristic optimizer fallback; geocode manual input |
| Resend | Transactional email | API key/REST | Quota/cost TBD | Queue/retry; lưu delivery status; không rollback nghiệp vụ |
| Firebase | Push notification | Service credential | Quota TBD | Retry transient; disable invalid token; in-app fallback |
| Azure Blob/R2/local | Document/photo storage | SDK/key/signed URL | Capacity/egress TBD | Retry multipart; checksum; local chỉ dev/fallback policy TBD |
| NHTSA | VIN decode | Public REST | Availability/rate TBD | Cho nhập thủ công; hiển thị source/confidence |
| Samsara/Motive/Geotab/TT ELD | Driver/vehicle/HOS | REST/webhook/credential | Provider-specific | Signature; mapping; scheduled sync; keep last known value |
| DAT/Truckstop/123Loadboard | Listing search/book/post truck | REST/credential | Contract/cost TBD | Provider abstraction; demo provider; dedupe by provider listing ID |
| Anthropic/OpenAI/DeepSeek | AI dispatch/PDF extraction | API key/REST | Token/model pricing; plan quota | Provider factory; fail session; không tự execute unclear result |
| Google reCAPTCHA | Public form protection | Site/secret verify | Quota TBD | Fail closed cho public create; accessible alternative `[CẦN XÁC NHẬN]` |
| SignalR | Internal realtime tracking/chat/notification | WebSocket/Bearer | Infra limit TBD | Reconnect/backoff; fetch snapshot after reconnect |
| Telegram | Notification/bot interaction | Bot API/token | Quota TBD | Retry; opt-in; không gửi sensitive payload |

Mọi integration phải có: timeout rõ, retry chỉ khi an toàn, circuit breaker khi phù hợp, correlation/provider request ID, credential rotation, health metric và runbook. Chi phí, quota và data residency của từng provider đều `[CẦN XÁC NHẬN]` trước production.

---

# 17. Reporting Requirements

| Report ID | Báo cáo | KPI/Cách tính | Nguồn | Actor/Filter | Refresh/Export |
|---|---|---|---|---|---|
| RP-01 | Operations Dashboard | Active/unassigned loads, idle trucks, map | Load/Truck/location | Manager/Dispatcher; date/status | Near-real-time `[CẦN XÁC NHẬN]` |
| RP-02 | Load Performance | Revenue, avg/load, delivery rate, on-time | Load | Manager; date/customer/type | On request; export TBD |
| RP-03 | Driver Report | loads, distance, on-time, efficiency | Load/Trip/Employee | Manager; driver/date | On request |
| RP-04 | Truck Report | utilization, mileage, maintenance cost | Truck/Trip/Maintenance | Manager; truck/date | On request |
| RP-05 | Financials | invoiced, paid, due, overdue, collection, profit/margin | Invoice/Payment/Expense | Owner/Finance; date/currency | On request; sensitive permission |
| RP-06 | Payroll | total/outstanding/average/history | PayrollInvoice/Payment | Finance/Manager; employee/period | On request |
| RP-07 | Expense | total/category/month/top trucks | Expense | Finance/Manager; type/date/truck | On request |
| RP-08 | Customer | volume, revenue, activity | Customer/Load/Invoice | Manager; customer/date | On request |
| RP-09 | Compliance | HOS violations, pending DVIR, expiring licenses | HOS/DVIR/License | Compliance/Manager | Near-real-time/daily |
| RP-10 | AI Usage | sessions, outcomes, cost/quota/model | AI session/decision | Owner/Admin | Weekly/current |

Quy tắc báo cáo: filter/timezone/currency phải hiển thị; không cộng khác currency nếu chưa quy đổi; division-by-zero trả 0/N/A; dữ liệu export tuân permission như UI; PII được tối thiểu hóa. Excel/PDF export và lịch gửi report chưa thấy contract, do đó `[CẦN XÁC NHẬN]`.

---

# 18. Testing Specification

## 18.1 Test scenarios theo chức năng

| Nhóm | Positive | Negative/Boundary | Permission/Security | Integration/Performance |
|---|---|---|---|---|
| Auth/Tenant | Login đúng, tenant đúng | token expired, header mismatch | cross-tenant/privilege escalation | Identity outage, login rate |
| Master Data | CRUD hợp lệ | duplicate, max length, dependency | role matrix/IDOR | VIN timeout; list p95 |
| Load | Draft→Delivered | dates/hazmat/state/duplicate | unauthorized state change | PDF/notification failure; concurrency |
| Trip | optimize/arrive/complete | stop order, conflict, cancel terminal | driver only assigned trip | Mapbox fallback; many stops |
| Driver App | location/status/POD | no proximity/offline/oversize | other-driver IDOR | reconnect/upload retry/battery `[CẦN XÁC NHẬN]` |
| Finance | tax/issue/partial/full | rounding, >due, duplicate | public token leakage/finance role | Stripe fail/out-of-order webhook/load |
| Compliance | sync/submit/review | signature/required fields/expiry | reviewer separation | provider timeout/bulk sync |
| Document/Chat | upload/send/read | MIME/size/nonparticipant | malicious file/XSS/token | blob/SignalR outage/throughput |
| AI/MCP | HITL approve/replan | quota/stale/invalid tool input | API key tenant escape/autonomous permission | LLM fail/rate/cost cap |
| Reports | KPI/filter/empty | boundary date/DST/zero | finance/tenant leakage | large range/query p95 |
| Subscription | activate/upgrade/cancel | duplicate event/past due | plan bypass | Stripe outage/reconciliation |
| Privacy | export/delete/cancel | legal hold/duplicate/expiry | cross-user export | large export/job recovery |

## 18.2 Test cases trọng yếu

| Test Case ID | Chức năng | Pre-condition | Test Steps/Test Data | Expected Result |
|---|---|---|---|---|
| TC-001 | Tenant isolation | User tenant A, record tenant B | GET/PUT bằng B ID/header | 403/404; không leak/mutation; security log |
| TC-002 | Token expiry | Expired access token | Call protected GET/POST | 401; POST không thực thi |
| TC-003 | Create load | Customer/address hợp lệ | POST valid DTO | Draft, unique number, audit |
| TC-004 | Load dates | pickup 17/7; delivery 16/7 | POST/PUT | 422 field error |
| TC-005 | Load state | Load Delivered | Cancel/dispatch | 409/422, no change |
| TC-006 | Hazmat eligibility | Hazmat load, driver thiếu endorsement | Eligibility/dispatch | Hard block, reason cụ thể |
| TC-007 | Driver proximity | Dispatched load, driver xa stop | Confirm pickup | Rejected, unchanged |
| TC-008 | Trip constraint | DropOff order trước Pickup | Create/optimize | Rejected/corrected; pickup first |
| TC-009 | Route fallback | Mapbox timeout | Optimize | Heuristic route, source flagged |
| TC-010 | Trip completion | All but last drop-off arrived | Mark last arrive | Trip Completed; related load Delivered |
| TC-011 | Payment partial | Total 100, paid 0 | Pay 40 + success webhook | Payment 40; invoice Partial; due 60 |
| TC-012 | Overpayment | Due 60 | Request 61 | 422, no intent |
| TC-013 | Webhook duplicate | Event X processed | Replay X | 2xx idempotent; one payment |
| TC-014 | Public link expired | ExpiresAt past | Open URL | No invoice/customer PII |
| TC-015 | Payroll overlap | Existing 1–7 Jul | Generate 5–10 Jul | Conflict/no duplicate |
| TC-016 | ELD signature | Invalid HMAC | POST webhook | Reject/no mutation/security log |
| TC-017 | Malicious upload | executable renamed `.jpg` | Upload POD | Reject by content; no public blob |
| TC-018 | Chat authorization | Nonparticipant user | GET/send conversation | 403/404 |
| TC-019 | AI HITL | Valid suggestion | Run without approve | No assignment mutation |
| TC-020 | AI stale decision | Load assigned after proposal | Approve old decision | Revalidation failure; no overwrite |
| TC-021 | MCP tenant | API key A | Tool query with B reference | Reject/audit/rate unaffected correctly |
| TC-022 | Report empty | Date has no data | Load report | 0/N/A, no divide exception |
| TC-023 | Feature gate | Plan lacks AI | Direct POST AI run | 403 feature disabled, no LLM call |
| TC-024 | Privacy export | User A and B data | Request A export | Only A/authorized tenant scope |

## 18.3 Entry/Exit Criteria

- Entry: requirement/AC baselined; test data/tenant/provider sandbox ready; schema/API version identified.
- Exit `[ĐỀ XUẤT]`: 100% Must AC tested; no open Critical/High; tenant isolation/payment/webhook/state-machine suites pass; agreed performance/security gates pass; UAT sign-off.
- Regression: unit/domain state rules; handler/integration DB; contract/API; e2e portal/mobile critical path; provider sandbox; architecture tests.

---

# 19. Risks

| Risk ID | Nhóm/Rủi ro | Nguyên nhân | Ảnh hưởng | Khả năng | Phương án xử lý |
|---|---|---|---|---|---|
| R-01 | Data: sai đơn vị distance | Unit chưa được xác định | Payroll/HOS/KPI sai | Cao | Chốt canonical unit, migrate/label/test |
| R-02 | Security: cross-tenant leak | Header/claim/cache/query sai scope | Nghiêm trọng | Trung bình | Central tenant resolution, IDOR tests, tenant-key cache |
| R-03 | Finance: duplicate/out-of-order webhook | Distributed delivery semantics | Ghi nhận tiền sai | Trung bình | Event idempotency, state reconciliation, unique constraint |
| R-04 | Legal: regional compliance sai | US/EU rule khác; scope pháp lý mơ hồ | Phạt/rủi ro khách hàng | Cao | Legal review, rule version/effective date, disclaimer |
| R-05 | AI autonomous unsafe | Context stale/tool partial failure | Sai assignment/dispatch | Trung bình | Default HITL, revalidate before execute, compensation/audit |
| R-06 | Integration outage | Nhiều provider critical | Gián đoạn | Cao | Timeout/circuit/retry/fallback/last-known/runbook |
| R-07 | Performance: EF lazy loading N+1 | Query graph lớn | API/report chậm | Cao | Projection/query profiling/load test |
| R-08 | Maintainability: missed SaveChanges | Handler tự commit | Mất mutation im lặng | Trung bình | Handler tests/review/analyzer `[ĐỀ XUẤT]` |
| R-09 | Tenant migration scale | DB per tenant | Deploy lâu/mixed schema | Trung bình | Migration orchestrator, canary/batch/status/rollback |
| R-10 | Mobile offline | Network đường dài không ổn định | Mất location/POD/status | Cao | Chốt offline queue/idempotency/conflict UX |
| R-11 | Privacy/retention | Chưa có policy chi tiết | Xóa thiếu/quá mức | Cao | Data inventory, legal hold/retention matrix, audit |
| R-12 | Security upload | File độc hại/public URL | Malware/data breach | Trung bình | Content scan/quarantine/signed URL/limits |
| R-13 | Scope/timeline | Platform quá rộng, input không có deadline/budget | Trễ/không hoàn thành | Cao | MVP gate, release train, dependency map, change control |
| R-14 | Data quality | Manual/import/provider khác schema | Quyết định/report sai | Cao | Validation, provenance, confidence, reconciliation |
| R-15 | Human resources | Domain trucking/compliance phức tạp | Hiểu sai rule | Trung bình | SME review/UAT/training |
| R-16 | Operational observability | Job/webhook/tenant failure khó thấy | MTTR cao | Trung bình | Tenant dashboards, correlation, alert, DLQ/replay |
| R-17 | Budget/provider cost | LLM/maps/storage/ELD fee chưa chốt | Margin thấp | Trung bình | Usage metering, caps, cost alerts, plan economics |
| R-18 | Security: subscription mutation thiếu authorization rõ ở controller | `cancel`, `change-plan`, `renew` không có attribute auth tại controller/action; có thể chỉ được bảo vệ bởi global policy nhưng chưa có bằng chứng | Thay đổi subscription trái phép | Cao | Xác minh global fallback policy; thêm explicit authorization và integration tests trước release |

---

## 19.1 Kế hoạch triển khai

| Giai đoạn | Công việc | Deliverable | Owner | Dependency | Trạng thái |
|---|---|---|---|---|---|
| 1 Requirement Gathering | Workshop business/legal/operations/finance; chốt open questions | BRD inputs, glossary, KPI baseline | BA/PO/SME | Stakeholder | `[CẦN XÁC NHẬN]` |
| 2 Business Analysis | Baseline process/rules/state/permission | Approved specification/backlog | BA/PO/Architect | Phase 1 | Baseline draft này |
| 3 UI/UX Design | IA, wireframe, prototype, accessibility | Design specs/prototype | UX/PO | Approved flows | `[CẦN XÁC NHẬN]` |
| 4 Database Design | ERD, retention, indexes, migration | Schema/migration plan | Architect/Backend/DBA | Rules | Existing + gaps |
| 5 Backend Development | Domain/handlers/API/jobs/integrations | Versioned API/OpenAPI/tests | Backend | DB/contracts | Existing + refinement |
| 6 Frontend Development | TMS/Admin/Customer/website/mobile | Portal/app builds | FE/Mobile | API/design | Existing + refinement |
| 7 Integration | Sandbox/provider/webhooks/reconciliation | Integration test evidence | Backend/DevOps | Provider accounts | `[CẦN XÁC NHẬN]` |
| 8 Testing | Functional/security/performance/regression | Test report/defect status | QA/Security | Stable build | `[CẦN XÁC NHẬN]` |
| 9 UAT | Business scenario/customer validation | UAT sign-off | PO/SME/Users | QA exit | `[CẦN XÁC NHẬN]` |
| 10 Deployment | Migration/canary/rollback/release | Production release/runbook | DevOps/PM | UAT/SLA | `[CẦN XÁC NHẬN]` |
| 11 Monitoring | SLO/alert/provider/job/tenant health | Dashboard/alerts | DevOps | Production | `[CẦN XÁC NHẬN]` |
| 12 Maintenance | Support, patch, roadmap, retention | Release cadence/SLA | PM/Product/Tech | Operations | `[CẦN XÁC NHẬN]` |

---

# 20. MVP

## 20.1 Đề xuất MVP

`[ĐỀ XUẤT]` MVP tập trung vào một luồng tạo doanh thu hoàn chỉnh, chưa bao gồm toàn bộ capability đã có trong repository.

**Bắt buộc:** auth/tenant/RBAC; customer/employee/driver/truck; load/trip; driver mobile active trip/location/status/POD; document/notification cơ bản; invoice/public payment; dashboard vận hành tối thiểu; audit/logging/backups/security baseline.

**Có thể trì hoãn:** container/terminal nếu khách hàng MVP không intermodal; payroll/expense/tax nâng cao; ELD provider ngoài; safety/maintenance đầy đủ; load board; AI/MCP; advanced reports; marketing CMS; privacy automation ngoài yêu cầu pháp lý của launch market.

**Thử nghiệm:** AI Dispatch chỉ HITL và feature flag; một ELD provider; một load board provider; route optimization Mapbox với heuristic fallback.

**Definition of Done:** critical path Create Load→Dispatch Trip→Driver Delivery/POD→Issue/Pay Invoice chạy được; tenant isolation/payment/security tests pass; monitoring/runbook/backup-restore evidence; UAT sign-off; không Critical/High defect.

**KPI MVP:** activation tenant, time-to-first-load, dispatch cycle time, % digital status/POD, invoice cycle/collection, weekly active dispatchers/drivers, critical error rate, support tickets. Target cụ thể `[CẦN XÁC NHẬN]`.

---

# 21. Roadmap

| Phase | Chức năng | Mục tiêu | Priority | Dependency |
|---|---|---|---|---|
| Phase 1 — MVP | Identity/tenant/master data/load/trip/driver/POD/invoice/payment/basic dashboard | Chứng minh end-to-end value | Must | Core infra/provider sandbox |
| Phase 2 — Improvement | Container, payroll/expense/tax, ELD/HOS, DVIR/safety, maintenance, advanced reports | Tăng depth/compliance | Should | SME/legal/data quality |
| Phase 3 — Expansion | Load boards, AI HITL/MCP, more providers/regions, customer experience | Tăng productivity/market | Could | Feature/quota/economics |
| Phase 4 — Optimization | Autonomous AI có guardrail, read models/cache, predictive analytics, DR/scale | Scale và tối ưu | Could | Mature telemetry/governance |

Timeline, team capacity và release dates `[CẦN XÁC NHẬN]`; không nên gắn ngày trước khi estimate theo backlog/DoR.

---

# 22. Open Questions

| ID | Loại | Nội dung cần xác nhận | Lý do | Người trả lời | Ảnh hưởng |
|---|---|---|---|---|---|
| OQ-01 | Business | Launch market/persona/vertical ưu tiên là US freight, EU, intermodal hay vehicle transport? | Chốt MVP/rules | Sponsor/PO | Rất cao |
| OQ-02 | Business | KPI baseline/target, pricing và overage AI? | Đo thành công/economics | Sponsor/Product/Finance | Cao |
| OQ-03 | Business | Timeline, budget, team capacity, release constraints? | Planning | Sponsor/PM | Rất cao |
| OQ-04 | User | Finance và Compliance dùng role mới hay permission trên Manager/Owner? | RBAC/UI/test | Product/Security | Cao |
| OQ-05 | User | Driver offline queue, conflict và retry UX như thế nào? | Mạng thực địa | Driver SME/Product | Cao |
| OQ-06 | Technical | Canonical unit cho distance, weight, volume, temperature? | Payroll/HOS/report | Architect/Product | Rất cao |
| OQ-07 | Technical | API versioning, paging, idempotency, concurrency token chuẩn? | Contract/retry | Architect/API lead | Cao |
| OQ-08 | Technical | SLO, RPS/concurrency/data volume, file limits? | NFR/test/infra | Architect/DevOps/Product | Cao |
| OQ-09 | Technical | Multi-instance SignalR backplane và job locking dùng gì? | Scale/reliability | Architect/DevOps | Trung bình |
| OQ-10 | Security | Password/MFA/lockout/token/session/rate-limit policy? | Account security | Security/Product | Rất cao |
| OQ-11 | Security | Upload scanning, signed URL, encryption/key rotation? | Document security | Security/DevOps | Cao |
| OQ-12 | Legal | HOS/ELD, ADR/hazmat, tax outputs có phải legal system of record? | Liability | Legal/Compliance | Rất cao |
| OQ-13 | Legal | GDPR/CCPA retention, legal hold, deletion/anonymization matrix? | Privacy | DPO/Legal | Rất cao |
| OQ-14 | Data | Soft/hard delete và cascade theo từng entity? | Integrity/audit | Product/DBA/Legal | Cao |
| OQ-15 | Data | Number sequence unique theo tenant/năm/type? | Invoice/load identity | Finance/Product | Cao |
| OQ-16 | Operation | Backup frequency, retention, RPO/RTO, DR region, restore ownership? | Continuity | DevOps/Sponsor | Rất cao |
| OQ-17 | Operation | Provider outage/manual fallback/reconciliation ownership? | Incident response | Ops/Product | Cao |
| OQ-18 | Business | On-time delivery dùng requested delivery hay công thức distance hiện tại? | KPI đúng | Product/Operations | Cao |
| OQ-19 | Finance | Currency conversion, tax rounding, credit/refund/chargeback workflow? | Finance correctness | Finance/Legal | Rất cao |
| OQ-20 | Product | MVP có bắt buộc container, ELD, AI, load board không? | Scope | Sponsor/PO | Rất cao |
| OQ-21 | Technical | Có yêu cầu xuất Excel/PDF và scheduled reports không? | API/UI/effort | Product | Trung bình |
| OQ-22 | User | Browser/OS/mobile minimum/accessibility target? | QA/design | Product/UX | Trung bình |
| OQ-23 | Security | Impersonation cần reason, approval, max duration và user notification? | Privileged access | Security/Support | Cao |
| OQ-24 | Operation | Tenant provisioning failure cleanup/retry và migration rollout policy? | SaaS operations | Architect/DevOps | Cao |

---

# 23. Traceability Matrix

| Business Requirement | Functional Requirement | User Story | Use Case | Test Case |
|---|---|---|---|---|
| BRQ-01 Cô lập và truy cập an toàn | FR-01/02/14 | US-01/02/03/18 | UC-01/02/14 | TC-001/002/023 |
| BRQ-02 Quản lý dữ liệu vận hành | FR-03/04/05/07 | US-04/05/06/09 | UC-03/04/05/07 | TC-003–010 |
| BRQ-03 Driver thực thi real-time | FR-06/11 | US-07/08/14/15 | UC-06/11 | TC-006/007/017/018 |
| BRQ-04 Billing và đối soát | FR-08/09/14 | US-10/11/12/18 | UC-08/09/14 | TC-011–015/023 |
| BRQ-05 Compliance/an toàn | FR-03/04/10 | US-04/05/13 | UC-03/04/10 | TC-006/016 |
| BRQ-06 Cộng tác/chứng từ/visibility | FR-11 | US-08/14/15 | UC-06/11 | TC-014/017/018 |
| BRQ-07 Hỗ trợ quyết định/automation | FR-12/13 | US-16/17 | UC-12/13 | TC-019–022 |
| BRQ-08 Quyền riêng tư/marketing | FR-15 | US-19 | UC-15 | TC-024 + cases marketing TBD |

---

# 24. Final Evaluation

## 24.1 Mức hoàn thiện

| Nhóm | Điểm 1–10 | Đã đủ | Còn thiếu/rủi ro hiểu sai | Có thể phát triển? |
|---|---:|---|---|---|
| Product overview/scope | 7 | Domain và components rõ | Market/MVP/budget/time | Chỉ core đã rõ |
| Stakeholder/roles/persona | 6 | 7 role và nhu cầu chính | Persona research; Finance/Compliance mapping | RBAC cần confirmation |
| Business process/rules | 8 | Load/trip/payment/payroll/AI rules khá rõ | AS-IS khách hàng, unit/KPI/legal | Core state flow có thể |
| Functional specification | 8 | 15 capability groups + AC | Per-endpoint edge rules và exact validator limits | Refinement được |
| UI/UX | 6 | Screen catalog/states/flows | Approved IA/wireframe/responsive/a11y | Chưa nên pixel-build mới |
| Data | 7 | DB split, entities, core relations | Full column dictionary/index/cascade/retention | Core schema có sẵn; gap cần chốt |
| API | 6 | Endpoint inventory/contract/error | OpenAPI exact schema/example/version/idempotency | Endpoint hiện có; contract freeze chưa |
| NFR/Security | 5 | Risk/control areas rõ | SLA/load/RPO/RTO/MFA/file limits | Không sign-off production |
| Integration | 7 | Provider/purpose/fallback tổng quan | Contracts/cost/quota/credential/data residency | Sandbox refinement được |
| Reporting | 7 | KPI baseline/report catalog | Formula approval/export/refresh/currency | Basic report có thể |
| Testing/traceability | 8 | Scenarios, 24 core cases, mapping | Full test data/automation/perf thresholds | QA planning được |
| MVP/Roadmap/Plan | 6 | Phasing hợp lý | Sponsor scope/capacity/dates | Chờ quyết định OQ-01/03/20 |
| Legal/Privacy | 4 | Capability/risk identified | Jurisdiction/policy/retention/legal hold | Chưa nên release pháp lý |
| Tổng thể | 6.7 | Baseline tốt cho discovery/refinement | Không phải scope/SLA/legal sign-off | Core development tiếp tục được; production gate chưa đủ |

## 24.2 Kết luận quyết định

**Có thể đưa vào phát triển/kiểm thử ngay:** state machine Load/Trip/Container; tenant isolation; core CRUD; eligibility guard; partial payment/idempotent webhook; AI HITL guardrail; error/audit patterns.

**Cần stakeholder xác nhận trước khi freeze:** MVP market/scope, permission matrix, canonical units, finance/tax/refund, offline mobile, SLA/performance, retention/privacy/legal, provider contracts và exact API/OpenAPI.

**Chưa nên phát triển hoặc phát hành production như cam kết:** autonomous AI mặc định, cross-region legal claims, hard-delete/privacy automation, scale/DR guarantee và scheduled/export reporting khi chưa có các quyết định tương ứng.

## 24.3 Nguồn bằng chứng chính

- Repository guidance và feature inventory: `CLAUDE.md`, `.claude/feature-map.md`.
- Nghiệp vụ hiện có: `docs/business-spec.md`, `docs/features.md`, `docs/ai-dispatch.md`, `docs/invoices.md`, `docs/customer-payments.md`, `docs/stripe-connect.md`.
- Kiến trúc/dữ liệu/API: `docs/architecture/*`, `docs/api/*`.
- Implementation evidence: `src/Core/Logistics.Domain`, `src/Core/Logistics.Application`, `src/Presentation/Logistics.API/Controllers`, các portal Angular và Driver App.
