# API 13 — Project Planning

> Dự án: PM Daily Work Management | Trạng thái: Draft (cho giai đoạn PLN-BE)
> Nguồn: Prompt Project Planning Requirement, `docs/planning/02` (PLN-FR), `docs/planning/03` (PLN-RULE), `docs/planning/04` (phân quyền), `docs/planning/14` (PLN-AC)
> **Quy ước chung**: prefix `/api/v1`, JSON ISO-8601 UTC, pagination `page/size/sort`, error response theo `docs/design/05-error-handling-design.md`, phân quyền theo `docs/planning/04`.

## 1. Mô tả tổng quan

Phân hệ Project Planning: lập kế hoạch dự án (Master/Detail), WBS, dependency, working calendar, auto scheduling, critical path, resource planning & workload, baseline/version, template, portfolio. Quyền theo `plan:*` (docs/planning/04).

## 2. Danh sách endpoint

### 2.1 Plan

| Method | Endpoint | Phân quyền | Mục đích | FR |
|---|---|---|---|---|
| GET | `/api/v1/plans` | `plan:view` | Danh sách plan (filter project/type/status) | PLN-FR-PLAN-* |
| POST | `/api/v1/plans` | `plan:create` | Tạo plan (Master/Detail) | PLN-FR-PLAN-01 |
| GET | `/api/v1/plans/{id}` | `plan:view` | Chi tiết plan + tree roll-up | PLN-FR-PLAN-01 |
| PUT | `/api/v1/plans/{id}` | `plan:update` | Sửa plan (cấu hình, calendar) | PLN-FR-PLAN-02 |
| DELETE | `/api/v1/plans/{id}` | `plan:delete` | Xóa mềm plan | PLN-FR-PLAN-03 |
| POST | `/api/v1/plans/{id}/submit` | `plan:update` | DRAFT → SUBMITTED | PLN-FR-PLAN-02 |
| POST | `/api/v1/plans/{id}/approve` | `plan:approve` | SUBMITTED → APPROVED | PLN-FR-PLAN-02 |
| POST | `/api/v1/plans/{id}/activate` | `plan:approve` | APPROVED → ACTIVE | PLN-FR-PLAN-02 |
| GET | `/api/v1/plans/{id}/gantt` | `plan:view` | Dữ liệu Gantt (tree + dep + resource + critical) | PLN-FR-WBS-*, LINK-* |
| GET | `/api/v1/plans/{id}/critical-path` | `plan:view` | Critical path (CPM) | PLN-FR-CP-* |
| POST | `/api/v1/plans/{id}/recalc` | `plan:schedule` | Trigger scheduling engine | PLN-FR-SCHED-* |

### 2.2 WBS & Task

| Method | Endpoint | Phân quyền | Mục đích |
|---|---|---|---|
| POST | `/api/v1/plans/{id}/tasks` | `plan:update` | Thêm task (leaf/child/milestone/external) |
| PUT | `/api/v1/plans/{id}/tasks/{taskId}` | `plan:update` | Sửa task + renumber + recalc |
| DELETE | `/api/v1/plans/{id}/tasks/{taskId}` | `plan:update` | Xóa task (confirm tree nếu summary) |
| PUT | `/api/v1/plans/{id}/tasks/{taskId}/move` | `plan:update` | Move (up/down/to-parent/indent/outdent) |

### 2.3 Dependency

| Method | Endpoint | Phân quyền | Mục đích |
|---|---|---|---|
| POST | `/api/v1/plans/{id}/tasks/{taskId}/dependencies` | `plan:update` | Tạo dependency (validator cycle) |
| DELETE | `/api/v1/plans/{id}/tasks/{taskId}/dependencies/{depId}` | `plan:update` | Xóa dependency |
| GET | `/api/v1/plans/{id}/tasks/dependencies` | `plan:view` | Danh sach dependency cua plan (cho UI Dependency Editor - bo sung 2026-08-10, PLN-FE-03) |

### 2.4 Calendar

| Method | Endpoint | Phân quyền | Mục đích |
|---|---|---|---|
| POST | `/api/v1/plan-calendars` | `plan:update` (ADMIN org) | Tạo calendar |
| GET | `/api/v1/plan-calendars` | `plan:view` | Danh sách (org + project) |
| PUT | `/api/v1/plan-calendars/{id}` | `plan:update` | Sửa cấu hình (working days/exceptions) |
| DELETE | `/api/v1/plan-calendars/{id}` | `plan:update` | Xóa (nếu không được tham chiếu) |
| POST | `/api/v1/plan-calendars/{id}/exceptions` | `plan:update` | Thêm exception (holiday/work) |
| GET | `/api/v1/plans/{id}/calendar` | `plan:view` | Calendar hiệu dung của plan |

### 2.5 Version & Baseline

