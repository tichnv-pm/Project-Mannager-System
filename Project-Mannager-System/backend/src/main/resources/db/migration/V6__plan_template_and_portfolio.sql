-- =============================================================================
-- PM Daily Work Management - Flyway migration V6 - PLAN TEMPLATES & PORTFOLIO (v1.1)
-- Nguon: docs/planning/12 §2.1-2.4, docs/planning/06, docs/api/13-planning-api.md.
-- =============================================================================

CREATE TABLE IF NOT EXISTS plan_templates (
    id UUID PRIMARY KEY,
    template_code VARCHAR(50) NOT NULL UNIQUE,
    template_name VARCHAR(255) NOT NULL,
    description TEXT,
    template_type VARCHAR(20) NOT NULL DEFAULT 'FULL',
    category VARCHAR(50) NOT NULL DEFAULT 'SOFTWARE',
    version_no INT NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED',
    organization_id UUID,
    is_built_in BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100)
);

ALTER TABLE plan_templates ADD COLUMN IF NOT EXISTS category VARCHAR(50) NOT NULL DEFAULT 'SOFTWARE';
ALTER TABLE plan_templates DROP CONSTRAINT IF EXISTS plan_templates_created_by_fkey;
ALTER TABLE plan_templates ALTER COLUMN created_by TYPE VARCHAR(100) USING created_by::text;
ALTER TABLE plan_templates DROP CONSTRAINT IF EXISTS ck_plan_tpl_type;
ALTER TABLE plan_templates ADD CONSTRAINT ck_plan_tpl_type CHECK (template_type IN ('FULL', 'FULL_LIFECYCLE', 'PARTIAL'));

CREATE TABLE IF NOT EXISTS plan_template_tasks (
    id UUID PRIMARY KEY,
    template_id UUID NOT NULL REFERENCES plan_templates(id) ON DELETE CASCADE,
    parent_id UUID REFERENCES plan_template_tasks(id) ON DELETE SET NULL,
    task_name VARCHAR(255) NOT NULL,
    task_type VARCHAR(30) NOT NULL DEFAULT 'TASK',
    sequence_no INT NOT NULL DEFAULT 1,
    wbs_code VARCHAR(50),
    duration_minutes INT NOT NULL DEFAULT 480,
    planned_effort_minutes INT NOT NULL DEFAULT 480,
    schedule_mode VARCHAR(20) NOT NULL DEFAULT 'AUTO',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100)
);

ALTER TABLE plan_template_tasks ADD COLUMN IF NOT EXISTS sequence_no INT NOT NULL DEFAULT 1;
ALTER TABLE plan_template_tasks ADD COLUMN IF NOT EXISTS wbs_code VARCHAR(50);
ALTER TABLE plan_template_tasks ADD COLUMN IF NOT EXISTS duration_minutes INT NOT NULL DEFAULT 480;
ALTER TABLE plan_template_tasks ADD COLUMN IF NOT EXISTS schedule_mode VARCHAR(20) NOT NULL DEFAULT 'AUTO';
ALTER TABLE plan_template_tasks ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE plan_template_tasks ALTER COLUMN wbs_code_md DROP NOT NULL;
ALTER TABLE plan_template_tasks DROP CONSTRAINT IF EXISTS plan_template_tasks_created_by_fkey;
ALTER TABLE plan_template_tasks ALTER COLUMN created_by TYPE VARCHAR(100) USING created_by::text;

