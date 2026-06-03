DO $$
BEGIN
    UPDATE hr_leave_type
    SET active = FALSE,
        updated_at = CURRENT_TIMESTAMP
    WHERE created_by IS NULL;

    IF to_regclass('public.employee_leave_balances') IS NOT NULL THEN
        DELETE FROM hr_leave_type leave_type
        WHERE leave_type.created_by IS NULL
          AND NOT EXISTS (
              SELECT 1
              FROM employee_leave_balances balance
              WHERE balance.leave_type_id = leave_type.id
          )
          AND NOT EXISTS (
              SELECT 1
              FROM hr_leave_request request
              WHERE request.leave_type_id = leave_type.id
          );
    ELSIF to_regclass('public.hr_leave_balance') IS NOT NULL THEN
        DELETE FROM hr_leave_type leave_type
        WHERE leave_type.created_by IS NULL
          AND NOT EXISTS (
              SELECT 1
              FROM hr_leave_balance balance
              WHERE balance.leave_type_id = leave_type.id
          )
          AND NOT EXISTS (
              SELECT 1
              FROM hr_leave_request request
              WHERE request.leave_type_id = leave_type.id
          );
    END IF;
END $$;
