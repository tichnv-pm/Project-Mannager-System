# Hướng dẫn sử dụng & nhập dữ liệu — PM Daily Work Management

> **Phạm vi tài liệu:** Hướng dẫn sử dụng từng chức năng và **số liệu cần nhập (dữ liệu đầu vào) cho từng màn hình**, kèm mô tả **mối liên kết dữ liệu giữa các chức năng** trong hệ thống.
> **Nguồn sự thật:** `docs/02-functional-requirements.md`, `docs/planning/02`, `docs/08-v1.2-e2e-software-management.md`, `docs/api/*`.
> **Tài khoản demo:** `admin / Admin@123`, `pm.minh / Pm@12345`, `member1 / Member@123`.
> **URL:** Frontend `http://localhost:4200`, Swagger `http://localhost:8080/swagger-ui.html`.

---

## MỤC LỤC

1. Nguyên tắc chung & thứ tự nhập liệu
2. Đăng nhập & tài khoản (Auth / Quản lý người dùng)
3. Dự án & Thành viên dự án
4. Công việc (Task) & Công việc của tôi
5. Cuộc họp & Action Item
6. Risk / Issue / Milestone
7. Thông báo & Nhật ký hoạt động
8. Báo cáo & Xuất liệu
9. Dashboard tổng quan
10. Project Planning (Kế hoạch) — WBS / Dependency / Lịch / Lập lịch & Găng / Resource / Version & Baseline / Change & Link / Template & Portfolio / Gantt
11. v1.2 — E2E: Sprint/Agile, Requirements Backlog, Wiki, Git Webhook, QA Testing, EVM Finance
12. Sơ đồ liên kết dữ liệu tổng thể

---

## 1. Nguyên tắc chung & thứ tự nhập dữ liệu

Hệ thống là **Modular Monolith** — dữ liệu giữa các chức năng **liên kết qua khóa ngoại (FK)**. Để nhập liệu ít lỗi nhất, hãy nhập theo **thứ tự phụ thuộc dữ liệu** sau (bậc trên phải tồn tại trước bậc dưới):

```
Users (ADMIN tạo tài khoản)
   │
   ▼
Projects (+ gán ProjectManager, thêm thành viên)
   │
   ├──► Project Planning: Plan → WBS → Dependency → Calendar → Scheduling/Critical → Resource
   │         └── (sau APPROVED) Version → Baseline → Change/Link → Portfolio
   │
   ├──► Tasks (Kanban thực thi) ⇄ liên kết WBS qua Plan Link
   ├──► Meetings → Action Items → (chuyển) Task
   ├──► Risks ⇄ Issues (khi OCCURRED) ⇄ Test Run (BUG)
   ├──► Milestones
   │
   └──► (v1.2) Sprint/Backlog, Wiki, QA, Git Webhook, EVM
   ▼
 Dashboard / Report / Notification / Audit (đầu ra — chỉ đọc)
```

**Quy ước chung:**

- **Mã (code)** như `PRJ001`, `PRJ001-TASK-000001`, `RISK-…`, `ISS-…` đều **tự sinh** — không cần nhập tay.
- **UUID**: mọi id PK là UUID do hệ thống sinh — giao diện thường dùng **dropdown thay vì nhập tay id**.
- **Bắt buộc (required)** được đánh dấu `*`.
- **Enum/giá trị chọn** phải nằm trong danh sách quy định (xem cột "Chọn một giá trị").
- **Version (optimistic lock)**: khi sửa bản ghi cũ đồng thời 2 người, ai lưu sau sẽ báo **409 Conflict** → tải lại dữ liệu mới rồi sửa lại.

---

## 2. Đăng nhập & tài khoản

### 2.1 Đăng nhập / Đăng xuất

**Cách dùng:**
1. Mở trang chủ → nhập **username + password**.
2. Đăng nhập thành công nhận **access token (ngắn hạn)** + **refresh token (dài hạn)**; hệ thống tự động làm mới token khi hết hạn.
3. Chọn **Đăng xuất** ở góc phải để kết thúc phiên (refresh token bị thu hồi).

**Dữ liệu nhập (màn hình Đăng nhập):**

