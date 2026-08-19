# 02 — Yêu cầu chức năng (Functional Requirements)

> Dự án: PM Daily Work Management
> Trạng thái: Draft. Nguồn: Prompt 02. Chi tiết luồng: `docs/use-cases/*` (Prompt 03).
> Quy ước ID: `FR-<MODULE>-<NN>`. Module: `AUTH, USER, PROJ, TASK, MEET, AI (action item), RISK, ISS, DEC, MIL, NOTIF, DASH, REP, AUD`.

## 1. Danh mục & enum dùng chung

### 1.1 Trạng thái công việc (TaskStatus)

| Giá trị | Ý nghĩa |
|---|---|
| `TODO` | Chưa bắt đầu |
| `IN_PROGRESS` | Đang thực hiện |
| `BLOCKED` | Bị chặn (bắt buộc có lý do blocker) |
| `REVIEW` | Đang chờ review |
| `DONE` | Hoàn thành (progress = 100, ghi actualCompletedAt) |
| `CANCELLED` | Hủy |

### 1.2 Mức ưu tiên (Priority)

`LOW`, `MEDIUM`, `HIGH`, `CRITICAL` — mặc định `MEDIUM`.

### 1.3 Loại công việc (TaskType) — *cần xác nhận*

`FEATURE`, `BUG`, `IMPROVEMENT`, `TASK`, `OTHER` — mặc định `TASK`.

### 1.4 Nguồn công việc (TaskSource) — *cần xác nhận*

`MANUAL`, `MEETING`, `ACTION_ITEM`, `ISSUE`, `OTHER` — mặc định `MANUAL`.

### 1.5 Trạng thái dự án (ProjectStatus)

`PLANNING`, `ACTIVE`, `ON_HOLD`, `COMPLETED`, `CANCELLED` — mặc định `PLANNING`.

### 1.6 Vai trò thành viên dự án (ProjectMemberRole)

`PROJECT_MANAGER`, `TECH_LEAD`, `BUSINESS_ANALYST`, `DEVELOPER`, `TESTER`, `DEVOPS`, `MEMBER`.

### 1.7 Trạng thái cuộc họp (MeetingStatus)

`SCHEDULED`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`.

### 1.8 Trạng thái Action Item — *cần xác nhận*

`OPEN`, `IN_PROGRESS`, `DONE`, `CANCELLED` — mặc định `OPEN`.

### 1.9 Risk

- Trạng thái: `OPEN`, `MONITORING`, `MITIGATED`, `OCCURRED`, `CLOSED` — mặc định `OPEN`.
- Mức độ (level): `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`.
- Xác suất / ảnh hưởng: `LOW`, `MEDIUM`, `HIGH`. — *cần xác nhận*

### 1.10 Issue

- Trạng thái: `OPEN`, `ANALYZING`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`, `REJECTED` — mặc định `OPEN`.
- Mức nghiêm trọng (severity): `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`. — *cần xác nhận*

### 1.11 Milestone

`NOT_STARTED`, `IN_PROGRESS`, `COMPLETED`, `DELAYED`, `CANCELLED` — mặc định `NOT_STARTED`.

### 1.12 Notification type

`TASK_ASSIGNED`, `TASK_DUE_SOON`, `TASK_OVERDUE`, `TASK_COMMENTED`, `MEETING_INVITED`, `ACTION_ITEM_ASSIGNED`.

### 1.13 Trạng thái tài khoản (UserStatus) — *cần xác nhận*

`ACTIVE`, `INACTIVE` — mặc định `ACTIVE`.

## 2. Trường dữ liệu công việc (Task)

| Nhóm | Trường | Bắt buộc | Ghi chú |
|---|---|---|---|
| Nhận dạng | `code` | Có (tự sinh) | `PRJ001-TASK-000001` — sinh an toàn concurrent |
| | `title` | Có | |
| | `description` | Không | |
| Định danh | `projectId` | Có | |
| | `parentTaskId` | Không | Task con cùng project; cấm vòng lặp |
| | `children` | — | Quan hệ con |
| Phân công | `reporterId` (người giao) | Có | Người tạo |
| | `assigneeId` (người thực hiện) | Không | Phải thuộc project |
| | `collaborators` (người phối hợp) | Không | Nhiều người, thuộc project |
| | `watchers` (người theo dõi) | Không | Nhiều người |
| Kế hoạch | `status` | Có | Enum 1.1 |
| | `priority` | Có | Enum 1.2, mặc định MEDIUM |
| | `type` | Có | Enum 1.3 |
| | `source` | Không | Enum 1.4 |
| | `startDate`, `dueDate` | Không* | dueDate ≥ startDate (*khuyến nghị bắt buộc khi tạo việc làm) |
| | `estimateMinutes` | Không | Thời gian dự kiến |
| Thực hiện | `progress` (0–100) | Có | DONE ⇒ 100 |
| | `actualCompletedAt` | Không | Ghi khi DONE |
| | `actualMinutes` | Không | Thời gian thực tế |
| | `blocked` + `blockerReason` | Không | BLOCKED ⇒ bắt buộc reason |
| | `notes` | Không | Ghi chú |
| | `tags` | Không | Nhiều tag |
| | `attachments` | Không | File metadata + storage path |
| Audit | `createdAt, createdBy, updatedAt, updatedBy, version` | Có | Hệ thống quản lý |

## 3. Trường dữ liệu Meeting / Risk / Issue / Milestone

**Meeting:** `title*`, `projectId*`, `startTime*`, `endTime*` (end > start), `location` / `meetingLink` (ít nhất 1 trong 2 — *cần xác nhận*), `chairpersonId*`, `participants[]` (không trùng), `agenda`, `content`, `conclusion`, `status`, `attachments`.

**Action Item:** `meetingId*`, `projectId*`, `title*`, `description`, `assigneeId*` (thuộc project), `dueDate`, `priority`, `status`, `progress`, `linkedTaskId` (sau khi chuyển thành task).

**Risk:** `code*` (tự sinh, không trùng), `projectId*`, `title*`, `description`, `probability*`, `impact*`, `level*` (tự tính theo xác suất × ảnh hưởng hoặc chọn tay — *cần xác nhận*), `ownerId*`, `mitigationPlan`, `contingencyPlan`, `status*`, `dueDate`.

**Issue:** `code*` (tự sinh, không trùng), `projectId*`, `title*`, `description`, `severity*`, `ownerId*`, `rootCause`, `solution`, `status*`, `dueDate`, `resolvedAt` (tự ghi khi RESOLVED).

