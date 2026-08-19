# API 05 — Công việc (Task)

> Dự án: PM Daily Work Management | Trạng thái: Draft
> Nguồn: Prompt 06, `docs/02-functional-requirements.md` (FR-TASK-01..17), `docs/04-business-rules.md` (BR-TASK), `docs/05-user-roles-permissions.md`
> **Quy ước chung**: prefix `/api/v1`, JSON ISO-8601 UTC, pagination `page/size/sort`, error response theo `docs/design/05-error-handling-design.md`, phân quyền theo `docs/05-user-roles-permissions.md`.

## 1. Mô tả tổng quan

Module lớn nhất: CRUD task, giao việc, chuyển trạng thái, tiến độ, blocker, task con, tags, bình luận, file đính kèm, lịch sử thay đổi (đọc từ `audit_logs`), xuất Excel. Mã tự sinh `PRJXXX-TASK-000001` (BR-TASK-14, sinh an toàn concurrent — `docs/database/04-database-rules.md` mục 5).

### 1.1 State machine (FR-TASK-07)

```
TODO ──→ IN_PROGRESS ──→ REVIEW ──→ DONE
  │           │            │  │
  │           └──→ BLOCKED ←┘  └──→ IN_PROGRESS (review lại)
  └─────→ CANCELLED (mọi trạng thái)
```
- `BLOCKED`: từ bất kỳ trạng thái nào, bắt buộc `blockerReason`.
- `DONE`: bắt buộc `progress = 100` + ghi `actualCompletedAt` (BR-TASK-04/06).
- Mọi chuyển trạng thái ngoài sơ đồ trên → `400 INVALID_STATUS_TRANSITION`.

## 2. Danh sách endpoint

| Method | Endpoint | Phân quyền | Mục đích | FR |
|---|---|---|---|---|
| GET | `/api/v1/tasks` | `task:view` | Danh sách, lọc, tìm kiếm | FR-TASK-04 |
| GET | `/api/v1/tasks/my-tasks` | `task:view` | Việc của tôi (assignee = me) | FR-TASK-14 |
| GET | `/api/v1/tasks/today` | `task:view` | Việc hạn hôm nay | FR-TASK-15 |
| GET | `/api/v1/tasks/overdue` | `task:view` | Việc quá hạn | FR-TASK-16 |
| GET | `/api/v1/tasks/export` | `task:export` | Xuất Excel (filter hiện tại) | FR-TASK-17 |
| POST | `/api/v1/tasks` | `task:create` | Tạo task | FR-TASK-01 |
| GET | `/api/v1/tasks/{id}` | `task:view` | Chi tiết | FR-TASK-03 |
| PUT | `/api/v1/tasks/{id}` | `task:update` | Cập nhật | FR-TASK-02 |
| DELETE | `/api/v1/tasks/{id}` | `task:delete` | Xóa mềm | FR-TASK-05 |
| PUT | `/api/v1/tasks/{id}/assignee` | `task:assign` | Giao việc | FR-TASK-06 |
| PUT | `/api/v1/tasks/{id}/status` | `task:update` / assignee | Chuyển trạng thái | FR-TASK-07 |
| PUT | `/api/v1/tasks/{id}/progress` | `task:update` / assignee | Cập nhật tiến độ | FR-TASK-08 |
| PUT | `/api/v1/tasks/{id}/blocker` | `task:update` / assignee | Đánh dấu blocker | FR-TASK-09 |
| GET | `/api/v1/tasks/{id}/children` | `task:view` | Task con | FR-TASK-12 |
| PUT | `/api/v1/tasks/{id}/tags` | `task:update` | Gán tag | FR-TASK-02 |
| PUT | `/api/v1/tasks/{id}/collaborators` | `task:assign` | Người phối hợp | FR-TASK-01 |
| PUT | `/api/v1/tasks/{id}/watchers` | `task:update` | Người theo dõi | FR-TASK-01 |
| GET | `/api/v1/tasks/{id}/comments` | `task:view` | Bình luận | FR-TASK-10 |
| POST | `/api/v1/tasks/{id}/comments` | `task:comment` | Thêm bình luận | FR-TASK-10 |
| PUT | `/api/v1/tasks/{id}/comments/{commentId}` | `task:comment` | Sửa bình luận | FR-TASK-10 |
| DELETE | `/api/v1/tasks/{id}/comments/{commentId}` | `task:comment` | Xóa bình luận | FR-TASK-10 |
| GET | `/api/v1/tasks/{id}/attachments` | `task:view` | File đính kèm | FR-TASK-11 |
| POST | `/api/v1/tasks/{id}/attachments` | `task:attachment` | Upload file | FR-TASK-11 |
| DELETE | `/api/v1/tasks/{id}/attachments/{attachmentId}` | `task:attachment` | Xóa file | FR-TASK-11 |
| GET | `/api/v1/tasks/{id}/history` | `task:view` | Lịch sử thay đổi | FR-TASK-13 |

