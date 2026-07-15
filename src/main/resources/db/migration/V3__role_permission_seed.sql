INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.is_active = TRUE
WHERE r.code = 'OWNER' ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'USERS_ACCESS','USERS_VIEW','BRANCHES_ACCESS','BRANCHES_VIEW','HR_ACCESS','EMPLOYEES_VIEW',
    'PRODUCTS_ACCESS','PRODUCTS_VIEW','PRODUCTS_UPDATE','INVENTORY_ACCESS','INVENTORY_VIEW',
    'INVENTORY_DOC_CREATE','ORDERS_ACCESS','ORDERS_VIEW','ORDERS_CREATE','ORDERS_CANCEL',
    'ORDERS_DISCOUNT','SHIFTS_ACCESS','SHIFTS_VIEW','REPORTS_ACCESS','REPORTS_VIEW_SALES',
    'REPORTS_VIEW_CASHIER','REPORTS_VIEW_PRODUCTS')
WHERE r.code = 'BRANCH_MANAGER' ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'PRODUCTS_ACCESS','PRODUCTS_VIEW','ORDERS_ACCESS','ORDERS_CREATE','SHIFTS_ACCESS',
    'SHIFTS_VIEW','SHIFTS_OPEN','SHIFTS_CLOSE')
WHERE r.code = 'CASHIER' ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'ORDERS_ACCESS','ORDERS_VIEW','ACCOUNTING_ACCESS','PAYMENTS_VIEW','CASH_MOVEMENTS_VIEW',
    'REPORTS_ACCESS','REPORTS_VIEW_SALES','REPORTS_VIEW_CASHIER','REPORTS_VIEW_PRODUCTS',
    'SHIFTS_ACCESS','SHIFTS_VIEW')
WHERE r.code = 'ACCOUNTANT' ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'USERS_ACCESS','USERS_VIEW','HR_ACCESS','EMPLOYEES_VIEW','EMPLOYEES_CREATE',
    'EMPLOYEES_UPDATE','EMPLOYEES_CHANGE_STATUS')
WHERE r.code = 'HR_MANAGER' ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN (
    'PRODUCTS_ACCESS','PRODUCTS_VIEW','PRODUCTS_CREATE','PRODUCTS_UPDATE','PRODUCTS_CHANGE_STATUS',
    'INVENTORY_ACCESS','INVENTORY_VIEW','INVENTORY_DOC_CREATE','INVENTORY_DOC_UPDATE','INVENTORY_DOC_APPROVE')
WHERE r.code = 'INVENTORY_MANAGER' ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT r.id, p.id, CURRENT_TIMESTAMP
FROM roles r
JOIN permissions p ON p.code IN (
    'PURCHASE_INVOICE_UNPOST',
    'PURCHASE_INVOICE_DELETE',
    'PURCHASE_RETURN_UNPOST',
    'PURCHASE_RETURN_DELETE',
    'PHYSICAL_COUNT_DELETE'
)
WHERE r.code IN ('OWNER', 'SYS_ADMIN')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT r.id, p.id, CURRENT_TIMESTAMP
FROM roles r
JOIN permissions p ON p.code = 'PHYSICAL_COUNT_REVERT_TO_DRAFT'
WHERE r.code IN ('OWNER', 'SYS_ADMIN')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT r.id, p.id, CURRENT_TIMESTAMP
FROM roles r
JOIN permissions p ON p.code IN (
    'PURCHASE_INVOICE_UNCOMPLETE',
    'PURCHASE_RETURN_UNCOMPLETE'
)
WHERE r.code IN ('OWNER', 'SYS_ADMIN')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT r.id, p.id, CURRENT_TIMESTAMP
FROM roles r
JOIN permissions p ON p.code = 'WASTE_UNCOMPLETE'
WHERE r.code IN ('OWNER', 'SYS_ADMIN')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT r.id, p.id, CURRENT_TIMESTAMP
FROM roles r
JOIN permissions p ON p.code = 'DEVICES_MANAGE'
WHERE r.code IN ('OWNER', 'SYS_ADMIN')
ON CONFLICT (role_id, permission_id) DO NOTHING;

