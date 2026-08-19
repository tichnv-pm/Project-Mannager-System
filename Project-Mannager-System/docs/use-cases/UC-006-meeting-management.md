# UC-006 — Quản lý cuộc họp (Meeting Management)

> Dự án: PM Daily Work Management | Trạng thái: Draft
> FR liên quan: FR-MEET-01..07 | BR liên quan: BR-MEET-01..06

## 1. Mã Use Case
`UC-006`

## 2. Tên
Quản lý cuộc họp (lên lịch / cập nhật / biên bản / người tham gia / hoàn thành / xóa)

## 3. Mô tả
PM lên lịch cuộc họp cho dự án với thời gian, địa điểm/meeting link, chủ trì, người tham gia, agenda. Trong/sau họp, cập nhật nội dung, kết luận và chuyển trạng thái COMPLETED để khóa biên bản. Người tham gia được thông báo khi được thêm vào họp. Họp hôm nay xuất hiện trên dashboard và danh sách riêng.

## 4. Actor
- ADMIN, PROJECT_MANAGER (tạo/sửa/xóa/hoàn thành).
- ADMIN, thành viên dự án (xem, là người tham gia).

## 5. Trigger
- Cần tổ chức / thay đổi / ghi biên bản cuộc họp.

## 6. Tiền điều kiện
1. Dự án tồn tại, chưa xóa mềm.
2. Chủ trì và người tham gia thuộc dự án.
3. User có quyền `meeting:manage` (ADMIN, PM dự án).

## 7. Hậu điều kiện
1. Họp được tạo/sửa/xóa mềm; biên bản được ghi; trạng thái chuyển COMPLETED khi khóa.
2. Notification `MEETING_INVITED` gửi cho người tham gia.
3. Audit log ghi nhận.

## 8. Luồng chính (tạo họp)

| Bước | Tác nhân | Hành động | Kết quả |
|---|---|---|---|
| 1 | PM/ADMIN | Nhập title, projectId, startTime, endTime, location/meetingLink, chairpersonId, participantIds, agenda | Gửi `POST /api/v1/meetings` |
| 2 | Hệ thống | Validate: endTime > startTime, chủ trì ∈ project, participants unique + ∈ project | Hợp lệ |
| 3 | Hệ thống | Tạo họp (status SCHEDULED) + bản ghi người tham gia | Họp mới |
| 4 | Hệ thống | Gửi notification MEETING_INVITED cho participants | Notification |
| 5 | Hệ thống | Ghi audit | Bản ghi audit |
| 6 | Hệ thống | Trả `201` + DTO họp (kèm participants) | — |

## 9. Luồng thay thế

**9.1 Cập nhật họp:** sửa thông tin + `version` → `PUT /api/v1/meetings/{id}` → validate → lưu → `200` + audit. Thêm người tham gia mới → notification mới.

**9.2 Ghi biên bản & hoàn thành:** sau họp, cập nhật content/conclusion → chuyển status COMPLETED → `200`. (Khóa biên bản — chờ xác nhận BR-MEET-05.)

**9.3 Xem chi tiết:** `GET /api/v1/meetings/{id}` → thông tin + agenda + content + conclusion + action items + attachments.

**9.4 Danh sách/lọc:** `GET /api/v1/meetings?projectId=&status=&fromTime=&toTime=&keyword=&page=&size=` → page.

**9.5 Họp hôm nay:** `GET /api/v1/meetings/today` → họp hôm nay theo timezone user (dùng cho dashboard).

**9.6 Xóa họp:** confirm → `DELETE /api/v1/meetings/{id}` → xóa mềm → `204` + audit. (Action item của họp giữ hay xóa — chờ xác nhận FR-MEET-07.)

**9.7 Hủy họp:** chuyển status CANCELLED thay vì xóa (khuyến nghị).

## 10. Luồng ngoại lệ

