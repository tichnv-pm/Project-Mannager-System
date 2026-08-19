# API 02 — Quản trị người dùng, vai trò & quyền (User Admin)

> Dự án: PM Daily Work Management | Trạng thái: Draft
> Nguồn: Prompt 06, `docs/02-functional-requirements.md` (FR-USER-01, FR-USER-02), `docs/05-user-roles-permissions.md`
> **Quy ước chung**: prefix `/api/v1`, JSON ISO-8601 UTC, pagination `page/size/sort`, error response theo `docs/design/05-error-handling-design.md`, phân quyền theo `docs/05-user-roles-permissions.md`.

## 1. Mô tả tổng quan

ADMIN quản lý tài khoản (tạo/sửa/vô hiệu hóa, gán vai trò hệ thống) và phân quyền cho vai trò (role × permission). Không có đăng ký công khai (BR-AUTH-07). Mọi endpoint yêu cầu quyền `user:view`, `user:manage` hoặc `role:manage`.

## 2. Danh sách endpoint

| Method | Endpoint | Phân quyền | Mục đích | FR |
|---|---|---|---|---|
| GET | `/api/v1/users` | `user:view` | Danh sách user (search/filter/paging) | FR-USER-01 |
| GET | `/api/v1/users/{id}` | `user:view` | Chi tiết user + vai trò | FR-USER-01 |
| POST | `/api/v1/users` | `user:manage` | Tạo tài khoản | FR-USER-01 |
| PUT | `/api/v1/users/{id}` | `user:manage` | Sửa thông tin & vai trò | FR-USER-01 |
| PATCH | `/api/v1/users/{id}/status` | `user:manage` | Kích hoạt / vô hiệu hóa | FR-USER-01 |
| DELETE | `/api/v1/users/{id}` | `user:manage` | Xóa mềm tài khoản | FR-USER-01 |
| GET | `/api/v1/roles` | `role:manage` | Danh sách vai trò + quyền | FR-USER-02 |
| POST | `/api/v1/roles` | `role:manage` | Tạo vai trò tùy chỉnh | FR-USER-02 |
| PUT | `/api/v1/roles/{roleId}` | `role:manage` | Sửa tên/mô tả & gán quyền | FR-USER-02 |
| DELETE | `/api/v1/roles/{roleId}` | `role:manage` | Xóa vai trò tùy chỉnh | FR-USER-02 |

## 3. Chi tiết endpoint

### 3.1 GET `/api/v1/users`

- **Phân quyền**: `user:view`. Audit: không.
- **Query params**:

| Param | Type | Bắt buộc | Ghi chú |
|---|---|---|---|
| `page` | int | — | ≥ 0, mặc định 0 |
| `size` | int | — | 1–100, mặc định 20 |
| `sort` | string | — | Whitelist: `username, fullName, email, status, createdAt` (VD `fullName,asc`) |
| `keyword` | string | — | LIKE trên username/fullName/email |
| `status` | string | — | `ACTIVE`, `INACTIVE` |
| `roleCode` | string | — | Lọc theo vai trò hệ thống |

- **Response `200`** — `PageResponse<UserResponse>`:

```json
{
  "content": [
    {
      "id": "00000000-0000-0000-0000-000000000001",
      "username": "admin",
      "fullName": "Quản trị viên",
      "email": "admin@pmdaily.local",
      "status": "ACTIVE",
      "roles": ["ADMIN"],
      "version": 5,
      "createdAt": "2026-07-01T02:00:00Z"
    }
  ],
  "page": 0, "size": 20, "totalElements": 5, "totalPages": 1, "hasNext": false, "hasPrevious": false
}
```

### 3.2 GET `/api/v1/users/{id}`

- **Response `200`** — `UserResponse` (kèm `permissions[]` tổng hợp từ vai trò). **Lỗi**: `404 NOT_FOUND`.

### 3.3 POST `/api/v1/users`

- **Phân quyền**: `user:manage`. Audit: có (tạo tài khoản).
- **Request body** — `UserCreateRequest`:

| Field | Type | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `username` | string | ✔ | 3–50 ký tự, unique (BR-AUTH-01) |
| `email` | string | ✔ | Định dạng email, unique |
| `fullName` | string | ✔ | Không để trống |
| `password` | string | ✔ | BR-AUTH-02 (mật khẩu tạm) |
| `status` | string | — | `ACTIVE` / `INACTIVE`, mặc định `ACTIVE` |
| `roleIds` | array<uuid> | — | Vai trò hệ thống; mặc định `PROJECT_MEMBER` |

- **Response `201`** — `UserResponse`. **Lỗi**: `400 VALIDATION_ERROR`; `409 DUPLICATE` (username/email trùng).

