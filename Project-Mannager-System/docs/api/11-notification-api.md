# API 11 — Thông báo (Notification)

> Dự án: PM Daily Work Management | Trạng thái: Draft
> Nguồn: Prompt 06, `docs/02-functional-requirements.md` (FR-NOTIF-01..03)
> **Quy ước chung**: prefix `/api/v1`, JSON ISO-8601 UTC, pagination `page/size/sort`, error response theo `docs/design/05-error-handling-design.md`.

## 1. Mô tả tổng quan

Người dùng xem thông báo của mình và đánh dấu đã đọc. Thông báo **chỉ sinh tự động** bởi hệ thống (khi giao việc, bình luận, mời họp...) và scheduled job (deadline/overdue — FR-NOTIF-03); không có endpoint tạo thủ công. Dedupe theo `(recipient_id, type, entity_id, ngày)` — unique index `uk_notifications_daily` (job chạy lại trong ngày không tạo trùng).

## 2. Danh sách endpoint

| Method | Endpoint | Phân quyền | Mục đích | FR |
|---|---|---|---|---|
| GET | `/api/v1/notifications` | `notification:view` | Danh sách của tôi | FR-NOTIF-01 |
| GET | `/api/v1/notifications/unread-count` | `notification:view` | Số chưa đọc | FR-NOTIF-01 |
| PUT | `/api/v1/notifications/{id}/read` | `notification:manage` | Đánh dấu đã đọc (1 cái) | FR-NOTIF-02 |
| PUT | `/api/v1/notifications/read-all` | `notification:manage` | Đánh dấu đã đọc (tất cả) | FR-NOTIF-02 |

> FR-NOTIF-03 (sinh tự động) không có endpoint — thực hiện trong service/job (Prompt 08/11).

## 3. Chi tiết endpoint

### 3.1 GET `/api/v1/notifications`

- **Phân quyền**: `notification:view` — chỉ dữ liệu của chính user (không truy vấn chéo).
- **Query params**: `page, size` (mặc định 20), `unreadOnly` (boolean, mặc định false), `type` (lọc theo enum notification type).
- **Response `200`** — `PageResponse<NotificationResponse>`:

```json
{
  "content": [
    {
      "id": "00000000-0000-0000-0000-000000001102",
      "type": "TASK_OVERDUE",
      "title": "Công việc đã quá hạn",
      "content": "Fix lỗi mất phiên đăng nhập trên iOS",
      "entityType": "TASK",
      "entityId": "00000000-0000-0000-0000-000000000403",
      "isRead": false,
      "readAt": null,
      "createdAt": "2026-08-01T00:00:00Z"
    }
  ],
  "page": 0, "size": 20, "totalElements": 5, "totalPages": 1, "hasNext": false, "hasPrevious": false
}
```

### 3.2 GET `/api/v1/notifications/unread-count`

- **Response `200`**: `{ "unreadCount": 2 }`.

### 3.3 PUT `/api/v1/notifications/{id}/read`

- **Phân quyền**: `notification:manage` — chỉ thông báo của chính user.
- **Response `200`** — `NotificationResponse` (isRead = true, readAt = now()).
- **Lỗi**: `404 NOT_FOUND` (không tồn tại hoặc không phải của user — không trả lộ thông tin), `403`.

### 3.4 PUT `/api/v1/notifications/read-all`

- **Response `200`**: `{ "updatedCount": 3 }`.

## 4. Traceability

| Endpoint | UC | AC |
|---|---|---|
| GET /notifications, /unread-count | UC-011 | AC-011-* |
| PUT /{id}/read, /read-all | UC-011 | AC-011-* |

## 5. Ghi chú thiết kế

- Notification `type` enum: `TASK_ASSIGNED, TASK_DUE_SOON, TASK_OVERDUE, TASK_COMMENTED, MEETING_INVITED, ACTION_ITEM_ASSIGNED` (docs/02 mục 1.12).
- Scheduled job (sau Prompt 11): tìm task `dueDate` trong N ngày tới (TASK_DUE_SOON) và task quá hạn chưa đóng (TASK_OVERDUE) → insert notification với `(recipient, type, entity_id, created_at::date)` dedupe.
- WebSocket/SSE push realtime: xem `docs/design/03-frontend-architecture.md` (polling ngắn hoặc SSE — chốt ở Prompt 14).
