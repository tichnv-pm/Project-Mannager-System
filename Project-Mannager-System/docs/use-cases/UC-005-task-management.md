# UC-005 — Quản lý công việc (Task Management)

> Dự án: PM Daily Work Management | Trạng thái: Draft — Use Case trọng tâm
> FR liên quan: FR-TASK-01..17 | BR liên quan: BR-TASK-01..18, BR-GEN-08, BR-GEN-09

## 1. Mã Use Case
`UC-005`

## 2. Tên
Quản lý công việc (tạo / sửa / xem / danh sách / giao việc / trạng thái / tiến độ / blocker / bình luận / file / task con / lịch sử / việc của tôi / xuất Excel)

## 3. Mô tả
Use case lớn nhất: toàn bộ vòng đời của công việc từ tạo, giao cho thành viên, theo dõi trạng thái/tiến độ/blocker, trao đổi qua bình luận, đính kèm file, phân rã task con, xem lịch sử thay đổi, tìm kiếm/lọc/phân trang/sắp xếp, và xuất Excel. Mã task tự sinh `PRJXXX-TASK-000001` an toàn concurrent.

## 4. Actor
- ADMIN, PROJECT_MANAGER (quản lý toàn bộ trong phạm vi dự án).
- PROJECT_MEMBER (tạo, sửa task được giao, bình luận, file).
- VIEWER (chỉ xem).

## 5. Trigger
- Có công việc mới / thay đổi.
- User cần xem việc của mình / hôm nay / quá hạn.
- User muốn xuất danh sách công việc.

## 6. Tiền điều kiện
1. Dự án tồn tại, chưa xóa mềm.
2. User có quyền tương ứng với hành động.
3. Người được giao/ phối hợp thuộc dự án.

## 7. Hậu điều kiện
1. Task tạo/sửa/xóa mềm đúng yêu cầu; mã unique.
2. Lịch sử (history) ghi nhận thay đổi trạng thái/assignee/progress.
3. Audit log ghi hành động quan trọng.
4. Notification được sinh khi giao việc / comment / deadline.

## 8. Luồng chính (tạo task)

| Bước | Tác nhân | Hành động | Kết quả |
|---|---|---|---|
| 1 | PM/ADMIN | Mở form tạo task: project, title, description, parentTask, assignee, collaborators, watchers, status, priority, type, source, startDate, dueDate, progress, estimateMinutes, tags, blocked/blockerReason, notes | Gửi `POST /api/v1/tasks` |
| 2 | Hệ thống | Validate toàn bộ (mục 11) | Hợp lệ |
| 3 | Hệ thống | Sinh mã `PRJXXX-TASK-NNNNNN` (unique, an toàn concurrent, trong transaction) | Mã unique |
| 4 | Hệ thống | Lưu task; nếu có parent → kiểm tra cùng project, không vòng lặp | Task mới |
| 5 | Hệ thống | Ghi history bản ghi đầu + audit | Lịch sử + audit |
| 6 | Hệ thống | Gửi notification `TASK_ASSIGNED` cho assignee (nếu có) | Notification |
| 7 | Hệ thống | Trả `201` + DTO task (kèm code) | — |

## 9. Luồng thay thế

**9.1 Cập nhật task:** sửa thông tin + `version` → `PUT /api/v1/tasks/{id}` → validate + optimistic locking → lưu + history + audit → `200`.

**9.2 Chuyển trạng thái:** `PATCH /api/v1/tasks/{id}/status {status, blockerReason?}` → kiểm tra state machine → DONE ⇒ progress=100 + `actualCompletedAt`; BLOCKED ⇒ bắt buộc reason → `200`.

**9.3 Cập nhật tiến độ:** `PATCH /api/v1/tasks/{id}/progress {progress}` → 0–100 → lưu; progress=100 → gợi ý chuyển DONE (BR-TASK-05).

**9.4 Giao việc / đổi assignee:** chọn assignee ∈ project → lưu + notification + history → `200`. Assignee sau đó xem trong "Việc của tôi".

**9.5 Bình luận:** `POST /api/v1/tasks/{id}/comments` → lưu + notification cho assignee/watchers → `201`; xem: `GET /api/v1/tasks/{id}/comments`.

**9.6 File đính kèm:** upload file (≤ 10MB, whitelist mime) → lưu storage + bản ghi attachment → `201`; tải lại bằng URL trả về.

**9.7 Tạo task con:** chọn parent trong cùng project → kiểm tra không vòng lặp → tạo con.

