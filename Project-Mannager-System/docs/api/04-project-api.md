# API 04 — Dự án & thành viên (Project)

> Dự án: PM Daily Work Management | Trạng thái: Draft
> Nguồn: Prompt 06, `docs/02-functional-requirements.md` (FR-PROJ-01..07), `docs/04-business-rules.md` (BR-PROJ), `docs/05-user-roles-permissions.md`
> **Quy ước chung**: prefix `/api/v1`, JSON ISO-8601 UTC, pagination `page/size/sort`, error response theo `docs/design/05-error-handling-design.md`, phân quyền theo `docs/05-user-roles-permissions.md`.

## 1. Mô tả tổng quan

Quản lý dự án (CRUD, xóa mềm) và thành viên dự án. Phạm vi dữ liệu: ADMIN/PM toàn bộ; MEMBER/VIEWER chỉ dự án tham gia. Mọi thao tác đều có kiểm tra membership kép (docs/05 quy tắc 1).

## 2. Danh sách endpoint

| Method | Endpoint | Phân quyền | Mục đích | FR |
|---|---|---|---|---|
| GET | `/api/v1/projects` | `project:view` | Danh sách, search, filter | FR-PROJ-04 |
| POST | `/api/v1/projects` | `project:create` | Tạo dự án | FR-PROJ-01 |
| GET | `/api/v1/projects/{id}` | `project:view` | Chi tiết dự án | FR-PROJ-03 |
| PUT | `/api/v1/projects/{id}` | `project:update` (PM dự án/ADMIN) | Cập nhật | FR-PROJ-02 |
| DELETE | `/api/v1/projects/{id}` | `project:delete` (PM dự án/ADMIN) | Xóa mềm | FR-PROJ-05 |
| GET | `/api/v1/projects/{id}/members` | `project:view` | Danh sách thành viên | FR-PROJ-07 |
| POST | `/api/v1/projects/{id}/members` | `project-member:manage` | Thêm thành viên | FR-PROJ-06 |
| PUT | `/api/v1/projects/{id}/members/{userId}` | `project-member:manage` | Đổi vai trò | FR-PROJ-06 |
| DELETE | `/api/v1/projects/{id}/members/{userId}` | `project-member:manage` | Xóa thành viên | FR-PROJ-06 |

## 3. Chi tiết endpoint

### 3.1 GET `/api/v1/projects`

- **Query params**:

| Param | Type | Bắt buộc | Ghi chú |
|---|---|---|---|
| `page`, `size` | int | — | page ≥ 0, size 1–100 |
| `sort` | string | — | Whitelist: `code, name, status, startDate, endDate, createdAt, progress` |
| `keyword` | string | — | LIKE trên code/name/customerName |
| `status` | string | — | `PLANNING, ACTIVE, ON_HOLD, COMPLETED, CANCELLED` |
| `myOnly` | boolean | — | `true` = chỉ dự án mình là thành viên (mặc định theo quyền) |
| `projectManagerId` | uuid | — | Lọc theo PM |

- **Response `200`** — `PageResponse<ProjectResponse>` (xem 3.2).
- **Lỗi**: `400 VALIDATION_ERROR` (sort ngoài whitelist, size vượt giới hạn).

### 3.2 POST `/api/v1/projects`

- **Phân quyền**: `project:create` (ADMIN, PROJECT_MANAGER). Audit: có (tạo dự án).
- **Request body** — `ProjectCreateRequest`:

| Field | Type | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `code` | string | ✔ | 3–20 ký tự, unique (BR-PROJ-01) |
| `name` | string | ✔ | ≤ 100 ký tự |
| `description` | string | — | — |
| `startDate` / `endDate` | date | — | endDate ≥ startDate (BR-PROJ-02) |
| `customerName` | string | — | ≤ 100 ký tự |
| `projectManagerId` | uuid | — | Phải tồn tại; PM trở thành thành viên (BR-PROJ-06) |
| `note` | string | — | — |

- **Response `201`** — `ProjectResponse`:

```json
{
  "id": "00000000-0000-0000-0000-000000000301",
  "code": "PRJ001",
  "name": "App Mobile Banking",
  "description": "Ứng dụng ngân hàng di động",
  "status": "ACTIVE",
  "startDate": "2026-05-01",
  "endDate": "2026-11-30",
  "projectManagerId": "00000000-0000-0000-0000-000000000002",
  "customerName": "VietBank",
  "progress": 0,
  "memberCount": 1,
  "createdAt": "2026-07-01T02:00:00Z",
  "version": 0
}
```

