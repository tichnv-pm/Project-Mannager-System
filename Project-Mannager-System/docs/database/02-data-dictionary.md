# Database 02 — Data Dictionary (Từ điển dữ liệu)

> Dự án: PM Daily Work Management | Trạng thái: Draft
> Quy ước: các bảng có dấu ★ dùng "cột chuẩn" (id UUID PK, version, created_at/by, updated_at/by, [deleted_at/by]) như mô tả ở `01-data-model.md` mục 4 — dưới đây chỉ liệt kê cột riêng.

## 1. users ★ (không soft delete; vô hiệu hóa bằng status)

| Cột | Kiểu | Nullable | Ràng buộc | Ý nghĩa | Ví dụ |
|---|---|---|---|---|---|
| username | varchar(50) | No | UNIQUE | Tên đăng nhập | `admin` |
| email | varchar(100) | No | UNIQUE | Email | `admin@pmdaily.local` |
| full_name | varchar(100) | No | | Họ tên | `Nguyễn Văn Minh` |
| password_hash | varchar(100) | No | | BCrypt hash | `$2b$10$Wpe...` |
| status | varchar(20) | No | DEFAULT 'ACTIVE' CHECK (ACTIVE, INACTIVE) | Trạng thái tài khoản | `ACTIVE` |
| failed_login_attempts | int | No | DEFAULT 0 | Số lần login sai liên tiếp (BR-AUTH-08) | `0` |
| locked_until | timestamptz | Yes | | Khóa tạm thời đến khi | `2026-08-01T10:30:00Z` |
| last_login_at | timestamptz | Yes | | Lần đăng nhập cuối | `2026-08-01T01:00:00Z` |

## 2. roles ★

| Cột | Kiểu | Nullable | Ràng buộc | Ý nghĩa | Ví dụ |
|---|---|---|---|---|---|
| code | varchar(50) | No | UNIQUE | Mã vai trò | `PROJECT_MANAGER` |
| name | varchar(100) | No | | Tên hiển thị | `Quản lý dự án` |
| description | varchar(255) | Yes | | Mô tả | |

## 3. permissions ★

| Cột | Kiểu | Nullable | Ràng buộc | Ý nghĩa | Ví dụ |
|---|---|---|---|---|---|
| code | varchar(100) | No | UNIQUE | Mã quyền (docs/05) | `task:update` |
| name | varchar(100) | No | | Tên hiển thị | `Cập nhật công việc` |
| description | varchar(255) | Yes | | Mô tả | |

## 4. user_roles (mapping)

| Cột | Kiểu | Nullable | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| id | uuid | No | PK | |
| user_id | uuid | No | FK → users, ON DELETE CASCADE | Người dùng |
| role_id | uuid | No | FK → roles, ON DELETE CASCADE | Vai trò |
| created_at | timestamptz | No | DEFAULT now() | |
| created_by | uuid | Yes | FK → users | |

Ràng buộc: **UNIQUE (user_id, role_id)**.

## 5. role_permissions (mapping)

| Cột | Kiểu | Nullable | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| id | uuid | No | PK | |
| role_id | uuid | No | FK → roles, ON DELETE CASCADE | Vai trò |
| permission_id | uuid | No | FK → permissions, ON DELETE CASCADE | Quyền |
| created_at | timestamptz | No | DEFAULT now() | |
| created_by | uuid | Yes | FK → users | |

Ràng buộc: **UNIQUE (role_id, permission_id)**.

## 6. refresh_tokens (không version/soft delete)

| Cột | Kiểu | Nullable | Ràng buộc | Ý nghĩa | Ví dụ |
|---|---|---|---|---|---|
| id | uuid | No | PK | | |
| user_id | uuid | No | FK → users, ON DELETE CASCADE | Chủ token | |
| token_hash | varchar(64) | No | UNIQUE | SHA-256 của refresh token | `9f86d0...` |
| expires_at | timestamptz | No | | Hết hạn (7 ngày) | `2026-08-08T01:00:00Z` |
| revoked_at | timestamptz | Yes | | Revoke khi logout/rotation | |
| replaced_by | uuid | Yes | | Token mới thay thế (rotation) | |
| created_at | timestamptz | No | DEFAULT now() | | |
| created_by | uuid | Yes | FK → users | | |

