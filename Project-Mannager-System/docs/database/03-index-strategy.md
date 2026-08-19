# Database 03 — Chiến lược Index

> Dự án: PM Daily Work Management | Trạng thái: Draft
> Nguyên tắc: index cho **điều kiện tìm kiếm phổ biến** (mục 15 Prompt 02 + query dashboard); không index dư thừa; bảng soft delete dùng **partial index** `WHERE deleted_at IS NULL` để giữ index nhỏ; không index cột có độ chọn lọc thấp một mình.

## 1. Index PK & UNIQUE (bắt buộc)

| Bảng | Index | Loại |
|---|---|---|
| mọi bảng | `pk_<bảng>` trên `id` | UNIQUE (PK) |
| users | `uk_users_username` (username), `uk_users_email` (email) | UNIQUE |
| roles / permissions | `uk_<bảng>_code` | UNIQUE |
| user_roles | `uk_user_roles_user_role` (user_id, role_id) | UNIQUE |
| role_permissions | `uk_role_permissions_role_perm` (role_id, permission_id) | UNIQUE |
| refresh_tokens | `uk_refresh_tokens_token_hash` | UNIQUE |
| projects | `uk_projects_code_active` (code) `WHERE deleted_at IS NULL` | UNIQUE partial |
| project_members | `uk_project_members_project_user` (project_id, user_id) | UNIQUE |
| tasks | `uk_tasks_code_active` (code) `WHERE deleted_at IS NULL` | UNIQUE partial |
| task_assignees / task_watchers / task_tags | `uk_..._task_user/tag` (task_id, user_id|tag_id) | UNIQUE |
| tags | `uk_tags_name` (name) | UNIQUE |
| meeting_participants | `uk_meeting_participants_meeting_user` | UNIQUE |
| action_items | `uk_action_items_linked_task` (linked_task_id) `WHERE linked_task_id IS NOT NULL` | UNIQUE |
| risks | `uk_risks_linked_issue` (linked_issue_id) `WHERE linked_issue_id IS NOT NULL` | UNIQUE |
| risks / issues | `uk_risks_code_active` / `uk_issues_code_active` (code) `WHERE deleted_at IS NULL` | UNIQUE partial |

### Project Planning (v1.1)

| Bảng | Index | Loại |
|---|---|---|
| project_plans | `uk_project_plans_code_active` (plan_code) `WHERE deleted_at IS NULL` | UNIQUE partial |
| project_plans | `uk_project_plans_master_active` ((project_id) WHERE plan_type='MASTER' AND status IN ('ACTIVE','APPROVED')) | UNIQUE partial |
| project_plans | `ix_project_plans_project` (project_id) `WHERE deleted_at IS NULL` | Danh sách plan theo dự án |
| plan_versions | `uk_plan_versions_plan_no` (plan_id, version_no) | UNIQUE |
| plan_versions | `uk_plan_versions_active` (plan_id) WHERE status='ACTIVE' | UNIQUE partial |
| plan_tasks | `uk_plan_tasks_code` (plan_id, task_code) `WHERE deleted_at IS NULL` | UNIQUE |
| plan_tasks | `ix_plan_tasks_parent` (parent_id) | Cây WBS |
| plan_tasks | `ix_plan_tasks_plan_wbs` (plan_id, wbs_code) | Thứ tự WBS |
| plan_task_dependencies | `uk_plan_dep_unique` (plan_id, predecessor_task_id, successor_task_id, dependency_type) | UNIQUE |
| plan_task_dependencies | `ix_plan_dep_predecessor` (predecessor_task_id) | Tra cứu dep |
| plan_task_dependencies | `ix_plan_dep_successor` (successor_task_id) | DAG calc |
| plan_calendars | `ix_plan_cals_parent` (parent_calendar_id) | Fallback |
| plan_calendar_working_days | `uk_plan_cal_day` (calendar_id, day_of_week) | UNIQUE |
| plan_calendar_exceptions | `uk_plan_cal_exc_date` (calendar_id, exception_date) | UNIQUE |
| plan_task_resources | `ix_plan_res_task` (task_id) | Workload |
| plan_task_resources | `ix_plan_res_resource` (resource_id) | Workload 1 resource |
| plan_baselines | `uk_plan_baseline_num` (plan_id, baseline_num) | UNIQUE |
| plan_baseline_tasks | `ix_plan_baseline_tasks_baseline` (baseline_id) | Snapshot lookup |
| plan_links | `uk_plan_links_primary` (planning_task_id) WHERE is_primary_execution=true | UNIQUE partial |
| plan_links | `ix_plan_links_target` (target_type, target_id) | Polymorphic lookup |
| plan_change_histories | `ix_plan_change_histories_plan` (plan_id, changed_at) | Query lịch sử |
| plan_change_requests | `ix_plan_change_req_status` (plan_id, status) | Duyệt suggestion |
| plan_templates | `uk_plan_templates_code` (template_code) | UNIQUE |
| plan_template_tasks | `ix_plan_template_tasks_parent` (template_id, parent_id) | Cây mẫu |
| resource_capacities | `uk_resource_capacities` (resource_type, resource_id, start_date) | UNIQUE |
| portfolio_projects | `uk_portfolio_projects` (portfolio_id, project_id) | UNIQUE |

