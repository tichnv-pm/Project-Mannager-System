# Planning 07 — WBS & Planning Task (Thiết kế)

> Dự án: PM Daily Work Management — phân hệ PROJECT PLANNING
> Trạng thái: Draft — nguồn Prompt Project Planning Requirement (mục WBS).
> Tài liệu: `docs/planning/02`, `docs/planning/03`, `docs/database/02` (plan_tasks).

## 1. Định nghĩa WBS

WBS (Work Breakdown Structure) là cây thứ bậc các phần tử kế hoạch trong một plan. Các loại nút (`taskType`):

| Loại | isSummary | Có con? | isMilestone | Ý nghĩa |
|---|---|---|---|---|
| `PHASE` | ✔ | Có | — | Giai đoạn lớn (từ template phase) |
| `SUMMARY_TASK` | ✔ | Có | — | Nút gộp nhóm |
| `WORK_PACKAGE` | — | có thể có | — | Gói công việc (nhóm task) |
| `TASK` | ✘ | Không | — | Task lá thực hiện |
| `MILESTONE` | ✘ | Không | ✔ | Cột mốc (duration=0) |
| `EXTERNAL_TASK` | ✘ | Không | — | Việc ngoài hệ thống (chỉ tham chiếu lịch) |

> `isSummary` trong DB được **suy tự động** từ việc có `plan_tasks.children` (không chỉnh tay) — nhưng vẫn lưu cột để dễ query/Gantt.

## 2. Cấu trúc bảng `plan_tasks` (logic)

Các trường đặc trưng (chi tiết colum: `docs/database/02`):

```
id, plan_id, parent_id, wbs_code, task_code, task_name, description,
task_type, outline_level, sequence_number, phase, work_package, deliverable,
owner_id, planned_start, planned_finish, duration_minutes, planned_effort_minutes,
actual_start, actual_finish, actual_effort_minutes, remaining_effort_minutes,
percent_complete, status, priority, schedule_mode, constraint_type, constraint_date,
is_summary, is_milestone, is_critical, version, created_*/updated_*
```

> `is_critical` lưu kết quả tính critical path (chụp tại lần recalc gần nhất), không phải do PM nhập.

## 3. Thao tác trên cây WBS

| Thao tác | Ý nghĩa | Quy tắc |
|---|---|---|
| Thêm task cùng cấp | Thêm sibling sau task hiện tại | Cập nhật `sequenceNumber` sau đó |
| Thêm task con | Thêm child dưới task hiện tại | Task lá (TASK/MILESTONE/EXTERNAL) không được làm cha → phải đổi taskType hoặc chặn |
| Indent | Giảm `outlineLevel`, cha thành parent hiện tại | Kiểm tra cha mới hợp lệ (không vòng lặp) |
| Outdent | Tăng `outlineLevel` | Cha mới = grandfather |
| Move up/down | Đổi vị trí giữa siblings | Renumber `sequence_number` + `wbs_code` |
| Move to parent | Đổi parent | Verify không vòng điệp; cùng plan |
| Expand/collapse | UI state (not server) | Không đổi dữ liệu |

Sau mỗi thao tác cây: engine **re-number** `wbs_code` toàn nhánh (vd: `1`, `1.1`, `1.1.2`). Lưu bằng transaction + optimistic locking (409 nếu xung).

## 4. Quy tắc chuẩn hóa `wbs_code`

- Cấp 1: `1..n` (theo `sequenceNumber` của root).
- Cấp 2: `1.1..1.n`.
- ... tiếp tục theo `outlineLevel`.
- `task_code` = unique trong plan (VD `task-0001`) hoặc đồng nhất với wbs_code — chốt: dùng `wbs_code` làm mã hiển thị, `task_code` làm unique nhận dạng tham chiếu API.

## 5. Roll-up tiến độ (progress) & ngày

Chỉ áp dụng cho Summary (có con); leaf cập nhật tay hoặc từ execution (xem `docs/planning/12`).

```
// Summary
plannedStart   = MIN(child.plannedStart)
plannedFinish  = MAX(child.plannedFinish)
actualStart    = MIN(child.actualStart WHERE != null)
actualFinish   = MAX(child.actualFinish WHERE != null)
summaryProgress:
  w = SUM(child.progress * child.plannedEffort)  (plannedEffort>0)
  nếu SUM(plannedEffort)=0  → dùng durationMinutes
  nếu duration=0            → avg progress
```

`isCritical` cho summary = bất kỳ child critical.

## 6. Xử lý xóa summary còn con

**ĐÃ CHỐT 2026-08-07**: **TỪ CHỐI xóa** — API trả `400 HAS_CHILDREN`; người dùng phải xóa hết task con trước khi xóa summary/cha. UI disable nút delete trên task có con (docs/planning/03 mục 14 #5, PLN-AC-WBS-04).

## 7. Validation tổng

| Ràng buộc | Lỗi (ErrorCode dự kiến) |
|---|---|
| Cha–con vòng lặp | `CIRCULAR_PARENT` (400) |
| Task lá làm cha | `INVALID_PARENT` (400) |
| Task khác plan khi move | `INVALID_PLAN` (400) |
| Xóa summary/cha còn con | `HAS_CHILDREN` (400) |
| `percentComplete` ngoài [0,100] | `VALIDATION_ERROR` |
| Milestone có `plannedEffort>0` hoặc duration>0 | `VALIDATION_ERROR` |
| Version cũ khi cập nhật | `CONFLICT` (409) |