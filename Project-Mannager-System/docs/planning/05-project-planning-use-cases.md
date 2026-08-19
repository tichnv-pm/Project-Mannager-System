# Planning 05 — Use Cases Project Planning

> Dự án: PM Daily Work Management — phân hệ PROJECT PLANNING
> Quy ước ID: `PLN-UC-<NN>`. Acceptance criteria tương ứng: `docs/planning/14`.
> Trạng thái: Draft — nguồn chính từ Prompt Project Planning Requirement.
> Tài liệu liên quan: `docs/planning/02`, `docs/planning/03`, `docs/use-cases/` (UC hiện tại).

## Danh sách use cases

| ID | Tên | Liên quan PLN-FR |
|---|---|---|
| PLN-UC-01 | Tạo & quản lý Project Plan (Master/Detail) | PLN-FR-PLAN-* |
| PLN-UC-02 | Quản lý Version plan | PLN-FR-VERSION-* |
| PLN-UC-03 | Soạn thảo WBS & planning task | PLN-FR-WBS-* |
| PLN-UC-04 | Quản lý Dependency & cycle check | PLN-FR-DEP-* |
| PLN-UC-05 | Cấu hình Working Calendar | PLN-FR-CAL-* |
| PLN-UC-06 | Auto Scheduling & recalc | PLN-FR-SCHED-* |
| PLN-UC-07 | Xem Critical Path | PLN-FR-CP-* |
| PLN-UC-08 | Gán resource & workload / over-allocation | PLN-FR-RES-* |
| PLN-UC-09 | Baseline & variance | PLN-FR-BASE-* |
| PLN-UC-10 | Change log sau APPROVED | PLN-FR-CHG-* |
| PLN-UC-11 | Liên kết plan_links | PLN-FR-LINK-* |
| PLN-UC-12 | Template plan | PLN-FR-TPL-* |
| PLN-UC-13 | Portfolio view | PLN-FR-PORT-* |
| PLN-UC-14 | Master–Detail roll-up | PLN-FR-MASTER-* |
| PLN-UC-15 | Plan → Execution kết hợp (actual) | PLN-FR-LINK-04..05 |

## PLN-UC-01 — Tạo & quản lý Project Plan

**Actor:** ADMIN, PROJECT_MANAGER (PM dự án).

**Trigger**: Cần lập kế hoạch cho dự án mới / soạn lại kế hoạch.

**Tiền điều kiện**: Dự án tồn tại chưa xóa mềm; user có `plan:create`/`plan:update`.

**Luồng chính**:
1. PM chọn project → chọn tạo MASTER (hoặc DETAIL kèm parent Master).
2. Nhập `planCode` (auto-detect unique), `planName`, `planType`, `calendarId` (mặc định calendar tổ chức), `plannedStart`.
3. Hệ thống tạo plan DRAFT + version 1 (v1 code), trả ProjectPlanResponse.
4. PM soạn WBS (UC-03), dependency (UC-04), resource (UC-08).
5. PM submit (SUBMITTED) → duyệt (APPROVED) → ACTIVE (qua PLN-UC-02/UC-09 baseline).

**Luồng thay thế/ngoại lệ**: planCode trùng → 409; không quyền → 403; project xóa mềm → 404.

**Audit**: `PLAN_CREATED`, `PLAN_STATUS_CHANGED`.

## PLN-UC-02 — Quản lý Version plan

**Actor**: ADMIN, PM (PM dự án).

**Luồng** (**note** — version là snapshot):
1. Gửi `POST /plans/{id}/versions` → tạo phiên bản mới (versionNo+1) snapshot toàn bộ tree.
2. Gán `activeVersionId` → chỉ một version ACTIVE.
3. Xem lịch sử version (danh sách + so sánh ngày/thời lượng).

**Ngoại lệ**: versionNo trùng (unique) → 409; plan chưa APPROVED vẫn cho tạo version nhưng baseline chỉ khi APPROVED.

## PLN-UC-03 — Soạn WBS & planning task

**Actor**: PM (manage), MEMBER (view).

