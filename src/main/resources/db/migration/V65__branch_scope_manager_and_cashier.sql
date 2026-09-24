-- =====================================================================
-- D135: switch branch scoping on for BRANCH_MANAGER and CASHIER.
--
-- The enforcement shipped inert. Every seeded role carried
-- is_branch_scoped = false, so CurrentUserScopeProvider took the unscoped
-- path for every caller and no listing was actually narrowed. This is the
-- data change that turns it on, and it is deliberately separate from the
-- code: the mechanism is reviewable without changing anyone's visibility,
-- and visibility changes without touching the mechanism.
--
-- It also repairs a contradiction rather than creating one. Both roles
-- already have users carrying a branch_id, which TenantUserService's
-- validateRoleBranch forbids for a role it believes is unscoped — so the
-- flag has been wrong, not the data.
--
-- OWNER and SYS_ADMIN are deliberately left unscoped: the owner sees every
-- branch by not being confined to one, and a platform operator has no
-- branch of their own. INVENTORY_MANAGER, ACCOUNTANT and HR_MANAGER are
-- left alone because whether they are per-branch or tenant-wide is a
-- per-tenant judgement nobody has made yet; they can be flipped by the
-- same statement whenever it is.
-- =====================================================================

-- A scoped role with no branch denies every branch check (D135 fails
-- closed), so flipping the flag on a user with a null branch_id is a
-- silent, total lockout on their next request. Refuse to migrate instead:
-- a failed deploy is visible, a cashier locked out at opening time is not.
-- Same guard shape V14 used before enforcing users.role_id NOT NULL.
DO $$
DECLARE
    stranded_count bigint;
BEGIN
    SELECT count(*)
    INTO stranded_count
    FROM users u
    JOIN roles r ON r.id = u.role_id
    WHERE r.code IN ('BRANCH_MANAGER', 'CASHIER')
      AND u.branch_id IS NULL;

    IF stranded_count > 0 THEN
        RAISE EXCEPTION
            'Cannot branch-scope BRANCH_MANAGER/CASHIER: % user(s) carry no branch_id. '
            'Assign each a branch first — scoping them now would lock them out entirely.',
            stranded_count;
    END IF;
END $$;

UPDATE roles
SET is_branch_scoped = true
WHERE code IN ('BRANCH_MANAGER', 'CASHIER')
  AND tenant_id IS NULL;