| Method | Endpoint | Phân quyền | Mục đích |
|---|---|---|---|
| POST | `/api/v1/plans/{id}/versions` | `plan:version` | Tạo version (snapshot) |
| GET | `/api/v1/plans/{id}/versions` | `plan:view` | Danh sách version |
| GET | `/api/v1/plans/{id}/versions/{versionNo}/diff` | `plan:view` | So sánh version |
| POST | `/api/v1/plans/{id}/baselines` | `plan:baseline` | Tạo baseline (chỉ APPROVED) |
| GET | `/api/v1/plans/{id}/baselines` | `plan:view` | Danh sách baseline |
| GET | `/api/v1/plans/{id}/baselines/{num}/variance` | `plan:view` | Variance so với current |
| DELETE | `/api/v1/plans/{id}/baselines/{num}` | `plan:baseline` | Xóa baseline (soft) |

### 2.6 Resource

| Method | Endpoint | Phân quyền | Mục đích |
|---|---|---|---|
| POST | `/api/v1/plans/{id}/tasks/{taskId}/resources` | `plan:resource` | Gán resource |
| PUT | `/api/v1/resource-allocations/{id}` | `plan:resource` | Sửa allocation (allocation percent) |
| DELETE | `/api/v1/resource-allocations/{id}` | `plan:resource` | Gỡ resource |
| GET | `/api/v1/resources/{resourceId}/workload?from&to&granularity` | `plan:view` | Workload 1 resource |
| GET | `/api/v1/plans/{id}/workload` | `plan:view` | Workload theo plan |
| GET | `/api/v1/plans/{id}/resources` | `plan:view` | Danh sách resource assignment của plan (cho UI Resource tab - bổ sung 2026-08-10, PLN-FE-06) |
| PUT | `/api/v1/resources/{resourceId}/capacity` | `plan:resource` | Cập nhật capacity |
| GET | `/api/v1/resources/overview` | `plan:view` | Over-allocation cross-plan |

### 2.7 Template

| Method | Endpoint | Phân quyền | Mục đích |
|---|---|---|---|
| GET | `/api/v1/plan-templates` | `plan:view` | Danh sách template (PUBLISHED) |
| POST | `/api/v1/plan-templates` | `plan:template` | Tạo template (ADMIN) |
| PUT | `/api/v1/plan-templates/{id}` | `plan:template` | Sửa template → version+1 |
| DELETE | `/api/v1/plan-templates/{id}` | `plan:template` | Xóa mềm |
| POST | `/api/v1/plan-templates/{id}/clone` | `plan:template` | Clone |
| POST | `/api/v1/plans/from-template` | `plan:create` | Tạo plan từ template |

### 2.8 plan_links

| Method | Endpoint | Phân quyền | Mục đích |
|---|---|---|---|
| POST | `/api/v1/plans/{id}/tasks/{taskId}/links` | `plan:link` | Tạo link tới execution/issue/risk |
| GET | `/api/v1/plans/{id}/tasks/{taskId}/links` | `plan:view` | Danh sách link |
| DELETE | `/api/v1/links/{id}` | `plan:link` | Gỡ link |

### 2.9 Change history

| Method | Endpoint | Phân quyền | Mục đích |
|---|---|---|---|
| GET | `/api/v1/plans/{id}/change-histories` | `plan:view` | Danh sách change history |
| GET | `/api/v1/plans/{id}/change-suggestions` | `plan:view` | Danh sách change suggestion của plan (cho UI Change tab - bổ sung 2026-08-11, PLN-FE-08) |
| POST | `/api/v1/plans/{id}/change-suggestions` | `plan:change` | Tạo suggestion |
| POST | `/api/v1/change-suggestions/{id}/accept` | `plan:change` | Duyệt suggestion |
| POST | `/api/v1/change-suggestions/{id}/reject` | `plan:change` | Từ chối |

### 2.10 Portfolio

| Method | Endpoint | Phân quyền | Mục đích |
|---|---|---|---|
| GET | `/api/v1/portfolio` | `plan:view` | Timeline đa dự án + summary |

## 3. Chi tiết endpoint

### 3.1 POST `/api/v1/plans`

- **Phân quyền**: `plan:create` (ADMIN, PM dự án). Audit: có.
- **Request body** — `PlanCreateRequest`:

| Field | Type | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `projectId` | uuid | ✔ | Project tồn tại, chưa xóa mềm |
| `planCode` | string | ✔ | Unique, 3–50 ký tự (PLN-RULE-PLAN-01) |
| `planName` | string | ✔ | ≤ 200 ký tự |
| `planType` | enum | ✔ | `MASTER, DETAIL, TEMPLATE_INSTANCE` |
| `parentPlanId` | uuid | — | Bắt buộc khi DETAIL; cha phải MASTER |
| `calendarId` | uuid | — | Mặc định organization calendar |
| `plannedStart` | date | — | Ngày kế hoạch bắt đầu |
| `description` | string | — | — |