-- Seed 8 built-in plan templates
INSERT INTO plan_templates (id, template_code, template_name, description, template_type, category, version_no, status, is_built_in, version)
VALUES
('a1111111-1111-4111-a111-111111111111', 'FULL_SDL', 'Software Development Lifecycle', 'Quy trình phát triển phần mềm đầy đủ 17 phases', 'FULL', 'SOFTWARE', 1, 'PUBLISHED', TRUE, 0),
('a2222222-2222-4222-a222-222222222222', 'AGILE_SPRINT', 'Agile Sprint (Scrum)', 'Mẫu kế hoạch theo Sprint lặp lại', 'FULL', 'AGILE', 1, 'PUBLISHED', TRUE, 0),
('a3333333-3333-4333-a333-333333333333', 'PMO_STANDARD', 'PMO Standard', 'Mẫu chuẩn của phòng PMO', 'FULL', 'MANAGEMENT', 1, 'PUBLISHED', TRUE, 0),
('a4444444-4444-4444-a444-444444444444', 'MAINTENANCE', 'Maintenance & Support', 'Mẫu vận hành & bảo trì hệ thống', 'PARTIAL', 'OPERATION', 1, 'PUBLISHED', TRUE, 0),
('a5555555-5555-4555-a555-555555555555', 'INFRASTRUCTURE', 'Infrastructure / Cloud', 'Mẫu triển khai hạ tầng & Cloud', 'FULL', 'INFRASTRUCTURE', 1, 'PUBLISHED', TRUE, 0),
('a6666666-6666-4666-a666-666666666666', 'MARKETING', 'Marketing Campaign', 'Mẫu chiến dịch Marketing', 'FULL', 'MARKETING', 1, 'PUBLISHED', TRUE, 0),
('a7777777-7777-4777-a777-777777777777', 'VENDOR', 'Vendor / SOW Deliverables', 'Mẫu bàn giao theo hợp đồng nhà thầu', 'FULL', 'VENDOR', 1, 'PUBLISHED', TRUE, 0),
('a8888888-8888-4888-a888-888888888888', 'DATA', 'Data Project', 'Mẫu dự án xử lý dữ liệu & Analytics', 'FULL', 'DATA', 1, 'PUBLISHED', TRUE, 0);

-- Seed 17 phases for FULL_SDL template
INSERT INTO plan_template_tasks (id, template_id, parent_id, task_name, task_type, sequence_no, wbs_code, duration_minutes, planned_effort_minutes, schedule_mode)
VALUES
('b0100000-0000-4000-b000-000000000001', 'a1111111-1111-4111-a111-111111111111', NULL, '1. INITIATION', 'PHASE', 1, '1', 2400, 2400, 'AUTO'),
('b0200000-0000-4000-b000-000000000002', 'a1111111-1111-4111-a111-111111111111', NULL, '2. REQUIREMENTS', 'PHASE', 2, '2', 4800, 4800, 'AUTO'),
('b0300000-0000-4000-b000-000000000003', 'a1111111-1111-4111-a111-111111111111', NULL, '3. DESIGN', 'PHASE', 3, '3', 4800, 4800, 'AUTO'),
('b0400000-0000-4000-b000-000000000004', 'a1111111-1111-4111-a111-111111111111', NULL, '4. ARCHITECTURE', 'PHASE', 4, '4', 2400, 2400, 'AUTO'),
('b0500000-0000-4000-b000-000000000005', 'a1111111-1111-4111-a111-111111111111', NULL, '5. DEVELOPMENT', 'PHASE', 5, '5', 9600, 9600, 'AUTO'),
('b0600000-0000-4000-b000-000000000006', 'a1111111-1111-4111-a111-111111111111', NULL, '6. INTEGRATION', 'PHASE', 6, '6', 2400, 2400, 'AUTO'),
('b0700000-0000-4000-b000-000000000007', 'a1111111-1111-4111-a111-111111111111', NULL, '7. TESTING', 'PHASE', 7, '7', 4800, 4800, 'AUTO'),
('b0800000-0000-4000-b000-000000000008', 'a1111111-1111-4111-a111-111111111111', NULL, '8. QUALITY ASSURANCE', 'PHASE', 8, '8', 2400, 2400, 'AUTO'),
('b0900000-0000-4000-b000-000000000009', 'a1111111-1111-4111-a111-111111111111', NULL, '9. DEPLOYMENT', 'PHASE', 9, '9', 1440, 1440, 'AUTO'),
('b1000000-0000-4000-b000-000000000010', 'a1111111-1111-4111-a111-111111111111', NULL, '10. TRAINING', 'PHASE', 10, '10', 1440, 1440, 'AUTO'),
('b1100000-0000-4000-b000-000000000011', 'a1111111-1111-4111-a111-111111111111', NULL, '11. DOCUMENTATION', 'PHASE', 11, '11', 2400, 2400, 'AUTO'),
('b1200000-0000-4000-b000-000000000012', 'a1111111-1111-4111-a111-111111111111', NULL, '12. UAT', 'PHASE', 12, '12', 2400, 2400, 'AUTO'),
('b1300000-0000-4000-b000-000000000013', 'a1111111-1111-4111-a111-111111111111', NULL, '13. SECURITY AUDIT', 'PHASE', 13, '13', 1440, 1440, 'AUTO'),
('b1400000-0000-4000-b000-000000000014', 'a1111111-1111-4111-a111-111111111111', NULL, '14. PERFORMANCE', 'PHASE', 14, '14', 1440, 1440, 'AUTO'),
('b1500000-0000-4000-b000-000000000015', 'a1111111-1111-4111-a111-111111111111', NULL, '15. SUPPORT & WARRANTY', 'PHASE', 15, '15', 4800, 4800, 'AUTO'),
('b1600000-0000-4000-b000-000000000016', 'a1111111-1111-4111-a111-111111111111', NULL, '16. MAINTENANCE', 'PHASE', 16, '16', 4800, 4800, 'AUTO'),
('b1700000-0000-4000-b000-000000000017', 'a1111111-1111-4111-a111-111111111111', NULL, '17. CLOSURE', 'PHASE', 17, '17', 960, 960, 'AUTO');

