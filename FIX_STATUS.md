# FIX STATUS — Module LOAD

> File trạng thái persistent. Mỗi lượt chỉ làm ĐÚNG 1 mục trong 8 mục.
> Đọc file này đầu mỗi lượt để biết mục nào đã xong, mục nào đang dở.

---

## Overall Plan

### 🔴 Phase 1 — Critical
- [ ] 1. Load State Machine (Draft → Dispatched → PickedUp → Delivered, Cancel từ mọi state)
- [ ] 2. Driver Share Calculation
- [ ] 3. Invoice Auto-Flip khi Load → Dispatched

### 🟡 Phase 2 — Code Quality
- [ ] 4. Gộp LoadView mapper (bỏ 1 trong 2 cách mapping trùng nhau)
- [ ] 5. Thêm index cho `Load.status`
- [ ] 6. Tách rule `@Mapping` dùng chung giữa `toEntity()` / `updateEntity()`

### 🟢 Phase 3 — Cleanup (chờ xác nhận)
- [ ] 7. Xóa `LoadBoardConfiguration` / `LoadBoardListing` — chờ PO xác nhận
- [ ] 8. Bổ sung query method cho `LoadConditionReportRepository` — xác nhận trước

---

## Fix Reports (append-only)