**Milestone:** `projectId*`, `name*`, `description`, `plannedDate*`, `actualDate` (tự ghi khi COMPLETED — *cần xác nhận*), `status*`, `progress*` (0–100; COMPLETED ⇒ 100), `note`.

## 4. Yêu cầu chức năng chi tiết

### 4.1 Auth & tài khoản

#### FR-AUTH-01 Đăng nhập

| Hạng mục | Nội dung |
|---|---|
| Mục tiêu | Xác thực người dùng bằng username/password, cấp access token + refresh token |
| Actor | Tất cả người dùng ACTIVE |
| Tiền điều kiện | Tài khoản tồn tại, trạng thái ACTIVE |
| Hậu điều kiện | Đăng nhập thành công: trả access token (ngắn hạn), refresh token (dài hạn, lưu DB); ghi audit login |
| Luồng chính | 1. User nhập username/password → 2. Hệ thống xác thực BCrypt → 3. Kiểm tra tài khoản ACTIVE → 4. Cấp cặp token + thông tin user cơ bản (roles/permissions) |
| Luồng thay thế | Sai mật khẩu ≤ 5 lần liên tiếp: khóa tài khoản tạm thời (*cần xác nhận*) |
| Ngoại lệ | Sai username/mật khẩu: trả lỗi chung "Tên đăng nhập hoặc mật khẩu không đúng" — không tiết lộ tài khoản có tồn tại; tài khoản INACTIVE: trả lỗi chung tương tự |
| Validation | username/password không rỗng; password ≤ 72 ký tự (BCrypt) |
| Phân quyền | Công khai (không cần token) |
| Audit log | Có — login thành công/thất bại (username, thời gian, IP) |
| Acceptance criteria | 1. Given tài khoản hợp lệ, When đăng nhập đúng, Then nhận được token và 200. 2. Given sai mật khẩu, When đăng nhập, Then 401 và message chung. 3. Given tài khoản INACTIVE, When đăng nhập, Then 401. 4. Given username không tồn tại, When đăng nhập, Then 401 cùng message chung. |

#### FR-AUTH-02 Refresh token

| Hạng mục | Nội dung |
|---|---|
| Mục tiêu | Cấp access token mới khi token cũ hết hạn |
| Actor | Người dùng đã đăng nhập |
| Tiền điều kiện | Refresh token hợp lệ, chưa hết hạn, chưa revoke |
| Hậu điều kiện | Access token mới (và refresh token mới theo chính sách rotation) |
| Luồng chính | 1. Client gửi refresh token → 2. Kiểm tra tồn tại + chưa revoke + chưa hết hạn → 3. Cấp token mới |
| Luồng thay thế | Có rotation: refresh token cũ bị revoke sau khi dùng |
| Ngoại lệ | Token revoked/hết hạn/không tồn tại: 401, buộc đăng nhập lại; dùng token revoked: có thể revoke cả chuỗi (detect reuse) — *cần xác nhận* |
| Validation | Refresh token không rỗng |
| Phân quyền | Công khai (không cần access token) |
| Audit log | Có — sự kiện refresh thành công/thất bại |
| Acceptance criteria | 1. Given token hợp lệ, When refresh, Then 200 + access token mới. 2. Given token revoked, When refresh, Then 401. 3. Given token hết hạn, When refresh, Then 401. |

#### FR-AUTH-03 Đăng xuất

| Hạng mục | Nội dung |
|---|---|
| Mục tiêu | Revoke refresh token đang dùng, kết thúc phiên |
| Actor | Người dùng đã đăng nhập |
| Tiền điều kiện | Có access token hợp lệ + refresh token cần revoke |
| Hậu điều kiện | Refresh token bị revoke; access token cũng không dùng được nữa (tùy chính sách) |
| Luồng chính | 1. Client gửi refresh token cần revoke → 2. Revoke → 3. Trả 204 |
| Ngoại lệ | Token không tồn tại: vẫn trả thành công (idempotent) |
| Phân quyền | Yêu cầu access token hợp lệ |
| Audit log | Có — đăng xuất |
| Acceptance criteria | 1. Given đang đăng nhập, When logout, Then 204 và refresh token không dùng được. 2. Given logout 2 lần, When logout lần 2, Then vẫn 204. |

#### FR-AUTH-04 Xem thông tin tài khoản hiện tại

| Hạng mục | Nội dung |
|---|---|
| Mục tiêu | Lấy thông tin user + roles + permissions hiện tại |
| Actor | Người dùng đã đăng nhập |
| Luồng chính | 1. Gọi GET /api/v1/auth/me → 2. Trả thông tin user (không kèm password hash) |
| Ngoại lệ | Token hết hạn: 401 |
| Phân quyền | Yêu cầu xác thực |
| Audit log | Không |
| Acceptance criteria | Given token hợp lệ, When gọi /me, Then 200 và không chứa password hash. |

#### FR-AUTH-05 Đổi mật khẩu

| Hạng mục | Nội dung |
|---|---|
| Mục tiêu | Người dùng tự đổi mật khẩu |
| Actor | Người dùng đã đăng nhập |
| Tiền điều kiện | Mật khẩu cũ đúng, mật khẩu mới hợp lệ |
| Hậu điều kiện | Mật khẩu mới được lưu (BCrypt); refresh token của user bị revoke (buộc đăng nhập lại) — *cần xác nhận* |
| Luồng chính | 1. Nhập mật khẩu cũ + mới → 2. Xác thực mật khẩu cũ → 3. Kiểm tra policy → 4. Lưu hash mới → 5. Revoke refresh tokens |
| Ngoại lệ | Mật khẩu cũ sai: 400; mật khẩu mới vi phạm policy: 400 |
| Validation | Mật khẩu mới ≥ 8 ký tự, có chữ + số + ký tự đặc biệt (BR-AUTH-02); khác mật khẩu cũ |
| Phân quyền | Chính chủ tài khoản |
| Audit log | Có — đổi mật khẩu |
| Acceptance criteria | 1. Given mật khẩu cũ đúng, When đổi, Then 204 và đăng nhập được với mật khẩu mới. 2. Given mật khẩu cũ sai, When đổi, Then 400. |

#### FR-AUTH-06 Admin reset mật khẩu