-- =============================================================================
-- SEED DATA: DỰ ÁN MẪU AGILE SCRUM HOÀN CHỈNH CHO CẢ 7 TABS TRỰC QUAN
-- =============================================================================

-- 1. LỊCH LÀM VIỆC (PLAN CALENDARS)
INSERT INTO plan_calendars (id, name, description, daily_working_hours, timezone, status) VALUES
    ('00000000-0000-0000-0000-000000000a01', 'Lịch Chuẩn Agile Team (8h/ngày, T2-T6)', 'Lịch làm việc chuẩn 40 giờ/tuần dành cho đội phát triển phần mềm Agile', 8, 'Asia/Ho_Chi_Minh', 'ACTIVE');

INSERT INTO plan_calendar_working_days (calendar_id, day_of_week, is_working, start_time, end_time) VALUES
    ('00000000-0000-0000-0000-000000000a01', 1, true, '08:00:00', '17:00:00'),
    ('00000000-0000-0000-0000-000000000a01', 2, true, '08:00:00', '17:00:00'),
    ('00000000-0000-0000-0000-000000000a01', 3, true, '08:00:00', '17:00:00'),
    ('00000000-0000-0000-0000-000000000a01', 4, true, '08:00:00', '17:00:00'),
    ('00000000-0000-0000-0000-000000000a01', 5, true, '08:00:00', '17:00:00'),
    ('00000000-0000-0000-0000-000000000a01', 6, false, NULL, NULL),
    ('00000000-0000-0000-0000-000000000a01', 7, false, NULL, NULL);

INSERT INTO plan_calendar_exceptions (calendar_id, exception_date, exception_type, note) VALUES
    ('00000000-0000-0000-0000-000000000a01', '2026-09-02', 'NON_WORKING', 'Nghỉ lễ Quốc khánh 2/9'),
    ('00000000-0000-0000-0000-000000000a01', '2026-09-03', 'NON_WORKING', 'Nghỉ bù lễ Quốc khánh');

-- 2. KẾ HOẠCH MASTER PLAN & DETAIL PLANS (PROJECT PLANS)
INSERT INTO project_plans (id, project_id, plan_code, plan_name, description, plan_type, parent_plan_id, calendar_id, planned_start, planned_finish, status, progress, duration_minutes, note) VALUES
    ('00000000-0000-0000-0000-000000000b01', '00000000-0000-0000-0000-000000000303', 'PLN-AGILE-MASTER', 'Master Plan: Tổng thể phát triển Nền tảng Nông sản (Agile Release Roadmap)', 'Kế hoạch phát triển tổng thể theo chuẩn Agile Release Train (Release 1.0 MVP & Release 2.0 Enhancement)', 'MASTER', NULL, '00000000-0000-0000-0000-000000000a01', '2026-08-01', '2027-02-28', 'APPROVED', 35, 144000, 'Kế hoạch tổng thể Master Plan đã phê duyệt'),
    ('00000000-0000-0000-0000-000000000b02', '00000000-0000-0000-0000-000000000303', 'PLN-SPRINT1-DETAIL', 'Detail Plan: Sprint 1 - Auth & Catalog Development', 'Kế hoạch chi tiết Sprint 1 tập trung vào Auth Service và Catalog Nông sản', 'DETAIL', '00000000-0000-0000-0000-000000000b01', '00000000-0000-0000-0000-000000000a01', '2026-08-01', '2026-08-14', 'ACTIVE', 85, 4800, 'Sprint 1 đang thực thi'),
    ('00000000-0000-0000-0000-000000000b03', '00000000-0000-0000-0000-000000000303', 'PLN-SPRINT2-DETAIL', 'Detail Plan: Sprint 2 - Shopping Cart & Payment Checkout', 'Kế hoạch chi tiết Sprint 2 tập trung vào Giỏ hàng Realtime và Tích hợp Cổng thanh toán VNPAY/Momo', 'DETAIL', '00000000-0000-0000-0000-000000000b01', '00000000-0000-0000-0000-000000000a01', '2026-08-15', '2026-08-28', 'DRAFT', 10, 4800, 'Sprint 2 đang chuẩn bị');

