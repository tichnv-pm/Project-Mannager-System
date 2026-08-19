# 04 — Quy tắc nghiệp vụ (Business Rules)

> Dự án: PM Daily Work Management
> Quy ước ID: `BR-<NHÓM>-<NN>`. Mức: BẮT BUỘC (bắt buộc v1) / KHUYẾN NGHỊ / CHỜ XÁC NHẬN.
> Nguồn: bộ prompt đã thống nhất (Prompt 02, 10, 11, 12, 13, 14) và tài liệu `docs/01`, `docs/02`.

## 1. Quy tắc chung (BR-GEN)

| ID | Quy tắc | Mức | Ghi chú |
|---|---|---|---|
| BR-GEN-01 | PK dùng UUID; bảng `snake_case`; ngày giờ `timestamptz` (UTC) | BẮT BUỘC | docs/00 mục 10 |
| BR-GEN-02 | Bảng nghiệp vụ có `created_at, created_by, updated_at, updated_by, version` | BẮT BUỘC | Optimistic locking |
| BR-GEN-03 | Xóa mềm cho dữ liệu nghiệp vụ; danh sách mặc định không hiển thị dữ liệu đã xóa; truy cập trực tiếp trả 404 | BẮT BUỘC | Bảng mapping không bắt buộc xóa mềm |
| BR-GEN-04 | Không trả Entity qua API — dùng Request/Response DTO + MapStruct | BẮT BUỘC | |
| BR-GEN-05 | Error response thống nhất: `timestamp, status, error, code, message, path, fieldErrors, traceId` | BẮT BUỘC | docs/02, Prompt 06 |
| BR-GEN-06 | Audit log cho hành động quan trọng (tạo/sửa/xóa, login/logout, phân quyền) | BẮT BUỘC | |
| BR-GEN-07 | Không hard-code secret/password; đọc từ environment variable | BẮT BUỘC | |
| BR-GEN-08 | Cập nhật với version cũ → 409 Conflict (không ghi đè) | BẮT BUỘC | |
| BR-GEN-09 | Không sinh trùng mã nghiệp vụ (task/risk/issue code) dưới mọi tình huống concurrent | BẮT BUỘC | Phần "Sinh mã an toàn" mục 9 |

## 2. Tài khoản & xác thực (BR-AUTH)

| ID | Quy tắc | Mức |
|---|---|---|
| BR-AUTH-01 | Username và email unique | BẮT BUỘC |
| BR-AUTH-02 | Mật khẩu ≥ 8 ký tự, gồm chữ thường + chữ hoa + chữ số + ký tự đặc biệt | BẮT BUỘC |
| BR-AUTH-03 | Mật khẩu lưu bằng BCrypt (strength ≥ 10); không bao giờ trả qua API | BẮT BUỘC |
| BR-AUTH-04 | Access token ≤ 15 phút; refresh token ≤ 7 ngày, lưu DB, có revoke, đổi mật khẩu thì revoke toàn bộ | BẮT BUỘC |
| BR-AUTH-05 | Login thất bại trả message chung — không tiết lộ tài khoản có tồn tại | BẮT BUỘC |
| BR-AUTH-06 | Tài khoản INACTIVE không được đăng nhập (trả lỗi chung) | BẮT BUỘC |
| BR-AUTH-07 | Không có đăng ký công khai — tài khoản do ADMIN tạo | BẮT BUỘC |
| BR-AUTH-08 | Khóa tạm thời sau 5 lần đăng nhập sai liên tiếp (thời gian khóa 5 phút — tham số hóa) | BẮT BUỘC |
| BR-AUTH-09 | Refresh token đã dùng sẽ bị revoke (rotation); phát hiện token dùng lại → revoke toàn bộ chuỗi | BẮT BUỘC |

## 3. Dự án & thành viên (BR-PROJ)

| ID | Quy tắc | Mức |
|---|---|---|
| BR-PROJ-01 | Mã dự án (`code`) không trùng | BẮT BUỘC |
| BR-PROJ-02 | `endDate` ≥ `startDate` | BẮT BUỘC |
| BR-PROJ-03 | Chỉ ADMIN hoặc PROJECT_MANAGER tạo dự án | BẮT BUỘC |
| BR-PROJ-04 | Chỉ ADMIN hoặc PM của dự án sửa/xóa dự án | BẮT BUỘC |
| BR-PROJ-05 | Không thêm trùng thành viên vào dự án | BẮT BUỘC |
| BR-PROJ-06 | PM của dự án phải là thành viên dự án | BẮT BUỘC |
| BR-PROJ-07 | Dự án đã xóa mềm không truy cập theo cách thông thường (404) | BẮT BUỘC |
| BR-PROJ-08 | Không xóa thành viên cuối cùng mang vai trò PROJECT_MANAGER nếu chưa gán PM mới | CHỜ XÁC NHẬN |
| BR-PROJ-09 | Dự án ACTIVE có task chưa đóng: xóa phải có cảnh báo xác nhận | KHUYẾN NGHỊ |
| BR-PROJ-10 | Vai trò trong dự án: `PROJECT_MANAGER, TECH_LEAD, BUSINESS_ANALYST, DEVELOPER, TESTER, DEVOPS, MEMBER` | BẮT BUỘC |

