# Database 01 — Mô hình dữ liệu (Data Model)

> Dự án: PM Daily Work Management | Trạng thái: Draft
> Nguồn: Prompt 05, `docs/02-functional-requirements.md`, `docs/04-business-rules.md`
> Chi tiết từng cột: `docs/database/02-data-dictionary.md` | Index: `03-index-strategy.md` | DDL: `database/schema.sql`
> **Cập nhật v1.1 (2026-08-07)**: bổ sung Phần Project Planning — bảng mới trong mục 2b, enum mục 5, check constraint mục 6; chi tiết cột ở phần B `02-data-dictionary.md`. Chưa áp dụng DDL (migration chưa viết).

## 1. Quyết định thiết kế (ADR)

| # | Quyết định | Lý do | Trade-off |
|---|---|---|---|
| DB-01 | **UUID PK** (gen_random_uuid) | Không lộ thứ tự dữ liệu, không xung đột khi merge, chuẩn cho hệ thống nội bộ | Index lớn hơn int; không sắp theo insertion |
| DB-02 | **snake_case** cho mọi định danh | Chuẩn PostgreSQL | — |
| DB-03 | **timestamptz (UTC)** cho ngày giờ; `date` cho ngày kế hoạch (không cần giờ) | NFR-TZ: lưu UTC, UI hiển thị theo user; date thuần cho ngày không nhạy múi giờ | — |
| DB-04 | **Enum dạng varchar + CHECK constraint** (không dùng PostgreSQL enum type) | Thêm giá trị mới chỉ cần alter constraint (dễ với Flyway); PostgreSQL enum khó sửa | Mất type-safety ở DB (bù bằng constraint) |
| DB-05 | **Soft delete** bằng `deleted_at + deleted_by` cho bảng nghiệp vụ; mapping table không soft delete | BR-GEN-03, NFR-DATA-01; mapping xóa vật lý đơn giản | Query phải luôn lọc deleted_at |
| DB-06 | **Unique index dạng partial** `WHERE deleted_at IS NULL` cho mã nghiệp vụ (project code, task code, risk code, issue code) | Dữ liệu xóa mềm không chặn tái sử dụng mã | — |
| DB-07 | **Không lưu ID dạng chuỗi**; quan hệ M:N qua bảng mapping | Chuẩn hóa (3NF) | Thêm join |
| DB-08 | **File chỉ lưu metadata + storage path** (server sinh path, không tin tên file user) | An toàn, đơn giản | Cần storage bên ngoài |
| DB-09 | **Refresh token lưu hash SHA-256** (không plaintext) | Đánh cắp DB không dùng được token | Cần thêm bước hash |
| DB-10 | **Audit log before/after JSONB** | Linh hoạt lưu snapshot | Không query sâu được vào JSON (chỉ lưu vết) |
| DB-11 | **Check constraint cho bất biến quan trọng** (progress 0–100, DONE ⇒ 100, BLOCKED ⇒ reason, dueDate ≥ startDate, endTime > startTime) | Lớp bảo vệ cuối cùng ngoài validation ứng dụng | Giữ số lượng check ở mức hợp lý để không phình DDL |

## 2. Tổng quan 23 bảng

| Nhóm | Bảng | Mục đích | Soft delete | Version |
|---|---|---|---|---|
| User & auth | `users` | Tài khoản (kích hoạt bằng status, không xóa mềm) | — | ✔ |
| | `roles` | Vai trò hệ thống | — | ✔ |
| | `permissions` | Quyền hạn | — | ✔ |
| | `user_roles` | User ↔ role (M:N) | — | — |
| | `role_permissions` | Role ↔ permission (M:N) | — | — |
| | `refresh_tokens` | Phiên đăng nhập (hash, revoke) | — | — |
| Project | `projects` | Dự án | ✔ | ✔ |
| | `project_members` | Thành viên + vai trò trong dự án | — | — |
| Task | `tasks` | Công việc (cha/con qua parent_task_id) | ✔ | ✔ |
| | `task_assignees` | Người phối hợp (M:N) | — | — |
| | `task_watchers` | Người theo dõi (M:N) | — | — |
| | `tags` | Nhãn | — | ✔ |
| | `task_tags` | Task ↔ tag (M:N) | — | — |
| | `task_comments` | Bình luận | ✔ | — |
| | `attachments` | File đính kèm (task/meeting) | ✔ | — |
| Meeting | `meetings` | Cuộc họp + biên bản | ✔ | ✔ |
| | `meeting_participants` | Người tham gia (M:N) | — | — |
| | `action_items` | Việc cần làm sau họp (liên kết task) | ✔ | ✔ |
| Risk/Issue | `risks` | Rủi ro (liên kết issue) | ✔ | ✔ |
| | `issues` | Vấn đề | ✔ | ✔ |
| Milestone | `milestones` | Mốc dự án | ✔ | ✔ |
| Notification | `notifications` | Thông báo in-app | — | — |
| Audit | `audit_logs` | Nhật ký hoạt động | — | — |