## 7. projects ★

| Cột | Kiểu | Nullable | Ràng buộc | Ý nghĩa | Ví dụ |
|---|---|---|---|---|---|
| code | varchar(20) | No | UNIQUE (partial, deleted=null) | Mã dự án | `PRJ001` |
| name | varchar(100) | No | | Tên dự án | `App Mobile Banking` |
| description | text | Yes | | Mô tả | |
| status | varchar(20) | No | DEFAULT 'PLANNING' CHECK | Trạng thái | `ACTIVE` |
| start_date | date | Yes | | Ngày bắt đầu | `2026-08-01` |
| end_date | date | Yes | CHECK end ≥ start | Ngày kết thúc | |
| project_manager_id | uuid | Yes | FK → users | PM của dự án | |
| customer_name | varchar(100) | Yes | | Tên khách hàng | `VietBank` |
| progress | int | No | DEFAULT 0, CHECK 0–100 | Tiến độ | `40` |
| note | text | Yes | | Ghi chú | |

## 8. project_members (mapping, không soft delete)

| Cột | Kiểu | Nullable | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| id | uuid | No | PK | |
| project_id | uuid | No | FK → projects, ON DELETE CASCADE | Dự án |
| user_id | uuid | No | FK → users, ON DELETE CASCADE | Thành viên |
| role | varchar(30) | No | CHECK (7 vai trò) | Vai trò trong dự án |
| created_at / created_by / updated_at / updated_by | | | | |

Ràng buộc: **UNIQUE (project_id, user_id)**.

## 9. meetings ★

| Cột | Kiểu | Nullable | Ràng buộc | Ý nghĩa | Ví dụ |
|---|---|---|---|---|---|
| project_id | uuid | No | FK → projects | Dự án | |
| title | varchar(200) | No | | Tiêu đề | `Họp sprint 12` |
| start_time | timestamptz | No | | Bắt đầu | `2026-08-01T02:00:00Z` |
| end_time | timestamptz | No | CHECK end > start | Kết thúc | |
| location | varchar(255) | Yes | | Địa điểm | `Phòng họp 2` |
| meeting_link | varchar(500) | Yes | | Link online | `https://meet.example.com/abc` |
| chairperson_id | uuid | No | FK → users | Chủ trì | |
| status | varchar(20) | No | DEFAULT 'SCHEDULED' CHECK | Trạng thái | `COMPLETED` |
| agenda | text | Yes | | Nội dung chương trình | |
| content | text | Yes | | Biên bản nội dung | |
| conclusion | text | Yes | | Kết luận | |

## 10. meeting_participants (mapping)

| Cột | Kiểu | Nullable | Ràng buộc |
|---|---|---|---|
| id | uuid | No | PK |
| meeting_id | uuid | No | FK → meetings, ON DELETE CASCADE |
| user_id | uuid | No | FK → users, ON DELETE CASCADE |
| created_at / created_by | | | |

Ràng buộc: **UNIQUE (meeting_id, user_id)**.

## 11. tags ★

| Cột | Kiểu | Nullable | Ràng buộc | Ý nghĩa | Ví dụ |
|---|---|---|---|---|---|
| name | varchar(50) | No | UNIQUE | Tên tag | `hotfix` |
| color | varchar(20) | Yes | | Màu hiển thị (hex) | `#f44336` |

## 12. tasks ★

