DO $$
BEGIN
    IF to_regclass('public.hr_salary') IS NOT NULL
        AND to_regclass('public.employee_salaries') IS NULL THEN
        ALTER TABLE hr_salary RENAME TO employee_salaries;
    END IF;

    IF to_regclass('public.hr_salary_adjustment') IS NOT NULL
        AND to_regclass('public.employee_salary_adjustments') IS NULL THEN
        ALTER TABLE hr_salary_adjustment RENAME TO employee_salary_adjustments;
    END IF;

    IF to_regclass('public.hr_leave_balance') IS NOT NULL
        AND to_regclass('public.employee_leave_balances') IS NULL THEN
        ALTER TABLE hr_leave_balance RENAME TO employee_leave_balances;
    END IF;
END $$;

DO $$
BEGIN
    IF to_regclass('public.employee_salary_adjustments') IS NOT NULL
        AND to_regclass('public.hr_salary_additions') IS NOT NULL THEN
        INSERT INTO employee_salary_adjustments (
            tenant_id,
            employee_id,
            branch_id,
            type,
            amount,
            adjustment_date,
            reason,
            notes,
            active,
            created_by,
            updated_by,
            created_at,
            updated_at
        )
        SELECT
            tenant_id,
            employee_id,
            branch_id,
            'ADDITION',
            amount,
            salary_month,
            title,
            notes,
            is_active,
            created_by,
            updated_by,
            created_at,
            updated_at
        FROM hr_salary_additions;
    END IF;
END $$;

DROP TABLE IF EXISTS hr_salary_additions CASCADE;
DROP TABLE IF EXISTS hr_leave_requests CASCADE;
DROP TABLE IF EXISTS hr_leave_types CASCADE;

DELETE FROM role_permissions role_permission
USING permissions permission
WHERE role_permission.permission_id = permission.id
  AND permission.code IN (
      'HR_SALARY_ADDITIONS_VIEW',
      'HR_SALARY_ADDITIONS_CREATE',
      'HR_SALARY_ADDITIONS_UPDATE'
  );

DELETE FROM user_permissions user_permission
USING permissions permission
WHERE user_permission.permission_id = permission.id
  AND permission.code IN (
      'HR_SALARY_ADDITIONS_VIEW',
      'HR_SALARY_ADDITIONS_CREATE',
      'HR_SALARY_ADDITIONS_UPDATE'
  );

DELETE FROM permissions
WHERE code IN (
    'HR_SALARY_ADDITIONS_VIEW',
    'HR_SALARY_ADDITIONS_CREATE',
    'HR_SALARY_ADDITIONS_UPDATE'
);
