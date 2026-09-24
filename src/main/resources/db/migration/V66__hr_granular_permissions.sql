-- =====================================================================
-- HR_MANAGE was one grant for five unrelated capabilities (leave requests,
-- leave balances, leave types, salaries, salary adjustments), so a tenant
-- could not grant an accountant payroll without also handing them leave
-- approval. This splits it into a VIEW/MANAGE pair per feature, mirroring
-- the INVENTORY_SETUP_VIEW/MANAGE and INVENTORY_STOCK_VIEW/MANAGE pattern.
--
-- HR_LEAVES_VIEW (leave types) already existed and keeps its name; only
-- leave-type writes move off HR_MANAGE, onto the new HR_LEAVE_TYPES_MANAGE.
--
-- HR_MANAGE is deactivated rather than deleted: existing role_permissions/
-- user_permissions rows referencing it are harmless once no controller
-- checks the code, and is_active = FALSE is what drops it from the admin
-- permissions panel (PermissionService.listActivePermissions filters on
-- it). Screens and actions inside HR now gate on an OR of the VIEW/MANAGE
-- codes below, matching every other module (inventory, purchase, assets,
-- ...).
--
-- HR_ACCESS stays active: unlike HR_MANAGE it never gated any endpoint, so
-- it is not superseded by this split. It becomes the module's coarse "can
-- they see HR exists" gate -- nav link and route only, no data -- checked
-- entirely in the frontend (src/access/moduleAccess.ts), since it does not
-- need backend enforcement to be safe: every actual read still goes
-- through the granular codes below.
--
-- created_at is NOT NULL with no default (V45 dropped the defaults), so
-- every insert here sets it explicitly.
-- =====================================================================