| Hạng mục | Nội dung |
|---|---|
| Mục tiêu | Admin đặt lại mật khẩu cho user (không cần mật khẩu cũ) |
| Actor | ADMIN |
| Luồng chính | 1. Admin chọn user → 2. Nhập mật khẩu mới → 3. Lưu hash + revoke refresh tokens |
| Ngoại lệ | User không tồn tại: 404 |
| Phân quyền | ADMIN |
| Audit log | Có — ai reset cho ai |
| Acceptance criteria | Given ADMIN, When reset, Then user đăng nhập được bằng mật khẩu mới và session cũ bị vô hiệu. |

#### FR-USER-01 Quản lý tài khoản

| Hạng mục | Nội dung |
|---|---|
| Mục tiêu | Admin tạo/sửa/vô hiệu hóa tài khoản, gán vai trò hệ thống |
| Actor | ADMIN |
| Luồng chính | 1. Tạo user (username, email, tên, vai trò) → 2. Cấp mật khẩu tạm → 3. Kích hoạt/vô hiệu hóa → 4. Sửa vai trò |
| Ngoại lệ | Username/email trùng: 409; Admin không thể vô hiệu hóa chính mình — *cần xác nhận* |
| Validation | username/email đúng định dạng, unique; không để trống họ tên |
| Phân quyền | ADMIN |
| Audit log | Có — tạo/sửa/vô hiệu hóa tài khoản |
| Acceptance criteria | 1. Given ADMIN, When tạo user, Then user đăng nhập được. 2. Given username trùng, When tạo, Then 409. 3. Given user INACTIVE, When đăng nhập, Then 401. |

#### FR-USER-02 Quản lý vai trò & quyền

| Hạng mục | Nội dung |
|---|---|
| Mục tiêu | Admin xem/gán quyền cho vai trò (roles × permissions) |
| Actor | ADMIN |
| Luồng chính | 1. Xem danh sách vai trò → 2. Xem quyền của vai trò → 3. Gỡ/thêm quyền (v1: theo ma trận mặc định trong docs/05) |
| Ngoại lệ | Không cho gỡ quyền cuối cùng của ADMIN |
| Phân quyền | ADMIN |
| Audit log | Có — thay đổi phân quyền |
| Acceptance criteria | Given ADMIN, When thay đổi quyền vai trò, Then người dùng mang vai trò đó áp dụng quyền mới ở lần gọi sau. |

### 4.2 Dashboard

#### FR-DASH-01 Xem dashboard

| Hạng mục | Nội dung |
|---|---|
| Mục tiêu | Màn hình tổng quan hằng ngày cho PM |
| Actor | PROJECT_MANAGER (mặc định), ADMIN; MEMBER/VIEWER tùy phân quyền — *cần xác nhận* |
| Tiền điều kiện | Đã đăng nhập; có dữ liệu task/họp trong phạm vi |
| Luồng chính | 1. Chọn project (hoặc "Tất cả") + khoảng thời gian (mặc định hôm nay) → 2. Hệ thống trả 1 lượt: totalTasksToday, overdueTasks, upcomingTasks, inProgressTasks, blockedTasks, meetingsToday, pendingActionItems, highRisks, openIssues, upcomingMilestones → 3. Trả biểu đồ tasksByStatus, tasksByPriority, projectProgress → 4. UI render cards + biểu đồ |
| Luồng thay thế | Không có dữ liệu: hiển thị empty state |
| Ngoại lệ | Project bị xóa mềm: loại khỏi danh sách; quyền không đủ: 403 |
| Validation | Filter: projectId (tùy chọn), fromDate/toDate (ISO-8601); số liệu aggregate tại DB, tránh N+1 |
| Phân quyền | ADMIN/PM: tất cả project; MEMBER: project của mình |
| Audit log | Không |
| Acceptance criteria | 1. Given PM có dữ liệu, When mở dashboard, Then đủ 13 nhóm số liệu + biểu đồ. 2. Given filter project, When chọn, Then số liệu theo project đó. 3. Given VIEWER, When mở, Then được phép xem (nếu được cấu hình). |

### 4.3 Dự án & thành viên

#### FR-PROJ-01 Tạo dự án

| Hạng mục | Nội dung |
|---|---|
| Mục tiêu | Tạo dự án mới với mã không trùng |
| Actor | ADMIN, PROJECT_MANAGER |
| Tiền điều kiện | Có quyền project:create |
| Hậu điều kiện | Dự án được tạo; PM (người tạo hoặc được chỉ định) trở thành thành viên |
| Luồng chính | 1. Nhập code, name, description, startDate, endDate, customerName, projectManagerId → 2. Validate → 3. Tạo + thêm PM vào project_members |
| Ngoại lệ | Mã trùng: 409; endDate < startDate: 400 |
| Validation | code/name bắt buộc, code unique (không phân biệt hoa thường — *cần xác nhận*); endDate ≥ startDate |
| Phân quyền | ADMIN, PROJECT_MANAGER |
| Audit log | Có — tạo dự án |
| Acceptance criteria | 1. Given PM hợp lệ, When tạo, Then 201 và PM là thành viên. 2. Given mã trùng, When tạo, Then 409. 3. Given endDate < startDate, When tạo, Then 400. |

#### FR-PROJ-02 Cập nhật dự án

| Hạng mục | Nội dung |
|---|---|
| Mục tiêu | Sửa thông tin dự án |
| Actor | ADMIN, PM của dự án |
| Luồng chính | 1. Mở dự án → 2. Sửa thông tin → 3. Validate + optimistic locking → 4. Lưu |
| Ngoại lệ | Version cũ: 409 conflict; dự án đã xóa mềm: 404; không phải PM dự án: 403 |
| Validation | Như FR-PROJ-01; kèm version bắt buộc |
| Phân quyền | ADMIN hoặc thành viên có vai trò PROJECT_MANAGER của dự án |
| Audit log | Có |
| Acceptance criteria | Given version đúng, When sửa, Then 200. Given version cũ, When sửa, Then 409. |

#### FR-PROJ-03 Xem chi tiết dự án

| Hạng mục | Nội dung |
|---|---|
| Actor | ADMIN, PM, MEMBER, VIEWER của dự án |
| Luồng chính | 1. Mở dự án → 2. Xem tổng quan: thông tin, tiến độ, thống kê nhanh |
| Ngoại lệ | Không phải thành viên và không phải ADMIN: 403; đã xóa mềm: 404 |
| Phân quyền | ADMIN hoặc thành viên dự án (hoặc được chia sẻ) |
| Audit log | Không |
| Acceptance criteria | Given thành viên dự án, When mở, Then 200. Given ngoài dự án, When mở, Then 403. |