## 2. Index truy vấn phổ biến (bảng nghiệp vụ)

### tasks (danh sách + filter — UC-005)

| Index | Lý do | Ghi chú |
|---|---|---|
| `ix_tasks_project_status` (project_id, status) `WHERE deleted_at IS NULL` | Filter phổ biến nhất: theo dự án + trạng thái | Kết hợp filter task trong project |
| `ix_tasks_assignee_status` (assignee_id, status) `WHERE deleted_at IS NULL` | "Việc của tôi" (my-tasks), gán việc | |
| `ix_tasks_due_date` (due_date) `WHERE deleted_at IS NULL` | Quá hạn / sắp đến hạn / sắp xếp theo hạn | Job notification cũng dùng |
| `ix_tasks_parent` (parent_task_id) | Task con của 1 cha | |
| `ix_tasks_created_at` (created_at DESC) | Sắp xếp mới nhất | Chỉ khi cần sort mặc định theo created_at |

### projects

| Index | Lý do |
|---|---|
| `ix_projects_status` (status) `WHERE deleted_at IS NULL` | Lọc trạng thái (FR-PROJ-04) |
| `ix_projects_manager` (project_manager_id) | "Dự án tôi quản lý" |

### meetings / meeting_participants

| Index | Lý do |
|---|---|
| `ix_meetings_project_start` (project_id, start_time) `WHERE deleted_at IS NULL` | Danh sách họp theo dự án + sắp theo thời gian |
| `ix_meeting_participants_user` (user_id) | "Họp của tôi" |

### action_items

| Index | Lý do |
|---|---|
| `ix_action_items_project_status` (project_id, status) `WHERE deleted_at IS NULL` | Dashboard pendingActionItems, danh sách |
| `ix_action_items_assignee_status` (assignee_id, status) `WHERE deleted_at IS NULL` | "Action item của tôi" + quá hạn |

### risks / issues

| Index | Lý do |
|---|---|
| `ix_risks_project_status_level` (project_id, status, level) `WHERE deleted_at IS NULL` | Dashboard highRisks + lọc |
| `ix_risks_owner` (owner_id) `WHERE deleted_at IS NULL` | Theo người phụ trách |
| `ix_issues_project_status` (project_id, status) `WHERE deleted_at IS NULL` | Dashboard openIssues + lọc |
| `ix_issues_owner` (owner_id) `WHERE deleted_at IS NULL` | Theo người phụ trách |

### milestones

| Index | Lý do |
|---|---|
| `ix_milestones_project_status_planned` (project_id, status, planned_date) `WHERE deleted_at IS NULL` | Dashboard upcomingMilestones + sắp xếp |

### notifications

| Index | Lý do |
|---|---|
| `ix_notifications_recipient_unread` (recipient_id, is_read) `WHERE is_read = false` | Đếm unread nhanh (unreadCount) |
| `ix_notifications_recipient_created` (recipient_id, created_at DESC) | Danh sách thông báo |
| `uk_notifications_daily` (recipient_id, type, entity_id, (created_at::date)) `WHERE entity_id IS NOT NULL` | Dedupe job deadline (BR-NOTIF-02) |

### audit_logs (append-only)

| Index | Lý do |
|---|---|
| `ix_audit_actor_created` (actor_id, created_at DESC) | Lọc theo người thực hiện |
| `ix_audit_action_created` (action, created_at DESC) | Lọc theo hành động |
| `ix_audit_entity` (entity_type, entity_id) | Tra cứu theo đối tượng |
| `ix_audit_created` (created_at DESC) | Danh sách mặc định |

## 3. Index KHÔNG tạo (tránh dư thừa)

1. `project_members.user_id` đứng lẻ — dùng chung với lookup membership; khi cần "dự án của tôi" truy qua join, index trên `project_members(project_id, user_id)` (unique) đã cover trường hợp (user_id) cho membership check chính; thêm `ix_project_members_user` (user_id) chỉ khi query "dự án của tôi" được đo là chậm.
2. Cột enum cố định nhỏ (status) đứng lẻ trên bảng có bộ lọc khác mạnh hơn — luôn ưu tiên composite bắt đầu bằng cột chọn lọc cao (project_id).
3. `tasks.title` — không dùng LIKE index (app dùng ILIKE '%kw%' quét tuần tự là chấp nhận được ở quy mô nội bộ ≤ 10k task; nếu cần sau này: pg_trgm).
4. Không index cột `deleted_at` đơn lẻ — partial index đã xử lý.
5. **Project Planning**: không index riêng `plan_task_dependencies.plan_id` (có trong composite unique) — khi query theo plan join qua tasks; `plan_links.plan_id` index nằm trong composite đủ dùng. `plan_baselines` không cần index theo `captured_at` (hiếm query).

## 4. Kiểm chứng

- [ ] Query dashboard (UC-002) giải thích được bằng EXPLAIN, không seq scan toàn bảng ở quy mô dữ liệu test.
- [ ] Query my-tasks / today / overdue dùng index `assignee_status` / `due_date`.
- [ ] UNIQUE partial hoạt động: tạo lại code sau khi xóa mềm không xung đột.
- [ ] Bỏ index không nằm trong danh sách trên nếu không chứng minh được cần.
