ALTER TABLE branches
    ADD COLUMN IF NOT EXISTS name_en VARCHAR(255),
    ADD COLUMN IF NOT EXISTS name_ar VARCHAR(255),
    ADD COLUMN IF NOT EXISTS address_en TEXT,
    ADD COLUMN IF NOT EXISTS address_ar TEXT;

ALTER TABLE roles
    ADD COLUMN IF NOT EXISTS name_en VARCHAR(255),
    ADD COLUMN IF NOT EXISTS name_ar VARCHAR(255),
    ADD COLUMN IF NOT EXISTS description_en TEXT,
    ADD COLUMN IF NOT EXISTS description_ar TEXT;

ALTER TABLE permissions
    ADD COLUMN IF NOT EXISTS name_en VARCHAR(255),
    ADD COLUMN IF NOT EXISTS name_ar VARCHAR(255),
    ADD COLUMN IF NOT EXISTS description_en TEXT,
    ADD COLUMN IF NOT EXISTS description_ar TEXT;

ALTER TABLE jobs
    ADD COLUMN IF NOT EXISTS name_en VARCHAR(255),
    ADD COLUMN IF NOT EXISTS name_ar VARCHAR(255),
    ADD COLUMN IF NOT EXISTS description_en TEXT,
    ADD COLUMN IF NOT EXISTS description_ar TEXT;

ALTER TABLE hr_employees
    ADD COLUMN IF NOT EXISTS full_name_en VARCHAR(255),
    ADD COLUMN IF NOT EXISTS full_name_ar VARCHAR(255),
    ADD COLUMN IF NOT EXISTS address_en TEXT,
    ADD COLUMN IF NOT EXISTS address_ar TEXT;

UPDATE branches
SET name_en = COALESCE(NULLIF(BTRIM(name_en), ''), name),
    address_en = COALESCE(NULLIF(BTRIM(address_en), ''), address);

UPDATE roles
SET name_en = COALESCE(NULLIF(BTRIM(name_en), ''), name),
    description_en = COALESCE(NULLIF(BTRIM(description_en), ''), description);

UPDATE permissions
SET name_en = COALESCE(NULLIF(BTRIM(name_en), ''), name),
    description_en = COALESCE(NULLIF(BTRIM(description_en), ''), description);

UPDATE jobs
SET name_en = COALESCE(NULLIF(BTRIM(name_en), ''), name),
    description_en = COALESCE(NULLIF(BTRIM(description_en), ''), description);

UPDATE hr_employees
SET full_name_en = COALESCE(NULLIF(BTRIM(full_name_en), ''), full_name),
    address_en = COALESCE(NULLIF(BTRIM(address_en), ''), address);

UPDATE roles r
SET name = seed.name_en,
    name_en = seed.name_en,
    name_ar = seed.name_ar,
    description = seed.description_en,
    description_en = seed.description_en,
    description_ar = seed.description_ar,
    is_active = TRUE,
    updated_at = CURRENT_TIMESTAMP
FROM (
    VALUES
        ('SYS_ADMIN', 'System Admin', 'مدير النظام', 'Full system admin access.', 'صلاحية كاملة لإدارة النظام.'),
        ('OWNER', 'Owner', 'المالك', 'Full tenant owner role with all permissions.', 'دور مالك المنشأة مع جميع الصلاحيات.'),
        ('BRANCH_MANAGER', 'Branch Manager', 'مدير الفرع', 'Manages branch operations, orders, inventory, users, and reports.', 'يدير عمليات الفرع والطلبات والمخزون والمستخدمين والتقارير.'),
        ('CASHIER', 'Cashier', 'أمين الصندوق', 'Handles orders and shifts.', 'يتعامل مع الطلبات والورديات.'),
        ('ACCOUNTANT', 'Accountant', 'محاسب', 'Reviews accounting, payments, cash movement, reports, and shifts.', 'يراجع الحسابات والمدفوعات وحركة النقد والتقارير والورديات.'),
        ('HR_MANAGER', 'HR Manager', 'مدير الموارد البشرية', 'Manages users and employee records.', 'يدير المستخدمين وسجلات الموظفين.'),
        ('INVENTORY_MANAGER', 'Inventory Manager', 'مدير المخزون', 'Manages products and inventory documents.', 'يدير المنتجات ومستندات المخزون.')
) AS seed(code, name_en, name_ar, description_en, description_ar)
WHERE r.code = seed.code;