| Cột | Kiểu | Nullable | Ràng buộc | Ý nghĩa | Ví dụ |
|---|---|---|---|---|---|
| code | varchar(40) | No | UNIQUE (partial, deleted=null) | Mã tự sinh | `PRJ001-TASK-000001` |
| project_id | uuid | No | FK → projects | Dự án | |
| parent_task_id | uuid | Yes | FK → tasks | Task cha (cùng project — ép ở service) | |
| title | varchar(200) | No | | Tiêu đề | `Xây dựng màn hình login` |
| description | text | Yes | | Mô tả | |
| reporter_id | uuid | No | FK → users | Người giao (người tạo) | |
| assignee_id | uuid | Yes | FK → users | Người thực hiện chính | |
| status | varchar(20) | No | DEFAULT 'TODO' CHECK | Trạng thái | `IN_PROGRESS` |
| priority | varchar(10) | No | DEFAULT 'MEDIUM' CHECK | Ưu tiên | `HIGH` |
| type | varchar(20) | No | DEFAULT 'TASK' CHECK | Loại | `FEATURE` |
| source | varchar(20) | Yes | CHECK | Nguồn | `ACTION_ITEM` |
| start_date | date | Yes | | Ngày bắt đầu | |
| due_date | date | Yes | CHECK due ≥ start | Hạn hoàn thành | |
| actual_completed_at | timestamptz | Yes | | Hoàn thành thực tế (DONE) | |
| progress | int | No | DEFAULT 0, CHECK 0–100 | Tiến độ % | `60` |
| blocked | boolean | No | DEFAULT false | Có blocker | `true` |
| blocker_reason | varchar(500) | Yes | CHECK blocked ⇒ reason | Lý do blocker | `Chờ môi trường test` |
| estimate_minutes | int | Yes | CHECK ≥ 0 | Thời gian dự kiến (phút) | `480` |
| actual_minutes | int | Yes | CHECK ≥ 0 | Thời gian thực tế (phút) | |
| notes | text | Yes | | Ghi chú | |

## 13. task_assignees (mapping — người phối hợp)

| Cột | Kiểu | Nullable | Ràng buộc |
|---|---|---|---|
| id | uuid | No | PK |
| task_id | uuid | No | FK → tasks, ON DELETE CASCADE |
| user_id | uuid | No | FK → users, ON DELETE CASCADE |
| created_at / created_by | | | |

Ràng buộc: **UNIQUE (task_id, user_id)**.

## 14. task_watchers (mapping — người theo dõi)

| Cột | Kiểu | Nullable | Ràng buộc |
|---|---|---|---|
| id | uuid | No | PK |
| task_id | uuid | No | FK → tasks, ON DELETE CASCADE |
| user_id | uuid | No | FK → users, ON DELETE CASCADE |
| created_at / created_by | | | |

Ràng buộc: **UNIQUE (task_id, user_id)**.

## 15. task_tags (mapping)

| Cột | Kiểu | Nullable | Ràng buộc |
|---|---|---|---|
| id | uuid | No | PK |
| task_id | uuid | No | FK → tasks, ON DELETE CASCADE |
| tag_id | uuid | No | FK → tags, ON DELETE CASCADE |
| created_at / created_by | | | |

Ràng buộc: **UNIQUE (task_id, tag_id)**.

## 16. task_comments ★ (không version — không edit conflict tranh chấp)

| Cột | Kiểu | Nullable | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| task_id | uuid | No | FK → tasks, ON DELETE CASCADE | Task |
| content | text | No | CHECK 1–2000 ký tự | Nội dung |

## 17. attachments ★ (không version)

| Cột | Kiểu | Nullable | Ràng buộc | Ý nghĩa | Ví dụ |
|---|---|---|---|---|---|
| task_id | uuid | Yes | FK → tasks | Task (nếu thuộc task) | |
| meeting_id | uuid | Yes | FK → meetings | Họp (nếu thuộc họp) | |
| file_name | varchar(255) | No | | Tên hiển thị | `BA-2026-08.docx` |
| file_path | varchar(500) | No | | Path do server sinh | `/uploads/2026/08/ab12.../file` |
| content_type | varchar(100) | Yes | | MIME | `application/pdf` |
| size_bytes | bigint | No | CHECK > 0 (app giới hạn 10MB) | Kích thước | `245760` |
| uploaded_by | uuid | Yes | FK → users | Người upload | |

## 18. action_items ★