### 3.4 PUT `/api/v1/users/{id}`

- **Phân quyền**: `user:manage`. Audit: có.
- **Request body** — `UserUpdateRequest`:

| Field | Type | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `fullName` | string | ✔ | Không để trống |
| `email` | string | ✔ | Định dạng, unique |
| `roleIds` | array<uuid> | — | Thay thế toàn bộ vai trò hiện tại |
| `version` | long | ✔ | Optimistic locking |

- **Response `200`** — `UserResponse`. **Lỗi**: `404`; `409 CONFLICT` (version cũ); `409 DUPLICATE` (email trùng).

### 3.5 PATCH `/api/v1/users/{id}/status`

- **Phân quyền**: `user:manage`. Audit: có.
- **Request body** — `UserStatusRequest`: `status` (ACTIVE/INACTIVE) + `version`.
- **Response `200`** — `UserResponse`.
- **Lỗi**: `400 BAD_REQUEST` khi ADMIN cố vô hiệu hóa chính mình (quy tắc 5, docs/05); `404`; `409 CONFLICT`.
- **Hậu điều kiện khi INACTIVE**: revoke toàn bộ refresh token của user; user không đăng nhập được (BR-AUTH-06).

### 3.6 DELETE `/api/v1/users/{id}`

- **Phân quyền**: `user:manage`. Audit: có (USER_DELETED).
- **Response `200`** — không có body.
- **Hành vi** (Prompt 23, soft delete): đặt `deleted_at` + `deleted_by`, đổi username/email thành `*_deleted_<epoch>` để tránh đụng unique constraint, revoke toàn bộ refresh token, user không đăng nhập được và không xuất hiện trong danh sách/chi tiết.
- **Lỗi**: `404 NOT_FOUND`; `400 BAD_REQUEST` khi tự xóa chính mình hoặc xóa `admin` hệ thống.

### 3.7 GET `/api/v1/roles`

- **Phân quyền**: `role:manage`.
- **Response `200`** — `List<RoleResponse>`:

```json
[
  {
    "id": "00000000-0000-0000-0000-000000000101",
    "code": "ADMIN",
    "name": "Quản trị hệ thống",
    "description": "Quản lý tài khoản, vai trò, quyền, audit",
    "isSystem": true,
    "permissions": ["user:view", "user:manage", "role:manage", "..."]
  }
]
```

### 3.8 POST `/api/v1/roles`

- **Phân quyền**: `role:manage`. Audit: có (ROLE_CREATED).
- **Request body** — `RoleCreateRequest`:

| Field | Type | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `code` | string | ✔ | 2–50 ký tự, unique |
| `name` | string | ✔ | 2–100 ký tự |
| `description` | string | — | ≤ 255 ký tự |
| `permissionCodes` | array<string> | — | Gán quyền ban đầu |

- **Response `201`** — `RoleResponse` với `isSystem=false`. **Lỗi**: `400 VALIDATION_ERROR`; `409 DUPLICATE` (code trùng).

### 3.9 PUT `/api/v1/roles/{roleId}`

- **Phân quyền**: `role:manage`. Audit: có (ROLE_UPDATED).
- **Request body** — `RoleUpdateRequest`: `name` (bắt buộc, 2–100 ký tự), `description` (≤ 255), `permissionCodes[]` (thay thế toàn bộ quyền).
- **Response `200`** — `RoleResponse`.
- **Lỗi**: `404 NOT_FOUND`; `400 BAD_REQUEST` khi gỡ quyền `role:manage` của vai trò hệ thống ADMIN (FR-USER-02); `400 VALIDATION_ERROR` khi `name` trống.

### 3.10 DELETE `/api/v1/roles/{roleId}`

- **Phân quyền**: `role:manage`. Audit: có (ROLE_DELETED).
- **Response `200`** — không có body.
- **Lỗi**: `404 NOT_FOUND`; `400 BAD_REQUEST` khi xóa vai trò hệ thống (`is_system=true` — safety lock, Prompt 23).

## 4. Traceability

| Endpoint | UC | AC |
|---|---|---|
| GET/POST/PUT/DELETE users, PATCH status | UC-002 (quản lý tài khoản) | AC-002-* |
| GET/POST/PUT/DELETE roles | UC-002 | AC-002-* |

> Ghi chú: AC chi tiết nằm trong `docs/06-acceptance-criteria.md` (nhóm AC-002).

## 5. Điểm cần xác nhận

1. Mật khẩu tạm khi tạo tài khoản: gửi qua email hay hiển thị 1 lần cho ADMIN (chưa có module email ở v1 → hiển thị 1 lần, đề xuất)?
2. Phân trang danh sách vai trò: chỉ có 4 vai trò cố định ở v1 → trả List, không phân trang.
