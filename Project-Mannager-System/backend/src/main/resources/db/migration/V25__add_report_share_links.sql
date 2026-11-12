-- GĐ4 (cải tiến hạ tầng/nghiệp vụ) phần 3/3: share-link cho stakeholder ngoài tổ chức xem báo cáo
-- tiến độ dự án (Project Progress Report) không cần đăng nhập. Token chính là id (UUID) của bản
-- ghi — cùng quy ước "UUID PK = định danh công khai không đoán được" đã dùng cho mọi entity khác
-- trong hệ thống (task/plan/.../{id}), không cần thêm cột token riêng.
CREATE TABLE report_share_links (
    id               uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id       uuid         NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    label            varchar(200),
    expires_at       timestamptz,
    deleted_at       timestamptz,
    deleted_by       uuid         REFERENCES users(id),
    version          bigint       NOT NULL DEFAULT 0,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    created_by       uuid         REFERENCES users(id),
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    updated_by       uuid         REFERENCES users(id)
);

CREATE INDEX idx_report_share_links_project ON report_share_links(project_id) WHERE deleted_at IS NULL;

-- Quyền tạo/thu hồi share-link — nghiêm hơn report:view vì đây là hành động public-facing.
INSERT INTO permissions (code, name, description) VALUES
    ('report:share', 'Chia sẻ báo cáo ngoài tổ chức', 'Tạo/thu hồi share-link báo cáo tiến độ cho stakeholder ngoài tổ chức')
ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.code = 'ADMIN' AND p.code = 'report:share'
ON CONFLICT DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.code = 'PROJECT_MANAGER' AND p.code = 'report:share'
ON CONFLICT DO NOTHING;