| Cột | Kiểu | Nullable | Ràng buộc | Ý nghĩa | Ví dụ |
|---|---|---|---|---|---|
| meeting_id | uuid | No | FK → meetings | Họp phát sinh | |
| project_id | uuid | No | FK → projects | Dự án (cùng meeting) | |
| title | varchar(200) | No | | Tiêu đề | `Gửi biên bản cho khách` |
| description | text | Yes | | Mô tả | |
| assignee_id | uuid | No | FK → users | Người phụ trách | |
| due_date | date | Yes | | Hạn | |
| priority | varchar(10) | No | DEFAULT 'MEDIUM' CHECK | Ưu tiên | |
| status | varchar(20) | No | DEFAULT 'OPEN' CHECK | Trạng thái | `IN_PROGRESS` |
| progress | int | No | DEFAULT 0, CHECK 0–100 | Tiến độ | |
| linked_task_id | uuid | Yes | FK → tasks, **UNIQUE** | Task sau khi chuyển | |

## 19. risks ★

| Cột | Kiểu | Nullable | Ràng buộc | Ý nghĩa | Ví dụ |
|---|---|---|---|---|---|
| code | varchar(30) | No | UNIQUE (partial) | Mã tự sinh | `RSK000001` |
| project_id | uuid | No | FK → projects | Dự án | |
| title | varchar(200) | No | | Tiêu đề | `Rủi ro chậm release` |
| description | text | Yes | | Mô tả | |
| probability | varchar(10) | No | CHECK | Xác suất | `HIGH` |
| impact | varchar(10) | No | CHECK | Ảnh hưởng | `MEDIUM` |
| level | varchar(10) | No | CHECK | Mức độ | `HIGH` |
| owner_id | uuid | No | FK → users | Người phụ trách | |
| mitigation_plan | text | Yes | | Phương án giảm thiểu | |
| contingency_plan | text | Yes | | Phương án dự phòng | |
| status | varchar(20) | No | DEFAULT 'OPEN' CHECK | Trạng thái | `MONITORING` |
| due_date | date | Yes | | Hạn xử lý | |
| linked_issue_id | uuid | Yes | FK → issues, **UNIQUE** | Issue khi OCCURRED | |

## 20. issues ★

| Cột | Kiểu | Nullable | Ràng buộc | Ý nghĩa | Ví dụ |
|---|---|---|---|---|---|
| code | varchar(30) | No | UNIQUE (partial) | Mã tự sinh | `ISS000001` |
| project_id | uuid | No | FK → projects | Dự án | |
| title | varchar(200) | No | | Tiêu đề | `Lỗi mất phiên đăng nhập` |
| description | text | Yes | | Mô tả | |
| severity | varchar(10) | No | CHECK | Mức nghiêm trọng | `CRITICAL` |
| owner_id | uuid | No | FK → users | Người phụ trách | |
| root_cause | text | Yes | | Nguyên nhân gốc | |
| solution | text | Yes | | Giải pháp | |
| status | varchar(20) | No | DEFAULT 'OPEN' CHECK | Trạng thái | `IN_PROGRESS` |
| due_date | date | Yes | | Hạn xử lý | |
| resolved_at | timestamptz | Yes | CHECK RESOLVED ⇒ có giá trị | Thời điểm xử lý xong | |

## 21. milestones ★

| Cột | Kiểu | Nullable | Ràng buộc | Ý nghĩa | Ví dụ |
|---|---|---|---|---|---|
| project_id | uuid | No | FK → projects | Dự án | |
| name | varchar(150) | No | | Tên | `Release 1.0` |
| description | text | Yes | | Mô tả | |
| planned_date | date | No | | Ngày kế hoạch | |
| actual_date | date | Yes | | Ngày thực tế | |
| status | varchar(20) | No | DEFAULT 'NOT_STARTED' CHECK | Trạng thái | `IN_PROGRESS` |
| progress | int | No | DEFAULT 0, CHECK 0–100 | Tiến độ | |
| note | text | Yes | | Ghi chú | |

## 22. notifications (không version/soft delete)

