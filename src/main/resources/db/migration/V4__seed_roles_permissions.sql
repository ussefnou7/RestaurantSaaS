INSERT INTO roles (code, name, description)
VALUES
    ('OWNER', 'Owner', 'Full tenant owner role with all permissions.'),
    ('BRANCH_MANAGER', 'Branch Manager', 'Manages branch operations, orders, inventory, users, and reports.'),
    ('CASHIER', 'Cashier', 'Handles orders and shifts.'),
    ('ACCOUNTANT', 'Accountant', 'Reviews accounting, payments, cash movement, reports, and shifts.'),
    ('HR_MANAGER', 'HR Manager', 'Manages users and employee records.'),
    ('INVENTORY_MANAGER', 'Inventory Manager', 'Manages products and inventory documents.')
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    description = EXCLUDED.description,
    is_active = TRUE,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO permissions (code, module, name, description, type)
VALUES
    ('USERS_ACCESS', 'USERS', 'Users Access', 'Access users module.', 'ACCESS'),
    ('USERS_VIEW', 'USERS', 'View Users', 'View tenant users.', 'ACTION'),
    ('USERS_CREATE', 'USERS', 'Create Users', 'Create tenant users.', 'ACTION'),
    ('USERS_UPDATE', 'USERS', 'Update Users', 'Update tenant users.', 'ACTION'),
    ('USERS_DELETE', 'USERS', 'Delete Users', 'Delete tenant users.', 'ACTION'),
    ('USERS_CHANGE_STATUS', 'USERS', 'Change User Status', 'Activate, deactivate, lock, or delete tenant users.', 'ACTION'),
    ('USERS_ASSIGN_ROLE', 'USERS', 'Assign User Role', 'Assign or change a user base role.', 'ACTION'),
    ('USERS_MANAGE_PERMISSIONS', 'USERS', 'Manage User Permissions', 'Replace direct user permissions.', 'ACTION'),

    ('BRANCHES_ACCESS', 'BRANCHES', 'Branches Access', 'Access branches module.', 'ACCESS'),
    ('BRANCHES_VIEW', 'BRANCHES', 'View Branches', 'View branches.', 'ACTION'),
    ('BRANCHES_CREATE', 'BRANCHES', 'Create Branches', 'Create branches.', 'ACTION'),
    ('BRANCHES_UPDATE', 'BRANCHES', 'Update Branches', 'Update branches.', 'ACTION'),
    ('BRANCHES_CHANGE_STATUS', 'BRANCHES', 'Change Branch Status', 'Activate or deactivate branches.', 'ACTION'),

    ('HR_ACCESS', 'HR', 'HR Access', 'Access HR module.', 'ACCESS'),
    ('EMPLOYEES_VIEW', 'HR', 'View Employees', 'View employees.', 'ACTION'),
    ('EMPLOYEES_CREATE', 'HR', 'Create Employees', 'Create employees.', 'ACTION'),
    ('EMPLOYEES_UPDATE', 'HR', 'Update Employees', 'Update employees.', 'ACTION'),
    ('EMPLOYEES_DELETE', 'HR', 'Delete Employees', 'Delete employees.', 'ACTION'),
    ('EMPLOYEES_CHANGE_STATUS', 'HR', 'Change Employee Status', 'Activate or deactivate employees.', 'ACTION'),

    ('PRODUCTS_ACCESS', 'PRODUCTS', 'Products Access', 'Access products module.', 'ACCESS'),
    ('PRODUCTS_VIEW', 'PRODUCTS', 'View Products', 'View products.', 'ACTION'),
    ('PRODUCTS_CREATE', 'PRODUCTS', 'Create Products', 'Create products.', 'ACTION'),
    ('PRODUCTS_UPDATE', 'PRODUCTS', 'Update Products', 'Update products.', 'ACTION'),
    ('PRODUCTS_DELETE', 'PRODUCTS', 'Delete Products', 'Delete products.', 'ACTION'),
    ('PRODUCTS_CHANGE_STATUS', 'PRODUCTS', 'Change Product Status', 'Activate or deactivate products.', 'ACTION'),

    ('INVENTORY_ACCESS', 'INVENTORY', 'Inventory Access', 'Access inventory module.', 'ACCESS'),
    ('INVENTORY_VIEW', 'INVENTORY', 'View Inventory', 'View inventory.', 'ACTION'),
    ('INVENTORY_DOC_CREATE', 'INVENTORY', 'Create Inventory Documents', 'Create inventory documents.', 'ACTION'),
    ('INVENTORY_DOC_UPDATE', 'INVENTORY', 'Update Inventory Documents', 'Update inventory documents.', 'ACTION'),
    ('INVENTORY_DOC_APPROVE', 'INVENTORY', 'Approve Inventory Documents', 'Approve inventory documents.', 'ACTION'),

    ('ORDERS_ACCESS', 'ORDERS', 'Orders Access', 'Access orders module.', 'ACCESS'),
    ('ORDERS_VIEW', 'ORDERS', 'View Orders', 'View orders.', 'ACTION'),
    ('ORDERS_CREATE', 'ORDERS', 'Create Orders', 'Create orders.', 'ACTION'),
    ('ORDERS_CANCEL', 'ORDERS', 'Cancel Orders', 'Cancel orders.', 'ACTION'),
    ('ORDERS_REFUND', 'ORDERS', 'Refund Orders', 'Refund orders.', 'ACTION'),
    ('ORDERS_DISCOUNT', 'ORDERS', 'Discount Orders', 'Apply order discounts.', 'ACTION'),

    ('SHIFTS_ACCESS', 'SHIFTS', 'Shifts Access', 'Access shifts module.', 'ACCESS'),
    ('SHIFTS_VIEW', 'SHIFTS', 'View Shifts', 'View shifts.', 'ACTION'),
    ('SHIFTS_OPEN', 'SHIFTS', 'Open Shifts', 'Open shifts.', 'ACTION'),
    ('SHIFTS_CLOSE', 'SHIFTS', 'Close Shifts', 'Close shifts.', 'ACTION'),

    ('ACCOUNTING_ACCESS', 'ACCOUNTING', 'Accounting Access', 'Access accounting module.', 'ACCESS'),
    ('PAYMENTS_VIEW', 'ACCOUNTING', 'View Payments', 'View payments.', 'ACTION'),
    ('PAYMENTS_CREATE', 'ACCOUNTING', 'Create Payments', 'Create payments.', 'ACTION'),
    ('CASH_MOVEMENTS_VIEW', 'ACCOUNTING', 'View Cash Movements', 'View cash movements.', 'ACTION'),
    ('CASH_MOVEMENTS_CREATE', 'ACCOUNTING', 'Create Cash Movements', 'Create cash movements.', 'ACTION'),

    ('REPORTS_ACCESS', 'REPORTS', 'Reports Access', 'Access reports module.', 'ACCESS'),
    ('REPORTS_VIEW_SALES', 'REPORTS', 'View Sales Reports', 'View sales reports.', 'ACTION'),
    ('REPORTS_VIEW_CASHIER', 'REPORTS', 'View Cashier Reports', 'View cashier reports.', 'ACTION'),
    ('REPORTS_VIEW_PRODUCTS', 'REPORTS', 'View Product Reports', 'View product reports.', 'ACTION'),

    ('ROLES_ACCESS', 'ROLES', 'Roles Access', 'Access roles module.', 'ACCESS'),
    ('ROLES_VIEW', 'ROLES', 'View Roles', 'View roles.', 'ACTION'),
    ('ROLES_UPDATE_DEFAULTS', 'ROLES', 'Update Role Defaults', 'Update default permissions for system roles.', 'ACTION'),

    ('PERMISSIONS_ACCESS', 'PERMISSIONS', 'Permissions Access', 'Access permissions module.', 'ACCESS'),
    ('PERMISSIONS_VIEW', 'PERMISSIONS', 'View Permissions', 'View permissions.', 'ACTION')
ON CONFLICT (code) DO UPDATE
SET module = EXCLUDED.module,
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    type = EXCLUDED.type,
    is_active = TRUE,
    updated_at = CURRENT_TIMESTAMP;
