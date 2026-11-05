# Khảo sát hệ thống — bản nháp

Backend sử dụng Java và Spring Boot theo `pom.xml`, frontend sử dụng React và TypeScript theo `/home/vumoi/logictics-app/package.json`. Cấu hình cơ sở dữ liệu và Compose thể hiện PostgreSQL; `Dockerfile` và `docker-compose.yml` mô tả môi trường container trong repository, chưa chứng minh triển khai production. Có mã xác thực Lark và JWT; cấu hình tenant có thể bật hoặc tắt. Nguồn: `src/main/java/com/company/logicstic/config/SecurityConfig.java`, `src/main/resources/application.yml`, `src/main/java/com/company/logicstic/integration/lark/auth/`.

Đồ thị GitNexus trả về luồng `AiDispatchController.runDispatch` và các phụ thuộc liên quan. Chỉ mục báo cũ; không coi kết quả là danh sách đầy đủ, đặc biệt đối với các thay đổi chưa commit. Mã hiện tại và tài liệu phân tích trước cần được kiểm tra cùng nhau.

Tài liệu tham khảo sẵn có: `docs/report-context/00_tong-quan.md`, `01_yeu-cau-va-tac-nhan.md`, `02_use-case.md`, `05_api.md`, `07_giao-dien.md` và `09_doi-chieu-sai-lech.md`. Đây là các bản khảo sát trước, không tự động được xác nhận là đúng với mọi thay đổi mới.
