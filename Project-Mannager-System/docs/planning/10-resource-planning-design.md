# Planning 10 — Resource Planning & Workload (Thiết kế)

> Dự án: PM Daily Work Management — phân hệ PROJECT PLANNING
> Trạng thái: Draft — nguồn Prompt Project Planning Requirement (mục Resource).
> Tài liệu: `docs/planning/03` (rules), `docs/planning/08` (engine), `docs/planning/06` (portfolio).

## 1. Mục đích & phạm vi (v1)

- Gán **tài nguyên** (resource) vào task: user nội bộ, team, role, external.
- Xem **workload** theo ngày/tuần/tháng cho từng resource.
- Cảnh báo **over-allocation** (tổng allocation > capacity).
- **KHÔNG** resource leveling (v1) — chỉ cảnh báo (mục 5).

## 2. Model — `plan_task_resources`

```
id, plan_id, task_id, resource_type (USER/TEAM/ROLE/EXTERNAL), resource_id,
allocation_percent (0..100), role_on_task, start_date, end_date,
planned_effort_minutes, version, created_*/updated_*
```

| resource_type | resource_id trỏ tới | Ghi chú |
|---|---|---|
| `USER` | users.id | Nhân viên nội bộ |
| `ROLE` | role key (vd "Backend Dev") | Vai trò, chưa gán người |
| `EXTERNAL` | tên/email ngoài (free text) | Nhà thầu, vendor |

> **ĐÃ CHỐT 2026-08-07**: bỏ `TEAM` ở v1 (chưa có module teams). `plan_task_resources` enum chỉ 3 giá trị.

> `plan_task_resources.plan_id` phải khớp `task.plan_id` (validate).

## 3. Capacity

- Mỗi `resource_type=USER` có `resource_capacities`: capacity % (mặc định 100), theo khoảng thời gian (start/end) hoặc vô hạn, `source` (ORG/PROJECT).
- Calendar của plan quyết định số ngày làm việc → workload tính theo working minutes.
- Capacity mặc định: 100% theo calendar org.

## 4. Workload tính (aggregation)

```
workload(resource, dateRange) =
  Σ over plan_task_resources r (join task dates):
    r.start/end ∩ [dateRange]  × allocation_percent × (workingMinutes của task)

demand = Σ workload của mọi plan ACTIVE/APPROVED (cùng resource)
capacity = resource_capacities (theo ngày) × workingMinutes
utilization = demand / capacity  → > 1 = over-allocation
```

> Over-allocation tính **chéo plan** (nhiều dự án cùng lúc) — đúng ý nghĩa portfolio mục 6 của `docs/planning/06`.

## 5. Cảnh báo over-allocation

- Khi gán/sửa allocation làm `utilization > 1` → trả `warning` trong response + bảng màu đỏ ở workload view.
- V1 chỉ cảnh báo; **không tự động san lịch** (leveling nằm Future).

## 6. API gợi ý

| Method | Endpoint | Mô tả |
|---|---|---|
| POST | `/api/v1/plans/{id}/tasks/{taskId}/resources` | Gán resource |
| PUT | `/api/v1/resource-allocations/{id}` | Sửa allocation |
| DELETE | `/api/v1/resource-allocations/{id}` | Gỡ resource |
| GET | `/api/v1/resources/{resourceId}/workload?from&to&granularity` | Workload |
| GET | `/api/v1/plans/{id}/workload` | Workload theo plan |
| GET | `/api/v1/resources/overview?from&to` | Tổng hợp over-allocation (portfolio) |

## 7. Quy tắc chốt (2026-08-07)

1. `allocation_percent` trong [0, 100]; > 100 không cho (khác đề xuất cũ) — over-allocation phát sinh do chồng nhiều task/resource, không cho khai báo trực tiếp > 100.
2. Gán resource cho summary: **CHO PHÉP gán "đại diện"** nhưng **KHÔNG tính vào workload** (docs/planning/03 mục 14 #8).
3. Resource EXTERNAL tính vào workload nhưng capacity = ∞ (không over).
4. TEAM loại khỏi v1 (đã chốt mục 2).
5. Task 100% mà có 2 user gán 50% → tổng 100% đạt capacity (đúng).
6. Nếu task AUTO thay đổi lịch → workload tự cập nhật khi recalc (không lưu workload).