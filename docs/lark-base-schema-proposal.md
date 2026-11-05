# Đề Xuất Bổ Sung Schema Lark Base Cho Phân Hệ AI (Gap & Dispatch)

> Tài liệu này mô tả các trường đề xuất bổ sung vào Bitable (Lark Base) để hiển thị và đồng bộ trạng thái của phân hệ AI Gap (AI Hỏi) và AI Dispatch (AI Điều Phối).
> Tuân thủ nguyên tắc: TMS là nguồn sự thật duy nhất (Single Source of Truth). Dữ liệu tính toán từ AI được lưu trữ trong CSDL TMS và chỉ phản chiếu (read-only projection) lên Lark Base.

---

## 1. Các trường đề xuất cho bảng `Loads` (Chuyến Hàng)

| Tên trường (Lark Base) | Kiểu dữ liệu | Quyền sở hữu | Mô tả nghiệp vụ | Mapping tương ứng trong TMS |
|---|---|---|---|---|
| `Has Blocking Gap` | Checkbox (Boolean) | TMS (Read-only) | Đánh dấu load đang bị chặn do thiếu thông tin cốt lõi (tọa độ, giờ hẹn, giá cước...). | `ai_load_gaps.severity = 'BLOCKING' AND status = 'OPEN'` |
| `Gap Summary` | Multiline Text | TMS (Read-only) | Tóm tắt câu hỏi tự nhiên bằng tiếng Việt về thông tin còn thiếu cần bổ sung. | `ai_load_gaps.question_natural` |
| `Gap Status` | Single Select | TMS (Read-only) | Trạng thái gap: `CLEAN`, `BLOCKING_OPEN`, `WARNING_OPEN`, `NEEDS_HUMAN`. | `ai_load_gaps.status` |
| `AI Suggested Truck` | Text | TMS (Read-only) | Biển số xe được AI đề xuất tối ưu nhất cho chuyến hàng. | `ai_dispatch_decisions.truck.number` |
| `AI Suggested Driver` | Text | TMS (Read-only) | Tên tài xế được AI đề xuất tối ưu. | `ai_dispatch_decisions.truck.main_driver.name` |
| `AI Optimization Score` | Number (2 decimals) | TMS (Read-only) | Điểm số tối ưu tổng hợp (0.00 - 1.00) dựa trên 4 tiện ích: Margin, On-Time, Deadhead, HOS. | `ai_dispatch_decisions.score` |
| `AI Dispatch Reasoning` | Multiline Text | TMS (Read-only) | Lập luận giải thích lý do đề xuất xe/tài xế (độ tin cậy, quãng đường chạy rỗng, biên lợi nhuận). | `ai_dispatch_decisions.reasoning` |

---

## 2. Quy tắc ghi và đồng bộ
1. **Một chiều (One-Way Projection)**:
   - Tất cả các trường AI trên Lark Base đều là **Read-Only** đối với người dùng Lark Base. Chỉ có worker/dịch vụ đồng bộ của TMS mới có quyền ghi giá trị lên các trường này.
2. **Không ghi đè dữ liệu nghiệp vụ ngoài A4**:
   - Khi có sự kiện chỉnh sửa bản ghi từ Lark Base (`bitable_record_changed_v1`), hệ thống chỉ tiếp nhận các trường được phép sửa theo A4. Mọi thay đổi đối với các trường AI trên Base sẽ bị bỏ qua (chống vòng lặp phản hồi).