| Trường | Bắt buộc | Dữ liệu mẫu | Ghi chú |
|---|---|---|---|
| Username | ✔ | `pm.minh` | Tài khoản ADMIN tạo, trạng thái ACTIVE mới đăng nhập được |
| Password | ✔ | `Pm@12345` | ≥ 8 ký tự, BCrypt |

> Không phải điền token — mọi xác thực tự động.

### 2.2 Quản lý tài khoản (ADMIN)

**Cách dùng:** Vào menu **Quản trị** → tab **Người dùng** → thêm/sửa/vô hiệu hóa/xóa mềm tài khoản; đặt mật khẩu mới cho user quên mật khẩu.

**Dữ liệu nhập (màn hình Tạo/Sửa User):**

| Trường | Bắt buộc | Dữ liệu mẫu | Ghi chú / Liên kết |
|---|---|---|---|
| Username | ✔ | `member2` | Unique, không phân biệt hoa thường |
| Email | ✔ | `member2@company.com` | Unique, đúng định dạng |
| Họ tên | ✔ | `Nguyễn Văn A` | Không bỏ trống |
| Vai trò hệ thống | ✔ | `PROJECT_MEMBER` | Lấy từ danh sách role; quyết định quyền toàn hệ thống |
| Trạng thái | ✔ | `ACTIVE` | `INACTIVE` thì không đăng nhập được |

> **Liên kết dữ liệu:** User sau khi tạo sẽ được chọn làm: **Thành viên dự án** (§3), **Assignee/Collaborator/Watcher** của Task (§4), **Người họp/Action Item** (§5), **Owner Risk/Issue** (§6), **Creator** Wiki/Test (v1.2).

### 2.3 Vai trò & Quyền (ADMIN)

**Dữ liệu nhập:** chọn vai trò → tích các nhóm quyền (vd nhóm `project:*`, `task:*`, `plan:*`, `financial:view`…).

> **Liên kết:** Quyền của vai trò quyết định màn hình/button hiển thị trong toàn bộ phần mềm. Quyền mới `plan:*` ở v1.1 phải gán cho role phù hợp trước khi PM dùng tab Kế hoạch.

---

## 3. Dự án & Thành viên dự án

### 3.1 Tạo dự án

**Cách dùng:** Menu **Dự án → Thêm mới** → điền thông tin → Lưu.

**Dữ liệu nhập (modal Tạo Dự án):**

| Trường | Bắt buộc | Dữ liệu mẫu | Ghi chú / Liên kết |
|---|---|---|---|
| Mã dự án | ✔ | `PRJ-WEBSITE` | Unique, không trùng |
| Tên dự án | ✔ | `Phát triển website bán hàng` | |
| Mô tả | | Nội dung mô tả | |
| Ngày bắt đầu | | `2026-09-01` | |
| Ngày kết thúc | | `2027-03-31` | Phải ≥ ngày bắt đầu |
| Khách hàng | | `Công ty XYZ` | |
| Quản lý dự án (PM) | ✔ | `pm.minh` | Tự động trở thành thành viên dự án |
| Trạng thái | ✔ | `ACTIVE` | `PLANNING/ACTIVE/ON_HOLD/COMPLETED/CANCELLED` |

> **Liên kết dữ liệu:** Mọi Task/Meeting/Milestone/Risk/Issue/Plan/Wiki… đều **trỏ về `projectId`**. Chỉ user thuộc dự án mới thao tác được dữ liệu của dự án.

### 3.2 Thành viên dự án

**Cách dùng:** Mở Chi tiết dự án → tab **Thành viên** → Thêm thành viên + chọn vai trò trong dự án.

**Dữ liệu nhập:**

| Trường | Bắt buộc | Ghi chú / Liên kết |
|---|---|---|
| Thành viên | ✔ | Chọn từ danh sách User |
| Vai trò trong dự án | ✔ | `PROJECT_MANAGER/TECH_LEAD/BUSINESS_ANALYST/DEVELOPER/TESTER/DEVOPS/MEMBER` |
| Loại bỏ? | | Không thêm trùng người |

> **Liên kết dữ liệu quan trọng:** **Bạn chỉ có thể gán Task / Action Item / Chairperson họp cho người thuộc dự án này.** Thành viên `PROJECT_MANAGER` của dự án có thể quản lý toàn bộ dữ liệu dự án.

### 3.3 Xóa mềm / Xem danh sách