#### FR-PROJ-04 Danh sách, tìm kiếm, lọc dự án

| Hạng mục | Nội dung |
|---|---|
| Mục tiêu | Liệt kê dự án theo quyền với search/filter/pagination/sort |
| Luồng chính | 1. Gọi danh sách với filter (keyword, status, member của tôi) → 2. Trả page dữ liệu |
| Ngoại lệ | Sort field không nằm whitelist: 400; page vượt tổng trang: trả page cuối hoặc 400 — *cần xác nhận* |
| Validation | page ≥ 0, size 1–100, sort theo whitelist |
| Phân quyền | ADMIN/PM: tất cả; MEMBER/VIEWER: dự án họ là thành viên |
| Acceptance criteria | Given filter status, When tìm, Then chỉ trả dự án đúng trạng thái. |

#### FR-PROJ-05 Xóa mềm dự án

| Hạng mục | Nội dung |
|---|---|
| Mục tiêu | Xóa mềm dự án; dữ liệu con (task, meeting...) giữ nguyên |
| Actor | ADMIN (khuyến nghị); PM dự án — *cần xác nhận* |
| Luồng chính | 1. Xác nhận → 2. Đánh dấu deleted → 3. Không hiển thị trong danh sách mặc định |
| Ngoại lệ | Dự án ACTIVE có task đang chạy: cảnh báo trước khi xóa |
| Phân quyền | ADMIN; PM của dự án (nếu xác nhận) |
| Audit log | Có — xóa mềm dự án |
| Acceptance criteria | Given dự án đã xóa, When tìm kiếm mặc định, Then không xuất hiện; khi truy cập trực tiếp, Then 404. |

#### FR-PROJ-06 Quản lý thành viên dự án

| Hạng mục | Nội dung |
|---|---|
| Mục tiêu | Thêm/xóa thành viên, đổi vai trò trong dự án |
| Actor | ADMIN, PM của dự án |
| Luồng chính | 1. Tìm user → 2. Thêm vào dự án + chọn vai trò → 3. Đổi vai trò → 4. Xóa thành viên |
| Ngoại lệ | Thêm trùng: 409; xóa PM duy nhất của dự án: chặn hoặc phải gán PM mới trước — *cần xác nhận* |
| Validation | User tồn tại; không thêm trùng |
| Phân quyền | ADMIN, PM của dự án |
| Audit log | Có — thêm/xóa/đổi vai trò thành viên |
| Acceptance criteria | Given PM dự án, When thêm user, Then user thấy dự án. Given thêm trùng, Then 409. |

#### FR-PROJ-07 Danh sách thành viên dự án

| Hạng mục | Nội dung |
|---|---|
| Actor | ADMIN, thành viên dự án |
| Luồng chính | 1. Mở tab Thành viên → 2. Xem danh sách user + vai trò |
| Phân quyền | ADMIN hoặc thành viên dự án |
| Acceptance criteria | Given thành viên, When mở tab, Then thấy danh sách người tham gia. |

### 4.4 Công việc (Task)

#### FR-TASK-01 Tạo công việc

| Hạng mục | Nội dung |
|---|---|
| Mục tiêu | Tạo task mới trong dự án; tự sinh mã `PRJXXX-TASK-000001` |
| Actor | ADMIN, PM, thành viên có quyền tạo |
| Tiền điều kiện | Thuộc dự án (hoặc ADMIN); dự án chưa xóa mềm |
| Hậu điều kiện | Task tồn tại ở trạng thái TODO; mã duy nhất; audit + history |
| Luồng chính | 1. Chọn project, parent task (tùy chọn), nhập title/description/assignee/collaborators/status/priority/type/source/dates/progress/estimate/tags/blocker/notes → 2. Validate → 3. Sinh mã → 4. Lưu |
| Luồng thay thế | Tạo kèm comments/attachments đầu tiên; tạo task con ngay khi tạo |
| Ngoại lệ | Assignee/collaborator không thuộc dự án: 400; parent task khác project: 400; vòng lặp cha-con: 400 |
| Validation | title/projectId bắt buộc; dueDate ≥ startDate; progress 0–100; assignee ∈ project; parentTask cùng project; BLOCKED ⇒ blockerReason |
| Phân quyền | ADMIN, PM dự án, hoặc thành viên có quyền task:create |
| Audit log | Có — tạo task |
| Acceptance criteria | 1. Given PM hợp lệ, When tạo, Then 201 + mã task đúng định dạng. 2. Given assignee ngoài dự án, Then 400. 3. Given parent khác project, Then 400. |

#### FR-TASK-02 Cập nhật công việc

| Hạng mục | Nội dung |
|---|---|
| Mục tiêu | Sửa thông tin task |
| Actor | ADMIN, PM dự án, người được giao (phạm vi hẹp) |
| Luồng chính | 1. Sửa thông tin + version → 2. Validate → 3. Lưu + history |
| Ngoại lệ | Version cũ: 409; task đã xóa mềm: 404; không quyền: 403 |
| Validation | Như FR-TASK-01; DONE ⇒ progress 100; progress 100 không bắt buộc DONE — *cần xác nhận* |
| Phân quyền | ADMIN, PM dự án; assignee được sửa các trường được phép (status/progress/notes) — chi tiết tại docs/05 |
| Audit log | Có |
| Acceptance criteria | Given version đúng, When sửa, Then 200 + history mới. Given version cũ, Then 409. |

#### FR-TASK-03 Xem chi tiết công việc

| Hạng mục | Nội dung |
|---|---|
| Actor | ADMIN, thành viên dự án có task |
| Luồng chính | 1. Mở task → 2. Xem thông tin + bình luận + lịch sử + task con |
| Ngoại lệ | Đã xóa mềm: 404; ngoài dự án: 403 |
| Phân quyền | ADMIN hoặc thành viên dự án |
| Acceptance criteria | Given thành viên dự án, When mở, Then 200 kèm đủ thông tin. |

#### FR-TASK-04 Danh sách, tìm kiếm, lọc, phân trang, sắp xếp

| Hạng mục | Nội dung |
|---|---|
| Mục tiêu | Danh sách task server-side với đầy đủ filter |
| Luồng chính | 1. Gọi GET /tasks với filter: keyword, projectId, assigneeId, status, priority, type, startDateFrom/To, dueDateFrom/To, overdue, blocked, tagId, sort, page, size → 2. Trả page |
| Ngoại lệ | Sort field không whitelist: 400 |
| Validation | page ≥ 0, size 1–100; sort whitelist; ISO-8601 cho ngày |
| Phân quyền | ADMIN: tất cả; PM: dự án quản lý; MEMBER: dự án tham gia |
| Acceptance criteria | Given filter status=DONE, When tìm, Then chỉ trả DONE. Given page vượt tổng, Then page rỗng hoặc 400 theo quy ước. |

