# Planning 02 — Yêu cầu chức năng Project Planning (Functional Requirements)

> Dự án: PM Daily Work Management — phân hệ PROJECT PLANNING
> Quy ước ID: `PLN-FR-<NHÓM>-<NN>`. Nhóm: `PLAN, VERSION, WBS, DEP, CAL, SCHED, CP, RES, BASE, LINK, TPL, PORT, MASTER, DETAIL`.
> Trạng thái: Draft — nguồn chính từ Prompt Project Planning Requirement.
> Tài liệu liên quan: `docs/planning/01`, `docs/planning/03`, `docs/planning/05`, `docs/02-functional-requirements.md`.

## 1. Danh mục & enum dùng chung

### 1.1 PlanType

| Giá trị | Ý nghĩa |
|---|---|
| `MASTER` | Kế hoạch tổng thể của một dự án — tổng hợp từ các Detail Plan |
| `DETAIL` | Kế hoạch chi tiết thuộc Master Plan (backed: phase/module/sprint/release/work-package/team/vendor) |
| `TEMPLATE_INSTANCE` | Bản sao hoạt động của một plan template |

### 1.2 PlanStatus (vòng đời kế hoạch)

`DRAFT` → `SUBMITTED` → `APPROVED` → `ACTIVE` → (`ON_HOLD` | `COMPLETED`) → `ARCHIVED` | `CANCELLED`.

| Giá trị | Ý nghĩa |
|---|---|
| `DRAFT` | Đang soạn (mặc định) |
| `SUBMITTED` | Đã gửi để chờ duyệt |
| `APPROVED` | Đã được duyệt — điều kiện để tạo baseline |
| `ACTIVE` | Đang là kế hoạch hiệu lực (duy nhất 1/1 master) |
| `ON_HOLD` | Tạm dừng kế hoạch |
| `COMPLETED` | Đã hoàn thành toàn bộ |
| `CANCELLED` | Hủy bỏ |
| `ARCHIVED` | Lưu trữ |

### 1.3 WBS TaskType

`PHASE`, `SUMMARY_TASK`, `WORK_PACKAGE`, `TASK`, `MILESTONE`, `EXTERNAL_TASK`.

### 1.4 TaskStatus (planning task)

`NOT_STARTED`, `IN_PROGRESS`, `COMPLETED`, `DELAYED`, `ON_HOLD`, `CANCELLED`.

### 1.5 DependencyType

`FS` (Finish-to-Start), `SS` (Start-to-Start), `FF` (Finish-to-Finish), `SF` (Start-to-Finish).

### 1.6 ScheduleMode

`AUTO` (lập lịch tự động), `MANUAL` (giữ nguyên).

### 1.7 ConstraintType

`AS_SOON_AS_POSSIBLE`, `AS_LATE_AS_POSSIBLE`, `START_NO_EARLIER_THAN`, `START_NO_LATER_THAN`, `FINISH_NO_EARLIER_THAN`, `FINISH_NO_LATER_THAN`, `MUST_START_ON`, `MUST_FINISH_ON`.

### 1.8 ResourceType (plan_task_resources.resource_type)

`USER`, `TEAM`, `GENERIC_ROLE`, `EXTERNAL`.

### 1.9 LinkedEntityType (plan_links)

`EXECUTION_TASK`, `ISSUE`, `RISK`, `MEETING`, `ACTION_ITEM`, `MILESTONE`, `ATTACHMENT`, `DOCUMENT`, `RELEASE`, `DEPLOYMENT`, `CHANGE_REQUEST`.

### 1.10 LinkType

`RELATED_TO`, `IMPLEMENTS`, `BLOCKED_BY`, `CAUSED_BY`, `DISCUSSED_IN`, `PRODUCES`, `DEPENDS_ON`, `RESOLVES`, `AFFECTS_SCHEDULE`.

### 1.11 PlanTemplateType

`FULL_LIFECYCLE`, `WATERFALL`, `AGILE_SCRUM`, `HYBRID`, `MAINTENANCE`, `MIGRATION`, `PRODUCT_DEV`, `OUTSOURCING`.
> Mapping 8 loại template → 17 phase chuẩn: xem `docs/planning/12` mục Template.