- **Response `201`** — `PlanResponse` (kèm trạng thái DRAFT, version 1 tự tạo).
- **Lỗi**: `400 VALIDATION_ERROR`; `409 DUPLICATE` (planCode); `400 INVALID_PARENT_DEPTH` (detail lồng detail); `403 ACCESS_DENIED`; `404 NOT_FOUND`.

### 3.2 POST `/api/v1/plans/{id}/tasks`

- **Phân quyền**: `plan:update` (ADMIN, PM dự án). Audit: có.
- **Request body** — `PlanTaskRequest`:

| Field | Type | Not null | Ghi chú |
|---|---|---|---|
| `parentId` | uuid | — | Summary/PHASE/WORK_PACKAGE hợp lệ |
| `taskCode` | string | ✔ | Unique trong plan |
| `taskName` | string | ✔ | ≤ 200 |
| `taskType` | enum | ✔ | PHASE, SUMMARY_TASK, WORK_PACKAGE, TASK, MILESTONE, EXTERNAL_TASK |
| `startDate` / `finishDate` | date | — | MANUAL |
| `scheduleMode` | enum | — | AUTO (mặc định) / MANUAL |
| `constraintType` | enum | — | FIXED_DATE, START_NO_EARLIER_THAN, REMOVE_SCHEDULE... |
| `plannedEffortMinutes` | int | — | ≥ 0 |
| `percentComplete` | int | — | 0–100 |
| `status`, `priority`, `ownerId` | | — | |

- Sau khi tạo: renumber wbsCode, nếu AUTO → recalc downstream.
- **Lỗi**: `400 CIRCULAR_PARENT`; `400 INVALID_PARENT` (leaf làm cha); `409 CONFLICT` (version).

### 3.3 GET `/api/v1/plans/{id}/gantt`

- **Response `200`** — GanttData (tree + dependencies + resources + critical + baseline):

```json
{
  "plan": { "id": "...", "planType": "MASTER", "status": "ACTIVE" },
  "tasks": [
    {
      "id": "...", "parentId": null, "wbsCode": "1", "taskName": "Phase 1 - Khởi tạo",
      "taskType": "PHASE", "start": "2026-08-01", "finish": "2026-08-15",
      "durationMinutes": 9600, "plannedEffortMinutes": 4800, "percentComplete": 100,
      "scheduleMode": "AUTO", "isCritical": false,
      "baseline": { "start": "2026-08-01", "finish": "2026-08-14" },
      "resources": [ { "resourceId": "...", "allocationPercent": 50 } ]
    }
  ],
  "dependencies": [ { "from": "t1", "to": "t2", "type": "FS", "lagMinutes": 0 } ],
  "warnings": []
}
```

- **Lỗi**: `403 ACCESS_DENIED` (ngoài phạm vi project), `404`.

### 3.4 POST `/api/v1/plans/{id}/baselines`

- **Tiền điều kiện**: plan.status = APPROVED (PLN-RULE-BASE-01).
- **Response `201`** — baseline info (baselineNum, captured snapshot tasks).
- **Lỗi**: `400 PLAN_NOT_APPROVED`; `409 PLAN_CHANGED`.

### 3.5 GET `/api/v1/portfolio`

- **Response `200`** — cấu trúc:

| Field | Type | Ghi chú |
|---|---|---|
| `projects` | list | Mỗi: id, code, name, pm, status, start/finish, progress, delayDays, isOverAllocated |

### 3.6 GET `/api/v1/plans/{id}/critical-path`

- **Phân quyền**: `plan:view`. Audit: không (tính live mỗi lần — PLN-RULE-CP-04).
- **Response `200`** — `CriticalPathResult` (docs/planning/09 muc 3):

```json
{
  "planId": "...", "plannedStart": "2026-08-03", "plannedFinish": "2026-08-10",
  "totalDurationMinutes": 2880, "thresholdMinutes": 0, "criticalTaskCount": 3,
  "tasks": [
    {
      "taskId": "...", "wbsCode": "1", "taskName": "Task A", "taskType": "TASK",
      "earlyStart": "2026-08-03", "earlyFinish": "2026-08-04",
      "lateStart": "2026-08-03", "lateFinish": "2026-08-04",
      "totalFloatMinutes": 0, "freeFloatMinutes": 0,
      "isCritical": true, "criticalPathId": 1
    }
  ]
}
```

- Task MILESTONE/MANUAL tính như task thường (PLN-RULE-CP-03); task không có ngày lập lịch bị bỏ qua; summary lấy từ con (không tính trực tiếp).
- **Lỗi**: `403 ACCESS_DENIED` (thiếu `plan:view`), `404 NOT_FOUND`.

