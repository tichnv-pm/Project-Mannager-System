-- =============================================================================
-- V10: DEMO LUONG CHUAN PHAT TRIEN PHAN MEM + DONG BO MOC BASELINE -> MILESTONE
-- (1) Moc baseline cua ke hoach (plan_task type=MILESTONE) duoc nang thanh
--     milestone trong bang milestones + plan_links targetType=MILESTONE
--     (quan ly trong tinh nang Milestone nhu task/meeting/risk/issue).
-- (2) Du an mau PRJ-CRM minh hoa duong di: tao du an -> Master/Detail plan
--     theo chuan FULL_SDL (17 phases) -> phan bo nguon luc -> giao viec
--     (execution task + plan_links EXECUTION_TASK is_primary_execution=true).
-- Chi chay o profile local (flyway.target=latest) - giong V2..V9.
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. MOC BASELINE -> MILESTONE (PRJ-AGILE - project 303)
--    MS-SP1 (master b01/c06) va MS-DTL1 (detail b02/e05) tro thanh milestone
--    that su trong tinh nang Milestone, lien ket nguoc ve ke hoach
-- ---------------------------------------------------------------------------
INSERT INTO milestones (id, project_id, name, description, planned_date, status, progress, note, created_by) VALUES
    ('00000000-0000-0000-0000-000000001006', '00000000-0000-0000-0000-000000000303',
     'Moc Hoan thanh Sprint 1 Demo & Review',
     'Chuyen tu moc baseline ke hoach MS-SP1 (master b01/c06) - Sprint Review trinh dien Dang nhap & Catalog',
     '2026-08-14', 'COMPLETED', 100,
     'Dong bo tu plan_task c06 (MS-SP1) - V10', '00000000-0000-0000-0000-000000000002'),
    ('00000000-0000-0000-0000-000000001007', '00000000-0000-0000-0000-000000000303',
     'Nghiem thu Sprint 1 voi Khach hang AgriCorp',
     'Chuyen tu moc baseline ke hoach MS-DTL1 (detail b02/e05) - Review san pham va nghiem thu cac cau chuyen nguoi dung',
     '2026-08-14', 'COMPLETED', 100,
     'Dong bo tu plan_task e05 (MS-DTL1) - V10', '00000000-0000-0000-0000-000000000002');

INSERT INTO plan_links (id, plan_id, planning_task_id, target_type, target_id, link_type, note, is_primary_execution, created_by) VALUES
    ('00000000-0000-0000-0000-000000000e51', '00000000-0000-0000-0000-000000000b01',
     '00000000-0000-0000-0000-000000000c06',
     'MILESTONE', '00000000-0000-0000-0000-000000001006', 'RELATED',
     'Moc baseline MS-SP1 - quan ly trong tinh nang Milestone (V10)', false,
     '00000000-0000-0000-0000-000000000002'),
    ('00000000-0000-0000-0000-000000000e52', '00000000-0000-0000-0000-000000000b02',
     '00000000-0000-0000-0000-000000000e05',
     'MILESTONE', '00000000-0000-0000-0000-000000001007', 'RELATED',
     'Moc baseline MS-DTL1 - quan ly trong tinh nang Milestone (V10)', false,
     '00000000-0000-0000-0000-000000000002');

-- ---------------------------------------------------------------------------
-- 2. DU AN MAU: PRJ-CRM (project 304) - "Hệ thống CRM Bán hàng - GlobeTel"
--    Buoc 1: Tao du an (PM pm.minh, khach hang GlobeTel)
-- ---------------------------------------------------------------------------
INSERT INTO projects (id, code, name, description, status, start_date, end_date, project_manager_id, customer_name, progress) VALUES
    ('00000000-0000-0000-0000-000000000304', 'PRJ-CRM', 'Hệ thống CRM Bán hàng - GlobeTel',
     'Du an mau demo luong chuan: tao du an -> Master/Detail plan theo FULL_SDL 17 phases -> phan bo nguon luc -> giao viec -> moc baseline synchronized sang tinh nang Milestone',
     'ACTIVE', '2026-09-01', '2027-03-31', '00000000-0000-0000-0000-000000000002', 'GlobeTel Viet Nam', 20);

