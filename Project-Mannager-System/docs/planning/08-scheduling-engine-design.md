# Planning 08 — Scheduling Engine (Thiết kế)

> Dự án: PM Daily Work Management — phân hệ PROJECT PLANNING
> Trạng thái: Draft — nguồn Prompt Project Planning Requirement (mục Scheduling).
> Tài liệu: `docs/planning/03`, `docs/planning/07`, `docs/planning/09` (critical path), `docs/planning/06` (roll-up).

## 1. Mục đích & phạm vi

- Tính **plannedStart/plannedFinish/duration** cho task AUTO dựa trên: dependency, calendar, constraint, lag, resource (nếu có ràng buộc).
- Tự lan truyền thay đổi downstream (thuật toán **topological forward pass** theo "dependency first — không DFS đấu đấm nhau").
- **KHÔNG** làm resource leveling v1 (chỉ cảnh báo over-allocation).
- **KHÔNG** làm backward pass để đẩy lịch (chỉ critical path dùng backward cho float — `docs/planning/09`).

## 2. Inputs / Outputs

**Inputs**: plan (calendar), danh sách tasks (start/finish/duration/effort, schedule_mode, constraint), dependencies (type + lag).
**Output**: cập nhật `plannedStart/Finish`, `durationMinutes`, `isCritical`; danh sách `warnings[]`.

## 3. Thuật toán chính

### 3.1 Forward scheduling (network) cho AUTO task

```
tasks topo-sort theo dependency (DFS) :
  for task in topo:
    if task.scheduleMode == MANUAL: giữ nguyên
    if task.constraint == FIXED_DATE: giữ (check hợp)
    else:
      candidate = calendar.resolve(
        max(
          predecessor.finish + lag   (FS)
          predecessor.start + lag    (SS)
          or successor contexts...
          plan.projectStart           (nếu không có pred sẵn)
        ),
        duration      = task.durationMinutes hoặc từ effort/resource
      )
      task.start = candidate; task.finish = calendar.addWorkingDuration(start, duration)
      if constraint STARTS_NO_EARLIER_THAN and start < constraintDate: push start = constraintDate
      if constraint START_NO_LATER_THAN (v1 cảnh báo, không auto)
```

### 3.2 Lag (−/+)

- Lag > 0: cộng thêm lag ngày làm việc sau khi finish predecessor.
- Lag < 0 (overlap — CHỜ XÁC NHẬN `PLN-RULE-SCHED-02`): v1 cho phép nhưng kèm cảnh báo; nếu backend chặn thì toggle config `scheduling.allowNegativeLag`.

### 3.3 Công cụ calendar

```
working time (h)   = compute daily avail (per weekday config)
addWorkingDuration(start, minutes) → (finish, workingMinutes)
số ngày chênh (ngày dương nếu ngoài giờ làm, weekend, holiday được skip...)
```

Chi tiết calendar: `docs/planning/09-bis` — không, xem `docs/planning/03` mục CAL & `docs/planning/06`... đang: **Working Calendar — `docs/planning/05-calendar…` chưa tồn tại**, chuyển nội dung calendar vào đây (mục 4).

## 4. Working Calendar (nhúng)

Calendar thuộc tổ chức (default) hoặc dự án; cấu hình:

| Cấu hình | Ý nghĩa |
|---|---|
| `workingDays` (thứ 2-Chủ nhật x1/0) | Ngày làm việc trong tuần |
| `dailyWorkingHours` | Giờ/ngày (vd 8: 08:00–12:00, 13:00–17:00) |
| `exceptions` | Ngày lễ `NON_WORKING` hoặc ngày làm bù `WORKING` |
| `fallback` | Tham chiếu calendar cha (org → project override) |

## 4. Trigger recalc

Call `SchedulingEngine.recalculate(planId)` khi:
- Thay đổi dependency (add/remove/change lag)
- Thay đổi duration/effort/start/finish của task AUTO
- Thay đổi calendar / plan.projectStart
- Thay đổi constraint
- Tạo version mới (chạy lại để snapshot nhât quán)

> Thực thi **synchronous trong request** nếu plan ≤ 200 task; plan > 200 task → chuyển **async job** (bảng `plan_recalc_jobs` + scheduled job tiêu thụ; UI đánh dấu ENQUEUED_RECALC và có thể poll status). **ĐÃ CHỐT 2026-08-07.**

## 5. Vi phạm & warnings

| Warning | Ý nghĩa | Xử lý |
|---|---|---|
| `CONSTRAINT_CONFLICT` | Constraint mâu thuẫn dependency | Cảnh báo, giữ theo constraint/FIXED thắng |
| `CYCLE_DEPENDENCY` | Vòng lặp | Từ chối tạo dep (400) |
| `DATE_NOT_WORKING` | Start nằm ngoài giờ/ngày lễ | Đẩy về giờ/ngày hợp lệ + warning |
| `NEGATIVE_LAG` | Lag âm | Tùy config (mục 3.2) |
| `SUMMARY_OVERRIDE` | Summary có ngày không khớp roll-up | tự roll-up lại |

## 7. Tính đúng đắn (điều kiện kiểm thử)

- Không task nào có `start > finish`.
- Dependencies luôn được tôn trọng (ai có predecessor thì start ≥ finish pred + lag).
- Task MANUAL không bị thay đổi.
- Holiday/weekend không được tính vào duration làm việc.
- Recalc là idempotent (gọi 2 lần cùng input → 2 output giống nhau).
- Merge: 2 task cùng predecessor không xung đột.

> Engine tính bằng milliseconds **không lưu kết quả chuỗi**, chỉ ghi kết quả cuối cùng xuống DB (data persisted, lại tính mỗi lần trigger) → **không có cột `computed_*`**, trừ `is_critical` mục docs/planning/07.