**9.8 Lịch sử:** `GET /api/v1/tasks/{id}/history` → danh sách thay đổi (field, từ → đến, actor, thời gian).

**9.9 Danh sách/lọc:** `GET /api/v1/tasks` với filter: keyword, projectId, assigneeId, status, priority, type, startDateFrom/To, dueDateFrom/To, overdue, blocked, tagId + page/size/sort → page server-side.

**9.10 Việc của tôi / hôm nay / quá hạn:** `GET /api/v1/tasks/my-tasks` (assignee = me), `GET /api/v1/tasks/today` (dueDate hôm nay theo timezone user), `GET /api/v1/tasks/overdue` (quá hạn chưa DONE).

**9.11 Xuất Excel:** bấm Export với filter hiện tại → `GET /api/v1/tasks/export` → file Excel (≤ 10.000 dòng).

**9.12 Xóa mềm:** confirm → `DELETE /api/v1/tasks/{id}` → đánh dấu deleted → `204` + audit. (Task con: xóa cả cây HOẶC chặn — chờ xác nhận BR-TASK-17.)

## 10. Luồng ngoại lệ

| # | Tình huống | Kết quả |
|---|---|---|
| 1 | `title` rỗng | `400` fieldErrors[title] |
| 2 | `dueDate < startDate` | `400` fieldErrors[dueDate] |
| 3 | `progress` ngoài [0,100] | `400` fieldErrors[progress] |
| 4 | DONE nhưng progress ≠ 100 | `400` |
| 5 | BLOCKED không có blockerReason | `400` |
| 6 | Assignee/collaborator không thuộc dự án | `400` |
| 7 | Parent task khác project | `400` |
| 8 | Tạo vòng lặp cha–con | `400` |
| 9 | Task đã xóa mềm | `404` |
| 10 | Cập nhật version cũ | `409` CONFLICT |
| 11 | Không đủ quyền (VD MEMBER sửa task người khác) | `403` |
| 12 | Trạng thái chuyển không hợp lệ theo state machine | `400` |
| 13 | File > 10MB / sai mime | `413` / `400` |
| 14 | Export > 10.000 dòng | `400` "Vượt giới hạn xuất dữ liệu" |
| 15 | Sort field không nằm whitelist | `400` |

## 11. Validation từng trường

| Trường | Quy tắc | Message lỗi |
|---|---|---|
| `title` | Bắt buộc, 1–200 ký tự | "Tiêu đề không được để trống" / "Tiêu đề tối đa 200 ký tự" |
| `projectId` | Bắt buộc, dự án tồn tại, chưa xóa | "Dự án không hợp lệ" |
| `parentTaskId` | Tùy chọn; cùng project; không vòng lặp | "Công việc cha không hợp lệ" |
| `assigneeId` / `collaborators[]` | Thuộc project | "Người thực hiện phải thuộc dự án" |
| `status` | Thuộc TaskStatus | "Trạng thái không hợp lệ" |
| `priority` | Thuộc Priority, mặc định MEDIUM | "Mức ưu tiên không hợp lệ" |
| `type` | Thuộc TaskType | "Loại công việc không hợp lệ" |
| `startDate` / `dueDate` | ISO-8601; `dueDate ≥ startDate` | "Hạn hoàn thành không được nhỏ hơn ngày bắt đầu" |
| `progress` | 0–100 | "Tiến độ phải từ 0 đến 100" |
| `blocked` + `blockerReason` | BLOCKED ⇒ reason bắt buộc 1–500 ký tự | "Phải nhập lý do blocker" |
| `estimateMinutes` | ≥ 0 | "Thời gian dự kiến không hợp lệ" |
| `version` | Bắt buộc khi update | "Phiên bản không hợp lệ" |

## 12. Business rule liên quan
BR-TASK-01..18, BR-GEN-08 (version), BR-GEN-09 (mã unique), BR-MEET — chuyển action item thành task (BR-AI-03) tạo task `source=ACTION_ITEM`.

## 13. Phân quyền

| Hành động | Quyền |
|---|---|
| Tạo / sửa / xóa / giao việc | `task:create` / `task:update` / `task:delete` / `task:assign` (ADMIN, PM dự án) |
| Sửa task được giao (MEMBER) | `task:update` giới hạn: status/progress/notes của task mình là assignee |
| Bình luận / file | `task:comment` / `task:attachment` (ADMIN, PM, MEMBER) |
| Xem | `task:view` (ADMIN, PM, MEMBER, VIEWER — phạm vi dự án) |
| Xuất Excel | `task:export` (ADMIN, PM dự án) |