#### FR-TASK-05 Xóa mềm công việc

| Hạng mục | Nội dung |
|---|---|
| Actor | ADMIN, PM dự án |
| Luồng chính | 1. Xác nhận → 2. Xóa mềm (task con kèm theo hoặc chặn — *cần xác nhận*) |
| Ngoại lệ | Task đã xóa: 404 |
| Phân quyền | ADMIN, PM dự án |
| Audit log | Có |
| Acceptance criteria | Given task có con, When xóa, Then không xuất hiện trong danh sách; task con theo chính sách đã chốt. |

#### FR-TASK-06 Giao việc & nhận việc

| Hạng mục | Nội dung |
|---|---|
| Mục tiêu | Gán/chuyển người thực hiện; thành viên nhận việc được giao |
| Actor | ADMIN, PM dự án (giao); assignee (nhận) |
| Luồng chính | 1. PM chọn assignee → 2. Lưu → 3. Thông báo TASK_ASSIGNED cho người được giao |
| Luồng thay thế | Member từ chối/nhận việc (nếu chốt luồng accept) |
| Validation | Assignee ∈ project; không giao cho task đã xóa |
| Phân quyền | Giao: ADMIN, PM dự án; nhận: chính assignee |
| Audit log | Có — đổi người thực hiện |
| Acceptance criteria | 1. Given PM, When giao, Then assignee thấy task trong "Việc của tôi" + có notification. 2. Given assignee ngoài dự án, Then 400. |

#### FR-TASK-07 Chuyển trạng thái

| Hạng mục | Nội dung |
|---|---|
| Mục tiêu | Chuyển trạng thái theo luồng hợp lệ, ghi lịch sử |
| Luồng chính | TODO → IN_PROGRESS → REVIEW → DONE; bất kỳ → BLOCKED (kèm reason) / CANCELLED |
| Luồng thay thế | REVIEW trả lại IN_PROGRESS |
| Ngoại lệ | Chuyển không hợp lệ (VD DONE → TODO trực tiếp): 400 |
| Validation | Trạng thái đích hợp lệ theo state machine; DONE ⇒ progress 100 + actualCompletedAt |
| Phân quyền | ADMIN, PM dự án; assignee cho task của mình |
| Audit log | Có — kèm history (từ → đến, ai, khi nào) |
| Acceptance criteria | Given task REVIEW, When PM chuyển DONE, Then 200 + actualCompletedAt có giá trị. Given chuyển trái luồng, Then 400. |

#### FR-TASK-08 Cập nhật tiến độ

| Hạng mục | Nội dung |
|---|---|
| Mục tiêu | Cập nhật progress 0–100 |
| Luồng chính | 1. Nhập progress → 2. Validate → 3. Lưu |
| Luồng thay thế | Progress = 100: gợi ý chuyển DONE (rule BR-TASK-04) |
| Ngoại lệ | Progress ngoài 0–100: 400; DONE mà progress < 100: 400 |
| Validation | 0 ≤ progress ≤ 100 |
| Phân quyền | ADMIN, PM dự án, assignee |
| Audit log | Có (thay đổi đáng kể) |
| Acceptance criteria | Given progress 50, When cập nhật, Then 200. Given progress 150, Then 400. |

#### FR-TASK-09 Đánh dấu blocker

| Hạng mục | Nội dung |
|---|---|
| Mục tiêu | Đánh dấu/ gỡ blocker kèm lý do |
| Luồng chính | 1. Bật blocked + blockerReason (bắt buộc) → 2. Lưu |
| Ngoại lệ | BLOCKED không có reason: 400 |
| Phân quyền | ADMIN, PM dự án, assignee |
| Audit log | Có |
| Acceptance criteria | Given chuyển BLOCKED, When không có reason, Then 400. |

#### FR-TASK-10 Bình luận

| Hạng mục | Nội dung |
|---|---|
| Mục tiêu | Trao đổi trên task |
| Actor | ADMIN, thành viên dự án |
| Luồng chính | 1. Gửi comment → 2. Lưu + thông báo cho assignee/watchers |
| Ngoại lệ | Comment rỗng/≥ 2000 ký tự: 400 |
| Validation | Nội dung 1–2000 ký tự |
| Phân quyền | ADMIN, thành viên dự án |
| Audit log | Không (có createdBy trên comment) |
| Acceptance criteria | Given thành viên dự án, When comment, Then lưu + notification cho người liên quan. |

#### FR-TASK-11 File đính kèm

| Hạng mục | Nội dung |
|---|---|
| Mục tiêu | Đính kèm file (metadata + storage path, không lưu binary trong DB) |
| Luồng chính | 1. Upload file → 2. Lưu vào storage + tạo bản ghi attachment |
| Ngoại lệ | Quá giới hạn kích thước: 413; sai loại: 400 |
| Validation | ≤ 10MB/file (BR-TASK-11); whitelist mime type — *cần xác nhận* |
| Phân quyền | ADMIN, thành viên dự án |
| Audit log | Có cho upload/delete |
| Acceptance criteria | Given file hợp lệ, When upload, Then 201 + có thể tải lại. Given file 50MB, Then 413. |

#### FR-TASK-12 Tạo công việc con

| Hạng mục | Nội dung |
|---|---|
| Mục tiêu | Tạo subtask (cùng project, cấm vòng lặp) |
| Luồng chính | 1. Chọn parent → 2. Tạo task con |
| Ngoại lệ | Parent khác project: 400; vòng lặp cha-con: 400; parent là task đã xóa: 400 |
| Validation | parentTaskId.projectId == task.projectId; không tạo vòng lặp |
| Phân quyền | Như tạo task |
| Acceptance criteria | Given parent P, When tạo con, Then con có parent = P. Given parent ở project khác, Then 400. |

#### FR-TASK-13 Xem lịch sử thay đổi

| Hạng mục | Nội dung |
|---|---|
| Mục tiêu | Xem history thay đổi của task (status, assignee, progress...) |
| Actor | ADMIN, thành viên dự án |
| Luồng chính | 1. Mở tab Lịch sử → 2. Xem danh sách thay đổi theo thời gian |
| Phân quyền | ADMIN, thành viên dự án |
| Acceptance criteria | Given task đã sửa nhiều lần, When mở lịch sử, Then thấy đủ các thay đổi kèm người/thời gian. |

