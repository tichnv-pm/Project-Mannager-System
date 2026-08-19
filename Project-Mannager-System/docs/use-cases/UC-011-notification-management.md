# UC-011 — Thông báo (Notification)

> Dự án: PM Daily Work Management | Trạng thái: Draft
> FR liên quan: FR-NOTIF-01..03 | BR liên quan: BR-NOTIF-01..04

## 1. Mã Use Case
`UC-011`

## 2. Tên
Thông báo in-app (xem, đánh dấu đã đọc, sinh tự động theo deadline)

## 3. Mô tả
Hệ thống sinh thông báo in-app khi: được giao task, task sắp đến hạn, task quá hạn, có bình luận mới, được thêm vào họp, được giao action item. Người dùng xem danh sách thông báo của mình, đánh dấu đã đọc từng cái hoặc tất cả, điều hướng tới đối tượng liên quan. Job định kỳ sinh thông báo deadline/overdue nhưng không tạo trùng.

## 4. Actor
- Người nhận (mọi vai trò đã đăng nhập).
- Hệ thống (scheduled job).

## 5. Trigger
- Sự kiện giao việc / comment / thêm vào họp / giao action item.
- Task sắp đến hạn hoặc quá hạn (job định kỳ).
- User mở trang thông báo.

## 6. Tiền điều kiện
1. User đã đăng nhập, có quyền `notification:view`.
2. Task/họp/AI tồn tại, chưa xóa mềm.

## 7. Hậu điều kiện
1. Notification lưu đúng người nhận, đúng loại, không trùng (với job deadline).
2. Đánh dấu đã đọc làm giảm unreadCount.

## 8. Luồng chính (xem thông báo)

| Bước | Tác nhân | Hành động | Kết quả |
|---|---|---|---|
| 1 | User | Mở trang/bell icon thông báo | Gửi `GET /api/v1/notifications?page=&size=` |
| 2 | Hệ thống | Lấy notification của user (mới nhất trước), đếm unread | Page + unreadCount |
| 3 | UI | Hiển thị badge số chưa đọc, danh sách theo thời gian | Hiển thị |

## 9. Luồng thay thế

**9.1 Đánh dấu 1 thông báo đã đọc:** bấm vào thông báo → `PATCH /api/v1/notifications/{id}/read` → `204`; unreadCount giảm 1; điều hướng tới đối tượng (task/họp/action item) nếu còn truy cập được.

**9.2 Đánh dấu tất cả đã đọc:** bấm "Đánh dấu tất cả đã đọc" → `PATCH /api/v1/notifications/read-all` → `204`; unreadCount = 0.

**9.3 Job sinh notification deadline (FR-NOTIF-03):**
| Bước | Tác nhân | Hành động | Kết quả |
|---|---|---|---|
| 1 | Hệ thống | Job chạy định kỳ (VD mỗi giờ) | Bắt đầu |
| 2 | Hệ thống | Tìm task chưa DONE, dueDate trong N ngày tới (sắp đến hạn) / quá hạn | Danh sách task |
| 3 | Hệ thống | Sinh notification TASK_DUE_SOON / TASK_OVERDUE cho assignee (dedupe theo recipient+type+taskId+ngày) | Notification mới |
| 4 | Hệ thống | Nếu đã tồn tại trong ngày → bỏ qua | Không trùng |

## 10. Luồng ngoại lệ

| # | Tình huống | Kết quả |
|---|---|---|
| 1 | Không có thông báo | `200` page rỗng, unreadCount = 0 (empty state) |
| 2 | Thông báo của người khác | `403` khi đánh dấu đọc |
| 3 | Đối tượng liên quan đã xóa mềm | Thông báo vẫn hiển thị; điều hướng trả về 404 → UI hiện thông báo lỗi |
| 4 | Job chạy lại cùng ngày | Không tạo trùng |

## 11. Validation từng trường

| Trường | Quy tắc | Message lỗi |
|---|---|---|
| `page` / `size` | page ≥ 0, size 1–100 | "Phân trang không hợp lệ" |
| `id` | UUID của chính user | "Thông báo không tồn tại" |

## 12. Business rule liên quan
BR-NOTIF-01 (các sự kiện sinh thông báo), BR-NOTIF-02 (dedupe), BR-NOTIF-03 (chỉ chủ sở hữu), BR-NOTIF-04 (chỉ in-app v1).

## 13. Phân quyền
- Xem: `notification:view` (mọi vai trò — chỉ của mình).
- Đánh dấu đọc: `notification:manage` (chỉ của mình).
- Job: chạy nội bộ, không phơi API.

## 14. Audit log cần ghi
Không (dữ liệu notification tự quản lý created_at).

## 15. API dự kiến

| Method | Endpoint | Mục đích |
|---|---|---|
| GET | `/api/v1/notifications` | Danh sách + unreadCount |
| PATCH | `/api/v1/notifications/{id}/read` | Đánh dấu đã đọc |
| PATCH | `/api/v1/notifications/read-all` | Đánh dấu tất cả đã đọc |

## 16. Acceptance criteria (Given/When/Then)

| # | Given | When | Then |
|---|---|---|---|
| AC-011-01 | User có 5 thông báo (2 chưa đọc) | Gọi danh sách | `200`; unreadCount = 2; mới nhất ở trước |
| AC-011-02 | PM giao task cho user | Xảy ra sự kiện giao việc | User nhận notification TASK_ASSIGNED |
| AC-011-03 | Comment mới trên task user theo dõi | Xảy ra sự kiện comment | User nhận notification TASK_COMMENTED |
| AC-011-04 | Task sắp đến hạn | Job chạy | Notification TASK_DUE_SOON cho assignee |
| AC-011-05 | Task vẫn sắp đến hạn | Job chạy lần 2 cùng ngày | Không tạo thêm notification |
| AC-011-06 | User bấm vào 1 thông báo | Đánh dấu đã đọc | `204`; unreadCount giảm 1 |
| AC-011-07 | User có 3 chưa đọc | Đánh dấu tất cả | `204`; unreadCount = 0 |
| AC-011-08 | User A đánh dấu thông báo của user B | Cố gắng đánh dấu đọc | `403` |
| AC-011-09 | Không có thông báo nào | Mở trang thông báo | Empty state hiển thị |
| AC-011-10 | Đối tượng đã xóa mềm | Bấm điều hướng | Không crash; hiển thị thông báo lỗi 404 |
