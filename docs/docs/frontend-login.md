# Login UI slice

## Mục tiêu

Trang đăng nhập tĩnh của Logicstic được dựng theo tinh thần mẫu [Animated Avatar
Login Form](https://www.codewithrandom.com/2024/05/19/animated-avatar-login-form-css/):
thẻ đăng nhập bo góc, nền xanh dịu, avatar SVG tương tác và form responsive.
Đây là lớp trình bày độc lập, không sao chép mã nguồn của trang tham chiếu.

## Phạm vi đã triển khai

- `src/main/resources/static/index.html`: trang mặc định tại `/`.
- `src/main/resources/static/login.css`: layout hai cột desktop, responsive mobile,
  màu sắc, focus state, validation state và avatar/paw animation.
- `src/main/resources/static/login.js`: mắt avatar đi theo con trỏ; avatar đưa tay
  che mắt khi focus mật khẩu; hiện/ẩn mật khẩu; validation email/mật khẩu; trạng thái
  form không làm giả request đăng nhập.

## Giới hạn tích hợp

Backend hiện là OAuth2 Resource Server và không cung cấp endpoint `/login`. Identity
Server bên ngoài vẫn sở hữu authorize, refresh và logout flow. Nút submit hiện chỉ
kiểm tra dữ liệu và hiển thị trạng thái sẵn sàng; khi có frontend auth flow, thay
callback trong `login.js` bằng PKCE authorize flow của Identity Server.

## Kiểm tra

```bash
node --check src/main/resources/static/login.js
./mvnw -q -DskipTests package
```

Maven package phải chứa `BOOT-INF/classes/static/index.html`, `login.css` và
`login.js`. Kiểm tra trực quan qua browser chưa chạy được trong môi trường hiện tại
vì CLI `agent-browser` chưa được cài.