- **Xóa mềm** không làm mất dữ liệu con (task/họp…), chỉ ẩn khỏi danh sách mặc định.
- Trang **Danh sách Dự án** có tìm kiếm theo từ khóa + lọc theo trạng thái + phân trang.

---

## 4. Công việc (Task) & Công việc của tôi

Task là trung tâm thực thi: có Kanban 6 cột, chi tiết với 5 tab (Mô tả / Bình luận / Đính kèm / Lịch sử / Task con), xuất Excel.

### 4.1 Tạo công việc

**Dữ liệu nhập (form Tạo Task):**

| Trường | Bắt buộc | Dữ liệu mẫu | Ghi chú / Liên kết |
|---|---|---|---|
| Tiêu đề | ✔ | `Xây dựng đăng nhập` | |
| Mô tả | | Mô tả chi tiết | |
| Dự án | ✔ | `PRJ-WEBSITE` | Task phải thuộc dự án |
| Task cha | | (chọn từ WBS/…) | Cùng dự án, chống vòng lặp cha-con |
| Người giao | ✔ | Tự điền = người tạo | |
| Người thực hiện | | Chọn thành viên | Tin phải **thuộc dự án** |
| Người phối hợp | | Nhiều thành viên | Thuộc dự án |
| Người theo dõi | | Nhiều thành viên | Nhận thông báo |
| Trạng thái | ✔ | `TODO` | `TODO/IN_PROGRESS/BLOCKED/REVIEW/DONE/CANCELLED` |
| Nghiêm cấm: Block | | `MEDIUM` | `LOW/MEDIUM/HIGH/CRITICAL` |
| Loại | ✔ | `TASK` | `FEATURE/BUG/IMPROVEMENT/TASK/OTHER` |
| Nguồn | | `ACTION_ITEM` | `MANUAL/MEETING/ACTION_ITEM/ISSUE/OTHER` |
| Ngày bắt đầu | | `2026-09-05` | |
| Ngày hạn | | `2026-09-12` | ≥ Ngày bắt đầu |
| Thời gian dự kiến (phút) | | `480` | Đơn vị giờ/ngày/tuần (v1.2) |
| Tiến độ % | ✔ | `0` | 0–100; `DONE` ⇒ 100 |
| Block + lý do | | | Bắt buộc nếu trạng thái `BLOCKED` |

> **Liên kết dữ liệu:**
> - Task gán đúng **Assignee** giúp task xuất hiện trong **"Công việc của tôi"** và bảng **báo cáo theo người thực hiện**.
> - **Nguồn `ACTION_ITEM`**: task được sinh ra khi chuyển Action Item (§5.4) → không cần tay.
> - Task (execution) được liên kết **WBS** qua **Plan Link** (§10.8) → khi task `DONE`, WBS tự cập nhật tiến độ (v1.1/v1.2).

### 4.2 Cập nhật Task / Kanban / Lịch Task

- **Kéo thả card** ở Kanban để đổi trạng thái theo đúng luồng: `TODO → IN_PROGRESS → REVIEW → DONE`; có thể `→ BLOCKED` (kèm lý do) hoặc `CANCELLED`.
- **Tiến độ**: kéo slide ≥ 0–100; đạt 100 gợi ý chuyển `DONE`.
- **Lịch sử**: ghi mọi thay đổi (status/assignee/progress…) kèm người + thời gian.

### 4.3 Bình luận, đính kèm, task con

| Chức năng | Dữ liệu nhập | Liên kết |
|---|---|---|
| Bình luận | Nội dung 1–2000 ký tự | Tự động báo cho người theo dõi/assignee |
| Đính kèm | File ≤ 10MB | Lưu metadata, không lưu binary trong DB |
| Task con | Tiêu đề + thông tin như task cha | Cùng dự án, tạo cây phụ |

### 4.4 Công việc của tôi / hôm nay / quá hạn

- Menu **"Việc của tôi"** → danh sách task `assignee = me`.
- **Công việc hôm nay / quá hạn** lọc theo `dueDate` + timezone người dùng — nguồn dữ liệu của dashboard.

---

## 5. Cuộc họp & Action Item

### 5.1 Lên lịch họp

**Dữ liệu nhập (form Họp):**