#### FR-TASK-14 Công việc của tôi

| Hạng mục | Nội dung |
|---|---|
| Mục tiêu | Danh sách task được giao cho tôi (assignee = me) |
| Luồng chính | 1. Gọi GET /tasks/my-tasks → 2. Trả page task assignee = me |
| Phân quyền | Chính người dùng |
| Acceptance criteria | Given user được giao 3 task, When gọi my-tasks, Then trả đúng 3 task. |

#### FR-TASK-15 Công việc hôm nay

| Hạng mục | Nội dung |
|---|---|
| Mục tiêu | Task hạn hôm nay (hoặc overdue + hôm nay) — theo quy ước: dueDate trong hôm nay |
| Luồng chính | 1. Gọi GET /tasks/today → 2. Trả task theo timezone người dùng |
| Acceptance criteria | Given task hạn hôm nay, When gọi today, Then có trong kết quả (theo timezone user). |

#### FR-TASK-16 Công việc quá hạn / sắp đến hạn

| Hạng mục | Nội dung |
|---|---|
| Mục tiêu | Liệt kê task overdue / sắp đến hạn (dueDate trong N ngày tới, mặc định 7) |
| Luồng chính | 1. Gọi GET /tasks/overdue (hoặc filter overdue=true) → 2. Trả kết quả |
| Phân quyền | Phạm vi theo project của người dùng |
| Acceptance criteria | Given task hạn qua rồi chưa DONE, When lọc overdue, Then có trong kết quả. |

#### FR-TASK-17 Xuất Excel

| Hạng mục | Nội dung |
|---|---|
| Mục tiêu | Xuất danh sách task hiện tại ra Excel |
| Luồng chính | 1. Bấm Export với filter hiện tại → 2. Trả file |
| Ngoại lệ | Số dòng vượt giới hạn (mặc định 10.000 — *cần xác nhận*): chặn export |
| Phân quyền | ADMIN, PM dự án; MEMBER nếu được cấu hình |
| Acceptance criteria | Given 100 task, When export, Then file Excel có đủ 100 dòng + header. |

### 4.5 Cuộc họp & Action Item

#### FR-MEET-01 Tạo/lên lịch cuộc họp

| Hạng mục | Nội dung |
|---|---|
| Mục tiêu | Lên lịch họp: thời gian, địa điểm/link, chủ trì, người tham gia, agenda |
| Actor | ADMIN, PM dự án |
| Luồng chính | 1. Nhập title/project/startTime/endTime/location hoặc meetingLink/chairperson/participants/agenda → 2. Validate → 3. Lưu → 4. Thông báo MEETING_INVITED cho người tham gia |
| Ngoại lệ | endTime ≤ startTime: 400; chủ trì/người tham gia không thuộc dự án: 400; tham gia trùng: 400 |
| Validation | endTime > startTime; chairperson ∈ project; participants unique, ∈ project; ít nhất location hoặc meetingLink |
| Phân quyền | ADMIN, PM dự án |
| Audit log | Có |
| Acceptance criteria | 1. Given dữ liệu hợp lệ, When tạo, Then 201 + người tham gia nhận notification. 2. Given endTime ≤ startTime, Then 400. |

#### FR-MEET-02 Cập nhật họp & người tham gia

| Hạng mục | Nội dung |
|---|---|
| Actor | ADMIN, PM dự án (hoặc chủ trì họp — *cần xác nhận*) |
| Luồng chính | 1. Sửa thông tin/người tham gia → 2. Validate → 3. Lưu |
| Ngoại lệ | Version cũ: 409 |
| Phân quyền | ADMIN, PM dự án |
| Audit log | Có |
| Acceptance criteria | Given version đúng, When sửa, Then 200. Given version cũ, Then 409. |

#### FR-MEET-03 Xem chi tiết họp (biên bản)

| Hạng mục | Nội dung |
|---|---|
| Actor | ADMIN, thành viên dự án |
| Luồng chính | 1. Mở họp → 2. Xem thông tin + agenda + content + conclusion + action items + attachments |
| Phân quyền | ADMIN, thành viên dự án |
| Acceptance criteria | Given thành viên dự án, When mở, Then thấy đủ nội dung biên bản. |

#### FR-MEET-04 Danh sách, tìm kiếm, lọc họp

| Hạng mục | Nội dung |
|---|---|
| Luồng chính | 1. Gọi danh sách với filter (projectId, status, fromTime, toTime, keyword) + phân trang |
| Phân quyền | ADMIN: tất cả; PM: dự án quản lý; MEMBER: dự án tham gia |
| Acceptance criteria | Given filter project, When tìm, Then chỉ trả họp của project đó. |

#### FR-MEET-05 Hoàn thành họp (khóa biên bản)

| Hạng mục | Nội dung |
|---|---|
| Mục tiêu | Chuyển họp → COMPLETED, cập nhật content/conclusion |
| Luồng chính | 1. Cập nhật biên bản → 2. Chuyển trạng thái COMPLETED |
| Ngoại lệ | Họp CANCELLED không chuyển được |
| Phân quyền | ADMIN, PM dự án, chủ trì |
| Audit log | Có |
| Acceptance criteria | Given họp SCHEDULED, When hoàn thành, Then status = COMPLETED. |

#### FR-MEET-06 Họp hôm nay

| Hạng mục | Nội dung |
|---|---|
| Mục tiêu | Danh sách họp hôm nay theo timezone người dùng |
| Acceptance criteria | Given họp hôm nay, When gọi, Then có trong kết quả. |

#### FR-MEET-07 Xóa họp

| Hạng mục | Nội dung |
|---|---|
| Actor | ADMIN, PM dự án |
| Luồng chính | 1. Xác nhận → 2. Xóa mềm; action item giữ nguyên (hoặc xóa kèm — *cần xác nhận*) |
| Audit log | Có |
| Acceptance criteria | Given họp đã xóa, When tìm mặc định, Then không xuất hiện. |

#### FR-AI-01 Tạo action item