| Cột | Kiểu | Nullable | Ràng buộc | Ý nghĩa | Ví dụ |
|---|---|---|---|---|---|
| id | uuid | No | PK | | |
| recipient_id | uuid | No | FK → users, ON DELETE CASCADE | Người nhận | |
| type | varchar(40) | No | CHECK | Loại | `TASK_OVERDUE` |
| title | varchar(200) | No | | Tiêu đề | `Công việc đã quá hạn` |
| content | text | Yes | | Nội dung | |
| entity_type | varchar(50) | Yes | | Loại đối tượng | `TASK` |
| entity_id | uuid | Yes | | ID đối tượng | |
| is_read | boolean | No | DEFAULT false | Đã đọc | |
| read_at | timestamptz | Yes | CHECK is_read ⇒ có giá trị | Thời điểm đọc | |
| created_at | timestamptz | No | DEFAULT now() | | |

## 23. audit_logs (append-only, không update)

| Cột | Kiểu | Nullable | Ràng buộc | Ý nghĩa | Ví dụ |
|---|---|---|---|---|---|
| id | uuid | No | PK | | |
| trace_id | varchar(64) | Yes | | Trace của request | `a1b2...` |
| actor_id | uuid | Yes | FK → users | Người thực hiện | |
| actor_username | varchar(100) | Yes | | Tiện tra cứu | `admin` |
| action | varchar(100) | No | | Hành động | `TASK_STATUS_CHANGE` |
| entity_type | varchar(50) | Yes | | Loại đối tượng | `TASK` |
| entity_id | uuid | Yes | | ID đối tượng | |
| before_data | jsonb | Yes | | Snapshot trước | `{"status":"REVIEW"}` |
| after_data | jsonb | Yes | | Snapshot sau | `{"status":"DONE"}` |
| created_at | timestamptz | No | DEFAULT now() | | |

## 24. Mã tự sinh (task/risk/issue)

- `tasks.code`: `PRJ001-TASK-000001` → prefix lấy từ project code + bộ đếm theo project (sinh trong transaction, retry khi conflict — xem `docs/04-business-rules.md` mục 12).
- `risks.code`: `RSK000001`, `issues.code`: `ISS000001` — bộ đếm toàn cục.
- Bảng `project_sequences` (tùy chọn v1): nếu giữ bộ đếm DB riêng — `{project_code, task_seq}`; chốt cách cài ở Prompt 11 khi implement.

---

# PHẦN B — Project Planning (v1.1 — chưa triển khai, dự thảo cho migration V4+)

> Bảng dùng tiền tố `plan_` (trừ `project_plans`, `resource_capacities`); tất cả kế thừa "cột chuẩn" (★) theo mục 1 của `01-data-model.md`.

## 25. project_plans ★

| Cột | Kiểu | Nullable | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| project_id | uuid | No | FK → projects | Dự án |
| plan_code | varchar(50) | No | UNIQUE (partial, deleted=null) | Mã plan |
| plan_name | varchar(200) | No | | Tên plan |
| description | text | Yes | | Mô tả |
| plan_type | varchar(30) | No | CHECK MASTER, DETAIL, TEMPLATE_INSTANCE | Loại |
| parent_plan_id | uuid | Yes | FK → project_plans (self) | Master cha (chỉ DETAIL) |
| calendar_id | uuid | Yes | FK → plan_calendars | Calendar làm việc |
| active_version_id | uuid | Yes | FK → plan_versions | Version đang hoạt động |
| planned_start | date | Yes | | Kế hoạch bắt đầu |
| planned_finish | date | Yes | | Kế hoạch kết thúc |
| status | varchar(20) | No | DEFAULT 'DRAFT' CHECK | Trạng thái |
| progress | int | No | DEFAULT 0, CHECK 0–100 | Tiến độ tổng hợp |
| duration_minutes | bigint | Yes | | Thời lượng (working) |
| note | text | Yes | | Ghi chú |

## 26. plan_versions ★

| Cột | Kiểu | Nullable | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| plan_id | uuid | No | FK → project_plans | Plan |
| version_no | int | No | UNIQUE (plan_id) | Số phiên bản |
| status | varchar(20) | No | DEFAULT 'ACTIVE' CHECK (ACTIVE, INACTIVE) | Trạng thái |
| snapshot_json | jsonb | Yes | | Snapshot tree (task+dep+resource) |
| note | text | Yes | | Ghi chú |

> Chỉ 1 version ACTIVE per plan → set ở service; snapshot được gen khi tạo version.

