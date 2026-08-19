# 07 — Ma trận traceability: Requirement → Use Case → API → Test Case

> Dự án: PM Daily Work Management
> Quy tắc: mỗi dòng thể hiện mối liên kết từ yêu cầu (FR/BR) → Use Case → API → Test Case.
> Cột "Test Case" được điền ở Prompt 20 (viết test case) — để trống cho đến lúc đó.
> Nguồn FR: `docs/02-functional-requirements.md` | Nguồn UC/AC: `docs/use-cases/UC-*.md`, `docs/06-acceptance-criteria.md` | Nguồn API: `docs/api/*` (Prompt 06).

## 1. Auth & User

| FR | UC | API dự kiến | Test Case (Prompt 20) |
|---|---|---|---|
| FR-AUTH-01 | UC-001 | POST /api/v1/auth/login | — |
| FR-AUTH-02 | UC-001 | POST /api/v1/auth/refresh | — |
| FR-AUTH-03 | UC-001 | POST /api/v1/auth/logout | — |
| FR-AUTH-04 | UC-001 | GET /api/v1/auth/me | — |
| FR-AUTH-05 | UC-001 | PUT /api/v1/auth/change-password | — |
| FR-AUTH-06 | UC-001 | POST /api/v1/auth/{userId}/reset-password | — |
| FR-USER-01 | UC-001 | CRUD /api/v1/users | — |
| FR-USER-02 | UC-001 | CRUD /api/v1/roles, /api/v1/permissions | — |

## 2. Dashboard

| FR | UC | API dự kiến | Test Case (Prompt 20) |
|---|---|---|---|
| FR-DASH-01 | UC-002 | GET /api/v1/dashboard | — |

## 3. Project & Member

| FR | UC | API dự kiến | Test Case (Prompt 20) |
|---|---|---|---|
| FR-PROJ-01 | UC-003 | POST /api/v1/projects | — |
| FR-PROJ-02 | UC-003 | PUT /api/v1/projects/{id} | — |
| FR-PROJ-03 | UC-003 | GET /api/v1/projects/{id} | — |
| FR-PROJ-04 | UC-003 | GET /api/v1/projects | — |
| FR-PROJ-05 | UC-003 | DELETE /api/v1/projects/{id} | — |
| FR-PROJ-06 | UC-004 | POST/DELETE/PUT /api/v1/projects/{id}/members... | — |
| FR-PROJ-07 | UC-004 | GET /api/v1/projects/{id}/members | — |

## 4. Task

| FR | UC | API dự kiến | Test Case (Prompt 20) |
|---|---|---|---|
| FR-TASK-01 | UC-005 | POST /api/v1/tasks | — |
| FR-TASK-02 | UC-005 | PUT /api/v1/tasks/{id} | — |
| FR-TASK-03 | UC-005 | GET /api/v1/tasks/{id} | — |
| FR-TASK-04 | UC-005 | GET /api/v1/tasks | — |
| FR-TASK-05 | UC-005 | DELETE /api/v1/tasks/{id} | — |
| FR-TASK-06 | UC-005 | PUT /api/v1/tasks/{id} (assignee) | — |
| FR-TASK-07 | UC-005 | PATCH /api/v1/tasks/{id}/status | — |
| FR-TASK-08 | UC-005 | PATCH /api/v1/tasks/{id}/progress | — |
| FR-TASK-09 | UC-005 | PATCH /api/v1/tasks/{id}/status | — |
| FR-TASK-10 | UC-005 | POST/GET /api/v1/tasks/{id}/comments | — |
| FR-TASK-11 | UC-005 | POST /api/v1/tasks/{id}/attachments | — |
| FR-TASK-12 | UC-005 | POST /api/v1/tasks (parentTaskId) | — |
| FR-TASK-13 | UC-005 | GET /api/v1/tasks/{id}/history | — |
| FR-TASK-14 | UC-005 | GET /api/v1/tasks/my-tasks | — |
| FR-TASK-15 | UC-005 | GET /api/v1/tasks/today | — |
| FR-TASK-16 | UC-005 | GET /api/v1/tasks/overdue | — |
| FR-TASK-17 | UC-005 | GET /api/v1/tasks/export | — |

## 5. Meeting & Action Item

