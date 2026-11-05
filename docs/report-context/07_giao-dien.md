# 07. Giao diện và route frontend

## 7.1 Quy ước đọc

Frontend được khảo sát ở sibling repository `/home/vumoi/logictics-app`. Route không đồng nghĩa mọi action CRUD được bật; router lọc theo allowlist backend và feature/capability flags. Các route bảo vệ bởi ProtectedRoute, TenantGuard, MainLayout và ResourceAccessBoundary theo cấu hình tương ứng. (nguồn: `/home/vumoi/logictics-app/src/router/AppRouter.tsx:170-239`, `/home/vumoi/logictics-app/src/pages/resourceRegistry.ts:98-153`)

API ở bảng dưới là collection/custom endpoint được khai báo bởi resource registry hoặc hook/API adapter. Các màn hình detail/action có thể gọi thêm endpoint con, được nêu khi xác định được từ source. Use Case nối với danh mục UC trong file 02; mã không thấy định nghĩa trực tiếp trong code được ghi [SUY LUẬN] theo tác vụ/màn hình.

## 7.2 Route đặc biệt

| Route | Page / mục đích | UC liên quan | Thành phần chính | API/data |
|---|---|---|---|---|
| `/login` | Đăng nhập/khởi động xác thực | UC-01 | LoginPage | Lark authorization/login; Lark callback endpoint. (nguồn: `/home/vumoi/logictics-app/src/router/AppRouter.tsx:148-166`, `/home/vumoi/logictics-app/src/constants/routes.ts:14-20`, backend `src/main/java/com/company/logicstic/integration/lark/auth/LarkAuthController.java:17-60`) |
| `/auth/callback` | Nhận callback và hoàn tất session | UC-01 | LarkCallbackPage | Callback backend; query/state theo flow auth. (nguồn: `/home/vumoi/logictics-app/src/router/AppRouter.tsx:164-166`, `/home/vumoi/logictics-app/src/pages/auth/LarkCallbackPage.tsx:9-19`) |
| `/select-tenant` | Màn hình tenant và khởi tạo xác thực lại | UC-02 | SelectTenantPage | Không tìm thấy API backend riêng để liệt kê tenant/chuyển tenant. Frontend identity hiện dựng danh sách một tenant từ claim hiện hành; đổi tenant bắt đầu lại xác thực để Identity Provider cấp tenant trong JWT. (nguồn: `/home/vumoi/logictics-app/src/router/AppRouter.tsx:170-172`, `/home/vumoi/logictics-app/src/hooks/useTenantList.ts:15-28`, `/home/vumoi/logictics-app/src/hooks/useSwitchTenant.ts:23-65`, `/home/vumoi/logictics-app/src/providers/auth/currentUser.ts:91-99`, `src/main/java/com/company/logicstic/controller/CurrentUserController.java:34-38`) |
| `/dashboard` | Executive dashboard cũ; hiện chuyển sang Operations | UC-11 [SUY LUẬN] | DashboardPage chỉ mount nếu executive contract confirmed | `EXECUTIVE_CONTRACT_CONFIRMED=false`, do đó router chuyển `/dashboard` sang `/operations`. (nguồn: `/home/vumoi/logictics-app/src/router/AppRouter.tsx:175-180`, `/home/vumoi/logictics-app/src/features/executive/config/executive.contract.ts:1-5`) |
| `/operations` | Dashboard điều hành | UC-11 [SUY LUẬN] | OperationsDashboardView, KPI strip, PriorityShipmentCard, OperationsBrief, NetworkMiniMap, DispatchTimeline | `useDashboardData`; các endpoint report, loads, trips, trucks theo API constants. Một số KPI để unavailable khi thiếu field; data mẫu chỉ bật riêng. (nguồn: `/home/vumoi/logictics-app/src/pages/dashboard/OperationsDashboardPage.tsx:7-31`, `/home/vumoi/logictics-app/src/features/dashboard/components/OperationsDashboardView.tsx:21-103`, `/home/vumoi/logictics-app/src/features/dashboard/api.ts:62-79`) |
| `/optimization` | Chạy tối ưu và so sánh candidate | UC-08 [SUY LUẬN] | OptimizationPage | POST `/api/optimization/runs`; GET `/api/optimization/runs/{id}`; POST accept assignment. (nguồn: `/home/vumoi/logictics-app/src/router/AppRouter.tsx:182-183`, `/home/vumoi/logictics-app/src/features/optimization/api/optimization.api.ts:1-5`) |
| `/optimization/runs/:id` | Chi tiết kết quả optimization | UC-08 [SUY LUẬN] | OptimizationRunShowPage | GET run; accept candidate endpoint. (nguồn: `/home/vumoi/logictics-app/src/router/AppRouter.tsx:182-183`, `/home/vumoi/logictics-app/src/features/optimization/api/optimization.api.ts:1-5`) |
| `/ai-dispatch`, `/ai-dispatch/:id`, `/ai-dispatch/show/:id` | Tạo/chọn run, xem session và xét decision | UC-07 | AiDispatchPage | `/api/ai/dispatch/runs`, `/sessions`, `/pending`, approve/reject/cancel/config/features. (nguồn: `/home/vumoi/logictics-app/src/router/AppRouter.tsx:185-187`, `/home/vumoi/logictics-app/src/features/ai-dispatch/api/aiDispatch.api.ts:11-24`) |
| `/reports/fleet` | Báo cáo đội xe | UC-11 [SUY LUẬN] | FleetReportPage | fleet policies, trucks, fleet report API. (nguồn: `/home/vumoi/logictics-app/src/router/AppRouter.tsx:184`, `/home/vumoi/logictics-app/src/pages/fleet/FleetReportPage.tsx:19-30`, `/home/vumoi/logictics-app/src/features/dashboard/api.ts:65-75`) |
| `/finance/profitability` | Phân tích profitability | UC-11 [SUY LUẬN] | ProfitabilityPage | backend profitability summary/report endpoints; feature boundary `PROFITABILITY_VIEW`. (nguồn: `/home/vumoi/logictics-app/src/router/AppRouter.tsx:188-192`, `/home/vumoi/logictics-app/src/pages/profitability/ProfitabilityPage.tsx:19-25`) |
| `/settlements`, `/settlements/:id` | Danh sách và chi tiết settlement | UC-14 [SUY LUẬN] | SettlementListPage, SettlementShowPage | `/api/driver-settlements` và action endpoints; custom query API. (nguồn: `/home/vumoi/logictics-app/src/router/AppRouter.tsx:193-204`, `/home/vumoi/logictics-app/src/pages/settlements/SettlementShow.tsx:71-83`, `src/main/java/com/company/logicstic/controller/DriverSettlementController.java:20-40`) |
| `/settlements/policies`, `/settlements/policies/:id` | Driver pay policy list/version detail | UC-14 [SUY LUẬN] | DriverPayPolicyListPage, DriverPayPolicyShowPage | `/api/driver-pay-policies` and new-version command. (nguồn: `/home/vumoi/logictics-app/src/router/AppRouter.tsx:205-219`, `src/main/java/com/company/logicstic/controller/DriverPayPolicyController.java:1-30`) |
| `/payroll`, `/payroll/:id`, `/payroll/reconciliation` | Payroll list/run detail/reconciliation | UC-14 [SUY LUẬN] | PayrollEntryPage, PayrollRunShowPage, PayrollReconciliationPage | `/api/payroll/**`; reconciliation route is guarded by `PAYROLL_RECONCILIATION_CONTRACT_CONFIRMED=true`. (nguồn: `/home/vumoi/logictics-app/src/router/AppRouter.tsx:223-225`, `/home/vumoi/logictics-app/src/features/payroll/api/payroll.api.ts:17`) |
| `/profile/payslips`, `/payslips/:id` | Xem danh sách/chi tiết phiếu lương | UC-14 [SUY LUẬN] | MyPayslipsPage, PayslipShowPage | `/api/driver/me/payslips`, `/api/payslips/{id}`, `/pdf`. (nguồn: `/home/vumoi/logictics-app/src/router/AppRouter.tsx:221-222`, `/home/vumoi/logictics-app/src/pages/payslips/MyPayslipsPage.tsx:20-29`, `src/main/java/com/company/logicstic/controller/PayslipController.java:15-30`) |
| `/403`, `/404`, `*` | Trạng thái bị cấm/không tìm thấy | UC phụ trợ [SUY LUẬN] | ForbiddenPage, NotFoundPage | Không gọi API nghiệp vụ. (nguồn: `/home/vumoi/logictics-app/src/router/AppRouter.tsx:167-168,244`) |
| `/diagnostics` | Diagnostics nếu `diagnosticsState` được truyền | UC phụ trợ [SUY LUẬN] | DiagnosticsPage | Chỉ khai báo route có điều kiện. (nguồn: `/home/vumoi/logictics-app/src/router/AppRouter.tsx:142-147`) |

