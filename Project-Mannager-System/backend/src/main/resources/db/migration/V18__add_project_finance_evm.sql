ALTER TABLE project_members
    ADD COLUMN hourly_rate VARCHAR(255);

CREATE TABLE project_financial_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    snapshot_date DATE NOT NULL,
    planned_value DOUBLE PRECISION NOT NULL,
    earned_value DOUBLE PRECISION NOT NULL,
    actual_cost DOUBLE PRECISION NOT NULL,
    cost_variance DOUBLE PRECISION NOT NULL,
    schedule_variance DOUBLE PRECISION NOT NULL,
    cpi DOUBLE PRECISION NOT NULL,
    spi DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_proj_fin_snap_date UNIQUE (project_id, snapshot_date)
);

INSERT INTO permissions (code, name, description) VALUES
    ('financial:view', 'Xem tài chính và EVM', 'Xem đơn giá giờ và báo cáo tài chính EVM'),
    ('financial:update', 'Cập nhật tài chính', 'Cập nhật đơn giá giờ thành viên')
ON CONFLICT (code) DO NOTHING;

-- Assign to ADMIN
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.code = 'ADMIN' AND p.code IN ('financial:view', 'financial:update')
ON CONFLICT DO NOTHING;

-- Assign to PROJECT_MANAGER
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.code = 'PROJECT_MANAGER' AND p.code IN ('financial:view', 'financial:update')
ON CONFLICT DO NOTHING;
