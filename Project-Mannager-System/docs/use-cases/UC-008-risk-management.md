# UC-008 — Quản lý Risk

> Dự án: PM Daily Work Management | Trạng thái: Draft
> FR liên quan: FR-RISK-01..05 | BR liên quan: BR-RISK-01..05

## 1. Mã Use Case
`UC-008`

## 2. Tên
Quản lý rủi ro (risk) — nhận diện, đánh giá, theo dõi, chuyển thành issue

## 3. Mô tả
PM nhận diện risk của dự án: mã tự sinh, đánh giá xác suất và mức ảnh hưởng, xác định mức độ (level), giao người phụ trách, lập phương án giảm thiểu (mitigation) và phương án dự phòng (contingency). Risk được theo dõi theo trạng thái; khi risk xảy ra (OCCURRED) có thể chuyển thành issue liên kết 1–1.

## 4. Actor
- ADMIN, PROJECT_MANAGER (toàn bộ).
- Owner (cập nhật trạng thái — chờ xác nhận FR-RISK-02).
- ADMIN, PM, MEMBER, VIEWER (xem).

## 5. Trigger
- Phát hiện rủi ro mới của dự án.
- Risk thay đổi mức độ / xảy ra / đóng.

## 6. Tiền điều kiện
1. Dự án tồn tại, chưa xóa mềm.
2. User có quyền `risk:manage` (ADMIN, PM dự án).
3. Owner thuộc dự án.

## 7. Hậu điều kiện
1. Risk tạo/sửa/xóa mềm; mã unique.
2. Khi chuyển OCCURRED + có issue liên kết: issue được tạo (không trùng).
3. Audit log ghi nhận.

## 8. Luồng chính (tạo risk)

| Bước | Tác nhân | Hành động | Kết quả |
|---|---|---|---|
| 1 | PM/ADMIN | Nhập title, description, probability, impact, ownerId, mitigationPlan, contingencyPlan, dueDate | Gửi `POST /api/v1/risks` |
| 2 | Hệ thống | Validate: projectId/title/ownerId bắt buộc, owner ∈ project | Hợp lệ |
| 3 | Hệ thống | Tính/chọn level từ probability × impact (chờ xác nhận BR-RISK-04); sinh mã unique | Risk mới |
| 4 | Hệ thống | Ghi audit | Bản ghi audit |
| 5 | Hệ thống | Trả `201` + DTO risk (kèm code, level) | — |

## 9. Luồng thay thế

**9.1 Cập nhật:** sửa thông tin + `version` → `PUT /api/v1/risks/{id}` → validate + optimistic locking → lưu → `200` + audit.

**9.2 Đổi trạng thái:** OPEN → MONITORING → MITIGATED → CLOSED; OPEN → OCCURRED (khi xảy ra).

**9.3 Chuyển thành issue:** risk chuyển OCCURRED → `POST /api/v1/risks/{id}/convert-to-issue` → tạo issue (project giống risk, severity mặc định theo level, owner = owner risk) → liên kết risk↔issue → `201`. Risk đã liên kết → `409`.

**9.4 Danh sách/lọc:** `GET /api/v1/risks?projectId=&status=&level=&ownerId=&page=&size=&sort=`; xem chi tiết `GET /api/v1/risks/{id}`.

**9.5 Xóa mềm:** confirm → `DELETE /api/v1/risks/{id}` → `204` + audit.

## 10. Luồng ngoại lệ

| # | Tình huống | Kết quả |
|---|---|---|
| 1 | Owner không thuộc dự án | `400` |
| 2 | Level không thuộc enum | `400` |
| 3 | Risk đã liên kết issue | `409` khi chuyển lần nữa |
| 4 | Version cũ | `409` CONFLICT |
| 5 | Risk đã xóa mềm | `404` |
| 6 | Không đủ quyền | `403` |
| 7 | Mã risk trùng (race) | Retry sinh mã, hoặc `409` khi hết lượt |

## 11. Validation từng trường

| Trường | Quy tắc | Message lỗi |
|---|---|---|
| `projectId` | Bắt buộc | "Dự án không hợp lệ" |
| `title` | Bắt buộc, 1–200 ký tự | "Tiêu đề không được để trống" |
| `probability` / `impact` | Bắt buộc, enum LOW/MEDIUM/HIGH | "Xác suất/ảnh hưởng không hợp lệ" |
| `level` | Thuộc RiskLevel (tự tính hoặc chọn tay) | "Mức độ không hợp lệ" |
| `ownerId` | Bắt buộc, ∈ project | "Người phụ trách không hợp lệ" |
| `mitigationPlan` / `contingencyPlan` | ≤ 2000 ký tự | — |
| `status` | Thuộc RiskStatus | "Trạng thái không hợp lệ" |
| `dueDate` | Tùy chọn, ISO-8601 | — |

## 12. Business rule liên quan
BR-RISK-01 (owner ∈ project), BR-RISK-02 (mã unique), BR-RISK-03 (chuyển issue không trùng), BR-RISK-04 (level), BR-RISK-05 (CLOSED không đổi trạng thái — khuyến nghị).

## 13. Phân quyền
- Tạo/sửa/xóa/chuyển issue: `risk:manage` (ADMIN, PM dự án).
- Cập nhật trạng thái: owner (chờ xác nhận).
- Xem: `risk:view` (ADMIN, PM, MEMBER, VIEWER — phạm vi dự án).

## 14. Audit log cần ghi
Tạo/sửa/xóa risk, chuyển trạng thái, chuyển risk thành issue (kèm issueId).

## 15. API dự kiến

| Method | Endpoint | Mục đích |
|---|---|---|
| POST | `/api/v1/risks` | Tạo risk |
| GET | `/api/v1/risks` | Danh sách + filter |
| GET | `/api/v1/risks/{id}` | Chi tiết |
| PUT | `/api/v1/risks/{id}` | Cập nhật |
| DELETE | `/api/v1/risks/{id}` | Xóa mềm |
| POST | `/api/v1/risks/{id}/convert-to-issue` | Chuyển thành issue |

## 16. Acceptance criteria (Given/When/Then)

| # | Given | When | Then |
|---|---|---|---|
| AC-008-01 | PM dự án | Tạo risk hợp lệ | `201`; `code` unique, level phản ánh probability × impact (nếu chốt tự tính) |
| AC-008-02 | Owner ngoài dự án | Tạo risk | `400` |
| AC-008-03 | Risk OPEN | Chuyển MONITORING | `200` |
| AC-008-04 | Risk OCCURRED chưa có issue | Chuyển thành issue | `201`; issue cùng project, liên kết risk↔issue |
| AC-008-05 | Risk đã liên kết issue | Chuyển thành issue lần nữa | `409` |
| AC-008-06 | Version cũ | Cập nhật risk | `409` |
| AC-008-07 | Risk đã xóa mềm | Xem chi tiết | `404` |
| AC-008-08 | Lọc level=HIGH | Gọi danh sách | Chỉ trả risk HIGH |
| AC-008-09 | Không đủ quyền | Xóa risk | `403` |