`/profile` và `/fleet/mileage-attributions` có trong route constants, nhưng không thấy `Route` mount tương ứng trong AppRouter hiện tại. `MapPage.tsx` có trong source nhưng không mount thành route trong AppRouter. Vì vậy không mô tả chúng là màn hình đang truy cập được qua router. (nguồn: `/home/vumoi/logictics-app/src/constants/routes.ts:24-37`, `/home/vumoi/logictics-app/src/router/AppRouter.tsx:137-245`, `/home/vumoi/logictics-app/src/pages/map/MapPage.tsx:1-14`)

## 7.3 Resource routes hiện được lọc qua registry

Các CRUD pattern được tạo từ helper trong routes.ts: list, create, edit/:id, show/:id. Runtime chỉ giữ resource có backend collection contract và capability phù hợp. Bảng cho thấy resource/page family và tình trạng operation; API bên dưới lấy từ foundationApiResources. (nguồn: `/home/vumoi/logictics-app/src/constants/routes.ts:5-12,38-65`, `/home/vumoi/logictics-app/src/pages/resourceRegistry.ts:20-96,98-153`, `/home/vumoi/logictics-app/src/components/resources/resourceCapabilities.ts:11-27`)

| Resource page | Route pattern | Mục đích, UC liên quan | API |
|---|---|---|---|
| Loads | `/loads`, `/loads/create`, `/loads/show/:id`; `/loads/edit/:id` bị lọc vì versioned writes chưa xác minh | Tra cứu, tạo, xem Load; UC-04/05. | `/api/loads`; Load timeline `/api/loads/{id}/timeline`; optional pickup date/AI gaps. (nguồn: `/home/vumoi/logictics-app/src/pages/resourceRegistry.ts:20-44`, `src/main/java/com/company/logicstic/controller/LoadController.java:28-82`) |
| Trips | `/trips`, `/trips/create`, `/trips/show/:id`; edit bị lọc | Danh sách, tạo, xem Trip; UC-06. | `/api/trips`; execution command `/api/trip-stops/**`. (nguồn: `/home/vumoi/logictics-app/src/pages/resourceRegistry.ts:45-58`, `src/main/java/com/company/logicstic/controller/TripExecutionController.java:26-103`) |
| Trucks | `/trucks`, `/trucks/create`, `/trucks/show/:id`; edit bị lọc | Fleet master; UC-15/24. | `/api/trucks`. (nguồn: `/home/vumoi/logictics-app/src/pages/resourceRegistry.ts:59-67`, `src/main/java/com/company/logicstic/controller/TruckController.java:30-80`) |
| Customers | `/customers`, `/customers/create`, `/customers/edit/:id`, `/customers/show/:id` | Master khách hàng; UC-03. | `/api/customers`. (nguồn: `/home/vumoi/logictics-app/src/pages/resourceRegistry.ts:68-78`, `src/main/java/com/company/logicstic/controller/CustomerController.java:30-80`) |
| Employees | `/employees`, create/edit/show | Nhân sự và Driver; UC-24. | `/api/employees`; driver projection `/api/drivers`. (nguồn: `/home/vumoi/logictics-app/src/pages/resourceRegistry.ts:79-89`, `src/main/java/com/company/logicstic/controller/EmployeeController.java:30-80`) |
| Invoices | `/invoices`, create/edit/show; delete không được bật | Tra cứu/tạo/sửa hóa đơn còn cho phép; UC-13. Mutability có kiểm tra draft. | `/api/invoices`, billing commands `/api/invoices/billing/**`. (nguồn: `/home/vumoi/logictics-app/src/pages/resourceRegistry.ts:90-101`, `src/main/java/com/company/logicstic/controller/BillingController.java:17-56`) |
| Payments | `/payments`, `/payments/show/:id`; create/edit phụ thuộc PAYMENT_COMMANDS_RUNTIME_VERIFIED, delete=false | Tra cứu payment; ghi rõ command contract đang khóa ở source. | `/api/payments`, payroll payment khác ở `/api/payroll/**`. (nguồn: `/home/vumoi/logictics-app/src/components/resources/resourceCapabilities.ts:17-26`, `/home/vumoi/logictics-app/src/features/payments/api/paymentCommands.ts:10-15`) |
| Documents | `/documents`, `/documents/show/:id`; không tạo/sửa | Tra cứu metadata và detail; UC-09. Không có upload UI generic vì thiếu multipart adapter. | `/api/documents` (GET list/detail, DELETE). (nguồn: `/home/vumoi/logictics-app/src/pages/resourceRegistry.ts:120-127`, `src/main/java/com/company/logicstic/controller/DocumentController.java:24-55`) |
| Notifications | `/notifications`, `/notifications/show/:id`; không create/edit/delete | Tra cứu và mark-read; UC-10. | `/api/notifications`; POST mark-all-read. (nguồn: `/home/vumoi/logictics-app/src/pages/resourceRegistry.ts:128-138`, `src/main/java/com/company/logicstic/controller/NotificationController.java:21-50`) |
| Terminals | list/create/edit/show theo generic capability | Quản lý terminal; UC-24. | `/api/terminals`. (nguồn: `/home/vumoi/logictics-app/src/pages/resourceRegistry.ts:139-153`, `src/main/java/com/company/logicstic/controller/TerminalController.java:30-80`) |