> Lưu ý routing: `my-tasks`, `today`, `overdue`, `export` phải được khai báo trước `{id}`.

## 3. Chi tiết endpoint

### 3.1 GET `/api/v1/tasks` (danh sách)

- **Query params**:

| Param | Type | Ghi chú |
|---|---|---|
| `page`, `size` | int | page ≥ 0, size 1–100 |
| `sort` | string | Whitelist: `code, title, status, priority, dueDate, progress, createdAt, updatedAt` |
| `keyword` | string | LIKE trên title/code |
| `projectId` | uuid | Bắt buộc đối với MEMBER/VIEWER (phạm vi quyền) |
| `assigneeId` | uuid | Lọc người thực hiện |
| `status` | string[] | Nhiều giá trị |
| `priority` | string[] | — |
| `type` | string[] | — |
| `tagId` | uuid | Lọc theo tag |
| `startDateFrom` / `startDateTo` | date | Khoảng startDate |
| `dueDateFrom` / `dueDateTo` | date | Khoảng dueDate |
| `overdue` | boolean | `true` = quá hạn chưa đóng |
| `blocked` | boolean | `true` = đang bị chặn |

- **Response `200`** — `PageResponse<TaskSummaryResponse>` (xem 3.3). **Lỗi**: `400` (sort ngoài whitelist, date range sai → `INVALID_DATE_RANGE`); `403` (project ngoài phạm vi).

### 3.2 GET `/api/v1/tasks/my-tasks` | `/today` | `/overdue`

- **Phân quyền**: `task:view`, dữ liệu theo project của user.
- **Params**: `page, size, sort` (whitelist như 3.1) + `projectId` (tùy chọn).
- `today`: task có `dueDate` = hôm nay theo timezone user; `overdue`: `dueDate` < hôm nay và status ≠ DONE/CANCELLED.
- **Response `200`** — `PageResponse<TaskSummaryResponse>`.

### 3.3 POST `/api/v1/tasks` (tạo)

- **Phân quyền**: `task:create` (ADMIN, PM dự án; PROJECT_MEMBER theo ma trận mặc định có — docs/05). Audit: có (tạo task).
- **Request body** — `TaskCreateRequest`:

| Field | Type | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `projectId` | uuid | ✔ | Project chưa xóa mềm |
| `parentTaskId` | uuid | — | Cùng project (BR-TASK-07), không vòng lặp (BR-TASK-08) |
| `title` | string | ✔ | ≤ 200 ký tự |
| `description` | string | — | — |
| `assigneeId` | uuid | — | Thuộc project (BR-TASK-11) |
| `collaboratorIds` | uuid[] | — | Thuộc project |
| `watcherIds` | uuid[] | — | Thuộc project |
| `status` | string | — | Mặc định `TODO` |
| `priority` | string | — | Mặc định `MEDIUM` |
| `type` | string | — | `FEATURE, BUG, IMPROVEMENT, TASK, OTHER`, mặc định `TASK` |
| `source` | string | — | `MANUAL, MEETING, ACTION_ITEM, ISSUE, OTHER`, mặc định `MANUAL` |
| `startDate` / `dueDate` | date | — | dueDate ≥ startDate (BR-TASK-02) |
| `progress` | int | — | 0–100 (BR-TASK-03) |
| `blocked` + `blockerReason` | boolean/string | — | blocked=true ⇒ bắt buộc reason (BR-TASK-10) |
| `estimateMinutes` | int | — | ≥ 0 |
| `notes` | string | — | — |
| `tagIds` | uuid[] | — | — |

- **Response `201`** — `TaskResponse`:

