# Danh mục sơ đồ — phạm vi đã khảo sát và việc cần làm

Danh mục chưa đầy đủ do chưa khảo sát toàn bộ module. Trạng thái “đã render” không đồng nghĩa hoàn thành: chưa kiểm tra trực quan hoặc runtime. UC002, UC006, UC007 còn hạn chế giao diện.

| Mã sơ đồ | Module | Use case | Loại | Mục đích | Mức độ | Trạng thái |
|---|---|---|---|---|---|---|
| ACT_UC001 | Load | UC001 | Hoạt động | Mô tả luồng UC | Bắt buộc | Đã render; cần xem bố cục |
| SEQ_UC001 | Load | UC001 | Tuần tự | Mô tả luồng UC | Bắt buộc | Đã render; cần xem bố cục |
| ACT_UC002 | Load | UC002 | Hoạt động | Mô tả luồng UC | Bắt buộc | Đã render; cần xem bố cục; cần xác minh UI |
| SEQ_UC002 | Load | UC002 | Tuần tự | Mô tả luồng UC | Bắt buộc | Đã render; cần xem bố cục; cần xác minh UI |
| ACT_UC003 | Load | UC003 | Hoạt động | Mô tả luồng UC | Bắt buộc | Đã render; cần xem bố cục |
| SEQ_UC003 | Load | UC003 | Tuần tự | Mô tả luồng UC | Bắt buộc | Đã render; cần xem bố cục |
| ACT_UC004 | Load | UC004 | Hoạt động | Mô tả luồng UC | Bắt buộc | Đã render; cần xem bố cục |
| SEQ_UC004 | Load | UC004 | Tuần tự | Mô tả luồng UC | Bắt buộc | Đã render; cần xem bố cục |
| ACT_UC005 | Load | UC005 | Hoạt động | Mô tả luồng UC | Bắt buộc | Đã render; cần xem bố cục |
| SEQ_UC005 | Load | UC005 | Tuần tự | Mô tả luồng UC | Bắt buộc | Đã render; cần xem bố cục |
| ACT_UC006 | Load | UC006 | Hoạt động | Mô tả luồng UC | Bắt buộc | Đã render; cần xem bố cục; cần xác minh UI |
| SEQ_UC006 | Load | UC006 | Tuần tự | Mô tả luồng UC | Bắt buộc | Đã render; cần xem bố cục; cần xác minh UI |
| ACT_UC007 | Load | UC007 | Hoạt động | Mô tả luồng UC | Bắt buộc | Đã render; cần xem bố cục; cần xác minh UI |
| SEQ_UC007 | Load | UC007 | Tuần tự | Mô tả luồng UC | Bắt buộc | Đã render; cần xem bố cục; cần xác minh UI |
| UC_Load, UC_Load_BoSung | Load | UC001–UC007 | Use case | Phân nhóm mục tiêu | Bắt buộc | Đã render; cần xem bố cục |
| CLASS_Load | Load | UC001–UC007 | Lớp | Lớp liên quan Load | Cần thiết | Đã render; cần xem bố cục |
| ERD_Load | Load | UC001–UC007 | ERD | Quan hệ Load trích đoạn | Cần thiết | Đã render; cần xem bố cục |
| UC_Load_TaiLieu, ACT_UC008, SEQ_UC008 | Load / chứng từ | UC008 | Use case, hoạt động, tuần tự | Xem chứng từ của Load | Bắt buộc | Đã render; sequence đã xem trực quan, còn lại cần xem |
| STATE_TripStop | Trip | Chưa lập UC | Trạng thái | Chuyển trạng thái điểm dừng theo service | Cần thiết | Đã render và xem trực quan; phạm vi service |
| DEP_Compose | Kiến trúc | Không áp dụng | Triển khai | Mô tả Compose cục bộ | Cần thiết | Đã render và xem trực quan; chưa xác minh runtime |
| COMP_System | Kiến trúc | Không áp dụng | Thành phần | Mô tả các thành phần có bằng chứng | Cần thiết | Đã render và xem trực quan; trích đoạn |
| Các module khác | Toàn hệ thống | Chưa xác định | Use case / hoạt động / tuần tự | Bao phủ nghiệp vụ sau khảo sát | Bắt buộc | Chưa khảo sát đầy đủ |
| ERD tổng thể, package, quy trình | Toàn hệ thống | Chưa xác định | ERD / package / workflow | Cần đối chiếu quan hệ xuyên module | Cần thiết | Chưa có đủ cơ sở |
| Communication, object | Chưa xác định | Chưa xác định | Tương tác / đối tượng | Minh họa nếu thực sự cần | Bổ sung khi phù hợp | Chưa xác định; không lập chỉ để tăng số lượng |

Load cần UC/activity/sequence để truy vết mục tiêu và hai sơ đồ lớp, ERD cho cấu trúc. TripStop cần sơ đồ trạng thái vì service kiểm tra trạng thái; các trạng thái và lối vào khác cần kiểm tra tiếp. Sơ đồ triển khai dùng Compose cục bộ, không chứng minh production. Các module khác cần phân tích trước khi quyết định thêm state, class hoặc ERD; object/communication chưa có tình huống bắt buộc đã chứng minh.
