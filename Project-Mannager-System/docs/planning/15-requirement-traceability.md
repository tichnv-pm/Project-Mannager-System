# Planning 15 — Requirement Traceability Matrix (Project Planning)

> Dự án: PM Daily Work Management — phân hệ PROJECT PLANNING
> Trạng thái: Draft — nguồn Prompt Project Planning Requirement.
> Quy ước ID: `PLN-BR-*` (business), `PLN-FR-*` (functional), `PLN-NFR-*` (phi chức năng), `PLN-RULE-*` (business rules), `PLN-AC-*` (acceptance).

## 1. Ma trận Business Requirement → Functional Requirement

| BR (BR-GEN/BR-..) | FR liên quan | Trạng thái |
|---|---|---|
| PLN-BR-GEN-01 Lập kế hoạch tổng thể | PLN-FR-PLAN-01..08, PLN-FR-MASTER-01..03 | ✔ |
| PLN-BR-GEN-02 Quản lý WBS | PLN-FR-WBS-01..09 | ✔ |
| PLN-BR-GEN-03 Lập lịch tự động | PLN-FR-SCHED-01..06, PLN-FR-DEP-*, PLN-FR-CAL-* | ✔ |
| PLN-BR-GEN-04 Critical path | PLN-FR-CP-01..05 | ✔ |
| PLN-BR-GEN-05 Resource planning & workload | PLN-FR-RES-01..08 | ✔ |
| PLN-BR-GEN-06 Baseline & version | PLN-FR-VERSION-*, PLN-FR-BASE-* | ✔ |
| PLN-BR-GEN-07 Template | PLN-FR-TPL-01..05 | ✔ |
| PLN-BR-GEN-08 Portfolio | PLN-FR-PORT-01..06 | ✔ |

## 2. Ma trận FR → Module → Architecture → API → Test

| ID | Chức năng | Module (back) | Endpoint liên quan (docs/api/13) | Test (PLN-AC / JUnit) |
|---|---|---|---|---|
| PLN-FR-PLAN-01 | CRUD plan | `plan` | `/plans` | PLN-AC-PLAN-01..06 |
| PLN-FR-PLAN-02 | State machine | `plan` | POST `/submit|approve|activate` | PLN-AC-PLAN-05 |
| PLN-FR-PLAN-03 | Master–Detail | `plan` | `/plans` (parentPlanId) | PLN-AC-PLAN-03, MASTER-* |
| PLN-FR-PLAN-04 | Calendar (default) | `schedule` | GET/PUT `/plans/{id}` | PLN-AC-CAL-* |
| PLN-FR-VERSION-* | Version/snapshot | `version` | `/versions` | PLN-AC-VERSION-* |
| PLN-FR-WBS-* | WBS tree | `wbs` | `/plans/{id}/tasks` | PLN-AC-WBS-* |
| PLN-FR-DEP-* | Dependency | `dependency` | `/tasks/{id}/dependencies` | PLN-AC-DEP-* |
| PLN-FR-CAL-* | Working calendar | `calendar` | `/calendars` | PLN-AC-CAL-* |
| PLN-FR-SCHED-* | Scheduling engine | `schedule` | POST `/recalc` | PLN-AC-SCHED-* |
| PLN-FR-CP-* | Critical path | `criticalpath` | GET `/critical-path` | PLN-AC-CP-* |
| PLN-FR-RES-* | Resource workload | `resource` | `/resources`, `/workload` | PLN-AC-RES-* |
| PLN-FR-BASE-* | Baseline & variance | `baseline` | `/baselines`, `/variance` | PLN-AC-BASE-* |
| PLN-FR-CHG-* | Change history | `change` | `/change-histories` | PLN-AC-CHG-* |
| PLN-FR-LINK-* | plan_links | `link` | `/links` | PLN-AC-LINK-* |
| PLN-FR-TPL-* | Template | `template` | `/templates` | PLN-AC-TPL-* |
| PLN-FR-PORT-* | Portfolio | `portfolio` | `/portfolio` | PLN-AC-PORT-* |
| PLN-FR-MASTER-* | Master roll-up | `master` | GET `/plans/{id}` | PLN-AC-MASTER-* |

