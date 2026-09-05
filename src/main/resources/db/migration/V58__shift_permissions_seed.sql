-- =====================================================================
-- D122 / D123: the two permissions the shift rewrite needs and that the
-- V2 seed never had.
--
-- Both existed only as prose before this. SHIFTS_CLOSE was seeded in V2
-- but no endpoint enforced it -- all three shift endpoints required
-- SHIFTS_OPEN, which the CASHIER role holds, so any cashier could close
-- any shift in the tenant. That is fixed in the controller; this file
-- supplies the two codes the fix needs.
-- =====================================================================

-- created_at is NOT NULL with no default: V45 dropped the CURRENT_TIMESTAMP
-- defaults across the schema, so every seed must supply it explicitly.
INSERT INTO permissions (code, module, name, description, type, created_at)
VALUES
    ('SHIFTS_FORCE_CLOSE', 'SHIFTS', 'Force Close Shifts',
     'Close a shift opened by another cashier. Recorded on the shift as a forced close.', 'ACTION',
     CURRENT_TIMESTAMP),
    ('SHIFTS_VIEW_VARIANCE', 'SHIFTS', 'View Shift Variance',
     'See expected cash, variance and handover variance. Separate from operating a shift.', 'ACTION',
     CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE
SET module = EXCLUDED.module,
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    type = EXCLUDED.type,
    is_active = TRUE,
    updated_at = CURRENT_TIMESTAMP;

-- OWNER holds every active permission (V3 grants by `p.is_active = TRUE`),
-- but that statement has already run -- so new codes need an explicit
-- grant or the role silently falls behind the permission table.
INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT r.id, p.id, CURRENT_TIMESTAMP
FROM roles r
JOIN permissions p ON p.code IN ('SHIFTS_FORCE_CLOSE', 'SHIFTS_VIEW_VARIANCE')
WHERE r.code = 'OWNER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- BRANCH_MANAGER gets the variance, which is the whole point of the
-- shifts list for them (D125), and deliberately NOT force close: D122
-- puts that in the hands of the cashier standing at the drawer, and a web
-- manager could not exercise it anyway -- their token carries no deviceId,
-- so every shift path rejects them (D127).
INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT r.id, p.id, CURRENT_TIMESTAMP
FROM roles r
JOIN permissions p ON p.code = 'SHIFTS_VIEW_VARIANCE'
WHERE r.code = 'BRANCH_MANAGER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- CASHIER receives neither, and that is the point of splitting them.
-- Force close is the permission that makes pushing a shortfall onto an
-- absent colleague harder (D122); view-variance is what keeps the count
-- blind (D123). Granting either to the role that operates the drawer
-- would undo the control. Stated as an assertion rather than left as an
-- absence, so a later "grant the cashier everything shifts-related" edit
-- has to argue with it.
DO $$
DECLARE
    leaked TEXT;
BEGIN
    SELECT string_agg(p.code, ', ') INTO leaked
    FROM role_permissions rp
    JOIN roles r ON r.id = rp.role_id
    JOIN permissions p ON p.id = rp.permission_id
    WHERE r.code = 'CASHIER'
      AND p.code IN ('SHIFTS_FORCE_CLOSE', 'SHIFTS_VIEW_VARIANCE');

    IF leaked IS NOT NULL THEN
        RAISE EXCEPTION 'CASHIER must hold neither SHIFTS_FORCE_CLOSE nor SHIFTS_VIEW_VARIANCE, found: %', leaked;
    END IF;
END $$;
