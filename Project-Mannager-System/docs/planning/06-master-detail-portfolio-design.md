# Planning 06 — Master Plan – Detail Plan – Portfolio (Thiết kế)

> Dự án: PM Daily Work Management — phân hệ PROJECT PLANNING
> Trạng thái: Draft — nguồn chính từ Prompt Project Planning Requirement (mục Master/Detail/Portfolio).
> Tài liệu liên quan: `docs/planning/01`, `docs/planning/02`, `docs/planning/08`, `docs/planning/12`.

## 1. Khái niệm

| Khái niệm | Mô tả |
|---|---|
| **Master Plan** | Kế hoạch tổng thể một dự án: các phase lớn + milestone chính; tổng hợp từ các Detail Plan. Nhiều version nhưng chỉ 1 version ACTIVE. |
| **Detail Plan** | Kế hoạch chi tiết thuộc Master (cha `parentPlanId`): chia theo phase/module/sprint/release/work package/team/vendor. Một Master có nhiều Detail. |
| **Portfolio** | Tổng hợp kế hoạch nhiều dự án: timeline chung, tổng hợp tiến độ, milestone chính, dự án trễ, xung đột nguồn lực, filter PM/đơn vị/khách hàng/status/thời gian. |

## 2. Quan hệ dữ liệu

```
projects 1 ── n project_plans (planType: MASTER/DETAIL/TEMPLATE_INSTANCE)
  └── project_plans.parent_plan_id ──> project_plans (chỉ DETAIL trỏ MASTER)
  └── project_plans.active_version_id ──> plan_versions
  └── project_plans.calendar_id ──> plan_calendars
```

Ràng buộc chính (chi tiết `docs/planning/03`, `docs/database/02`):

1. `parent_plan_id` chỉ được khác NULL khi `planType = DETAIL` và cha phải là `MASTER`.
2. Không tạo vòng lặp master–detail (1 cấp ở v1 — không có "detail của detail").
3. Tối đa **1** Master ACTIVE trên mỗi dự án (unique partial).
4. Detail Plan **roll-up** lên Master: `plannedStart = min(child)`, `plannedFinish = max(child)`, `plannedEffortMinutes = tổng`, `progress` theo trọng số planned effort (công thức `docs/planning/07` mục roll-up).

## 3. Luồng vòng đời plan (status)

```
DRAFT ──submit──▶ SUBMITTED ──approve──▶ APPROVED ──activate──▶ ACTIVE ──▶ COMPLETED
    │                 │                    │                        │
    └──cancel──▶ CANCELLED   ON_HOLD ◀─────┘                        └──▶ ARCHIVED
```

| Chuyển | Điều kiện | Quyền |
|---|---|---|
| DRAFT → SUBMITTED | Có ≥ 1 planning task | `plan:update` |
| SUBMITTED → APPROVED | Không có validation lỗi | `plan:approve` |
| APPROVED → ACTIVE | (Tự hoặc PM) | `plan:approve` |
| ACTIVE → COMPLETED | progress = 100, mọi task đóng | `plan:approve` |
| bất kỳ → CANCELLED / ARCHIVED | — | `plan:approve` |
| ON_HOLD ↔ ACTIVE | — | `plan:approve` |

> Baseline chỉ được tạo khi status = APPROVED (xem `docs/planning/11`).

## 4. Roll-up công thức (thống nhất toàn phân hệ)

Áp dụng 3 tầng: **WBS Summary** → **Detail → Master** → **Master → Portfolio**.

```
summaryProgress = SUM(childProgress * childPlannedEffort) / SUM(childPlannedEffort)
```

Fallback (thứ tự):
1. Nếu `SUM(plannedEffort) = 0` → dùng trọng số `durationMinutes`.
2. Nếu duration cũng = 0 → trung bình đơn giản progress của children.

Ngày: `start = min(children.start)`, `finish = max(children.finish)`.

> Nguồn effort của leaf có thể là `plannedEffortMinutes` hoặc (khi liên kết execution) tổng `estimateMinutes` — xem `docs/planning/12`.

## 5. Portfolio — thiết kế đọc (read model)

Không tạo bảng lưu số liệu portfolio (tính tại thời điểm đọc qua aggregate query — theo NFR-PERF của hệ thống). Dữ liệu trả về:

| Nhóm | Nội dung |
|---|---|
| Timeline | Mỗi dự án 1 thanh (hoặc nhóm): start–finish (Master ACTIVE), kèm progress overlay |
| Tiến độ | progress tổng hợp theo công thức mục 4 (trọng số effort) |
| Milestone | Danh sách milestone chính (task MILESTONE cấp 1-2 của Master) sắp theo ngày |
| Trễ hạn | Dự án có `delayDays > threshold` (cấu hình, mặc định 7) — so với baseline/actual |
| Xung đột nguồn lực | Tổng allocation theo (resource, tuần) vượt capacity — từ `plan_task_resources` của mọi plan ACTIVE |
| Filter | `pmId`, `department` (nếu có), `customerName`, `status`, `fromDate/toDate` |

Phân quyền Portfolio: `plan:view` + phạm vi dự án (giống `project:view`).

## 6. Ví dụ luồng nghiệp vụ

1. PM tạo Master Plan "Toàn trình App Mobile" (type MASTER) từ template FULL_LIFECYCLE.
2. Tạo 3 Detail Plan: "Backend Plan", "Frontend Plan", "Testing Plan" (parentPlanId = Master).
3. PM soạn WBS trong từng Detail; Master tự roll-up start/finish/effort/progress.
4. Master được submit → approve → baseline (chụp snapshot).
5. Mỗi tuần Portfolio cho lãnh đạo thấy tổng thể các dự án + cảnh báo trễ/xung đột nguồn lực.

## 7. API gợi ý (chi tiết `docs/api/13-planning-api.md`)

| Method | Endpoint | Mục đích |
|---|---|---|
| GET | `/api/v1/plans` | Danh sách plan (lọc) |
| POST | `/api/v1/plans` | Tạo plan |
| GET | `/api/v1/plans/{id}` | Chi tiết plan (roll-up) |
| PUT | `/api/v1/plans/{id}` | Sửa plan (cấu hình, calendar, status) |
| DELETE | `/api/v1/plans/{id}` | Xóa mềm |
| POST | `/api/v1/plans/{id}/submit` | DRAFT → SUBMITTED |
| POST | `/api/v1/plans/{id}/approve` | SUBMITTED → APPROVED |
| POST | `/api/v1/plans/{id}/activate` | APPROVED → ACTIVE |
| GET | `/api/v1/portfolio` | Dữ liệu portfolio |