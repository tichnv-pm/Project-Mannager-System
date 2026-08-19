# API 09 — Issue

> Dự án: PM Daily Work Management | Trạng thái: Draft
> Nguồn: Prompt 06, `docs/02-functional-requirements.md` (FR-ISS-01..04)
> **Quy ước chung**: prefix `/api/v1`, JSON ISO-8601 UTC, pagination `page/size/sort`, error response theo `docs/design/05-error-handling-design.md`.

## 1. Mô tả tổng quan

Theo dõi vấn đề (issue) trong dự án; mã tự sinh `ISS000001` (global sequence). Khi chuyển `RESOLVED`, hệ thống tự ghi `resolvedAt`.

## 2. Danh sách endpoint

| Method | Endpoint | Phân quyền | Mục đích | FR |
|---|---|---|---|---|
| GET | `/api/v1/issues` | `issue:view` | Danh sách, lọc | FR-ISS-03 |
| POST | `/api/v1/issues` | `issue:manage` | Tạo | FR-ISS-01 |
| GET | `/api/v1/issues/{id}` | `issue:view` | Chi tiết | FR-ISS-03 |
| PUT | `/api/v1/issues/{id}` | `issue:manage` / owner | Cập nhật | FR-ISS-02 |
| DELETE | `/api/v1/issues/{id}` | `issue:manage` | Xóa mềm | FR-ISS-04 |

## 3. Chi tiết endpoint

### 3.1 GET `/api/v1/issues`

- **Query params**: `page, size, sort` (whitelist: `code, title, severity, status, dueDate, createdAt`), `projectId`, `status`, `severity`, `ownerId`.
- **Response `200`** — `PageResponse<IssueResponse>` (xem 3.3).

### 3.2 POST `/api/v1/issues`

- **Phân quyền**: `issue:manage` (ADMIN, PM dự án). Audit: có.
- **Request body** — `IssueCreateRequest`:

| Field | Type | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `projectId` | uuid | ✔ | — |
| `title` | string | ✔ | ≤ 200 ký tự |
| `description` | string | — | — |
| `severity` | string | ✔ | `LOW, MEDIUM, HIGH, CRITICAL` |
| `ownerId` | uuid | ✔ | Thuộc project |
| `rootCause` | string | — | — |
| `solution` | string | — | — |
| `status` | string | — | Mặc định `OPEN` |
| `dueDate` | date | — | — |

- **Response `201`** — `IssueResponse`:

```json
{
  "id": "00000000-0000-0000-0000-000000000901",
  "code": "ISS000001",
  "projectId": "00000000-0000-0000-0000-000000000301",
  "title": "Lỗi mất phiên đăng nhập trên iOS",
  "severity": "CRITICAL",
  "owner": { "id": "00000000-0000-0000-0000-000000000005", "fullName": "Phạm Thu Thảo" },
  "status": "IN_PROGRESS",
  "dueDate": "2026-08-05",
  "rootCause": null,
  "solution": null,
  "resolvedAt": null,
  "createdAt": "2026-07-28T02:00:00Z",
  "version": 0
}
```

- **Lỗi**: `400 VALIDATION_ERROR`; `400 NOT_PROJECT_MEMBER`; `409` (mã trùng); `403`, `404`.

### 3.3 GET `/api/v1/issues/{id}`

- **Response `200`**. **Lỗi**: `403`, `404`.

### 3.4 PUT `/api/v1/issues/{id}`

- **Phân quyền**: `issue:manage` (ADMIN, PM dự án) — mọi trường; owner — chỉ `status` (chờ xác nhận FR-ISS-02). Audit: có.
- **Request body** — `IssueUpdateRequest`: `title, description, severity, ownerId, rootCause, solution, status, dueDate` + `version`.
- **Response `200`**. Hậu điều kiện: khi `status = RESOLVED` → `resolvedAt = now()` (nếu chưa có); `solution` không bắt buộc ở v1 (chờ xác nhận FR-ISS-02).
- **Lỗi**: `404`, `403`, `409 CONFLICT`.

### 3.5 DELETE `/api/v1/issues/{id}`

- **Phân quyền**: `issue:manage`. **Response `204`** (xóa mềm). **Lỗi**: `403`, `404`. Audit: có.

## 4. Traceability

| Endpoint | UC | AC |
|---|---|---|
| GET /issues, /{id} | UC-009 | AC-009-* |
| POST/PUT/DELETE | UC-009 | AC-009-* |

## 5. Điểm cần xác nhận

1. FR-ISS-02 — khi RESOLVED có bắt buộc `solution` không (mặc định v1: không bắt buộc).
2. Owner issue có được cập nhật trạng thái không (mặc định: có, chỉ status).
