# UC-010 — Quản lý Milestone

> Dự án: PM Daily Work Management | Trạng thái: Draft
> FR liên quan: FR-MIL-01..04 | BR liên quan: BR-MIL-01..03

## 1. Mã Use Case
`UC-010`

## 2. Tên
Quản lý milestone (kế hoạch, theo dõi, hoàn thành)

## 3. Mô tả
PM khai báo milestone của dự án với ngày kế hoạch, theo dõi trạng thái và tiến độ; khi hoàn thành thì chuyển COMPLETED với progress 100 và ghi ngày thực tế. Milestone trễ hạn được hiển thị trạng thái DELAYED và xuất hiện trên dashboard (upcomingMilestones).

## 4. Actor
- ADMIN, PROJECT_MANAGER (toàn bộ).
- ADMIN, PM, MEMBER, VIEWER (xem).

## 5. Trigger
- Có mốc quan trọng của dự án cần theo dõi.
- Milestone đến gần / trễ / hoàn thành.

## 6. Tiền điều kiện
1. Dự án tồn tại, chưa xóa mềm.
2. User có quyền `milestone:manage` (ADMIN, PM dự án).

## 7. Hậu điều kiện
1. Milestone tạo/sửa/xóa mềm; COMPLETED ⇒ progress 100 + actualDate.
2. Audit log ghi nhận.

## 8. Luồng chính (tạo milestone)

| Bước | Tác nhân | Hành động | Kết quả |
|---|---|---|---|
| 1 | PM/ADMIN | Nhập name, description, plannedDate, note | Gửi `POST /api/v1/milestones` |
| 2 | Hệ thống | Validate: projectId/name/plannedDate bắt buộc | Hợp lệ |
| 3 | Hệ thống | Tạo milestone (status NOT_STARTED, progress 0) | Milestone mới |
| 4 | Hệ thống | Ghi audit | Bản ghi audit |
| 5 | Hệ thống | Trả `201` + DTO milestone | — |

## 9. Luồng thay thế

**9.1 Cập nhật tiến độ & trạng thái:** cập nhật progress/status + `version` → `PUT /api/v1/milestones/{id}` → COMPLETED ⇒ progress = 100 + ghi actualDate → `200` + audit.

**9.2 Đánh dấu trễ:** nếu `plannedDate` đã qua và status ≠ COMPLETED/CANCELLED, hệ thống đề xuất chuyển DELAYED (hoặc tự gợi ý — khuyến nghị BR-MIL-03).

**9.3 Danh sách/lọc:** `GET /api/v1/milestones?projectId=&status=&page=&size=&sort=plannedDate` (mặc định sắp theo plannedDate).

**9.4 Xem chi tiết:** `GET /api/v1/milestones/{id}`.

**9.5 Xóa mềm:** confirm → `DELETE /api/v1/milestones/{id}` → `204` + audit.

## 10. Luồng ngoại lệ

| # | Tình huống | Kết quả |
|---|---|---|
| 1 | `progress` ngoài [0,100] | `400` fieldErrors[progress] |
| 2 | COMPLETED mà progress < 100 | `400` |
| 3 | Version cũ | `409` CONFLICT |
| 4 | Milestone đã xóa mềm | `404` |
| 5 | Không đủ quyền | `403` |

## 11. Validation từng trường

| Trường | Quy tắc | Message lỗi |
|---|---|---|
| `projectId` | Bắt buộc | "Dự án không hợp lệ" |
| `name` | Bắt buộc, 1–150 ký tự | "Tên milestone không được để trống" |
| `description` | ≤ 2000 ký tự | — |
| `plannedDate` | Bắt buộc, ISO-8601 | "Ngày kế hoạch không được để trống" |
| `actualDate` | ISO-8601, ghi khi COMPLETED | — |
| `status` | Thuộc MilestoneStatus | "Trạng thái không hợp lệ" |
| `progress` | 0–100 | "Tiến độ phải từ 0 đến 100" |

## 12. Business rule liên quan
BR-MIL-01 (progress 0–100), BR-MIL-02 (COMPLETED ⇒ 100 + actualDate), BR-MIL-03 (DELAYED).

## 13. Phân quyền
- Tạo/sửa/xóa: `milestone:manage` (ADMIN, PM dự án).
- Xem: `milestone:view` (ADMIN, PM, MEMBER, VIEWER — phạm vi dự án).

## 14. Audit log cần ghi
Tạo/sửa/xóa milestone, chuyển trạng thái (kèm actualDate).

## 15. API dự kiến

| Method | Endpoint | Mục đích |
|---|---|---|
| POST | `/api/v1/milestones` | Tạo milestone |
| GET | `/api/v1/milestones` | Danh sách + filter |
| GET | `/api/v1/milestones/{id}` | Chi tiết |
| PUT | `/api/v1/milestones/{id}` | Cập nhật |
| DELETE | `/api/v1/milestones/{id}` | Xóa mềm |

## 16. Acceptance criteria (Given/When/Then)

| # | Given | When | Then |
|---|---|---|---|
| AC-010-01 | PM dự án | Tạo milestone hợp lệ | `201`; status NOT_STARTED, progress 0 |
| AC-010-02 | `progress = 101` | Cập nhật | `400` |
| AC-010-03 | Milestone progress 80 | Chuyển COMPLETED | `400` (phải 100 trước) |
| AC-010-04 | Milestone progress 100 | Chuyển COMPLETED | `200`; actualDate có giá trị |
| AC-010-05 | Version cũ | Cập nhật milestone | `409` |
| AC-010-06 | plannedDate qua, chưa đóng | Mở dashboard | Xuất hiện trong upcomingMilestones với trạng thái trễ (DELAYED gợi ý) |
| AC-010-07 | Milestone đã xóa mềm | Xem chi tiết | `404` |
| AC-010-08 | Sắp xếp mặc định | Gọi danh sách | Sắp theo plannedDate tăng dần |
| AC-010-09 | Không đủ quyền | Xóa milestone | `403` |
