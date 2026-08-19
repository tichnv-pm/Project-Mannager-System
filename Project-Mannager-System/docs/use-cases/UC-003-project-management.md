# UC-003 — Quản lý dự án (Project Management)

> Dự án: PM Daily Work Management | Trạng thái: Draft
> FR liên quan: FR-PROJ-01..05 | BR liên quan: BR-PROJ-01..10

## 1. Mã Use Case
`UC-003`

## 2. Tên
Quản lý dự án (tạo / sửa / xem / danh sách / xóa mềm)

## 3. Mô tả
ADMIN hoặc PM tạo dự án mới (mã không trùng), cập nhật thông tin dự án, xem chi tiết, tìm kiếm/lọc/phân trang danh sách, và xóa mềm dự án. PM được chỉ định trở thành thành viên dự án. Dữ liệu dự án bị xóa mềm không xuất hiện trong danh sách mặc định.

## 4. Actor
- ADMIN, PROJECT_MANAGER (tạo/sửa/xóa).
- ADMIN, thành viên dự án (xem).

## 5. Trigger
- Có dự án mới cần quản lý.
- Thông tin dự án thay đổi.
- User tìm dự án trong danh sách.

## 6. Tiền điều kiện
1. User có quyền tương ứng (`project:create/update/delete/view`).
2. User là PM của dự án khi thao tác trên dự án của mình (hoặc ADMIN).

## 7. Hậu điều kiện
1. Dự án được tạo/sửa/xóa mềm đúng yêu cầu.
2. PM (người tạo hoặc được chỉ định) là thành viên dự án với vai trò PROJECT_MANAGER.
3. Audit log ghi nhận hành động.

## 8. Luồng chính (tạo dự án)

| Bước | Tác nhân | Hành động | Kết quả |
|---|---|---|---|
| 1 | PM/ADMIN | Mở form tạo dự án, nhập `code, name, description, startDate, endDate, customerName, projectManagerId, status, note` | Gửi `POST /api/v1/projects` |
| 2 | Hệ thống | Validate (mã bắt buộc + unique, ngày hợp lệ, PM tồn tại) | Hợp lệ |
| 3 | Hệ thống | Tạo dự án trong transaction | Dự án mới |
| 4 | Hệ thống | Thêm PM vào `project_members` (vai trò PROJECT_MANAGER) | Thành viên PM |
| 5 | Hệ thống | Ghi audit | Bản ghi audit |
| 6 | Hệ thống | Trả `201` + DTO dự án | — |

## 9. Luồng thay thế

**9.1 Cập nhật dự án:** user mở dự án → sửa thông tin + gửi `version` → hệ thống validate + kiểm tra optimistic locking → lưu → `200` + audit.

**9.2 Xem chi tiết:** mở dự án từ danh sách → `GET /api/v1/projects/{id}` → trả thông tin + thống kê nhanh (task theo trạng thái, tiến độ).

**9.3 Danh sách/tìm kiếm:** `GET /api/v1/projects?keyword=&status=&memberOfMe=&page=&size=&sort=` → trả page. PM mặc định thấy dự án mình quản lý/tham gia; ADMIN thấy tất cả.

**9.4 Xóa mềm:** user bấm Xóa → confirm dialog → `DELETE /api/v1/projects/{id}` → đánh dấu `deleted_at` → `204` + audit. Dự án không hiển thị trong danh sách mặc định, truy cập trực tiếp trả `404`.

## 10. Luồng ngoại lệ

| # | Tình huống | Kết quả |
|---|---|---|
| 1 | Mã dự án đã tồn tại | `409` DUPLICATE (kèm message "Mã dự án đã tồn tại") |
| 2 | `endDate < startDate` | `400` VALIDATION_ERROR fieldErrors[endDate] |
| 3 | `projectManagerId` không tồn tại | `404` |
| 4 | User không phải PM dự án, không phải ADMIN | `403` |
| 5 | Sửa với `version` cũ | `409` CONFLICT |
| 6 | Truy cập dự án đã xóa mềm | `404` |
| 7 | Thiếu `code`/`name` | `400` fieldErrors |
| 8 | Page/size/sort không hợp lệ | `400` |