INSERT INTO project_members (project_id, user_id, role) VALUES
    ('00000000-0000-0000-0000-000000000304', '00000000-0000-0000-0000-000000000002', 'PROJECT_MANAGER'),
    ('00000000-0000-0000-0000-000000000304', '00000000-0000-0000-0000-000000000003', 'DEVELOPER'),
    ('00000000-0000-0000-0000-000000000304', '00000000-0000-0000-0000-000000000004', 'TESTER'),
    ('00000000-0000-0000-0000-000000000304', '00000000-0000-0000-0000-000000000005', 'DEVELOPER');

INSERT INTO project_sequences (project_id, task_seq) VALUES
    ('00000000-0000-0000-0000-000000000304', 12);

-- ---------------------------------------------------------------------------
-- Buoc 2: Lap ke hoach theo chuan phat trien phan mem
--  2.1 MASTER PLAN: 17 phases FULL_SDL (khoi tao -> dong goi ban giao)
-- ---------------------------------------------------------------------------
INSERT INTO project_plans (id, project_id, plan_code, plan_name, description, plan_type, parent_plan_id, calendar_id, planned_start, planned_finish, status, progress, duration_minutes, note) VALUES
    ('00000000-0000-0000-0000-00000000d001', '00000000-0000-0000-0000-000000000304',
     'PF-CRM-MASTER', 'Master Plan: CRM theo chuan 17 phases phat trien phan mem (FULL_SDL)',
     'Ke hoach tong the he thong CRM tu khoi tao den ban giao - chuan Software Development Lifecycle',
     'MASTER', NULL, '00000000-0000-0000-0000-000000000a01', '2026-09-01', '2027-02-09', 'APPROVED', 20, 59520,
     'Master da phe duyet - baseline 1 kem theo');