## 27. plan_tasks ★ (1)

| Cột | Kiểu | Nullable | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| plan_id | uuid | No | FK → project_plans | Plan |
| plan_version_id | uuid | Yes | FK → plan_versions | Version (if versioned) |
| parent_id | uuid | Yes | FK → plan_tasks (self) | Cha WBS |
| wbs_code | varchar(60) | No | | `1`, `1.1`, `1.2.1`... |
| task_code | varchar(40) | No | UNIQUE (plan_id, task_code) | Mã task trong plan |
| task_name | varchar(200) | No | | Tên |
| description | text | Yes | | Mô tả |
| task_type | varchar(30) | No | CHECK | PHASE/SUMMARY/WORK_PACKAGE/TASK/MILESTONE/EXTERNAL |
| outline_level | int | No | | Cấp cây |
| sequence_number | int | No | | Thứ tự sibling |
| phase | varchar(50) | Yes | | Phase gợi ý |
| work_package | varchar(50) | Yes | | WP gợi ý |
| deliverable | varchar(200) | Yes | | Deliverable |
| owner_id | uuid | Yes | FK → users | Người phụ trách |
| planned_start | date | Yes | | Bắt đầu kế hoạch |
| planned_finish | date | Yes | | Kết thúc kế hoạch |
| duration_minutes | bigint | Yes | | Số phút kéo dài |
| planned_effort_minutes | int | Yes | CHECK ≥ 0 | Effort dự kiến |
| actual_start | date | Yes | | Bắt đầu thực tế |
| actual_finish | date | Yes | | Kết thúc thực tế |
| actual_effort_minutes | int | Yes | | Effort thực tế |
| remaining_effort_minutes | int | Yes | | Effort còn lại |
| percent_complete | int | No | DEFAULT 0 CHECK 0–100 | Tiến độ |
| status | varchar(20) | No | DEFAULT 'NOT_STARTED' CHECK | Trạng thái |
| priority | varchar(10) | Yes | CHECK | Ưu tiên |
| schedule_mode | varchar(10) | No | DEFAULT 'AUTO' CHECK | AUTO/MANUAL |
| constraint_type | varchar(30) | Yes | CHECK | FIXED_DATE... |
| constraint_date | date | Yes | | Ngày constraint |
| is_summary | boolean | No | DEFAULT false | Có con (suy) |
| is_milestone | boolean | No | DEFAULT false | Là milestone |
| is_critical | boolean | No | DEFAULT false | Thuộc critical path (recalc) |
| (columns chuẩn + version) | | | | |

(1) Trường `is_critical` lưu kết quả chụp recalc — không phải input của user (docs/planning/07 §2, §5).

## 28. plan_task_dependencies (mapping, không soft delete — xóa khi xóa task)

| Cột | Kiểu | Nullable | Ràng buộc |
|---|---|---|---|
| id | uuid | No | PK |
| plan_id | uuid | No | FK → project_plans |
| predecessor_task_id | uuid | No | FK → plan_tasks |
| successor_task_id | uuid | No | FK → plan_tasks |
| dependency_type | varchar(10) | No | CHECK (FS, SS, FF, SF) |
| lag_minutes | int | No | DEFAULT 0 |

Ràng buộc: `predecessor <> successor`; UNIQUE (plan_id, predecessor_task_id, successor_task_id, dependency_type).

## 29. plan_calendars ★

| Cột | Kiểu | Nullable | Ràng buộc | Ý nghĩa |
|---|---|---|---|---|
| name | varchar(100) | No | | Tên |
| description | text | Yes | | |
| parent_calendar_id | uuid | Yes | FK → plan_calendars | Fallback (org) |
| organization_id | uuid | Yes | FK → tùy mô hình org (nếu có) | Thuộc org / null = system |
| daily_working_hours | int | Yes | CHECK 1–24 | Giờ/ngày mặc định |
| timezone | varchar(50) | Yes | | Múi giờ |
| status | varchar(20) | No | DEFAULT 'ACTIVE' CHECK | |

## 30. plan_calendar_working_days (không version)