## 4. Công việc (BR-TASK)

| ID | Quy tắc | Mức |
|---|---|---|
| BR-TASK-01 | `title` và `projectId` bắt buộc | BẮT BUỘC |
| BR-TASK-02 | `dueDate` ≥ `startDate` | BẮT BUỘC |
| BR-TASK-03 | `progress` trong [0, 100] | BẮT BUỘC |
| BR-TASK-04 | Khi `status = DONE` thì `progress = 100` | BẮT BUỘC |
| BR-TASK-05 | Khi `progress = 100` → được phép chuyển DONE (không bắt buộc tự động) | CHỜ XÁC NHẬN |
| BR-TASK-06 | Khi chuyển DONE phải ghi `actualCompletedAt` | BẮT BUỘC |
| BR-TASK-07 | Task con phải cùng project với task cha | BẮT BUỘC |
| BR-TASK-08 | Không tạo vòng lặp cha–con (task không thể là cha của chính nó hoặc tổ tiên của nó) | BẮT BUỘC |
| BR-TASK-09 | Không cập nhật task đã xóa mềm (404) | BẮT BUỘC |
| BR-TASK-10 | Khi `status = BLOCKED` bắt buộc có `blockerReason` | BẮT BUỘC |
| BR-TASK-11 | Assignee/collaborators/watchers phải là thành viên của project | BẮT BUỘC |
| BR-TASK-12 | Người giao việc: ADMIN hoặc PM dự án (hoặc quyền task:assign) | BẮT BUỘC |
| BR-TASK-13 | MEMBER chỉ được sửa task được giao cho mình (status/progress/notes) hoặc được cấp quyền | BẮT BUỘC |
| BR-TASK-14 | Mã task tự sinh `PRJXXX-TASK-000001`, unique, sinh an toàn concurrent | BẮT BUỘC |
| BR-TASK-15 | File đính kèm ≤ 10MB/file, whitelist mime type | BẮT BUỘC |
| BR-TASK-16 | Comment dài 1–2000 ký tự | BẮT BUỘC |
| BR-TASK-17 | Task có con thì xóa cha: xóa mềm cả cây con HOẶC chặn đến khi xóa con — chọn 1 | CHỜ XÁC NHẬN |
| BR-TASK-18 | Export giới hạn 10.000 dòng | BẮT BUỘC |

## 5. Cuộc họp (BR-MEET)

| ID | Quy tắc | Mức |
|---|---|---|
| BR-MEET-01 | `endTime` > `startTime` | BẮT BUỘC |
| BR-MEET-02 | Chủ trì (`chairpersonId`) phải thuộc project | BẮT BUỘC |
| BR-MEET-03 | Người tham gia không trùng, đều thuộc project | BẮT BUỘC |
| BR-MEET-04 | Ít nhất một trong `location`/`meetingLink` khi lên lịch (trừ họp online chỉ link) | CHỜ XÁC NHẬN |
| BR-MEET-05 | Họp `COMPLETED` mới cho phép khóa biên bản (không sửa thêm sau khi khóa — tùy chọn) | CHỜ XÁC NHẬN |
| BR-MEET-06 | Họp `CANCELLED` không chuyển sang trạng thái khác được | BẮT BUỘC |

## 6. Action item (BR-AI)

| ID | Quy tắc | Mức |
|---|---|---|
| BR-AI-01 | Action item phải thuộc cùng project với meeting | BẮT BUỘC |
| BR-AI-02 | `assigneeId` phải thuộc project | BẮT BUỘC |
| BR-AI-03 | Chuyển action item thành task: task tạo mới `source = ACTION_ITEM`; không tạo trùng (mỗi AI tối đa 1 task liên kết) | BẮT BUỘC |
| BR-AI-04 | Action item đã có `linkedTaskId` không chuyển lại lần nữa (409) | BẮT BUỘC |

## 7. Risk (BR-RISK)