INSERT INTO plan_tasks (id, plan_id, parent_id, wbs_code, task_code, task_name, description, task_type, outline_level, sequence_number, phase, work_package, owner_id, planned_start, planned_finish, duration_minutes, planned_effort_minutes, percent_complete, status, priority, schedule_mode, is_summary, is_milestone, is_critical) VALUES
    ('00000000-0000-0000-0000-00000000d101', '00000000-0000-0000-0000-00000000d001', NULL, '1', 'CRM-P01', '1. INITIATION', 'Khoi tao du an, chot pham vi va cuon sach', 'PHASE', 1, 1, '1. INITIATION', NULL, '00000000-0000-0000-0000-000000000002', '2026-09-01', '2026-09-07', 2400, 2400, 100, 'COMPLETED', 'HIGH', 'AUTO', false, false, false),
    ('00000000-0000-0000-0000-00000000d102', '00000000-0000-0000-0000-00000000d001', NULL, '2', 'CRM-P02', '2. REQUIREMENTS', 'Khao sat va phan tich yeu cau CRM', 'PHASE', 2, 2, '2. REQUIREMENTS', NULL, '00000000-0000-0000-0000-000000000002', '2026-09-08', '2026-09-21', 4800, 4800, 60, 'IN_PROGRESS', 'HIGH', 'AUTO', false, false, false),
    ('00000000-0000-0000-0000-00000000d103', '00000000-0000-0000-0000-00000000d001', NULL, '3', 'CRM-P03', '3. DESIGN', 'Thiet ke he thong CRM (Screen + Data model)', 'PHASE', 3, 3, '3. DESIGN', NULL, '00000000-0000-0000-0000-000000000002', '2026-09-22', '2026-10-05', 4800, 4800, 0, 'NOT_STARTED', 'HIGH', 'AUTO', false, false, false),
    ('00000000-0000-0000-0000-00000000d104', '00000000-0000-0000-0000-00000000d001', NULL, '4', 'CRM-P04', '4. ARCHITECTURE', 'Thiet ke kien truc Spring Boot modular monolith', 'PHASE', 4, 4, '4. ARCHITECTURE', NULL, '00000000-0000-0000-0000-000000000002', '2026-10-06', '2026-10-12', 2400, 2400, 0, 'NOT_STARTED', 'HIGH', 'AUTO', false, false, false),
    ('00000000-0000-0000-0000-00000000d105', '00000000-0000-0000-0000-00000000d001', NULL, '5', 'CRM-P05', '5. DEVELOPMENT', 'Phat trien chuc nang CRM core (xem detail plan PF-CRM-DEV)', 'PHASE', 5, 5, '5. DEVELOPMENT', NULL, '00000000-0000-0000-0000-000000000002', '2026-10-13', '2026-11-09', 9600, 9600, 0, 'NOT_STARTED', 'HIGH', 'AUTO', false, false, false),
    ('00000000-0000-0000-0000-00000000d106', '00000000-0000-0000-0000-00000000d001', NULL, '6', 'CRM-P06', '6. INTEGRATION', 'Tich hop API noi bo va ben thu 3', 'PHASE', 6, 6, '6. INTEGRATION', NULL, '00000000-0000-0000-0000-000000000002', '2026-11-10', '2026-11-16', 2400, 2400, 0, 'NOT_STARTED', 'HIGH', 'AUTO', false, false, false),
    ('00000000-0000-0000-0000-00000000d107', '00000000-0000-0000-0000-00000000d001', NULL, '7', 'CRM-P07', '7. TESTING', 'Kiem thu don vi va tich hop', 'PHASE', 7, 7, '7. TESTING', NULL, '00000000-0000-0000-0000-000000000002', '2026-11-17', '2026-11-30', 4800, 4800, 0, 'NOT_STARTED', 'HIGH', 'AUTO', false, false, false),
    ('00000000-0000-0000-0000-00000000d108', '00000000-0000-0000-0000-00000000d001', NULL, '8', 'CRM-P08', '8. QUALITY ASSURANCE', 'Dam bao chat luong va quy trinh QA', 'PHASE', 8, 8, '8. QUALITY ASSURANCE', NULL, '00000000-0000-0000-0000-000000000002', '2026-12-01', '2026-12-07', 2400, 2400, 0, 'NOT_STARTED', 'HIGH', 'AUTO', false, false, false),
    ('00000000-0000-0000-0000-00000000d109', '00000000-0000-0000-0000-00000000d001', NULL, '9', 'CRM-P09', '9. DEPLOYMENT', 'Trien khai moi truong staging/uat', 'PHASE', 9, 9, '9. DEPLOYMENT', NULL, '00000000-0000-0000-0000-000000000002', '2026-12-08', '2026-12-10', 1440, 1440, 0, 'NOT_STARTED', 'HIGH', 'AUTO', false, false, false),
    ('00000000-0000-0000-0000-00000000d110', '00000000-0000-0000-0000-00000000d001', NULL, '10', 'CRM-P10', '10. TRAINING', 'Dao tao nguoi dung CRM', 'PHASE', 10, 10, '10. TRAINING', NULL, '00000000-0000-0000-0000-000000000002', '2026-12-11', '2026-12-15', 1440, 1440, 0, 'NOT_STARTED', 'HIGH', 'AUTO', false, false, false),
    ('00000000-0000-0000-0000-00000000d111', '00000000-0000-0000-0000-00000000d001', NULL, '11', 'CRM-P11', '11. DOCUMENTATION', 'Tai lieu huong dan su dung va van hanh', 'PHASE', 11, 11, '11. DOCUMENTATION', NULL, '00000000-0000-0000-0000-000000000002', '2026-12-16', '2026-12-22', 2400, 2400, 0, 'NOT_STARTED', 'HIGH', 'AUTO', false, false, false),
    ('00000000-0000-0000-0000-00000000d112', '00000000-0000-0000-0000-00000000d001', NULL, '12', 'CRM-P12', '12. UAT', 'Nghiem thu voi nguoi dung cuoi (GlobeTel)', 'PHASE', 12, 12, '12. UAT', NULL, '00000000-0000-0000-0000-000000000002', '2026-12-23', '2026-12-29', 2400, 2400, 0, 'NOT_STARTED', 'HIGH', 'AUTO', false, false, false),
    ('00000000-0000-0000-0000-00000000d113', '00000000-0000-0000-0000-00000000d001', NULL, '13', 'CRM-P13', '13. SECURITY AUDIT', 'Rao soat bao mat he thong', 'PHASE', 13, 13, '13. SECURITY AUDIT', NULL, '00000000-0000-0000-0000-000000000002', '2026-12-30', '2027-01-05', 1440, 1440, 0, 'NOT_STARTED', 'HIGH', 'AUTO', false, false, false),
    ('00000000-0000-0000-0000-00000000d114', '00000000-0000-0000-0000-00000000d001', NULL, '14', 'CRM-P14', '14. PERFORMANCE', 'Kiem tra hieu nang va tai', 'PHASE', 14, 14, '14. PERFORMANCE', NULL, '00000000-0000-0000-0000-000000000002', '2027-01-06', '2027-01-08', 1440, 1440, 0, 'NOT_STARTED', 'HIGH', 'AUTO', false, false, false),
    ('00000000-0000-0000-0000-00000000d115', '00000000-0000-0000-0000-00000000d001', NULL, '15', 'CRM-P15', '15. SUPPORT & WARRANTY', 'Ho tro va bao hanh sau trien khai', 'PHASE', 15, 15, '15. SUPPORT & WARRANTY', NULL, '00000000-0000-0000-0000-000000000002', '2027-01-11', '2027-01-22', 4800, 4800, 0, 'NOT_STARTED', 'HIGH', 'AUTO', false, false, false),
    ('00000000-0000-0000-0000-00000000d116', '00000000-0000-0000-0000-00000000d001', NULL, '16', 'CRM-P16', '16. MAINTENANCE', 'Bao tri va nang cap', 'PHASE', 16, 16, '16. MAINTENANCE', NULL, '00000000-0000-0000-0000-000000000002', '2027-01-25', '2027-02-05', 4800, 4800, 0, 'NOT_STARTED', 'HIGH', 'AUTO', false, false, false),
    ('00000000-0000-0000-0000-00000000d117', '00000000-0000-0000-0000-00000000d001', NULL, '17', 'CRM-P17', '17. CLOSURE', 'Dong goi, ban giao va chot chi phi', 'PHASE', 17, 17, '17. CLOSURE', NULL, '00000000-0000-0000-0000-000000000002', '2027-02-08', '2027-02-09', 960, 960, 0, 'NOT_STARTED', 'HIGH', 'AUTO', false, false, false);