### 3.7 Resource Planning & Workload (docs/planning/10 muc 6)

- **Gán**: `POST /api/v1/plans/{id}/tasks/{taskId}/resources` — **`plan:resource`**, audit `PLAN_RESOURCE_ASSIGNED`.

```json
{ "resourceType": "USER", "resourceId": "<uuid users/roles|tự đặt cho EXTERNAL>",
  "allocationPercent": 60, "roleOnTask": "Backend Dev",
  "startDate": null, "endDate": null, "plannedEffortMinutes": null }
```

- `allocationPercent` ∈ [1,100] (PLN-RULE-RES-01; TEAM → 400 — PLN-AC-RES-07); USER/ROLE phải tồn tại (404); trùng (task, type, resource) → 409. Response kèm `overAllocation`/`utilizationPercent` tính lại demand của resource trong cửa sổ allocation (PLN-RULE-RES-03 — chỉ cảnh báo, không leveling).
- **Workload**: `GET /resources/{id}/workload` + `GET /plans/{id}/workload` + `GET /resources/overview` (`plan:view`) — `from,to` bắt buộc, `granularity=DAY|WEEK|MONTH`.

```json
{ "resourceType": "USER", "resourceId": "...", "resourceName": "Nguyễn Văn A",
  "granularity": "DAY", "from": "2026-08-03", "to": "2026-08-04",
  "totalDemandMinutes": 960, "totalCapacityMinutes": 960,
  "totalUtilizationPercent": 100.0, "overAllocation": false,
  "buckets": [ { "date": "2026-08-03", "demandMinutes": 480, "capacityMinutes": 480,
                 "utilizationPercent": 100.0, "overAllocation": false } ] }
```

- Demand 1 ngày = `allocationPercent% × durationMinutes / workingDays(task)` theo calendar của plan; task summary gán được nhưng **không** tính (muc 7 #2); EXTERNAL: `capacityMinutes` = null, không bao giờ over (PLN-AC-RES-06). Capacity mặc định 100% × 480 phút/ngày làm việc, override theo `resource_capacities`.
- **Capacity**: `PUT /api/v1/resources/{resourceId}/capacity` (`plan:resource`) — upsert theo khóa (type, id, startDate): body `{"resourceType":"USER","capacityPercent":50,"startDate":"2026-08-03","endDate":null,"source":"ORG"}`.
- **Gỡ/sửa**: `PUT|DELETE /api/v1/resource-allocations/{id}` (`plan:resource`) — audit `PLAN_RESOURCE_UPDATED/REMOVED`.
- **Overview**: chỉ đếm allocation của plan APPROVED/ACTIVE (docs/planning/10 muc 4); member thiếu `plan:resource` chỉ xem workload của chính mình (PLN-AC-RES-04), còn lại 403.

## 4. Traceability

| Module API | PLN-UC | PLN-AC |
|---|---|---|
| /plans | PLN-UC-01, 06, 14 | PLN-AC-PLAN-*, MASTER-* |
| /versions, /baselines | PLN-UC-02, 09 | PLN-AC-VERSION-*, BASE-* |
| /plans/{id}/tasks | PLN-UC-03 | PLN-AC-WBS-* |
| /dependencies | PLN-UC-04 | PLN-AC-DEP-* |
| /plan-calendars | PLN-UC-05 | PLN-AC-CAL-* |
| /recalc, /critical-path | PLN-UC-06, 07 | PLN-AC-SCHED-*, CP-* |
| /resources, /workload | PLN-UC-08 | PLN-AC-RES-* |
| /change-histories, /change-suggestions | PLN-UC-10 | PLN-AC-CHG-* |
| /tasks/{id}/links | PLN-UC-11, 15 | PLN-AC-LINK-* |
| /plan-templates | PLN-UC-12 | PLN-AC-TPL-* |
| /portfolio | PLN-UC-13 | PLN-AC-PORT-* |
| /plans/{id}/gantt | PLN-UC-01..07 (UI) | PLN-AC-GANTT-* (docs/planning/13) |

## 5. Điểm cần xác nhận

1. Async recalc job hay sync trong request (PLN-RULE-SCHED — docs/planning/08 §4).
2. Lag âm cho phép hay chặn (docs/planning/03 PLN-RULE-SCHED-*).
3. Baseline: cho phép xóa baseline hay bất biến tuyệt đối (docs/planning/11 §6).
4. Endpoint `plan:update` có nên tách riêng quyền `plan:recalc` cho UI tự động hay dùng chung quyền trigger (đề xuất: `plan:schedule`).

> Chi tiết DTO đầy đủ & OpenAPI: bổ sung trong bước implement (giống các API module khác — `docs/api/openapi.yaml` mở rộng).