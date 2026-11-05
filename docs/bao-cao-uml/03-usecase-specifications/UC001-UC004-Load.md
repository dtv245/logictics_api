# Đặc tả nhóm Load — bản phân tích tĩnh

Các vai trò API là ADMIN, ACCOUNTANT, DISPATCHER theo `SecurityConfig` cho `/api/loads/**`. Cần xác minh runtime và khả năng truy cập các tài nguyên phụ của từng vai trò; không suy ra nghiệm thu từ mã. Tác nhân phụ cho bốn UC: không xác định. Hệ thống ngoài không tham gia trực tiếp các luồng được mô tả.

## UC001 — Tạo Load

**Mục đích:** ghi một Load cho khách hàng và yêu cầu vận chuyển. **Tiền điều kiện:** người dùng có quyền; khách hàng tồn tại. **Hậu điều kiện:** nếu thành công Load được lưu trong giao dịch và trả `LoadView`; nếu lỗi giao dịch không hoàn tất.

**Luồng chính:** (1) mở màn hình tạo; (2) nhập dữ liệu và lưu; (3) `LoadForm` kiểm tra biểu mẫu; `LoadCreate` chuẩn bị mutation; (4) data provider gửi `POST /api/loads`; (5) controller kiểm tra DTO `@Valid` và gọi `LoadService.create`; (6) mapper tạo Load, service tìm khách hàng và các xe/điều phối viên nếu được chọn; (7) `saveAndFlush`, xử lý ngày nghiệp vụ qua `capture`, flush lần nữa; (8) nếu có publisher thì phát sự kiện nội bộ; (9) trả 201 và frontend điều hướng danh sách theo cấu hình.

**Thay thế:** người dùng hủy và quay lại danh sách. Ngày nghiệp vụ không có thì `capture` giữ nguyên; nếu có ngày thay đổi hợp lệ, ghi lịch sử qua JDBC. **Ngoại lệ:** biểu mẫu hoặc DTO sai; quan hệ không tồn tại; provenance không hợp lệ; không có quyền hoặc lỗi lưu. `LoadCreate` áp dụng lỗi trường backend cho form.

**Quy tắc:** DTO yêu cầu tên, loại, trạng thái, nguồn, khách hàng, khoảng cách, các cờ, chi phí, tiền tệ, địa chỉ chính và tọa độ; form mặc định status `draft`, type `STANDARD`, source `MANUAL`, USD và tọa độ bằng 0. Khi bật hàng nguy hiểm form yêu cầu lớp nguy hiểm và mã UN; không suy ra DTO backend có cùng ràng buộc. Ngày nghiệp vụ yêu cầu lý do, mã lý do, nguồn và actor hợp lệ ánh xạ Employee; không tự tính từ thời điểm hẹn. Dữ liệu vào `CreateLoadRequest`; đầu ra `ApiResponse<LoadView>`. API `POST /api/loads`.

**Kiểm thử đề xuất:** tạo dữ liệu hợp lệ; thiếu khách hàng; customerId không tồn tại; bật hazmat thiếu mã UN trên UI; provenance không đầy đủ; từ chối quyền; lỗi lưu phải rollback. **Nguồn:** `src/main/java/com/company/logicstic/controller/LoadController.java#create`, `service/LoadService.java#create`, `service/rating/LoadPickupBusinessDateService.java#capture`, `dto/load/CreateLoadRequest.java`, frontend `src/pages/loads/create.tsx`, `src/features/loads/components/LoadForm.tsx`, `src/providers/dataProvider.ts`, `src/pages/resourceRegistry.ts`. Đường dẫn Java tương đối `src/main/java/com/company/logicstic/`, frontend tương đối `/home/vumoi/logictics-app/`.

## UC002 — Cập nhật Load — CẦN XÁC MINH RUNTIME

**Giới hạn quan trọng:** route sửa bị loại khỏi router khi `CORE_VERSIONED_WRITES_RUNTIME_VERIFIED = false`; luồng UI dưới đây là mã có điều kiện, chưa phải chức năng đang bật. Nguồn frontend: `src/components/resources/resourceCapabilities.ts`, `src/router/AppRouter.tsx`.

**Mục đích:** sửa Load hiện có. **Tiền điều kiện:** có quyền và phiên bản hiện tại; Load tồn tại. **Hậu điều kiện:** dữ liệu mới và lịch sử ngày nếu thay đổi được lưu trong giao dịch; lỗi không hoàn tất giao dịch.