## 2b. Tổng quan bảng bổ sung — PROJECT PLANNING (v1.1, chưa triển khai)

Quyết định thiết kế được thêm (bổ sung ADR mục 1): PK UUID, version optimistic, soft delete cho dữ liệu nghiệp vụ planning, `plan_type` quyết định liên kết cha-con, baseline bất biến.

| Nhóm | Bảng | Mục đích | Soft delete | Version |
|---|---|---|---|---|
| Plan | `project_plans` | Plan Master/Detail/Template instance + state machine | ✔ | ✔ |
| | `plan_versions` | Version/snapshot plan | ✔ | ✔ |
| WBS | `plan_tasks` | Planning task (cây WBS) | ✔ | ✔ |
| | `plan_task_dependencies` | Dependency FS/SS/FF/SF + lag | — | — |
| Calendar | `plan_calendars` | Working calendar org/project | ✔ | ✔ |
| | `plan_calendar_working_days` | Ngày làm việc trong tuần + giờ | — | — |
| | `plan_calendar_exceptions` | Nghỉ lễ (NON_WORKING) / làm bù (WORKING) | — | — |
| Resource | `plan_task_resources` | Gán resource vào task (USER/TEAM/ROLE/EXTERNAL) | — | — |
| | `resource_capacities` | Capacity theo resource + khoảng thời gian | — | ✔ |
| Baseline | `plan_baselines` | Baseline immutable (chỉ APPROVED) | ✔ | ✔ |
| | `plan_baseline_tasks` | Snapshot task tại baseline + resources (JSON) | — | — |
| Link | `plan_links` | Liên kết planning task ↔ execution/issue/risk/... (polymorphic) | ✔ | ✔ |
| Change | `plan_change_requests` | Change suggestion sau APPROVED | ✔ | ✔ |
| | `plan_change_histories` | Change log diff old/new, actor | ✔ | — |
| Template | `plan_templates` | Template plan (8 mặc định, version) | ✔ | ✔ |
| | `plan_template_tasks` | Task mẫu của template | — | — |
| Portfolio | `portfolios` | (Phạm vi nâng cao — v1.1 đọc trực tiếp, chưa cần ghi) | ✔ | ✔ |
| | `portfolio_projects` | Liên kết người dùng ↔ portfolio | — | — |

> Migration cho nhóm này tách riêng (vd `V4__project_planning.sql` trở đi), theo đúng quy trình Flyway: không sửa file đã chạy.

## 3. Mối quan hệ chính

| Quan hệ | Chi tiết |
|---|---|
| tasks → tasks | `parent_task_id` self-FK (cấp 1; vòng lặp bị chặn ở tầng service, DB không ép) |
| tasks → projects | N:1, bắt buộc |
| tasks.assignee_id → users | Người thực hiện chính (N:1, nullable — có thể chưa giao) |
| tasks.reporter_id → users | Người giao (N:1, bắt buộc = người tạo) |
| tasks ↔ users (collaborators) | M:N qua `task_assignees` |
| tasks ↔ users (watchers) | M:N qua `task_watchers` |
| tasks ↔ tags | M:N qua `task_tags` |
| meetings → projects | N:1 |
| meetings.chairperson_id → users | Chủ trì |
| meetings ↔ users | M:N qua `meeting_participants` |
| action_items → meetings / projects / tasks | N:1; `linked_task_id` unique (1 AI ⇔ tối đa 1 task) |
| risks → projects; risks.linked_issue_id → issues | unique (1 risk ⇔ tối đa 1 issue) |
| issues → projects | N:1 |
| milestones → projects | N:1 |
| attachments → tasks hoặc meetings | task_id/meeting_id nullable, CHECK ít nhất 1 có giá trị |
| notifications → users | recipient_id N:1 |
| audit_logs → users | actor_id N:1 (nullable — hệ thống/job) |

### Mối quan hệ bổ sung — Project Planning (v1.1)

