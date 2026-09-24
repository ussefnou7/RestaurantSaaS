-- =====================================================================
-- The Add/Edit Employee form loads branches, jobs and users up front
-- (Promise.all in EmployeeFormModal) to populate its dropdowns, and shows
-- "You do not have permission to perform this action" for the whole modal
-- the instant any one of those three read calls 403s -- before the user
-- types anything.
--
-- HR_MANAGER had none of BRANCHES_VIEW / JOBS_VIEW (V67 only fixed the
-- employee endpoints themselves). BRANCH_MANAGER already has BRANCHES_VIEW
-- but was missing JOBS_VIEW.
--
-- created_at is NOT NULL with no default (V45 dropped the defaults), so
-- every insert here sets it explicitly.
-- =====================================================================

INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT r.id, p.id, CURRENT_TIMESTAMP
FROM roles r
CROSS JOIN permissions p
WHERE (r.code = 'HR_MANAGER' AND p.code IN ('BRANCHES_VIEW', 'JOBS_VIEW'))
   OR (r.code = 'BRANCH_MANAGER' AND p.code = 'JOBS_VIEW')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Backfill existing HR_MANAGER and BRANCH_MANAGER users (D36: user_permissions
-- is a snapshot taken at user creation).
INSERT INTO user_permissions (tenant_id, user_id, permission_id, created_at)
SELECT u.tenant_id, u.id, p.id, CURRENT_TIMESTAMP
FROM users u
JOIN roles r ON r.id = u.role_id
CROSS JOIN permissions p
WHERE (r.code = 'HR_MANAGER' AND p.code IN ('BRANCHES_VIEW', 'JOBS_VIEW'))
   OR (r.code = 'BRANCH_MANAGER' AND p.code = 'JOBS_VIEW')
ON CONFLICT ON CONSTRAINT uk_user_permissions_tenant_user_permission DO NOTHING;