-- 3. WBS TASKS FOR MASTER PLAN
INSERT INTO plan_tasks (id, plan_id, parent_id, wbs_code, task_code, task_name, description, task_type, outline_level, sequence_number, phase, work_package, owner_id, planned_start, planned_finish, duration_minutes, planned_effort_minutes, actual_start, actual_finish, actual_effort_minutes, remaining_effort_minutes, percent_complete, status, priority, schedule_mode, is_summary, is_milestone, is_critical) VALUES
    ('00000000-0000-0000-0000-000000000c01', '00000000-0000-0000-0000-000000000b01', NULL, '1', 'TSK-P1', 'RELEASE 1.0 - CORE MVP PLATFORM', 'Giai đoạn 1 phát hành MVP cơ bản', 'PHASE', 1, 1, 'RELEASE 1.0', NULL, '00000000-0000-0000-0000-000000000002', '2026-08-01', '2026-08-28', 9600, 9600, '2026-08-01', NULL, 4320, 5280, 65, 'IN_PROGRESS', 'HIGH', 'AUTO', true, false, false),
    ('00000000-0000-0000-0000-000000000c02', '00000000-0000-0000-0000-000000000b01', '00000000-0000-0000-0000-000000000c01', '1.1', 'TSK-SP1', 'SPRINT 1: Authentication & Product Catalog (2 tuần)', 'Sprint 1 xây dựng đăng nhập và danh mục nông sản', 'WORK_PACKAGE', 2, 1, 'SPRINT 1', 'Auth & Catalog', '00000000-0000-0000-0000-000000000002', '2026-08-01', '2026-08-14', 4800, 4800, '2026-08-01', NULL, 4320, 480, 85, 'IN_PROGRESS', 'HIGH', 'AUTO', true, false, false),
    ('00000000-0000-0000-0000-000000000c03', '00000000-0000-0000-0000-000000000b01', '00000000-0000-0000-0000-000000000c02', '1.1.1', 'TSK-101', 'Thiết kế UI/UX Design System & Mobile Prototypes', 'Thiết kế Figma UI/UX chuẩn Agile Mobile First', 'TASK', 3, 1, 'SPRINT 1', 'UI/UX', '00000000-0000-0000-0000-000000000003', '2026-08-01', '2026-08-04', 1920, 1920, '2026-08-01', '2026-08-04', 1920, 0, 100, 'COMPLETED', 'HIGH', 'AUTO', false, false, false),
    ('00000000-0000-0000-0000-000000000c04', '00000000-0000-0000-0000-000000000b01', '00000000-0000-0000-0000-000000000c02', '1.1.2', 'TSK-102', 'Xây dựng API OAuth2 / JWT Auth Service & Phân quyền', 'Backend Auth service JWT single-flight refresh token', 'TASK', 3, 2, 'SPRINT 1', 'Backend Auth', '00000000-0000-0000-0000-000000000003', '2026-08-05', '2026-08-09', 2400, 2400, '2026-08-05', '2026-08-09', 2400, 0, 100, 'COMPLETED', 'HIGH', 'AUTO', false, false, true),
    ('00000000-0000-0000-0000-000000000c05', '00000000-0000-0000-0000-000000000b01', '00000000-0000-0000-0000-000000000c02', '1.1.3', 'TSK-103', 'Xây dựng API Catalog Nông sản & Tìm kiếm Elasticsearch', 'Tích hợp bộ lọc nông sản theo vùng miền và loại hàng', 'TASK', 3, 3, 'SPRINT 1', 'Catalog Search', '00000000-0000-0000-0000-000000000005', '2026-08-10', '2026-08-14', 2400, 2400, '2026-08-10', NULL, 1800, 600, 75, 'IN_PROGRESS', 'MEDIUM', 'AUTO', false, false, true),
    ('00000000-0000-0000-0000-000000000c06', '00000000-0000-0000-0000-000000000b01', '00000000-0000-0000-0000-000000000c02', '1.1.4', 'MS-SP1', 'Mốc Hoàn thành Sprint 1 Demo & Review', 'Họp Sprint Review trình diễn tính năng Đăng nhập & Catalog', 'MILESTONE', 3, 4, 'SPRINT 1', 'Sprint Review', '00000000-0000-0000-0000-000000000002', '2026-08-14', '2026-08-14', 0, 0, '2026-08-14', '2026-08-14', 0, 0, 100, 'COMPLETED', 'HIGH', 'AUTO', false, true, true),
    ('00000000-0000-0000-0000-000000000c07', '00000000-0000-0000-0000-000000000b01', '00000000-0000-0000-0000-000000000c01', '1.2', 'TSK-SP2', 'SPRINT 2: Shopping Cart & Payment Gateway (2 tuần)', 'Sprint 2 phát triển Giỏ hàng và Cổng thanh toán', 'WORK_PACKAGE', 2, 2, 'SPRINT 2', 'Cart & Payment', '00000000-0000-0000-0000-000000000002', '2026-08-15', '2026-08-28', 4800, 4800, NULL, NULL, 0, 4800, 15, 'IN_PROGRESS', 'HIGH', 'AUTO', true, false, false),
    ('00000000-0000-0000-0000-000000000c08', '00000000-0000-0000-0000-000000000b01', '00000000-0000-0000-0000-000000000c07', '1.2.1', 'TSK-201', 'Phát triển Giỏ hàng Realtime (Redis Cluster)', 'Lưu trữ trạng thái giỏ hàng realtime tối ưu tốc độ', 'TASK', 3, 1, 'SPRINT 2', 'Cart Redis', '00000000-0000-0000-0000-000000000003', '2026-08-15', '2026-08-19', 2400, 2400, NULL, NULL, 0, 2400, 30, 'IN_PROGRESS', 'MEDIUM', 'AUTO', false, false, true),
    ('00000000-0000-0000-0000-000000000c09', '00000000-0000-0000-0000-000000000b01', '00000000-0000-0000-0000-000000000c07', '1.2.2', 'TSK-202', 'Tích hợp Cổng thanh toán VNPAY / Momo API', 'Xử lý mã hóa checksum SHA256 và callback bảo mật', 'TASK', 3, 2, 'SPRINT 2', 'Payment Gateway', '00000000-0000-0000-0000-000000000005', '2026-08-20', '2026-08-25', 2880, 2880, NULL, NULL, 0, 2880, 0, 'NOT_STARTED', 'HIGH', 'AUTO', false, false, true),
    ('00000000-0000-0000-0000-000000000c10', '00000000-0000-0000-0000-000000000b01', NULL, '2', 'TSK-P2', 'RELEASE 2.0 - ADVANCED ANALYTICS & LOGISTICS', 'Giai đoạn 2 mở rộng tính năng nâng cao', 'PHASE', 1, 2, 'RELEASE 2.0', NULL, '00000000-0000-0000-0000-000000000002', '2026-09-01', '2027-02-28', 19200, 19200, NULL, NULL, 0, 19200, 0, 'NOT_STARTED', 'MEDIUM', 'AUTO', true, false, false);