-- ---------------------------------------------------------------------------
--  2.2 DETAIL PLAN: PF-CRM-DEV - chi tiet giai doan DEVELOPMENT (phases 5)
-- ---------------------------------------------------------------------------
INSERT INTO project_plans (id, project_id, plan_code, plan_name, description, plan_type, parent_plan_id, calendar_id, planned_start, planned_finish, status, progress, duration_minutes, note) VALUES
    ('00000000-0000-0000-0000-00000000d002', '00000000-0000-0000-0000-000000000304',
     'PF-CRM-DEV', 'Detail Plan: Phat trien CRM Core (giao doan DEVELOPMENT)',
     'Chi tiet cong viec phat trien CRM core: entity, API auth, CRUD khach hang/don hang, dashboard bao cao',
     'DETAIL', '00000000-0000-0000-0000-00000000d001', '00000000-0000-0000-0000-000000000a01',
     '2026-10-13', '2026-11-09', 'ACTIVE', 0, 9600, 'Detail dang thuc thi theo master da phe duyet');

INSERT INTO plan_tasks (id, plan_id, parent_id, wbs_code, task_code, task_name, description, task_type, outline_level, sequence_number, phase, work_package, owner_id, planned_start, planned_finish, duration_minutes, planned_effort_minutes, percent_complete, status, priority, schedule_mode, is_summary, is_milestone, is_critical) VALUES
    ('00000000-0000-0000-0000-00000000d201', '00000000-0000-0000-0000-00000000d002', NULL, '1', 'CRM-D1', 'Khoi tao du lieu & Entity CRM', 'Bang du lieu PGSQL + Flyway migration cho khach hang, don hang, hop dong', 'TASK', 1, 1, '5. DEVELOPMENT', 'Data Layer', '00000000-0000-0000-0000-000000000003', '2026-10-13', '2026-10-20', 2880, 2400, 0, 'NOT_STARTED', 'HIGH', 'AUTO', false, false, false),
    ('00000000-0000-0000-0000-00000000d202', '00000000-0000-0000-0000-00000000d002', NULL, '2', 'CRM-D2', 'API Auth/JWT & Phan quyen RBAC', 'Login, refresh token, phan quyen theo chuc vu van hanh', 'TASK', 2, 2, '5. DEVELOPMENT', 'Backend', '00000000-0000-0000-0000-000000000005', '2026-10-21', '2026-10-28', 2880, 2400, 0, 'NOT_STARTED', 'HIGH', 'AUTO', false, false, false),
    ('00000000-0000-0000-0000-00000000d203', '00000000-0000-0000-0000-00000000d002', NULL, '3', 'CRM-D3', 'CRUD Khach hang & Don hang (API REST)', 'Restful API quan ly khach hang, don hang, lich su tuong tac', 'TASK', 3, 3, '5. DEVELOPMENT', 'Backend', '00000000-0000-0000-0000-000000000005', '2026-10-29', '2026-11-05', 2880, 2880, 0, 'NOT_STARTED', 'HIGH', 'AUTO', false, false, false),
    ('00000000-0000-0000-0000-00000000d204', '00000000-0000-0000-0000-00000000d002', NULL, '4', 'CRM-D4', 'Dashboard Bao cao & Xuat CSV', 'Bao cao doanh so, pipeline ban hang; xuat CSV tai du lieu', 'TASK', 4, 4, '5. DEVELOPMENT', 'Frontend', '00000000-0000-0000-0000-000000000004', '2026-11-04', '2026-11-09', 2880, 1920, 0, 'NOT_STARTED', 'HIGH', 'AUTO', false, false, false),
    ('00000000-0000-0000-0000-00000000d205', '00000000-0000-0000-0000-00000000d002', NULL, '5', 'CRM-D5', 'MS-DEV-RW: Review & nghiem thu giai doan DEV', 'Moc baseline giai doan DEVELOPMENT - Review sprint voi PM va Khach hang', 'MILESTONE', 5, 5, '5. DEVELOPMENT', 'Milestone', '00000000-0000-0000-0000-000000000002', '2026-11-09', '2026-11-09', 0, 0, 0, 'NOT_STARTED', 'HIGH', 'AUTO', false, true, false);