**Luồng chính**: Creating tree, add child/sibling, indent/outdent/move (up/down/to-parent), tự đánh lại `wbsCode`, cập nhật trường task; tạo task type MILESTONE.

**Chặn**: vòng lặp cha-con (400 `CIRCULAR_PARENT`), xóa summary còn con (cần confirm `confirmDeleteTree`), milestone có con (400).

## PLN-UC-04 — Dependency & cycle check

**Actor**: `+`.

**Luồng**:
1. Chọn predecessor/successor + type (FS/SS/FF/SF) + lag.
2. Hệ thống kiểm tra: không self (predecessor=same), không cycle (DFS), cùng plan.
3. Lưu → trigger `schedule-recalc` nếu task AUTO.

**Ngoại lệ**: cycle → 400 `DEPENDENCY_CYCLE`; cross-plan → 400 `CROSS_PROJECT_DEPENDENCY`.

## PLN-UC-05 — Working calendar

**Actor**: ADMIN (calendar tổ chức), PM (calendar dự án).

**Luồng**: chọn calendar → thêm/cấu hình working-day (giờ vào/ra, ngày trong tuần), thêm holiday (exception NON_WORKING), thêm special working date (WORKING) → fallback lên tổ chức.

## PLN-UC-06 — Auto scheduling & recalc

**Trigger**: thay đổi dependency / duration / calendar / constraint / start-finish.

**Luồng**:
1. UI (hoặc API) gửi thay đổi → service gọi `SchedulingEngine.recalculate(planId, change)`
2. Engine tính lại downstream AUTO tasks theo calendar; bỏ qua MANUAL.
3. Roll-up Summary (min/max start-finish).
4. Trả `warnings[]` (constraint xung, date không hợp).

## PLN-UC-07 — Critical path

**Luồng**: mở tab Critical Path → service tính forward/backward pass → trả ES/EF/LS/LF + Total/Free Float, đánh dấu node TF=0 là critical. Không lưu cứng.

## PLN-UC-08 — Resource assignment & workload

**Luồng**:
1. PM gán resource (user/team/role/external) vào task (allocation %, effort, ngày từ-đến).
2. Xem workload 1 resource theo ngày/tuần/tháng (aggregate trên nhiều plan/project).
3. Hệ thống tính tổng allocation vs capacity → cảnh báo over-allocation > 100%.

## PLN-UC-09 — Baseline & variance

**Tiền điều kiện**: plan approved. Hệ thống chụp snapshot (task dates, effort, progress, resource, milestone), baselineNum++. Không ghi đè. Xem: variance (Current-Baseline) theo start/finish/duration/effort/milestone.

## PLN-UC-10 — Change history

**Luồng**: sau APPROVED mọi thay đổi lịch tạo `plan_change_history`; nếu do Issue/Risk/Execution → tạo change suggestion → PM duyệt/ từ chối → chỉ apply khi accept.

## PLN-UC-11 — plan_links

**Luồng**: chọn planning task → chọn entity (execution task/issue/risk/...) + linkType + note → lưu. Validate: Execution task 1 planning chính unique. Nếu Risk OCCURRED → tạo issue và giữ link.

## PLN-UC-12 — Template

**Luồng**: ADMIN tạo/sửa/clone template, cho tạo plan từ template (chọn bỏ phase). PM clone template (chỉ đọc template catalog).

## PLN-UC-13 — Portfolio

**Luồng**: mở Portfolio → timeline đa dự án, tổng hợp progress, milestone chính, dự án trễ (delay > threshold), over-allocation chéo project, lọc theo PM/đvị/customer/status/time.

## PLN-UC-14 — Master–Detail roll-up

**Luồng**: Detail roll-up theo Planned Effort → thành Master start/finish/effort/progress; xem chi tiết Drill-down Master → Detail.

## PLN-UC-15 — Plan ↔ Execution kết hợp

**Luồng**: planning task liên kết execution task(s). Actual effort/progress có thể roll-up từ execution theo config; không tự hoàn thành khi execution chưa DONE; thay đổi của execution → change suggestion (UC-10) do link sửa; baseline không đổi.

> Mọi UC đều có acceptance criteria tại `docs/planning/14` và traceability tại `docs/planning/15`.