## 3. Ma trận FR → DB bảng (docs/database)

| Bảng | FR |
|---|---|
| `project_plans` | PLAN-*, MASTER-* |
| `plan_versions` | VERSION-* |
| `plan_tasks` | WBS-*, SCHED-*, RES-*, BASE-* (is_critical) |
| `plan_task_dependencies` | DEP-*, SCHED-* |
| `plan_calendars` | CAL-* |
| `plan_calendar_working_days`, `plan_calendar_exceptions` | CAL-* |
| `plan_task_resources` | RES-* |
| `plan_baselines`, `plan_baseline_tasks` | BASE-* |
| `plan_links` | LINK-* |
| `plan_change_requests`, `plan_change_histories` | CHG-* |
| `plan_templates`, `plan_template_tasks` | TPL-* |
| `portfolios`, `portfolio_projects`, `resource_capacities` | PORT-*, RES-* |

## 4. Ma trận NFR (non-functional)

| NFR | Nội dung | Kiểm chứng |
|---|---|---|
| PLN-NFR-GEN-01 | Hiệu năng plan ≤ 500 task recalc < 2s | JUnit perf test |
| PLN-NFR-GEN-02 | Gantt render 500 task mượt | e2e Playwright (future) |
| PLN-NFR-GEN-03 | Bất biến baseline | Integration test (fail nếu có UPDATE) |
| PLN-NFR-GEN-04 | Audit log mọi change về plan | unit test AOP |
| PLN-NFR-GEN-05 | Timezone UTC lưu trữ timestamptz | database test |

## 5. Ánh xạ kiến trúc chung (đã chốt ở v1.0)

- API chỉ DTO (không entity) — rule chung.
- Soft delete (archive thay vì xóa) — rule chung.
- Optimistic lock (version) — rule chung.
- @PreAuthorize plan:* — permission (docs/04).

## 5b. Trạng thái triển khai backend (PLN-BE)

