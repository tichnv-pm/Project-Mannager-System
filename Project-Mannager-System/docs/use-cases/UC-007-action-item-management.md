# UC-007 — Quản lý Action Item

> Dự án: PM Daily Work Management | Trạng thái: Draft
> FR liên quan: FR-AI-01..04 | BR liên quan: BR-AI-01..04

## 1. Mã Use Case
`UC-007`

## 2. Tên
Quản lý action item (tạo / cập nhật / chuyển thành task / theo dõi quá hạn)

## 3. Mô tả
Sau cuộc họp, PM tạo action item cho người phụ trách với hạn hoàn thành. Action item được theo dõi tới khi đóng; có thể chuyển thành task (cùng dự án, liên kết 1–1, chống tạo trùng) để quản lý chi tiết. Action item quá hạn xuất hiện trên dashboard và danh sách quá hạn.

## 4. Actor
- ADMIN, PROJECT_MANAGER (tạo/sửa/chuyển thành task).
- Assignee (cập nhật trạng thái/tiến độ của action item mình).

## 5. Trigger
- Kết thúc cuộc họp có việc cần theo dõi.
- Action item đến hạn / quá hạn.

## 6. Tiền điều kiện
1. Meeting thuộc dự án, tồn tại.
2. Assignee thuộc dự án.
3. User có quyền `action-item:manage` (ADMIN, PM dự án).

## 7. Hậu điều kiện
1. Action item được tạo/sửa/đóng; chuyển thành task tạo task mới `source = ACTION_ITEM` và gắn `linkedTaskId`.
2. Notification `ACTION_ITEM_ASSIGNED` gửi cho assignee.
3. Audit log ghi nhận.

## 8. Luồng chính (tạo action item)

| Bước | Tác nhân | Hành động | Kết quả |
|---|---|---|---|
| 1 | PM/ADMIN | Trong họp (hoặc màn hình họp), nhập title, description, assigneeId, dueDate, priority | Gửi `POST /api/v1/action-items` |
| 2 | Hệ thống | Validate: meetingId/projectId hợp lệ, assignee ∈ project | Hợp lệ |
| 3 | Hệ thống | Tạo action item (status OPEN) | Action item mới |
| 4 | Hệ thống | Gửi notification ACTION_ITEM_ASSIGNED cho assignee | Notification |
| 5 | Hệ thống | Ghi audit | Bản ghi audit |
| 6 | Hệ thống | Trả `201` | — |

## 9. Luồng thay thế

**9.1 Cập nhật:** assignee cập nhật status/progress (OPEN → IN_PROGRESS → DONE); PM cập nhật toàn bộ + `version` → `PUT /api/v1/action-items/{id}` → `200`.

**9.2 Chuyển thành task:** chọn action item chưa có `linkedTaskId` → `POST /api/v1/action-items/{id}/convert-to-task` → tạo task (project = project của AI, source = ACTION_ITEM, title mặc định = title AI) → gắn `linkedTaskId` → `201` + DTO task.

**9.3 Theo dõi quá hạn:** danh sách AI chưa đóng + dueDate < hôm nay → hiển thị trên dashboard (`pendingActionItems`, badge overdue) và có bộ lọc `overdue=true`.

**9.4 Danh sách:** `GET /api/v1/action-items?meetingId=&projectId=&status=&assigneeId=&overdue=&page=&size=`.

## 10. Luồng ngoại lệ

| # | Tình huống | Kết quả |
|---|---|---|
| 1 | Assignee không thuộc dự án | `400` |
| 2 | AI thuộc project khác với meeting | `400` |
| 3 | AI đã có linkedTaskId | `409` "Action item đã được chuyển thành công việc" |
| 4 | Version cũ | `409` CONFLICT |
| 5 | Không đủ quyền | `403` |
| 6 | Meeting đã xóa mềm | `404` |

## 11. Validation từng trường

| Trường | Quy tắc | Message lỗi |
|---|---|---|
| `meetingId` | Bắt buộc, tồn tại | "Cuộc họp không hợp lệ" |
| `projectId` | Bắt buộc, = project của meeting | "Dự án không hợp lệ" |
| `title` | Bắt buộc, 1–200 ký tự | "Tiêu đề không được để trống" |
| `assigneeId` | Bắt buộc, ∈ project | "Người phụ trách không hợp lệ" |
| `dueDate` | Tùy chọn, ISO-8601 | — |
| `priority` | Thuộc Priority | "Mức ưu tiên không hợp lệ" |
| `status` | Thuộc ActionItemStatus | "Trạng thái không hợp lệ" |

## 12. Business rule liên quan
BR-AI-01 (cùng project với meeting), BR-AI-02 (assignee ∈ project), BR-AI-03 (chuyển thành task chống trùng), BR-AI-04 (đã link không chuyển lại).

## 13. Phân quyền
- Tạo/sửa/chuyển thành task: `action-item:manage` (ADMIN, PM dự án).
- Cập nhật status/progress: chính assignee.
- Xem: `action-item:view` (ADMIN, PM, MEMBER, VIEWER — phạm vi dự án).

## 14. Audit log cần ghi
Tạo/sửa/xóa AI, chuyển AI thành task (kèm taskId tạo mới).

## 15. API dự kiến

| Method | Endpoint | Mục đích |
|---|---|---|
| POST | `/api/v1/action-items` | Tạo AI |
| GET | `/api/v1/action-items` | Danh sách + filter |
| PUT | `/api/v1/action-items/{id}` | Cập nhật |
| POST | `/api/v1/action-items/{id}/convert-to-task` | Chuyển thành task |

## 16. Acceptance criteria (Given/When/Then)

| # | Given | When | Then |
|---|---|---|---|
| AC-007-01 | PM dự án | Tạo AI hợp lệ | `201`; assignee nhận notification |
| AC-007-02 | Assignee ngoài dự án | Tạo AI | `400` |
| AC-007-03 | AI chưa có linkedTask | Chuyển thành task | `201`; task có source=ACTION_ITEM, cùng project; AI có linkedTaskId |
| AC-007-04 | AI đã có linkedTaskId | Chuyển thành task lần 2 | `409` |
| AC-007-05 | AI hết hạn chưa DONE | Lọc overdue | Có trong kết quả |
| AC-007-06 | Assignee của AI | Cập nhật status DONE | `200` |
| AC-007-07 | User không có quyền | Sửa AI | `403` |
| AC-007-08 | Version cũ | Cập nhật AI | `409` |
