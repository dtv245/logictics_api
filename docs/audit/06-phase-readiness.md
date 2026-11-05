# 06 — Phase readiness và khuyến nghị

## Kết luận phát triển hiện tại

Hệ thống có chiều sâu backend ở một số mảng và không chỉ là prototype UI. Core CRUD, trip execution, financial commands, payroll/settlement, Lark authentication và tenant datasource routing đều có source code ở controller/service/repository/entity/migration. Nhưng hiện chưa có bằng chứng runtime rằng một installation mới kết nối DB, migrate và hoàn tất end-to-end TMS flow; một số module UI chỉ tồn tại như page/resource source, không được đưa vào runtime.

### Có backend + frontend contract tương ứng

- Lark login/current-user, customers, employees, drivers/profiles, trucks, terminals.
- Load CRUD/timeline, trip CRUD, trip driver assignment và trip stop execution.
- AI dispatch/gaps API, rating/invoices/payments, shipment cost/accessorial, profitability reports.
- Driver settlement, payroll runs/payments/payslips; reconciliation route hiện bật.

Các kết luận trên là “đã triển khai trong source và static contract khớp”, **không đồng nghĩa đã chạy được** trên database/config hiện tại.

### Một phần hoặc bị gate

- Load và trip status có field/update path, nhưng chưa thấy một lifecycle contract đầy đủ tách command/state transition cho cả vòng đời.
- AI approval bị tắt mặc định; provider/model API config chưa khớp default Compose.
- Fleet history/report UI không được route đầy đủ; live-tracking hiển thị dữ liệu tọa độ đã lưu, không có telemetry stream.
- Documents metadata có API; upload không exposed, frontend download path không tồn tại ở backend.
- Notifications list/mark-all-read có; generic item actions giới hạn.
- Tenancy/router/provisioning code có nhưng multi-tenancy tắt mặc định, registry URL chưa cấu hình và không có tenant lifecycle HTTP API.

### Chủ yếu là UI scaffold hoặc chưa có runtime route

- Maintenance, HOS/ELD, DVIR, accidents, containers, load board, products: có page/resource source (một số có entities/migrations), nhưng thiếu matching collection API và bị foundation allowlist loại khỏi runtime.
- Optimization: backend run/accept và frontend components/API có, nhưng `/optimization` chưa gắn vào AppRouter.
- Role admin, fleet report/mileage pages và expense UI cũng chưa tích hợp đầy đủ.
- Vite dev offline fallback là mock, không phải chức năng backend.

## Rủi ro cần ưu tiên

| Ưu tiên | Vấn đề | Tác động |
|---|---|---|
| P0 — trước phase | PostgreSQL Compose defaults không cùng DB/user với API; Flyway mặc định bị tắt trong Compose trong khi Hibernate validate schema; `.env.example` chọn `nodb`. | Có thể không khởi động được hoặc khởi động ở mode không dùng persistence; dữ liệu/API test sai ngay từ nền tảng. |
| P1 — auth contract | Frontend OIDC gửi JWK-verified access token, backend bearer filter chỉ xác minh internal HMAC token từ luồng Lark; không thấy backend OIDC/JWK validator hoặc token exchange trong source. | OIDC login có thể qua UI nhưng các API bảo vệ trả 401; cần chốt issuer, thuật toán, audience, claim contract hoặc exchange path trước nghiệm thu. |
| P1 — trước bật multi-tenant | Tenant provisioning không có API orchestration, registry DDL không versioned, create→migrate→register không có cleanup; tenancy disabled mặc định. | Không có đường provisioning/operation hoàn chỉnh và dễ tạo database orphan nếu provision fail. |
| P1 — trước nghiệm thu TMS | Load/trip lifecycle chưa có command/state machine đầy đủ và thiếu runtime proof của dispatch → execution → cost → profitability. | Status UI có thể không đảm bảo business transition/audit nhất quán. |
| P1 — financial/document | Document upload/download mismatch; expense chỉ approve/reject; UI capability cần đối chiếu với API thật. | Người dùng có thể gặp thao tác không được hỗ trợ; rủi ro chứng từ/actual cost không đi hết quy trình. |
| P2 — điều hướng/integration | `/optimization` link dead; `loadsResource` duplicate; AI adapter dùng global Axios khác shared client. | Một số UI không truy cập được; transport/error/refresh behavior không thống nhất. |
| P2 — observable environment | Dev mock fallback trả health UP và mutation success khi backend offline; Redis được khai báo nhưng chưa có consumer trong source. | Demo có thể tạo cảm giác hệ thống đã tích hợp/persist; topology có thành phần không được dùng. |

## Phase tiếp theo nên làm gì?

### Khuyến nghị: Phase 0 — chốt deployment/API contract và kiểm chứng core vertical slice

Đừng bắt đầu bằng mở thêm CRUD UI. Phase tiếp theo nên chốt một cấu hình chạy xác định và kiểm chứng một vertical slice trên disposable PostgreSQL riêng:

1. Chọn profile thật (không `nodb`), thống nhất DB name/user/secret giữa Compose/API, quyết định Flyway chạy ở startup hay pipeline.
2. Xác nhận migration trên database rỗng và API readiness với schema hiện hành; chỉ sau đó coi endpoint là runtime-usable.
3. Xác nhận frontend/backend token contract cho OIDC và Lark: issuer, signature algorithm, audience, tenant/role claims, refresh và token exchange; đối chiếu với filter backend thực tế.
4. Chốt trạng thái/command contract cho `Load` và `Trip`: dispatch/assignment, arrive/service/depart, pickup/delivery/complete/cancel; gắn events/audit và expected-version behavior.
5. Theo cùng luồng, xác nhận driver/truck assignment → stop execution → actual cost approval → profitability response bằng API contracts; ghi rõ các phụ thuộc external IdP/Lark/LLM/route provider và gate. Không coi Vite fallback là test environment.

Lý do chọn Phase 0: hiện mismatch database/config khiến cả backend foundation chưa có đường khởi động mặc định đáng tin cậy; khi nền persistence/tenant mode chưa rõ, mở thêm modules sẽ tăng số contract chưa kiểm chứng. Sau khi Phase 0 qua gate, phase nghiệp vụ kế tiếp nên là **hoàn thiện core operations lifecycle**, rồi mới đưa các UI bị gate (maintenance/HOS/DVIR/container/load-board) vào runtime theo từng API contract.

## Trạng thái kiểm chứng

- Source static review: hoàn thành cho hai working tree tại 2026-11-04.
- Build/lint/unit/E2E: không chạy theo phạm vi chỉ đọc; chưa có pass/fail trong báo cáo.
- PostgreSQL/Flyway/Redis/Docker runtime: không chạy/không truy cập.
- External IdP/Lark/LLM/map providers: chưa xác minh.
- Source files: không sửa. Chỉ tạo sáu báo cáo audit được yêu cầu.

