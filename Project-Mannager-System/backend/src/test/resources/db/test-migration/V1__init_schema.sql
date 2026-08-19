-- =============================================================================
-- PM Daily Work Management - Flyway migration V1 (H2-compatible, TEST ONLY)
-- Ban nay danh cho profile test (H2 MODE=PostgreSQL).
-- Khi chinh sua V1__init_schema.sql (PostgreSQL), phai dong bo ban nay.
-- Khac biet chinh: RANDOM_UUID() thay gen_random_uuid(), json thay jsonb,
-- CAST(... AS DATE) thay ::date.
-- =============================================================================

-- =============================================================================
-- 1. USER & AUTH
-- =============================================================================

CREATE TABLE users (
    id                    uuid DEFAULT RANDOM_UUID() PRIMARY KEY,
    username              varchar(50)  NOT NULL,
    email                 varchar(100) NOT NULL,
    full_name             varchar(100) NOT NULL,
    password_hash         varchar(100) NOT NULL,
    status                varchar(20)  NOT NULL DEFAULT 'ACTIVE',
    failed_login_attempts int          NOT NULL DEFAULT 0,
    locked_until          timestamp with time zone,
    last_login_at         timestamp with time zone,
    version               bigint       NOT NULL DEFAULT 0,
    created_at            timestamp with time zone  NOT NULL DEFAULT now(),
    created_by            uuid         REFERENCES users(id),
    updated_at            timestamp with time zone  NOT NULL DEFAULT now(),
    updated_by            uuid         REFERENCES users(id),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE roles (
    id          uuid DEFAULT RANDOM_UUID() PRIMARY KEY,
    code        varchar(50)  NOT NULL,
    name        varchar(100) NOT NULL,
    description varchar(255),
    version     bigint       NOT NULL DEFAULT 0,
    created_at  timestamp with time zone  NOT NULL DEFAULT now(),
    created_by  uuid         REFERENCES users(id),
    updated_at  timestamp with time zone  NOT NULL DEFAULT now(),
    updated_by  uuid         REFERENCES users(id)
);

CREATE TABLE permissions (
    id          uuid DEFAULT RANDOM_UUID() PRIMARY KEY,
    code        varchar(100) NOT NULL,
    name        varchar(100) NOT NULL,
    description varchar(255),
    version     bigint       NOT NULL DEFAULT 0,
    created_at  timestamp with time zone  NOT NULL DEFAULT now(),
    created_by  uuid         REFERENCES users(id),
    updated_at  timestamp with time zone  NOT NULL DEFAULT now(),
    updated_by  uuid         REFERENCES users(id)
);

CREATE TABLE user_roles (
    id         uuid DEFAULT RANDOM_UUID() PRIMARY KEY,
    user_id    uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id    uuid        NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    created_by uuid        REFERENCES users(id)
);

CREATE TABLE role_permissions (
    id            uuid DEFAULT RANDOM_UUID() PRIMARY KEY,
    role_id       uuid        NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id uuid        NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,
    created_at    timestamp with time zone NOT NULL DEFAULT now(),
    created_by    uuid        REFERENCES users(id)
);

CREATE TABLE refresh_tokens (
    id          uuid DEFAULT RANDOM_UUID() PRIMARY KEY,
    user_id     uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash  varchar(64) NOT NULL,
    expires_at  timestamp with time zone NOT NULL,
    revoked_at  timestamp with time zone,
    replaced_by uuid,
    created_at  timestamp with time zone NOT NULL DEFAULT now(),
    created_by  uuid        REFERENCES users(id)
);

-- =============================================================================
-- 2. PROJECT
-- =============================================================================

CREATE TABLE projects (
    id                 uuid DEFAULT RANDOM_UUID() PRIMARY KEY,
    code               varchar(20)  NOT NULL,
    name               varchar(100) NOT NULL,
    description        text,
    status             varchar(20)  NOT NULL DEFAULT 'PLANNING',
    start_date         date,
    end_date           date,
    project_manager_id uuid        REFERENCES users(id),
    customer_name      varchar(100),
    progress           int         NOT NULL DEFAULT 0,
    note               text,
    deleted_at         timestamp with time zone,
    deleted_by         uuid        REFERENCES users(id),
    version            bigint      NOT NULL DEFAULT 0,
    created_at         timestamp with time zone NOT NULL DEFAULT now(),
    created_by         uuid        REFERENCES users(id),
    updated_at         timestamp with time zone NOT NULL DEFAULT now(),
    updated_by         uuid        REFERENCES users(id),
    CONSTRAINT ck_projects_status CHECK (status IN ('PLANNING', 'ACTIVE', 'ON_HOLD', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_projects_progress CHECK (progress BETWEEN 0 AND 100),
    CONSTRAINT ck_projects_dates CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date)
);

CREATE TABLE project_members (
    id         uuid DEFAULT RANDOM_UUID() PRIMARY KEY,
    project_id uuid        NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    user_id    uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role       varchar(30) NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    created_by uuid        REFERENCES users(id),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_by uuid        REFERENCES users(id),
    CONSTRAINT ck_project_members_role CHECK (role IN (
        'PROJECT_MANAGER', 'TECH_LEAD', 'BUSINESS_ANALYST', 'DEVELOPER', 'TESTER', 'DEVOPS', 'MEMBER'
    ))
);

-- =============================================================================
-- 3. TASK (táº¡o trÆ°á»›c action_items vÃ  attachments vÃ¬ chÃºng tham chiáº¿u tá»›i Ä‘Ã¢y)
-- =============================================================================

CREATE TABLE tags (
    id         uuid DEFAULT RANDOM_UUID() PRIMARY KEY,
    name       varchar(50)  NOT NULL,
    color      varchar(20),
    version    bigint       NOT NULL DEFAULT 0,
    created_at timestamp with time zone  NOT NULL DEFAULT now(),
    created_by uuid         REFERENCES users(id),
    updated_at timestamp with time zone  NOT NULL DEFAULT now(),
    updated_by uuid         REFERENCES users(id)
);

CREATE TABLE tasks (
    id                  uuid DEFAULT RANDOM_UUID() PRIMARY KEY,
    code                varchar(40)  NOT NULL,
    project_id          uuid         NOT NULL REFERENCES projects(id),
    parent_task_id      uuid         REFERENCES tasks(id),
    title               varchar(200) NOT NULL,
    description         text,
    reporter_id         uuid         NOT NULL REFERENCES users(id),
    assignee_id         uuid         REFERENCES users(id),
    status              varchar(20)  NOT NULL DEFAULT 'TODO',
    priority            varchar(10)  NOT NULL DEFAULT 'MEDIUM',
    type                varchar(20)  NOT NULL DEFAULT 'TASK',
    source              varchar(20),
    start_date          date,
    due_date            date,
    actual_completed_at timestamp with time zone,
    progress            int          NOT NULL DEFAULT 0,
    blocked             boolean      NOT NULL DEFAULT false,
    blocker_reason      varchar(500),
    estimate_minutes    int,
    actual_minutes      int,
    notes               text,
    deleted_at          timestamp with time zone,
    deleted_by          uuid         REFERENCES users(id),
    version             bigint       NOT NULL DEFAULT 0,
    created_at          timestamp with time zone  NOT NULL DEFAULT now(),
    created_by          uuid         REFERENCES users(id),
    updated_at          timestamp with time zone  NOT NULL DEFAULT now(),
    updated_by          uuid         REFERENCES users(id),
    CONSTRAINT ck_tasks_status CHECK (status IN ('TODO', 'IN_PROGRESS', 'BLOCKED', 'REVIEW', 'DONE', 'CANCELLED')),
    CONSTRAINT ck_tasks_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_tasks_type CHECK (type IN ('FEATURE', 'BUG', 'IMPROVEMENT', 'TASK', 'OTHER')),
    CONSTRAINT ck_tasks_source CHECK (source IS NULL OR source IN ('MANUAL', 'MEETING', 'ACTION_ITEM', 'ISSUE', 'OTHER')),
    CONSTRAINT ck_tasks_progress CHECK (progress BETWEEN 0 AND 100),
    CONSTRAINT ck_tasks_dates CHECK (due_date IS NULL OR start_date IS NULL OR due_date >= start_date),
    CONSTRAINT ck_tasks_done CHECK (status <> 'DONE' OR progress = 100),
    CONSTRAINT ck_tasks_done_at CHECK (status <> 'DONE' OR actual_completed_at IS NOT NULL),
    CONSTRAINT ck_tasks_blocked CHECK (blocked = false OR blocker_reason IS NOT NULL),
    CONSTRAINT ck_tasks_estimate CHECK (estimate_minutes IS NULL OR estimate_minutes >= 0),
    CONSTRAINT ck_tasks_actual CHECK (actual_minutes IS NULL OR actual_minutes >= 0)
);

CREATE TABLE task_assignees (
    id         uuid DEFAULT RANDOM_UUID() PRIMARY KEY,
    task_id    uuid        NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    user_id    uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    created_by uuid        REFERENCES users(id)
);

CREATE TABLE task_watchers (
    id         uuid DEFAULT RANDOM_UUID() PRIMARY KEY,
    task_id    uuid        NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    user_id    uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    created_by uuid        REFERENCES users(id)
);

CREATE TABLE task_tags (
    id         uuid DEFAULT RANDOM_UUID() PRIMARY KEY,
    task_id    uuid        NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    tag_id     uuid        NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    created_by uuid        REFERENCES users(id)
);

CREATE TABLE task_comments (
    id         uuid DEFAULT RANDOM_UUID() PRIMARY KEY,
    task_id    uuid        NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    content    text        NOT NULL,
    deleted_at timestamp with time zone,
    deleted_by uuid        REFERENCES users(id),
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    created_by uuid        REFERENCES users(id),
    updated_at timestamp with time zone NOT NULL DEFAULT now(),
    updated_by uuid        REFERENCES users(id),
    CONSTRAINT ck_task_comments_len CHECK (char_length(content) BETWEEN 1 AND 2000)
);

-- =============================================================================
-- 4. MEETING & ACTION ITEM (action_items tham chiáº¿u tasks â€” pháº£i Ä‘á»©ng sau tasks)
-- =============================================================================

CREATE TABLE meetings (
    id               uuid DEFAULT RANDOM_UUID() PRIMARY KEY,
    project_id       uuid         NOT NULL REFERENCES projects(id),
    title            varchar(200) NOT NULL,
    start_time       timestamp with time zone  NOT NULL,
    end_time         timestamp with time zone  NOT NULL,
    location         varchar(255),
    meeting_link     varchar(500),
    chairperson_id   uuid         NOT NULL REFERENCES users(id),
    status           varchar(20)  NOT NULL DEFAULT 'SCHEDULED',
    agenda           text,
    content          text,
    conclusion       text,
    deleted_at       timestamp with time zone,
    deleted_by       uuid         REFERENCES users(id),
    version          bigint       NOT NULL DEFAULT 0,
    created_at       timestamp with time zone  NOT NULL DEFAULT now(),
    created_by       uuid         REFERENCES users(id),
    updated_at       timestamp with time zone  NOT NULL DEFAULT now(),
    updated_by       uuid         REFERENCES users(id),
    CONSTRAINT ck_meetings_status CHECK (status IN ('SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_meetings_time CHECK (end_time > start_time),
    CONSTRAINT ck_meetings_place CHECK (location IS NOT NULL OR meeting_link IS NOT NULL)
);

CREATE TABLE meeting_participants (
    id         uuid DEFAULT RANDOM_UUID() PRIMARY KEY,
    meeting_id uuid        NOT NULL REFERENCES meetings(id) ON DELETE CASCADE,
    user_id    uuid        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    created_by uuid        REFERENCES users(id)
);

CREATE TABLE action_items (
    id             uuid DEFAULT RANDOM_UUID() PRIMARY KEY,
    meeting_id     uuid         NOT NULL REFERENCES meetings(id),
    project_id     uuid         NOT NULL REFERENCES projects(id),
    title          varchar(200) NOT NULL,
    description    text,
    assignee_id    uuid         NOT NULL REFERENCES users(id),
    due_date       date,
    priority       varchar(10)  NOT NULL DEFAULT 'MEDIUM',
    status         varchar(20)  NOT NULL DEFAULT 'OPEN',
    progress       int          NOT NULL DEFAULT 0,
    linked_task_id uuid         REFERENCES tasks(id),
    deleted_at     timestamp with time zone,
    deleted_by     uuid         REFERENCES users(id),
    version        bigint       NOT NULL DEFAULT 0,
    created_at     timestamp with time zone  NOT NULL DEFAULT now(),
    created_by     uuid         REFERENCES users(id),
    updated_at     timestamp with time zone  NOT NULL DEFAULT now(),
    updated_by     uuid         REFERENCES users(id),
    CONSTRAINT ck_action_items_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_action_items_status CHECK (status IN ('OPEN', 'IN_PROGRESS', 'DONE', 'CANCELLED')),
    CONSTRAINT ck_action_items_progress CHECK (progress BETWEEN 0 AND 100),
    CONSTRAINT ck_action_items_done CHECK (status <> 'DONE' OR progress = 100)
);

CREATE TABLE attachments (
    id           uuid DEFAULT RANDOM_UUID() PRIMARY KEY,
    task_id      uuid         REFERENCES tasks(id) ON DELETE CASCADE,
    meeting_id   uuid         REFERENCES meetings(id) ON DELETE CASCADE,
    file_name    varchar(255) NOT NULL,
    file_path    varchar(500) NOT NULL,
    content_type varchar(100),
    size_bytes   bigint       NOT NULL,
    uploaded_by  uuid         REFERENCES users(id),
    deleted_at   timestamp with time zone,
    deleted_by   uuid         REFERENCES users(id),
    created_at   timestamp with time zone  NOT NULL DEFAULT now(),
    CONSTRAINT ck_attachments_owner CHECK (task_id IS NOT NULL OR meeting_id IS NOT NULL),
    CONSTRAINT ck_attachments_size CHECK (size_bytes > 0)
);

-- =============================================================================
-- 5. RISK / ISSUE / MILESTONE (issues trÆ°á»›c risks â€” risks tham chiáº¿u issues)
-- =============================================================================

CREATE TABLE issues (
    id          uuid DEFAULT RANDOM_UUID() PRIMARY KEY,
    code        varchar(30)  NOT NULL,
    project_id  uuid         NOT NULL REFERENCES projects(id),
    title       varchar(200) NOT NULL,
    description text,
    severity    varchar(10)  NOT NULL,
    owner_id    uuid         NOT NULL REFERENCES users(id),
    root_cause  text,
    solution    text,
    status      varchar(20)  NOT NULL DEFAULT 'OPEN',
    due_date    date,
    resolved_at timestamp with time zone,
    deleted_at  timestamp with time zone,
    deleted_by  uuid         REFERENCES users(id),
    version     bigint       NOT NULL DEFAULT 0,
    created_at  timestamp with time zone  NOT NULL DEFAULT now(),
    created_by  uuid         REFERENCES users(id),
    updated_at  timestamp with time zone  NOT NULL DEFAULT now(),
    updated_by  uuid         REFERENCES users(id),
    CONSTRAINT ck_issues_severity CHECK (severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_issues_status CHECK (status IN ('OPEN', 'ANALYZING', 'IN_PROGRESS', 'RESOLVED', 'CLOSED', 'REJECTED')),
    CONSTRAINT ck_issues_resolved CHECK (status <> 'RESOLVED' OR resolved_at IS NOT NULL)
);

CREATE TABLE risks (
    id               uuid DEFAULT RANDOM_UUID() PRIMARY KEY,
    code             varchar(30)  NOT NULL,
    project_id       uuid         NOT NULL REFERENCES projects(id),
    title            varchar(200) NOT NULL,
    description      text,
    probability      varchar(10)  NOT NULL,
    impact           varchar(10)  NOT NULL,
    level            varchar(10)  NOT NULL,
    owner_id         uuid         NOT NULL REFERENCES users(id),
    mitigation_plan  text,
    contingency_plan text,
    status           varchar(20)  NOT NULL DEFAULT 'OPEN',
    due_date         date,
    linked_issue_id  uuid         REFERENCES issues(id),
    deleted_at       timestamp with time zone,
    deleted_by       uuid         REFERENCES users(id),
    version          bigint       NOT NULL DEFAULT 0,
    created_at       timestamp with time zone  NOT NULL DEFAULT now(),
    created_by       uuid         REFERENCES users(id),
    updated_at       timestamp with time zone  NOT NULL DEFAULT now(),
    updated_by       uuid         REFERENCES users(id),
    CONSTRAINT ck_risks_probability CHECK (probability IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_risks_impact CHECK (impact IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_risks_level CHECK (level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    CONSTRAINT ck_risks_status CHECK (status IN ('OPEN', 'MONITORING', 'MITIGATED', 'OCCURRED', 'CLOSED'))
);

CREATE TABLE milestones (
    id           uuid DEFAULT RANDOM_UUID() PRIMARY KEY,
    project_id   uuid         NOT NULL REFERENCES projects(id),
    name         varchar(150) NOT NULL,
    description  text,
    planned_date date         NOT NULL,
    actual_date  date,
    status       varchar(20)  NOT NULL DEFAULT 'NOT_STARTED',
    progress     int          NOT NULL DEFAULT 0,
    note         text,
    deleted_at   timestamp with time zone,
    deleted_by   uuid         REFERENCES users(id),
    version      bigint       NOT NULL DEFAULT 0,
    created_at   timestamp with time zone  NOT NULL DEFAULT now(),
    created_by   uuid         REFERENCES users(id),
    updated_at   timestamp with time zone  NOT NULL DEFAULT now(),
    updated_by   uuid         REFERENCES users(id),
    CONSTRAINT ck_milestones_status CHECK (status IN ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED', 'DELAYED', 'CANCELLED')),
    CONSTRAINT ck_milestones_progress CHECK (progress BETWEEN 0 AND 100),
    CONSTRAINT ck_milestones_done CHECK (status <> 'COMPLETED' OR progress = 100)
);

-- =============================================================================
-- 6. NOTIFICATION & AUDIT
-- =============================================================================

CREATE TABLE notifications (
    id           uuid DEFAULT RANDOM_UUID() PRIMARY KEY,
    recipient_id uuid         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type         varchar(40)  NOT NULL,
    title        varchar(200) NOT NULL,
    content      text,
    entity_type  varchar(50),
    entity_id    uuid,
    is_read      boolean      NOT NULL DEFAULT false,
    read_at      timestamp with time zone,
    created_at   timestamp with time zone  NOT NULL DEFAULT now(),
    CONSTRAINT ck_notifications_type CHECK (type IN (
        'TASK_ASSIGNED', 'TASK_DUE_SOON', 'TASK_OVERDUE',
        'TASK_COMMENTED', 'MEETING_INVITED', 'ACTION_ITEM_ASSIGNED'
    )),
    CONSTRAINT ck_notifications_read CHECK (is_read = false OR read_at IS NOT NULL)
);

CREATE TABLE audit_logs (
    id             uuid DEFAULT RANDOM_UUID() PRIMARY KEY,
    trace_id       varchar(64),
    actor_id       uuid         REFERENCES users(id),
    actor_username varchar(100),
    action         varchar(100) NOT NULL,
    entity_type    varchar(50),
    entity_id      uuid,
    before_data    json,
    after_data     json,
    created_at     timestamp with time zone  NOT NULL DEFAULT now()
);

-- =============================================================================
-- 7. UNIQUE CONSTRAINTS
-- =============================================================================

ALTER TABLE users ADD CONSTRAINT uk_users_username UNIQUE (username);
ALTER TABLE users ADD CONSTRAINT uk_users_email UNIQUE (email);
ALTER TABLE roles ADD CONSTRAINT uk_roles_code UNIQUE (code);
ALTER TABLE permissions ADD CONSTRAINT uk_permissions_code UNIQUE (code);
ALTER TABLE user_roles ADD CONSTRAINT uk_user_roles_user_role UNIQUE (user_id, role_id);
ALTER TABLE role_permissions ADD CONSTRAINT uk_role_permissions_role_perm UNIQUE (role_id, permission_id);
ALTER TABLE refresh_tokens ADD CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash);
ALTER TABLE project_members ADD CONSTRAINT uk_project_members_project_user UNIQUE (project_id, user_id);
ALTER TABLE meeting_participants ADD CONSTRAINT uk_meeting_participants_meeting_user UNIQUE (meeting_id, user_id);
ALTER TABLE task_assignees ADD CONSTRAINT uk_task_assignees_task_user UNIQUE (task_id, user_id);
ALTER TABLE task_watchers ADD CONSTRAINT uk_task_watchers_task_user UNIQUE (task_id, user_id);
ALTER TABLE task_tags ADD CONSTRAINT uk_task_tags_task_tag UNIQUE (task_id, tag_id);
ALTER TABLE tags ADD CONSTRAINT uk_tags_name UNIQUE (name);

-- MÃ£ nghiá»‡p vá»¥: unique theo báº£n ghi CHÆ¯A xÃ³a (soft delete khÃ´ng cháº·n tÃ¡i sá»­ dá»¥ng mÃ£)
CREATE UNIQUE INDEX uk_projects_code_active ON projects (code);
CREATE UNIQUE INDEX uk_tasks_code_active ON tasks (code);
CREATE UNIQUE INDEX uk_risks_code_active ON risks (code);
CREATE UNIQUE INDEX uk_issues_code_active ON issues (code);

-- LiÃªn káº¿t 1â€“1
CREATE UNIQUE INDEX uk_action_items_linked_task ON action_items (linked_task_id);
CREATE UNIQUE INDEX uk_risks_linked_issue ON risks (linked_issue_id);

-- =============================================================================
-- 8. INDEX â€” truy váº¥n phá»• biáº¿n (chi tiáº¿t: docs/database/03-index-strategy.md)
-- =============================================================================

CREATE INDEX ix_tasks_project_status ON tasks (project_id, status);
CREATE INDEX ix_tasks_assignee_status ON tasks (assignee_id, status);
CREATE INDEX ix_tasks_due_date ON tasks (due_date);
CREATE INDEX ix_tasks_parent ON tasks (parent_task_id);
CREATE INDEX ix_tasks_created_at ON tasks (created_at DESC);

CREATE INDEX ix_projects_status ON projects (status);
CREATE INDEX ix_projects_manager ON projects (project_manager_id);

CREATE INDEX ix_meetings_project_start ON meetings (project_id, start_time);
CREATE INDEX ix_meeting_participants_user ON meeting_participants (user_id);

CREATE INDEX ix_action_items_project_status ON action_items (project_id, status);
CREATE INDEX ix_action_items_assignee_status ON action_items (assignee_id, status);

CREATE INDEX ix_risks_project_status_level ON risks (project_id, status, level);
CREATE INDEX ix_risks_owner ON risks (owner_id);
CREATE INDEX ix_issues_project_status ON issues (project_id, status);
CREATE INDEX ix_issues_owner ON issues (owner_id);

CREATE INDEX ix_milestones_project_status_planned ON milestones (project_id, status, planned_date);

CREATE INDEX ix_notifications_recipient_unread ON notifications (recipient_id, is_read);
CREATE INDEX ix_notifications_recipient_created ON notifications (recipient_id, created_at DESC);
-- H2 khong ho tro functional index (CAST trong danh sach cot) â€” chi duoc tao o PostgreSQL.
CREATE INDEX ix_notifications_daily ON notifications (recipient_id, type, entity_id);

CREATE INDEX ix_audit_actor_created ON audit_logs (actor_id, created_at DESC);
CREATE INDEX ix_audit_action_created ON audit_logs (action, created_at DESC);
CREATE INDEX ix_audit_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX ix_audit_created ON audit_logs (created_at DESC);

-- =============================================================================
-- 9. Bá»˜ Äáº¾M SINH MÃƒ (task/risk/issue â€” xem docs/database/04-database-rules.md má»¥c 5)
-- =============================================================================

CREATE TABLE project_sequences (
    project_id uuid PRIMARY KEY REFERENCES projects(id) ON DELETE CASCADE,
    task_seq    bigint NOT NULL DEFAULT 0
);

CREATE TABLE global_sequences (
    name varchar(30) PRIMARY KEY,
    seq  bigint NOT NULL DEFAULT 0
);