```json
{
  "id": "00000000-0000-0000-0000-000000000401",
  "code": "PRJ001-TASK-000001",
  "projectId": "00000000-0000-0000-0000-000000000301",
  "projectCode": "PRJ001",
  "projectName": "App Mobile Banking",
  "parentTaskId": null,
  "title": "Xây dựng màn hình login",
  "status": "TODO",
  "priority": "HIGH",
  "type": "FEATURE",
  "source": "MANUAL",
  "assignee": { "id": "00000000-0000-0000-0000-000000000003", "fullName": "Trần Thị Lan" },
  "reporter": { "id": "00000000-0000-0000-0000-000000000002", "fullName": "Nguyễn Văn Minh" },
  "progress": 0,
  "blocked": false,
  "startDate": "2026-07-20",
  "dueDate": "2026-08-05",
  "estimateMinutes": 480,
  "tags": [{ "id": "00000000-0000-0000-0000-000000000502", "name": "backend", "color": "#2196f3" }],
  "collaborators": [],
  "watchers": [],
  "createdAt": "2026-07-20T02:00:00Z",
  "updatedAt": "2026-07-20T02:00:00Z",
  "version": 0
}
```

- **Lỗi**: `400 VALIDATION_ERROR`, `400 NOT_PROJECT_MEMBER` (assignee ngoài project), `400 PARENT_TASK_PROJECT_MISMATCH`, `400 CIRCULAR_PARENT`, `404 NOT_FOUND`, `403 ACCESS_DENIED`.

### 3.4 GET `/api/v1/tasks/{id}`

- **Response `200`** — `TaskResponse` (kèm `comments` tổng hợp? Không — comments lấy riêng qua endpoint 3.11; chỉ kèm `attachmentCount`, `commentCount`). **Lỗi**: `404`, `403`.

### 3.5 PUT `/api/v1/tasks/{id}` (cập nhật tổng hợp)

- **Phân quyền**: `task:update`; PROJECT_MEMBER chỉ task mình là assignee và chỉ trường `status/progress/notes` (docs/05 quy tắc 2). Audit: có.
- **Request body** — `TaskUpdateRequest`: các field như Create (chỉ gửi field muốn sửa) + `version` (bắt buộc).
- **Response `200`** — `TaskResponse`. **Lỗi**: `400` (BLOCKED thiếu reason, DONE thiếu progress 100...), `404`, `403`, `409 CONFLICT` (version cũ).
- **Hậu điều kiện**: ghi `audit_logs` (before/after) → dữ liệu cho GET /history; sinh notification cho assignee/watchers nếu field liên quan thay đổi.

### 3.6 DELETE `/api/v1/tasks/{id}`

- **Phân quyền**: `task:delete` (ADMIN, PM dự án). Audit: có.
- **Response `204`**. Xóa mềm; chính sách task con chờ xác nhận (BR-TASK-17 — mặc định v1: chặn xóa cha khi còn task con, yêu cầu xóa con trước).
- **Lỗi**: `400 BAD_REQUEST` (còn task con), `404`, `403`.

### 3.7 PUT `/api/v1/tasks/{id}/assignee`

- **Phân quyền**: `task:assign`. Audit: có (đổi người thực hiện).
- **Request body**: `{ "assigneeId": "..." }` (có thể `null` để gỡ người thực hiện).
- **Response `200`** — `TaskResponse`. **Lỗi**: `400 NOT_PROJECT_MEMBER`; `404`; `403`.
- **Hậu điều kiện**: sinh notification `TASK_ASSIGNED` cho assignee mới.

### 3.8 PUT `/api/v1/tasks/{id}/status`

- **Phân quyền**: `task:update` (ADMIN, PM dự án) hoặc assignee. Audit: có (từ → đến, ai, khi nào).
- **Request body**: `{ "status": "DONE" }`; kèm `blockerReason` khi chuyển `BLOCKED`.
- **Response `200`** — `TaskResponse`. Hậu điều kiện: DONE ⇒ progress = 100, `actualCompletedAt = now()`; BLOCKED ⇒ `blocked = true`.
- **Lỗi**: `400 INVALID_STATUS_TRANSITION`; `400 BLOCKER_REASON_REQUIRED`; `400 PROGRESS_REQUIRED_FOR_DONE`; `403` (assignee không được đổi trạng thái task không phải của mình).

### 3.9 PUT `/api/v1/tasks/{id}/progress`

- **Phân quyền**: như 3.8. Audit: có (thay đổi đáng kể).
- **Request body**: `{ "progress": 75 }` (0–100).
- **Response `200`**. **Lỗi**: `400` (ngoài 0–100); `400 PROGRESS_REQUIRED_FOR_DONE` (DONE mà < 100).

### 3.10 PUT `/api/v1/tasks/{id}/blocker`

- **Phân quyền**: như 3.8. Audit: có.
- **Request body**: `{ "blocked": true, "blockerReason": "Chờ bên thứ ba" }` — reason bắt buộc khi blocked=true.
- **Response `200`**. **Lỗi**: `400 BLOCKER_REASON_REQUIRED`.