| Trường | Bắt buộc | Dữ liệu mẫu | Ghi chú / Liên kết |
|---|---|---|---|
| Tiêu đề | ✔ | `Họp Sprint Review tuần 3` | |
| Dự án | ✔ | `PRJ-WEBSITE` | |
| Bắt đầu / kết thúc | ✔ | `2026-09-15 09:00` / `10:00` | Kết thúc > bắt đầu |
| Địa điểm / Liên kết | Đ(ít nhất) | Phòng họp hoặc Google Meet link | Ít nhất 1 trong 2 |
| Chủ trì | ✔ | `pm.spqr` | Thuộc dự án |
| Người tham gia | ✔ | Nhiều nhất | Thuộc dự án, không trùng |
| Chương trình (agenda) | | Danh sách nội dung | |
| Trạng thái | ✔ | `SCHEDULED` | `SCHEDULED/IN_PROGRESS/COMPLETED/CANCELLED` |

> **Liên kết:** Người tham gia nhận thông báo `MEETING_INVITED`. Sau khi họp, điền **biên bản** (content/conclusion) và chuyển `COMPLETED` để khóa biên bản.

### 5.2 Mở họp hôm nay

Danh sách họp theo timezone của user → dùng để chuẩn bị nhắc việc trong dashboard.

### 5.3 Action Item

**Dữ liệu nhập:**

| Trường | Bắt buộc | Dữ liệu mẫu | Liên kết |
|---|---|---|---|
| Cuộc họp | ✔ | Chọn meeting | AI thuộc project = project của meeting |
| Tiêu đề | ✔ | `Soạn API spec đăng nhập` | |
| Người thực hiện | ✔ | Chọn thành viên | Nhận báo `ACTION_ITEM_ASSIGNED` |
| Ngày hạn | ✔ | `2026-09-20` | |
| Mức ưu tiên | | `HIGH` | |
| Trạng thái / Tiến độ | ✔ | `OPEN` / `0%` | `OPEN/IN_PROGRESS/DONE/CANCELLED` |

### 5.4 Chuyển Action Item → Task

- Chọn AI **chưa có** `linkedTaskId` → nút **Chuyển thành công việc**.
- Hệ thống tự tạo Task cùng project (nguồn `ACTION_ITEM`) và liên kết 2 chiều; sau đó Không tạo lại (409).

---

## 6. Risk / Issue / Milestone

### 6.1 Risk (rủi ro)

**Dữ liệu nhập:**

