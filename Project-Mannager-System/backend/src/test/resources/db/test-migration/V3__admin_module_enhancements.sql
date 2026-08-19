-- =============================================================================
-- PM Daily Work Management - Flyway migration V3 (H2-compatible, TEST ONLY)
-- =============================================================================

-- 1. Thêm cột xóa mềm cho users
ALTER TABLE users ADD COLUMN deleted_at timestamp with time zone DEFAULT NULL;
ALTER TABLE users ADD COLUMN deleted_by uuid DEFAULT NULL REFERENCES users(id);

-- 2. Thêm cột phân biệt vai trò hệ thống (system roles)
ALTER TABLE roles ADD COLUMN is_system boolean NOT NULL DEFAULT false;

-- 3. Cập nhật vai trò hệ thống có sẵn
UPDATE roles SET is_system = true WHERE code IN ('ADMIN', 'PROJECT_MANAGER', 'PROJECT_MEMBER', 'VIEWER');
