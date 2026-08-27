# Postman API tests

Thư mục này chứa bộ request có thể import trực tiếp vào Postman:

- `logicstic-api.postman_collection.json`: collection API, OAuth và test scripts.
- `logicstic-local.postman_environment.json`: biến môi trường local.
- `generate_collection.py`: sinh lại hai file JSON sau khi cập nhật collection.

## Import và chạy

1. Trong Postman, chọn **Import** và nhập cả hai file JSON.
2. Chọn environment **Logicstic Local**.
3. Điền `clientId`, `clientSecret`, `username` và `password`, hoặc dán JWT có sẵn
   vào `accessToken`.
4. Mở Collection Runner và chỉ chạy folder
   **`00 - RUN THIS - E2E Smoke Flow`**.

Flow tự chạy theo thứ tự:

1. kiểm tra health và tạo `flowRunId`;
2. lấy token nếu `accessToken` đang trống;
3. kiểm tra token trước khi ghi dữ liệu;
4. tạo Role → Customer → Employee → Terminal → Truck → Load;
5. đọc lại Load và kiểm tra các quan hệ;
6. xóa dữ liệu vừa tạo theo thứ tự ngược.

Token cần role `SUPERADMIN` hoặc `OWNER` để chạy toàn bộ flow. Mặc định
`cleanupAfterRun=true`; đổi thành `false` nếu muốn giữ dữ liệu để kiểm tra.
Các folder còn lại là catalogue request để test thủ công từng API.

Environment mặc định dùng `http://localhost:18080` cho API chạy từ IntelliJ.
Nếu chỉ chạy stack Docker, đổi `baseUrl` thành `http://localhost:8080`.

API là resource server và Identity Server chạy riêng. Nếu Identity Server local dùng certificate
tự ký, cần tin cậy certificate đó hoặc tắt **SSL certificate verification** chỉ trong môi trường
local của Postman.

Profile `nodb` chỉ cung cấp health/OpenAPI; các business controller bị tắt. Để test CRUD, chạy API
với PostgreSQL bằng profile `local` hoặc stack Docker:

```bash
docker compose up -d --build
```

## Cơ chế an toàn

- `allowFileUpload=false`: request upload bị bỏ qua. Chọn file trong Postman rồi đổi thành `true`.
- `allowStateTransitions=false`: không tự dispatch/pick-up/deliver load hoặc trip.
- `allowCancellation=false`: không tự cancel state machine.
- `allowDestructive=false`: toàn bộ folder cleanup bị bỏ qua.
- `cleanupAfterRun=true`: chỉ cleanup dữ liệu do E2E flow vừa tạo; không phụ thuộc
  `allowDestructive`.

Không bật đồng thời normal state-transition sequence và cancellation trên cùng một load/trip.

## Test scripts có sẵn

Collection tự:

- thêm `Authorization: Bearer {{accessToken}}`;
- sinh `X-Request-Id` mới cho từng request;
- sinh các mốc thời gian hợp lệ cho payload;
- kiểm tra không có lỗi HTTP 5xx;
- kiểm tra envelope `ApiResponse` cho endpoint JSON;
- kiểm tra status code của từng request;
- lưu ID sau khi tạo role, customer, employee, terminal, truck, load, trip, invoice, payment,
  inspection, conversation, message và document.

Sinh lại JSON:

```bash
python3 scripts/postman/generate_collection.py
```