| ID | Quy tắc | Mức |
|---|---|---|
| BR-RISK-01 | `ownerId` phải thuộc project | BẮT BUỘC |
| BR-RISK-02 | Mã risk tự sinh, không trùng | BẮT BUỘC |
| BR-RISK-03 | Risk `OCCURRED` → có thể chuyển thành issue (liên kết 1–1, không trùng) | BẮT BUỘC |
| BR-RISK-04 | `level` tính từ (probability × impact) hoặc chọn tay — chốt 1 phương án | CHỜ XÁC NHẬN |
| BR-RISK-05 | Risk `CLOSED` không đổi trạng thái khác được (trừ khi có quyền đặc biệt) | KHUYẾN NGHỊ |

## 8. Issue (BR-ISS)

| ID | Quy tắc | Mức |
|---|---|---|
| BR-ISS-01 | `ownerId` phải thuộc project | BẮT BUỘC |
| BR-ISS-02 | Mã issue tự sinh, không trùng | BẮT BUỘC |
| BR-ISS-03 | Khi `status = RESOLVED` phải ghi `resolvedAt` | BẮT BUỘC |
| BR-ISS-04 | Issue RESOLVED nên có `solution` | KHUYẾN NGHỊ |

## 9. Milestone (BR-MIL)

| ID | Quy tắc | Mức |
|---|---|---|
| BR-MIL-01 | `progress` trong [0, 100] | BẮT BUỘC |
| BR-MIL-02 | `status = COMPLETED` ⇒ `progress = 100` (và ghi `actualDate`) | BẮT BUỘC |
| BR-MIL-03 | Trạng thái `DELAYED` chỉ khi có sự kiện thực tế hoặc trễ hạn — hiển thị rõ trong UI | KHUYẾN NGHỊ |

## 10. Notification (BR-NOTIF)

| ID | Quy tắc | Mức |
|---|---|---|
| BR-NOTIF-01 | Sinh notification khi: giao task, task sắp đến hạn, task quá hạn, comment mới, được thêm vào họp, được giao action item | BẮT BUỘC |
| BR-NOTIF-02 | Job định kỳ không tạo notification trùng (dedupe theo recipient + type + taskId + ngày) | BẮT BUỘC |
| BR-NOTIF-03 | Notification chỉ thuộc về người nhận; không ai khác đọc được | BẮT BUỘC |
| BR-NOTIF-04 | Chỉ in-app ở v1 — chưa có email | BẮT BUỘC |

## 11. Dashboard & Report (BR-REP)

| ID | Quy tắc | Mức |
|---|---|---|
| BR-REP-01 | Dashboard trả đủ 13 nhóm số liệu + 3 biểu đồ (docs/02 FR-DASH-01); filter theo projectId/fromDate/toDate | BẮT BUỘC |
| BR-REP-02 | "Hôm nay" tính theo timezone người dùng | BẮT BUỘC |
| BR-REP-03 | Report tính tại DB (aggregate), không N+1, không load toàn bộ | BẮT BUỘC |
| BR-REP-04 | Export tối đa 10.000 dòng | BẮT BUỘC |

## 12. Sinh mã tự động an toàn concurrent

Áp dụng cho: task code, risk code, issue code, project code.

1. Mã theo dạng `PREFIX + số thứ tự đã pad` (VD `PRJ001-TASK-000001`).
2. Sinh số thứ tự trong **cùng transaction với INSERT** — dùng khóa DB (unique constraint) + retry khi conflict; không dùng `max(code) + 1` ngoài transaction.
3. Có unique constraint ở DB làm lớp bảo vệ cuối; vi phạm unique → sinh lại mã (tối đa N lần) rồi mới trả lỗi 409/500.
4. Test concurrent: chạy nhiều luồng tạo task cùng lúc, đảm bảo không trùng mã.

## 13. Tổng hợp quy tắc chờ xác nhận

| # | Nội dung | Ảnh hưởng nếu chọn khác |
|---|---|---|
| 1 | Chặn xóa PM cuối cùng của dự án (BR-PROJ-08) | UX quản trị |
| 2 | Xóa task cha xóa cả cây con hay chặn (BR-TASK-17) | Data integrity |
| 3 | Level risk tính tự động hay chọn tay (BR-RISK-04) | Form & logic |
| 4 | Progress 100 có tự chuyển DONE không (BR-TASK-05) | Luồng làm việc |
| 5 | Họp COMPLETED có khóa biên bản không (BR-MEET-05) | Permission sửa biên bản |
| 6 | Chủ trì họp có quyền sửa họp không (FR-MEET-02) | Permission |
| 7 | Bắt buộc location hoặc meetingLink (BR-MEET-04) | Validation form |
| 8 | Issue RESOLVED bắt buộc solution (BR-ISS-04) | Validation |