| Trường | Bắt buộc | Dữ liệu | Ghi chú / Liên kết |
|---|---|---|---|
| Dự án | ✔ | `PRJ-WEBSITE` | |
| Tiêu đề / Mô tả | ✔ / | | |
| Xác suất | ✔ | `MEDIUM` | `LOW/`MEDIUM/HIGH` |
| Ảnh hưởng | ✔ | `HIGH` | `LOW/MEDIUM/HIGH` |
| Mức (level) | ✔ | Tự tính/chọn | `LOW/MEDIUM/HIGH/CRITICAL` |
| Người sở hữu | ✔ | Chọn thành viên | |
| Phương án giảm / dự phòng | | Nội dung | |
| Trạng thái / Hạn | ✔ | `OPEN` | `OPEN/MONITORING/MITIGATED/OCCURRED/CLOSED` |

> **Liên kết quan trọng — Risk→Issue:** khi Risk chuyển `OCCURRED`, hệ thống **tự tạo Issue** và liên kết; không cho tạo trùng.

### 6.2 Issue (vấn đề)

**Dữ liệu nhập:** Dự án*, Tiêu đề/ mô tả, **Độ nghiêm**(`LOW/MEDIUM/HIGH/CRITICAL`)*, **Người sở hữu***, `rootCause`, `solution`, trạng thái `OPEN/ANALYZING/IN_PROGRESS/RESOLVED/CLOSED/REJECTED`, ngày hạn.

> Khi Issue `RESOLVED` hệ thống tự ghi `resolvedAt`. Issue có thể được **tự tạo** từ **Risk OCCURRED** hoặc từ **Test Run FAILED** (v1.2 §11.3).

### 6.3 Milestone (mốc)

**Dữ liệu nhập:** Dự án*, **Tên mốc***, mô tả, **Ngày kế hoạch***, trạng thái `NOT_STARTED/IN_PROGRESS/COMPLETED/DELAYED/CANCELLED`, **Tiến độ `0-100`**, ghi chú.

> **COMPLETED ⇒ tiến độ phải = 100** và tự ghi ngày thực tế. Milestone được dùng làm **mốc quan trọng** trong Portfolio và là nơi gắn **Detail Plan** (v1.1 §10.9 / v1.2).

---

## 7. Thông báo & Nhật ký hoạt động

### 7.1 Thông báo (in-app)

- **Chuông** ở header hiển thị badge số **chưa đọc** (`unreadCount`).
- Xem danh sách, **đọc 1 / đọc tất cả**.
- **Tự sinh** bởi job định kỳ khi Task sắp quá hạn / quá hạn (không trùng lặp trong ngày), báo người được giao, invit họp, action item, và cảnh **wiki được sửa / Test Run lỗi nặng / CPI < 0.85** (v1.2).

### 7.2 Nhật ký hoạt động (Audit)

- **ADMIN** mở menu **Quản trị → Nhật ký hoạt động**, lọc theo user / action / thời gian, xem trước + sau (JSON).
- Mọi tạo/sửa/xóa/đổi trạng thái quan trọng, ghi audit **tự động** — không cần nhập liệu.

---

## 8. Báo cáo & Dashboard

### 8.1 Báo cáo (ADMIN / PM)

Chọn loại + filter (projectId, from/to, assignee…) → **xuất CSV/Excel** (lọc thu hẹp nếu vượt 10.000 dòng):

| Báo cáo | Nội dung hiển thị |
|---|---|
| Task theo trạng thái | Đếm task theo status |
| Task theo người thực thi | Đếm theo assignee |
| Task quá hạn | Danh sách task chưa DONE, quá hạn |
| Tiến độ dự án | Tiến độ từng dự án |
| Risk & Issue | Tổng hợp loại đang mở |

> **Liên kết:** Dữ liệu báo cáo là đầu ra, lấy trực tiếp từ Task/Risk/Issue/Milestone đã nhập ở các phần trên.

### 8.2 Dashboard

Màn hình chính của PM — **chỉ đọc**, tự tổng hợp từ mọi chức năng:
- **13 nhóm chỉ số**: task tổng hôm nay / đang chạy / quá hạn / sắp đến hạn / bị chặn, họp hôm nay, action item đang mở, risk cao, issue mở, milestone tới.
- **Biểu đồ**: task theo status/priority; tiến độ dự án.
- **v1.2**: thêm gauge **CPI/SPI (EVM)**, **Sprint Burndown**, mật độ defect QA, lịch sử Wiki sửa đổi.

> Muốn dashboard "đẹp và có ý nghĩa", hãy **nhập đủ task có ngày hạn + thuộc thành viên**, đủ họp, milestones, risk/issue.

---

## 9. Project Planning (Kế hoạch) — v1.1

Phân hệ **Kế hoạch** quản lý kế hoạch yêu cầu toàn dự án (Master/Detail), bắt buộc nhập **theo thứ tự** vì dữ liệu tầng sau cần tầng trước.

### 9.1 Plan (Kế hoạch Master/Detail)

**Cách dùng:** Menu **Kế hoạch → Tạo kế hoạch (Master) / Tạo kế hoạch (Detail)**.

**Dữ liệu nhập (Plan):**

| Trường | Bắt buộc | Dữ liệu mẫu | Ghi chú / Liên kết |
|---|---|---|---|
| Tên / mã kế hoạch | ✔ | `KH-WEBSITE-MASTER` / `PRJ` | planCode unique theo dự án |
| Dự án | ✔ | `PRJ-WEBSITE` | Chủ quyền của kế hoạch |
| Loại kế hoạch | ✔ | `MASTER` | `MASTER/DETAIL/TEMPLATE_INSTANCE` |
| Plan cha (Master) | (nếu Detail) | Chọn Master plan | Detail phải thuộc 1 Master; cấm vòng lặp |
| Trạng thái | ✔ | `DRAFT` | `DRAFT→SUBMITTED→APPROVED→ACTIVE`; đạt ACTIVE phải qua APPROVED |
| Vòng đời từ template | | Chọn template | |

> **Liên kết:** Master roll-up từ nhiều Detail. Chỉ 1 Master/quyền trạng thái `ACTIVE` cho 1 dự án. **Baseline chỉ tạo khi `APPROVED`.**

### 9.2 WBS (cây công việc)

**Cách dùng:** Tab **WBS** trong kế hoạch → **Thêm task gốc / con / cùng cấp → Chỉnh sửa → Di chuyển (Indent/Outdent/Up/Down)**.

**Dữ liệu nhập (Plan Task):**

| Trường | Bắt buộc | Dữ liệu mẫu | Ghi chú / Liên kết |
|---|---|---|---|
| Tên | ✔ | `Phân tích yêu cầu` | |
| Loại task | ✔ | `PHASE/TASK/MILESTONE` | `PHASE`, `SUMMARY_TASK`, `WORK_PACKAGE`, `TASK`, `MILESTONE`, `EXTERNAL_TASK` |
| Cấp / WBSCode | Tự sinh | `1`, `1.1`, `1.1.2` | Auto renumber khi move |
| Thời lượng (duration) | Pr | `480` phút | Milestone duration = 0 |
| Công sức (effort) | | Giá trị | Đơn vị đa n (v1.2) |
| Ngày bắt đầu / kết thúc | **khi MANUAL** | | Task `AUTO` sẽ tính lại |
| Tiến độ % | ✔ | `0` | MILESTONE chỉ 0/100 |
| Owner | | Chọn thành viên | |
| ScheduleMode | ✔ | `AUTO` | `AUTO` (tự lịch) / `MANUAL` |
| Constraint + ngày | | `START_NO_EARLIER_THAN`… | |

> **Liên kết:** Summary (`isSummary`) tự động khi có con; task `MILESTONE` khi completa sẽ đẩy lên **Milestone** của dự án (v1.2 liên kết Master–Detail).

### 9.3 Dependency (Liên kết Task)

- Chọn **Task trước (predecessor) + Task sau (successor)** + **Kiểu liên kết** `FS/SS/FF/SF` + **Lag (phút)**.
- **Cấm** tự trỏ (self) và vòng lặp (cycle); tr= cần recalc.
- > **Liên kết:** Dependency là đầu vào của **Scheduling Essay** (§9.5) và **Critical Path** (§9.6).

### 9.4 Lịch làm việc (Calendar)

- Chọn **Lịch** cho tổ chức (`SYSTEM`) / dự án (`PROJECT`); đặt **Ngày làm việc** + **giờ** (vd Mon–Fri 08:00–17:00) và **giờ mỗi ngày**.
- **Ngoại lệ**: `NON_WORKING` (nghỉ lễ) / `WORKING` (ngày làm bù).
- **Kế thừa**: Lịch con thiếu thì dùng lịch tổ chức.

> **Liên kết:** Calendar quyết định kết quả **Scheduling** (loại trừ cuối tuần/nghỉ).

### 9.5 Scheduling & Critical Path

- Nút **Recalc** (`plan:schedule`) → engine forward pass tính start/finish cho task `AUTO` theo dependency + calendar + `lag`; trả **warnings** (xung đột lookup, ngày không phải ngày làm việc, lag âm…).
- Tab **Găng (Critical Path)**: hiện ES/EF/LS/LF, **Total Float / Free Float**, đánh dấu task **critical** (TF ≤ ngưỡng), chuỗi dọc theo dependency.

### 9.6 Resource & Workload

- Gán **Người/TEAM/Role/External** cho task với **% allocation** + **effort/actual**.
- Xem **Workload theo Ngày/Tuần/Tháng** (demand vs capacity, **over-allocation** > THÔNG báo cảnh báo).
- **Overview** tổng hợp nhiều dự án.

> **Liên kết:** Resource dùng **User/Team** — nên có sẵn **thành viên dự án**. Capacity tăng **Resource Capacities** (vd 480 phút/ngày).

### 9.7 Version & Baseline

- **Tạo Version**: chụp snapshot toàn bộ kế hoạch; tăng `versionNo` tự động; đặt **1 phiên bản active**.
- **Diff Version**: so sánh vN với vN+1 (thêm/xóa task, đổi ngày/duration/effort/progress).
- **Baseline**: chỉ tạo khi Plan `APPROVED`; mỗi baseline có `baselineNum` **không ghi đè**; **Variance** so sánh hiện tại vs baseline (lệch start/finish, duration, công sức, **delay-days**, task chậm mốc).

### 9.8 Change & Link

- **Change Request**: sau khi plan `APPROVED`, mọi đổi ảnh hưởng lịch cần **tạo request → duyệt (dual-approve ≥ ngưỡng) → apply/reject**; ghi vào change history (JSON before/after).
- **Plan Link**: liên kết WBS ↔ **Execution Task / Issue / Risk / Meeting / Milestone** với loại `RELATED_TO/IMPLEMENTS/BLOCKED_BY/…`.

> **Liên kết chính:** Khi lập trình **tương quan** (execution task) `DONE` → WBS cập nhật tiến độ 100%, master roll-up (v1.1/v1.2).

### 9.9 Template & Portfolio

- **Template**: dùng 8 mẫu chuẩn (`FULL_LIFECYCLE/AGILE_SCRUM/…`) để **tạo Plan từ phạm vi**, bỏ phase không dùng.
- **Portfolio**: timeline đa dự án, tổng hợp tiến độ theo Master, phát hiện **chậm tiến độ** và **over-allocation chéo dự án**, mốc quan trọng.

### 9.10 Gantt

- Tab **Gantt**: lưới WBS bên trái + timeline SVG bên phải; zoom Ngày/Tuần/Tháng; đường hôm nay; critical đỏ, baseline xám, milestone kim cương, mũi tên dependency, chip resource.
- **Chỉ xem kết quả** — không nhập liệu tại đây.

---

## 10. X — phân hệ v1.2 (E2E Software Management)

### 10.1 Sprint / Agile (Sprint Timebox)

- Tạo **Sprint** với ngày bắt đầu/kết thúc (timebox).
- **Planning Task** liên kết Runtime: nếu finish giả định *vượt* kernel`Sprint` → cảnh báo `WARNING_OUT_OF_SPRINT_BOUNDARY`.
- **Cross-sprint dependency**: Task Sprint 2 phụ thuộc Sprint 1 **màu hợp lệ**; ngược lại (Sprint 1 phụ thuộc Sprint 2) → **lỗi 400**.
- **Sprint Backlog**: Action Item đã duyệt chuyển thành Task được nạp vào **Backlog Sprint** hiện hành.

**Dữ liệu nhập:** Tên/ngày Sprint, thành viên trong Sprint, chọn task backlog.

### 10.2 Requirements Backlog (Epic/User Story)

**Dữ liệu nhập:** Epic / User Story (mô tả, ưu tiên, người phụ trách).

> **Liên kết:** Epic/Story **định hình WBS** — User Story được liên kết tới `plan_task`, hệ thống tinh **tiến độ thực tế của yêu cầu nghiệp vụ** qua roll-up tự động.

### 10.3 Project Wiki

- **Khởi tạo** Wiki dự án → hệ thống tự tạo 5 mẫu chuẩn (Getting Started, Architecture, Coding Guidelines, QA, Operations).
- Duyệt **Thêm/Edit trang con**, mỗi `version` + **lịch sử sửa đổi** (who/when/content).

**Dữ liệu nhập:** Tiêu đề + Nội dung (định dạng text/placeholder); chọn Cạnh cha (nếu có).

### 10.4 Git Webhook

- Admin cấu hình **Webhook Secret**; hệ thống nhận payload Git (GitHub/GitLab) xác thực **HMAC-SHA256**.
- **Regex]** trích xuất mã task từ message commit (VD `[PRJ001-TASK-000123] …`).
- Khi **PR merged** → Task liên quan chuyển `DONE`, và Test Case loại READY_TO_TEST.

**Dữ liệu nhập:** Secret token + Webhook URL; nội dung commit chứa mã task đúng định dạng.

### 10.5 QA Testing (Test Case / Test Run)

**Dữ liệu nhập:**
- **Test Case**: tên, bước (steps, expected), kết quả mong đợi, trạng thái.
- **Test Run**: thực thi trên các Test Case; mỗi Test Step đánh **PASS/FAIL**.

> **Liên kết tự động:** Khi Test Step **FAILED** → hệ thống **tự tạo Issue loại `BUG`** (V1.0), liên kết Test Case, giao lại Dev; khi `fix` xong, Dev liên kết lại.

### 10.6 EVM Finance

- Dữ liệu cần: **Budget at Completion** theo task, **Planned Progress (%)**, **Actual Progress (%)**, **giờ thực tế** + **đơn giá giờ** (`hourly_rate` — **mã hóa AES-GCM**, chỉ PM/ADMIN có `financial:view` đọc được).
- Hệ thống tính **PV/EV/AC, CV=EV-AC, SV=EV-PV, CPI=EV/AC, SPI=EV/PV**.
- **CPI < 0.85** → tự động gửi **thông báo vượt ngân sách nghiêm trọng**.

**Dữ liệu nhập:** Budget, cost rate cho thành viên, actual hours của dev, tiến độ thực tế.

### 10.7 Liên kết nhiều đơn vị (Duration/Effort unit)

- Task / Plan Task đặt **estimateUnit/durationUnit/effortUnit**: `MINUTE/HOUR/DAY/WEEK/MONTH` (quy đổi: ngày=8h, tuần=5ngày=40h=2400min, tháng=20ngày).

### 10.8 Milestone-driven Detail Planning (Master–Detail v1.2)

- Detail Plan có **`parent_milestone_task_id`** trỏ **task `MILESTONE`** của Master Plan.
- Khi Detail kết thúc Recalculate (**Event-driven**), hệ thống **tự đồng i** Finish & PercentComplete lên dòng Milestone Master.

---

## 11. Sơ đồ liên kết dữ liệu tổng thể

```
Users (ADMIN tạo, gán role/quyền)
   │
