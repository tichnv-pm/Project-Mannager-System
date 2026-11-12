# API 12 — Báo cáo & Nhật ký hoạt động (Report & Audit)

> Dự án: PM Daily Work Management | Trạng thái: Draft
> Nguồn: Prompt 06, `docs/02-functional-requirements.md` (FR-REP-01..06, FR-AUD-01), `docs/design/06-logging-audit-design.md`
> **Quy ước chung**: prefix `/api/v1`, JSON ISO-8601 UTC, pagination `page/size/sort`, error response theo `docs/design/05-error-handling-design.md`.

## 1. Mô tả tổng quan

Báo cáo tổng hợp (aggregate tại DB, tránh N+1) cho ADMIN/PM + xuất file; nhật ký hoạt động chỉ ADMIN xem. Report chỉ đọc dữ liệu **chưa xóa mềm**.

## 2. Danh sách endpoint

| Method | Endpoint | Phân quyền | Mục đích | FR |
|---|---|---|---|---|
| GET | `/api/v1/reports/tasks-by-status` | `report:view` | Task theo trạng thái | FR-REP-01 |
| GET | `/api/v1/reports/tasks-by-assignee` | `report:view` | Task theo người thực hiện | FR-REP-02 |
| GET | `/api/v1/reports/overdue-tasks` | `report:view` | Danh sách task quá hạn | FR-REP-03 |
| GET | `/api/v1/reports/project-progress` | `report:view` | Tiến độ dự án | FR-REP-04 |
| GET | `/api/v1/reports/risk-issue-summary` | `report:view` | Tổng hợp risk & issue | FR-REP-05 |
| GET | `/api/v1/reports/quality-kpi` | `report:view` | KPI chất lượng (defect density, escape rate) | GĐ3 cải tiến |
| GET | `/api/v1/reports/export` | `report:export` | Xuất CSV/Excel báo cáo | FR-REP-06 |
| POST | `/api/v1/projects/{projectId}/report-share-links` | `report:share` | Tạo share-link cho stakeholder ngoài tổ chức | GĐ4 cải tiến |
| GET | `/api/v1/projects/{projectId}/report-share-links` | `report:share` | Danh sách share-link của dự án | GĐ4 cải tiến |
| DELETE | `/api/v1/report-share-links/{id}` | `report:share` | Thu hồi share-link | GĐ4 cải tiến |
| GET | `/api/v1/public/shared-reports/{token}` | public (không auth) | Xem báo cáo tiến độ qua share-link | GĐ4 cải tiến |
| GET | `/api/v1/audit-logs` | `audit:view` | Nhật ký hoạt động | FR-AUD-01 |

## 3. Chi tiết endpoint

### 3.1 GET `/api/v1/reports/tasks-by-status`

- **Query params**: `projectId` (bắt buộc), `fromDate`/`toDate` (mặc định 30 ngày).
- **Response `200`**:

```json
{ "items": [ { "status": "TODO", "count": 3 }, { "status": "DONE", "count": 10 } ] }
```

- **Lỗi**: `400 VALIDATION_ERROR` / `INVALID_DATE_RANGE`; `403` (project ngoài phạm vi).

### 3.2 GET `/api/v1/reports/tasks-by-assignee`

- **Query params**: như 3.1.
- **Response `200`**:

```json
{ "items": [ { "assigneeId": "00000000-0000-0000-0000-000000000003", "fullName": "Trần Thị Lan", "count": 5, "doneCount": 2 } ] }
```

### 3.3 GET `/api/v1/reports/overdue-tasks`

- **Query params**: `projectId` (bắt buộc), `page`, `size` (mặc định 50, tối đa 100).
- **Response `200`** — `PageResponse<TaskSummaryResponse>`: task `dueDate` < hôm nay, status ≠ DONE/CANCELLED, sắp theo `dueDate asc`.

### 3.4 GET `/api/v1/reports/project-progress`