| Quan hệ | Chi tiết |
|---|---|
| project_plans → projects | N:1, bắt buộc |
| project_plans → project_plans | `parent_plan_id` self-FK (chỉ DETAIL→MASTER, 1 cấp) |
| project_plans → plan_versions | `active_version_id` 1:1 (chỉ 1 ACTIVE) |
| project_plans → plan_calendars | `calendar_id` N:1 |
| plan_versions → project_plans | N:1, `versionNo` unique per plan |
| plan_tasks → project_plans / plan_versions | N:1; `parent_id` self-FK (cây WBS) |
| plan_tasks → users | `owner_id` N:1 nullable |
| plan_task_dependencies → plan_tasks | `predecessor_task_id`/`successor_task_id` N:1 |
| plan_calendars → plan_calendars | `parent_calendar_id` self-FK (fallback org) |
| plan_task_resources → plan_tasks | N:1; `resource_type` quyết định FK `resource_id` |
| resource_capacities | resource_id polymorphic (user; khóa unique `(resource_type, resource_id, start_date)`) |
| plan_baselines → project_plans / plan_versions | N:1; `baseline_num` unique per plan |
| plan_baseline_tasks → plan_tasks / plan_baselines | N:1; snapshot (task có thể đổi id khi version) |
| plan_links → plan_tasks + polymorphic | `target_type` + `target_id` (execution task/issue/risk/...) |
| plan_change_requests → plans | N1; `status` pending/applied/rejected |
| plan_change_histories → plans | N1 |
| plan_templates → plan_template_tasks | N1 |
| portfolios → portfolio_projects | M:N (user sở hữu portfolio) |

## 4. Quy ước cột chuẩn (áp dụng mọi bảng có đánh dấu)

| Cột | Kiểu | Ghi chú |
|---|---|---|
| `id` | `uuid` PK | `DEFAULT gen_random_uuid()` |
| `version` | `bigint NOT NULL DEFAULT 0` | Optimistic locking (`@Version`) |
| `created_at` | `timestamptz NOT NULL DEFAULT now()` | |
| `created_by` | `uuid NULL REFERENCES users(id)` | |
| `updated_at` | `timestamptz NOT NULL DEFAULT now()` | |
| `updated_by` | `uuid NULL REFERENCES users(id)` | |
| `deleted_at` | `timestamptz NULL` | Soft delete |
| `deleted_by` | `uuid NULL REFERENCES users(id)` | |

## 5. Enum (varchar + CHECK)

| Bảng · cột | Giá trị | Ghi chú |
|---|---|---|
| `users.status` | ACTIVE, INACTIVE | |
| `projects.status` | PLANNING, ACTIVE, ON_HOLD, COMPLETED, CANCELLED | |
| `project_members.role` | PROJECT_MANAGER, TECH_LEAD, BUSINESS_ANALYST, DEVELOPER, TESTER, DEVOPS, MEMBER | |
| `tasks.status` | TODO, IN_PROGRESS, BLOCKED, REVIEW, DONE, CANCELLED | |
| `tasks.priority` | LOW, MEDIUM, HIGH, CRITICAL | |
| `tasks.type` | FEATURE, BUG, IMPROVEMENT, TASK, OTHER | |
| `tasks.source` | MANUAL, MEETING, ACTION_ITEM, ISSUE, OTHER | |
| `meetings.status` | SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED | |
| `action_items.status` | OPEN, IN_PROGRESS, DONE, CANCELLED | |
| `risks.probability` / `risks.impact` | LOW, MEDIUM, HIGH | |
| `risks.level` | LOW, MEDIUM, HIGH, CRITICAL | |
| `risks.status` | OPEN, MONITORING, MITIGATED, OCCURRED, CLOSED | |
| `issues.severity` | LOW, MEDIUM, HIGH, CRITICAL | |
| `issues.status` | OPEN, ANALYZING, IN_PROGRESS, RESOLVED, CLOSED, REJECTED | |
| `milestones.status` | NOT_STARTED, IN_PROGRESS, COMPLETED, DELAYED, CANCELLED | |
| `notifications.type` | TASK_ASSIGNED, TASK_DUE_SOON, TASK_OVERDUE, TASK_COMMENTED, MEETING_INVITED, ACTION_ITEM_ASSIGNED |
| `project_plans.plan_type` | MASTER, DETAIL, TEMPLATE_INSTANCE |
| `project_plans.status` | DRAFT, SUBMITTED, APPROVED, ACTIVE, ON_HOLD, COMPLETED, CANCELLED, ARCHIVED |
| `plan_tasks.task_type` | PHASE, SUMMARY_TASK, WORK_PACKAGE, TASK, MILESTONE, EXTERNAL_TASK |
| `plan_tasks.schedule_mode` | AUTO, MANUAL |
| `plan_tasks.constraint_type` | FIXED_DATE, START_NO_EARLIER_THAN, START_NO_LATER_THAN, REMOVE_SCHEDULE |
| `plan_tasks.status` | NOT_STARTED, IN_PROGRESS, COMPLETED, DELAYED, CANCELLED |
| `plan_task_dependencies.type` | FS, SS, FF, SF |
| `plan_task_resources.resource_type` | USER, TEAM, ROLE, EXTERNAL |
| `plan_links.target_type` | EXECUTION_TASK, ISSUE, RISK, MILESTONE |
| `plan_links.link_type` | RELATED, BLOCKED_BY (v1, chờ xác nhận) |
| `plan_change_requests.status` | PENDING, APPLIED, REJECTED |
| `plan_templates.template_type` | FULL_LIFECYCLE, PARTIAL |
| `plan_templates.status` | DRAFT, PUBLISHED |
| `plan_calendar_exceptions.exception_type` | NON_WORKING, WORKING |
| `resource_capacities.source` | ORG, PROJECT | |

