# Danh mục chức năng ứng viên

Các nhóm dưới đây được tổng hợp từ bản khảo sát trước và cần đối chiếu lại với controller, service, giao diện và quyền. Cột trạng thái phản ánh mức độ kiểm tra trong hồ sơ này, không biểu thị hoàn thành sản phẩm.

| Nhóm khảo sát | Chức năng ứng viên | Trạng thái | Nguồn cần đối chiếu |
|---|---|---|---|
| Xác thực | Đăng nhập Lark, xem danh tính hiện hành | Cần xác minh luồng | `integration/lark/auth/LarkAuthController.java`, `CurrentUserController.java` |
| Đơn hàng | Tìm kiếm, xem, tạo, sửa, xóa Load; xem timeline | Có mã nguồn; chưa xác minh hoạt động | `LoadController.java`, `LoadTimelineController.java`, frontend `src/pages/loads/` |
| Khách hàng và danh mục | Quản lý khách hàng, nhân viên, tài xế, xe, terminal | Cần xác minh từng thao tác | `CustomerController.java`, `EmployeeController.java`, `DriverController.java`, `TruckController.java`, `TerminalController.java` |
| Chuyến vận chuyển | Quản lý chuyến, gán tài xế, thao tác điểm dừng | Cần xác minh từng thao tác | `TripController.java`, `TripExecutionController.java` |
| Điều phối | Tạo và xử lý đề xuất AI; chạy tối ưu hóa | Cần xác minh điều kiện bật và kết quả | `ai/dispatch/AiDispatchController.java`, `OptimizationRunController.java` |
| Tài chính | Định giá, hóa đơn, thanh toán, phụ phí và chi phí | Cần xác minh từng thao tác | `RatingController.java`, `BillingController.java`, `PaymentController.java`, `AccessorialController.java` |
| Lương tài xế | Quyết toán, kỳ lương, phiếu lương, lệnh thanh toán | Cần xác minh từng luồng | `DriverSettlementController.java`, `PayrollRunController.java`, `PayslipController.java` |
| Hồ sơ tài xế | Hồ sơ, giấy phép, hợp đồng, sức khỏe và thông tin liên quan | Cần khảo sát bổ sung; có tệp chưa theo dõi | `DriverProfileController.java`, `DriverLicenseController.java`, các `Driver*Controller.java` |
| Báo cáo và thông báo | Báo cáo và đọc thông báo | Cần xác minh từng màn hình | `ReportController.java`, `NotificationController.java` |
| Tài liệu và tích hợp | Tra cứu tài liệu; đồng bộ và nhận sự kiện Lark | Cần xác minh luồng và môi trường | `DocumentController.java`, `integration/lark/base/`, `integration/lark/event/` |

Đường dẫn Java tương đối với `src/main/java/com/company/logicstic/`. Danh mục này chưa bao quát mọi thao tác; không dựa vào các hằng số route để khẳng định màn hình đã được mount.

## Đối chiếu chi tiết Load

Load lưu khách hàng, địa chỉ và tọa độ lấy/giao, ngày yêu cầu và phân công xe/điều phối viên; giao diện gọi là đơn hàng. `TripStop` liên kết riêng tới Load và Trip, do đó không đồng nhất Load với Trip. Nguồn: `entity/Load.java`, `entity/TripStop.java`, `src/pages/loads/create.tsx` ở frontend. Bốn chức năng đã đối chiếu được liệt kê trong `usecase-inventory.md`; tìm kiếm, xóa, sửa ngày nghiệp vụ và các tab chuyên biệt vẫn cần kiểm tra.