INSERT INTO plan_task_dependencies (id, plan_id, predecessor_task_id, successor_task_id, dependency_type, lag_minutes) VALUES
    ('00000000-0000-0000-0000-00000000d301', '00000000-0000-0000-0000-00000000d002', '00000000-0000-0000-0000-00000000d201', '00000000-0000-0000-0000-00000000d202', 'FS', 0),
    ('00000000-0000-0000-0000-00000000d302', '00000000-0000-0000-0000-00000000d002', '00000000-0000-0000-0000-00000000d202', '00000000-0000-0000-0000-00000000d203', 'FS', 0),
    ('00000000-0000-0000-0000-00000000d303', '00000000-0000-0000-0000-00000000d002', '00000000-0000-0000-0000-00000000d203', '00000000-0000-0000-0000-00000000d204', 'FS', 0),
    ('00000000-0000-0000-0000-00000000d304', '00000000-0000-0000-0000-00000000d002', '00000000-0000-0000-0000-00000000d204', '00000000-0000-0000-0000-00000000d205', 'FS', 0);

-- ---------------------------------------------------------------------------
-- Buoc 3: Phan bo nguon luc (allocation theo phan tram + vai tro tren task)
-- ---------------------------------------------------------------------------
INSERT INTO plan_task_resources (plan_id, task_id, resource_type, resource_id, role_on_task, allocation_percent, start_date, end_date, planned_effort_minutes, created_by) VALUES
    ('00000000-0000-0000-0000-00000000d002', '00000000-0000-0000-0000-00000000d201', 'USER', '00000000-0000-0000-0000-000000000003', 'Backend Dev', 60, '2026-10-13', '2026-10-20', 1440, '00000000-0000-0000-0000-000000000002'),
    ('00000000-0000-0000-0000-00000000d002', '00000000-0000-0000-0000-00000000d202', 'USER', '00000000-0000-0000-0000-000000000005', 'Backend Dev', 70, '2026-10-21', '2026-10-28', 1680, '00000000-0000-0000-0000-000000000002'),
    ('00000000-0000-0000-0000-00000000d002', '00000000-0000-0000-0000-00000000d203', 'USER', '00000000-0000-0000-0000-000000000003', 'Backend Dev', 50, '2026-10-29', '2026-11-05', 1200, '00000000-0000-0000-0000-000000000002'),
    ('00000000-0000-0000-0000-00000000d002', '00000000-0000-0000-0000-00000000d203', 'USER', '00000000-0000-0000-0000-000000000005', 'Fullstack', 50, '2026-10-29', '2026-11-05', 1200, '00000000-0000-0000-0000-000000000002'),
    ('00000000-0000-0000-0000-00000000d002', '00000000-0000-0000-0000-00000000d204', 'USER', '00000000-0000-0000-0000-000000000004', 'QA/Tester', 60, '2026-11-04', '2026-11-09', 1200, '00000000-0000-0000-0000-000000000002');

