# Hồ sơ phân tích và thiết kế UML — bản khảo sát ban đầu

Đề tài: **Xây dựng hệ thống quản lý và điều phối vận tải thông minh Logistics**.

Hồ sơ này **chưa hoàn thành**. Các danh mục hiện tại là điểm xuất phát để tiếp tục đối chiếu từng chức năng, không phải danh sách use case đầy đủ hoặc bằng chứng nghiệm thu hệ thống. Các sơ đồ hiện có chỉ bao phủ một phần module Load. Không dùng hồ sơ hiện tại làm bản báo cáo cuối cùng.

## Nguồn và giới hạn

- Backend cục bộ `logictics_api`, HEAD `2bc3944e232671892cb5b0ec3e2bad547f100c9a`; frontend cục bộ `/home/vumoi/logictics-app`, HEAD `bbe136fca4d822c828f789d82ed643722d2847d7`. Backend có nhiều thay đổi và tệp chưa theo dõi; HEAD không mô tả đầy đủ nội dung khảo sát.
- Chỉ mục GitNexus backend báo cũ do thay đổi trong workspace. Kết quả đồ thị cần được kiểm tra lại với mã hiện hành; chưa cập nhật chỉ mục trong lần khảo sát này.
- Không tìm thấy `quanly.docx` khi tìm trong hai repository. Chưa đối chiếu được tài liệu đó; không suy ra chức năng không tồn tại.
- Chưa kiểm tra hoạt động thực tế, dữ liệu production hay xác thực các tích hợp ngoài. Một khai báo route, endpoint hoặc lớp không đủ chứng minh một chức năng hoạt động xuyên suốt.
- Tài liệu nội bộ `../report-context/` là nguồn tham khảo có sẵn, không thay thế kiểm tra mã; một số mô tả có thể đã cũ.

## Tiến độ và việc còn lại

Đã khảo sát vị trí hai repository, cấu trúc sơ bộ, tài liệu hiện có và tám luồng Load ở các mức xác minh khác nhau. Các tệp trong `01-phan-tich-he-thong` ghi danh mục ứng viên và ranh giới bằng chứng. Cần tiếp tục kiểm tra route, biểu mẫu, quyền, controller, service, repository và migration cho các chức năng còn lại; các thư mục chưa có sơ đồ thể hiện phần chưa hoàn thành.

PlantUML và Pandoc hiện có trong PATH. Với PlantUML, từ thư mục dự án có thể dùng `plantuml -checkonly <tep.puml>` để kiểm tra, sau đó `plantuml -tpng <tep.puml>` hoặc `plantuml -tsvg <tep.puml>` để xuất ảnh. Kiểm tra cú pháp không thay thế kiểm tra bố cục bằng mắt.

## Cập nhật tiến độ Load

Đã lập tám use case UC001–UC008, đặc tả, 24 sơ đồ PlantUML và ảnh SVG/PNG, cùng hai chương bản nháp. Xem [bảng tiến độ](kiem-tra-tien-do.md). Phần còn lại của Load và các module khác chưa hoàn thành; trạng thái đầu tài liệu vẫn áp dụng cho toàn bộ hồ sơ. Đã kiểm tra cú pháp bằng PlantUML 1.2020.02; đã xem trực quan sequence UC001 và UC008, state TripStop, component và deployment. PlantUML, Graphviz và Pandoc hiện có trong PATH; `quanly.docx` không có tại `docs/bao-cao-uml/quanly.docx`.