| Hạng mục | Nội dung |
|---|---|
| Mục tiêu | Ghi nhận action item từ họp, gán người phụ trách, hạn |
| Actor | ADMIN, PM dự án |
| Luồng chính | 1. Chọn họp → 2. Nhập title/description/assignee/dueDate/priority → 3. Lưu → 4. Thông báo ACTION_ITEM_ASSIGNED |
| Ngoại lệ | assignee không thuộc project: 400 |
| Validation | meetingId/projectId/title/assigneeId bắt buộc; project của AI = project của meeting |
| Phân quyền | ADMIN, PM dự án |
| Audit log | Có |
| Acceptance criteria | Given PM, When tạo AI, Then lưu + notification cho assignee. |

#### FR-AI-02 Cập nhật action item

| Hạng mục | Nội dung |
|---|---|
| Actor | ADMIN, PM dự án, assignee |
| Luồng chính | 1. Cập nhật status/progress/nội dung → 2. Lưu |
| Phân quyền | ADMIN, PM dự án; assignee cập nhật status/progress |
| Acceptance criteria | Given assignee, When cập nhật progress, Then 200. |

#### FR-AI-03 Chuyển action item thành task

| Hạng mục | Nội dung |
|---|---|
| Mục tiêu | Tạo task từ AI (cùng project), liên kết 2 chiều, chống tạo trùng |
| Luồng chính | 1. Chọn AI chưa có linkedTask → 2. Tạo task (source = ACTION_ITEM) → 3. Gắn linkedTaskId |
| Ngoại lệ | AI đã có linkedTask: 409 |
| Phân quyền | ADMIN, PM dự án |
| Audit log | Có |
| Acceptance criteria | 1. Given AI chưa link, When chuyển, Then task được tạo + AI trỏ tới task. 2. Given AI đã link, When chuyển lại, Then 409. |

#### FR-AI-04 Theo dõi action item quá hạn

| Hạng mục | Nội dung |
|---|---|
| Mục tiêu | Danh sách AI chưa đóng, quá hạn (phục vụ dashboard & report) |
| Phân quyền | ADMIN, PM dự án |
| Acceptance criteria | Given AI hết hạn chưa DONE, When lọc, Then có trong danh sách quá hạn. |

### 4.6 Risk / Issue / Quyết định / Milestone

#### FR-RISK-01 Tạo risk

| Hạng mục | Nội dung |
|---|---|
| Mục tiêu | Nhận diện risk mới, tự sinh mã |
| Actor | ADMIN, PM dự án |
| Luồng chính | 1. Nhập title/description/probability/impact/owner/mitigationPlan/contingencyPlan/dueDate → 2. Tính/ chọn level → 3. Lưu |
| Ngoại lệ | owner ngoài project: 400; mã trùng: 409 (tự sinh lại) |
| Validation | projectId/title/ownerId/probability/impact bắt buộc; owner ∈ project |
| Phân quyền | ADMIN, PM dự án |
| Audit log | Có |
| Acceptance criteria | Given PM, When tạo risk, Then 201 + mã unique. |

#### FR-RISK-02 Cập nhật risk

| Hạng mục | Nội dung |
|---|---|
| Actor | ADMIN, PM dự án; owner cập nhật trạng thái — *cần xác nhận* |
| Ngoại lệ | Version cũ: 409 |
| Audit log | Có |
| Acceptance criteria | Given version đúng, When cập nhật, Then 200. Given version cũ, Then 409. |

#### FR-RISK-03 Danh sách, lọc, chi tiết risk

| Hạng mục | Nội dung |
|---|---|
| Luồng chính | 1. Lọc theo projectId/status/level/ownerId + phân trang → 2. Xem chi tiết |
| Phân quyền | ADMIN, PM dự án; MEMBER/VIEWER đọc được risk của dự án mình |
| Acceptance criteria | Given filter level=HIGH, When tìm, Then chỉ trả HIGH. |

#### FR-RISK-04 Xóa risk

| Hạng mục | Nội dung |
|---|---|
| Actor | ADMIN, PM dự án |
| Luồng chính | 1. Xác nhận → 2. Xóa mềm |
| Audit log | Có |

#### FR-RISK-05 Chuyển risk thành issue

| Hạng mục | Nội dung |
|---|---|
| Mục tiêu | Risk OCCURRED → tạo issue liên kết (chống trùng) |
| Luồng chính | 1. Risk chuyển OCCURRED → 2. Tạo issue (nếu chưa có) → 3. Liên kết risk↔issue |
| Ngoại lệ | Risk đã liên kết issue: 409 |
| Phân quyền | ADMIN, PM dự án |
| Audit log | Có |
| Acceptance criteria | Given risk OCCURRED, When chuyển, Then issue được tạo + liên kết. Given đã liên kết, Then 409. |

#### FR-ISS-01 Tạo issue

| Hạng mục | Nội dung |
|---|---|
| Actor | ADMIN, PM dự án |
| Luồng chính | 1. Nhập title/severity/owner/description → 2. Lưu (mã tự sinh) |
| Validation | projectId/title/ownerId/severity bắt buộc; owner ∈ project |
| Phân quyền | ADMIN, PM dự án |
| Audit log | Có |
| Acceptance criteria | Given PM, When tạo issue, Then 201 + mã unique. |

#### FR-ISS-02 Cập nhật issue

| Hạng mục | Nội dung |
|---|---|
| Luồng chính | 1. Cập nhật severity/rootCause/solution/status → 2. RESOLVED tự ghi resolvedAt → 3. Lưu |
| Ngoại lệ | RESOLVED không có solution — *cần xác nhận*; version cũ: 409 |
| Phân quyền | ADMIN, PM dự án; owner cập nhật trạng thái |
| Audit log | Có |
| Acceptance criteria | Given chuyển RESOLVED, When lưu, Then resolvedAt có giá trị. |

#### FR-ISS-03 Danh sách, lọc, chi tiết issue

| Hạng mục | Nội dung |
|---|---|
| Luồng chính | 1. Lọc theo projectId/status/severity/ownerId + phân trang → 2. Xem chi tiết |
| Phân quyền | ADMIN, PM dự án; MEMBER/VIEWER đọc được issue của dự án mình |

#### FR-ISS-04 Xóa issue

| Hạng mục | Nội dung |
|---|---|
| Actor | ADMIN, PM dự án |
| Luồng chính | 1. Xác nhận → 2. Xóa mềm |
| Audit log | Có |

#### FR-DEC-01 Ghi nhận quyết định

