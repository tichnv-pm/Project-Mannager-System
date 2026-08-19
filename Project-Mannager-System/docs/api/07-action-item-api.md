# API 07 — Action Item

> Dự án: PM Daily Work Management | Trạng thái: Draft
> Nguồn: Prompt 06, `docs/02-functional-requirements.md` (FR-AI-01..04)
> **Quy ước chung**: prefix `/api/v1`, JSON ISO-8601 UTC, pagination `page/size/sort`, error response theo `docs/design/05-error-handling-design.md`.

## 1. Mô tả tổng quan

Ghi nhận việc cần làm từ cuộc họp, gán người phụ trách và hạn; theo dõi quá hạn; chuyển thành task (liên kết 1–1, chống trùng bằng unique index `uk_action_items_linked_task`).

## 2. Danh sách endpoint

| Method | Endpoint | Phân quyền | Mục đích | FR |
|---|---|---|---|---|
| GET | `/api/v1/action-items` | `action-item:view` | Danh sách, lọc | FR-AI-04 |
| GET | `/api/v1/action-items/overdue` | `action-item:view` | Quá hạn chưa đóng | FR-AI-04 |
| POST | `/api/v1/action-items` | `action-item:manage` | Tạo | FR-AI-01 |
| GET | `/api/v1/action-items/{id}` | `action-item:view` | Chi tiết | FR-AI-02 |
| PUT | `/api/v1/action-items/{id}` | `action-item:manage` / assignee | Cập nhật | FR-AI-02 |
| DELETE | `/api/v1/action-items/{id}` | `action-item:manage` | Xóa | FR-AI-02 |
| POST | `/api/v1/action-items/{id}/convert-to-task` | `action-item:manage` | Chuyển thành task | FR-AI-03 |

## 3. Chi tiết endpoint

### 3.1 GET `/api/v1/action-items`

- **Query params**: `page, size, sort` (whitelist: `title, dueDate, priority, status, createdAt`), `projectId`, `status`, `assigneeId`, `overdue` (boolean).
- **Response `200`** — `PageResponse<ActionItemResponse>` (xem 3.3).

### 3.2 GET `/api/v1/action-items/overdue`

- Action item `dueDate` < hôm nay, status ≠ DONE/CANCELLED.
- **Response `200`** — `PageResponse<ActionItemResponse>`.

### 3.3 POST `/api/v1/action-items`

- **Phân quyền**: `action-item:manage` (ADMIN, PM dự án). Audit: có.
- **Request body** — `ActionItemCreateRequest`:

| Field | Type | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `meetingId` | uuid | ✔ | Phải tồn tại |
| `projectId` | uuid | ✔ | Phải khớp project của meeting (FR-AI-01) |
| `title` | string | ✔ | ≤ 200 ký tự |
| `description` | string | — | — |
| `assigneeId` | uuid | ✔ | Thuộc project |
| `dueDate` | date | — | — |
| `priority` | string | — | Mặc định `MEDIUM` |

- **Response `201`** — `ActionItemResponse`:

```json
{
  "id": "00000000-0000-0000-0000-000000000701",
  "meetingId": "00000000-0000-0000-0000-000000000601",
  "projectId": "00000000-0000-0000-0000-000000000301",
  "title": "Theo dõi trạng thái fix lỗi iOS hằng ngày",
  "assignee": { "id": "00000000-0000-0000-0000-000000000005", "fullName": "Phạm Thu Thảo" },
  "dueDate": "2026-08-03",
  "priority": "HIGH",
  "status": "OPEN",
  "progress": 0,
  "linkedTaskId": null,
  "createdAt": "2026-08-01T03:00:00Z",
  "version": 0
}
```

- **Lỗi**: `400 VALIDATION_ERROR`; `400 NOT_PROJECT_MEMBER`; `400 BAD_REQUEST` (project không khớp meeting); `403`, `404`.
- **Hậu điều kiện**: notification `ACTION_ITEM_ASSIGNED` cho assignee.

### 3.4 GET `/api/v1/action-items/{id}`

- **Response `200`**. **Lỗi**: `403`, `404`.

### 3.5 PUT `/api/v1/action-items/{id}`

- **Phân quyền**: `action-item:manage` (ADMIN, PM dự án) — mọi trường; assignee — chỉ `status/progress`. Audit: có.
- **Request body** — `ActionItemUpdateRequest`: `title, description, dueDate, priority, status, progress` + `version`.
- **Response `200`**. **Lỗi**: `404`, `403`, `409 CONFLICT`.

### 3.6 DELETE `/api/v1/action-items/{id}`

- **Phân quyền**: `action-item:manage`. **Response `204`**. **Lỗi**: `403`, `404`. Audit: có.

### 3.7 POST `/api/v1/action-items/{id}/convert-to-task`

- **Phân quyền**: `action-item:manage`. Audit: có (chuyển thành task).
- **Request body**: `{ "dueDate": "2026-08-03", "priority": "HIGH" }` (tùy chọn; mặc định lấy từ action item).
- **Response `201`** — `TaskResponse` (source = `ACTION_ITEM`, assignee/tiêu đề/dueDate/priority kế thừa).
- **Hậu điều kiện**: `linked_task_id` của action item được gắn; không thể chuyển lại.
- **Lỗi**: `409 ALREADY_LINKED` (đã có linkedTask); `400 BAD_REQUEST` (action item đã DONE/CANCELLED); `403`, `404`.

## 4. Traceability

| Endpoint | UC | AC |
|---|---|---|
| GET /action-items, /overdue, /{id} | UC-007 | AC-007-* |
| POST/PUT/DELETE | UC-007 | AC-007-* |
| convert-to-task | UC-007 | AC-007-* |