| Bước | Module | Endpoint | Trạng thái | Ghi chú |
|---|---|---|---|---|
| PLN-BE-01 | `plan` | GET/POST `/plans`, GET/PUT/DELETE `/plans/{id}`, POST `/plans/{id}/submit\|approve\|activate` | ✔ 2026-08-07 | PlanIntegrationTest 2 tests; mvn clean verify/package 233 tests PASS |
| PLN-BE-02 | `plan-task` (WBS) | `/plans/{id}/tasks` (+move) | ✔ 2026-08-07 | PlanTaskIntegrationTest 3 tests; 233 tests PASS |
| PLN-BE-03 | `plan-dependency` | `/plans/{id}/tasks/{taskId}/dependencies` (POST/DELETE) + GET `/plans/{id}/tasks/dependencies` (bổ sung 2026-08-10 cho PLN-FE-03) | ✔ 2026-08-07 | PlanDependencyIntegrationTest 7 tests (thêm test GET list — view/member/404); 277 tests PASS |
| PLN-BE-04 | `plan-calendar` | `/plan-calendars` + `/plans/{id}/calendar` | ✔ 2026-08-08 | PlanCalendarIntegrationTest 7 tests (CRUD, exception unique date, effective fallback org, xóa bị chặn khi tham chiếu, phân quyền ADMIN org); mvn clean verify/package 240 tests PASS |
| PLN-BE-05 | `scheduling` | POST `/plans/{id}/recalc` | ✔ 2026-08-08 | PlanSchedulingIntegrationTest 6 tests (forward pass FS+lag+working days, holiday + WORKING exception, MANUAL/FIXED_DATE giữ nguyên, constraint + CONSTRAINT_CONFLICT/NO_START_ANCHOR warnings, milestone + summary/plan roll-up, idempotent + phân quyền plan:schedule); mvn clean verify 246 tests PASS |
| PLN-BE-06 | `critical-path` | GET `/plans/{id}/critical-path` | ✔ 2026-08-08 | PlanCriticalPathIntegrationTest 6 tests (CPM forward/backward float, threshold 0, MILESTONE + MANUAL trong critical, recalc snapshot is_critical, no-dep = total critical, empty + phân quyền plan:view + 404); mvn clean verify/package 252 tests PASS |
| PLN-BE-07 | `plan-resource` | `/resources`, `/workload`, `/capacity` + GET `/plans/{id}/resources` (bổ sung 2026-08-10 cho PLN-FE-06) | ✔ 2026-08-08 | PlanResourceIntegrationTest 8 tests (thêm test GET list — pm/member/404); 278 tests PASS |
| PLN-BE-08 | `plan-baseline` | `/versions`, `/baselines`, `/variance` | ✔ 2026-08-08 | PlanBaselineIntegrationTest 7 tests (versionNo max+1 + chỉ 1 ACTIVE + list desc, diff durationMinutes 960→480 v2-vs-v3 + TASK_ADDED + diff mới nhất 404, baseline chỉ APPROVED (DRAFT/SUBMITTED 400), snapshot bất biến + baselineNum đơn điệu kể cả soft delete, variance start +29 ngày/finish +29/duration −480/progressDiff +50/milestone hoàn thành (MILESTONE roll-up), variance task bị xóa, phân quyền member/viewer/404); mvn clean verify/package 266 tests PASS |
| PLN-BE-09 | `plan-change` + `plan-link` | `/change-histories`, `/links` | ✔ 2026-08-08 | PlanChangeLinkIntegrationTest 8 tests (thêm GET list change-suggestions 2026-08-11 cho PLN-FE-08 — pm/member/viewer/404) (link create primary/related + duplicate 409 + 1 primary duy nhất, cặp link hợp lệ Milestone BLOCKED_BY 400 / primary chỉ EXECUTION_TASK / target 404 / project khác 400 + phân quyền member/viewer, change history chỉ ghi sau APPROVED (plannedStart/percentComplete/dependency), suggestion accept apply + reject không apply + 409 xử lý 2 lần, dual approve PM+ADMIN khi effort ≥ 10.000 phút (PENDING chờ người 2 + cùng người 409), suggestion phân quyền member 403/404/400 plan khác, delete link ghi history); mvn clean verify/package 279 tests PASS |
| PLN-BE-10 | `plan-template` + `portfolio` | `/plan-templates`, `/plan-templates/{id}`, `/plans/from-template`, `/portfolio` | ✔ 2026-08-08 | PlanTemplatePortfolioIntegrationTest 3 tests (8 built-in templates FULL_SDL 17 phases/AGILE_SPRINT/PMO_STANDARD/MAINTENANCE/INFRASTRUCTURE/MARKETING/VENDOR/DATA, copy cây task template → plan mới, aggregate portfolio active projects/progress/delayDays/criticalTaskCount/mốc chính + phân quyền); mvn clean verify/package 276 tests PASS. Gantt endpoint `/gantt` bổ sung 2026-08-11 cho PLN-FE-10 (xem PLN-FE-10 — 281 tests PASS) |

> Traceability với prompt planning: ≥ 100% AC có test; 100% FR map module; 100% FR map bảng; mảng còn dư: cross references đã chốt.

## 5c. Trạng thái triển khai frontend (PLN-FE)

