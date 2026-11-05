# Danh mục use case — phân tích tĩnh từng phần

| UC ID | Tên | Actor | Module | API | Trạng thái |
|---|---|---|---|---|---|
| UC001 | Tạo Load | ADMIN, ACCOUNTANT, DISPATCHER | Load | POST /api/loads | Đã đối chiếu mã chính; chưa kiểm thử runtime |
| UC002 | Cập nhật Load | ADMIN, ACCOUNTANT, DISPATCHER | Load | PUT /api/loads/{id} | Có mã UI/backend; route sửa bị tắt, cần xác minh runtime |
| UC003 | Xem chi tiết Load | ADMIN, ACCOUNTANT, DISPATCHER | Load | GET /api/loads/{id} | Đã đối chiếu mã chính; chưa kiểm thử runtime |
| UC004 | Xem timeline Load | ADMIN, ACCOUNTANT, DISPATCHER | Load | GET /api/loads/{id}/timeline | Đã đối chiếu mã chính; chưa kiểm thử runtime |
| UC005 | Tra cứu danh sách Load | ADMIN, ACCOUNTANT, DISPATCHER | Load | GET /api/loads | Đối chiếu tĩnh; chưa kiểm thử runtime |
| UC006 | Xóa Load | Người có quyền API Load | Load | DELETE /api/loads/{id} | Backend có mã; giao diện cần xác minh |
| UC007 | Sửa ngày lấy hàng nghiệp vụ | Người có quyền API Load và Employee | Load | POST /api/loads/{id}/requested-pickup-business-date | Backend có mã; UI bị tắt, cần xác minh |
| UC008 | Xem chứng từ của Load | ADMIN, ACCOUNTANT, DISPATCHER | Load / chứng từ | GET /api/documents?loadId=... | Đối chiếu tĩnh; chưa kiểm thử runtime |

Danh mục chưa đầy đủ: các chức năng trong các tab Load, cùng các module khác, còn cần khảo sát. Vai trò là quyền backend; khả năng sử dụng toàn bộ giao diện và tài nguyên phụ cần kiểm chứng thêm. Xem đặc tả `../03-usecase-specifications/UC001-UC004-Load.md` và `../03-usecase-specifications/UC005-UC007-Load.md`.