**Luồng chính:** (1) mở trang sửa và nạp Load; (2) sửa thông tin; (3) form kiểm tra, `useResourceEditContract.prepare` chuẩn bị dữ liệu kèm phiên bản; (4) data provider gửi `PUT /api/loads/{id}`; (5) controller xác thực DTO; (6) service khóa Load bằng `findByIdForUpdate`, kiểm tra `expectedVersion`, ánh xạ và resolve quan hệ; (7) capture ngày nghiệp vụ, lưu và phát sự kiện nếu publisher có; (8) trả `LoadView`, giao diện chuyển tới chi tiết theo cấu hình.

**Thay thế:** hủy trở về danh sách; bỏ qua ngày nghiệp vụ để giữ lịch sử. **Ngoại lệ:** Load hoặc quan hệ không tồn tại, phiên bản xung đột, validation/provenance sai, từ chối quyền hoặc lỗi cơ sở dữ liệu. Frontend hiển thị lỗi trường và phản hồi xung đột qua contract. **Quy tắc:** `expectedVersion` bắt buộc và không âm; version phải khớp dữ liệu hiện hành. Đầu vào `UpdateLoadRequest`; đầu ra `LoadView`; API `PUT /api/loads/{id}`. **Kiểm thử đề xuất:** sửa thành công; version cũ; Load không tồn tại; ngày mới không có provenance; bỏ qua ngày giữ giá trị. **Nguồn:** `controller/LoadController.java#update`, `service/LoadService.java#update`, `repository/LoadRepository.java#findByIdForUpdate`, `dto/load/UpdateLoadRequest.java`, frontend `src/pages/loads/edit.tsx`, `src/components/resources/useResourceEditContract.ts`.

## UC003 — Xem chi tiết Load

**Mục đích:** đọc thông tin Load. **Tiền điều kiện:** có quyền đọc và định danh Load. **Hậu điều kiện:** hiển thị chi tiết hoặc phản hồi lỗi; không thay đổi Load. **Luồng chính:** (1) mở trang chi tiết; (2) `LoadShow` dùng `useShow`; (3) data provider gửi `GET /api/loads/{id}`; (4) controller gọi `LoadService.getById`; (5) repository tìm Load, mapper chuyển `LoadView`; (6) frontend hiển thị. **Thay thế:** hiển thị trạng thái đang tải; có nút thử lại sau lỗi. **Ngoại lệ:** Load không tồn tại, quyền bị từ chối hoặc lỗi mạng. **Quy tắc:** phân quyền `/api/loads/**`; `findById` dùng entity graph cho customer, xe và điều phối viên. **Đầu vào:** id. **Đầu ra:** `LoadView`. **Kiểm thử đề xuất:** id hợp lệ, id không tồn tại, không có quyền, lỗi mạng và thử lại. **Nguồn:** `controller/LoadController.java#getById`, `service/LoadService.java#getById`, `repository/LoadRepository.java`, frontend `src/pages/loads/show.tsx` và `src/providers/dataProvider.ts`.

## UC004 — Xem timeline Load

**Mục đích:** xem trạng thái hiện tại và lịch sử sự kiện của Load. **Tiền điều kiện:** Load tồn tại và có quyền đọc. **Hậu điều kiện:** hiển thị trang sự kiện hoặc trạng thái rỗng; không ghi dữ liệu. **Luồng chính:** (1) mở thẻ timeline; (2) `LoadTimelinePanel` gửi `GET /api/loads/{loadId}/timeline`; (3) controller kiểm tra phân trang, sắp xếp theo `occurredAt` tăng dần; (4) service tìm Load rồi gọi `findPageByLoadId`; (5) trả `LoadTimelineResponse`; (6) hiển thị sự kiện. **Thay thế:** không có sự kiện thì hiển thị rỗng. **Ngoại lệ:** Load không tồn tại, tham số trang sai, quyền hoặc lỗi mạng. **Quy tắc:** mặc định page 1, pageSize 20; không suy ra mọi sự kiện của Load đã được nạp nếu còn các trang khác. **Đầu vào:** loadId, page, pageSize; **đầu ra:** trạng thái, danh sách và thông tin trang. **Kiểm thử đề xuất:** có sự kiện; không có sự kiện; id không tồn tại; trang không hợp lệ; nhiều hơn 20 sự kiện. **Nguồn:** `controller/LoadTimelineController.java#getTimeline`, `service/LoadTimelineService.java#getTimeline`, `repository/LoadEventRepository.java`, frontend `src/features/loads/components/LoadTimelinePanel.tsx`.