UPDATE permissions p
SET module = seed.module,
    name = seed.name_en,
    name_en = seed.name_en,
    name_ar = seed.name_ar,
    description = seed.description_en,
    description_en = seed.description_en,
    description_ar = seed.description_ar,
    type = seed.type,
    is_active = TRUE,
    updated_at = CURRENT_TIMESTAMP
FROM (
    VALUES
        ('USERS_ACCESS', 'USERS', 'Users Access', 'الوصول إلى المستخدمين', 'Access users module.', 'الوصول إلى وحدة المستخدمين.', 'ACCESS'),
        ('USERS_VIEW', 'USERS', 'View Users', 'عرض المستخدمين', 'View tenant users.', 'عرض مستخدمي المنشأة.', 'ACTION'),
        ('USERS_CREATE', 'USERS', 'Create Users', 'إنشاء المستخدمين', 'Create tenant users.', 'إنشاء مستخدمي المنشأة.', 'ACTION'),
        ('USERS_UPDATE', 'USERS', 'Update Users', 'تحديث المستخدمين', 'Update tenant users.', 'تحديث مستخدمي المنشأة.', 'ACTION'),
        ('USERS_DELETE', 'USERS', 'Delete Users', 'حذف المستخدمين', 'Delete tenant users.', 'حذف مستخدمي المنشأة.', 'ACTION'),
        ('USERS_CHANGE_STATUS', 'USERS', 'Change User Status', 'تغيير حالة المستخدم', 'Activate, deactivate, lock, or delete tenant users.', 'تفعيل أو تعطيل أو قفل أو حذف مستخدمي المنشأة.', 'ACTION'),
        ('USERS_ASSIGN_ROLE', 'USERS', 'Assign User Role', 'تعيين دور المستخدم', 'Assign or change a user base role.', 'تعيين أو تغيير الدور الأساسي للمستخدم.', 'ACTION'),
        ('USER_PERMISSIONS_UPDATE', 'USERS', 'Update User Permissions', 'تحديث صلاحيات المستخدم', 'Replace direct user permissions.', 'استبدال الصلاحيات المباشرة للمستخدم.', 'ACTION'),

        ('BRANCHES_ACCESS', 'BRANCHES', 'Branches Access', 'الوصول إلى الفروع', 'Access branches module.', 'الوصول إلى وحدة الفروع.', 'ACCESS'),
        ('BRANCHES_VIEW', 'BRANCHES', 'View Branches', 'عرض الفروع', 'View branches.', 'عرض الفروع.', 'ACTION'),
        ('BRANCHES_CREATE', 'BRANCHES', 'Create Branches', 'إنشاء الفروع', 'Create branches.', 'إنشاء الفروع.', 'ACTION'),
        ('BRANCHES_UPDATE', 'BRANCHES', 'Update Branches', 'تحديث الفروع', 'Update branches.', 'تحديث الفروع.', 'ACTION'),
        ('BRANCHES_CHANGE_STATUS', 'BRANCHES', 'Change Branch Status', 'تغيير حالة الفرع', 'Activate or deactivate branches.', 'تفعيل أو تعطيل الفروع.', 'ACTION'),

        ('HR_ACCESS', 'HR', 'HR Access', 'الوصول إلى الموارد البشرية', 'Access HR module.', 'الوصول إلى وحدة الموارد البشرية.', 'ACCESS'),
        ('EMPLOYEES_VIEW', 'HR', 'View Employees', 'عرض الموظفين', 'View employees.', 'عرض الموظفين.', 'ACTION'),
        ('EMPLOYEES_CREATE', 'HR', 'Create Employees', 'إنشاء الموظفين', 'Create employees.', 'إنشاء الموظفين.', 'ACTION'),
        ('EMPLOYEES_UPDATE', 'HR', 'Update Employees', 'تحديث الموظفين', 'Update employees.', 'تحديث الموظفين.', 'ACTION'),
        ('EMPLOYEES_DELETE', 'HR', 'Delete Employees', 'حذف الموظفين', 'Delete employees.', 'حذف الموظفين.', 'ACTION'),
        ('EMPLOYEES_CHANGE_STATUS', 'HR', 'Change Employee Status', 'تغيير حالة الموظف', 'Activate or deactivate employees.', 'تفعيل أو تعطيل الموظفين.', 'ACTION'),

        ('PRODUCTS_ACCESS', 'PRODUCTS', 'Products Access', 'الوصول إلى المنتجات', 'Access products module.', 'الوصول إلى وحدة المنتجات.', 'ACCESS'),
        ('PRODUCTS_VIEW', 'PRODUCTS', 'View Products', 'عرض المنتجات', 'View products.', 'عرض المنتجات.', 'ACTION'),
        ('PRODUCTS_CREATE', 'PRODUCTS', 'Create Products', 'إنشاء المنتجات', 'Create products.', 'إنشاء المنتجات.', 'ACTION'),
        ('PRODUCTS_UPDATE', 'PRODUCTS', 'Update Products', 'تحديث المنتجات', 'Update products.', 'تحديث المنتجات.', 'ACTION'),
        ('PRODUCTS_DELETE', 'PRODUCTS', 'Delete Products', 'حذف المنتجات', 'Delete products.', 'حذف المنتجات.', 'ACTION'),
        ('PRODUCTS_CHANGE_STATUS', 'PRODUCTS', 'Change Product Status', 'تغيير حالة المنتج', 'Activate or deactivate products.', 'تفعيل أو تعطيل المنتجات.', 'ACTION'),

        ('INVENTORY_ACCESS', 'INVENTORY', 'Inventory Access', 'الوصول إلى المخزون', 'Access inventory module.', 'الوصول إلى وحدة المخزون.', 'ACCESS'),
        ('INVENTORY_VIEW', 'INVENTORY', 'View Inventory', 'عرض المخزون', 'View inventory.', 'عرض المخزون.', 'ACTION'),
        ('INVENTORY_DOC_CREATE', 'INVENTORY', 'Create Inventory Documents', 'إنشاء مستندات المخزون', 'Create inventory documents.', 'إنشاء مستندات المخزون.', 'ACTION'),
        ('INVENTORY_DOC_UPDATE', 'INVENTORY', 'Update Inventory Documents', 'تحديث مستندات المخزون', 'Update inventory documents.', 'تحديث مستندات المخزون.', 'ACTION'),
        ('INVENTORY_DOC_APPROVE', 'INVENTORY', 'Approve Inventory Documents', 'اعتماد مستندات المخزون', 'Approve inventory documents.', 'اعتماد مستندات المخزون.', 'ACTION'),

        ('ORDERS_ACCESS', 'ORDERS', 'Orders Access', 'الوصول إلى الطلبات', 'Access orders module.', 'الوصول إلى وحدة الطلبات.', 'ACCESS'),
        ('ORDERS_VIEW', 'ORDERS', 'View Orders', 'عرض الطلبات', 'View orders.', 'عرض الطلبات.', 'ACTION'),
        ('ORDERS_CREATE', 'ORDERS', 'Create Orders', 'إنشاء الطلبات', 'Create orders.', 'إنشاء الطلبات.', 'ACTION'),
        ('ORDERS_CANCEL', 'ORDERS', 'Cancel Orders', 'إلغاء الطلبات', 'Cancel orders.', 'إلغاء الطلبات.', 'ACTION'),
        ('ORDERS_REFUND', 'ORDERS', 'Refund Orders', 'استرداد الطلبات', 'Refund orders.', 'إجراء استرداد للطلبات.', 'ACTION'),
        ('ORDERS_DISCOUNT', 'ORDERS', 'Discount Orders', 'خصم الطلبات', 'Apply order discounts.', 'تطبيق خصومات على الطلبات.', 'ACTION'),

        ('SHIFTS_ACCESS', 'SHIFTS', 'Shifts Access', 'الوصول إلى الورديات', 'Access shifts module.', 'الوصول إلى وحدة الورديات.', 'ACCESS'),
        ('SHIFTS_VIEW', 'SHIFTS', 'View Shifts', 'عرض الورديات', 'View shifts.', 'عرض الورديات.', 'ACTION'),
        ('SHIFTS_OPEN', 'SHIFTS', 'Open Shifts', 'فتح الورديات', 'Open shifts.', 'فتح الورديات.', 'ACTION'),
        ('SHIFTS_CLOSE', 'SHIFTS', 'Close Shifts', 'إغلاق الورديات', 'Close shifts.', 'إغلاق الورديات.', 'ACTION'),

        ('ACCOUNTING_ACCESS', 'ACCOUNTING', 'Accounting Access', 'الوصول إلى الحسابات', 'Access accounting module.', 'الوصول إلى وحدة الحسابات.', 'ACCESS'),
        ('PAYMENTS_VIEW', 'ACCOUNTING', 'View Payments', 'عرض المدفوعات', 'View payments.', 'عرض المدفوعات.', 'ACTION'),
        ('PAYMENTS_CREATE', 'ACCOUNTING', 'Create Payments', 'إنشاء المدفوعات', 'Create payments.', 'إنشاء المدفوعات.', 'ACTION'),
        ('CASH_MOVEMENTS_VIEW', 'ACCOUNTING', 'View Cash Movements', 'عرض حركة النقد', 'View cash movements.', 'عرض حركة النقد.', 'ACTION'),
        ('CASH_MOVEMENTS_CREATE', 'ACCOUNTING', 'Create Cash Movements', 'إنشاء حركة نقدية', 'Create cash movements.', 'إنشاء حركات نقدية.', 'ACTION'),

        ('REPORTS_ACCESS', 'REPORTS', 'Reports Access', 'الوصول إلى التقارير', 'Access reports module.', 'الوصول إلى وحدة التقارير.', 'ACCESS'),
        ('REPORTS_VIEW_SALES', 'REPORTS', 'View Sales Reports', 'عرض تقارير المبيعات', 'View sales reports.', 'عرض تقارير المبيعات.', 'ACTION'),
        ('REPORTS_VIEW_CASHIER', 'REPORTS', 'View Cashier Reports', 'عرض تقارير أمين الصندوق', 'View cashier reports.', 'عرض تقارير أمين الصندوق.', 'ACTION'),
        ('REPORTS_VIEW_PRODUCTS', 'REPORTS', 'View Product Reports', 'عرض تقارير المنتجات', 'View product reports.', 'عرض تقارير المنتجات.', 'ACTION'),

        ('ROLES_ACCESS', 'ROLES', 'Roles Access', 'الوصول إلى الأدوار', 'Access roles module.', 'الوصول إلى وحدة الأدوار.', 'ACCESS'),
        ('ROLES_VIEW', 'ROLES', 'View Roles', 'عرض الأدوار', 'View roles.', 'عرض الأدوار.', 'ACTION'),
        ('ROLES_UPDATE_DEFAULTS', 'ROLES', 'Update Role Defaults', 'تحديث افتراضيات الأدوار', 'Update default permissions for system roles.', 'تحديث الصلاحيات الافتراضية لأدوار النظام.', 'ACTION'),

        ('PERMISSIONS_ACCESS', 'PERMISSIONS', 'Permissions Access', 'الوصول إلى الصلاحيات', 'Access permissions module.', 'الوصول إلى وحدة الصلاحيات.', 'ACCESS'),
        ('PERMISSIONS_VIEW', 'PERMISSIONS', 'View Permissions', 'عرض الصلاحيات', 'View permissions.', 'عرض الصلاحيات.', 'ACTION'),

        ('JOBS_VIEW', 'JOB', 'View Jobs', 'عرض الوظائف', 'View jobs.', 'عرض الوظائف.', 'ACTION'),
        ('JOBS_CREATE', 'JOB', 'Create Jobs', 'إنشاء الوظائف', 'Create jobs.', 'إنشاء الوظائف.', 'ACTION'),
        ('JOBS_UPDATE', 'JOB', 'Update Jobs', 'تحديث الوظائف', 'Update jobs.', 'تحديث الوظائف.', 'ACTION'),
        ('HR_EMPLOYEES_VIEW', 'HR', 'View Employees', 'عرض موظفي الموارد البشرية', 'View employees.', 'عرض الموظفين.', 'ACTION'),
        ('HR_EMPLOYEES_CREATE', 'HR', 'Create Employees', 'إنشاء موظفي الموارد البشرية', 'Create employees.', 'إنشاء الموظفين.', 'ACTION'),
        ('HR_EMPLOYEES_UPDATE', 'HR', 'Update Employees', 'تحديث موظفي الموارد البشرية', 'Update employees.', 'تحديث الموظفين.', 'ACTION'),
        ('HR_LEAVES_VIEW', 'HR', 'View Leave Requests', 'عرض طلبات الإجازات', 'View employee leave requests.', 'عرض طلبات إجازات الموظفين.', 'ACTION'),
        ('HR_LEAVES_CREATE', 'HR', 'Create Leave Requests', 'إنشاء طلبات الإجازات', 'Create employee leave requests.', 'إنشاء طلبات إجازات الموظفين.', 'ACTION'),
        ('HR_LEAVES_UPDATE_STATUS', 'HR', 'Update Leave Status', 'تحديث حالة الإجازة', 'Approve, reject, or cancel leave requests.', 'اعتماد أو رفض أو إلغاء طلبات الإجازات.', 'ACTION')
) AS seed(code, module, name_en, name_ar, description_en, description_ar, type)
WHERE p.code = seed.code;
