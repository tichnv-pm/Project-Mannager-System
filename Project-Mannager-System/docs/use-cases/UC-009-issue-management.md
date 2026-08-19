# UC-009 — Quản lý Issue

> Dự án: PM Daily Work Management | Trạng thái: Draft
> FR liên quan: FR-ISS-01..04 | BR liên quan: BR-ISS-01..04

## 1. Mã Use Case
`UC-009`

## 2. Tên
Quản lý vấn đề (issue) — ghi nhận, phân tích, xử lý, đóng

## 3. Mô tả
PM ghi nhận issue của dự án: mã tự sinh, mức nghiêm trọng, người phụ trách. Issue được phân tích root cause, đề xuất giải pháp, xử lý theo trạng thái; khi RESOLVED hệ thống tự ghi `resolvedAt`. Issue có thể được tạo trực tiếp hoặc từ risk OCCURRED (UC-008).

## 4. Actor
- ADMIN, PROJECT_MANAGER (toàn bộ).
- Owner (cập nhật trạng thái — chờ xác nhận FR-ISS-02).
- ADMIN, PM, MEMBER, VIEWER (xem).

## 5. Trigger
- Phát hiện vấn đề cần xử lý.
- Risk OCCURRED chuyển thành issue.

## 6. Tiền điều kiện
1. Dự án tồn tại, chưa xóa mềm.
2. User có quyền `issue:manage` (ADMIN, PM dự án).
3. Owner thuộc dự án.

## 7. Hậu điều kiện
1. Issue tạo/sửa/xóa mềm; mã unique; `resolvedAt` được ghi khi RESOLVED.
2. Audit log ghi nhận.

## 8. Luồng chính (tạo issue)

| Bước | Tác nhân | Hành động | Kết quả |
|---|---|---|---|
| 1 | PM/ADMIN | Nhập title, description, severity, ownerId, dueDate | Gửi `POST /api/v1/issues` |
| 2 | Hệ thống | Validate: projectId/title/ownerId/severity bắt buộc, owner ∈ project | Hợp lệ |
| 3 | Hệ thống | Sinh mã unique; tạo issue (status OPEN) | Issue mới |
| 4 | Hệ thống | Ghi audit | Bản ghi audit |
| 5 | Hệ thống | Trả `201` + DTO issue (kèm code) | — |

## 9. Luồng thay thế

**9.1 Cập nhật & xử lý:** sửa thông tin + `version` → `PUT /api/v1/issues/{id}` → validate + optimistic locking → lưu → `200` + audit. Khi chuyển RESOLVED: hệ thống ghi `resolvedAt` (khuyến nghị bắt buộc `solution`).

**9.2 Chuỗi trạng thái:** OPEN → ANALYZING → IN_PROGRESS → RESOLVED → CLOSED; REJECTED (từ OPEN/ANALYZING).

**9.3 Danh sách/lọc:** `GET /api/v1/issues?projectId=&status=&severity=&ownerId=&page=&size=&sort=`; chi tiết `GET /api/v1/issues/{id}`.

**9.4 Xóa mềm:** confirm → `DELETE /api/v1/issues/{id}` → `204` + audit.

## 10. Luồng ngoại lệ

| # | Tình huống | Kết quả |
|---|---|---|
| 1 | Owner không thuộc dự án | `400` |
| 2 | Severity không thuộc enum | `400` |
| 3 | Chuyển RESOLVED không có solution (nếu chốt bắt buộc BR-ISS-04) | `400` |
| 4 | Version cũ | `409` CONFLICT |
| 5 | Issue đã xóa mềm | `404` |
| 6 | Không đủ quyền | `403` |
| 7 | Mã trùng (race) | Retry sinh mã |

## 11. Validation từng trường

| Trường | Quy tắc | Message lỗi |
|---|---|---|
| `projectId` | Bắt buộc | "Dự án không hợp lệ" |
| `title` | Bắt buộc, 1–200 ký tự | "Tiêu đề không được để trống" |
| `severity` | Bắt buộc, enum LOW/MEDIUM/HIGH/CRITICAL | "Mức nghiêm trọng không hợp lệ" |
| `ownerId` | Bắt buộc, ∈ project | "Người phụ trách không hợp lệ" |
| `rootCause` / `solution` | ≤ 2000 ký tự | — |
| `status` | Thuộc IssueStatus | "Trạng thái không hợp lệ" |
| `dueDate` | Tùy chọn, ISO-8601 | — |

## 12. Business rule liên quan
BR-ISS-01 (owner ∈ project), BR-ISS-02 (mã unique), BR-ISS-03 (RESOLVED ⇒ resolvedAt), BR-ISS-04 (solution khi RESOLVED — khuyến nghị).

## 13. Phân quyền
- Tạo/sửa/xóa: `issue:manage` (ADMIN, PM dự án).
- Cập nhật trạng thái: owner (chờ xác nhận).
- Xem: `issue:view` (ADMIN, PM, MEMBER, VIEWER — phạm vi dự án).

## 14. Audit log cần ghi
Tạo/sửa/xóa issue, chuyển trạng thái (kèm resolvedAt).

## 15. API dự kiến

| Method | Endpoint | Mục đích |
|---|---|---|
| POST | `/api/v1/issues` | Tạo issue |
| GET | `/api/v1/issues` | Danh sách + filter |
| GET | `/api/v1/issues/{id}` | Chi tiết |
| PUT | `/api/v1/issues/{id}` | Cập nhật |
| DELETE | `/api/v1/issues/{id}` | Xóa mềm |

## 16. Acceptance criteria (Given/When/Then)

| # | Given | When | Then |
|---|---|---|---|
| AC-009-01 | PM dự án | Tạo issue hợp lệ | `201`; `code` unique |
| AC-009-02 | Owner ngoài dự án | Tạo issue | `400` |
| AC-009-03 | Issue IN_PROGRESS | Chuyển RESOLVED (có solution) | `200`; `resolvedAt` có giá trị |
| AC-009-04 | Issue RESOLVED | Chuyển CLOSED | `200` |
| AC-009-05 | Version cũ | Cập nhật issue | `409` |
| AC-009-06 | Issue đã xóa mềm | Xem chi tiết | `404` |
| AC-009-07 | Lọc status=OPEN | Gọi danh sách | Chỉ trả issue OPEN |
| AC-009-08 | Không đủ quyền | Sửa issue | `403` |
| AC-009-09 | Risk OCCURRED | Chuyển thành issue (UC-008) | Issue tạo từ risk có cùng project + liên kết |