## 2. Yêu cầu chức năng (PLN-FR)

### 2.1 Project Plan & Master/Detail (PLN-FR-PLAN-*)

| ID | Yêu cầu |
|---|---|
| PLN-FR-PLAN-01 | Tạo `project_plan` thuộc một project; bắt buộc `planType` (MASTER/DETAIL), `planCode` unique theo project. |
| PLN-FR-PLAN-02 | Detail Plan `parentPlanId` trỏ Master Plan; một Master có nhiều Detail; Detail không phải là cha của Master (vòng lặp cấm). |
| PLN-FR-PLAN-03 | Master Plan roll-up: start min / finish max / effort tổng / tiến độ từ các Detail Plan. |
| PLN-FR-PLAN-04 | Tối đa một Plan `isActive` khi status = `ACTIVE` cho một project (unique partial). |
| PLN-FR-PLAN-05 | CRUD plan cơ bản: danh sách lọc theo projectId/status/planType/keyword + phân trang `page/size/sort`. |
| PLN-FR-PLAN-06 | Trạng thái hỗ trợ luồng DRAFT → SUBMITTED → APPROVED → ACTIVE; NULL cấm bỏ qua giai đoạn APPROVED để đạt ACTIVE; ON_HOLD/CANCELLED/ARCHIVED tùy người quyền. |
| PLN-FR-PLAN-07 | Xóa mềm plan (không xóa nếu còn Detail (master) hoặc plan task còn con — theo quy tắc xác nhận). |
| PLN-FR-PLAN-08 | `plannedStart/plannedFinish` của plan được tổng hợp từ WBS khi roll-up; `actualStart/actualFinish/progress` tương ứng. |

### 2.2 Plan Version (PLN-FR-VERSION-*)

| ID | Yêu cầu |
|---|---|
| PLN-FR-VERSION-01 | Mỗi plan có nhiều `plan_versions` (số phiên bản tăng dần, `versionNo`). |
| PLN-FR-VERSION-02 | Khi tạo phiên bản mới: chụp snapshot dữ liệu plan tree được tham chiếu (mutable trong phiên bản hiện tại, không đụng bản trước). |
| PLN-FR-VERSION-03 | Xác lập 1 phiên bản và là `activeVersionId` của plan; các phiên bản khác giữ nguyên (history). |
| PLN-FR-VERSION-04 | Chỉ tạo baseline từ một phiên bản `APPROVED` (có quan hệ với baseline). |
| PLN-FR-VERSION-05 | Version trước khi có baseline không bị ghi đè; dữ liệu thay đổi sau approve ghi vào change history. |

### 2.3 WBS & Planning Task (PLN-FR-WBS-*)

| ID | Yêu cầu |
|---|---|
| PLN-FR-WBS-01 | Tạo/tree plan tasks theo cấp (parentId), `outlineLevel`, `sequenceNumber`, `wbsCode` tự sinh theo quy ước (VD `1`, `1.1`, `1.1.2`). |
| PLN-FR-WBS-02 | Thêm task cùng cấp / thêm task con; indent/outdent; move up/down; move đến parent khác (trong cùng plan). |
| PLN-FR-WBS-03 | Expand/collapse cho summary trong UI (dữ liệu lưu cờ `expanded` phía client, không cần server). |
| PLN-FR-WBS-04 | Cấm cha–con vòng lặp (task không thể thành cha của chính mình hoặc hậu duệ của mình) — kiểm tra toàn tuyến cả cây. |
| PLN-FR-WBS-05 | Không cho xóa summary task còn con trừ khi user xác nhận xóa cả cây (giao diện alert + server xác nhận cờ dịch). |
| PLN-FR-WBS-06 | `wbsCode` tự động khi thêm/đọc nếu chưa có; renumber khi move/indent/outdent. |
| PLN-FR-WBS-07 | Cập nhật trường planning task: name/description/taskType/status/priority/constraintType/constraintDate/scheduleMode/duration/effort/start-finish/actual/owner. |
| PLN-FR-WBS-08 | Milestone task: `isMilestone` đúng kiểu; duration=0; `percentComplete` 0/100. |
| PLN-FR-WBS-09 | `isSummary` tự động = có con (không chỉnh tay). |

