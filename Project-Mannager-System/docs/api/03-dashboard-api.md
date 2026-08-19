# API 03 — Dashboard (Tổng quan hằng ngày)

> Dự án: PM Daily Work Management | Trạng thái: Draft
> Nguồn: Prompt 06, `docs/02-functional-requirements.md` (FR-DASH-01), `docs/05-user-roles-permissions.md`
> **Quy ước chung**: prefix `/api/v1`, JSON ISO-8601 UTC, error response theo `docs/design/05-error-handling-design.md`, phân quyền theo `docs/05-user-roles-permissions.md`.

## 1. Mô tả tổng quan

Màn hình tổng quan hằng ngày của PM: 10 nhóm số liệu + 3 nhóm biểu đồ. Số liệu được **aggregate tại DB** (tránh N+1). Phạm vi dữ liệu: ADMIN/PM xem tất cả dự án; MEMBER/VIEWER chỉ các dự án mình tham gia (docs/05 quy tắc 8).

## 2. Danh sách endpoint

| Method | Endpoint | Phân quyền | Mục đích | FR |
|---|---|---|---|---|
| GET | `/api/v1/dashboard/summary` | `dashboard:view` | 10 nhóm số liệu | FR-DASH-01 |
| GET | `/api/v1/dashboard/task-stats` | `dashboard:view` | Biểu đồ tasksByStatus, tasksByPriority | FR-DASH-01 |
| GET | `/api/v1/dashboard/projects/progress` | `dashboard:view` | Biểu đồ projectProgress | FR-DASH-01 |

## 3. Chi tiết endpoint

### 3.1 GET `/api/v1/dashboard/summary`

- **Phân quyền**: `dashboard:view` (phạm vi: ADMIN/PM tất cả; MEMBER/VIEWER project của mình).
- **Query params**:

| Param | Type | Bắt buộc | Ghi chú |
|---|---|---|---|
| `projectId` | uuid | — | Lọc theo dự án; bỏ trống = tất cả (trong phạm vi quyền) |
| `fromDate` / `toDate` | date | — | Khoảng thời gian, ISO-8601; mặc định hôm nay |

- **Response `200`** — `DashboardSummaryResponse`:

```json
{
  "totalTasksToday": 12,
  "overdueTasks": 3,
  "upcomingTasks": 5,
  "inProgressTasks": 8,
  "blockedTasks": 1,
  "meetingsToday": 2,
  "pendingActionItems": 4,
  "highRisks": 2,
  "openIssues": 1,
  "upcomingMilestones": 3
}
```

| Field | Định nghĩa |
|---|---|
| `totalTasksToday` | Task có `dueDate` = hôm nay (theo timezone user) |
| `overdueTasks` | Task `dueDate` < hôm nay, status ≠ DONE/CANCELLED |
| `upcomingTasks` | Task `dueDate` trong 7 ngày tới (chưa DONE/CANCELLED) |
| `inProgressTasks` | Status = IN_PROGRESS |
| `blockedTasks` | Status = BLOCKED (hoặc `blocked = true`) |
| `meetingsToday` | Họp hôm nay (theo timezone user, chưa CANCELLED) |
| `pendingActionItems` | Action item status ≠ DONE/CANCELLED |
| `highRisks` | Risk status OPEN/MONITORING, level HIGH/CRITICAL |
| `openIssues` | Issue status ≠ RESOLVED/CLOSED/REJECTED |
| `upcomingMilestones` | Milestone plannedDate trong 30 ngày tới, status ≠ COMPLETED/CANCELLED |

- **Lỗi**: `403 ACCESS_DENIED` khi `projectId` nằm ngoài phạm vi; `400 VALIDATION_ERROR` (fromDate > toDate → `INVALID_DATE_RANGE`).

### 3.2 GET `/api/v1/dashboard/task-stats`

- **Query params**: như 3.1.
- **Response `200`**:

```json
{
  "tasksByStatus": [
    { "status": "TODO", "count": 3 },
    { "status": "IN_PROGRESS", "count": 8 },
    { "status": "BLOCKED", "count": 1 },
    { "status": "REVIEW", "count": 1 },
    { "status": "DONE", "count": 10 }
  ],
  "tasksByPriority": [
    { "priority": "HIGH", "count": 6 }
  ]
}
```

### 3.3 GET `/api/v1/dashboard/projects/progress`

- **Query params**: `projectId` (tùy chọn, nhiều lần).
- **Response `200`**:

```json
{
  "projects": [
    { "projectId": "00000000-0000-0000-0000-000000000301", "code": "PRJ001", "name": "App Mobile Banking", "progress": 45 }
  ]
}
```

- Tiến độ dự án lấy từ `projects.progress` (không tính lại theo task ở v1).

## 4. Traceability

| Endpoint | UC | AC |
|---|---|---|
| GET /dashboard/summary | UC-002 | AC-002-* |
| GET /dashboard/task-stats | UC-002 | AC-002-* |
| GET /dashboard/projects/progress | UC-002 | AC-002-* |

## 5. Ghi chú

- 3 endpoint có thể gọi song song từ client (single-fetch pattern: gọi 1 lúc 3 request, render khi đủ); không gộp thành 1 endpoint lớn để dễ cache riêng từng nhóm.
- Số liệu "hôm nay" tính theo timezone người dùng (`X-Timezone` header hoặc offset param — chốt ở Prompt 14).
