# 06 — Acceptance Criteria tổng hợp

> Dự án: PM Daily Work Management
> Tài liệu tổng hợp toàn bộ Acceptance Criteria từ các Use Case (Prompt 03). Chi tiết từng UC tại `docs/use-cases/UC-*.md`.
> Cách đọc: AC-<UC>-<STT>. Khi viết test case (Prompt 20), mỗi AC sẽ được ánh xạ sang test case tương ứng.

## 1. UC-001 — Xác thực & phiên đăng nhập

| ID | Given | When | Then |
|---|---|---|---|
| AC-001-01 | Tài khoản ACTIVE, mật khẩu đúng | Đăng nhập | `200` + access/refresh token, không chứa password hash |
| AC-001-02 | Mật khẩu sai | Đăng nhập | `401` message chung, không phân biệt username tồn tại hay không |
| AC-001-03 | Tài khoản INACTIVE | Đăng nhập | `401` message chung |
| AC-001-04 | Username rỗng | Đăng nhập | `400` fieldErrors[username] |
| AC-001-05 | Access token hết hạn, refresh token hợp lệ | Refresh | `200` + access token mới |
| AC-001-06 | Refresh token đã revoke | Refresh | `401` |
| AC-001-07 | Refresh token hết hạn | Refresh | `401` |
| AC-001-08 | Đang có phiên | Đăng xuất | `204`; refresh token không dùng lại được |
| AC-001-09 | Đã đăng xuất | Đăng xuất lần nữa | `204` (idempotent) |
| AC-001-10 | Mật khẩu cũ đúng, mới hợp lệ | Đổi mật khẩu | `204`; đăng nhập được bằng mật khẩu mới; phiên cũ vô hiệu |
| AC-001-11 | Mật khẩu cũ sai | Đổi mật khẩu | `400` |
| AC-001-12 | Mật khẩu mới vi phạm policy | Đổi mật khẩu | `400` kèm message policy |
| AC-001-13 | ADMIN | Reset mật khẩu user | `204`; user đăng nhập bằng mật khẩu mới |
| AC-001-14 | Token hợp lệ | Gọi /auth/me | `200` user + roles + permissions, không có password hash |
| AC-001-15 | Token hết hạn | Gọi /auth/me | `401` |

## 2. UC-002 — Dashboard

| ID | Given | When | Then |
|---|---|---|---|
| AC-002-01 | 3 task hôm nay, 1 quá hạn, 1 họp hôm nay | Mở dashboard | `200`; totalTasksToday=3, overdueTasks=1, meetingsToday=1 |
| AC-002-02 | Task ở 3 trạng thái | Mở dashboard | tasksByStatus đủ 3 nhóm, số lượng đúng |
| AC-002-03 | Có task CRITICAL | Mở dashboard | tasksByPriority có CRITICAL |
| AC-002-04 | 2 dự án, chọn 1 | Lọc projectId | Số liệu chỉ tính dự án đã chọn |
| AC-002-05 | Không có dữ liệu | Mở dashboard | `200` toàn 0, empty state |
| AC-002-06 | Không có quyền | Mở dashboard | `403` |
| AC-002-07 | fromDate > toDate | Gửi dashboard | `400` |
| AC-002-08 | Task hạn hôm nay theo timezone user | User +07:00 mở dashboard | Tính trong "hôm nay" |

## 3. UC-003 — Dự án

| ID | Given | When | Then |
|---|---|---|---|
| AC-003-01 | PM, dữ liệu hợp lệ | Tạo dự án | `201`; PM là thành viên; response là DTO |
| AC-003-02 | Mã đã tồn tại | Tạo trùng mã | `409` |
| AC-003-03 | endDate < startDate | Tạo dự án | `400` fieldErrors[endDate] |
| AC-003-04 | Không có quyền tạo | Tạo dự án | `403` |
| AC-003-05 | Version đúng | Cập nhật | `200` |
| AC-003-06 | Version cũ | Cập nhật | `409`, không ghi đè |
| AC-003-07 | MEMBER không phải PM dự án | Sửa dự án | `403` |
| AC-003-08 | Dự án đã xóa mềm | Truy cập chi tiết | `404`; không có trong danh sách mặc định |
| AC-003-09 | 25 dự án | page=0, size=10 | `200`; 10 bản ghi, total=25, hasNext=true |
| AC-003-10 | Tìm "mobile" | keyword=mobile | Chỉ trả dự án khớp |
| AC-003-11 | MEMBER thuộc 1 dự án | Gọi danh sách | Chỉ trả dự án mình tham gia |