### 2.4 Dependency (PLN-FR-DEP-*)

| ID | Yêu cầu |
|---|---|
| PLN-FR-DEP-01 | CRUD dependency: predecessor/successor cùng plan, `dependencyType` (FS/SS/FF/SF), `lagMinutes` (>=0 ở bản đầu, âm chờ chốt). |
| PLN-FR-DEP-02 | Chặn predecessor == successor (tự trỏ). |
| PLN-FR-DEP-03 | Chặn tạo cycle dependency (trong phạm vi một plan). |
| PLN-FR-DEP-04 | Nhiều predecessor / nhiều successor cho một task (M:N). |
| PLN-FR-DEP-05 | Cross-project dependency: **không** cho phép tạo ở v1; để roadmap (ghi audit/throw). |
| PLN-FR-DEP-06 | Xóa/misc dependency thay đổi kích hoạt recalculation schedule (nếu AUTO). |

### 2.5 Working Calendar (PLN-FR-CAL-*)

| ID | Yêu cầu |
|---|---|
| PLN-FR-CAL-01 | Lịch tổ chức (loại SYSTEM) và lịch dự án (loại PROJECT) — plan `calendarId` trỏ về. |
| PLN-FR-CAL-02 | Cấu hình working days (VD Mon–Fri 08:00–17:00, hoặc Mon–Sat 2–6) và số giờ/ngày. |
| PLN-FR-CAL-03 | Holiday / non-working day (plan_calendar_exceptions, type NON_WORKING). |
| PLN-FR-CAL-04 | Special working date (type WORKING — đổi ngày thường thành làm việc). |
| PLN-FR-CAL-05 | Kế thừa: project calendar không đủ → fallback về tổ chức. |

### 2.6 Scheduling Engine (PLN-FR-SCHED-*)

| ID | Yêu cầu |
|---|---|
| PLN-FR-SCHED-01 | Task AUTO: tính start & finish theo (predecessors, lag, duration, working day calendar), loại trừ holiday/nghỉ. |
| PLN-FR-SCHED-02 | Task MANUAL: không tự động đổi ngày. |
| PLN-FR-SCHED-03 | Rêcal sau khi dependency/duration/calendar/constraint/start/finish thay đổi (automated); chỉ tác động downstream của đối tượng liên quan. |
| PLN-FR-SCHED-04 | Summary task: plannedStart = min(child start), plannedFinish = max(child finish) — tính lại sau roll-out. |
| PLN-FR-SCHED-05 | Constraint: kiểm tra xung đột constraint vs dependency → trả cảnh báo (không ép ép). |
| PLN-FR-SCHED-06 | Gợi ý: kết quả lập lịch trả `warnings[]` (ví dụ date ép buộc, non-working overlap). |

### 2.7 Critical Path (PLN-FR-CP-*)

| ID | Yêu cầu |
|---|---|
| PLN-FR-CP-01 | Tính ES/EF (forward pass) và LS/LF (backward pass) cho plan (dựa dependency + duration + calendar). |
| PLN-FR-CP-02 | Tính **Total Float** & **Free Float** cho từng task; `isCritical` = TF <= threshold (cấu hình, mặc định 0). |
| PLN-FR-CP-03 | Liệt kê chuỗi critical path (từ start → finish, node TF=0). |
| PLN-FR-CP-04 | Tính lại toàn bộ critical path khi: duration, dependency, calendar, constraint, start/finish thay đổi. |
| PLN-FR-CP-05 | Trả `CriticalPathResult` kèm tổng duration dự tính để hiển thị Gantt. |

### 2.8 Resource Planning (PLN-FR-RES-*)