-- ---------------------------------------------------------------------------
-- Buoc 4: Giao viec (execution task + plan_links EXECUTION_TASK primary)
-- ---------------------------------------------------------------------------
INSERT INTO tasks (id, code, project_id, parent_task_id, title, description, reporter_id, assignee_id,
                   status, priority, type, source, start_date, due_date, actual_completed_at, progress,
                   blocked, blocker_reason, estimate_minutes, actual_minutes, created_at, created_by) VALUES
    ('00000000-0000-0000-0000-000000000421', 'PRJ-CRM-TASK-000001', '00000000-0000-0000-0000-000000000304', NULL,
     'Phat trien API Auth & Phan quyen CRM', 'Trien khai chuc nang CRM-D2 (API Auth/JWT + RBAC) theo detail plan PF-CRM-DEV',
     '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000005',
     'TODO', 'HIGH', 'FEATURE', 'MANUAL', '2026-10-21', '2026-10-28', NULL, 0,
     false, NULL, 2400, 0, now(), '00000000-0000-0000-0000-000000000002'),
    ('00000000-0000-0000-0000-000000000422', 'PRJ-CRM-TASK-000002', '00000000-0000-0000-0000-000000000304', NULL,
     'Kich ban + thuc thi kiem thu CRM', 'Viet kich ban va thuc thi kiem thu cho Dashboard Bao cao theo CRM-D4',
     '00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000004',
     'TODO', 'MEDIUM', 'TASK', 'MANUAL', '2026-11-04', '2026-11-09', NULL, 0,
     false, NULL, 1920, 0, now(), '00000000-0000-0000-0000-000000000002');

INSERT INTO plan_links (id, plan_id, planning_task_id, target_type, target_id, link_type, note, is_primary_execution, created_by) VALUES
    ('00000000-0000-0000-0000-00000000d401', '00000000-0000-0000-0000-00000000d002',
     '00000000-0000-0000-0000-00000000d202',
     'EXECUTION_TASK', '00000000-0000-0000-0000-000000000421', 'RELATED',
     'Giao viec CRM-D2 cho member5 (luong tu ke hoach -> cong viec)', true,
     '00000000-0000-0000-0000-000000000002'),
    ('00000000-0000-0000-0000-00000000d402', '00000000-0000-0000-0000-00000000d002',
     '00000000-0000-0000-0000-00000000d204',
     'EXECUTION_TASK', '00000000-0000-0000-0000-000000000422', 'RELATED',
     'Giao viec CRM-D4 cho member4 (kiem thu Dashboard)', true,
     '00000000-0000-0000-0000-000000000002');

