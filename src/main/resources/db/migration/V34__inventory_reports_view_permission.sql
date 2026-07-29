-- =====================================================================
-- Inventory reporting: read-only permission for the inventory reports module.
-- INVENTORY_REPORTS_VIEW guards GET /api/inventory/reports/** (stock valuation today).
-- Mirrors the ASSETS_VIEW seed + grant pattern from V17__assets_view_permission.sql.
-- =====================================================================

INSERT INTO permissions (code, module, name, description, type)
VALUES
    ('INVENTORY_REPORTS_VIEW', 'INVENTORY', 'View Inventory Reports',
     'View inventory reports (stock valuation).', 'ACTION')
ON CONFLICT (code) DO UPDATE
SET module = EXCLUDED.module,
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    type = EXCLUDED.type,
    is_active = TRUE,
    updated_at = CURRENT_TIMESTAMP;

-- Default grants: owners/admins plus branch managers, who already hold the other
-- read permissions (INVENTORY_VIEW, REPORTS_VIEW_*) seeded in V3.
INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT r.id, p.id, CURRENT_TIMESTAMP
FROM roles r
JOIN permissions p ON p.code = 'INVENTORY_REPORTS_VIEW'
WHERE r.code IN ('OWNER', 'SYS_ADMIN', 'BRANCH_MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;
