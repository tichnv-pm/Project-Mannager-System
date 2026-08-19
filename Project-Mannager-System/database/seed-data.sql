-- =============================================================================
-- PM Daily Work Management — SEED DATA (CHỈ DÀNH CHO LOCAL DEVELOPMENT)
-- =============================================================================
-- File này là bản tham chiếu cho Flyway migration `V2__seed_local_data.sql`
-- (chỉ chạy ở profile local — KHÔNG dùng cho production).
--
-- TÀI KHOẢN DEMO (password chỉ dùng local):
--   admin    / Admin@123     (ADMIN)
--   pm.minh  / Pm@12345      (PROJECT_MANAGER)
--   member1  / Member@123    (PROJECT_MEMBER)
--   member2  / Member@123    (PROJECT_MEMBER)
--   member3  / Member@123    (PROJECT_MEMBER)
--
-- Mật khẩu là BCrypt hash cost 10, prefix $2b$ (sinh bằng bcryptjs).
-- Spring Security BCryptPasswordEncoder verify được $2b$ (BCrypt.checkpw).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. ROLES
-- -----------------------------------------------------------------------------
INSERT INTO roles (id, code, name, description) VALUES
    ('00000000-0000-0000-0000-000000000101', 'ADMIN',            'Quản trị hệ thống',    'Quản lý tài khoản, vai trò, quyền, audit'),
    ('00000000-0000-0000-0000-000000000102', 'PROJECT_MANAGER',  'Quản lý dự án',        'Quản lý dự án, công việc, họp, risk/issue/milestone'),
    ('00000000-0000-0000-0000-000000000103', 'PROJECT_MEMBER',   'Thành viên dự án',     'Thực hiện và cập nhật công việc được giao'),
    ('00000000-0000-0000-0000-000000000104', 'VIEWER',           'Người xem',            'Chỉ xem thông tin');

-- -----------------------------------------------------------------------------
-- 2. PERMISSIONS (32 quyền — docs/05-user-roles-permissions.md mục 3)
-- -----------------------------------------------------------------------------
INSERT INTO permissions (id, code, name) VALUES
    ('00000000-0000-0000-0000-000000000201', 'user:view',             'Xem người dùng'),
    ('00000000-0000-0000-0000-000000000202', 'user:manage',           'Quản lý tài khoản'),
    ('00000000-0000-0000-0000-000000000203', 'role:manage',           'Quản lý vai trò & quyền'),
    ('00000000-0000-0000-0000-000000000204', 'project:view',          'Xem dự án'),
    ('00000000-0000-0000-0000-000000000205', 'project:create',        'Tạo dự án'),
    ('00000000-0000-0000-0000-000000000206', 'project:update',        'Sửa dự án'),
    ('00000000-0000-0000-0000-000000000207', 'project:delete',        'Xóa dự án'),
    ('00000000-0000-0000-0000-000000000208', 'project-member:manage', 'Quản lý thành viên dự án'),
    ('00000000-0000-0000-0000-000000000209', 'task:view',             'Xem công việc'),
    ('00000000-0000-0000-0000-000000000210', 'task:create',           'Tạo công việc'),
    ('00000000-0000-0000-0000-000000000211', 'task:update',           'Cập nhật công việc'),
    ('00000000-0000-0000-0000-000000000212', 'task:delete',           'Xóa công việc'),
    ('00000000-0000-0000-0000-000000000213', 'task:assign',           'Giao việc'),
    ('00000000-0000-0000-0000-000000000214', 'task:comment',          'Bình luận'),
    ('00000000-0000-0000-0000-000000000215', 'task:attachment',       'File đính kèm'),
    ('00000000-0000-0000-0000-000000000216', 'task:export',           'Xuất Excel'),
    ('00000000-0000-0000-0000-000000000217', 'meeting:view',          'Xem cuộc họp'),
    ('00000000-0000-0000-0000-000000000218', 'meeting:manage',        'Quản lý cuộc họp'),
    ('00000000-0000-0000-0000-000000000219', 'action-item:view',      'Xem action item'),
    ('00000000-0000-0000-0000-000000000220', 'action-item:manage',    'Quản lý action item'),
    ('00000000-0000-0000-0000-000000000221', 'risk:view',             'Xem risk'),
    ('00000000-0000-0000-0000-000000000222', 'risk:manage',           'Quản lý risk'),
    ('00000000-0000-0000-0000-000000000223', 'issue:view',            'Xem issue'),
    ('00000000-0000-0000-0000-000000000224', 'issue:manage',          'Quản lý issue'),
    ('00000000-0000-0000-0000-000000000225', 'milestone:view',        'Xem milestone'),
    ('00000000-0000-0000-0000-000000000226', 'milestone:manage',      'Quản lý milestone'),
    ('00000000-0000-0000-0000-000000000227', 'dashboard:view',        'Xem dashboard'),
    ('00000000-0000-0000-0000-000000000228', 'report:view',           'Xem báo cáo'),
    ('00000000-0000-0000-0000-000000000229', 'report:export',         'Xuất báo cáo'),
    ('00000000-0000-0000-0000-000000000230', 'notification:view',     'Xem thông báo'),
    ('00000000-0000-0000-0000-000000000231', 'notification:manage',   'Quản lý thông báo'),
    ('00000000-0000-0000-0000-000000000232', 'audit:view',            'Xem nhật ký hoạt động');