INSERT INTO permissions (code, module, name, name_en, name_ar, description, type, created_at)
VALUES
    ('HR_LEAVE_REQUESTS_VIEW', 'HR', 'View Leave Requests', 'View Leave Requests', 'عرض طلبات الإجازات',
     'View employee leave requests.', 'ACTION', CURRENT_TIMESTAMP),
    ('HR_LEAVE_REQUESTS_MANAGE', 'HR', 'Manage Leave Requests', 'Manage Leave Requests', 'إدارة طلبات الإجازات',
     'Create, approve, reject and cancel employee leave requests.', 'ACTION', CURRENT_TIMESTAMP),
    ('HR_LEAVE_BALANCES_VIEW', 'HR', 'View Leave Balances', 'View Leave Balances', 'عرض أرصدة الإجازات',
     'View employee leave balances.', 'ACTION', CURRENT_TIMESTAMP),
    ('HR_LEAVE_BALANCES_MANAGE', 'HR', 'Manage Leave Balances', 'Manage Leave Balances', 'إدارة أرصدة الإجازات',
     'Generate and adjust employee leave balances.', 'ACTION', CURRENT_TIMESTAMP),
    ('HR_LEAVE_TYPES_MANAGE', 'HR', 'Manage Leave Types', 'Manage Leave Types', 'إدارة أنواع الإجازات',
     'Create, update and change the status of leave types.', 'ACTION', CURRENT_TIMESTAMP),
    ('HR_SALARIES_VIEW', 'HR', 'View Salaries', 'View Salaries', 'عرض الرواتب',
     'View employee salaries.', 'ACTION', CURRENT_TIMESTAMP),
    ('HR_SALARIES_MANAGE', 'HR', 'Manage Salaries', 'Manage Salaries', 'إدارة الرواتب',
     'Create employee salaries.', 'ACTION', CURRENT_TIMESTAMP),
    ('HR_SALARY_ADJUSTMENTS_VIEW', 'HR', 'View Salary Adjustments', 'View Salary Adjustments', 'عرض تعديلات الرواتب',
     'View employee salary adjustments.', 'ACTION', CURRENT_TIMESTAMP),
    ('HR_SALARY_ADJUSTMENTS_MANAGE', 'HR', 'Manage Salary Adjustments', 'Manage Salary Adjustments', 'إدارة تعديلات الرواتب',
     'Create and cancel employee salary adjustments.', 'ACTION', CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE
SET module = EXCLUDED.module,
    name = EXCLUDED.name,
    name_en = EXCLUDED.name_en,
    name_ar = EXCLUDED.name_ar,
    description = EXCLUDED.description,
    type = EXCLUDED.type,
    is_active = TRUE,
    updated_at = CURRENT_TIMESTAMP;

-- Default for newly created OWNER and BRANCH_MANAGER users, matching what HR_MANAGE gave them.
INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT r.id, p.id, CURRENT_TIMESTAMP
FROM roles r
CROSS JOIN permissions p
WHERE r.code IN ('OWNER', 'BRANCH_MANAGER')
  AND p.code IN (
    'HR_LEAVE_REQUESTS_VIEW', 'HR_LEAVE_REQUESTS_MANAGE',
    'HR_LEAVE_BALANCES_VIEW', 'HR_LEAVE_BALANCES_MANAGE',
    'HR_LEAVE_TYPES_MANAGE',
    'HR_SALARIES_VIEW', 'HR_SALARIES_MANAGE',
    'HR_SALARY_ADJUSTMENTS_VIEW', 'HR_SALARY_ADJUSTMENTS_MANAGE'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Backfill every existing OWNER and BRANCH_MANAGER user.
--
-- Required, not a convenience: D36 makes user_permissions a snapshot taken at
-- user creation, so seeding role_permissions above does nothing for anyone who
-- already exists. Without this, shipping the controller change silently
-- removes HR from every current owner and branch manager.
INSERT INTO user_permissions (tenant_id, user_id, permission_id, created_at)
SELECT u.tenant_id, u.id, p.id, CURRENT_TIMESTAMP
FROM users u
JOIN roles r ON r.id = u.role_id
CROSS JOIN permissions p
WHERE r.code IN ('OWNER', 'BRANCH_MANAGER')
  AND p.code IN (
    'HR_LEAVE_REQUESTS_VIEW', 'HR_LEAVE_REQUESTS_MANAGE',
    'HR_LEAVE_BALANCES_VIEW', 'HR_LEAVE_BALANCES_MANAGE',
    'HR_LEAVE_TYPES_MANAGE',
    'HR_SALARIES_VIEW', 'HR_SALARIES_MANAGE',
    'HR_SALARY_ADJUSTMENTS_VIEW', 'HR_SALARY_ADJUSTMENTS_MANAGE'
  )
ON CONFLICT ON CONSTRAINT uk_user_permissions_tenant_user_permission DO NOTHING;

-- Also backfill anyone who was manually granted HR_MANAGE beyond the
-- OWNER/BRANCH_MANAGER default -- e.g. an HR_MANAGER or ACCOUNTANT a tenant
-- had already empowered by hand via the admin panel -- so that grant is not
-- silently revoked by deactivating HR_MANAGE below.
INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT rp.role_id, p.id, CURRENT_TIMESTAMP
FROM role_permissions rp
JOIN permissions manage ON manage.id = rp.permission_id AND manage.code = 'HR_MANAGE'
CROSS JOIN permissions p
WHERE p.code IN (
    'HR_LEAVE_REQUESTS_VIEW', 'HR_LEAVE_REQUESTS_MANAGE',
    'HR_LEAVE_BALANCES_VIEW', 'HR_LEAVE_BALANCES_MANAGE',
    'HR_LEAVE_TYPES_MANAGE',
    'HR_SALARIES_VIEW', 'HR_SALARIES_MANAGE',
    'HR_SALARY_ADJUSTMENTS_VIEW', 'HR_SALARY_ADJUSTMENTS_MANAGE'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO user_permissions (tenant_id, user_id, permission_id, created_at)
SELECT up.tenant_id, up.user_id, p.id, CURRENT_TIMESTAMP
FROM user_permissions up
JOIN permissions manage ON manage.id = up.permission_id AND manage.code = 'HR_MANAGE'
CROSS JOIN permissions p
WHERE p.code IN (
    'HR_LEAVE_REQUESTS_VIEW', 'HR_LEAVE_REQUESTS_MANAGE',
    'HR_LEAVE_BALANCES_VIEW', 'HR_LEAVE_BALANCES_MANAGE',
    'HR_LEAVE_TYPES_MANAGE',
    'HR_SALARIES_VIEW', 'HR_SALARIES_MANAGE',
    'HR_SALARY_ADJUSTMENTS_VIEW', 'HR_SALARY_ADJUSTMENTS_MANAGE'
  )
ON CONFLICT ON CONSTRAINT uk_user_permissions_tenant_user_permission DO NOTHING;

-- HR_MANAGE is superseded by the granular codes above; deactivate it so it
-- drops out of the admin permissions panel. HR_ACCESS is kept active: it
-- gates only whether the HR module's nav link and route render at all (see
-- src/access/moduleAccess.ts in the frontend repo), never any actual HR
-- data, so it is not superseded by the same granular split.
UPDATE permissions
SET is_active = FALSE,
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'HR_MANAGE';