## 4. UC-004 — Thành viên dự án

| ID | Given | When | Then |
|---|---|---|---|
| AC-004-01 | PM dự án, user chưa là thành viên | Thêm thành viên | `201`; user thấy dự án |
| AC-004-02 | User đã là thành viên | Thêm lại | `409` |
| AC-004-03 | PM dự án | Đổi vai trò | `200`; vai trò mới phản ánh |
| AC-004-04 | Dự án chỉ còn 1 PM | Xóa PM đó | `400`, không xóa được |
| AC-004-05 | MEMBER | Thêm thành viên | `403` |
| AC-004-06 | Dự án đã xóa mềm | Thêm thành viên | `404` |
| AC-004-07 | role không hợp lệ | Thêm thành viên | `400` fieldErrors[role] |
| AC-004-08 | 3 thành viên | Xem danh sách | `200` đủ 3 + vai trò |

## 5. UC-005 — Công việc (trọng tâm)

| ID | Given | When | Then |
|---|---|---|---|
| AC-005-01 | PM, dữ liệu hợp lệ | Tạo task | `201` + code đúng định dạng |
| AC-005-02 | 2 request đồng thời | Tạo task cùng lúc | 2 mã khác nhau |
| AC-005-03 | title rỗng | Tạo task | `400` fieldErrors[title] |
| AC-005-04 | title > 200 ký tự | Tạo task | `400` |
| AC-005-05 | dueDate < startDate | Tạo task | `400` fieldErrors[dueDate] |
| AC-005-06 | progress = -1 hoặc 101 | Cập nhật progress | `400` |
| AC-005-07 | REVIEW, progress 50 | Chuyển DONE | `400` |
| AC-005-08 | BLOCKED không reason | Chuyển trạng thái | `400` |
| AC-005-09 | Assignee ngoài dự án | Giao task | `400` |
| AC-005-10 | Parent khác project | Tạo task con | `400` |
| AC-005-11 | Vòng lặp cha–con | Tạo task con | `400` |
| AC-005-12 | Task đã xóa mềm | Sửa task | `404` |
| AC-005-13 | Version cũ | Cập nhật | `409` |
| AC-005-14 | MEMBER sửa task người khác | Cập nhật | `403` |
| AC-005-15 | MEMBER sửa task được giao (status/progress) | Cập nhật | `200` |
| AC-005-16 | Nhiều thay đổi status/assignee/progress | Xem lịch sử | Đủ các thay đổi kèm actor + thời gian |
| AC-005-17 | Task quá hạn chưa DONE | Lọc overdue | Có trong kết quả |
| AC-005-18 | Task hạn hôm nay (timezone user) | /tasks/today | Có trong kết quả |
| AC-005-19 | Được giao 3 task | /tasks/my-tasks | Đúng 3 task |
| AC-005-20 | Keyword không khớp | Tìm kiếm | `200` page rỗng, total=0 |
| AC-005-21 | page vượt tổng trang | Gọi danh sách | `200` page rỗng (hoặc 400 theo quy ước) |
| AC-005-22 | Sort field không hợp lệ | Gọi danh sách | `400` |
| AC-005-23 | 100 task khớp filter | Export | File Excel đủ 100 dòng + header |
| AC-005-24 | File 50MB | Upload | `413` |
| AC-005-25 | Task REVIEW | PM chuyển DONE | `200`; progress=100, actualCompletedAt có giá trị |
| AC-005-26 | DONE → TODO trực tiếp | Chuyển trạng thái | `400` |

## 6. UC-006 — Cuộc họp