Projecs ────────────────► ProjectMembers (User + vai trò)
   │
   ├─► Tasks ──(source MANUAL / ACTION_ITEM / ISSUE)──► assignee/collaborator/watcher = User
   │        └─ Kanban task DONE ──► (Plan Link) ──► plan_task WBS % = 100
   │
   ├─► Meetings ──► Action Items ──converts──► Task (source=ACTION_ITEM)
   │
   ├─► Risks ──(OCCURRED)──► Issue
   │        ▲                  │
   │        └──────────────────┘ (chuyển issue↔risk)
   │        a: Test Run FAILED ─► Issue (Bug) [v1.2]
   │
   ├─► Milestones ──(v1.2)──► Master Plan MILESTONE (parent_milestone)
   │
   ├─► Project Planning [v1.1]:
   │      Plan(Master/Detail) → WBS → Dependency → Calendar
   │         → Scheduling → Critical Path → Resource → Version/Baseline
   │         → Change/Link ─(Plan Link)──► Tasks/Risk/Issue/Milestone
   │         → Template → Portfolio → Gantt
   │
   ├─► [v1.2]: Sprint/Backlog ─► Tasks; Wiki; Git Webhook; QA Test; EVM
   │
   ▼
 Dashboard ── báo cáo ── Audit ── Notification ── Gantt/Portfolio/Burndown (chỉ đoc)