| ID | Yêu cầu |
|---|---|
| PLN-FR-RES-01 | Gán resource vào planning task: `resourceType` (USER/TEAM/GENERIC_ROLE/EXTERNAL), `resourceId`, `genericRole`, `allocationPercent`, `plannedEffort` (h), `actualEffort`, assignment start/finish. |
| PLN-FR-RES-02 | Một task có nhiều assignment (M:N user/team/role). |
| PLN-FR-RES-03 | Workload theo ngày/tuần/tháng cho từng resource/team, aggregate trên nhiều task và nhiều dự án. |
| PLN-FR-RES-04 | Trữ capacity của resource (`resource_capacities`): tổng giờ/ngày, tuần; kế hoạch tổng capacity. |
| PLN-FR-RES-05 | Phát hiện **over-allocation**: tổng allocation > capacity trong kỳ → warning (danh sách + indicator). |
| PLN-FR-RES-06 | Filter workload theo resource/team/project/thời gian. |
| PLN-FR-RES-07 | Không cho resource leveling (v1) — chỉ cảnh báo. |
| PLN-FR-RES-08 | Xem workload theo nhiều dự án (tổng hợp từ các plan phân phối). |

### 2.9 Baseline & Version (PLN-FR-BASE-*)

| ID | Yêu cầu |
|---|---|
| PLN-FR-BASE-01 | Tạo baseline: chỉ khi plan status APPROVED (trả lỗi nếu không). |
| PLN-FR-BASE-02 | Baseline snapshot: plannedStart/finish/duration/effort/progress của từng task, resource allocation, milestone dates. |
| PLN-FR-BASE-03 | Nhiều baseline: `baselineNum` tăng (0,1,2…) — **không ghi đè** baseline cũ. |
| PLN-FR-BASE-04 | So sánh variance: Current vs Baseline start/finish, duration, effort, milestone date, delay-days. |
| PLN-FR-BASE-05 | Xóa baseline: chỉ xóa bình thường (giữ lịch sử) — không sửa content đã snapshot. |

### 2.10 Change History (PLN-FR-CHG-*)

| ID | Yêu cầu |
|---|---|
| PLN-FR-CHG-01 | Sau khi plan APPROVED: mọi đổi (task dates, dependencies, resource, cancel/….) bắt buộc ghi `plan_change_histories`. |
| PLN-FR-CHG-02 | Mỗi bản ghi: `before_data`/`after_data` (JSON), `reason`, `requestedBy`, `approvedBy`, `affectedTaskIds`, schedule/resource impact summary. |
| PLN-FR-CHG-03 | PM (hoặc ADMIN) phải xác nhận (approve/reject) change ảnh hưởng lịch do Issue/Risk/ExecutionTask gây ra. |
| PLN-FR-CHG-04 | Change request workflow: tạo → duyệt → apply hoặc reject; có audit log. |
| PLN-FR-CHG-05 | Baseline không thay đổi theo change history (immutable). |

### 2.11 Link (PLN-FR-LINK-*)

| ID | Yêu cầu |
|---|---|
| PLN-FR-LINK-01 | Tạo/lập liên kết planning task với entity ngoài (EXECUTION_TASK, ISSUE, RISK, MEETING, ACTION_ITEM, MILESTONE) qua `plan_links` (bảng riêng, không cột danh sách). |
| PLN-FR-LINK-02 | `linkType` phân loại (RELATED_TO/IMPLEMENTS/BLOCKED_BY/…) — validate theo cặp hợp lệ. |
| PLN-FR-LINK-03 | Một Execution Task chỉ có tối đa **1** planning task chính; có thể có nhiều link phụ (linkType khác). |
| PLN-FR-LINK-04 | Actual effort/progress của planning task có thể tổng hợp từ Execution (roll-up rule cấu hình) — không tự ghi nếu Execution chưa đóng. |
| PLN-FR-LINK-05 | Không tự hoàn thành planning task khi vẫn còn execution chưa DONE. |
| PLN-FR-LINK-06 | Issue liên kết BLOCKED_BY/AFFECTS_SCHEDULE; Risk — khi OCCURRED cho tạo Issue và giữ link (không mất). |
| PLN-FR-LINK-07 | Action Item → Planning task: tạo mới task (chuyển đổi) theo quy (giống UC-007); 1 AI → tối đa 1 planning task (unique). |
| PLN-FR-LINK-08 | Milestone dùng liên kết chéo MT (không phủ định planning). |
| PLN-FR-LINK-09 | Attachment hoặc file của plan: gắn vào plan task (link ATTACHMENT/DOCUMENT) — nếu không dùng bảng chỗ riêng. |
| PLN-FR-LINK-10 | Thay đổi Issue/Risk/Execution → **không** tự đổi lịch: luôn tạo change suggestion cần PM approve. |

