-- Dedicated permissions for high-risk inventory document actions.

INSERT INTO permissions (code, module, name, description, type)
VALUES
    ('PURCHASE_INVOICE_UNPOST', 'INVENTORY', 'Unpost Purchase Invoice',
     'Move a posted purchase invoice back to complete by recording ledger reversals.', 'ACTION'),
    ('PURCHASE_INVOICE_DELETE', 'INVENTORY', 'Delete Purchase Invoice',
     'Permanently delete draft purchase invoices that have no ledger history.', 'ACTION'),
    ('PURCHASE_RETURN_UNPOST', 'INVENTORY', 'Unpost Purchase Return',
     'Move a posted purchase return back to complete by recording ledger reversals.', 'ACTION'),
    ('PURCHASE_RETURN_DELETE', 'INVENTORY', 'Delete Purchase Return',
     'Permanently delete draft purchase returns that have no ledger history.', 'ACTION'),
    ('PHYSICAL_COUNT_DELETE', 'INVENTORY', 'Delete Physical Count',
     'Permanently delete draft or in-progress physical counts.', 'ACTION')
ON CONFLICT (code) DO UPDATE
SET module = EXCLUDED.module,
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    type = EXCLUDED.type,
    is_active = TRUE,
    updated_at = CURRENT_TIMESTAMP;

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

