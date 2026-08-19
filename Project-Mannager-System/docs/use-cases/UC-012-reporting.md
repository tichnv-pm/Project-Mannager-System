# UC-012 — Báo cáo (Reporting)

> Dự án: PM Daily Work Management | Trạng thái: Draft
> FR liên quan: FR-REP-01..06 | BR liên quan: BR-REP-03, BR-REP-04

## 1. Mã Use Case
`UC-012`

## 2. Tên
Xem và xuất báo cáo tiến độ

## 3. Mô tả
PM/ADMIN xem các báo cáo: task theo trạng thái, task theo người thực hiện, task quá hạn, tiến độ dự án, tổng hợp risk/issue. Mọi báo cáo có filter dự án + khoảng thời gian, tính toán aggregate tại DB, và có thể xuất CSV/Excel trong giới hạn dòng cho phép.

## 4. Actor
- ADMIN, PROJECT_MANAGER (xem + export).
- VIEWER (xem — chờ xác nhận docs/05 mục 7).

## 5. Trigger
- PM cần báo cáo cho stakeholder / tự tổng hợp.
- Định kỳ tổng kết tuần/tháng.

## 6. Tiền điều kiện
1. User có quyền `report:view` / `report:export`.
2. Dự án tồn tại, chưa xóa mềm.

## 7. Hậu điều kiện
1. Báo cáo hiển thị đúng filter; export trả file hợp lệ.

## 8. Luồng chính (xem báo cáo task theo trạng thái)

| Bước | Tác nhân | Hành động | Kết quả |
|---|---|---|---|
| 1 | PM | Mở trang Báo cáo, chọn loại + projectId + fromDate/toDate | Gửi `GET /api/v1/reports/tasks-by-status?projectId=&fromDate=&toDate=` |
| 2 | Hệ thống | Validate filter + quyền `report:view` | Hợp lệ |
| 3 | Hệ thống | Aggregate tại DB: GROUP BY status | Bộ số liệu |
| 4 | Hệ thống | Trả `200` dữ liệu (có total) | — |
| 5 | UI | Render bảng + biểu đồ | Hiển thị |

## 9. Luồng thay thế

**9.1 Báo cáo task theo người thực hiện:** `GET /api/v1/reports/tasks-by-assignee` → GROUP BY assignee (số task, % hoàn thành).

**9.2 Báo cáo task quá hạn:** `GET /api/v1/reports/overdue-tasks` → danh sách task quá hạn (có phân trang, giới hạn dòng).

**9.3 Báo cáo tiến độ dự án:** `GET /api/v1/reports/project-progress` → progress từng dự án (từ task thực tế: trung bình tiến độ task theo project).

**9.4 Báo cáo risk & issue:** `GET /api/v1/reports/risk-issue` → tổng hợp risk/issue mở theo level/severity.

**9.5 Export:** chọn loại báo cáo + filter → `GET /api/v1/reports/{type}/export` → file CSV/Excel. Vượt 10.000 dòng → từ chối (BR-REP-04).

## 10. Luồng ngoại lệ

| # | Tình huống | Kết quả |
|---|---|---|
| 1 | Không có dữ liệu | `200` với số liệu 0; empty state |
| 2 | `fromDate > toDate` | `400` |
| 3 | Export > 10.000 dòng | `400` "Dữ liệu vượt giới hạn xuất, hãy thu hẹp filter" |
| 4 | Không có quyền | `403` |
| 5 | Dự án không tồn tại / đã xóa | `404` |
| 6 | Loại báo cáo không hợp lệ | `404` |

## 11. Validation từng trường

| Trường | Quy tắc | Message lỗi |
|---|---|---|
| `projectId` | Tùy chọn; UUID; thuộc phạm vi | "Dự án không hợp lệ" |
| `fromDate` / `toDate` | ISO-8601; fromDate ≤ toDate | "Khoảng thời gian không hợp lệ" |
| `page` / `size` | page ≥ 0, size 1–100 | "Phân trang không hợp lệ" |

## 12. Business rule liên quan
BR-REP-03 (aggregate tại DB, không N+1), BR-REP-04 (export ≤ 10.000 dòng).

## 13. Phân quyền
- Xem: `report:view` (ADMIN, PROJECT_MANAGER; VIEWER — chờ xác nhận).
- Export: `report:export` (ADMIN, PROJECT_MANAGER).

## 14. Audit log cần ghi
Export báo cáo (loại, filter, người xuất) — khuyến nghị.

## 15. API dự kiến

| Method | Endpoint | Mục đích |
|---|---|---|
| GET | `/api/v1/reports/tasks-by-status` | Task theo trạng thái |
| GET | `/api/v1/reports/tasks-by-assignee` | Task theo người thực hiện |
| GET | `/api/v1/reports/overdue-tasks` | Task quá hạn |
| GET | `/api/v1/reports/project-progress` | Tiến độ dự án |
| GET | `/api/v1/reports/risk-issue` | Risk & issue |
| GET | `/api/v1/reports/{type}/export` | Export CSV/Excel |

## 16. Acceptance criteria (Given/When/Then)

| # | Given | When | Then |
|---|---|---|---|
| AC-012-01 | 10 task: 4 DONE, 3 IN_PROGRESS, 3 TODO | Xem task-by-status | Đếm đúng 4/3/3 |
| AC-012-02 | 2 assignee với số task khác nhau | Xem task-by-assignee | Đếm đúng theo từng người |
| AC-012-03 | Có task quá hạn chưa DONE | Xem overdue-tasks | Có trong báo cáo; task DONE quá hạn không tính |
| AC-012-04 | 2 dự án, chọn 1 | Lọc projectId | Số liệu chỉ của dự án đã chọn |
| AC-012-05 | `fromDate > toDate` | Xem báo cáo | `400` |
| AC-012-06 | Không có dữ liệu | Xem báo cáo | `200` số liệu 0, empty state |
| AC-012-07 | 500 dòng phù hợp | Export | File có 500 dòng + header |
| AC-012-08 | 15.000 dòng phù hợp | Export | `400` từ chối, yêu cầu thu hẹp filter |
| AC-012-09 | MEMBER | Xem báo cáo | `403` (theo ma trận docs/05) |
| AC-012-10 | 3 risk HIGH + 2 issue OPEN | Xem risk-issue | Đếm đúng 3 risk HIGH, 2 issue OPEN |