| FR | UC | API dự kiến | Test Case (Prompt 20) |
|---|---|---|---|
| FR-MEET-01 | UC-006 | POST /api/v1/meetings | — |
| FR-MEET-02 | UC-006 | PUT /api/v1/meetings/{id} | — |
| FR-MEET-03 | UC-006 | GET /api/v1/meetings/{id} | — |
| FR-MEET-04 | UC-006 | GET /api/v1/meetings | — |
| FR-MEET-05 | UC-006 | PUT /api/v1/meetings/{id} | — |
| FR-MEET-06 | UC-006 | GET /api/v1/meetings/today | — |
| FR-MEET-07 | UC-006 | DELETE /api/v1/meetings/{id} | — |
| FR-AI-01 | UC-007 | POST /api/v1/action-items | — |
| FR-AI-02 | UC-007 | PUT /api/v1/action-items/{id} | — |
| FR-AI-03 | UC-007 | POST /api/v1/action-items/{id}/convert-to-task | — |
| FR-AI-04 | UC-007 | GET /api/v1/action-items | — |

## 6. Risk / Issue / Milestone

| FR | UC | API dự kiến | Test Case (Prompt 20) |
|---|---|---|---|
| FR-RISK-01 | UC-008 | POST /api/v1/risks | — |
| FR-RISK-02 | UC-008 | PUT /api/v1/risks/{id} | — |
| FR-RISK-03 | UC-008 | GET /api/v1/risks | — |
| FR-RISK-04 | UC-008 | DELETE /api/v1/risks/{id} | — |
| FR-RISK-05 | UC-008 | POST /api/v1/risks/{id}/convert-to-issue | — |
| FR-ISS-01 | UC-009 | POST /api/v1/issues | — |
| FR-ISS-02 | UC-009 | PUT /api/v1/issues/{id} | — |
| FR-ISS-03 | UC-009 | GET /api/v1/issues | — |
| FR-ISS-04 | UC-009 | DELETE /api/v1/issues/{id} | — |
| FR-MIL-01 | UC-010 | POST /api/v1/milestones | — |
| FR-MIL-02 | UC-010 | PUT /api/v1/milestones/{id} | — |
| FR-MIL-03 | UC-010 | GET /api/v1/milestones | — |
| FR-MIL-04 | UC-010 | DELETE /api/v1/milestones/{id} | — |
| FR-DEC-01 | UC-006 (biên bản) | (qua meeting/notes) | — |

## 7. Notification / Report / Audit

| FR | UC | API dự kiến | Test Case (Prompt 20) |
|---|---|---|---|
| FR-NOTIF-01 | UC-011 | GET /api/v1/notifications | — |
| FR-NOTIF-02 | UC-011 | PATCH /api/v1/notifications/{id}/read, /read-all | — |
| FR-NOTIF-03 | UC-011 | (scheduled job — nội bộ) | — |
| FR-REP-01 | UC-012 | GET /api/v1/reports/tasks-by-status | — |
| FR-REP-02 | UC-012 | GET /api/v1/reports/tasks-by-assignee | — |
| FR-REP-03 | UC-012 | GET /api/v1/reports/overdue-tasks | — |
| FR-REP-04 | UC-012 | GET /api/v1/reports/project-progress | — |
| FR-REP-05 | UC-012 | GET /api/v1/reports/risk-issue | — |
| FR-REP-06 | UC-012 | GET /api/v1/reports/{type}/export | — |
| FR-AUD-01 | UC-013 | GET /api/v1/audit-logs | — |

## 8. Business rules → FR/UC

| BR | FR liên quan | UC liên quan |
|---|---|---|
| BR-AUTH-01..09 | FR-AUTH-01..06, FR-USER-01 | UC-001 |
| BR-PROJ-01..10 | FR-PROJ-01..07 | UC-003, UC-004 |
| BR-TASK-01..18 | FR-TASK-01..17 | UC-005 |
| BR-MEET-01..06 | FR-MEET-01..07 | UC-006 |
| BR-AI-01..04 | FR-AI-01..04 | UC-007 |
| BR-RISK-01..05 | FR-RISK-01..05 | UC-008 |
| BR-ISS-01..04 | FR-ISS-01..04 | UC-009 |
| BR-MIL-01..03 | FR-MIL-01..04 | UC-010 |
| BR-NOTIF-01..04 | FR-NOTIF-01..03 | UC-011 |
| BR-REP-01..04 | FR-REP-01..06 | UC-002, UC-012 |
| BR-GEN-01..09 | Toàn bộ | Toàn bộ |

