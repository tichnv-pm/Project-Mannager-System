-- =============================================================================
-- PM Daily Work Management - Flyway migration V4 - PROJECT PLANNING (v1.1)
-- Nguon: docs/database/02-data-dictionary.md (Phần B), docs/planning/*
-- Quyết định đã chốt 2026-08-07 (docs/planning/03 mục 14):
--   - resource_type = USER/ROLE/EXTERNAL (bỏ TEAM)
--   - lag âm được phép (allowNegativeLag, mặc định true)
--   - xóa summary còn con => từ chối (400 HAS_CHILDREN)
--   - baseline bất biến, chỉ soft-delete
--   - recalc nặng (> 200 task) => async job (plan_recalc_jobs)
--   - change suggestion => dual-approve cho plan lớn (PM + ADMIN)
-- =============================================================================

-- =============================================================================
-- 1. PLAN CALENDARS (tạo trước project_plans vì project_plans.calendar_id FK)
-- =============================================================================
CREATE TABLE plan_calendars (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name                varchar(100) NOT NULL,
    description         text,
    parent_calendar_id  uuid REFERENCES plan_calendars(id),
    organization_id     uuid,
    daily_working_hours int CHECK (daily_working_hours IS NULL OR daily_working_hours BETWEEN 1 AND 24),
    timezone            varchar(50),
    status              varchar(20) NOT NULL DEFAULT 'ACTIVE',
    deleted_at          timestamptz,
    deleted_by          uuid REFERENCES users(id),
    version             bigint NOT NULL DEFAULT 0,
    created_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid REFERENCES users(id),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    updated_by          uuid REFERENCES users(id),
    CONSTRAINT ck_plan_calendars_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE plan_calendar_working_days (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    calendar_id uuid NOT NULL REFERENCES plan_calendars(id) ON DELETE CASCADE,
    day_of_week int NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    is_working  boolean NOT NULL DEFAULT true,
    start_time  time,
    end_time    time
);

CREATE UNIQUE INDEX uk_plan_cal_day ON plan_calendar_working_days (calendar_id, day_of_week);

CREATE TABLE plan_calendar_exceptions (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    calendar_id    uuid NOT NULL REFERENCES plan_calendars(id) ON DELETE CASCADE,
    exception_date date NOT NULL,
    exception_type varchar(20) NOT NULL,
    note           varchar(200),
    CONSTRAINT ck_plan_cal_exc_type CHECK (exception_type IN ('NON_WORKING', 'WORKING'))
);

CREATE UNIQUE INDEX uk_plan_cal_exc_date ON plan_calendar_exceptions (calendar_id, exception_date);
CREATE INDEX ix_plan_cals_parent ON plan_calendars (parent_calendar_id);

-- =============================================================================
-- 2. PROJECT PLANS
-- =============================================================================
CREATE TABLE project_plans (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id        uuid NOT NULL REFERENCES projects(id),
    plan_code         varchar(50) NOT NULL,
    plan_name         varchar(200) NOT NULL,
    description       text,
    plan_type         varchar(30) NOT NULL,
    parent_plan_id    uuid REFERENCES project_plans(id),
    calendar_id       uuid REFERENCES plan_calendars(id),
    active_version_id uuid,
    planned_start     date,
    planned_finish    date,
    status            varchar(20) NOT NULL DEFAULT 'DRAFT',
    progress          int NOT NULL DEFAULT 0,
    duration_minutes  bigint,
    note              text,
    deleted_at        timestamptz,
    deleted_by        uuid REFERENCES users(id),
    version           bigint NOT NULL DEFAULT 0,
    created_at        timestamptz NOT NULL DEFAULT now(),
    created_by        uuid REFERENCES users(id),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    updated_by        uuid REFERENCES users(id),
    CONSTRAINT ck_project_plans_type CHECK (plan_type IN ('MASTER', 'DETAIL', 'TEMPLATE_INSTANCE')),
    CONSTRAINT ck_project_plans_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'ACTIVE',
        'ON_HOLD', 'COMPLETED', 'CANCELLED', 'ARCHIVED')),
    CONSTRAINT ck_project_plans_progress CHECK (progress BETWEEN 0 AND 100),
    CONSTRAINT ck_project_plans_dates CHECK (planned_start IS NULL OR planned_finish IS NULL
        OR planned_finish >= planned_start),
    CONSTRAINT ck_project_plans_parent CHECK (parent_plan_id IS NULL OR (plan_type = 'DETAIL'
        AND parent_plan_id <> id))
);

CREATE UNIQUE INDEX uk_project_plans_code_active ON project_plans (project_id, plan_code) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uk_project_plans_master_active ON project_plans (project_id)
    WHERE deleted_at IS NULL AND plan_type = 'MASTER' AND status IN ('APPROVED', 'ACTIVE');
CREATE INDEX ix_project_plans_project ON project_plans (project_id) WHERE deleted_at IS NULL;

-- =============================================================================
-- 3. PLAN VERSIONS
-- =============================================================================
CREATE TABLE plan_versions (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id       uuid NOT NULL REFERENCES project_plans(id) ON DELETE CASCADE,
    version_no    int NOT NULL,
    status        varchar(20) NOT NULL DEFAULT 'ACTIVE',
    snapshot_json jsonb,
    note          text,
    version       bigint NOT NULL DEFAULT 0,
    created_at    timestamptz NOT NULL DEFAULT now(),
    created_by    uuid REFERENCES users(id),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    updated_by    uuid REFERENCES users(id),
    CONSTRAINT ck_plan_versions_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE UNIQUE INDEX uk_plan_versions_plan_no ON plan_versions (plan_id, version_no);
CREATE UNIQUE INDEX uk_plan_versions_active ON plan_versions (plan_id) WHERE status = 'ACTIVE';

ALTER TABLE project_plans
    ADD CONSTRAINT fk_project_plans_active_version FOREIGN KEY (active_version_id) REFERENCES plan_versions(id);

-- =============================================================================
-- 4. PLAN TASKS (WBS)
-- =============================================================================
CREATE TABLE plan_tasks (
    id                       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id                  uuid NOT NULL REFERENCES project_plans(id),
    plan_version_id          uuid REFERENCES plan_versions(id),
    parent_id                uuid REFERENCES plan_tasks(id),
    wbs_code                 varchar(60) NOT NULL,
    task_code                varchar(40) NOT NULL,
    task_name                varchar(200) NOT NULL,
    description              text,
    task_type                varchar(30) NOT NULL,
    outline_level            int NOT NULL DEFAULT 1,
    sequence_number          int NOT NULL DEFAULT 1,
    phase                    varchar(50),
    work_package             varchar(50),
    deliverable              varchar(200),
    owner_id                 uuid REFERENCES users(id),
    planned_start            date,
    planned_finish           date,
    duration_minutes         bigint,
    planned_effort_minutes   int CHECK (planned_effort_minutes IS NULL OR planned_effort_minutes >= 0),
    actual_start             date,
    actual_finish            date,
    actual_effort_minutes    int CHECK (actual_effort_minutes IS NULL OR actual_effort_minutes >= 0),
    remaining_effort_minutes int CHECK (remaining_effort_minutes IS NULL OR remaining_effort_minutes >= 0),
    percent_complete         int NOT NULL DEFAULT 0,
    status                   varchar(20) NOT NULL DEFAULT 'NOT_STARTED',
    priority                 varchar(10),
    schedule_mode            varchar(10) NOT NULL DEFAULT 'AUTO',
    constraint_type          varchar(30),
    constraint_date          date,
    is_summary               boolean NOT NULL DEFAULT false,
    is_milestone             boolean NOT NULL DEFAULT false,
    is_critical              boolean NOT NULL DEFAULT false,
    deleted_at               timestamptz,
    deleted_by               uuid REFERENCES users(id),
    version                  bigint NOT NULL DEFAULT 0,
    created_at               timestamptz NOT NULL DEFAULT now(),
    created_by               uuid REFERENCES users(id),
    updated_at               timestamptz NOT NULL DEFAULT now(),
    updated_by               uuid REFERENCES users(id),
    CONSTRAINT ck_plan_tasks_type CHECK (task_type IN ('PHASE', 'SUMMARY_TASK', 'WORK_PACKAGE',
        'TASK', 'MILESTONE', 'EXTERNAL_TASK')),
    CONSTRAINT ck_plan_tasks_status CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED',
        'DELAYED', 'CANCELLED')),
    CONSTRAINT ck_plan_tasks_schedule CHECK (schedule_mode IN ('AUTO', 'MANUAL')),
    CONSTRAINT ck_plan_tasks_constraint CHECK (constraint_type IS NULL OR constraint_type IN (
        'FIXED_DATE', 'START_NO_EARLIER_THAN', 'START_NO_LATER_THAN', 'REMOVE_SCHEDULE')),
    CONSTRAINT ck_plan_tasks_priority CHECK (priority IS NULL OR priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_plan_tasks_progress CHECK (percent_complete BETWEEN 0 AND 100),
    CONSTRAINT ck_plan_tasks_milestone CHECK (task_type <> 'MILESTONE'
        OR (duration_minutes IS NULL OR duration_minutes = 0)),
    CONSTRAINT ck_plan_tasks_dates CHECK (planned_start IS NULL OR planned_finish IS NULL
        OR planned_finish >= planned_start)
);

CREATE UNIQUE INDEX uk_plan_tasks_code ON plan_tasks (plan_id, task_code) WHERE deleted_at IS NULL;
CREATE INDEX ix_plan_tasks_parent ON plan_tasks (parent_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_plan_tasks_plan_wbs ON plan_tasks (plan_id, wbs_code) WHERE deleted_at IS NULL;

-- =============================================================================
-- 5. PLAN TASK DEPENDENCIES
-- =============================================================================
CREATE TABLE plan_task_dependencies (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id             uuid NOT NULL REFERENCES project_plans(id),
    predecessor_task_id uuid NOT NULL REFERENCES plan_tasks(id) ON DELETE CASCADE,
    successor_task_id   uuid NOT NULL REFERENCES plan_tasks(id) ON DELETE CASCADE,
    dependency_type     varchar(10) NOT NULL,
    lag_minutes         int NOT NULL DEFAULT 0,
    created_at          timestamptz NOT NULL DEFAULT now(),
    created_by          uuid REFERENCES users(id),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    updated_by          uuid REFERENCES users(id),
    CONSTRAINT ck_plan_dep_type CHECK (dependency_type IN ('FS', 'SS', 'FF', 'SF')),
    CONSTRAINT ck_plan_dep_not_self CHECK (predecessor_task_id <> successor_task_id)
);

CREATE UNIQUE INDEX uk_plan_dep_unique ON plan_task_dependencies
    (plan_id, predecessor_task_id, successor_task_id, dependency_type);
CREATE INDEX ix_plan_dep_predecessor ON plan_task_dependencies (predecessor_task_id);
CREATE INDEX ix_plan_dep_successor ON plan_task_dependencies (successor_task_id);

-- =============================================================================
-- 6. PLAN TASK RESOURCES
-- =============================================================================
CREATE TABLE plan_task_resources (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id                 uuid NOT NULL REFERENCES project_plans(id),
    task_id                 uuid NOT NULL REFERENCES plan_tasks(id) ON DELETE CASCADE,
    resource_type           varchar(20) NOT NULL,
    resource_id             uuid NOT NULL,
    role_on_task            varchar(50),
    allocation_percent      int NOT NULL DEFAULT 100,
    start_date              date,
    end_date                date,
    planned_effort_minutes  int CHECK (planned_effort_minutes IS NULL OR planned_effort_minutes >= 0),
    created_at              timestamptz NOT NULL DEFAULT now(),
    created_by              uuid REFERENCES users(id),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    updated_by              uuid REFERENCES users(id),
    CONSTRAINT ck_plan_res_type CHECK (resource_type IN ('USER', 'ROLE', 'EXTERNAL')),
    CONSTRAINT ck_plan_res_alloc CHECK (allocation_percent BETWEEN 0 AND 100)
);

CREATE INDEX ix_plan_res_task ON plan_task_resources (task_id);
CREATE INDEX ix_plan_res_resource ON plan_task_resources (resource_type, resource_id);

-- =============================================================================
-- 7. RESOURCE CAPACITIES
-- =============================================================================
CREATE TABLE resource_capacities (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    resource_type    varchar(20) NOT NULL,
    resource_id      uuid NOT NULL,
    capacity_percent int NOT NULL DEFAULT 100,
    start_date       date NOT NULL,
    end_date         date,
    source           varchar(10) NOT NULL DEFAULT 'ORG',
    version          bigint NOT NULL DEFAULT 0,
    created_at       timestamptz NOT NULL DEFAULT now(),
    created_by       uuid REFERENCES users(id),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    updated_by       uuid REFERENCES users(id),
    CONSTRAINT ck_res_cap_type CHECK (resource_type IN ('USER', 'ROLE')),
    CONSTRAINT ck_res_cap_percent CHECK (capacity_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_res_cap_source CHECK (source IN ('ORG', 'PROJECT'))
);

CREATE UNIQUE INDEX uk_resource_capacities ON resource_capacities (resource_type, resource_id, start_date);

-- =============================================================================
-- 8. PLAN BASELINES (bất biến — chỉ soft-delete)
-- =============================================================================
CREATE TABLE plan_baselines (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id     uuid NOT NULL REFERENCES project_plans(id),
    version_id  uuid REFERENCES plan_versions(id),
    baseline_num int NOT NULL,
    description text,
    captured_at timestamptz NOT NULL DEFAULT now(),
    captured_by uuid REFERENCES users(id),
    deleted_at  timestamptz,
    deleted_by  uuid REFERENCES users(id),
    version     bigint NOT NULL DEFAULT 0,
    created_at  timestamptz NOT NULL DEFAULT now(),
    created_by  uuid REFERENCES users(id),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    updated_by  uuid REFERENCES users(id)
);

CREATE UNIQUE INDEX uk_plan_baseline_num ON plan_baselines (plan_id, baseline_num);

CREATE TABLE plan_baseline_tasks (
    id                       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    baseline_id              uuid NOT NULL REFERENCES plan_baselines(id) ON DELETE CASCADE,
    task_id                  uuid REFERENCES plan_tasks(id),
    wbs_code                 varchar(60) NOT NULL,
    task_name                varchar(200) NOT NULL,
    task_type                varchar(30) NOT NULL,
    planned_start            date,
    planned_finish           date,
    duration_minutes         int,
    planned_effort_minutes   int,
    percent_complete         int NOT NULL DEFAULT 0,
    resources_snapshot       jsonb,
    created_at               timestamptz NOT NULL DEFAULT now(),
    created_by               uuid REFERENCES users(id)
);

CREATE INDEX ix_plan_baseline_tasks_baseline ON plan_baseline_tasks (baseline_id);

-- =============================================================================
-- 9. PLAN LINKS (polymorphic)
-- =============================================================================
CREATE TABLE plan_links (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id            uuid NOT NULL REFERENCES project_plans(id),
    planning_task_id   uuid NOT NULL REFERENCES plan_tasks(id) ON DELETE CASCADE,
    target_type        varchar(30) NOT NULL,
    target_id          uuid NOT NULL,
    link_type          varchar(20) NOT NULL DEFAULT 'RELATED',
    note               varchar(255),
    is_primary_execution boolean NOT NULL DEFAULT false,
    deleted_at         timestamptz,
    deleted_by         uuid REFERENCES users(id),
    version            bigint NOT NULL DEFAULT 0,
    created_at         timestamptz NOT NULL DEFAULT now(),
    created_by         uuid REFERENCES users(id),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    updated_by         uuid REFERENCES users(id),
    CONSTRAINT ck_plan_links_target_type CHECK (target_type IN ('EXECUTION_TASK', 'ISSUE', 'RISK', 'MILESTONE')),
    CONSTRAINT ck_plan_links_link_type CHECK (link_type IN ('RELATED', 'BLOCKED_BY'))
);

CREATE UNIQUE INDEX uk_plan_links_primary ON plan_links (planning_task_id)
    WHERE is_primary_execution = true AND deleted_at IS NULL;
CREATE INDEX ix_plan_links_target ON plan_links (target_type, target_id) WHERE deleted_at IS NULL;

-- =============================================================================
-- 10. PLAN CHANGE (requests + histories)
-- =============================================================================
CREATE TABLE plan_change_requests (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id            uuid NOT NULL REFERENCES project_plans(id),
    source_type        varchar(30),
    source_id          uuid,
    title              varchar(200) NOT NULL,
    description        text NOT NULL,
    suggested_changes  jsonb NOT NULL,
    status             varchar(20) NOT NULL DEFAULT 'PENDING',
    reviewed_by        uuid REFERENCES users(id),
    reviewed_at        timestamptz,
    deleted_at         timestamptz,
    deleted_by         uuid REFERENCES users(id),
    version            bigint NOT NULL DEFAULT 0,
    created_at         timestamptz NOT NULL DEFAULT now(),
    created_by         uuid REFERENCES users(id),
    updated_at         timestamptz NOT NULL DEFAULT now(),
    updated_by         uuid REFERENCES users(id),
    CONSTRAINT ck_plan_chg_req_status CHECK (status IN ('PENDING', 'APPLIED', 'REJECTED'))
);

CREATE INDEX ix_plan_chg_req_plan_status ON plan_change_requests (plan_id, status) WHERE deleted_at IS NULL;

CREATE TABLE plan_change_histories (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id           uuid NOT NULL REFERENCES project_plans(id),
    change_type       varchar(40) NOT NULL,
    entity_type       varchar(30) NOT NULL,
    entity_id         uuid,
    field_changed     varchar(100),
    old_value         text,
    new_value         text,
    reason            text,
    change_request_id uuid REFERENCES plan_change_requests(id),
    changed_at        timestamptz NOT NULL DEFAULT now(),
    changed_by        uuid REFERENCES users(id),
    deleted_at        timestamptz,
    deleted_by        uuid REFERENCES users(id),
    created_at        timestamptz NOT NULL DEFAULT now(),
    created_by        uuid REFERENCES users(id),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    updated_by        uuid REFERENCES users(id)
);

CREATE INDEX ix_plan_chg_hist_plan ON plan_change_histories (plan_id, changed_at) WHERE deleted_at IS NULL;

-- =============================================================================
-- 11. PLAN TEMPLATES
-- =============================================================================
CREATE TABLE plan_templates (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    template_code   varchar(50) NOT NULL,
    template_name   varchar(100) NOT NULL,
    description     text,
    template_type   varchar(30) NOT NULL,
    phase_set       jsonb NOT NULL DEFAULT '[]',
    version_no      int NOT NULL DEFAULT 1,
    status          varchar(20) NOT NULL DEFAULT 'DRAFT',
    organization_id uuid,
    is_built_in     boolean NOT NULL DEFAULT false,
    deleted_at      timestamptz,
    deleted_by      uuid REFERENCES users(id),
    version         bigint NOT NULL DEFAULT 0,
    created_at      timestamptz NOT NULL DEFAULT now(),
    created_by      uuid REFERENCES users(id),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    updated_by      uuid REFERENCES users(id),
    CONSTRAINT ck_plan_tpl_type CHECK (template_type IN ('FULL_LIFECYCLE', 'PARTIAL')),
    CONSTRAINT ck_plan_tpl_status CHECK (status IN ('DRAFT', 'PUBLISHED'))
);

CREATE UNIQUE INDEX uk_plan_templates_code ON plan_templates (template_code) WHERE deleted_at IS NULL;

CREATE TABLE plan_template_tasks (
    id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id            uuid NOT NULL REFERENCES plan_templates(id) ON DELETE CASCADE,
    parent_id              uuid REFERENCES plan_template_tasks(id),
    wbs_code_md            varchar(60) NOT NULL,
    task_name              varchar(200) NOT NULL,
    task_type              varchar(30) NOT NULL,
    planned_effort_minutes int CHECK (planned_effort_minutes IS NULL OR planned_effort_minutes >= 0),
    default_role           varchar(50),
    sequence_number        int NOT NULL DEFAULT 1,
    created_at             timestamptz NOT NULL DEFAULT now(),
    created_by             uuid REFERENCES users(id)
);

CREATE INDEX ix_plan_template_tasks_parent ON plan_template_tasks (template_id, parent_id);

-- =============================================================================
-- 12. PORTFOLIOS
-- =============================================================================
CREATE TABLE portfolios (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name        varchar(100) NOT NULL,
    owner_id    uuid NOT NULL REFERENCES users(id),
    description text,
    is_shared   boolean NOT NULL DEFAULT false,
    deleted_at  timestamptz,
    deleted_by  uuid REFERENCES users(id),
    version     bigint NOT NULL DEFAULT 0,
    created_at  timestamptz NOT NULL DEFAULT now(),
    created_by  uuid REFERENCES users(id),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    updated_by  uuid REFERENCES users(id)
);

CREATE INDEX ix_portfolios_owner ON portfolios (owner_id) WHERE deleted_at IS NULL;

CREATE TABLE portfolio_projects (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    portfolio_id uuid NOT NULL REFERENCES portfolios(id) ON DELETE CASCADE,
    project_id   uuid NOT NULL REFERENCES projects(id),
    weight       int NOT NULL DEFAULT 1,
    created_at   timestamptz NOT NULL DEFAULT now(),
    created_by   uuid REFERENCES users(id)
);

CREATE UNIQUE INDEX uk_portfolio_projects ON portfolio_projects (portfolio_id, project_id);

-- =============================================================================
-- 13. PLAN RECALC JOBS (async scheduling — quyết định đã chốt)
-- =============================================================================
CREATE TABLE plan_recalc_jobs (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id       uuid NOT NULL REFERENCES project_plans(id),
    status        varchar(20) NOT NULL DEFAULT 'PENDING',
    requested_by  uuid REFERENCES users(id),
    requested_at  timestamptz NOT NULL DEFAULT now(),
    started_at    timestamptz,
    finished_at   timestamptz,
    error_message text,
    version       bigint NOT NULL DEFAULT 0,
    created_at    timestamptz NOT NULL DEFAULT now(),
    created_by    uuid REFERENCES users(id),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    updated_by    uuid REFERENCES users(id),
    CONSTRAINT ck_plan_recalc_status CHECK (status IN ('PENDING', 'PROCESSING', 'DONE', 'FAILED'))
);

CREATE INDEX ix_plan_recalc_plan_status ON plan_recalc_jobs (plan_id, status);
CREATE INDEX ix_plan_recalc_status ON plan_recalc_jobs (status) WHERE status IN ('PENDING', 'PROCESSING');

-- =============================================================================
-- 14. SEED PERMISSIONS (plan:* — docs/planning/04; đồng bộ vào permissions + role)
--     Chạy được trên DB đã có dữ liệu (không dùng INSERT cứng ID vì có thể trùng).
-- =============================================================================
INSERT INTO permissions (code, name, description) VALUES
    ('plan:view',     'Xem kế hoạch',          'Xem plan, WBS, Gantt, baseline, portfolio'),
    ('plan:create',   'Tạo kế hoạch',          'Tạo plan mới (master/detail)'),
    ('plan:update',   'Sửa kế hoạch',          'Sửa WBS, task, dependency, calendar của plan'),
    ('plan:delete',   'Xóa kế hoạch',          'Xóa mềm plan'),
    ('plan:approve',  'Duyệt kế hoạch',        'SUBMITTED → APPROVED; kích hoạt ACTIVE'),
    ('plan:version',  'Phiên bản kế hoạch',    'Tạo phiên bản plan mới (snapshot)'),
    ('plan:baseline', 'Baseline',              'Tạo baseline + xem variance'),
    ('plan:change',   'Thay đổi kế hoạch',     'Tạo/duyệt change history sau APPROVED'),
    ('plan:resource', 'Nguồn lực',             'Gán resource, chỉnh capacity, xem workload'),
    ('plan:template', 'Template',              'Quản lý template (CRUD/version/clone)'),
    ('plan:link',     'Liên kết kế hoạch',     'Tạo/xóa liên kết plan_links'),
    ('plan:schedule', 'Lập lịch',              'Trigger recalc / xem warnings & critical path')
ON CONFLICT (code) DO NOTHING;

-- ADMIN: toàn bộ quyền mới
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'ADMIN' AND p.code LIKE 'plan:%'
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- PROJECT_MANAGER: tất cả trừ plan:template
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'PROJECT_MANAGER' AND p.code LIKE 'plan:%' AND p.code <> 'plan:template'
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- PROJECT_MEMBER: chỉ plan:view
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'PROJECT_MEMBER' AND p.code = 'plan:view'
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- VIEWER: chỉ plan:view
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.code = 'VIEWER' AND p.code = 'plan:view'
  AND NOT EXISTS (SELECT 1 FROM role_permissions rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
