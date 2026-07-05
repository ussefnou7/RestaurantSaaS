-- Dedicated permission for resetting an in-progress physical count to draft.
-- Interim default grant follows the inventory action-permission pattern until
-- approval workflows own assignment of document actions.

INSERT INTO permissions (code, module, name, description, type)
VALUES
    ('PHYSICAL_COUNT_REVERT_TO_DRAFT', 'INVENTORY', 'Revert Physical Count To Draft',
     'Reset an in-progress physical count back to draft before reconciliation.', 'ACTION')
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
JOIN permissions p ON p.code = 'PHYSICAL_COUNT_REVERT_TO_DRAFT'
WHERE r.code IN ('OWNER', 'SYS_ADMIN')
ON CONFLICT (role_id, permission_id) DO NOTHING;
