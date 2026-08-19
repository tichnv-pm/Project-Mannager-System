# UC-001 — Xác thực & phiên đăng nhập (Authentication)

> Dự án: PM Daily Work Management | Trạng thái: Draft
> FR liên quan: FR-AUTH-01..06, FR-USER-01 | BR liên quan: BR-AUTH-01..09

## 1. Mã Use Case
`UC-001`

## 2. Tên
Xác thực và quản lý phiên đăng nhập (login / refresh / logout / đổi mật khẩu)

## 3. Mô tả
Người dùng đăng nhập bằng username/password để nhận access token (ngắn hạn) và refresh token (dài hạn, lưu DB). Khi access token hết hạn, client dùng refresh token để cấp token mới. Người dùng có thể đăng xuất (revoke refresh token) và tự đổi mật khẩu. Admin có thể reset mật khẩu cho người dùng khác.

## 4. Actor
- Người dùng (ADMIN / PROJECT_MANAGER / PROJECT_MEMBER / VIEWER) — tài khoản ACTIVE.
- ADMIN — reset mật khẩu.

## 5. Trigger
- User mở ứng dụng chưa có phiên.
- Access token hết hạn.
- User muốn đổi mật khẩu / đăng xuất.

## 6. Tiền điều kiện
1. Tài khoản tồn tại, trạng thái ACTIVE.
2. Mật khẩu đã được hash BCrypt trong DB.

## 7. Hậu điều kiện
1. Đăng nhập thành công: client giữ access token (15 phút) + refresh token (7 ngày); refresh token được lưu ở bảng `refresh_tokens`.
2. Đăng xuất: refresh token bị revoke.
3. Đổi mật khẩu: hash mới được lưu, refresh token của user bị revoke.

## 8. Luồng chính (đăng nhập)

| Bước | Tác nhân | Hành động | Kết quả |
|---|---|---|---|
| 1 | User | Nhập username + password, bấm Đăng nhập | Gửi `POST /api/v1/auth/login` |
| 2 | Hệ thống | Tìm user theo username; kiểm tra trạng thái ACTIVE | Tìm thấy user |
| 3 | Hệ thống | So khớp mật khẩu bằng BCrypt | Khớp |
| 4 | Hệ thống | Tạo access token + refresh token; lưu refresh token (hash) vào DB | Cặp token hợp lệ |
| 5 | Hệ thống | Ghi audit login thành công | Bản ghi audit |
| 6 | Hệ thống | Trả `200` kèm `accessToken, refreshToken, expiresIn, user (id, username, fullName, email, roles, permissions)` | Client lưu token |

## 9. Luồng thay thế

**9.1 Refresh token (access token hết hạn):**
| Bước | Tác nhân | Hành động | Kết quả |
|---|---|---|---|
| 1 | Hệ thống | Client gọi `POST /api/v1/auth/refresh` kèm refresh token | — |
| 2 | Hệ thống | Kiểm tra token tồn tại, chưa revoke, chưa hết hạn | Hợp lệ |
| 3 | Hệ thống | Cấp access token mới (+ refresh token mới theo chính sách rotation) | `200` + token mới |

**9.2 Đổi mật khẩu (FR-AUTH-05):** user nhập mật khẩu cũ + mới → hệ thống xác thực mật khẩu cũ, kiểm tra policy → lưu hash mới → revoke refresh tokens → `204`.

**9.3 Reset mật khẩu (FR-AUTH-06):** ADMIN chọn user, nhập mật khẩu mới → lưu hash → revoke refresh tokens → `204`.

**9.4 Đăng xuất (FR-AUTH-03):** client gửi refresh token cần revoke → hệ thống revoke → `204` (idempotent: token không tồn tại vẫn trả `204`).

**9.5 Xem thông tin tài khoản:** `GET /api/v1/auth/me` trả user + roles + permissions (không kèm password hash).

## 10. Luồng ngoại lệ

