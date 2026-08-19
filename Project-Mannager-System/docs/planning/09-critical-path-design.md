# Planning 09 — Critical Path (Thiết kế)

> Dự án: PM Daily Work Management — phân hệ PROJECT PLANNING
> Trạng thái: Draft — nguồn Prompt Project Planning Requirement (mục Critical Path).
> Tài liệu: `docs/planning/08` (engine), `docs/planning/03` (rule), `docs/planning/07` (is_critical).

## 1. Mục đích

- Xác định các task **quan trọng** (trễ 1 ngày ⇒ trễ toàn plan) để PM ưu tiên.
- Hiển thị trên Gantt (đường màu), và nổi bật trong danh sách.
- Không phải chức năng nhập liệu — là **view tính lại theo yêu cầu** (không lưu cột `is_critical` mới — lưu kết quả chụp gần nhất; xem ghi chú mục 5).

## 2. Phương pháp — forward & backward pass (CPM)

```
forward pass  (từ start):
  ES(t) = max(EF(pred) + lag) cho mọi pred; EF(t) = ES + duration(working days)
  ES của node không có pred = plan.projectStart (theo calendar)

backward pass (từ finish — mục tiêu: plan.finish):
  LF(t) = min(LS(succ)) cho mọi succ; LS(t) = LF − duration
  LF của node không có succ = plan.finish

float:
  TotalFloat(t) = LS(t) − ES(t)  (= LF − EF)
  FreeFloat(t)  = min(ES(succ)) − EF(t)  (0 nếu không succ)

critical khi: TotalFloat <= threshold (config, mặc định 0)
```

> Lưu ý: duration = working days theo calendar (không đếm holiday/weekend), lag âm được xử lý như `docs/planning/08` mục 3.2.

## 3. Dữ liệu trả về

| Field | Giải thích |
|---|---|
| `taskId`, `taskName`, `wbsCode` | Nhận dạng |
| `es/ef` | Early start/finish |
| `ls/lf` | Late start/finish |
| `totalFloat` | Total float (minutes/working days) |
| `freeFloat` | Free float |
| `isCritical` | `totalFloat <= threshold` |
| `criticalPathId` | Nhóm critical path (nếu nhiều path) |

## 4. API

| Method | Endpoint | Mô tả |
|---|---|---|
| GET | `/api/v1/plans/{id}/critical-path` | Tính & trả danh sách task + float |

> Không có endpoint "lưu critical path" — tính lại mỗi lần (mang tính chụp, đủ cho UI).

## 5. Ghi chú lưu trữ

- Kết quả critical path tính được dùng cho:
  - UI highlight,
  - cảnh báo khi dependency/effort thay đổi làm đổi path (so với baseline? — v1: chỉ hiển thị).
- Trường `is_critical` trên `plan_tasks` (nếu có) là kết quả chụp từ recalc gần nhất, đánh dấu là "không phải input" — tránh nhầm là PM nhập.
- **CHỜ XÁC NHẬN**: có cần lưu `critical_path` snapshot theo version để so sánh baseline vs current hay không (đề xuất v1: KHÔNG — tính live, chỉ lưu `is_critical` bảng tasks).

## 6. Quy tắc cần chốt

1. Threshold float (mặc định 0 phút) — cấu hình tổ chức hay dự án? (đề xuất: config hệ thống, đổi được).
2. Có tính critical path trên Master (dựa trên detail) không? (đề xuất: v1 chỉ tính trong từng plan; portfolio chỉ dùng roll-up).
3. Task MANUAL có nằm trong critical path không? (đề xuất: CÓ — là ràng buộc bắt buộc nếu là predecessor).

## 7. Ví dụ minh họa

```
Tasks: A(2d) → B(3d) → D(1d); A → C(1d) → D
Calendar 8h/ngày, không ngày lễ:
forward:  A: ES=0 EF=2d; B: ES=2 EF=5; C: ES=2 EF=3; D: ES=max(5,3)=5 EF=6
backward: D: LF=6 LS=5; B: LF=5 LS=2; C: LF=5 LS=4; A: LF=min(2,4)=2 LS=0
float:    A=0, B=0, D=0 critical; C TF=1 (không critical)
→ Critical path: A → B → D
```