| Cột | Kiểu | Nullable | Ràng buộc |
|---|---|---|---|
| id | uuid | No | PK |
| calendar_id | uuid | No | FK → plan_calendars, CASCADE |
| day_of_week | int | No | CHECK 1–7 (1=Thứ 2) |
| is_working | boolean | No | DEFAULT true |
| start_time | time | Yes | Giờ vào |
| end_time | time | Yes | Giờ ra |

UNIQUE (calendar_id, day_of_week).

## 31. plan_calendar_exceptions (không version)
| Cột | Kiểu | Nullable | Ràng buộc |
|---|---|---|---|
| id | uuid | No | PK |
| calendar_id | uuid | No | FK → plan_calendars, CASCADE |
| exception_date | date | No | |
| exception_type | varchar(20) | No | CHECK (NON_WORKING, WORKING) |
| note | varchar(200) | Yes | |

UNIQUE (calendar_id, exception_date).

## 32. plan_task_resources (mapping, không version — không soft delete)
| Cột | Kiểu | Nullable | Ràng buộc |
|---|---|---|---|
| id | uuid | No | PK |
| plan_id | uuid | No | FK → project_plans |
| task_id | uuid | No | FK → plan_tasks |
| resource_type | varchar(20) | No | CHECK (USER, TEAM, ROLE, EXTERNAL) |
| resource_id | uuid | No | FK polymorph (users/teams/roles...) |
| role_on_task | varchar(50) | Yes | |
| allocation_percent | int | No | DEFAULT 100 CHECK 0–100 |
| start_date | date | Yes | |
| end_date | date | Yes | |
| planned_effort_minutes | int | Yes | |

> `plan_id` phải khớp `plan_tasks.plan_id` (validate service).

## 33. resource_capacities ★
| Cột | Kiểu | Nullable | Ràng buộc |
|---|---|---|---|
| resource_type | varchar(20) | No | CHECK (USER, TEAM, ROLE) |
| resource_id | uuid | No | FK polymorph |
| capacity_percent | int | No | DEFAULT 100 CHECK 0–100 |
| start_date | date | No | |
| end_date | date | Yes | NULL = vô hạn |
| source | varchar(10) | No | DEFAULT 'ORG' CHECK (ORG, PROJECT) |

UNIQUE (resource_type, resource_id, start_date).

## 34. plan_baselines ★
| Cột | Kiểu | Nullable | Ràng buộc |
|---|---|---|---|
| plan_id | uuid | No | FK → project_plans |
| version_id | uuid | Yes | FK → plan_versions |
| baseline_num | int | No | |
| description | text | Yes | |
| captured_at | timestamptz | No | DEFAULT now() |
| captured_by | uuid | Yes | FK → users |

UNIQUE (plan_id, baseline_num).

## 35. plan_baseline_tasks (không version/soft delete)
| Cột | Kiểu | Nullable | Ràng buộc |
|---|---|---|---|
| id | uuid | No | PK |
| baseline_id | uuid | No | FK → plan_baselines, CASCADE |
| task_id | uuid | Yes | FK → plan_tasks (nullable khi task bị xóa) |
| wbs_code | varchar(60) | No | |
| task_name | varchar(200) | No | |
| task_type | varchar(30) | No | | 
| planned_start / planned_finish | date | Yes | |
| duration_minutes / planned_effort_minutes | int | Yes | |
| percent_complete | int | No | DEFAULT 0 |
| resources_snapshot | jsonb | Yes | List resource + allocation |

## 36. plan_links ★
| Cột | Kiểu | Nullable | Ràng buộc |
|---|---|---|---|
| plan_id | uuid | No | FK → project_plans |
| planning_task_id | uuid | No | FK → plan_tasks |
| target_type | varchar(30) | No | CHECK (EXECUTION_TASK, ISSUE, RISK, MILESTONE) |
| target_id | uuid | No | Polymorphic ID |
| link_type | varchar(20) | No | CHECK (RELATED, BLOCKED_BY) |
| note | varchar(255) | Yes | |
| is_primary_execution | boolean | No | DEFAULT false (1 planning task ⇔ 1 exec chính) |

> UNIQUE cho **primary execution**: khoảng (planning_task_id) WHERE is_primary_execution = true.