## 11. Validation từng trường

| Trường | Quy tắc | Message lỗi |
|---|---|---|
| `code` | Bắt buộc, 1–20 ký tự, unique (không phân biệt hoa thường — chờ xác nhận) | "Mã dự án không được để trống" / "Mã dự án đã tồn tại" |
| `name` | Bắt buộc, 1–100 ký tự | "Tên dự án không được để trống" |
| `description` | ≤ 2000 ký tự | "Mô tả tối đa 2000 ký tự" |
| `startDate` / `endDate` | ISO-8601; `endDate ≥ startDate` | "Ngày kết thúc không được nhỏ hơn ngày bắt đầu" |
| `status` | Thuộc enum ProjectStatus | "Trạng thái không hợp lệ" |
| `projectManagerId` | UUID tồn tại, là user ACTIVE | "Quản lý dự án không hợp lệ" |
| `customerName` | ≤ 100 ký tự | — |
| `version` | Bắt buộc khi update | "Phiên bản không hợp lệ" |

## 12. Business rule liên quan
BR-PROJ-01 (mã unique), BR-PROJ-02 (ngày), BR-PROJ-03 (ai tạo), BR-PROJ-04 (ai sửa/xóa), BR-PROJ-06 (PM là thành viên), BR-PROJ-07 (xóa mềm → 404), BR-PROJ-09 (cảnh báo khi xóa dự án ACTIVE có task chưa đóng).

## 13. Phân quyền

| Hành động | Quyền |
|---|---|
| Tạo | `project:create` (ADMIN, PROJECT_MANAGER) |
| Sửa / xóa | `project:update` / `project:delete` (ADMIN, PM của dự án) |
| Xem chi tiết / danh sách | `project:view` (ADMIN; thành viên dự án) |

## 14. Audit log cần ghi
Tạo dự án, sửa dự án (trước/sau), xóa mềm dự án — kèm `actorId, projectId, action, before/after (JSONB)`.

## 15. API dự kiến

| Method | Endpoint | Mục đích |
|---|---|---|
| POST | `/api/v1/projects` | Tạo dự án |
| GET | `/api/v1/projects` | Danh sách (keyword, status, page, size, sort) |
| GET | `/api/v1/projects/{id}` | Chi tiết |
| PUT | `/api/v1/projects/{id}` | Cập nhật (kèm version) |
| DELETE | `/api/v1/projects/{id}` | Xóa mềm |

## 16. Acceptance criteria (Given/When/Then)

| # | Given | When | Then |
|---|---|---|---|
| AC-003-01 | PM có quyền tạo, dữ liệu hợp lệ | Tạo dự án | `201`; dự án có `projectManager` là thành viên; `201` response là DTO không phải Entity |
| AC-003-02 | Mã dự án đã tồn tại | Tạo dự án trùng mã | `409` DUPLICATE |
| AC-003-03 | `endDate < startDate` | Tạo dự án | `400` fieldErrors[endDate] |
| AC-003-04 | USER không có quyền tạo | Tạo dự án | `403` |
| AC-003-05 | User sửa dự án với version đúng | Cập nhật | `200`; dữ liệu mới phản ánh thay đổi |
| AC-003-06 | User sửa với version cũ | Cập nhật | `409` CONFLICT, không ghi đè |
| AC-003-07 | MEMBER không phải PM dự án | Sửa dự án | `403` |
| AC-003-08 | Dự án đã xóa mềm | Truy cập chi tiết | `404`; không có trong danh sách mặc định |
| AC-003-09 | 25 dự án | Gọi danh sách page=0, size=10 | `200`; tổng phần tử 25, 10 bản ghi, `hasNext=true` |
| AC-003-10 | Tìm keyword "mobile" | Gọi danh sách keyword=mobile | Chỉ trả dự án khớp keyword |
| AC-003-11 | MEMBER chỉ thuộc 1 dự án | Gọi danh sách | Chỉ trả dự án mình tham gia |
