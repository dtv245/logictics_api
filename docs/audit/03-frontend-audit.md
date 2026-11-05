# 03 — Frontend audit

## 1. Kiến trúc và runtime

Frontend dùng React 18 + TypeScript + Vite + Refine v4 + React Router 6 + Ant Design 5. `App.tsx` nối auth provider, access-control provider, custom data provider, i18n, notifications và router. Features được chia theo domain, nhưng còn song song cấu trúc cũ `src/features/` và lớp `src/pages`, `src/providers`, `src/api`, `src/hooks`.

`dataProvider.ts` dùng allowlist collection path, whitelist filter/sort, validate envelope/pagination, lọc mutation fields và cấm generic mutation cho resource cần command adapter. Đây là bảo vệ tốt khỏi tự phát minh REST contract. ([`dataProvider.ts`](../../../logictics-app/src/providers/dataProvider.ts#L197), [`resourceRegistry.ts`](../../../logictics-app/src/pages/resourceRegistry.ts#L25))

Điểm cần lưu ý: [`pages/index.ts`](../../../logictics-app/src/pages/index.ts#L183) thêm `loadsResource` hai lần vào `appResources`; `createFoundationResources()` lọc/map nhưng không deduplicate. Vì vậy Refine nhận resource name `loads` lặp lại.

## 2. Routes, navigation và feature gates

- Runtime bảo vệ login/callback, protected route, tenant guard, resource access boundary và route cụ thể cho `/tms`, AI Dispatch, profitability, settlements, payroll và payslips. `/loads` list chuyển hướng sang `/tms/dispatch`.
- Runtime truyền `foundationResourcePageRoutes`, chỉ mount routes thuộc `foundationApiResources`; các page/resource code khác không đồng nghĩa với route đang dùng. Xem [`App.tsx`](../../../logictics-app/src/App.tsx#L203) và [`resourceRegistry.ts`](../../../logictics-app/src/pages/resourceRegistry.ts#L104).
- Có link `/optimization` trong TMS subnav và `routes.optimization`, nhưng `AppRouter` không đăng ký `OptimizationPage`/`OptimizationRunShow`. Nhấn link sẽ rơi vào wildcard NotFound. Fleet report/mileage pages/route constants tương tự chưa được mount.
- Payroll reconciliation hiện **được bật** trong source: `PAYROLL_RECONCILIATION_CONTRACT_CONFIRMED = true`; AppRouter dùng constant này để mount route.

## 3. Authentication, token và tenant context

Auth provider hỗ trợ Lark callback và OIDC gateway. Sau login, `createCurrentUserLoader()` gọi `/api/me` và yêu cầu subject, tenant, roles khớp với token identity; lệch thì clear session. Shared API client gắn bearer token, `X-Request-Id`, xác nhận envelope và single-flight refresh/401 replay một lần. ([`sessionManager.ts`](../../../logictics-app/src/providers/auth/sessionManager.ts#L135), [`currentUser.ts`](../../../logictics-app/src/providers/auth/currentUser.ts#L60), [`apiClient.ts`](../../../logictics-app/src/providers/api/apiClient.ts#L120))

OIDC gateway xác minh access token bằng remote JWK Set, sau đó giữ và gửi cùng access token tới `/api/me`/các API. Backend source hiện chỉ cho thấy filter xác minh bearer token qua internal HMAC validator của Lark, không thấy OIDC/JWK resource-server validator hoặc token-exchange endpoint. Do đó OIDC frontend path có triển khai, nhưng tương thích xác thực với backend chưa được chứng minh và có thể bị 401 nếu Identity Server không phát loại token mà backend validator chấp nhận. Lark login có luồng callback riêng để đổi code thành internal token. ([`jwtVerifier.ts`](../../../logictics-app/src/providers/auth/jwtVerifier.ts#L96), [`sessionManager.ts`](../../../logictics-app/src/providers/auth/sessionManager.ts#L135), backend [`LarkAuthenticationFilter.java`](../../src/main/java/com/company/logicstic/integration/lark/auth/LarkAuthenticationFilter.java#L40))

Lark session được lưu trong browser storage dưới `logistics_lark_session`, bao gồm access token, và được validate expiry khi bootstrap. Đây là token storage phía client, khác với HttpOnly cookie. ([`sessionManager.ts`](../../../logictics-app/src/providers/auth/sessionManager.ts#L22), [`sessionManager.ts`](../../../logictics-app/src/providers/auth/sessionManager.ts#L255))

`/select-tenant` không đọc danh sách từ tenant directory API. `/api/me` dựng `tenants: [tenant hiện tại]`; `useTenantList()` chỉ lấy mảng này. Nút đổi tenant không gửi tenant ID/header mà buộc đăng nhập lại và trông chờ IdP cấp token mới. Đây là flow re-authentication, chưa phải UI chọn tenant từ danh sách nhiều tenant. ([`currentUser.ts`](../../../logictics-app/src/providers/auth/currentUser.ts#L84), [`useTenantList.ts`](../../../logictics-app/src/hooks/useTenantList.ts#L15), [`useSwitchTenant.ts`](../../../logictics-app/src/hooks/useSwitchTenant.ts#L23))

Role matrix fail-closed và role guard trên route phục vụ UX. Backend vẫn là nguồn authorization cuối.

## 4. Module và API integration

| Module | Trạng thái frontend source |
|---|---|
| Dashboard/Operations | `/tms` là màn vận hành runtime; queries gọi loads/trips/trucks, reports và AI endpoints. Dashboard cũ không phải route mặc định hiện tại. |
| Loads | TMS dispatch/shipment detail và load form có adapter/API thật; list `/loads` redirect vào dispatch. API core được runtime allowlist. |
| Trips | CRUD resource và trip execution hooks cho assign/unassign driver, stops, arrive/start/complete/depart. |
| AI Dispatch/gaps | Page và adapters gọi endpoint thật; có component trong TMS. Bị giới hạn bởi backend approval gate và provider config; legacy mock flag tồn tại nhưng default false. |
| Optimization | Component/API có thật nhưng route hiện chưa nối vào router. |
| Fleet/drivers | Truck/driver CRUD, driver profile và một số HR/credential subresources dùng API thật. Fleet reporting/mileage attribution page chưa mount. |
| Map/tracking | `/tms/live-tracking` được mount, đọc tọa độ truck/load và vẽ map; route estimate/geocode gọi external map services. Không thấy client telemetry/WebSocket stream. `MapPage` độc lập không nằm trong router hiện tại. |
| Documents | Runtime read-only. Upload/create/edit bị loại khỏi resource config; download adapter yêu cầu `/api/documents/{id}/download` nhưng backend không có route đó. |
| Notifications | Runtime read/action scope; list/mark-all-read có contract. Generic create/edit/delete không được bật. |
| Finance | Invoice billing/rating, payments, profitability, settlements, payroll và payslips có API adapters/queries. Reconciliation route hiện enabled. |
| Chưa đưa vào runtime | Maintenance, HOS/ELD, DVIR, accidents, containers, load-board và products có resource/page source, nhưng không thuộc foundation API allowlist; AppRouter runtime lọc chúng ra. |
| Expenses | Các page/resource/test của expenses bị xóa khỏi working tree hiện tại; không có route frontend đang dùng. |

## 5. Mock/demo/fixture và API transport

[`vite.config.ts`](../../../logictics-app/vite.config.ts#L67) khi dev proxy không kết nối backend sẽ trả health `UP`, giả lập `/api/me` thành local admin, trả fixture cho một số GET, và generic success cho POST/PUT/PATCH. `/api/loads` được xử lý riêng và trả 502 “BACKEND_OFFLINE”. Vì vậy dev view có thể vừa báo health thành công/nhận mutation giả vừa không có persistence. Không dùng mock response làm bằng chứng API thật.

Shared provider dùng base URL cấu hình, nhưng một số adapter AI dùng global `axios` với relative `/api/...` và tự gắn auth header; chúng không đi qua shared refresh/envelope/error interceptor. Điều này tạo khác biệt transport và làm deployment phụ thuộc việc `/api` được reverse-proxy cùng origin.

## 6. Tests

Có 197 file test/spec trong `src/tests`, 13 Playwright E2E spec, cộng fixtures; Playwright config chỉ khởi chạy Vite dev server, không khởi chạy Spring/PostgreSQL. Vì vậy bộ E2E hiện chủ yếu xác nhận UI/browser contract, không chứng minh full frontend → backend → database flow. Không chạy build/lint/unit/E2E trong audit.