### 3.11 GET `/api/v1/tasks/{id}/children` + PUT `/tags` + PUT `/collaborators` + PUT `/watchers`

- `GET /children`: `task:view` → `List<TaskSummaryResponse>`.
- `PUT /tags`: `{ "tagIds": [...] }` → thay thế toàn bộ tags; tags phải tồn tại (404).
- `PUT /collaborators`: `{ "userIds": [...] }` (`task:assign`) — thay thế toàn bộ; user phải thuộc project (`400 NOT_PROJECT_MEMBER`).
- `PUT /watchers`: `{ "userIds": [...] }` (`task:update`) — thay thế toàn bộ.

### 3.12 Bình luận

| Endpoint | Body | Response | Lỗi |
|---|---|---|---|
| GET `/tasks/{id}/comments` | — | `200` `List<CommentResponse>` (tăng dần createdAt) | 403/404 |
| POST `/tasks/{id}/comments` | `{ "content": "..." }` (1–2000 ký tự) | `201` `CommentResponse` | `400 VALIDATION_ERROR` (BR-TASK-16) |
| PUT `/tasks/{id}/comments/{commentId}` | `{ "content": "..." }` | `200` | 403 (không phải tác giả), 404 |
| DELETE `/tasks/{id}/comments/{commentId}` | — | `204` | 403 (không phải tác giả), 404 |

- Hậu điều kiện POST: notification `TASK_COMMENTED` cho assignee/watchers (trừ người viết). Audit: không (createdBy trên comment).

### 3.13 File đính kèm

| Endpoint | Body | Response | Ghi chú |
|---|---|---|---|
| GET `/tasks/{id}/attachments` | — | `200` `List<AttachmentResponse>` | Metadata + URL tải |
| POST `/tasks/{id}/attachments` | `multipart/form-data` field `file` | `201` `AttachmentResponse` | ≤ 10MB (BR-TASK-15), whitelist mime type; lưu path server sinh UUID |
| DELETE `/tasks/{id}/attachments/{attachmentId}` | — | `204` | Xóa file khỏi storage + bản ghi |

- `AttachmentResponse`: `{ id, fileName, filePath (URL tải), contentType, sizeBytes, uploadedBy, createdAt }`. **Lỗi**: `413 PAYLOAD_TOO_LARGE`, `400` (sai mime type), `403`, `404`. Audit: có cho upload/delete.

### 3.14 GET `/api/v1/tasks/{id}/history`

- **Phân quyền**: `task:view`.
- **Response `200`** — `List<TaskHistoryEntry>` đọc từ `audit_logs` (entity_type = `TASK`, entity_id = taskId):

```json
[
  {
    "changedAt": "2026-07-29T08:00:00Z",
    "changedBy": "00000000-0000-0000-0000-000000000005",
    "changedByUsername": "member3",
    "action": "task:update",
    "changes": { "status": { "from": "IN_PROGRESS", "to": "BLOCKED" }, "blocked": { "from": false, "to": true } }
  }
]
```

### 3.15 GET `/api/v1/tasks/export`

- **Phân quyền**: `task:export` (ADMIN, PM dự án). Audit: có (export).
- **Query params**: như 3.1 (filter hiện tại).
- **Response `200`** — `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`, tên file `tasks-<yyyyMMdd-HHmmss>.xlsx` (header `Content-Disposition`).
- **Cột**: Code, Tiêu đề, Trạng thái, Ưu tiên, Người thực hiện, Ngày bắt đầu, Hạn, Tiến độ, Blocker, Project.
- **Lỗi**: `400 EXPORT_LIMIT_EXCEEDED` (quá 10.000 dòng — BR-TASK-18).

## 4. Traceability

| Endpoint | UC | AC |
|---|---|---|
| GET /tasks, GET /tasks/{id}, my-tasks/today/overdue | UC-005 | AC-005-* |
| POST/PUT/DELETE /tasks | UC-005 | AC-005-* |
| status/progress/blocker | UC-005 | AC-005-* |
| comments | UC-005 | AC-005-* |
| attachments | UC-005 | AC-005-* |
| export | UC-005 | AC-005-* |
| history | UC-005 | AC-005-* |

## 5. Điểm cần xác nhận

1. BR-TASK-17 — xóa task có con: mặc định v1 = chặn xóa khi còn con.
2. FR-TASK-02 — `progress = 100` có tự động chuyển DONE không (mặc định: không, chỉ gợi ý).
3. Whitelist mime type upload (mặc định đề xuất: png, jpg, jpeg, gif, pdf, xlsx, xls, docx, txt).
