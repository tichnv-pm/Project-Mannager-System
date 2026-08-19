-- V12__add_project_wiki_and_milestone_planning.sql
-- Tạo các bảng quản lý Wiki dự án và bổ sung liên kết Milestone cho Kế hoạch chi tiết

CREATE TABLE wiki_page_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    parent_template_id UUID REFERENCES wiki_page_templates(id) ON DELETE SET NULL,
    title VARCHAR(255) NOT NULL,
    content_placeholder TEXT NOT NULL,
    sequence_no INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE project_wiki_pages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id),
    parent_page_id UUID REFERENCES project_wiki_pages(id) ON DELETE SET NULL,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID REFERENCES users(id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by UUID REFERENCES users(id),
    deleted_at TIMESTAMPTZ,
    deleted_by UUID REFERENCES users(id)
);

CREATE UNIQUE INDEX idx_wiki_proj_title_active ON project_wiki_pages(project_id, title) WHERE deleted_at IS NULL;

CREATE TABLE project_wiki_page_histories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wiki_page_id UUID NOT NULL REFERENCES project_wiki_pages(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    changed_by UUID REFERENCES users(id),
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE project_plans 
    ADD COLUMN parent_milestone_task_id UUID REFERENCES plan_tasks(id) ON DELETE SET NULL;