| Bước | Module FE | Trang | Trạng thái | Ghi chú |
|---|---|---|---|---|
| PLN-FE-01 | `pages/planning` — plan list + editor + lifecycle | `/plans`, `/plans/{id}` | ✔ 2026-08-10 | `plan.model.ts` + `plan.service.ts` (10 test PASS); PlanListComponent (search keyword/project/type/status, pagination, cards, create/edit modal master–detail, submit/approve/activate, delete mềm); PlanDetailComponent (info, roll-up progress, master–detail children, lifecycle, edit/delete); sidebar "Kế hoạch" với `plan:view`; status-chip thêm DRAFT/SUBMITTED/APPROVED/ON_HOLD/ARCHIVED; admin catalog bổ sung 12 quyền `plan:*`; npm test 29 tests PASS + npm run build PASS (warning budget — **đã xử lý ✔ 2026-08-11**: gộp button/modal chung sang `styles.scss`) |
| PLN-FE-02 | WBS tree editor | `/plans/{id}` (tab WBS) | ✔ 2026-08-10 | `plan-wbs-editor.component.*` mới: cây WBS (PHASE/SUMMARY_TASK/WORK_PACKAGE/TASK/MILESTONE/EXTERNAL_TASK), expand/collapse; thêm task gốc/con/cùng cấp, sửa, xóa (chặn summary còn con — PLN-AC-WBS-04), move ↑↓ →(INDENT) ←(OUTDENT) với disable theo sibling; wbsCode, type badge, tag milestone/critical, progress, status chip; modal create/edit (taskCode/taskName/taskType/dates/effort/percentComplete/status/priority/scheduleMode, MILESTONE effort = 0); PlanService +5 methods task CRUD + move (17 test PASS cho service); event `changed` → re-fetch plan roll-up; npm test 35 tests PASS + npm run build PASS | |
| PLN-FE-03 | Dependency editor | `/plans/{id}` (tab Liên kết Task) | ✔ 2026-08-10 | `plan-dependency-editor.component.*` mới: form tạo liên kết FS/SS/FF/SF + lag phút (âm cho phép — PLN-RULE-DEP-04, cảnh báo lead time), predecessor/successor select từ WBS (loại trừ self — PLN-RULE-DEP-01), hiển thị lỗi BE nguyên dạng (DEPENDENCY_CYCLE/SELF_DEPENDENCY/409 trùng — PLN-AC-DEP-02); danh sách dependency kèm type badge + lag chip (âm màu cam), xóa có xác nhận; PlanService +3 methods getDependencies/createDependency/deleteDependency (20 test PASS cho service); backend bổ sung GET read-only `/plans/{id}/tasks/dependencies` (`plan:view`) + PlanDependencyIntegrationTest 7 tests (277 tests PASS) — docs/api/13 §2.3; tab mới trong `/plans/{id}`; npm test 38 tests PASS + npm run build PASS | |
| PLN-FE-04 | Calendar | `/plans/{id}` (tab Lịch làm việc) | ✔ 2026-08-10 | `plan-calendar.component.*` mới: xem calendar hiệu lực của plan (`GET /plans/{id}/calendar`) kèm nguồn gốc (trực tiếp/kế thừa org/hệ thống — PLN-AC-CAL-03), grid 7 ngày làm việc + giờ bắt đầu/kết thúc, bảng exceptions (NON_WORKING/WORKING — PLN-AC-CAL-05); quản lý calendars modal (tạo/sửa/xóa org — xóa bị chặn khi tham chiếu, toggle ngày làm việc, giờ/ngày, timezone, status, version lock), gán calendar cho plan qua `PUT /plans/{id}` (calendarId), thêm exception (date/type/note); PlanService +6 methods calendar (26 test PASS cho service); npm test 44 tests PASS + npm run build PASS | |
| PLN-FE-05 | Recalc & Critical path | `/plans/{id}` (tab Lịch trình & Găng) | ✔ 2026-08-10 | `plan-scheduling.component.*` mới: nút Recalc (`POST /plans/{id}/recalc` — `plan:schedule`, PLN-AC-SCHED-*), card kết quả (phạm vi lịch/thời lượng/task đã đặt lịch) + bảng warnings từ engine (CONSTRAINT_CONFLICT/DATE_NOT_WORKING/NEGATIVE_LAG/NO_START_ANCHOR/CYCLE_DEPENDENCY với badge màu riêng); Critical Path tự tải (`GET /plans/{id}/critical-path`: early/late dates, total/free float, chip đường găng/ngoài găng, threshold — PLN-AC-CP-*), reload sau recalc + emit `changed` cập nhật roll-up plan; PlanService +2 methods recalculatePlan/getCriticalPath (28 test PASS cho service); npm test 46 tests PASS + npm run build PASS | |
| PLN-FE-06 | Resource & Workload | `/plans/{id}` (tab Resource) | ✔ 2026-08-10 | `plan-resource.component.*` mới: gán resource USER/ROLE/EXTERNAL theo task (select thành viên dự án cho USER — PLN-RES rules, allocation % 1-100, vai trò, khoảng ngày, công sức, cảnh báo over-allocation sau khi gán); danh sách allocation của plan (sửa inline %/role/ngày, gỡ, 8/8 test backend); workload theo plan DATE range + granularity DAY/WEEK/MONTH (bảng demand/capacity/utilization/bucket, over-allocation chip); overview cross-plan (demand/capacity/utilization, chỉ APPROVED/ACTIVE) + modal cập nhật capacity (0-100%, nguồn PROJECT); backend bổ sung GET read-only `/plans/{id}/resources` (plan:view) + PlanResourceIntegrationTest 8 tests (278 tests PASS — docs/api/13 §2.6); PlanService +7 methods (35 test PASS cho service); npm test 53 tests PASS + npm run build PASS | |
| PLN-FE-07 | Version & Baseline | `/plans/{id}` (tab Version & Baseline) | ✔ 2026-08-11 | `plan-version-baseline.component.*` mới: danh sách version (versionNo/status/note/createdAt/isActive/counts task-dependency-resource), tạo version (`POST /plans/{id}/versions` — `plan:version`, note ≤ 500 ký tự); so sánh diff version (`GET /plans/{id}/versions/{versionNo}/diff` — modal bảng wbsCode/taskName/field/from→to, giá trị trống hiển thị —); baseline chỉ tạo khi plan APPROVED (`plan:baseline`, hint trong UI khi chưa APPROVED), danh sách baseline (baselineNum/description/capturedAt/versionNo/taskCount), xóa mềm baseline có xác nhận (`DELETE /plans/{id}/baselines/{num}`); variance (`GET /plans/{id}/baselines/{num}/variance`) modal so sánh BL vs current: start/finish, duration/effort (phút), progress, chips lệch (trễ start/finish, milestoneDone, taskDeleted); PlanService +7 methods getVersions/createVersion/getVersionDiff/getBaselines/createBaseline/getBaselineVariance/deleteBaseline (42 test PASS cho service); tab mới trong `/plans/{id}`; npm test 60 tests PASS + npm run build PASS | |
| PLN-FE-08 | Change & Link | `/plans/{id}` (tab Change & Link) | ✔ 2026-08-11 | `plan-change.component.*` mới: change suggestions (list status PENDING/APPLIED/REJECTED + badge màu, duyệt/từ chối có xác nhận — `plan:change`, hint dual-approve effort ≥ 10.000 phút cần 2 người duyệt; modal tạo suggestion: title/description/sourceType/sourceId + nhiều dòng thay đổi PLAN_TASK — chọn task từ WBS, field plannedStart/plannedFinish/durationMinutes/plannedEffortMinutes/percentComplete/status, old/new value); plan links (chọn task → list link EXECUTION_TASK/ISSUE/RISK/MILESTONE với chip BLOCKED_BY/RELATED, ⭐ primary execution, tạo link modal: target/type/note/primary, gỡ link — `plan:link`); change history (bảng changeType/entityType/fieldChanged/old→new/reason/changedAt — chỉ ghi sau APPROVED); backend bổ sung GET read-only `/plans/{id}/change-suggestions` (`plan:view`, scoped theo project — member OK, viewer 403, 404) + PlanChangeLinkIntegrationTest 8 tests (279 tests PASS — docs/api/13 §2.9); PlanService +8 methods (50 test PASS cho service); tab mới trong `/plans/{id}`; routes `app.routes.ts` chuyển lazy-load `loadComponent` (main bundle 996 kB → 389 kB — hết 403 budget); npm test 68 tests PASS + npm run build PASS | |
| PLN-FE-09 | Template & Portfolio | `/plans/templates` + `/portfolio` (sidebar) | ✔ 2026-08-11 | `plan-template.component.*` mới: thư viện template (grid card: templateCode/name/type FULL-PARTIAL/category/version/status PUBLISHED-DRAFT/built-in/taskCount — 8 template mặc định seed), modal chi tiết (cây task theo parentId + sequenceNo, wbsCode/type/duration/effort/scheduleMode), tạo plan từ template (`POST /plans/from-template` — `plan:create`): chọn project (ProjectService options), planCode/planName, Master/Detail + chọn master cha khi DETAIL (tải getPlans MASTER theo project), startDate bắt buộc, navigate sang plan mới; `plan-portfolio.component.*` mới: 5 stat card (total/active/delayed/over-allocated/avg progress) + bảng dự án (code/PM/status/planned range/progress bar/delay chip/🧵 critical count/⚠ over) + upcoming milestones; sidebar thêm Template + Portfolio (`plan:view`); routes lazy `loadComponent`; PlanService +4 methods getTemplates/getTemplateDetail/createPlanFromTemplate/getPortfolio (54 test PASS cho service); npm test 72 tests PASS + npm run build PASS | |
| PLN-FE-10 | Gantt UI | `/plans/{id}` (tab Gantt) | ✔ 2026-08-11 | License đã chốt 2026-08-07: tự dựng SVG không dependency (docs/planning/13 §4). Backend: `GET /plans/{id}/gantt` (`plan:view`) — `PlanGanttService` (tree theo outlineLevel+sequenceNumber, isCritical tính live qua CriticalPathService, baseline overlay baseline mới nhất, resources theo task, dependencies, warnings = [] read-only) + `PlanGanttIntegrationTest` 2 tests (critical theo engine sau recalc A/B — plan không plannedFinish để B end đúng planFinish, baseline start 08-03, resource 80%, dep FS, phân quyền member 200/viewer 403/404). FE: `plan-gantt.component.*` mới — grid WBS trái sticky + timeline SVG phải (sync scroll 1 container), zoom Ngày/Tuần/Tháng (30/14/5px), expand/collapse cây + Mở hết/Thu hết, header tháng + ngày/tuần, today line đứt nét, dep arrow FS có marker, bar task/summary (đậm)/critical (đỏ)/EXTERNAL (nét đứt), progress fill, milestone kim cương xoay 45°, baseline bar xám phía trên, resource chip %, tooltip từng bar; PlanService +1 method getGantt (55 test PASS cho service); tab mới trong `/plans/{id}`; mvn clean verify 281 tests PASS + build PASS; npm test 73 tests PASS + npm run build PASS | |

## 6. Key outstanding & cần confirm trước dev

1. PLN-RULE-SCHED-02 (lag âm), PLN-RULE-SCHED-03 (config critical), PLN-RULE-RES-04/05 (TEAM/EXTERNAL).
2. PLN-RULE-GEN-PERF job async recalc hay sync (docs/planning/08 §4).
3. PLN-RULE-WBS-06 delete tree confirm.
4. ~~License Gantt~~ (docs/planning/13 §4) — **đã chốt 2026-08-07: tự dựng SVG**.
5. Resources snapshot trong baseline (docs/planning/11 §6.2).