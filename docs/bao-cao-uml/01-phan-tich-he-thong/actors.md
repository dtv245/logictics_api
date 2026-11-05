# Tác nhân ứng viên

Các nhãn quyền trong `SecurityConfig.java` và ma trận quyền frontend cần được đối chiếu theo từng endpoint trước khi gắn vào use case. Các nhóm ứng viên gồm người dùng quản trị, điều phối, kế toán, nhân sự xử lý lương và tài xế; Lark là hệ thống ngoài trong luồng xác thực và tích hợp. Vai trò ứng dụng và chức danh thực tế không nhất thiết tương ứng một-một. Chưa có căn cứ trong lần kiểm tra này để đưa khách hàng vào sơ đồ như người trực tiếp sử dụng hệ thống.

Nguồn cần kiểm tra: `src/main/java/com/company/logicstic/config/SecurityConfig.java`, `/home/vumoi/logictics-app/src/providers/permissions/roleMatrix.ts`, `src/main/java/com/company/logicstic/integration/lark/`.
