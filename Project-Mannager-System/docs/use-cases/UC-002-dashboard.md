# UC-002 — Dashboard công việc hằng ngày

> Dự án: PM Daily Work Management | Trạng thái: Draft
> FR liên quan: FR-DASH-01 | BR liên quan: BR-REP-01, BR-REP-02, BR-REP-03

## 1. Mã Use Case
`UC-002`

## 2. Tên
Xem dashboard tổng quan công việc hằng ngày

## 3. Mô tả
PM (hoặc ADMIN) mở màn hình dashboard để nắm toàn cảnh trong 1 lượt: task hôm nay/quá hạn/sắp đến hạn/đang thực hiện/blocked, họp hôm nay, action item chưa đóng, risk cao, issue mở, milestone sắp tới, kèm biểu đồ task theo trạng thái/ưu tiên và tiến độ dự án. Dashboard có filter theo dự án và khoảng thời gian.

## 4. Actor
- PROJECT_MANAGER (mặc định), ADMIN.
- PROJECT_MEMBER / VIEWER: xem phạm vi dự án mình tham gia (nếu được cấu hình).

## 5. Trigger
- User mở trang Dashboard.
- User thay đổi filter (dự án / khoảng thời gian).

## 6. Tiền điều kiện
1. User đã đăng nhập, có quyền `dashboard:view`.
2. Có dữ liệu task/họp/action item/risk/issue/milestone trong phạm vi.

## 7. Hậu điều kiện
1. Dashboard hiển thị đủ 13 nhóm số liệu + 3 biểu đồ.
2. Số liệu phản ánh đúng filter hiện tại, tính theo timezone người dùng cho "hôm nay".

## 8. Luồng chính

| Bước | Tác nhân | Hành động | Kết quả |
|---|---|---|---|
| 1 | User | Mở trang Dashboard | Gửi `GET /api/v1/dashboard?projectId=&fromDate=&toDate=` |
| 2 | Hệ thống | Kiểm tra quyền `dashboard:view` + phạm vi dự án | Hợp lệ |
| 3 | Hệ thống | Aggregate tại DB: đếm task theo điều kiện (hôm nay/quá hạn/sắp hạn/in-progress/blocked), họp hôm nay, action item chưa đóng, risk HIGH/CRITICAL, issue chưa đóng, milestone sắp tới | Bộ số liệu |
| 4 | Hệ thống | Aggregate biểu đồ: task theo status, task theo priority, tiến độ từng dự án | Bộ dữ liệu biểu đồ |
| 5 | Hệ thống | Trả `200` payload đầy đủ | — |
| 6 | UI | Render cards + biểu đồ + skeleton trong lúc loading | Hiển thị hoàn chỉnh |

## 9. Luồng thay thế

**9.1 Lọc theo dự án:** user chọn dự án trong dropdown → gọi lại dashboard với `projectId` → số liệu chỉ tính trong dự án đó.

**9.2 Lọc theo thời gian:** user chọn khoảng `fromDate`–`toDate` → các chỉ số có ngày được tính trong khoảng (mặc định: hôm nay).

**9.3 Nhấn vào card:** nhấn card "Quá hạn" → điều hướng sang danh sách task với filter tương ứng (overdue=true).

## 10. Luồng ngoại lệ

| # | Tình huống | Kết quả |
|---|---|---|
| 1 | Không có dữ liệu trong phạm vi | `200` với toàn bộ số liệu = 0; UI hiển thị empty state |
| 2 | User không có quyền `dashboard:view` | `403` ACCESS_DENIED |
| 3 | `projectId` không hợp lệ / không thuộc phạm vi | `404` hoặc `403` |
| 4 | `fromDate` > `toDate` | `400` VALIDATION_ERROR |
| 5 | Token hết hạn | `401` → refresh token → gọi lại |

## 11. Validation từng trường

| Trường | Quy tắc | Message lỗi |
|---|---|---|
| `projectId` | Tùy chọn; UUID hợp lệ; thuộc phạm vi user | "Dự án không hợp lệ" |
| `fromDate` / `toDate` | ISO-8601; `fromDate ≤ toDate` | "Khoảng thời gian không hợp lệ" |

## 12. Business rule liên quan
BR-REP-01 (13 nhóm số liệu + biểu đồ), BR-REP-02 ("hôm nay" theo timezone user), BR-REP-03 (aggregate tại DB, không N+1).

## 13. Phân quyền
- `dashboard:view`: ADMIN, PROJECT_MANAGER, PROJECT_MEMBER (phạm vi dự án tham gia), VIEWER (phạm vi dự án tham gia).

## 14. Audit log cần ghi
Không (chỉ đọc).

## 15. API dự kiến

| Method | Endpoint | Mục đích |
|---|---|---|
| GET | `/api/v1/dashboard` | Trả toàn bộ số liệu dashboard, filter qua query params |

## 16. Acceptance criteria (Given/When/Then)

| # | Given | When | Then |
|---|---|---|---|
| AC-002-01 | PM có 3 task hôm nay, 1 task quá hạn, 1 họp hôm nay | Mở dashboard | `200`; `totalTasksToday=3, overdueTasks=1, meetingsToday=1` |
| AC-002-02 | PM có task ở 3 trạng thái | Mở dashboard | `tasksByStatus` có đúng 3 nhóm + số lượng đúng |
| AC-002-03 | Có task CRITICAL trong phạm vi | Mở dashboard | `tasksByPriority` có nhóm CRITICAL |
| AC-002-04 | 2 dự án trong hệ thống, chọn 1 | Lọc theo projectId | Số liệu chỉ tính cho dự án đã chọn |
| AC-002-05 | Không có bất kỳ dữ liệu nào | Mở dashboard | `200` toàn bộ số liệu = 0, UI empty state |
| AC-002-06 | User không có quyền dashboard | Mở dashboard | `403` |
| AC-002-07 | `fromDate > toDate` | Gửi dashboard | `400` |
| AC-002-08 | Task hạn hôm nay theo timezone +07:00 | User ở múi giờ +07:00 mở dashboard | Task đó được tính trong "hôm nay" |