| # | Tình huống | Kết quả |
|---|---|---|
| 1 | Username không tồn tại / mật khẩu sai | `401` message chung "Tên đăng nhập hoặc mật khẩu không đúng" — không tiết lộ username có tồn tại |
| 2 | Tài khoản INACTIVE | `401` message chung (giống #1) |
| 3 | Refresh token revoked / hết hạn / không tồn tại | `401` → client đăng xuất cục bộ, về trang login |
| 4 | Quá 5 lần sai mật khẩu liên tiếp (nếu áp dụng BR-AUTH-08) | Khóa tạm thời 5 phút |
| 5 | Mật khẩu cũ sai khi đổi mật khẩu | `400` VALIDATION_ERROR |

## 11. Validation từng trường

| Trường | Quy tắc | Message lỗi |
|---|---|---|
| `username` | Bắt buộc, 3–50 ký tự | "Tên đăng nhập không được để trống" / "Tên đăng nhập phải từ 3–50 ký tự" |
| `password` | Bắt buộc, 8–72 ký tự, ≥ 1 chữ thường + ≥ 1 chữ hoa + ≥ 1 số + ≥ 1 ký tự đặc biệt | "Mật khẩu phải từ 8 ký tự, gồm chữ thường, chữ hoa, số và ký tự đặc biệt" |
| `refreshToken` | Bắt buộc | "Refresh token không được để trống" |

## 12. Business rule liên quan
BR-AUTH-01 (unique username/email), BR-AUTH-02 (policy mật khẩu), BR-AUTH-03 (BCrypt), BR-AUTH-04 (thời hạn token, revoke khi đổi mật khẩu), BR-AUTH-05 (message chung), BR-AUTH-06 (INACTIVE), BR-AUTH-07 (không đăng ký công khai), BR-AUTH-08, BR-AUTH-09.

## 13. Phân quyền
- `POST /auth/login`, `POST /auth/refresh`: công khai (không cần token).
- `POST /auth/logout`, `GET /auth/me`, `PUT /auth/change-password`: yêu cầu access token hợp lệ, chính chủ tài khoản.
- `POST /auth/{userId}/reset-password`: yêu cầu `user:manage`.

## 14. Audit log cần ghi
- Login thành công / thất bại (username, thời gian, IP).
- Logout.
- Đổi mật khẩu / reset mật khẩu (ai reset cho ai).
- Refresh thành công / thất bại.
- Không log token/password.

## 15. API dự kiến

| Method | Endpoint | Mục đích |
|---|---|---|
| POST | `/api/v1/auth/login` | Đăng nhập |
| POST | `/api/v1/auth/refresh` | Cấp token mới |
| POST | `/api/v1/auth/logout` | Đăng xuất, revoke refresh token |
| GET | `/api/v1/auth/me` | Thông tin user hiện tại |
| PUT | `/api/v1/auth/change-password` | Đổi mật khẩu |
| POST | `/api/v1/auth/{userId}/reset-password` | Admin reset mật khẩu |

## 16. Acceptance criteria (Given/When/Then)

| # | Given | When | Then |
|---|---|---|---|
| AC-001-01 | Tài khoản ACTIVE, mật khẩu đúng | User đăng nhập | `200` kèm access + refresh token, không chứa password hash |
| AC-001-02 | Mật khẩu sai | User đăng nhập | `401` message chung, không phân biệt username tồn tại hay không |
| AC-001-03 | Tài khoản INACTIVE | User đăng nhập | `401` message chung |
| AC-001-04 | Username rỗng | User đăng nhập | `400` VALIDATION_ERROR fieldErrors[username] |
| AC-001-05 | Access token hết hạn, refresh token hợp lệ | Client gọi refresh | `200` + access token mới |
| AC-001-06 | Refresh token đã revoke | Client gọi refresh | `401` |
| AC-001-07 | Refresh token hết hạn | Client gọi refresh | `401` |
| AC-001-08 | Đang có phiên | User đăng xuất | `204`; refresh token không dùng lại được |
| AC-001-09 | Đã đăng xuất rồi | User đăng xuất lần nữa | `204` (idempotent) |
| AC-001-10 | Mật khẩu cũ đúng, mới hợp lệ | User đổi mật khẩu | `204`; đăng nhập được bằng mật khẩu mới; phiên cũ hết hiệu lực |
| AC-001-11 | Mật khẩu cũ sai | User đổi mật khẩu | `400` |
| AC-001-12 | Mật khẩu mới thiếu ký tự đặc biệt | User đổi mật khẩu | `400` kèm message policy |
| AC-001-13 | ADMIN | Reset mật khẩu user khác | `204`; user đăng nhập được bằng mật khẩu mới |
| AC-001-14 | Token hợp lệ | Gọi `GET /auth/me` | `200` user + roles + permissions, không có password hash |
| AC-001-15 | Token hết hạn | Gọi `GET /auth/me` | `401` |
