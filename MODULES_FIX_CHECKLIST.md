# Checklist sửa lỗi `modules`

Nguồn phát hiện: [`MODULES_ERRORS.md`](MODULES_ERRORS.md)

Quy tắc cập nhật:

- Chỉ tích `[x]` khi code đã sửa và kiểm tra hồi quy liên quan chạy đạt.
- Nếu lỗi chỉ sửa một phần, giữ `[ ]` và ghi rõ phần còn lại.
- Không tự đóng các capability cần quyết định nghiệp vụ hoặc hạ tầng chưa có contract.

## P0 — Chặn đường chạy chính

- [x] **MOD-001** — Sửa JPQL messaging (`employee.id`, `readBy.id`) và thêm test parse/query.
- [x] **MOD-002** — Gán `sentAt`, `isDeleted` khi gửi message và thêm regression test.
- [x] **MOD-003** — Bỏ `Payment.tenantId` UUID dư thừa trong kiến trúc database-per-tenant.
- [x] **MOD-015** — Sửa migration V2 rename sai/bỏ sót cột audit; test đối chiếu hai chiều V1/V2.
- [x] **MOD-016** — Khôi phục fresh compile của `DataSeeder` bằng các import messaging bị thiếu.

## P1 — Sai dữ liệu hoặc chức năng chính chưa khép kín

- [x] **MOD-004** — Khôi phục duplicate check khi update Employee/Truck; test với mapper thật.
- [x] **MOD-005** — Driver list/detail chỉ trả employee có quyền driver `update_trip_status`.
- [x] **MOD-006** — Resolve và round-trip container/origin terminal/destination terminal của Load.
- [x] **MOD-007** — Tạo conversation cùng participants hợp lệ.
- [x] **MOD-008** — Không cho create/update Load bypass state machine.
- [x] **MOD-009** — Hoàn thiện `lastMessageAt` và mark-read/unread workflow.
- [x] **MOD-010** — Hoàn thiện document upload/download/delete blob lifecycle.

## P2 — Chức năng phụ và quality gate

- [x] **MOD-011** — Inspection create/update/view quản lý defects.
- [x] **MOD-012** — Trip lifecycle và stop management.
- [x] **MOD-013** — Notification producer và contract phạm vi người nhận/tenant.
- [x] **MOD-014** — Chuẩn hóa zero/trailing-zero của driver share mà không làm mất phần lẻ có nghĩa.

## Quality gate cuối

- [x] Targeted tests cho từng lỗi đã sửa đều đạt.
- [x] `mvn -Dspotless.apply.skip=true test` đạt toàn bộ (112 test, 0 lỗi, 0 skip). Wrapper
  `mvnw` đang bị xóa trong worktree nên dùng Maven hệ thống.
- [x] Spring context smoke test và Hibernate repository-query test đều được bật, không còn skip.
- [x] `MODULES_ERRORS.md` đã đồng bộ trạng thái; không có mục đã sửa mang trạng thái `OPEN`.
