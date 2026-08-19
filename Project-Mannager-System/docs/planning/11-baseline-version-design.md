# Planning 11 — Baseline & Version Plan (Thiết kế)

> Dự án: PM Daily Work Management — phân hệ PROJECT PLANNING
> Trạng thái: Draft — nguồn Prompt Project Planning Requirement (mục Baseline & Version).
> Tài liệu: `docs/planning/02` (FR BASE-*, VERSION-*), `docs/planning/03` (rules), `docs/planning/09` (variance), `docs/planning/06`.

## 1. Version plan

| Field | Ý nghĩa |
|---|---|
| `versionNo` | 1, 2, 3… (unique per plan) |
| `status` | DRAFT / ACTIVE / NOT_ACTIVE |
| `snapshot` | Snapshot toàn bộ **tree** tại thời điểm tạo |

Luồng:

```
Tạo plan → version 1 (v1)
Mỗi lần "tạo phiên bản mới" → versionNo+1, snapshot: copy toàn bộ plan_tasks/dependencies/resources (version tương ứng),
  giữ liên kết plan_links nếu tương thích (chỉ copy ref, không duplicate entity), 
  baseline không liên quan (baseline độc lập).
Chỉ 1 version có status=ACTIVE đạt được → auto set theo action (baseline tạo thì gắn vào ACTIVE).
```

> So sánh version: xem diff `plannedStart/Finish`, `duration`, `effort` giữa v1 và v2.

## 2. Baseline

| Field | Ý nghĩa |
|---|---|
| `plan_baselines` | baseline_num (1..n), plan_id, version_id (managed), createdBy, captureComment |
| `plan_baseline_tasks` | snapshot từng task: task_id, planned_* gốc (start/finish/duration/effort), percent_complete, resource(s) |

Quy tắc:

1. Baseline **chỉ tạo được khi plan.status = APPROVED** (không tạo ở DRAFT).
2. Mỗi lần tạo baseline mới → `baseline_num = max+1` (kể cả khi xóa).
3. Baseline **bất biến** — không ghi đè; snapshot stored at creation time.
4. Xóa baseline → **ĐÃ CHỐT 2026-08-07**: bất biến — chỉ soft-delete (set deleted_at), không ghi đè, không xóa vật lý.
5. `plan_baselines.active_baseline_id` (plan) — con trỏ 1..1 tới baseline "đang so sánh" (mặc định baseline mới nhất).

> Baseline vs Version khác nhau:
> - **Version** = lịch sử soạn thảo (có thể so sánh, không phải để giữ lịch chuẩn gốc).
> - **Baseline** = cam kết thời gian/duration/effort lúc APPROVED, bất biến, dùng so sánh **variance**.

## 3. Variance (Current vs Baseline)

```
startVar   = current.task.start − baseline.task.start
finishVar  = current.finish − baseline.finish
durationVar = current.duration − baseline.duration
effortVar  = current.plannedEffort − baseline.plannedEffort
milestoneDone (chỉ áp cho MILESTONE):
   baselineStatus = chưa đạt (baseline percent 0) vs hiện tại percent ≥ 100
% progressDiff = current.progress − baseline.progress
```

Hiển thị: Gantt overlay (baseline bar màu xám/border đục), bảng So sánh "Baseline vs Plan vs Actual".

## 4. Task ưu tiên & giới hạn (v1)

- Không có tính năng **scope baseline** (baseline theo phần WBS) — v1 baseline **toàn phase của plan** duy nhất.
- Baseline per version: chọn tạo baseline gắn với version ACTIVE.

## 5. API gợi ý

| Method | Endpoint | Mô tả |
|---|---|---|
| POST | `/api/v1/plans/{id}/versions` | Tạo version mới |
| GET | `/api/v1/plans/{id}/versions` | Danh sách version |
| GET | `/api/v1/plans/{id}/versions/{versionNo}/diff` | So sánh 2 version |
| POST | `/api/v1/plans/{id}/baselines` | Tạo baseline (yêu cầu APPROVED) |
| GET | `/api/v1/plans/{id}/baselines` | Danh sách baseline |
| GET | `/api/v1/plans/{id}/baselines/{num}/variance` | Variance vs current |

## 6. Quy tắc cần xác nhận (rồi xem PLN-RULE-BASE-*)

1. Cho phép tạo baseline khi plan APPROVED nhưng **chưa có working calendar**? (đề xuất: bắt buộc chọn calendar trước khi APPROVED.)
2. Baseline có tính resource snapshot? (đề xuất: **có** — `plan_baseline_tasks` lưu danh sách resource + allocation as text/json? — chốt: thêm bảng `plan_baseline_task_resources` nếu cần; v1 dùng cột JSON `resources_snapshot`.)
3. Khi baseline đổi theo version (v2 tạo → baseline mới) → baseline cũ giữ hay bị "superseded"? (đề xuất: giữ hoàn toàn; thêm flag `is_superseded`? — đề xuất: không — chỉ đổi con trỏ `activeBaselineId`.)

> Baseline là **chức năng mapping to plan**, không toàn detail — v1 áp dụng cho Master (chính) và Detail (tùy chọn).