-- 4. WBS TASKS FOR DETAIL PLAN 1 (PLN-SPRINT1-DETAIL)
INSERT INTO plan_tasks (id, plan_id, parent_id, wbs_code, task_code, task_name, description, task_type, outline_level, sequence_number, phase, work_package, owner_id, planned_start, planned_finish, duration_minutes, planned_effort_minutes, actual_start, actual_finish, actual_effort_minutes, remaining_effort_minutes, percent_complete, status, priority, schedule_mode, is_summary, is_milestone, is_critical) VALUES
    ('00000000-0000-0000-0000-000000000e01', '00000000-0000-0000-0000-000000000b02', NULL, '1', 'DTL-SP1', 'SPRINT 1 BACKLOG & EXECUTION', 'Nhóm công việc chi tiết của Sprint 1', 'WORK_PACKAGE', 1, 1, 'SPRINT 1', 'Auth & Catalog', '00000000-0000-0000-0000-000000000002', '2026-08-01', '2026-08-14', 4800, 4800, '2026-08-01', NULL, 4320, 480, 85, 'IN_PROGRESS', 'HIGH', 'AUTO', true, false, false),
    ('00000000-0000-0000-0000-000000000e02', '00000000-0000-0000-0000-000000000b02', '00000000-0000-0000-0000-000000000e01', '1.1', 'DTL-101', 'Figma Prototypes & Design Tokens UI', 'Thiết kế chi tiết giao diện responsive mobile & desktop', 'TASK', 2, 1, 'SPRINT 1', 'UI/UX', '00000000-0000-0000-0000-000000000003', '2026-08-01', '2026-08-04', 1920, 1920, '2026-08-01', '2026-08-04', 1920, 0, 100, 'COMPLETED', 'HIGH', 'AUTO', false, false, false),
    ('00000000-0000-0000-0000-000000000e03', '00000000-0000-0000-0000-000000000b02', '00000000-0000-0000-0000-000000000e01', '1.2', 'DTL-102', 'API Auth OAuth2 & Single-Flight Refresh Token', 'Xây dựng service đăng nhập bảo mật kèm rate limiting', 'TASK', 2, 2, 'SPRINT 1', 'Backend Auth', '00000000-0000-0000-0000-000000000003', '2026-08-05', '2026-08-09', 2400, 2400, '2026-08-05', '2026-08-09', 2400, 0, 100, 'COMPLETED', 'HIGH', 'AUTO', false, false, true),
    ('00000000-0000-0000-0000-000000000e04', '00000000-0000-0000-0000-000000000b02', '00000000-0000-0000-0000-000000000e01', '1.3', 'DTL-103', 'API Catalog Nông sản Elasticsearch Query', 'Tối ưu câu truy vấn tìm kiếm danh mục sản phẩm', 'TASK', 2, 3, 'SPRINT 1', 'Catalog Search', '00000000-0000-0000-0000-000000000005', '2026-08-10', '2026-08-14', 2400, 2400, '2026-08-10', NULL, 1800, 600, 75, 'IN_PROGRESS', 'MEDIUM', 'AUTO', false, false, true),
    ('00000000-0000-0000-0000-000000000e05', '00000000-0000-0000-0000-000000000b02', '00000000-0000-0000-0000-000000000e01', '1.4', 'MS-DTL1', 'Nghiệm thu Sprint 1 với Khách hàng AgriCorp', 'Review sản phẩm và nghiệm thu các câu chuyện người dùng', 'MILESTONE', 2, 4, 'SPRINT 1', 'Sprint Review', '00000000-0000-0000-0000-000000000002', '2026-08-14', '2026-08-14', 0, 0, '2026-08-14', '2026-08-14', 0, 0, 100, 'COMPLETED', 'HIGH', 'AUTO', false, true, true);

