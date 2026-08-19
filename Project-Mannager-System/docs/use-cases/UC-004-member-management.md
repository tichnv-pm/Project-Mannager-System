# UC-004 — Quản lý thành viên dự án

> Dự án: PM Daily Work Management | Trạng thái: Draft
> FR liên quan: FR-PROJ-06, FR-PROJ-07 | BR liên quan: BR-PROJ-05, BR-PROJ-06, BR-PROJ-08, BR-PROJ-10

## 1. Mã Use Case
`UC-004`

## 2. Tên
Quản lý thành viên dự án (thêm / xóa / đổi vai trò / xem danh sách)

## 3. Mô tả
ADMIN hoặc PM của dự án thêm user vào dự án với vai trò cụ thể (`PROJECT_MANAGER, TECH_LEAD, BUSINESS_ANALYST, DEVELOPER, TESTER, DEVOPS, MEMBER`), đổi vai trò, xóa thành viên khỏi dự án. Không được thêm trùng thành viên; không được xóa PM cuối cùng của dự án khi chưa có PM thay thế.

## 4. Actor
- ADMIN, PROJECT_MANAGER (PM của dự án).

## 5. Trigger
- Có thành viên mới tham gia / rời dự án.
- Vai trò thành viên thay đổi.

## 6. Tiền điều kiện
1. User thao tác có quyền `project-member:manage` (ADMIN hoặc PM của dự án).
2. Dự án tồn tại, chưa xóa mềm.
3. User được thêm phải tồn tại và trạng thái ACTIVE.

## 7. Hậu điều kiện
1. Thành viên được thêm/xóa/đổi vai trò trong `project_members`.
2. Audit log ghi nhận thay đổi.
3. Thành viên mới thấy dự án trong danh sách của họ.

## 8. Luồng chính (thêm thành viên)

| Bước | Tác nhân | Hành động | Kết quả |
|---|---|---|---|
| 1 | PM/ADMIN | Mở tab Thành viên, tìm user cần thêm, chọn vai trò | Gửi `POST /api/v1/projects/{projectId}/members` kèm `{userId, role}` |
| 2 | Hệ thống | Validate: dự án tồn tại, user tồn tại, chưa là thành viên, dự án chưa xóa | Hợp lệ |
| 3 | Hệ thống | Thêm bản ghi `project_members` (trong transaction) | Thành viên mới |
| 4 | Hệ thống | Ghi audit | Bản ghi audit |
| 5 | Hệ thống | Trả `201` | — |

## 9. Luồng thay thế

**9.1 Đổi vai trò:** chọn thành viên → chọn vai trò mới → `PUT /api/v1/projects/{projectId}/members/{userId}/role` kèm `{role}` → validate (không đổi PM cuối cùng nếu không có PM thay thế) → lưu → `200` + audit.

**9.2 Xóa thành viên:** chọn thành viên → confirm → `DELETE /api/v1/projects/{projectId}/members/{userId}` → xóa bản ghi mapping → `204` + audit. Nếu là PM duy nhất còn lại → chặn (BR-PROJ-08).

**9.3 Xem danh sách:** `GET /api/v1/projects/{projectId}/members` → trả page/sort (tên, vai trò) cho ADMIN và thành viên dự án.

## 10. Luồng ngoại lệ

| # | Tình huống | Kết quả |
|---|---|---|
| 1 | Thêm user đã là thành viên | `409` DUPLICATE "Thành viên đã tồn tại trong dự án" |
| 2 | User không tồn tại / INACTIVE | `404` / `400` |
| 3 | Dự án không tồn tại / đã xóa mềm | `404` |
| 4 | User thao tác không phải PM dự án, không phải ADMIN | `403` |
| 5 | Xóa PM duy nhất còn lại | `400` "Cần có ít nhất một PM cho dự án" |
| 6 | `role` không thuộc enum | `400` fieldErrors[role] |

## 11. Validation từng trường

| Trường | Quy tắc | Message lỗi |
|---|---|---|
| `userId` | Bắt buộc, UUID tồn tại | "Người dùng không tồn tại" |
| `role` | Bắt buộc, thuộc ProjectMemberRole | "Vai trò không hợp lệ" |
| `projectId` | UUID hợp lệ | "Dự án không hợp lệ" |

## 12. Business rule liên quan
BR-PROJ-05 (không thêm trùng), BR-PROJ-06 (PM là thành viên), BR-PROJ-08 (chặn xóa PM cuối cùng), BR-PROJ-10 (enum vai trò).

## 13. Phân quyền
- Thêm/xóa/đổi vai trò: `project-member:manage` (ADMIN, PM của dự án).
- Xem danh sách: ADMIN, thành viên dự án (`project:view`).

## 14. Audit log cần ghi
Thêm thành viên, xóa thành viên, đổi vai trò — kèm `actorId, projectId, userId, role trước/sau`.

## 15. API dự kiến

| Method | Endpoint | Mục đích |
|---|---|---|
| POST | `/api/v1/projects/{projectId}/members` | Thêm thành viên |
| PUT | `/api/v1/projects/{projectId}/members/{userId}/role` | Đổi vai trò |
| DELETE | `/api/v1/projects/{projectId}/members/{userId}` | Xóa thành viên |
| GET | `/api/v1/projects/{projectId}/members` | Danh sách thành viên |

## 16. Acceptance criteria (Given/When/Then)

| # | Given | When | Then |
|---|---|---|---|
| AC-004-01 | PM dự án, user chưa là thành viên | Thêm thành viên | `201`; user thấy dự án trong danh sách của mình |
| AC-004-02 | User đã là thành viên | Thêm lại | `409` DUPLICATE |
| AC-004-03 | PM dự án | Đổi vai trò thành viên | `200`; vai trò mới phản ánh trong danh sách |
| AC-004-04 | Dự án có 1 PM duy nhất | Xóa PM đó | `400`, không xóa được |
| AC-004-05 | MEMBER không có quyền | Thêm thành viên | `403` |
| AC-004-06 | Dự án đã xóa mềm | Thêm thành viên | `404` |
| AC-004-07 | `role` không hợp lệ | Thêm thành viên | `400` fieldErrors[role] |
| AC-004-08 | Dự án có 3 thành viên | Xem danh sách | `200` đủ 3 thành viên + vai trò |
