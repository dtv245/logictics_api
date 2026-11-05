# 01 — Tổng quan hệ thống LogisticsX

**Ngày audit:** 2026-11-04  
**Phạm vi code:** working tree hiện tại của `logictics_api` và repo sibling `logictics-app`.  
**Loại audit:** static source review. Không chạy app, test, migration hay truy cập database.

## Kết luận nhanh

LogisticsX hiện là một **modular monolith TMS**: Spring Boot/JPA/Flyway ở backend và React/Refine ở frontend. Source đã có các lát nghiệp vụ chạy qua nhiều tầng cho load, trip execution, driver assignment, chi phí, billing, settlement/payroll, xác thực Lark và tenant database routing. Mức hoàn thiện không đồng đều: một số màn hình không được đưa vào runtime, một số API chỉ hỗ trợ một phần, một số tính năng bị gate bởi cấu hình, và chế độ dev có mock fallback.

Không thể kết luận hệ thống đang chạy ổn chỉ từ source. Chưa xác minh kết nối API–PostgreSQL hoặc provider bên ngoài. Riêng mặc định Docker Compose có mismatch giữa database/user của PostgreSQL và API; cấu hình Compose cũng tắt Flyway trong khi JPA validate schema. Đây là blocker trước khi dựa vào Compose để đánh giá các phase tiếp theo.

## Hai repo và cấu trúc

| Thành phần | Repo | Kiến trúc trong source |
|---|---|---|
| Backend | `/home/vumoi/logictics_api` | Java 21, Spring Boot, REST controllers, services, repositories, JPA entities, PostgreSQL, Flyway, Spring Security. Domain tập trung trong một ứng dụng; chưa thấy ranh giới microservice. |
| Frontend | `/home/vumoi/logictics-app` (`logictisc-app` trong đề bài) | React 18, TypeScript, Vite, Refine, React Router, Ant Design, Axios/TanStack Query. Runtime dùng registry API/resource allowlist; còn nhiều page/resource code chưa được mount. |

Backend khởi chạy từ [`LogisticApplication.java`](../../src/main/java/com/company/logicstic/LogisticApplication.java). Các tầng chính nằm dưới `controller/`, `service/`, `repository/`, `entity/`, `dto/`, `mapper/`, `config/` và `integration/lark/`. Cấu hình nhiều tenant được đặt trong `config/` và `service/Tenant*`. Frontend khởi tạo Refine/providers ở [`App.tsx`](../../../logictics-app/src/App.tsx); route có guard và lazy page trong [`AppRouter.tsx`](../../../logictics-app/src/router/AppRouter.tsx).

## Mức độ tích hợp theo lát nghiệp vụ

- **Đã có backend và frontend contract tương ứng:** core customer/employee/driver/truck/load/trip CRUD; trip driver assignment và stop execution; AI dispatch/gap API; rating/invoice/payment; shipment costs/profitability; settlement/payroll/payslips; Lark login. Đây là đánh giá đối chiếu đường dẫn/method trong source, không phải xác nhận runtime.
- **Auth cần xác minh end-to-end:** Lark callback đổi code thành internal JWT; frontend OIDC gửi JWK-verified access token, trong khi backend source hiện chỉ thể hiện HMAC validator cho internal token.
- **Một phần:** load/trip status lifecycle; fleet reports và location tracking; documents; notifications; multi-tenant selection/provisioning; Lark Base với cấu hình external.
- **Có code UI nhưng không có API contract đầy đủ và/hoặc bị loại khỏi runtime:** maintenance, HOS/ELD, DVIR, accidents, containers, load board, products. Resource registry hiện tại chủ động chỉ đưa các resource có API contract vào navigation/router.
- **Có API/backend nhưng khó truy cập từ UI hiện tại:** optimization page có component và API adapter nhưng không được gắn route; một số fleet/report/role/Lark admin capabilities cũng thiếu route UI.
- **Mock:** Vite dev proxy tự trả dữ liệu giả cho một phần API khi backend không kết nối được. Đây là hành vi dev, không phải bằng chứng backend hoạt động.

Chi tiết route–API–entity và trạng thái xem [04-api-integration-matrix.md](04-api-integration-matrix.md).

## Hạn chế phương pháp

Working tree ở cả hai repo đã có thay đổi local trước audit; báo cáo mô tả **source đang có tại thời điểm audit**, không khẳng định mọi thay đổi đã commit. GitNexus backend index báo chậm một commit; các lần đọc context bị lỗi version lưu trữ của MCP. Vì vậy, các kết luận về flow được xác minh trực tiếp bằng source, route annotations và API adapters.

Không chạy build/unit/E2E test, không start backend/frontend, không gọi API, không mở database, không chạy Flyway. Báo cáo không xác nhận dữ liệu đang có, trạng thái container, secrets, kết nối Lark/LLM/IdP hay hành vi triển khai thực tế.

## Các quyết định cần có trước phase tiếp

1. Chốt một bộ PostgreSQL database/user/credential thống nhất giữa Compose và API, rồi xác nhận migration/bootstrap policy cho database mới.
2. Chốt tenancy mode và ranh giới control plane/tenant plane; hiện router có code nhưng bị tắt mặc định và registry DB chưa được cấu hình trong Compose.
3. Chọn vertical slice vận hành để hoàn thiện: load state → dispatch/assignment → trip execution → actual cost → profitability; xác định API lifecycle còn thiếu.
4. Chốt danh sách UI sẽ được đưa vào runtime. Các resource thiếu backend đang bị ẩn, nhưng page code vẫn tồn tại và dễ bị nhầm là đã triển khai.

