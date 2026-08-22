-- Grant user:view permission to PROJECT_MANAGER role
-- dynamically by looking up codes in roles and permissions tables,
-- allowing PMs to view the list of system users when adding project members.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.code = 'PROJECT_MANAGER' AND p.code = 'user:view'
AND NOT EXISTS (
    SELECT 1 FROM role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id
);