- **Query params**: `projectId` (nhiều giá trị, tối đa 50).
- **Response `200`**:

```json
{
  "items": [
    { "projectId": "00000000-0000-0000-0000-000000000301", "code": "PRJ001", "name": "App Mobile Banking",
      "progress": 45, "totalTasks": 20, "doneTasks": 9 }
  ]
}
```

- Tiến độ: ưu tiên `projects.progress`; `totalTasks/doneTasks` đếm theo task chưa xóa.

### 3.5 GET `/api/v1/reports/risk-issue-summary`

- **Query params**: `projectId` (bắt buộc).
- **Response `200`**:

```json
{
  "openRisks": 2, "openIssues": 1,
  "risksByLevel": [ { "level": "CRITICAL", "count": 1 } ],
  "issuesBySeverity": [ { "severity": "CRITICAL", "count": 1 } ]
}
```

### 3.6 GET `/api/v1/reports/quality-kpi` (GĐ3 cải tiến)

- **Query params**: `projectId` (bắt buộc).
- **Response `200`**:

```json
{
  "totalIssues": 3, "issuesFoundInQa": 2, "issuesEscaped": 1, "escapeRatePercent": 33.33,
  "completedTasks": 1, "defectDensityPerTask": 3.0,
  "issuesBySeverity": [ { "severity": "CRITICAL", "count": 1 } ]
}
```

- **Escape rate**: % Issue có `testCaseId = NULL` (không gắn với 1 test case QA — tức không bị phát hiện qua QA testing trước đó, coi như "lọt" ra ngoài) trên tổng số Issue của dự án.
- **Defect density**: tổng số Issue / số task đã `DONE` của dự án (0 nếu chưa có task DONE nào).

### 3.7 GET `/api/v1/reports/export`

- **Phân quyền**: `report:export`. Audit: có (export).
- **Query params**: `report` (bắt buộc: `tasks-by-status | tasks-by-assignee | overdue-tasks | project-progress | risk-issue-summary`), `format` (`xlsx` mặc định, `csv`), các filter của báo cáo tương ứng.
- **Response `200`** — file `report-<report>-<yyyyMMdd-HHmmss>.<ext>` (`Content-Disposition`).
- **Streaming (v1.1)**: endpoint trả trực tiếp qua `HttpServletResponse.getOutputStream()` — service ghi CSV vào `OutputStream` bằng `PrintWriter` UTF-8 autoflush, không giữ toàn bộ trong RAM (thay `byte[]` cũ).
- **Lỗi**: `400 EXPORT_LIMIT_EXCEEDED` (quá 10.000 dòng — FR-REP-06); `400 VALIDATION_ERROR` (report không hợp lệ).

### 3.8 Share-link báo cáo tiến độ cho stakeholder ngoài tổ chức (GĐ4 cải tiến)

Cho PM/ADMIN chia sẻ % tiến độ dự án (từ `project-progress`, §3.4) với người ngoài tổ chức (khách hàng, nhà đầu tư...) qua link công khai, không cần tài khoản. Token công khai **chính là `id`** (UUID) của share-link — cùng quy ước "UUID không đoán được" dùng cho mọi entity khác trong hệ thống, không có cột token riêng.

- **POST `/api/v1/projects/{projectId}/report-share-links`** (`report:share`, nghiêm hơn `report:view` vì là hành động public-facing — chỉ ADMIN/PM dự án, không có MEMBER). Audit: có.
  - Request: `{ "label": "Báo cáo cho khách hàng ABC", "expiresAt": "2026-12-31" }` (cả 2 field tùy chọn; `expiresAt` tính hết ngày UTC đó).
  - Response `201` — `ReportShareLinkResponse`: `{ id, projectId, label, expiresAt, active, createdBy, createdAt }`. `id` dùng trực tiếp trong URL công khai.