| Hạng mục | Nội dung |
|---|---|
| Mục tiêu | Ghi nhận quyết định quan trọng (v1) |
| Luồng chính | 1. Quyết định được ghi vào conclusion của họp hoặc notes của task → 2. Có audit/history |
| Phân quyền | Theo đối tượng chứa (họp/task) |
| Acceptance criteria | Given quyết định được ghi ở họp, When mở biên bản, Then thấy nội dung + người ghi. |

#### FR-MIL-01 Tạo milestone

| Hạng mục | Nội dung |
|---|---|
| Actor | ADMIN, PM dự án |
| Luồng chính | 1. Nhập name/plannedDate/description → 2. Lưu |
| Validation | projectId/name/plannedDate bắt buộc |
| Phân quyền | ADMIN, PM dự án |
| Audit log | Có |
| Acceptance criteria | Given PM, When tạo milestone, Then 201. |

#### FR-MIL-02 Cập nhật milestone

| Hạng mục | Nội dung |
|---|---|
| Luồng chính | 1. Cập nhật trạng thái/progress/actualDate → 2. COMPLETED ⇒ progress 100 (+ actualDate) |
| Ngoại lệ | COMPLETED mà progress < 100: 400; version cũ: 409 |
| Phân quyền | ADMIN, PM dự án |
| Audit log | Có |
| Acceptance criteria | Given chuyển COMPLETED, When progress < 100, Then 400. |

#### FR-MIL-03 Danh sách, lọc milestone

| Hạng mục | Nội dung |
|---|---|
| Luồng chính | 1. Lọc theo projectId/status + phân trang; sắp theo plannedDate |
| Phân quyền | ADMIN, thành viên dự án |
| Acceptance criteria | Given filter status=DELAYED, When tìm, Then chỉ trả DELAYED. |

#### FR-MIL-04 Xóa milestone

| Hạng mục | Nội dung |
|---|---|
| Actor | ADMIN, PM dự án |
| Luồng chính | 1. Xác nhận → 2. Xóa mềm |
| Audit log | Có |

### 4.7 Notification

#### FR-NOTIF-01 Danh sách thông báo & số chưa đọc

| Hạng mục | Nội dung |
|---|---|
| Mục tiêu | Xem thông báo của tôi (phân trang), kèm unreadCount |
| Luồng chính | 1. Gọi danh sách → 2. Trả page + unreadCount |
| Phân quyền | Chính người dùng |
| Acceptance criteria | Given 5 thông báo (2 chưa đọc), When gọi, Then unreadCount = 2. |

#### FR-NOTIF-02 Đánh dấu đã đọc

| Hạng mục | Nội dung |
|---|---|
| Luồng chính | 1. Đánh dấu 1 thông báo / tất cả đã đọc |
| Phân quyền | Chính người dùng |
| Acceptance criteria | Given đánh dấu đọc 1 thông báo, When gọi lại, Then unreadCount giảm. |

#### FR-NOTIF-03 Sinh thông báo tự động

| Hạng mục | Nội dung |
|---|---|
| Mục tiêu | Scheduled job sinh thông báo deadline/overdue, không trùng |
| Luồng chính | 1. Job chạy định kỳ → 2. Tìm task sắp đến hạn (N ngày) / quá hạn → 3. Sinh notification (dedupe theo task+type+ngày) |
| Ngoại lệ | Job chạy lại trong cùng ngày: không tạo trùng |
| Phân quyền | Hệ thống |
| Acceptance criteria | 1. Given task sắp hết hạn, When job chạy, Then có notification. 2. Given job chạy lần 2 cùng ngày, Then không tạo trùng. |

### 4.8 Report

#### FR-REP-01..05 Các báo cáo

| Hạng mục | FR-REP-01 Task theo trạng thái | FR-REP-02 Task theo người thực hiện | FR-REP-03 Task quá hạn | FR-REP-04 Tiến độ dự án | FR-REP-05 Risk & issue |
|---|---|---|---|---|---|
| Mục tiêu | Số task theo từng status | Số task theo assignee | Danh sách task quá hạn | Tiến độ theo dự án | Tổng hợp risk/issue mở |
| Actor | ADMIN, PM dự án | ADMIN, PM dự án | ADMIN, PM dự án | ADMIN, PM dự án | ADMIN, PM dự án |
| Input | projectId, from/to | projectId, from/to | projectId, from/to | projectId(s) | projectId |
| Output | Bảng/ biểu đồ đếm | Bảng/ biểu đồ đếm | Bảng chi tiết | Progress từng dự án | Bảng tổng hợp |
| Hậu điều kiện | Dữ liệu aggregate tại DB | Dữ liệu aggregate tại DB | Có giới hạn số dòng | Không N+1 | Không N+1 |
| Audit log | Không | Không | Không | Không | Không |
| Acceptance criteria | Đếm đúng theo dữ liệu | Đếm đúng theo assignee | Chỉ gồm task chưa DONE quá hạn | Tiến độ phản ánh task thực tế | Đếm đúng risk/issue mở |

#### FR-REP-06 Xuất CSV/Excel báo cáo

| Hạng mục | Nội dung |
|---|---|
| Luồng chính | 1. Chọn báo cáo + filter → 2. Export file |
| Ngoại lệ | Vượt giới hạn dòng (10.000): chặn, yêu cầu thu hẹp filter — *cần xác nhận* |
| Phân quyền | ADMIN, PM dự án |
| Acceptance criteria | Given báo cáo 500 dòng, When export, Then file có đủ dòng + header. |

### 4.9 Audit

#### FR-AUD-01 Xem nhật ký hoạt động

| Hạng mục | Nội dung |
|---|---|
| Mục tiêu | Admin xem audit log (filter theo user/action/thời gian) |
| Luồng chính | 1. Mở trang Audit → 2. Lọc → 3. Xem chi tiết (before/after JSON) |
| Phân quyền | ADMIN |
| Acceptance criteria | Given hành động đã thực hiện, When lọc, Then thấy đủ bản ghi kèm trace. |

## 5. Ma trận FR → Module

| Module | FR |
|---|---|
| auth | FR-AUTH-01..06 |
| user | FR-USER-01, FR-USER-02 |
| dashboard | FR-DASH-01 |
| project | FR-PROJ-01..07 |
| task | FR-TASK-01..17 |
| meeting | FR-MEET-01..07 |
| action-item | FR-AI-01..04 |
| risk | FR-RISK-01..05 |
| issue | FR-ISS-01..04 |
| decision | FR-DEC-01 |
| milestone | FR-MIL-01..04 |
| notification | FR-NOTIF-01..03 |
| report | FR-REP-01..06 |
| audit | FR-AUD-01 |
