-- =====================================================================
-- D52: split read access from management for the Fixed Assets module.
-- Adds ASSETS_VIEW (guards all GET endpoints); ASSETS_MANAGE remains on writes.
-- Mirrors the ASSETS_MANAGE seed + grant pattern from V16__assets.sql.
-- =====================================================================

INSERT INTO permissions (code, module, name, description, type)
VALUES
    ('ASSETS_VIEW', 'ASSETS', 'View Assets',
     'View fixed assets, lines, disposals, maintenance, and reports.', 'ACTION')
ON CONFLICT (code) DO UPDATE
SET module = EXCLUDED.module,
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    type = EXCLUDED.type,
    is_active = TRUE,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT r.id, p.id, CURRENT_TIMESTAMP
FROM roles r
JOIN permissions p ON p.code = 'ASSETS_VIEW'
WHERE r.code IN ('OWNER', 'SYS_ADMIN')
ON CONFLICT (role_id, permission_id) DO NOTHING;
