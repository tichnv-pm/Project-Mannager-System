# Planning 14 — Acceptance Criteria (Chấp nhận nghiệm thu)

> Dự án: PM Daily Work Management — phân hệ PROJECT PLANNING
> Quy ước ID: `PLN-AC-<NHÓM>-<NN>` (tương ứng FR mục docs/planning/02).
> Trạng thái: Draft — nguồn Prompt Project Planning Requirement + rule docs/planning/03.

## PLN-AC-PLAN

| ID | CRITERION |
|---|---|
| PLN-AC-PLAN-01 | Tạo được plan Master/Detail/Template, bắt buộc planCode unique, planName ≥ 3 ký tự |
| PLN-AC-PLAN-02 | Tạo dự án không plan → không thể gọi endpoint tạo plan (project required, not soft-deleted) |
| PLN-AC-PLAN-03 | Detail chỉ được trỏ Master; quá 1 level → lỗi `INVALID_PARENT_DEPTH` |
| PLN-AC-PLAN-04 | Tối đa 1 Master ACTIVE/dự án |
| PLN-AC-PLAN-05 | Status chuyển theo state machine (chuỗi đầy đủ docs/planning/06); chuyển không hợp lệ → 400 `INVALID_TRANSITION` |
| PLN-AC-PLAN-06 | Xóa mềm: danh sách mặc định không hiện plan deleted |

## PLN-AC-VERSION

| ID | CRITERION |
|---|---|
| PLN-AC-VERSION-01 | Tạo version mới → versionNo tăng theo max+1; snapshot giống hiện tại 100% |
| PLN-AC-VERSION-02 | Tạo version khi PLAN=APPROVED → `ACTIVE` version giữ baseline (giữ liên tục) |
| PLN-AC-VERSION-03 | So sánh v1 vs v2 trả ra danh sách diff (start/finish/duration/effort) |
| PLN-AC-VERSION-04 | Chỉ 1 active version; gọi set active version ≠ → 409/400 |
| PLN-AC-VERSION-05 | Snapshot tạo sai version → không tác động baseline hiện tại |

## PLN-AC-WBS

| ID | CRITERION |
|---|---|
| PLN-AC-WBS-01 | Thêm task tự đánh lại wbsCode & sequenceNumber & outlineLevel |
| PLN-AC-WBS-02 | Chặn vòng lặp cha-con → `CIRCULAR_PARENT` |
| PLN-AC-WBS-03 | Chặn task lá làm cha (TASK/MILESTONE có con) → 400 |
| PLN-AC-WBS-04 | Xóa summary còn con → **400 HAS_CHILDREN** (từ chối); xóa task con thành công khi hết con | 
| PLN-AC-WBS-05 | Milestone: duration 0, isSummary false; cho phép có milestone trên summary |
| PLN-AC-WBS-06 | Roll-up progress leaf theo effort; khi effort=0 fallback duration; duration=0 → avg |
| PLN-AC-WBS-07 | Thay đổi effort leaf → summary progress update | 
| PLN-AC-WBS-08 | Tree data trả về đúng 1 root/plan |
| PLN-AC-WBS-09 | Khi 1 task con bị xóa → renumber sequence sibling còn lại |
| PLN-AC-WBS-10 | Excel import/export WBS (đâu ra giai đoạn sau) |

## PLN-AC-DEP

| ID | CRITERION |
|---|---|
| PLN-AC-DEP-01 | Tạo dep cùng plan, từ→to khác nhau; lag hợp (int hoặc 0) |
| PLN-AC-DEP-02 | Dependency vòng lặp → 400 `DEPENDENCY_CYCLE` (2+ node tự khép) |
| PLN-AC-DEP-03 | FS/SS/FF/SF → online đúng cho từng type |
| PLN-AC-DEP-04 | Xóa task có dep-truy → xóa luôn dep (cascade soft delete) |
| PLN-AC-DEP-05 | Lag âm v1 được phép + cảnh báo (config allowNegativeLag) | 
| PLN-AC-DEP-06 | Cross-plan dep → 400 `CROSS_PROJECT_DEPENDENCY` |

## PLN-AC-CAL

| ID | CRITERION |
|---|---|
| PLN-AC-CAL-01 | Tạo calendar org + exception; workspace mịn true default |
| PLN-AC-CAL-02 | Weekend/event nào bị loại khỏi tính duration |
| PLN-AC-CAL-03 | Calendar tham chiếu org khi tạo plan (fallback) |
| PLN-AC-CAL-04 | Date làm việc nếu rơi ngày lễ → đẩy sang ngày tiếp hợp lệ |
| PLN-AC-CAL-05 | Tạo exception WORKING để weekend thành ngày làm việc |

## PLN-AC-SCHED

| ID | CRITERION |
|---|---|
| PLN-AC-SCHED-01 | Task AUTO recalc: start/finish đúng theo dep + calendar ± lag |
| PLN-AC-SCHED-02 | Task MANUAL không bị auto thay đổi bởi recalc |
| PLN-AC-SCHED-03 | Recalc idempotent (2 lần = cùng kết quả) |
| PLN-AC-SCHED-04 | Thay đổi schedule → warnings trả về (constraint, date...) |
| PLN-AC-SCHED-05 | Roll-up summary tái tính sau recalc |
| PLN-AC-SCHED-06 | Nhiều thread cùng edit → optimistic lock 409 |