-- 5. LIÊN KẾT TASK (TASK DEPENDENCIES)
INSERT INTO plan_task_dependencies (id, plan_id, predecessor_task_id, successor_task_id, dependency_type, lag_minutes) VALUES
    ('00000000-0000-0000-0000-000000000d01', '00000000-0000-0000-0000-000000000b01', '00000000-0000-0000-0000-000000000c03', '00000000-0000-0000-0000-000000000c04', 'FS', 0),
    ('00000000-0000-0000-0000-000000000d02', '00000000-0000-0000-0000-000000000b01', '00000000-0000-0000-0000-000000000c04', '00000000-0000-0000-0000-000000000c05', 'FS', 0),
    ('00000000-0000-0000-0000-000000000d03', '00000000-0000-0000-0000-000000000b01', '00000000-0000-0000-0000-000000000c05', '00000000-0000-0000-0000-000000000c06', 'FS', 0),
    ('00000000-0000-0000-0000-000000000d04', '00000000-0000-0000-0000-000000000b01', '00000000-0000-0000-0000-000000000c06', '00000000-0000-0000-0000-000000000c08', 'FS', 0),
    ('00000000-0000-0000-0000-000000000d05', '00000000-0000-0000-0000-000000000b01', '00000000-0000-0000-0000-000000000c08', '00000000-0000-0000-0000-000000000c09', 'FS', 0),
    ('00000000-0000-0000-0000-000000000d06', '00000000-0000-0000-0000-000000000b02', '00000000-0000-0000-0000-000000000e02', '00000000-0000-0000-0000-000000000e03', 'FS', 0),
    ('00000000-0000-0000-0000-000000000d07', '00000000-0000-0000-0000-000000000b02', '00000000-0000-0000-0000-000000000e03', '00000000-0000-0000-0000-000000000e04', 'FS', 0);