## 6. Bất biến do DB bảo vệ (Check constraint)

| Bảng | Check | BR tương ứng |
|---|---|---|
| `projects` | `end_date IS NULL OR start_date IS NULL OR end_date >= start_date` | BR-PROJ-02 |
| `projects` | `progress BETWEEN 0 AND 100` | |
| `tasks` | `progress BETWEEN 0 AND 100` | BR-TASK-03 |
| `tasks` | `status <> 'DONE' OR progress = 100` | BR-TASK-04 |
| `tasks` | `status <> 'DONE' OR actual_completed_at IS NOT NULL` | BR-TASK-06 |
| `tasks` | `blocked = false OR blocker_reason IS NOT NULL` | BR-TASK-10 |
| `tasks` | `due_date IS NULL OR start_date IS NULL OR due_date >= start_date` | BR-TASK-02 |
| `tasks` | `estimate_minutes IS NULL OR estimate_minutes >= 0` | |
| `meetings` | `end_time > start_time` | BR-MEET-01 |
| `meetings` | `location IS NOT NULL OR meeting_link IS NOT NULL` | BR-MEET-04 (chờ xác nhận) |
| `action_items` | `progress BETWEEN 0 AND 100`; `status <> 'DONE' OR progress = 100` | |
| `milestones` | `progress BETWEEN 0 AND 100`; `status <> 'COMPLETED' OR progress = 100` | BR-MIL-01/02 |
| `issues` | `status <> 'RESOLVED' OR resolved_at IS NOT NULL` | BR-ISS-03 |
| `attachments` | `task_id IS NOT NULL OR meeting_id IS NOT NULL` | |
| `attachments` | `size_bytes > 0` | |
| `task_comments` | `char_length(content) BETWEEN 1 AND 2000` | BR-TASK-16 |
| `project_plans` | `parent_plan_id IS NULL OR (plan_type = 'DETAIL' AND parent_plan_id <> id)` | PLN-RULE-PLAN-* |
| `project_plans` | `active_version_id IS NULL OR plan_version tồn tại` | (service check) |
| `plan_tasks` | `planned_start IS NULL OR planned_finish IS NULL OR planned_finish >= planned_start` | PLN-RULE-SCHED-* |
| `plan_tasks` | `percent_complete BETWEEN 0 AND 100` | |
| `plan_tasks` | `task_type <> 'MILESTONE' OR (duration_minutes IS NULL OR duration_minutes = 0)` | PLN-RULE-WBS-04 |
| `plan_task_dependencies` | `predecessor_task_id <> successor_task_id` | PLN-RULE-DEP-01 |
| `plan_task_resources` | `allocation_percent BETWEEN 0 AND 100` | (có thể >100 nếu chấp nhận over — chờ) |
| `plan_links` | `target_type IS NOT NULL AND target_id IS NOT NULL` | (polymorphic) |

## 7. Dữ liệu demo & migration

- `database/schema.sql` = bản tham chiếu cho Flyway `V1__init_schema.sql` (Prompt 08 sẽ chuyển đổi chính thức).
- `database/seed-data.sql` = bản tham chiếu cho `V2__seed_local_data.sql` (chỉ chạy profile local).
- Mật khẩu demo (local only): admin/`Admin@123`, pm.minh/`Pm@12345`, members/`Member@123` — hash BCrypt cost 10 (prefix `$2b$`, Spring Security `BCryptPasswordEncoder` verify được). Xem header file seed.
- ID cố định dạng `00000000-0000-0000-0000-0000000000NN` trong seed để ổn định giữa môi trường và test.