## PLN-AC-CP

| ID | CRITERION |
|---|---|
| PLN-AC-CP-01 | Critical path tính đúng theo CPM (forward/backward float) |
| PLN-AC-CP-02 | TotalFloat=0 → isCritical=true |
| PLN-AC-CP-03 | Task MANUAL nằm trên critical path được tính luôn |
| PLN-AC-CP-04 | Recalc xong → danh sách critical updated |
| PLN-AC-CP-05 | Không có dep → toàn bộ task đều critical (mọi start=0..? ) |

## PLN-AC-RES

| ID | CRITERION |
|---|---|
| PLN-AC-RES-01 | Gán resource vào task (type USER/TEAM/ROLE/EXTERNAL) |
| PLN-AC-RES-02 | Workload theo Resource hiển thị % theo (ngày/tuần/tháng) |
| PLN-AC-RES-03 | Over-allocation → alert + warning trả về (không level v1) |
| PLN-AC-RES-04 | Quyền hạn: MEMBER chỉ thấy workload mình, không sửa |
| PLN-AC-RES-05 | Resource gán lên summary → cho phép (đại diện) nhưng KHÔNG tính workload | 
| PLN-AC-RES-06 | Type EXTERNAL không có capacity (không over alert) | 
| PLN-AC-RES-07 | Type TEAM bị từ chối (400 VALIDATION_ERROR) — không có trong enum | 

## PLN-AC-BASE

| ID | CRITERION |
|---|---|
| PLN-AC-BASE-01 | Tạo baseline → chỉ khi APPROVED, versionNo tự tăng |
| PLN-AC-BASE-02 | Baseline bất biến, copy đúng current snapshot |
| PLN-AC-BASE-03 | Baseline thứ 2 → không ghi đè; `plan_baselines` được giữ |
| PLN-AC-BASE-04 | Variance: start/finish/duration/effort/progress so với current |
| PLN-AC-BASE-05 | Create baseline khi thay đổi tree bean → OK (snapshot) |

## PLN-AC-CHG

| ID | CRITERION |
|---|---|
| PLN-AC-CHG-01 | Thay đổi sau APPROVED → tạo change_history ghi lại chi tiết diff |
| PLN-AC-CHG-02 | Change từ execution (link) → suggestion + PM duyệt | 
| PLN-AC-CHG-02b | Plan effort ≥ 10,000 phút → dual approve (PM + ADMIN) trước khi apply |
| PLN-AC-CHG-03 | Từ chối suggestion → không apply |
| PLN-AC-CHG-04 | Change history chứa field thay đổi, old/new, actor, thời gian |
| PLN-AC-CHG-05 | Rollback (không có v1) → future refactor |

## PLN-AC-LINK

| ID | CRITERION |
|---|---|
| PLN-AC-LINK-01 | Tạo link planning task ↔ Execution/Issue/Risk... đúng type |
| PLN-AC-LINK-02 | Planning task có maximum 1 Execution task (primary) |
| PLN-AC-LINK-03 | Entity type/ID ref valid ngay khi tạo |
| PLN-AC-LINK-04 | Execution progress → config rolled-up actual |
| PLN-AC-LINK-05 | Risk OCCURRED → issue link được tạo tự động |
| PLN-AC-LINK-06 | Link lưu kèm source/user/timestamp |

## PLN-AC-TPL

| ID | CRITERION |
|---|---|
| PLN-AC-TPL-01 | Tạo template mặc định 8 (PUBLISHED) |
| PLN-AC-TPL-02 | Tạo plan từ template (Master + full) |
| PLN-AC-TPL-03 | Sửa template → version tăng, không đổi template gốc |
| PLN-AC-TPL-04 | Template xóa soft; plan đã dùng → plan giữ độc lập |
| PLN-AC-TPL-05 | Clone template → 2 bản độc lập |

## PLN-AC-PORT

| ID | CRITERION |
|---|---|
| PLN-AC-PORT-01 | Portfolio hiển thị timeline đa dự án (Master ACTIVE) |
| PLN-AC-PORT-02 | Progress tổng theo công thức roll-up (tương tự mục 4 docs 06) |
| PLN-AC-PORT-03 | Milestone chính + dự án trễ so threshold |
| PLN-AC-PORT-04 | Over-allocation chéo project hiển thị trung bình |
| PLN-AC-PORT-05 | Filter PM / đơn vị / khách / status / ngày |

## PLN-AC-MASTER

| PLN-AC-MASTER-01 | Master auto roll-up từ detail (start/finish/effort/progress) |
| PLN-AC-MASTER-02 | Detail đổi → Master update (trigger) |
| PLN-AC-MASTER-03 | Drill-down từ Master → Detail trong Gantt |

## 2. Ghi chú chung

- Acceptance phải được 100% test qua integration test backend (JUnit) + e2e frontend (Playwright — backlog v1.1).
- Mọi AC có traceback tới FR/BR/rule (docs/planning/15).
- AC nào "CHỜ XÁC NHẬN" không đưa vào test cho đến khi PM/khách chốt.