-- 6. PHÂN BỔ RESOURCE (RESOURCE ALLOCATIONS & CAPACITIES)
INSERT INTO plan_task_resources (id, plan_id, task_id, resource_type, resource_id, role_on_task, allocation_percent, start_date, end_date, planned_effort_minutes) VALUES
    ('00000000-0000-0000-0000-000000000f01', '00000000-0000-0000-0000-000000000b01', '00000000-0000-0000-0000-000000000c03', 'USER', '00000000-0000-0000-0000-000000000003', 'UI/UX Designer', 100, '2026-08-01', '2026-08-04', 1920),
    ('00000000-0000-0000-0000-000000000f02', '00000000-0000-0000-0000-000000000b01', '00000000-0000-0000-0000-000000000c04', 'USER', '00000000-0000-0000-0000-000000000003', 'Backend Lead', 100, '2026-08-05', '2026-08-09', 2400),
    ('00000000-0000-0000-0000-000000000f03', '00000000-0000-0000-0000-000000000b01', '00000000-0000-0000-0000-000000000c05', 'USER', '00000000-0000-0000-0000-000000000005', 'Fullstack Dev', 100, '2026-08-10', '2026-08-14', 2400),
    ('00000000-0000-0000-0000-000000000f04', '00000000-0000-0000-0000-000000000b02', '00000000-0000-0000-0000-000000000e03', 'USER', '00000000-0000-0000-0000-000000000003', 'Backend Engineer', 100, '2026-08-05', '2026-08-09', 2400),
    ('00000000-0000-0000-0000-000000000f05', '00000000-0000-0000-0000-000000000b02', '00000000-0000-0000-0000-000000000e04', 'USER', '00000000-0000-0000-0000-000000000005', 'Search Specialist', 100, '2026-08-10', '2026-08-14', 2400);

INSERT INTO resource_capacities (id, resource_type, resource_id, capacity_percent, start_date, source) VALUES
    ('00000000-0000-0000-0000-000000000f10', 'USER', '00000000-0000-0000-0000-000000000003', 100, '2026-08-01', 'PROJECT'),
    ('00000000-0000-0000-0000-000000000f11', 'USER', '00000000-0000-0000-0000-000000000005', 100, '2026-08-01', 'PROJECT');

-- 7. VERSION & BASELINE (PLAN VERSIONS & BASELINES)
INSERT INTO plan_versions (id, plan_id, version_no, status, note) VALUES
    ('00000000-0000-0000-0000-000000000e11', '00000000-0000-0000-0000-000000000b01', 1, 'ACTIVE', 'Phiên bản ban đầu kế hoạch Master Plan đã phê duyệt'),
    ('00000000-0000-0000-0000-000000000e12', '00000000-0000-0000-0000-000000000b02', 1, 'ACTIVE', 'Phiên bản chi tiết Sprint 1 Kế hoạch cơ sở');

UPDATE project_plans SET active_version_id = '00000000-0000-0000-0000-000000000e11' WHERE id = '00000000-0000-0000-0000-000000000b01';
UPDATE project_plans SET active_version_id = '00000000-0000-0000-0000-000000000e12' WHERE id = '00000000-0000-0000-0000-000000000b02';

INSERT INTO plan_baselines (id, plan_id, version_id, baseline_num, description) VALUES
    ('00000000-0000-0000-0000-000000000e21', '00000000-0000-0000-0000-000000000b01', '00000000-0000-0000-0000-000000000e11', 1, 'Baseline #1: Đường cơ sở ban đầu Master Plan (Khởi tạo dự án)'),
    ('00000000-0000-0000-0000-000000000e22', '00000000-0000-0000-0000-000000000b02', '00000000-0000-0000-0000-000000000e12', 1, 'Baseline #1: Đường cơ sở Sprint 1');