## 14. Audit log cần ghi
Tạo/sửa/xóa task; đổi assignee; chuyển trạng thái; đổi progress đáng kể; upload/delete attachment — kèm before/after (JSONB).

## 15. API dự kiến

| Method | Endpoint | Mục đích |
|---|---|---|
| POST | `/api/v1/tasks` | Tạo task |
| PUT | `/api/v1/tasks/{id}` | Cập nhật |
| GET | `/api/v1/tasks/{id}` | Chi tiết |
| DELETE | `/api/v1/tasks/{id}` | Xóa mềm |
| GET | `/api/v1/tasks` | Danh sách + filter + phân trang |
| PATCH | `/api/v1/tasks/{id}/status` | Chuyển trạng thái |
| PATCH | `/api/v1/tasks/{id}/progress` | Cập nhật tiến độ |
| POST | `/api/v1/tasks/{id}/comments` | Thêm bình luận |
| GET | `/api/v1/tasks/{id}/comments` | Danh sách bình luận |
| POST | `/api/v1/tasks/{id}/attachments` | Upload file |
| GET | `/api/v1/tasks/{id}/history` | Lịch sử thay đổi |
| GET | `/api/v1/tasks/my-tasks` | Việc của tôi |
| GET | `/api/v1/tasks/today` | Việc hôm nay |
| GET | `/api/v1/tasks/overdue` | Việc quá hạn |
| GET | `/api/v1/tasks/export` | Xuất Excel |

## 16. Acceptance criteria (Given/When/Then)

| # | Given | When | Then |
|---|---|---|---|
| AC-005-01 | PM, dữ liệu hợp lệ | Tạo task | `201` + `code` đúng định dạng `PRJ001-TASK-000001` |
| AC-005-02 | 2 request tạo task đồng thời | Tạo cùng lúc | 2 mã khác nhau, không trùng |
| AC-005-03 | `title` rỗng | Tạo task | `400` fieldErrors[title] |
| AC-005-04 | `title` > 200 ký tự | Tạo task | `400` |
| AC-005-05 | `dueDate < startDate` | Tạo task | `400` fieldErrors[dueDate] |
| AC-005-06 | `progress = -1` hoặc `101` | Cập nhật progress | `400` |
| AC-005-07 | Task status=REVIEW, progress=50 | Chuyển DONE | `400` (DONE phải 100) |
| AC-005-08 | Chuyển BLOCKED không kèm reason | Chuyển trạng thái | `400` |
| AC-005-09 | Assignee ngoài dự án | Tạo/giao task | `400` |
| AC-005-10 | Parent thuộc project khác | Tạo task con | `400` |
| AC-005-11 | Chọn task làm parent của chính nó (hoặc vòng lặp) | Tạo task con | `400` |
| AC-005-12 | Task đã xóa mềm | Sửa task | `404` |
| AC-005-13 | Sửa với version cũ | Cập nhật | `409` CONFLICT |
| AC-005-14 | MEMBER sửa task của người khác | Cập nhật | `403` |
| AC-005-15 | MEMBER sửa task được giao cho mình (status/progress) | Cập nhật | `200` |
| AC-005-16 | Task đổi status/assignee/progress nhiều lần | Xem lịch sử | Đủ các thay đổi kèm actor + thời gian |
| AC-005-17 | Task quá hạn chưa DONE | Lọc overdue=true | Có trong kết quả |
| AC-005-18 | Task hạn hôm nay (timezone user) | Gọi /tasks/today | Có trong kết quả |
| AC-005-19 | User được giao 3 task | Gọi /tasks/my-tasks | Đúng 3 task |
| AC-005-20 | Tìm keyword không có kết quả | Tìm kiếm | `200` page rỗng, total=0 |
| AC-005-21 | page = 999 khi tổng có 5 task | Gọi danh sách | `200` page rỗng (hoặc 400 theo quy ước chốt) |
| AC-005-22 | Sort field không hợp lệ | Gọi danh sách sort=xyz | `400` |
| AC-005-23 | 100 task khớp filter | Export | File Excel đủ 100 dòng + header |
| AC-005-24 | File 50MB | Upload attachment | `413` |
| AC-005-25 | Task REVIEW | PM chuyển DONE | `200`; progress=100, actualCompletedAt có giá trị |
| AC-005-26 | Chuyển DONE → TODO trực tiếp | Chuyển trạng thái | `400` (không hợp lệ theo state machine) |