- **GET `/api/v1/projects/{projectId}/report-share-links`** (`report:share`) — danh sách link của 1 dự án, mới nhất trước.
- **DELETE `/api/v1/report-share-links/{id}`** (`report:share`) — thu hồi (soft delete). `204`. Sau khi thu hồi, link công khai trả `404` ngay.
- **GET `/api/v1/public/shared-reports/{token}`** — **không cần đăng nhập** (`permitAll`, cùng mẫu `/api/v1/public/webhooks/**`), rate-limit riêng theo IP (mặc định 30 req/60s, chống dò token — `RateLimitFilter`). Response `200` — `PublicProjectProgressResponse`: `{ projectCode, projectName, label, progress, totalTasks, doneTasks, generatedAt }` — **chỉ** các field tổng hợp này, không có gì khác từ dự án. **Lỗi**: `404 NOT_FOUND` cho cả 3 trường hợp (chưa từng tồn tại / đã thu hồi / đã hết hạn) — **không phân biệt** qua message khác nhau, tránh lộ thông tin cho người đang dò token.
- **Frontend**: quản lý (tạo/copy/thu hồi) ở tab "📈 Tiến độ dự án" trong màn hình Report (`report-list.component`); xem công khai ở route riêng `/public/shared-reports/:token` (`public-shared-report.component`, không nằm trong layout/`authGuard` chính) — có nút "In / Lưu PDF" dùng `window.print()` (không thêm thư viện PDF mới).

### 3.9 GET `/api/v1/audit-logs`

- **Phân quyền**: `audit:view` (chỉ ADMIN). Audit: không (bản thân là log).
- **Query params**:

| Param | Type | Ghi chú |
|---|---|---|
| `page`, `size` | int | Mặc định 20, size ≤ 100 |
| `sort` | string | Whitelist: `createdAt, action, actorUsername`; mặc định `createdAt,desc` |
| `actorId` | uuid | Lọc theo người thực hiện |
| `action` | string | Lọc theo hành động (VD `task:update`) |
| `entityType` | string | `TASK, PROJECT, USER, ...` |
| `entityId` | uuid | Lọc theo đối tượng |
| `fromDate` / `toDate` | datetime | Khoảng thời gian |

- **Response `200`** — `PageResponse<AuditLogResponse>`:

```json
{
  "content": [
    {
      "id": "00000000-0000-0000-0000-000000001201",
      "traceId": "a1b2c3d4-...",
      "actorId": "00000000-0000-0000-0000-000000000005",
      "actorUsername": "member3",
      "action": "task:update",
      "entityType": "TASK",
      "entityId": "00000000-0000-0000-0000-000000000403",
      "beforeData": { "status": "IN_PROGRESS" },
      "afterData": { "status": "BLOCKED", "blocked": true },
      "createdAt": "2026-07-29T08:00:00Z"
    }
  ],
  "page": 0, "size": 20, "totalElements": 120, "totalPages": 6, "hasNext": true, "hasPrevious": false
}
```

- **Ghi chú**: `beforeData`/`afterData` là JSONB đã **che field nhạy cảm** (không chứa password/token — docs/design/04 mục 8).

## 4. Traceability

| Endpoint | UC | AC |
|---|---|---|
| GET /reports/* | UC-012 | AC-012-* |
| GET /reports/export | UC-012 | AC-012-* |
| /projects/{id}/report-share-links, /public/shared-reports/{token} | GĐ4 cải tiến | — |
| GET /audit-logs | UC-013 | AC-013-* |

## 5. Ghi chú thiết kế

- Dashboard (API 03) và Report (API 12) dùng chung nhóm query aggregate; Dashboard là "hôm nay", Report là phân tích theo khoảng thời gian — tách riêng để không lẫn phạm vi (FR-DASH-01 vs FR-REP-01..05).
- Audit logs là bảng ghi có hạn (không soft delete) — retention policy chờ xác nhận (docs/design/06).
