# Hướng dẫn tiếp tục

## Trạng thái đã lưu

Không sửa mã ứng dụng; chỉ sửa `docs/bao-cao-uml`. Đã giữ UC001–UC007; bổ sung UC008 xem metadata chứng từ theo Load. Đã vẽ thêm state TripStop theo service và component/deployment trích đoạn. Xem `kiem-tra-tien-do.md`, `01-phan-tich-he-thong/usecase-inventory.md`, `03-usecase-specifications/UC005-UC007-Load.md`, `03-usecase-specifications/UC008-XemChungTuLoad.md`, `danh-muc-so-do.md` và ma trận truy vết. UC002 route tắt; UC006 chưa xác minh nút UI; UC007 form tắt. Chưa có xác minh runtime. Các ảnh UC002 đã xuất lại sau sửa nhãn. Mã PlantUML trong các thư mục `source/`, ảnh trong `images/`.

## Lô tiếp theo

1. Tiếp tục Load từ các tab `src/pages/loads/show.tsx`: `LoadFinancialPanel`, `LoadTripPanel`, `LoadExceptionsPanel`, `LoadAiPanel`, `LoadLarkSyncPanel` và các component redesign trong `/home/vumoi/logictics-app/src/features/loads/components/`. Đối chiếu API, service và cờ bật trước khi lập UC009 trở đi; không tính tab placeholder hoặc upload bị tắt như tính năng hoạt động. Đối chiếu riêng action trong `DispatchPage` và backend `LoadPickupBusinessDateController` cùng các controller `rating`, chi phí và Lark.
2. Sau Load, tiếp tục xác thực: đã đọc `integration/lark/auth/LarkAuthController.java`, `controller/CurrentUserController.java`, frontend `src/providers/authProvider.ts`, `src/pages/auth/`, `src/router/AppRouter.tsx`; cần đọc `LarkAuthService`, session provider và dependency, lập UC sau mã cuối cùng. Khách hàng mới đọc `CustomerController`; cần kiểm tra `CustomerService`, repository, form và quyền.
3. Lập kiểm kê controller và route còn lại, tách API nội bộ khỏi mục tiêu người dùng, đối chiếu từng nhóm và lưu sau mỗi lô. Không suy ra số UC cuối cùng từ số endpoint.
4. Đánh dấu đủ bằng chứng, kiểm tra cú pháp/render riêng mỗi lô, kiểm tra trực quan các hình; cập nhật hai chương và độ bao phủ hai chiều. Chỉ đánh dấu COMPLETED khi toàn bộ điều kiện được đáp ứng.

Nguồn phân tích cũ có `docs/report-context/` để dẫn đường, cần kiểm tra lại mã. GitNexus báo chỉ mục cũ, không dùng như nguồn duy nhất. `quanly.docx` không có ở đường dẫn yêu cầu. Backend workspace có thay đổi chưa commit; không reset, clean, checkout, commit hoặc chỉnh sửa source.

Đã đối chiếu `TripExecutionService` cho bốn chuyển trạng thái điểm dừng; `STATE_TripStop` không khẳng định mọi chuyển trạng thái hoặc trạng thái cuối. Kiểm tra 24 sơ đồ qua PlantUML và cấu trúc PNG; cần kiểm tra trực quan từng hình, không coi contact sheet thu nhỏ là nghiệm thu.