| ID | Given | When | Then |
|---|---|---|---|
| AC-006-01 | Dữ liệu hợp lệ | Tạo họp | `201`; participants nhận notification |
| AC-006-02 | endTime ≤ startTime | Tạo họp | `400` |
| AC-006-03 | Chủ trì ngoài dự án | Tạo họp | `400` |
| AC-006-04 | Participant trùng | Tạo họp | `400` |
| AC-006-05 | PM dự án, version đúng | Cập nhật họp | `200` |
| AC-006-06 | Version cũ | Cập nhật họp | `409` |
| AC-006-07 | Họp SCHEDULED | Hoàn thành | `200`; COMPLETED |
| AC-006-08 | Họp CANCELLED | Chuyển COMPLETED | `400` |
| AC-006-09 | Họp hôm nay (timezone user) | /meetings/today | Có trong kết quả |
| AC-006-10 | Họp đã xóa mềm | Xem chi tiết | `404` |
| AC-006-11 | MEMBER ngoài dự án | Xem họp | `403` |
| AC-006-12 | Không có họp | Danh sách | `200` page rỗng, empty state |

## 7. UC-007 — Action Item

| ID | Given | When | Then |
|---|---|---|---|
| AC-007-01 | PM dự án | Tạo AI | `201`; assignee nhận notification |
| AC-007-02 | Assignee ngoài dự án | Tạo AI | `400` |
| AC-007-03 | AI chưa link task | Chuyển thành task | `201`; task source=ACTION_ITEM, cùng project; AI có linkedTaskId |
| AC-007-04 | AI đã link task | Chuyển lần 2 | `409` |
| AC-007-05 | AI quá hạn chưa DONE | Lọc overdue | Có trong kết quả |
| AC-007-06 | Assignee | Cập nhật DONE | `200` |
| AC-007-07 | Không có quyền | Sửa AI | `403` |
| AC-007-08 | Version cũ | Cập nhật AI | `409` |

## 8. UC-008 — Risk

| ID | Given | When | Then |
|---|---|---|---|
| AC-008-01 | PM dự án | Tạo risk | `201`; code unique, level phản ánh probability × impact |
| AC-008-02 | Owner ngoài dự án | Tạo risk | `400` |
| AC-008-03 | Risk OPEN | Chuyển MONITORING | `200` |
| AC-008-04 | Risk OCCURRED chưa có issue | Chuyển thành issue | `201`; issue cùng project, liên kết 1–1 |
| AC-008-05 | Risk đã liên kết issue | Chuyển lần nữa | `409` |
| AC-008-06 | Version cũ | Cập nhật | `409` |
| AC-008-07 | Risk đã xóa mềm | Xem chi tiết | `404` |
| AC-008-08 | Lọc level=HIGH | Danh sách | Chỉ trả HIGH |
| AC-008-09 | Không có quyền | Xóa risk | `403` |

## 9. UC-009 — Issue

| ID | Given | When | Then |
|---|---|---|---|
| AC-009-01 | PM dự án | Tạo issue | `201`; code unique |
| AC-009-02 | Owner ngoài dự án | Tạo issue | `400` |
| AC-009-03 | Issue IN_PROGRESS | Chuyển RESOLVED (có solution) | `200`; resolvedAt có giá trị |
| AC-009-04 | Issue RESOLVED | Chuyển CLOSED | `200` |
| AC-009-05 | Version cũ | Cập nhật | `409` |
| AC-009-06 | Issue đã xóa mềm | Xem chi tiết | `404` |
| AC-009-07 | Lọc status=OPEN | Danh sách | Chỉ trả OPEN |
| AC-009-08 | Không có quyền | Sửa issue | `403` |
| AC-009-09 | Risk OCCURRED | Chuyển thành issue | Cùng project + liên kết |

## 10. UC-010 — Milestone

| ID | Given | When | Then |
|---|---|---|---|
| AC-010-01 | PM dự án | Tạo milestone | `201`; NOT_STARTED, progress 0 |
| AC-010-02 | progress = 101 | Cập nhật | `400` |
| AC-010-03 | progress 80 | Chuyển COMPLETED | `400` |
| AC-010-04 | progress 100 | Chuyển COMPLETED | `200`; actualDate có giá trị |
| AC-010-05 | Version cũ | Cập nhật | `409` |
| AC-010-06 | plannedDate qua, chưa đóng | Dashboard | Xuất hiện upcomingMilestones, gợi ý DELAYED |
| AC-010-07 | Đã xóa mềm | Xem chi tiết | `404` |
| AC-010-08 | Danh sách | Sắp xếp mặc định | Theo plannedDate tăng dần |
| AC-010-09 | Không có quyền | Xóa milestone | `403` |

## 11. UC-011 — Notification