Các resource UI-only như accidents, containers, expenses, maintenance, load-board, products, HOS/ELD và DVIR có page definitions ở pages registry nhưng không nằm trong allowlist foundation API resource hiện tại; không tính chúng là route đã mount ở runtime registry. (nguồn: `/home/vumoi/logictics-app/src/pages/index.ts:197-274`, `/home/vumoi/logictics-app/src/pages/resourceRegistry.ts:20-104,139-153`)

## 7.4 Các màn hình đề xuất chụp cho báo cáo

Đây là checklist thu thập ảnh, không phải khẳng định các trạng thái đã được chụp. Dữ liệu đề xuất phải là dữ liệu test/fixture có nhãn hoặc dữ liệu production đã ẩn danh và được phép dùng. Không dựng KPI hay vị trí giả như live.

| # | Màn hình / trạng thái cần chụp | Dữ liệu cần chuẩn bị | Gợi ý chú thích |
|---:|---|---|---|
| 1 | Login trước khi xác thực | Không cần dữ liệu nghiệp vụ | “Màn hình đăng nhập qua Lark” |
| 2 | Callback lỗi/state không hợp lệ | State hết hạn trong môi trường test | “Trạng thái lỗi xác thực” |
| 3 | Chọn tenant | Tài khoản/tenant test có quyền | “Chọn ngữ cảnh tenant” |
| 4 | Operations, LIVE, chưa có dữ liệu | API trả empty/unavailable | “Dashboard hiển thị dữ liệu chưa sẵn sàng” |
| 5 | Operations, LIVE, một KPI lỗi | Endpoint report lỗi test | “Lỗi riêng widget và retry” |
| 6 | Operations, preview mode | Dữ liệu preview có nhãn sample | “Bản xem thử — dữ liệu mẫu” |
| 7 | Operations, 320 px | Same preview fixture hoặc live empty | “Bố cục điều hành trên mobile” |
| 8 | Load list có filter | Load test fixture đã ẩn danh | “Tìm kiếm và lọc Load” |
| 9 | Create Load có lỗi validation | Bỏ trống field bắt buộc trong form test | “Validation biểu mẫu tạo Load” |
| 10 | Load detail, timeline đang tải/empty | Load fixture không có event | “Chi tiết Load và timeline rỗng” |
| 11 | Load detail, AI gaps | Gap test có trạng thái cần thao tác | “Gap cần dispatcher xử lý” |
| 12 | Trip list và detail | Trip test với 2-3 stops | “Trip và danh sách điểm dừng” |
| 13 | Trip stop transition lỗi | Transition không hợp lệ trong test env | “Không cho chuyển trạng thái sai bước” |
| 14 | AI Dispatch pending decisions | Session test từ backend, hiển thị nhãn status | “Recommendation chờ duyệt” |
| 15 | AI Dispatch approve/reject có lỗi conflict | Decision fixture stale/version conflict | “Feedback conflict khi duyệt decision” |
| 16 | Optimization candidate comparison | Run test có feasible và infeasible candidate | “So sánh kết quả optimization” |
| 17 | Optimization accept thành công / conflict | Test run/command có expected outcome | “Xác nhận candidate và xử lý xung đột” |
| 18 | Documents list empty + upload disabled/absent | Không đính kèm tài liệu thật | “Danh mục tài liệu hiện hỗ trợ đọc” |
| 19 | Notifications list | Notification test có unread/read | “Trạng thái thông báo” |
| 20 | Fleet report, payroll hoặc settlement | Test data tổng hợp, không chứa PII thật | “Báo cáo fleet / nghiệp vụ tài chính” |

Operations preview fixture được định nghĩa riêng và có copy báo dữ liệu mẫu; route dashboard Executive đang redirect; MapPage không được mount. Những điểm này cần thể hiện trung thực trong ảnh và chú thích. (nguồn: `/home/vumoi/logictics-app/src/pages/dashboard/OperationsDashboardPage.tsx:7-31`, `/home/vumoi/logictics-app/src/components/dashboard/OperationsDashboardPreview.tsx:17-35,110-118`, `/home/vumoi/logictics-app/src/features/dashboard/components/OperationsDashboardView.tsx:50-103`, `/home/vumoi/logictics-app/src/features/executive/config/executive.contract.ts:1-5`, `/home/vumoi/logictics-app/src/pages/map/MapPage.tsx:6-14`)
