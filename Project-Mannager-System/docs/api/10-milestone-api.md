# API 10 — Milestone

> Dự án: PM Daily Work Management | Trạng thái: Draft
> Nguồn: Prompt 06, `docs/02-functional-requirements.md` (FR-MIL-01..04)
> **Quy ước chung**: prefix `/api/v1`, JSON ISO-8601 UTC, pagination `page/size/sort`, error response theo `docs/design/05-error-handling-design.md`.

## 1. Mô tả tổng quan

Quản lý mốc quan trọng của dự án. `COMPLETED` bắt buộc `progress = 100`; `actualDate` tự ghi khi hoàn thành (mặc định = hôm nay nếu chưa có — chờ xác nhận FR-MIL-02).

## 2. Danh sách endpoint

| Method | Endpoint | Phân quyền | Mục đích | FR |
|---|---|---|---|---|
| GET | `/api/v1/milestones` | `milestone:view` | Danh sách, lọc | FR-MIL-03 |
| POST | `/api/v1/milestones` | `milestone:manage` | Tạo | FR-MIL-01 |
| GET | `/api/v1/milestones/{id}` | `milestone:view` | Chi tiết | FR-MIL-03 |
| PUT | `/api/v1/milestones/{id}` | `milestone:manage` | Cập nhật | FR-MIL-02 |
| DELETE | `/api/v1/milestones/{id}` | `milestone:manage` | Xóa mềm | FR-MIL-04 |

## 3. Chi tiết endpoint

### 3.1 GET `/api/v1/milestones`

- **Query params**: `page, size`, `projectId`, `status`, `sort` (whitelist: `name, plannedDate, status, progress, createdAt`; mặc định `plannedDate,asc`).
- **Response `200`** — `PageResponse<MilestoneResponse>` (xem 3.3).

### 3.2 POST `/api/v1/milestones`

- **Phân quyền**: `milestone:manage` (ADMIN, PM dự án). Audit: có.
- **Request body** — `MilestoneCreateRequest`:

| Field | Type | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `projectId` | uuid | ✔ | — |
| `name` | string | ✔ | ≤ 150 ký tự |
| `description` | string | — | — |
| `plannedDate` | date | ✔ | — |
| `note` | string | — | — |

- **Response `201`** — `MilestoneResponse`:

```json
{
  "id": "00000000-0000-0000-0000-000000001001",
  "projectId": "00000000-0000-0000-0000-000000000301",
  "projectCode": "PRJ001",
  "projectName": "App Mobile Banking",
  "name": "Release 1.0",
  "description": "Phát hành bản chính thức 1.0",
  "plannedDate": "2026-09-30",
  "actualDate": null,
  "status": "IN_PROGRESS",
  "progress": 40,
  "note": null,
  "createdAt": "2026-08-01T03:00:00Z",
  "version": 0
}
```

- **Lỗi**: `400 VALIDATION_ERROR`; `403`, `404`.

### 3.3 GET `/api/v1/milestones/{id}`

- **Response `200`**. **Lỗi**: `403`, `404`.

### 3.4 PUT `/api/v1/milestones/{id}`

- **Phân quyền**: `milestone:manage`. Audit: có.
- **Request body** — `MilestoneUpdateRequest`: `name, description, plannedDate, status, progress, actualDate, note` + `version`.
- **Response `200`**. Hậu điều kiện:
  - `status = COMPLETED` → bắt buộc `progress = 100`; `actualDate` tự ghi = hôm nay nếu chưa có.
  - `progress = 100` không tự động chuyển COMPLETED (chỉ gợi ý).
- **Lỗi**: `400 PROGRESS_REQUIRED_FOR_DONE` (COMPLETED mà progress < 100); `404`, `403`, `409 CONFLICT`.

### 3.5 DELETE `/api/v1/milestones/{id}`

- **Phân quyền**: `milestone:manage`. **Response `204`** (xóa mềm). **Lỗi**: `403`, `404`. Audit: có.

## 4. Traceability

| Endpoint | UC | AC |
|---|---|---|
| GET /milestones, /{id} | UC-010 | AC-010-* |
| POST/PUT/DELETE | UC-010 | AC-010-* |

## 5. Điểm cần xác nhận

1. FR-MIL-02 — `actualDate` tự ghi khi COMPLETED hay bắt buộc user nhập (mặc định: tự ghi nếu trống).