-- -----------------------------------------------------------------------------
-- 3. USERS (password demo ở header — BCrypt cost 10)
-- -----------------------------------------------------------------------------
INSERT INTO users (id, username, email, full_name, password_hash, status) VALUES
    ('00000000-0000-0000-0000-000000000001', 'admin',   'admin@pmdaily.local',   'Quản trị viên',     '$2b$10$WpeOF88sOi5V/5lKbLuGJ.0DFg2ZuX6EqGCXTdOmNLpaQWcSH/Suu', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000002', 'pm.minh', 'minh@pmdaily.local',    'Nguyễn Văn Minh',   '$2b$10$rGLYO437dQJW9zm2vOSbteVLLzeA7JizAz9yQlRkI4KdKg/tbPqKW', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000003', 'member1', 'lan@pmdaily.local',     'Trần Thị Lan',      '$2b$10$dNirRcWmN9.DXW4Baa6.Tuu5RE766w/W/0SxFXd8udurbKifps162', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000004', 'member2', 'hung@pmdaily.local',    'Lê Văn Hùng',       '$2b$10$dNirRcWmN9.DXW4Baa6.Tuu5RE766w/W/0SxFXd8udurbKifps162', 'ACTIVE'),
    ('00000000-0000-0000-0000-000000000005', 'member3', 'thao@pmdaily.local',    'Phạm Thu Thảo',     '$2b$10$dNirRcWmN9.DXW4Baa6.Tuu5RE766w/W/0SxFXd8udurbKifps162', 'ACTIVE');

INSERT INTO user_roles (user_id, role_id) VALUES
    ('00000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000101'),  -- admin → ADMIN
    ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000102'),  -- pm.minh → PROJECT_MANAGER
    ('00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000103'),  -- member1 → PROJECT_MEMBER
    ('00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000103'),  -- member2 → PROJECT_MEMBER
    ('00000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000103');  -- member3 → PROJECT_MEMBER

-- -----------------------------------------------------------------------------
-- 4. ROLE_PERMISSIONS (theo ma trận docs/05 mục 4)
-- -----------------------------------------------------------------------------
-- ADMIN: toàn bộ quyền
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.code = 'ADMIN';

-- PROJECT_MANAGER: tất cả trừ user:view, user:manage, role:manage, audit:view
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'PROJECT_MANAGER'
  AND p.code NOT IN ('user:view', 'user:manage', 'role:manage', 'audit:view');

-- PROJECT_MEMBER
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'PROJECT_MEMBER'
  AND p.code IN ('project:view', 'task:view', 'task:create', 'task:update', 'task:comment',
                 'task:attachment', 'meeting:view', 'action-item:view', 'risk:view',
                 'issue:view', 'milestone:view', 'dashboard:view',
                 'notification:view', 'notification:manage');

-- VIEWER
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'VIEWER'
  AND p.code IN ('project:view', 'task:view', 'meeting:view', 'action-item:view',
                 'risk:view', 'issue:view', 'milestone:view', 'dashboard:view',
                 'report:view', 'notification:view', 'notification:manage');

-- -----------------------------------------------------------------------------
-- 5. PROJECTS & MEMBERS
-- -----------------------------------------------------------------------------
INSERT INTO projects (id, code, name, description, status, start_date, end_date, project_manager_id, customer_name, progress) VALUES
    ('00000000-0000-0000-0000-000000000301', 'PRJ001', 'App Mobile Banking', 'Ứng dụng ngân hàng di động cho khách hàng cá nhân', 'ACTIVE',   '2026-05-01', '2026-11-30', '00000000-0000-0000-0000-000000000002', 'VietBank', 45),
    ('00000000-0000-0000-0000-000000000302', 'PRJ002', 'Website E-Commerce',  'Website bán hàng đa kênh',                       'PLANNING', '2026-09-01', '2027-03-31', '00000000-0000-0000-0000-000000000002', 'ShopNow',  0),
    ('00000000-0000-0000-0000-000000000303', 'PRJ-AGILE', 'Nền tảng Nông sản E-Commerce (Agile Platform)', 'Hệ thống phần mềm sàn giao dịch nông sản đa kênh phát triển theo đúng chuẩn Agile Scrum', 'ACTIVE', '2026-08-01', '2027-02-28', '00000000-0000-0000-0000-000000000002', 'AgriCorp Việt Nam', 35);

INSERT INTO project_members (project_id, user_id, role) VALUES
    ('00000000-0000-0000-0000-000000000301', '00000000-0000-0000-0000-000000000002', 'PROJECT_MANAGER'),
    ('00000000-0000-0000-0000-000000000301', '00000000-0000-0000-0000-000000000003', 'DEVELOPER'),
    ('00000000-0000-0000-0000-000000000301', '00000000-0000-0000-0000-000000000004', 'TESTER'),
    ('00000000-0000-0000-0000-000000000301', '00000000-0000-0000-0000-000000000005', 'DEVELOPER'),
    ('00000000-0000-0000-0000-000000000302', '00000000-0000-0000-0000-000000000002', 'PROJECT_MANAGER'),
    ('00000000-0000-0000-0000-000000000302', '00000000-0000-0000-0000-000000000003', 'DEVELOPER'),
    ('00000000-0000-0000-0000-000000000303', '00000000-0000-0000-0000-000000000002', 'PROJECT_MANAGER'),
    ('00000000-0000-0000-0000-000000000303', '00000000-0000-0000-0000-000000000003', 'DEVELOPER'),
    ('00000000-0000-0000-0000-000000000303', '00000000-0000-0000-0000-000000000004', 'TESTER'),
    ('00000000-0000-0000-0000-000000000303', '00000000-0000-0000-0000-000000000005', 'DEVELOPER');

-- -----------------------------------------------------------------------------
-- 6. TAGS
-- -----------------------------------------------------------------------------
INSERT INTO tags (id, name, color) VALUES
    ('00000000-0000-0000-0000-000000000501', 'hotfix',  '#f44336'),
    ('00000000-0000-0000-0000-000000000502', 'backend', '#2196f3');

-- -----------------------------------------------------------------------------
-- 7. TASKS (PRJ001: 5 task, PRJ002: 1 task — người giao là pm.minh)
-- -----------------------------------------------------------------------------
INSERT INTO tasks (id, code, project_id, parent_task_id, title, description, reporter_id, assignee_id,
                   status, priority, type, source, start_date, due_date, actual_completed_at, progress,
                   blocked, blocker_reason, estimate_minutes, actual_minutes, created_at, created_by) VALUES
    ('00000000-0000-0000-0000-000000000401', 'PRJ001-TASK-000001', '00000000-0000-0000-0000-000000000301', NULL,
     'Xây dựng màn hình login', 'Màn hình đăng nhập kèm captcha và forgot password',
     '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000003',
     'IN_PROGRESS', 'HIGH', 'FEATURE', 'MANUAL', '2026-07-20', '2026-08-05', NULL, 60,
     false, NULL, 480, 320, '2026-07-20T02:00:00Z', '00000000-0000-0000-0000-000000000002'),
    ('00000000-0000-0000-0000-000000000402', 'PRJ001-TASK-000002', '00000000-0000-0000-0000-000000000301', NULL,
     'Viết test case module thanh toán', NULL,
     '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000004',
     'DONE', 'MEDIUM', 'TASK', 'MANUAL', '2026-07-15', '2026-07-25', '2026-07-24T09:00:00Z', 100,
     false, NULL, 960, 900, '2026-07-15T02:00:00Z', '00000000-0000-0000-0000-000000000002'),
    ('00000000-0000-0000-0000-000000000403', 'PRJ001-TASK-000003', '00000000-0000-0000-0000-000000000301', NULL,
     'Fix lỗi mất phiên đăng nhập trên iOS', 'Bug CRITICAL phát hiện ở bản test 0.9',
     '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000005',
     'BLOCKED', 'CRITICAL', 'BUG', 'ISSUE', '2026-07-28', '2026-07-31', NULL, 30,
     true, 'Chờ Apple cấp quyền kiểm tra token trên thiết bị thật', 240, 120,
     '2026-07-28T02:00:00Z', '00000000-0000-0000-0000-000000000002'),
    ('00000000-0000-0000-0000-000000000404', 'PRJ001-TASK-000004', '00000000-0000-0000-0000-000000000301', NULL,
     'Thiết kế API module thanh toán', NULL,
     '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000003',
     'REVIEW', 'HIGH', 'FEATURE', 'MEETING', '2026-07-22', '2026-08-05', NULL, 90,
     false, NULL, 720, 640, '2026-07-22T02:00:00Z', '00000000-0000-0000-0000-000000000002'),
    ('00000000-0000-0000-0000-000000000405', 'PRJ001-TASK-000005', '00000000-0000-0000-0000-000000000301', NULL,
     'Cập nhật tài liệu API theo phiên bản mới', 'Bổ sung luồng refresh token',
     '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000004',
     'TODO', 'LOW', 'IMPROVEMENT', 'MANUAL', '2026-08-01', '2026-08-01', NULL, 0,
     false, NULL, 180, NULL, '2026-08-01T01:30:00Z', '00000000-0000-0000-0000-000000000002'),
    ('00000000-0000-0000-0000-000000000406', 'PRJ002-TASK-000001', '00000000-0000-0000-0000-000000000302', NULL,
     'Dựng skeleton frontend', NULL,
     '00000000-0000-0000-0000-000000000002', NULL,
     'TODO', 'MEDIUM', 'TASK', 'MANUAL', '2026-09-01', '2026-09-15', NULL, 0,
     false, NULL, 960, NULL, '2026-08-01T02:00:00Z', '00000000-0000-0000-0000-000000000002');

-- Task con (demo cấu trúc cha/con): subtask của task 404
INSERT INTO tasks (id, code, project_id, parent_task_id, title, description, reporter_id, assignee_id,
                   status, priority, type, source, start_date, due_date, actual_completed_at, progress,
                   blocked, blocker_reason, estimate_minutes, actual_minutes, created_at, created_by) VALUES
    ('00000000-0000-0000-0000-000000000407', 'PRJ001-TASK-000006', '00000000-0000-0000-0000-000000000301', '00000000-0000-0000-0000-000000000404',
     'Viết OpenAPI cho endpoint payment', NULL,
     '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000003',
     'IN_PROGRESS', 'MEDIUM', 'TASK', 'MANUAL', '2026-07-25', '2026-08-03', NULL, 70,
     false, NULL, 360, 200, '2026-07-25T02:00:00Z', '00000000-0000-0000-0000-000000000002');

-- Task dự án PRJ-AGILE (đồng bộ V2 — demo tích hợp planning/execution)
INSERT INTO tasks (id, code, project_id, parent_task_id, title, description, reporter_id, assignee_id,
                   status, priority, type, source, start_date, due_date, actual_completed_at, progress,
                   blocked, blocker_reason, estimate_minutes, actual_minutes, created_at, created_by) VALUES
    ('00000000-0000-0000-0000-000000000411', 'PRJ-AGILE-TASK-001', '00000000-0000-0000-0000-000000000303', NULL,
     'Phát triển giao diện Login UI Mobile & Design System', 'Xây dựng màn hình đăng nhập Flutter / React Native',
     '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000003',
     'DONE', 'HIGH', 'FEATURE', 'MANUAL', '2026-08-01', '2026-08-04', '2026-08-04T17:00:00Z', 100,
     false, NULL, 1920, 1920, '2026-08-01T02:00:00Z', '00000000-0000-0000-0000-000000000002'),
    ('00000000-0000-0000-0000-000000000412', 'PRJ-AGILE-TASK-002', '00000000-0000-0000-0000-000000000303', NULL,
     'Viết API Spring Security Auth Single-Flight Token', 'Backend OAuth2 service kèm Refresh Token',
     '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000003',
     'DONE', 'HIGH', 'FEATURE', 'MANUAL', '2026-08-05', '2026-08-09', '2026-08-09T17:00:00Z', 100,
     false, NULL, 2400, 2400, '2026-08-05T02:00:00Z', '00000000-0000-0000-0000-000000000002'),
    ('00000000-0000-0000-0000-000000000413', 'PRJ-AGILE-TASK-003', '00000000-0000-0000-0000-000000000303', NULL,
     'Tối ưu Elasticsearch Query cho Danh mục Nông sản', 'Tích hợp bộ lọc nông sản theo vùng miền',
     '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000005',
     'IN_PROGRESS', 'MEDIUM', 'FEATURE', 'MANUAL', '2026-08-10', '2026-08-14', NULL, 75,
     false, NULL, 2400, 1800, '2026-08-10T02:00:00Z', '00000000-0000-0000-0000-000000000002'),
    ('00000000-0000-0000-0000-000000000414', 'PRJ-AGILE-TASK-004', '00000000-0000-0000-0000-000000000303', NULL,
     'Tích hợp SDK Thanh toán VNPAY Checksum SHA256', 'Xử lý callback bảo mật cổng VNPAY',
     '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000005',
     'TODO', 'HIGH', 'FEATURE', 'MANUAL', '2026-08-20', '2026-08-25', NULL, 0,
     false, NULL, 2880, 0, '2026-08-11T02:00:00Z', '00000000-0000-0000-0000-000000000002');

-- Người phối hợp & người theo dõi
INSERT INTO task_assignees (task_id, user_id) VALUES
    ('00000000-0000-0000-0000-000000000401', '00000000-0000-0000-0000-000000000005');
INSERT INTO task_watchers (task_id, user_id) VALUES
    ('00000000-0000-0000-0000-000000000401', '00000000-0000-0000-0000-000000000004');
INSERT INTO task_tags (task_id, tag_id) VALUES
    ('00000000-0000-0000-0000-000000000403', '00000000-0000-0000-0000-000000000501'),
    ('00000000-0000-0000-0000-000000000401', '00000000-0000-0000-0000-000000000502');

INSERT INTO task_comments (task_id, content, created_at, created_by) VALUES
    ('00000000-0000-0000-0000-000000000403', 'Đã liên hệ bên QA, họ xác nhận lỗi xảy ra khi refresh token quá chậm. Đang chờ phản hồi từ bên OS.', '2026-07-29T08:00:00Z', '00000000-0000-0000-0000-000000000003');

-- -----------------------------------------------------------------------------
-- 8. MEETINGS & ACTION ITEMS
-- -----------------------------------------------------------------------------
INSERT INTO meetings (id, project_id, title, start_time, end_time, location, meeting_link, chairperson_id, status, agenda, content, conclusion, created_by) VALUES
    ('00000000-0000-0000-0000-000000000601', '00000000-0000-0000-0000-000000000301', 'Họp sprint 12 — review & planning',
     '2026-08-01T02:00:00Z', '2026-08-01T03:00:00Z', 'Phòng họp 2', NULL,
     '00000000-0000-0000-0000-000000000002', 'COMPLETED',
     '1. Review sprint 11. 2. Chốt task sprint 12. 3. Rủi ro còn tồn đọng.',
     'Review xong 4 task, chấp nhận task 404. Sprint 12 gồm 5 task chính.',
     'Chốt: task 404 chuyển REVIEW, task 403 có action item theo dõi từng ngày.',
     '00000000-0000-0000-0000-000000000002'),
    ('00000000-0000-0000-0000-000000000602', '00000000-0000-0000-0000-000000000301', 'Daily standup',
     '2026-08-02T01:00:00Z', '2026-08-02T01:15:00Z', NULL, 'https://meet.pmdaily.local/daily',
     '00000000-0000-0000-0000-000000000002', 'SCHEDULED',
     'Cập nhật hằng ngày', NULL, NULL,
     '00000000-0000-0000-0000-000000000002');

INSERT INTO meeting_participants (meeting_id, user_id) VALUES
    ('00000000-0000-0000-0000-000000000601', '00000000-0000-0000-0000-000000000002'),
    ('00000000-0000-0000-0000-000000000601', '00000000-0000-0000-0000-000000000003'),
    ('00000000-0000-0000-0000-000000000601', '00000000-0000-0000-0000-000000000004'),
    ('00000000-0000-0000-0000-000000000601', '00000000-0000-0000-0000-000000000005'),
    ('00000000-0000-0000-0000-000000000602', '00000000-0000-0000-0000-000000000002'),
    ('00000000-0000-0000-0000-000000000602', '00000000-0000-0000-0000-000000000003');

INSERT INTO action_items (id, meeting_id, project_id, title, description, assignee_id, due_date, priority, status, progress, created_by) VALUES
    ('00000000-0000-0000-0000-000000000701', '00000000-0000-0000-0000-000000000601', '00000000-0000-0000-0000-000000000301',
     'Theo dõi trạng thái fix lỗi iOS hằng ngày', 'Cập nhật vào biên bản daily, báo ngay nếu chưa có phản hồi từ OS',
     '00000000-0000-0000-0000-000000000005', '2026-08-03', 'HIGH', 'IN_PROGRESS', 40,
     '00000000-0000-0000-0000-000000000002'),
    ('00000000-0000-0000-0000-000000000702', '00000000-0000-0000-0000-000000000601', '00000000-0000-0000-0000-000000000301',
     'Gửi biên bản sprint 12 cho khách hàng', NULL,
     '00000000-0000-0000-0000-000000000002', '2026-08-04', 'MEDIUM', 'OPEN', 0,
     '00000000-0000-0000-0000-000000000002');

-- -----------------------------------------------------------------------------
-- 9. RISKS / ISSUES / MILESTONES
-- -----------------------------------------------------------------------------
INSERT INTO risks (id, code, project_id, title, description, probability, impact, level, owner_id, mitigation_plan, contingency_plan, status, due_date, created_by) VALUES
    ('00000000-0000-0000-0000-000000000801', 'RSK000001', '00000000-0000-0000-0000-000000000301',
     'Rủi ro chậm release do phụ thuộc bên thứ ba', 'API thanh toán của bên thứ ba chưa ổn định ở môi trường test',
     'HIGH', 'HIGH', 'CRITICAL', '00000000-0000-0000-0000-000000000002',
     'Song song phát triển với môi trường sandbox, tăng thời gian dự phòng 2 tuần',
     'Giảm phạm vi release 1.0, lùi tính năng thanh toán sang release 1.1',
     'MONITORING', '2026-08-15', '00000000-0000-0000-0000-000000000002'),
    ('00000000-0000-0000-0000-000000000802', 'RSK000002', '00000000-0000-0000-0000-000000000301',
     'Rủi ro nghỉ việc của thành viên chủ chốt', 'Developer chính xin nghỉ',
     'LOW', 'HIGH', 'MEDIUM', '00000000-0000-0000-0000-000000000002',
     'Ghi chép tài liệu kỹ thuật đầy đủ, rà soát knowledge transfer định kỳ',
     NULL, 'OPEN', '2026-09-01', '00000000-0000-0000-0000-000000000002');

INSERT INTO issues (id, code, project_id, title, description, severity, owner_id, root_cause, solution, status, due_date, created_by) VALUES
    ('00000000-0000-0000-0000-000000000901', 'ISS000001', '00000000-0000-0000-0000-000000000301',
     'Lỗi mất phiên đăng nhập trên iOS', 'Người dùng bị đăng xuất bất thường khi chuyển app',
     'CRITICAL', '00000000-0000-0000-0000-000000000005',
     'Refresh token hết hạn nhanh hơn dự kiến trên iOS khi app bị kill trong thời gian dài',
     'Tăng thời hạn refresh token cho iOS, thêm cơ chế silent refresh trước khi gọi API',
     'IN_PROGRESS', '2026-08-05', '00000000-0000-0000-0000-000000000002');

INSERT INTO milestones (id, project_id, name, description, planned_date, status, progress, note, created_by) VALUES
    ('00000000-0000-0000-0000-000000001001', '00000000-0000-0000-0000-000000000301', 'Release 1.0', 'Phát hành bản chính thức 1.0', '2026-09-30', 'IN_PROGRESS', 40, 'Dự kiến đúng tiến độ', '00000000-0000-0000-0000-000000000002'),
    ('00000000-0000-0000-0000-000000001002', '00000000-0000-0000-0000-000000000301', 'Release 1.1', 'Bản vá và tính năng thanh toán', '2026-11-30', 'NOT_STARTED', 0, NULL, '00000000-0000-0000-0000-000000000002'),
    ('00000000-0000-0000-0000-000000001003', '00000000-0000-0000-0000-000000000302', 'Go-live website', 'Ra mắt website bán hàng', '2027-03-31', 'NOT_STARTED', 0, NULL, '00000000-0000-0000-0000-000000000002');

-- -----------------------------------------------------------------------------
-- 10. NOTIFICATIONS
-- -----------------------------------------------------------------------------
INSERT INTO notifications (recipient_id, type, title, content, entity_type, entity_id, created_at) VALUES
    ('00000000-0000-0000-0000-000000000003', 'TASK_ASSIGNED', 'Bạn được giao công việc mới', 'Xây dựng màn hình login', 'TASK', '00000000-0000-0000-0000-000000000401', '2026-07-20T02:00:00Z'),
    ('00000000-0000-0000-0000-000000000005', 'TASK_OVERDUE', 'Công việc đã quá hạn', 'Fix lỗi mất phiên đăng nhập trên iOS', 'TASK', '00000000-0000-0000-0000-000000000403', '2026-08-01T00:00:00Z'),
    ('00000000-0000-0000-0000-000000000003', 'TASK_COMMENTED', 'Có bình luận mới trên công việc của bạn', 'member3 đã bình luận về task 403', 'TASK', '00000000-0000-0000-0000-000000000403', '2026-07-29T08:00:00Z'),
    ('00000000-0000-0000-0000-000000000003', 'MEETING_INVITED', 'Bạn được thêm vào cuộc họp', 'Họp sprint 12 — review & planning', 'MEETING', '00000000-0000-0000-0000-000000000601', '2026-07-29T02:00:00Z'),
    ('00000000-0000-0000-0000-000000000005', 'ACTION_ITEM_ASSIGNED', 'Bạn được giao action item', 'Theo dõi trạng thái fix lỗi iOS hằng ngày', 'ACTION_ITEM', '00000000-0000-0000-0000-000000000701', '2026-08-01T03:00:00Z');

-- -----------------------------------------------------------------------------
-- 11. BỘ ĐẾM SINH MÃ (đồng bộ với dữ liệu đã chèn)
-- -----------------------------------------------------------------------------
INSERT INTO project_sequences (project_id, task_seq) VALUES
    ('00000000-0000-0000-0000-000000000301', 6),
    ('00000000-0000-0000-0000-000000000302', 1),
    ('00000000-0000-0000-0000-000000000303', 10);

INSERT INTO global_sequences (name, seq) VALUES
    ('risk',  2),
    ('issue', 1);
