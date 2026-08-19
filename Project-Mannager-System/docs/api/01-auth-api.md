# API 01 — Xác thực & tài khoản (Auth)

> Dự án: PM Daily Work Management | Trạng thái: Draft
> Nguồn: Prompt 06, `docs/02-functional-requirements.md` (FR-AUTH-01..06), `docs/design/04-security-design.md`, `docs/use-cases/UC-001-login.md`
> **Quy ước chung**: prefix `/api/v1`, JSON ISO-8601 UTC, pagination `page/size/sort`, error response theo `docs/design/05-error-handling-design.md` (BR-GEN-05), phân quyền theo `docs/05-user-roles-permissions.md`.

## 1. Mô tả tổng quan

Quản lý phiên đăng nhập: cấp cặp token (access JWT 15 phút + refresh token 7 ngày lưu DB), refresh có rotation, logout idempotent, đổi mật khẩu, reset mật khẩu bởi ADMIN. Login/refresh **công khai**; các endpoint còn lại yêu cầu `Authorization: Bearer <accessToken>`.

## 2. Danh sách endpoint

| Method | Endpoint | Phân quyền | Mục đích | FR |
|---|---|---|---|---|
| POST | `/api/v1/auth/login` | Công khai | Đăng nhập, cấp cặp token | FR-AUTH-01 |
| POST | `/api/v1/auth/refresh` | Công khai | Cấp token mới (rotation) | FR-AUTH-02 |
| POST | `/api/v1/auth/logout` | Đã xác thực | Revoke refresh token | FR-AUTH-03 |
| GET | `/api/v1/auth/me` | Đã xác thực | Thông tin user + roles + permissions | FR-AUTH-04 |
| PUT | `/api/v1/auth/change-password` | Chính chủ | Đổi mật khẩu | FR-AUTH-05 |
| POST | `/api/v1/auth/{userId}/reset-password` | `user:manage` | ADMIN reset mật khẩu | FR-AUTH-06 |

## 3. Chi tiết endpoint

### 3.1 POST `/api/v1/auth/login`

- **Phân quyền**: công khai (không cần token). Audit: có (thành công/thất bại, IP).
- **Request body** — `LoginRequest`:

| Field | Type | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `username` | string | ✔ | 3–50 ký tự |
| `password` | string | ✔ | 8–72 ký tự |

- **Response `200`** — `TokenResponse`:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "c8e7b1a2-...",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": "00000000-0000-0000-0000-000000000002",
    "username": "pm.minh",
    "fullName": "Nguyễn Văn Minh",
    "email": "minh@pmdaily.local",
    "roles": ["PROJECT_MANAGER"],
    "permissions": ["project:create", "task:create", "..."]
  }
}
```

- **Lỗi**:
  - `400 VALIDATION_ERROR` — thiếu/vi phạm ràng buộc field (fieldErrors).
  - `401 INVALID_LOGIN` — sai username/mật khẩu hoặc tài khoản INACTIVE (message chung, không tiết lộ tài khoản tồn tại — BR-AUTH-05/06).
  - `423` / `401` (theo cấu hình BR-AUTH-08) — khóa tạm thời sau 5 lần sai liên tiếp (chờ xác nhận).
- **Ghi chú**: trả về `user` không bao giờ chứa `passwordHash`; reset `failedLoginAttempts` khi thành công.

### 3.2 POST `/api/v1/auth/refresh`

- **Phân quyền**: công khai. Audit: có.
- **Request body** — `RefreshRequest`:

| Field | Type | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `refreshToken` | string | ✔ | Không rỗng |

- **Response `200`** — `TokenResponse` (cặp token MỚI; token cũ bị revoke, ghi `replaced_by` — rotation).
- **Lỗi**:
  - `400 VALIDATION_ERROR` — refreshToken rỗng.
  - `401 UNAUTHORIZED` — token không tồn tại / revoked / hết hạn → client logout cục bộ, về trang login.
  - (Tùy chọn BR-AUTH-09) phát hiện reuse token đã revoke → revoke toàn bộ token của user, trả 401.

### 3.3 POST `/api/v1/auth/logout`

- **Phân quyền**: access token hợp lệ. Audit: có.
- **Request body** — `LogoutRequest`:

| Field | Type | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `refreshToken` | string | ✔ | Không rỗng |

- **Response `204`** — không body. Idempotent: token không tồn tại vẫn trả 204 (FR-AUTH-03).

### 3.4 GET `/api/v1/auth/me`

- **Phân quyền**: đã xác thực. Audit: không.
- **Response `200`** — `UserResponse` (id, username, fullName, email, roles[], permissions[]) — không chứa password hash.
- **Lỗi**: `401 UNAUTHORIZED` — token hết hạn/sai/signature không hợp lệ.

### 3.5 PUT `/api/v1/auth/change-password`

- **Phân quyền**: chính chủ tài khoản. Audit: có (đổi mật khẩu).
- **Request body** — `ChangePasswordRequest`:

| Field | Type | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `currentPassword` | string | ✔ | Phải khớp mật khẩu hiện tại |
| `newPassword` | string | ✔ | BR-AUTH-02: ≥ 8 ký tự, chữ thường + hoa + số + ký tự đặc biệt; khác mật khẩu cũ; ≤ 72 ký tự |

- **Response `204`**. Hậu điều kiện: lưu hash BCrypt mới (cost ≥ 10), **revoke toàn bộ refresh token** của user → phiên cũ hết hiệu lực.
- **Lỗi**: `400 VALIDATION_ERROR` (policy/mật khẩu cũ sai); `401 UNAUTHORIZED`.

### 3.6 POST `/api/v1/auth/{userId}/reset-password`

- **Phân quyền**: `user:manage` (ADMIN). Audit: có (ai reset cho ai).
- **Path param**: `userId` (UUID).
- **Request body** — `ResetPasswordRequest`: `newPassword` (BR-AUTH-02).
- **Response `204`**. Hậu điều kiện: hash mới được lưu, revoke toàn bộ refresh token của user.
- **Lỗi**: `404 NOT_FOUND` (user không tồn tại); `400 VALIDATION_ERROR` (policy).

## 4. Traceability

| Endpoint | UC | AC |
|---|---|---|
| POST /auth/login | UC-001 | AC-001-01..04 |
| POST /auth/refresh | UC-001 | AC-001-05..07 |
| POST /auth/logout | UC-001 | AC-001-08..09 |
| PUT /auth/change-password | UC-001 | AC-001-10..12 |
| POST /auth/{userId}/reset-password | UC-001 | AC-001-13 |
| GET /auth/me | UC-001 | AC-001-14..15 |

## 5. Điểm cần xác nhận

1. BR-AUTH-08 (khóa tạm 5 lần sai): có áp dụng v1 không → quyết định trước Prompt 09.
2. BR-AUTH-09 (revoke chuỗi khi phát hiện reuse): v1 hoặc để sau?