### 2.12 Template (PLN-FR-TPL-*)

| ID | Yêu cầu |
|---|---|
| PLN-FR-TPL-01 | CRUD plan template (phase/structure, wbs mẫu, task mẫu, milestone, dependency, generic role, default duration, deliverable, acceptance criteria). |
| PLN-FR-TPL-02 | Tạo plan từ template (tải template cây → plan tree) với option bỏ phase không áp dụng. |
| PLN-FR-TPL-03 | Clone template; Sửa template; version template (`templateVersionNo`). |
| PLN-FR-TPL-04 | 8 template chuẩn (FULL_LIFECYCLE, WATERFALL, AGILE_SCRUM, HYBRID, MAINTENANCE, MIGRATION, PRODUCT_DEV, OUTSOURCING) — seed dữ liệu mặc định trong local profile. |
| PLN-FR-TPL-05 | Khi tạo từ template: giữ milestone mặc định (list chuẩn trong `docs/planning/12`). |

### 2.13 Portfolio (PLN-FR-PORT-*)

| ID | Yêu cầu |
|---|---|
| PLN-FR-PORT-01 | Portfolio là view tổng hợp (không chính data): nhiều project/plan trên 1 màn hình. |
| PLN-FR-PORT-02 | Timeline nhiều dự án trên cùng timeline; tổng hợp progress theo nhánh master. |
| PLN-FR-PORT-03 | Theo dõi milestone quan trọng (các milestone chính), dự án chậm tiến độ (delay > threshold). |
| PLN-FR-PORT-04 | Phát hiện over-allocation resource chéo project (tổng allocation của một user/team > 100% cùng kỳ). |
| PLN-FR-PORT-05 | Lọc theo PM/đơn vị/customer/status/thời gian. |
| PLN-FR-PORT-06 | Roll-up Master → Portfolio theo Planned Effort trọng (hoặc cấu hình chuẩn duy nhất). |

### 2.14 Master–Detail roll-up chung (PLN-FR-MASTER-*)

| ID | Yêu cầu |
|---|---|
| PLN-FR-MASTER-01 | Detail Plan roll-up lên Master Plan theo Planned Effort (công thức chuẩn). |
| PLN-FR-MASTER-02 | Master tổng hợp: totalEffort, totalDuration, progress, start/finish từ detail (không phụ thuộc WBS detail). |
| PLN-FR-MASTER-03 | Nếu chưa có detail task children, Master WBS có thể tự dựng các phase cấp cao (từ template) tham chiếu. |

## 5. Ma trận PLN-FR → Module

| Module (backend) | FR |
|---|---|
| project-planning | PLN-FR-PLAN-* |
| planning-version | PLN-FR-VERSION-* |
| planning-task (WBS) | PLN-FR-WBS-* |
| planning-dependency | PLN-FR-DEP-* |
| planning-calendar | PLN-FR-CAL-* |
| scheduling-engine | PLN-FR-SCHED-* |
| critical-path | PLN-FR-CP-* |
| planning-resource | PLN-FR-RES-* |
| planning-baseline | PLN-FR-BASE-* (+ PLN-FR-CHG-*) |
| planning-link | PLN-FR-LINK-* |
| planning-template | PLN-FR-TPL-* |
| portfolio | PLN-FR-PORT-*, PLN-FR-MASTER-* |

> **Ghi chú NLP-IN hợp lệ hóa:** Mọi FR đều cần cập nhật ma trận traceability (`docs/planning/15`) và AC (`docs/planning/14`).