## 8b. Project Planning (v1.1) — có ma trận riêng

Phân hệ PROJECT PLANNING có traceability hoàn chỉnh riêng tại **`docs/planning/15-requirement-traceability.md`** (BR→FR→Module→DB→API→AC), use case tại **`docs/planning/05`**, acceptance tại **`docs/planning/14`**.

Tóm tắt mapping FR ↔ API ↔ AC (chi tiết `docs/api/13-planning-api.md` §4):

| PLN-FR | API | PLN-AC |
|---|---|---|
| PLN-FR-PLAN-*, MASTER-* | `/api/v1/plans*` | PLN-AC-PLAN-*, MASTER-* |
| PLN-FR-VERSION-*, BASE-* | `/versions`, `/baselines` | PLN-AC-VERSION-*, BASE-* |
| PLN-FR-WBS-* | `/plans/{id}/tasks` | PLN-AC-WBS-* |
| PLN-FR-DEP-* | `.../dependencies` | PLN-AC-DEP-* |
| PLN-FR-CAL-* | `/plan-calendars` | PLN-AC-CAL-* |
| PLN-FR-SCHED-*, CP-* | `/recalc`, `/critical-path` | PLN-AC-SCHED-*, CP-* |
| PLN-FR-RES-* | `/resources`, `/workload` | PLN-AC-RES-* |
| PLN-FR-CHG-* | `/change-histories`, `/change-suggestions` | PLN-AC-CHG-* |
| PLN-FR-LINK-* | `.../links` | PLN-AC-LINK-* |
| PLN-FR-TPL-* | `/plan-templates` | PLN-AC-TPL-* |
| PLN-FR-PORT-* | `/portfolio` | PLN-AC-PORT-* |

## 9. NFR → Kiểm chứng

| NFR | Kiểm chứng ở giai đoạn | UC liên quan |
|---|---|---|
| NFR-PERF-01..05 | Prompt 14 (query test), Prompt 26 (review) | UC-002, UC-012 |
| NFR-SEC-01..08 | Prompt 09 (auth test), Prompt 26 (security review) | UC-001 |
| NFR-REL-02 | Prompt 11 (concurrent test) — ✔ đã làm: `concurrent_create_differentProjects_sequentialCodesPerProject` (mã task theo project không trùng, tuần tự) | UC-003..010 |
| NFR-TZ-01..04 | Prompt 11/23 (timezone test) — Prompt 11 đã làm phần task: `LocalDate.now()` cho due/today/overdue; còn lại Prompt 23 | UC-002, UC-005, UC-006 |
| NFR-LOG-02 | Prompt 14 (audit), Prompt 26 | UC-013 |

## 10. Độ phủ — tự kiểm tra (Prompt 03)

- [x] Không trùng yêu cầu: mỗi FR ánh xạ đúng 1 UC chính.
- [x] Đủ CRUD: project, task, meeting, risk, issue, milestone, action item đều có create/read/update/delete.
- [x] Có tìm kiếm, lọc, phân trang: FR-PROJ-04, FR-TASK-04, FR-MEET-04, FR-RISK-03, FR-ISS-03, FR-MIL-03.
- [x] Có dữ liệu rỗng: AC-002-05, AC-005-20, AC-006-12, AC-011-09, AC-012-06, AC-013-04.
- [x] Có lỗi phân quyền: AC-002-06, AC-003-04/07, AC-004-05, AC-005-14, AC-006-11, AC-008-09, AC-009-08, AC-010-09, AC-011-08, AC-012-09, AC-013-05.
- [x] Có lỗi dữ liệu không hợp lệ: validation từng trường ở mọi UC (mục 11).
- [x] Có concurrent update: AC-003-06, AC-005-02/13, AC-006-06, AC-007-08, AC-008-06, AC-009-05, AC-010-05.
- [x] Có dữ liệu đã xóa mềm: AC-003-08, AC-004-06, AC-005-12, AC-006-10, AC-007-(n/a), AC-008-07, AC-009-06, AC-010-07.
- [x] Token hết hạn / refresh: AC-001-05..07, AC-001-15.