```

**Đọc theo chiều mũi tên:** dữ liệu ở **Bậc thấp phải nhập trước** (User → Project/Member → Plan → WBS → Task thực thi…), mọi **màn hình Dashboard/Báo cáo/Tổng hợp là đầu ra** tổng hợp từ các bậc đó.

---

## 12. Memo kiểm tra nhanh (nếu gặp lỗi)

| Lỗi | Nguyên nhân | Xử lý |
|---|---|---|
| `Assignive ngoài dự án` 400 | Chọn người chưa là thành viên dự án | Thêm người vào **Thành viên dự án** (§3.2) trước |
| `409 Conflict` khi sửa | Version (optimistic lock) đã cũ | Tải lại dữ liệu mới, áp lại thay đổi |
| `Vượt ngưỡng effort` | Export > row limit | Thu hẹp filter |
| Tài khoản không vào được | `INACTIVE` hoặc sai mật khẩu | ADMIN kiểm tra **Quản lý tài khoản/reset mt** |
| Không mở được tab Kế hoạch | Thiếu quyền `plan:*` | ADMIN gán quyền cho role |
| CPI < 0.85 | EVM vượt ngân sách | Nhập chính `hourly_rate`, budget, actual hours |
| Khó xuất hiện cho khác | Chọn người không đúng trong dropdown thành viên | Bổ sung thành viên dự án |
| Recalc không ra | Nconscien task `MANUAL` | Đổi `AUTO` hoặc nhập thủ công |

---
*Tài liệu này tóm tắt từ source trong `docs/`. Mọi chi tiết định dạng/field trở về `docs/api/*` và `docs/planning/*`.*