-- ---------------------------------------------------------------------------
-- Buoc 5: Baseline cho master (version 1 + baseline 1 + snapshot cac phase chinh)
-- ---------------------------------------------------------------------------
INSERT INTO plan_versions (id, plan_id, version_no, status, note, version, created_at, created_by) VALUES
    ('00000000-0000-0000-0000-00000000d011', '00000000-0000-0000-0000-00000000d001', 1, 'ACTIVE',
     'Version 1 - Master chuan FULL_SDL (17 phases)', 1, now(), '00000000-0000-0000-0000-000000000002');

UPDATE project_plans SET active_version_id = '00000000-0000-0000-0000-00000000d011'
 WHERE id = '00000000-0000-0000-0000-00000000d001';

INSERT INTO plan_baselines (id, plan_id, version_id, baseline_num, description, captured_at, captured_by, version, created_at, created_by) VALUES
    ('00000000-0000-0000-0000-00000000d012', '00000000-0000-0000-0000-00000000d001',
     '00000000-0000-0000-0000-00000000d011', 1,
     'Baseline 1 - Phe duyet Master chuan FULL_SDL', now(), '00000000-0000-0000-0000-000000000002',
     1, now(), '00000000-0000-0000-0000-000000000002');

INSERT INTO plan_baseline_tasks (id, baseline_id, task_id, wbs_code, task_name, task_type, planned_start, planned_finish, duration_minutes, planned_effort_minutes, percent_complete) VALUES
    ('00000000-0000-0000-0000-00000000d013', '00000000-0000-0000-0000-00000000d012', '00000000-0000-0000-0000-00000000d101', '1', '1. INITIATION', 'PHASE', '2026-09-01', '2026-09-07', 2400, 2400, 0),
    ('00000000-0000-0000-0000-00000000d014', '00000000-0000-0000-0000-00000000d012', '00000000-0000-0000-0000-00000000d105', '5', '5. DEVELOPMENT', 'PHASE', '2026-10-13', '2026-11-09', 9600, 9600, 0),
    ('00000000-0000-0000-0000-00000000d015', '00000000-0000-0000-0000-00000000d012', '00000000-0000-0000-0000-00000000d107', '7', '7. TESTING', 'PHASE', '2026-11-17', '2026-11-30', 4800, 4800, 0),
    ('00000000-0000-0000-0000-00000000d016', '00000000-0000-0000-0000-00000000d012', '00000000-0000-0000-0000-00000000d109', '9', '9. DEPLOYMENT', 'PHASE', '2026-12-08', '2026-12-10', 1440, 1440, 0),
    ('00000000-0000-0000-0000-00000000d017', '00000000-0000-0000-0000-00000000d012', '00000000-0000-0000-0000-00000000d117', '17', '17. CLOSURE', 'PHASE', '2027-02-08', '2027-02-09', 960, 960, 0);

-- ---------------------------------------------------------------------------
-- Buoc 6: Moc ke hoach (moc baseline cua ke hoach) -> milestone + plan_links
--         (cung co che nhu task/meeting/risk/issue - V10, phan 1)
-- ---------------------------------------------------------------------------
INSERT INTO milestones (id, project_id, name, description, planned_date, status, progress, note, created_by) VALUES
    ('00000000-0000-0000-0000-000000001008', '00000000-0000-0000-0000-000000000304',
     'MS-DEV-RW: Review & nghiem thu giai doan DEV',
     'Moc baseline tu detail plan PF-CRM-DEV (task CRM-D5) - Review voi PM va Khach hang khi ket thuc DEVELOPMENT',
     '2026-11-09', 'NOT_STARTED', 0,
     'Dong bo tu plan_task d205 (CRM-D5) - V10', '00000000-0000-0000-0000-000000000002');

INSERT INTO plan_links (id, plan_id, planning_task_id, target_type, target_id, link_type, note, is_primary_execution, created_by) VALUES
    ('00000000-0000-0000-0000-00000000d403', '00000000-0000-0000-0000-00000000d002',
     '00000000-0000-0000-0000-00000000d205',
     'MILESTONE', '00000000-0000-0000-0000-000000001008', 'RELATED',
     'Moc baseline MS-DEV-RW - quan ly trong tinh nang Milestone (V10)', false,
     '00000000-0000-0000-0000-000000000002');