| ID | Given | When | Then |
|---|---|---|---|
| AC-011-01 | 5 thông báo (2 chưa đọc) | Danh sách | `200`; unreadCount=2; mới nhất trước |
| AC-011-02 | Giao task | Sự kiện giao việc | User nhận TASK_ASSIGNED |
| AC-011-03 | Comment trên task theo dõi | Sự kiện comment | User nhận TASK_COMMENTED |
| AC-011-04 | Task sắp đến hạn | Job chạy | TASK_DUE_SOON cho assignee |
| AC-011-05 | Task vẫn sắp đến hạn | Job chạy lần 2 cùng ngày | Không tạo trùng |
| AC-011-06 | Bấm 1 thông báo | Đánh dấu đọc | `204`; unreadCount giảm 1 |
| AC-011-07 | 3 chưa đọc | Đánh dấu tất cả | `204`; unreadCount=0 |
| AC-011-08 | User A đánh dấu thông báo của B | Cố đánh dấu | `403` |
| AC-011-09 | Không có thông báo | Mở trang | Empty state |
| AC-011-10 | Đối tượng đã xóa mềm | Điều hướng | Không crash, hiển thị lỗi 404 |

## 12. UC-012 — Báo cáo

| ID | Given | When | Then |
|---|---|---|---|
| AC-012-01 | 10 task: 4/3/3 | task-by-status | Đếm đúng |
| AC-012-02 | 2 assignee | task-by-assignee | Đếm đúng theo người |
| AC-012-03 | Task quá hạn chưa DONE | overdue-tasks | Có trong báo cáo; DONE không tính |
| AC-012-04 | 2 dự án, chọn 1 | Lọc projectId | Chỉ tính dự án chọn |
| AC-012-05 | fromDate > toDate | Xem báo cáo | `400` |
| AC-012-06 | Không có dữ liệu | Xem báo cáo | `200` số liệu 0, empty state |
| AC-012-07 | 500 dòng | Export | File đủ 500 dòng + header |
| AC-012-08 | 15.000 dòng | Export | `400` từ chối |
| AC-012-09 | MEMBER | Xem báo cáo | `403` |
| AC-012-10 | 3 risk HIGH + 2 issue OPEN | risk-issue | Đếm đúng |

## 13. UC-013 — Audit log

| ID | Given | When | Then |
|---|---|---|---|
| AC-013-01 | Có login + tạo task | Danh sách audit | Đủ bản ghi, mới nhất trước |
| AC-013-02 | Lọc userId | Danh sách | Chỉ bản ghi user đó |
| AC-013-03 | Lọc action | Danh sách | Chỉ bản ghi đúng action |
| AC-013-04 | Không khớp | Danh sách | `200` page rỗng |
| AC-013-05 | Không phải ADMIN | Danh sách | `403` |
| AC-013-06 | Bản ghi có before/after | Chi tiết | Hiển thị dữ liệu trước/sau |
| AC-013-07 | fromDate > toDate | Danh sách | `400` |
| AC-013-08 | Bản ghi login | Danh sách | Không chứa password/token |

## 14. Tổng hợp theo nhóm kiểm thử bắt buộc (Prompt 20)

| Nhóm test | AC liên quan |
|---|---|
| Positive | AC-001-01, 003-01, 005-01, 006-01, 008-01, 009-01, 010-01... |
| Negative / Validation | AC-001-04, 003-03, 005-03..11, 006-02..04... |
| Boundary | AC-001-12 (policy), 005-04 (maxlength), 005-06 (0/100/101) |
| Permission | AC-002-06, 003-04/07, 004-05, 005-14, 006-11, 011-08, 012-09, 013-05 |
| Authentication | AC-001-02/03/05/06/07/15 |
| Pagination / Sorting | AC-003-09, 005-21/22 |
| Search / Filter | AC-003-10, 005-17, 008-08, 009-07 |
| Concurrent update | AC-003-06, 005-02/13, 006-06, 007-08, 008-06, 009-05, 010-05 |
| Soft delete | AC-003-08, 004-06, 005-12, 006-10, 008-07, 009-06, 010-07 |
| Empty data | AC-002-05, 005-20, 006-12, 011-09, 012-06, 013-04 |
| API error / size limit | AC-005-24, 012-08 |
| Token expiration | AC-001-05/06/07/15 |
| Date/timezone | AC-002-08, 005-18, 006-09, 010-06 |