INSERT INTO plan_baseline_tasks (baseline_id, task_id, wbs_code, task_name, task_type, planned_start, planned_finish, duration_minutes, planned_effort_minutes, percent_complete) VALUES
    ('00000000-0000-0000-0000-000000000e21', '00000000-0000-0000-0000-000000000c03', '1.1.1', 'Thiết kế UI/UX Design System & Mobile Prototypes', 'TASK', '2026-08-01', '2026-08-04', 1920, 1920, 100),
    ('00000000-0000-0000-0000-000000000e21', '00000000-0000-0000-0000-000000000c04', '1.1.2', 'Xây dựng API OAuth2 / JWT Auth Service & Phân quyền', 'TASK', '2026-08-05', '2026-08-09', 2400, 2400, 100),
    ('00000000-0000-0000-0000-000000000e21', '00000000-0000-0000-0000-000000000c05', '1.1.3', 'Xây dựng API Catalog Nông sản & Tìm kiếm Elasticsearch', 'TASK', '2026-08-10', '2026-08-14', 2400, 2400, 75);

-- 8. CHANGE & LINK (CHANGE REQUESTS & POLYMORPHIC LINKS)
INSERT INTO plan_links (id, plan_id, planning_task_id, target_type, target_id, link_type, note, is_primary_execution) VALUES
    ('00000000-0000-0000-0000-000000000e31', '00000000-0000-0000-0000-000000000b01', '00000000-0000-0000-0000-000000000c04', 'ISSUE', '00000000-0000-0000-0000-000000000901', 'RELATED', 'Liên kết tới Issue Lỗi mất phiên đăng nhập trên iOS', false),
    ('00000000-0000-0000-0000-000000000e32', '00000000-0000-0000-0000-000000000b01', '00000000-0000-0000-0000-000000000c09', 'RISK', '00000000-0000-0000-0000-000000000801', 'BLOCKED_BY', 'Liên kết tới Rủi ro phụ thuộc API bên thứ ba VNPAY', false),
    ('00000000-0000-0000-0000-000000000e33', '00000000-0000-0000-0000-000000000b01', '00000000-0000-0000-0000-000000000c06', 'MILESTONE', '00000000-0000-0000-0000-000000001001', 'RELATED', 'Liên kết trực tiếp tới Mốc phát hành Release 1.0 của dự án', false),
    ('00000000-0000-0000-0000-000000000e34', '00000000-0000-0000-0000-000000000b01', '00000000-0000-0000-0000-000000000c03', 'EXECUTION_TASK', '00000000-0000-0000-0000-000000000411', 'RELATED', 'Thực thi giao diện Login UI Mobile & Design System', true),
    ('00000000-0000-0000-0000-000000000e35', '00000000-0000-0000-0000-000000000b01', '00000000-0000-0000-0000-000000000c04', 'EXECUTION_TASK', '00000000-0000-0000-0000-000000000412', 'RELATED', 'Thực thi API Spring Security Auth Single-Flight Token', true),
    ('00000000-0000-0000-0000-000000000e36', '00000000-0000-0000-0000-000000000b01', '00000000-0000-0000-0000-000000000c05', 'EXECUTION_TASK', '00000000-0000-0000-0000-000000000413', 'RELATED', 'Thực thi Query Elasticsearch Catalog Nông sản', true),
    ('00000000-0000-0000-0000-000000000e37', '00000000-0000-0000-0000-000000000b01', '00000000-0000-0000-0000-000000000c09', 'EXECUTION_TASK', '00000000-0000-0000-0000-000000000414', 'RELATED', 'Thực thi Tích hợp SDK VNPAY Checksum SHA256', true);

INSERT INTO plan_change_requests (id, plan_id, title, description, suggested_changes, status) VALUES
    ('00000000-0000-0000-0000-000000000e41', '00000000-0000-0000-0000-000000000b01', 'Đề xuất điều chỉnh thời gian Sprint 2 và tích hợp Redis Cluster', 'Bổ sung thêm 2 ngày kiểm thử cho tính năng giỏ hàng realtime', '{"reason": "Tăng độ ổn định cho Redis Cluster", "effort_impact_days": 2}', 'APPLIED'),
    ('00000000-0000-0000-0000-000000000e42', '00000000-0000-0000-0000-000000000b02', 'Yêu cầu bổ sung kiểm thử bảo mật PenTest cho Sprint 1', 'Bổ sung 1 task quét lỗ hổng bảo mật OWASP Top 10 cho Auth Service', '{"action": "ADD_TASK", "task_name": "PenTest Auth Service"}', 'PENDING');