## 37. plan_change_requests ★
| Cột | Kiểu | Nullable | Ràng buộc |
|---|---|---|---|
| plan_id | uuid | No | FK |
| source_type | varchar(30) | Yes | ISSUE/RISK/EXECUTION/MANUAL |
| source_id | uuid | Yes | |
| title | varchar(200) | No | |
| description | text | No | |
| suggested_changes | jsonb | No | Diff payload |
| status | varchar(20) | No | DEFAULT 'PENDING' CHECK (PENDING, APPLIED, REJECTED) |
| reviewed_by | uuid | Yes | FK → users |
| reviewed_at | timestamptz | Yes | |

## 38. plan_change_histories ★ (không version)
| Cột | Kiểu | Nullable | Ràng buộc |
|---|---|---|---|
| id | uuid | No | PK |
| plan_id | uuid | No | FK |
| change_type | varchar(40) | No | (WBS_CHANGE, DATE_CHANGE, DEPENDENCY...) |
| entity_type | varchar(30) | No | |
| entity_id | uuid | Yes | |
| field_changed | varchar(100) | Yes | |
| old_value | text | Yes | |
| new_value | text | Yes | |
| reason | text | Yes | |
| change_request_id | uuid | Yes | FK → plan_change_requests (nếu từ suggestion) |
| changed_at | timestamptz | No | DEFAULT now() |
| changed_by | uuid | Yes | FK → users |

## 39. plan_templates ★
| Cột | Kiểu | Nullable | Ràng buộc |
|---|---|---|---|
| id | uuid | No | PK |
| template_code | varchar(50) | No | UNIQUE |
| template_name | varchar(100) | No | |
| description | text | Yes | |
| template_type | varchar(30) | No | CHECK (FULL_LIFECYCLE, PARTIAL) |
| phase_set | jsonb | No | Danh sách 17 phase tùy chọn (template subset) |
| version_no | int | No | DEFAULT 1 |
| status | varchar(20) | No | DEFAULT 'DRAFT' CHECK (DRAFT, PUBLISHED) |
| organization_id | uuid | Yes | NULL = built-in |
| is_built_in | boolean | No | DEFAULT false |

## 40. plan_template_tasks (không version)
| Cột | Kiểu | Nullable | Ràng buộc |
|---|---|---|---|
| id | uuid | No | PK |
| template_id | uuid | No | FK → plan_templates, CASCADE |
| parent_id | uuid | Yes | FK self (cây) |
| wbs_code_md | varchar(60) | No | Mẫu wbs |
| task_name | varchar(200) | No | |
| task_type | varchar(30) | No | |
| planned_effort_minutes | int | Yes | |
| default_role | varchar(50) | Yes | |
| sequence_number | int | No | |

## 41. portfolios / portfolio_projects (v1.1 đơn giản)
### portfolios ★
| Cột | Kiểu | Nullable | Ràng buộc |
|---|---|---|---|
| name | varchar(100) | No | |
| owner_id | uuid | No | FK → users |
| description | text | Yes | |
| is_shared | boolean | No | DEFAULT false |

### portfolio_projects (mapping)

| Cột | Kiểu | Nullable | Ràng buộc |
|---|---|---|---|
| id | uuid | No | PK |
| portfolio_id | uuid | No | FK → portfolios, CASCADE |
| project_id | uuid | No | FK → projects |
| weight | int | No | DEFAULT 1 |

UNIQUE (portfolio_id, project_id).

## 42. Tổng kết
            
- Các bảng planning đều có audit & soft delete phù hợp (property dùng chung docs 04 §2).
- Không sửa migration đã chạy: bổ sung qua `V4__project_planning.sql` trở đi (một lần, tách theo module khi implement PLN-BE-*).

## 43. Mã tự sinh planning

- `project_plans.plan_code`: tự sinh hoặc nhập tay — unique partial, trùng → 409 (chờ xác nhận PLN-RULE-PLAN).
- `plan_tasks.task_code`: nhận dạng tham chiếu API — có thể đồng nhất wbs_code (chờ PLN-RULE-WBS).
