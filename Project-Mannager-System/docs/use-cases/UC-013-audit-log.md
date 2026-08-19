# UC-013 — Nhật ký hoạt động (Audit Log)

> Dự án: PM Daily Work Management | Trạng thái: Draft
> FR liên quan: FR-AUD-01 | BR liên quan: BR-GEN-06, NFR-LOG-02

## 1. Mã Use Case
`UC-013`

## 2. Tên
Xem nhật ký hoạt động (audit log)

## 3. Mô tả
ADMIN xem nhật ký mọi hành động quan trọng của hệ thống: login/logout, tạo/sửa/xóa dữ liệu nghiệp vụ, thay đổi phân quyền — kèm ai thực hiện, khi nào, dữ liệu trước/sau. Có thể lọc theo người dùng, hành động, đối tượng, khoảng thời gian.

## 4. Actor
- ADMIN (`audit:view`).

## 5. Trigger
- Cần truy vết một thay đổi / sự cố.
- Kiểm tra định kỳ hoạt động hệ thống.

## 6. Tiền điều kiện
1. User có quyền `audit:view`.
2. Có bản ghi audit được sinh (BR-GEN-06).

## 7. Hậu điều kiện
1. Danh sách audit log được trả về đúng filter, phân trang.

## 8. Luồng chính

| Bước | Tác nhân | Hành động | Kết quả |
|---|---|---|---|
| 1 | ADMIN | Mở trang Nhật ký hoạt động, chọn filter (userId, action, fromDate, toDate, module) | Gửi `GET /api/v1/audit-logs?userId=&action=&fromDate=&toDate=&page=&size=` |
| 2 | Hệ thống | Validate filter + quyền `audit:view` | Hợp lệ |
| 3 | Hệ thống | Truy vấn bảng `audit_logs` (sắp giảm dần theo thời gian) | Page dữ liệu |
| 4 | Hệ thống | Trả `200` page; chi tiết before/after nếu cần | — |
| 5 | UI | Hiển thị bảng: thời gian, actor, hành động, đối tượng, kết quả | Hiển thị |

## 9. Luồng thay thế

**9.1 Xem chi tiết 1 bản ghi:** bấm vào bản ghi → `GET /api/v1/audit-logs/{id}` → xem `before_data` / `after_data` (JSONB).

## 10. Luồng ngoại lệ

| # | Tình huống | Kết quả |
|---|---|---|
| 1 | Không có bản ghi khớp filter | `200` page rỗng (empty state) |
| 2 | User không có quyền `audit:view` | `403` |
| 3 | Bản ghi không tồn tại | `404` |
| 4 | `fromDate > toDate` | `400` |

## 11. Validation từng trường

| Trường | Quy tắc | Message lỗi |
|---|---|---|
| `userId` | Tùy chọn, UUID | "Người dùng không hợp lệ" |
| `action` | Tùy chọn, thuộc danh sách action đã định nghĩa | "Hành động không hợp lệ" |
| `fromDate` / `toDate` | ISO-8601; fromDate ≤ toDate | "Khoảng thời gian không hợp lệ" |
| `page` / `size` | page ≥ 0, size 1–100 | "Phân trang không hợp lệ" |

## 12. Business rule liên quan
BR-GEN-06 (audit log hành động quan trọng), NFR-LOG-02 (before/after JSONB), NFR-LOG-03 (không log token/password — dữ liệu nhạy cảm được che).

## 13. Phân quyền
- Xem: `audit:view` (chỉ ADMIN).

## 14. Audit log cần ghi
Không (chính là chức năng đọc audit).

## 15. API dự kiến

| Method | Endpoint | Mục đích |
|---|---|---|
| GET | `/api/v1/audit-logs` | Danh sách + filter + phân trang |
| GET | `/api/v1/audit-logs/{id}` | Chi tiết bản ghi (before/after) |

## 16. Acceptance criteria (Given/When/Then)

| # | Given | When | Then |
|---|---|---|---|
| AC-013-01 | Có login + tạo task gần đây | Xem danh sách audit | Thấy đủ bản ghi, mới nhất trước |
| AC-013-02 | Lọc theo userId | Xem danh sách | Chỉ còn bản ghi của user đó |
| AC-013-03 | Lọc theo action | Xem danh sách | Chỉ còn bản ghi đúng action |
| AC-013-04 | Không có bản ghi khớp | Xem danh sách | `200` page rỗng |
| AC-013-05 | USER không phải ADMIN | Xem danh sách | `403` |
| AC-013-06 | Bản ghi có before/after | Xem chi tiết | Hiển thị dữ liệu trước/sau (dạng JSON) |
| AC-013-07 | `fromDate > toDate` | Xem danh sách | `400` |
| AC-013-08 | Bản ghi login | Xem danh sách | Không chứa password/token ở mọi dạng |
