-- =====================================================================
-- The owner dashboard's one permission.
--
-- DASHBOARD_VIEW opens the page and grants nothing else, which is the
-- whole design. The dashboard is the only screen that reads across every
-- module, so if this permission carried the data too it would become a
-- way around the permissions on the screens that own it -- most sharply
-- SHIFTS_VIEW_VARIANCE, which V58 split out precisely to keep expected
-- cash and variance away from whoever operates the drawer. Every block
-- and every alert is therefore gated separately, on the permission of
-- its source module: REPORTS_VIEW_SALES, ORDERS_VIEW, SHIFTS_VIEW,
-- SHIFTS_VIEW_VARIANCE, INVENTORY_REPORTS_VIEW, INVENTORY_STOCK_VIEW,
-- INVENTORY_PURCHASE_VIEW.
--
-- A caller holding only this permission therefore sees a page whose
-- every block reports HIDDEN_NO_PERMISSION -- not a page of zeros. That
-- distinction is the contract: an empty dashboard must never be
-- indistinguishable from a quiet month.
--
-- Type is ACCESS, not ACTION: it is the gate on reaching a screen, like
-- REPORTS_ACCESS, and it authorises no state change. The dashboard is
-- read-only -- nothing behind it writes.
-- =====================================================================

-- created_at is NOT NULL with no default: V45 dropped the CURRENT_TIMESTAMP
-- defaults across the schema, so every seed must supply it explicitly.
INSERT INTO permissions (code, module, name, description, type, created_at)
VALUES
    ('DASHBOARD_VIEW', 'DASHBOARD', 'View Dashboard',
     'Open the owner dashboard. Grants no data on its own -- every block and alert is gated on '
     || 'the permission of the module it reads from, so a user sees exactly the blocks they could '
     || 'already see elsewhere.', 'ACCESS',
     CURRENT_TIMESTAMP)
ON CONFLICT (code) DO UPDATE
SET module = EXCLUDED.module,
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    type = EXCLUDED.type,
    is_active = TRUE,
    updated_at = CURRENT_TIMESTAMP;

-- OWNER holds every active permission (V3 grants by `p.is_active = TRUE`),
-- but that statement has already run -- so a new code needs an explicit
-- grant or the role silently falls behind the permission table.
INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT r.id, p.id, CURRENT_TIMESTAMP
FROM roles r
JOIN permissions p ON p.code = 'DASHBOARD_VIEW'
WHERE r.code = 'OWNER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- BRANCH_MANAGER and ACCOUNTANT get the gate too, and it is safe to give
-- it to them for the same reason it is safe to give it to anyone: the
-- gate reveals nothing. A branch manager's token is branch-scoped, so
-- every query behind it narrows to their own branch (D135), and the
-- blocks they have no permission for come back withheld rather than
-- blank. Granting the page is not granting the figures.
INSERT INTO role_permissions (role_id, permission_id, created_at)
SELECT r.id, p.id, CURRENT_TIMESTAMP
FROM roles r
JOIN permissions p ON p.code = 'DASHBOARD_VIEW'
WHERE r.code IN ('BRANCH_MANAGER', 'ACCOUNTANT')
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- CASHIER does not receive it, and that is a deliberate absence rather
-- than an oversight. A cashier stands at a drawer; the dashboard exists
-- to judge how that drawer is being run, and alert C2 names cashiers who
-- are repeatedly short. Handing the subject of a control the control's
-- output defeats it. Stated as an assertion so that a later "grant the
-- cashier the dashboard, it is only a read screen" edit has to argue
-- with this comment first.
DO $$
DECLARE
    leaked INTEGER;
BEGIN
    SELECT COUNT(*) INTO leaked
    FROM role_permissions rp
    JOIN roles r       ON r.id = rp.role_id
    JOIN permissions p ON p.id = rp.permission_id
    WHERE r.code = 'CASHIER'
      AND p.code = 'DASHBOARD_VIEW';

    IF leaked > 0 THEN
        RAISE EXCEPTION
            'CASHIER must not hold DASHBOARD_VIEW: alert C2 reports cashier shortage patterns, '
            'and the role it reports on cannot be the role that reads it.';
    END IF;
END $$;
