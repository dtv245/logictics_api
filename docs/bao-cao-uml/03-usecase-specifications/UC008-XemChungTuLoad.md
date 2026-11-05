# UC008 — Xem chứng từ của Load

**Mục đích:** xem danh sách metadata chứng từ gắn với một Load. **Actor chính:** người dùng có quyền đọc chứng từ; backend cho ADMIN, ACCOUNTANT, DISPATCHER. **Actor phụ:** không xác định. **Tiền điều kiện:** đã mở một Load, có `loadId`, tenant và quyền giao diện phù hợp. **Hậu điều kiện:** bảng hiển thị trang chứng từ hoặc trạng thái lỗi; UC không sửa chứng từ.

**Luồng chính:** (1) mở tab chứng từ trong `LoadShow`; (2) `LoadDocumentsPanel` kiểm tra quyền, tenant và `loadId`; (3) `useList` gửi yêu cầu đến data provider với bộ lọc `loadId`, trang và kích thước trang; (4) provider gửi GET `/api/documents`; (5) `DocumentController.search` gọi `DocumentService.search`; (6) service kiểm tra sắp xếp, phân trang và gọi `DocumentRepository.search`; (7) truy vấn trả trang `Document`, ánh xạ `DocumentView` có tính trạng thái hết hạn; (8) frontend trình bày bảng.

**Thay thế:** có thể đổi trang; frontend có thể nạp trước trang kế tiếp khi còn kết quả. **Ngoại lệ:** quyền bị từ chối hiển thị `ForbiddenState`; thiếu tenant hoặc id thì truy vấn không bật; lỗi truy vấn/validation hoặc mạng được chuyển cho bảng. **Quy tắc:** backend lọc theo loadId và các bộ lọc tùy chọn; không tự suy ra trả tệp gốc hay xác nhận POD. Modal upload trong panel có mã nhưng cờ `DOCUMENT_UPLOAD_CONTRACT_CONFIRMED` false nên không hiển thị. **Đầu vào:** loadId, trang, kích thước trang và bộ lọc; **đầu ra:** `ApiResponse<PagedResponse<DocumentView>>`. **API:** `GET /api/documents?loadId=...`. **Kiểm thử đề xuất:** Load có/không có chứng từ, đổi trang, không có quyền, thiếu tenant, lỗi API; xác nhận không hiển thị upload khi cờ tắt.

**Bằng chứng:** backend `src/main/java/com/company/logicstic/controller/DocumentController.java#search`, `service/DocumentService.java#search`, `repository/DocumentRepository.java#search`, `entity/Document.java`, `config/SecurityConfig.java`; frontend `/home/vumoi/logictics-app/src/pages/loads/show.tsx`, `src/features/loads/components/LoadDocumentsPanel.tsx`, `src/pages/resourceRegistry.ts`, `src/providers/dataProvider.ts`, `src/features/documents/api/documents.api.ts`. Đối chiếu mã tĩnh, chưa xác nhận runtime.

## Bảng tương tác của hình 3.10

| Bước | Thành phần | Nội dung |
|---|---|---|
| 1 | Người dùng → LoadDocumentsPanel | Mở tab và chờ kiểm tra quyền, tenant, id. |
| 2 | Panel → dataProvider | `useList` yêu cầu chứng từ theo Load và trang. |
| 3 | dataProvider → DocumentController | GET `/api/documents` với `loadId` và phân trang. |
| 4 | Controller → DocumentService | `search` chuyển các bộ lọc. |
| 5 | Service → DocumentRepository → database | Kiểm tra sort/trang, thực hiện `search`. |
| 6 | Service → controller → frontend | Ánh xạ `DocumentView`, trả trang và cập nhật bảng; có thể nạp trước trang sau. |
| 7 | Frontend | 403 hiển thị trạng thái không có quyền; lỗi khác được chuyển cho bảng. |
