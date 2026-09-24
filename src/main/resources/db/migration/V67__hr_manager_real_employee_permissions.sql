-- =====================================================================
-- HR_MANAGER's employee-management grant was silently wrong: V3 granted the
-- legacy EMPLOYEES_VIEW/CREATE/UPDATE/CHANGE_STATUS codes (no HR_ prefix),
-- which no controller has ever checked -- EmployeeController enforces
-- HR_EMPLOYEES_VIEW/CREATE/UPDATE. HR_MANAGER has had zero real access to
-- Add Employee since the HR_ prefix was introduced ("You do not have
-- permission to perform this action" on every create attempt).
--
-- BRANCH_MANAGER has the same gap: it only ever held the dead
-- EMPLOYEES_VIEW code, despite already holding every other HR granular
-- MANAGE permission granted in V66.
--
-- The legacy EMPLOYEES_* codes stay seeded (OWNER and SYS_ADMIN also hold
-- them, harmlessly, and are unaffected by this migration) -- deactivating
-- them tenant-wide is a separate concern from fixing these two roles.
--
-- created_at is NOT NULL with no default (V45 dropped the defaults), so
-- every insert here sets it explicitly.
-- =====================================================================

INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT r.id, p.id, CURRENT_TIMESTAMP
FROM roles r
CROSS JOIN permissions p
WHERE r.code IN ('HR_MANAGER', 'BRANCH_MANAGER')
  AND p.code IN ('HR_EMPLOYEES_VIEW', 'HR_EMPLOYEES_CREATE', 'HR_EMPLOYEES_UPDATE')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Backfill every existing HR_MANAGER and BRANCH_MANAGER user.
--
-- Required, not a convenience: D36 makes user_permissions a snapshot taken
-- at user creation, so seeding role_permissions above does nothing for
-- anyone who already exists.
INSERT INTO user_permissions (tenant_id, user_id, permission_id, created_at)
SELECT u.tenant_id, u.id, p.id, CURRENT_TIMESTAMP
FROM users u
JOIN roles r ON r.id = u.role_id
CROSS JOIN permissions p
WHERE r.code IN ('HR_MANAGER', 'BRANCH_MANAGER')
  AND p.code IN ('HR_EMPLOYEES_VIEW', 'HR_EMPLOYEES_CREATE', 'HR_EMPLOYEES_UPDATE')
ON CONFLICT ON CONSTRAINT uk_user_permissions_tenant_user_permission DO NOTHING;

-- Drop the dead legacy grant from these two roles specifically: it never
-- did anything and only clutters the admin permissions panel with checked
-- toggles for capability that now comes from the codes above.
DELETE FROM role_permissions
WHERE role_id IN (SELECT id FROM roles WHERE code IN ('HR_MANAGER', 'BRANCH_MANAGER'))
  AND permission_id IN (
    SELECT id FROM permissions
    WHERE code IN ('EMPLOYEES_VIEW', 'EMPLOYEES_CREATE', 'EMPLOYEES_UPDATE', 'EMPLOYEES_CHANGE_STATUS')
  );

DELETE FROM user_permissions
WHERE user_id IN (
    SELECT id FROM users WHERE role_id IN (SELECT id FROM roles WHERE code IN ('HR_MANAGER', 'BRANCH_MANAGER'))
  )
  AND permission_id IN (
    SELECT id FROM permissions
    WHERE code IN ('EMPLOYEES_VIEW', 'EMPLOYEES_CREATE', 'EMPLOYEES_UPDATE', 'EMPLOYEES_CHANGE_STATUS')
  );
