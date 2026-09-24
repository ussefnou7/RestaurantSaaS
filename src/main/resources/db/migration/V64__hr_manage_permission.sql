-- =====================================================================
-- HR stops being role-locked and becomes grantable.
--
-- The leave, leave-balance, salary and salary-adjustment controllers were
-- gated on isOwnerOrBranchManager(), and leave-type writes on isOwner().
-- A role gate cannot be delegated: an HR_MANAGER could not be given HR,
-- and a trusted accountant could not be given payroll, no matter what the
-- sysadmin panel showed. The admin-web checked invented codes
-- (HR_LEAVES_CREATE, HR_SALARY_ADDITIONS_*) that were never seeded, so its
-- HR gating was decorative.
--
-- One code rather than a fine-grained set: a restaurant does not staff an
-- HR department, it has one person who does all of it.
--
-- created_at is NOT NULL with no default (V45 dropped the defaults), so
-- every insert here sets it explicitly.
-- =====================================================================

INSERT INTO permissions (code, module, name, name_en, name_ar, description, type, created_at)
VALUES
    ('HR_MANAGE', 'HR', 'Manage HR', 'Manage HR', 'إدارة الموارد البشرية',
     'Leave requests, leave balances, leave types, salaries and salary adjustments.',
     'ACTION', CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE
SET module = EXCLUDED.module,
    name = EXCLUDED.name,
    name_en = EXCLUDED.name_en,
    name_ar = EXCLUDED.name_ar,
    description = EXCLUDED.description,
    type = EXCLUDED.type;

-- Default for newly created OWNER and BRANCH_MANAGER users.
INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT r.id, p.id, CURRENT_TIMESTAMP
FROM roles r
CROSS JOIN permissions p
WHERE r.code IN ('OWNER', 'BRANCH_MANAGER')
  AND p.code = 'HR_MANAGE'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Backfill every existing OWNER and BRANCH_MANAGER user.
--
-- Required, not a convenience: D36 makes user_permissions a snapshot taken at
-- user creation, so seeding role_permissions above does nothing for anyone who
-- already exists. Without this, shipping the controller change silently removes
-- HR from every current owner and branch manager.
INSERT INTO user_permissions (tenant_id, user_id, permission_id, created_at)
SELECT u.tenant_id, u.id, p.id, CURRENT_TIMESTAMP
FROM users u
JOIN roles r ON r.id = u.role_id
CROSS JOIN permissions p
WHERE r.code IN ('OWNER', 'BRANCH_MANAGER')
  AND p.code = 'HR_MANAGE'
ON CONFLICT ON CONSTRAINT uk_user_permissions_tenant_user_permission DO NOTHING;