| # | Tình huống | Kết quả |
|---|---|---|
| 1 | `endTime ≤ startTime` | `400` fieldErrors[endTime] |
| 2 | Chủ trì không thuộc dự án | `400` |
| 3 | Người tham gia trùng | `400` "Người tham gia không được trùng" |
| 4 | Người tham gia không thuộc dự án | `400` |
| 5 | Không có location lẫn meetingLink | `400` (nếu chốt bắt buộc BR-MEET-04) |
| 6 | Version cũ | `409` CONFLICT |
| 7 | Họp đã xóa mềm | `404` |
| 8 | Không đủ quyền | `403` |
| 9 | Họp CANCELLED chuyển trạng thái khác | `400` |

## 11. Validation từng trường

| Trường | Quy tắc | Message lỗi |
|---|---|---|
| `title` | Bắt buộc, 1–200 ký tự | "Tiêu đề không được để trống" |
| `projectId` | Bắt buộc | "Dự án không hợp lệ" |
| `startTime` / `endTime` | ISO-8601; `endTime > startTime` | "Thời gian kết thúc phải sau thời gian bắt đầu" |
| `location` / `meetingLink` | Ít nhất 1 (chờ xác nhận); meetingLink phải là URL hợp lệ | "Cần địa điểm hoặc link họp" |
| `chairpersonId` | Bắt buộc, ∈ project | "Người chủ trì không hợp lệ" |
| `participantIds` | Không trùng, ∈ project | "Người tham gia không hợp lệ" |
| `version` | Bắt buộc khi update | "Phiên bản không hợp lệ" |

## 12. Business rule liên quan
BR-MEET-01 (endTime > startTime), BR-MEET-02 (chủ trì), BR-MEET-03 (participants), BR-MEET-04, BR-MEET-05 (khóa biên bản), BR-MEET-06 (CANCELLED).

## 13. Phân quyền
- Tạo/sửa/xóa/hoàn thành: `meeting:manage` (ADMIN, PM dự án; chủ trì hoàn thành họp — chờ xác nhận).
- Xem: `meeting:view` (ADMIN, PM, MEMBER, VIEWER — phạm vi dự án).

## 14. Audit log cần ghi
Tạo/sửa/xóa họp, thêm người tham gia, chuyển trạng thái (SCHEDULED → COMPLETED/CANCELLED).

## 15. API dự kiến

| Method | Endpoint | Mục đích |
|---|---|---|
| POST | `/api/v1/meetings` | Tạo họp |
| GET | `/api/v1/meetings` | Danh sách + filter |
| GET | `/api/v1/meetings/{id}` | Chi tiết (biên bản) |
| PUT | `/api/v1/meetings/{id}` | Cập nhật |
| DELETE | `/api/v1/meetings/{id}` | Xóa mềm |
| GET | `/api/v1/meetings/today` | Họp hôm nay |

## 16. Acceptance criteria (Given/When/Then)

| # | Given | When | Then |
|---|---|---|---|
| AC-006-01 | Dữ liệu hợp lệ | Tạo họp | `201`; participants nhận notification MEETING_INVITED |
| AC-006-02 | `endTime ≤ startTime` | Tạo họp | `400` |
| AC-006-03 | Chủ trì không thuộc dự án | Tạo họp | `400` |
| AC-006-04 | Participant trùng lặp | Tạo họp | `400` |
| AC-006-05 | PM dự án | Cập nhật họp version đúng | `200` |
| AC-006-06 | Version cũ | Cập nhật họp | `409` |
| AC-006-07 | Họp đang SCHEDULED | Hoàn thành họp | `200`; status = COMPLETED |
| AC-006-08 | Họp CANCELLED | Chuyển COMPLETED | `400` |
| AC-006-09 | Họp hôm nay theo timezone user | Gọi /meetings/today | Có trong kết quả |
| AC-006-10 | Họp đã xóa mềm | Xem chi tiết | `404` |
| AC-006-11 | MEMBER ngoài dự án | Xem họp | `403` |
| AC-006-12 | Không có họp nào | Gọi danh sách | `200` page rỗng (empty state) |