- **Lỗi**: `400 VALIDATION_ERROR`; `400 INVALID_DATE_RANGE`; `409 DUPLICATE` (mã trùng).

### 3.3 GET `/api/v1/projects/{id}`

- **Phân quyền**: `project:view` + thành viên dự án (hoặc ADMIN). Không audit.
- **Response `200`** — `ProjectResponse` (kèm `memberCount`). **Lỗi**: `403 ACCESS_DENIED` (ngoài phạm vi), `404 NOT_FOUND` (không tồn tại hoặc đã xóa mềm — BR-PROJ-07).

### 3.4 PUT `/api/v1/projects/{id}`

- **Phân quyền**: `project:update` + PM dự án hoặc ADMIN. Audit: có.
- **Request body** — `ProjectUpdateRequest`: như Create + `version` (bắt buộc).
- **Response `200`** — `ProjectResponse`. **Lỗi**: `404`; `409 CONFLICT` (version cũ — BR-GEN-08); `403`; `400 INVALID_DATE_RANGE`.
- **Ghi chú**: đổi `projectManagerId` → cập nhật membership (thêm PM mới, giữ PM cũ nếu vẫn là thành viên).

### 3.5 DELETE `/api/v1/projects/{id}`

- **Phân quyền**: `project:delete` + PM dự án hoặc ADMIN. Audit: có (xóa mềm).
- **Query param**: `confirm=true` (bắt buộc khi dự án ACTIVE có task chưa đóng — BR-PROJ-09).
- **Response `204`**. **Lỗi**: `400 BAD_REQUEST` (dự án ACTIVE có task mở mà thiếu confirm — message kèm số task); `404`; `403`.
- **Hậu điều kiện**: `deleted_at` được ghi; dữ liệu con (task, meeting...) giữ nguyên; không xuất hiện trong danh sách mặc định.

### 3.6 GET `/api/v1/projects/{id}/members`

- **Phân quyền**: `project:view` + thành viên dự án/ADMIN.
- **Response `200`** — `List<ProjectMemberResponse>`:

```json
[
  {
    "userId": "00000000-0000-0000-0000-000000000002",
    "username": "pm.minh",
    "fullName": "Nguyễn Văn Minh",
    "email": "minh@pmdaily.local",
    "role": "PROJECT_MANAGER",
    "joinedAt": "2026-07-01T02:00:00Z"
  }
]
```

### 3.7 POST `/api/v1/projects/{id}/members`

- **Phân quyền**: `project-member:manage` + PM dự án/ADMIN. Audit: có (thêm thành viên).
- **Request body** — `ProjectMemberRequest`: `userId` (uuid, bắt buộc), `role` (enum BR-PROJ-10).
- **Response `201`** — `ProjectMemberResponse`. **Lỗi**: `409 DUPLICATE` (thêm trùng — BR-PROJ-05); `404 NOT_FOUND` (user/project); `400 VALIDATION_ERROR`.

### 3.8 PUT `/api/v1/projects/{id}/members/{userId}`

- **Phân quyền**: như 3.7. Audit: có (đổi vai trò).
- **Request body**: `{ "role": "TECH_LEAD" }`.
- **Response `200`** — `ProjectMemberResponse`. **Lỗi**: `404` (thành viên không tồn tại trong dự án).

### 3.9 DELETE `/api/v1/projects/{id}/members/{userId}`

- **Phân quyền**: như 3.7. Audit: có (xóa thành viên).
- **Response `204`**.
- **Lỗi**: `400 PROJECT_MANAGER_REQUIRED` khi xóa PM cuối cùng của dự án mà chưa gán PM mới (BR-PROJ-08); `404`.

## 4. Traceability

| Endpoint | UC | AC |
|---|---|---|
| GET /projects, GET /projects/{id} | UC-003 | AC-003-* |
| POST/PUT/DELETE /projects | UC-003 | AC-003-* |
| */members | UC-004 | AC-004-* |

## 5. Điểm cần xác nhận

1. Kiểm tra mã `code` có phân biệt hoa thường không (mặc định đề xuất: phân biệt, không cấm uppercase).
2. FR-PROJ-05: PM dự án có được xóa dự án không (ma trận docs/05: có, giới hạn PM dự án).
