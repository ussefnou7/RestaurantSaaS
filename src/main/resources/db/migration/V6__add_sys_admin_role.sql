-- 1. Ensure system tenant exists
INSERT INTO tenants (
    id,
    name,
    code,
    status,
    created_at
)
VALUES (
           0,
           'System',
           'system',
           'ACTIVE',
           CURRENT_TIMESTAMP
       )
    ON CONFLICT (id) DO NOTHING;

-- Fix tenants sequence after explicit id = 0
SELECT setval(
               'tenants_id_seq',
               GREATEST((SELECT MAX(id) FROM tenants), 1)
       );

-- 2. Ensure SYS_ADMIN role exists
INSERT INTO roles (
    code,
    name,
    description,
    is_active,
    created_at
)
VALUES (
           'SYS_ADMIN',
           'System Admin',
           'Full system admin access',
           TRUE,
           CURRENT_TIMESTAMP
       )
    ON CONFLICT (code) DO NOTHING;

-- 3. Give SYS_ADMIN all permissions as default role permissions
INSERT INTO role_permissions (
    role_id,
    permission_id,
    created_at
)
SELECT
    r.id,
    p.id,
    CURRENT_TIMESTAMP
FROM roles r
         CROSS JOIN permissions p
WHERE r.code = 'SYS_ADMIN'
    ON CONFLICT DO NOTHING;

-- 4. Create default SYS_ADMIN user
-- username stored lowercase because login normalizes username to lowercase.
-- Login username can be: Nou7 or nou7
-- Password: secret123
INSERT INTO users (
    tenant_id,
    full_name,
    username,
    email,
    password_hash,
    status,
    created_at
)
VALUES (
           0,
           'Nou7',
           'nou7',
           'nou7@test.com',
           '$2a$10$QjCk73uhV24E.Tqs/34ic.8IyxZ25bAiiZWc68FZfoCh/Jqe33EzG',
           'ACTIVE',
           CURRENT_TIMESTAMP
       )
    ON CONFLICT (tenant_id, username) DO NOTHING;

-- 5. Assign SYS_ADMIN role to the default user
INSERT INTO user_roles (
    tenant_id,
    user_id,
    role_id,
    scope,
    branch_id,
    created_at
)
SELECT
    0,
    u.id,
    r.id,
    'TENANT',
    NULL,
    CURRENT_TIMESTAMP
FROM users u
         JOIN roles r ON r.code = 'SYS_ADMIN'
WHERE u.tenant_id = 0
  AND u.username = 'nou7'
    ON CONFLICT (tenant_id, user_id)
DO UPDATE SET
    role_id = EXCLUDED.role_id,
           scope = EXCLUDED.scope,
           branch_id = NULL,
           updated_at = CURRENT_TIMESTAMP;

-- 6. Copy SYS_ADMIN role permissions into user_permissions
INSERT INTO user_permissions (
    tenant_id,
    user_id,
    permission_id,
    created_at
)
SELECT
    0,
    u.id,
    rp.permission_id,
    CURRENT_TIMESTAMP
FROM users u
         JOIN roles r ON r.code = 'SYS_ADMIN'
         JOIN role_permissions rp ON rp.role_id = r.id
WHERE u.tenant_id = 0
  AND u.username = 'nou7'
    ON CONFLICT (tenant_id, user_id, permission_id) DO NOTHING;