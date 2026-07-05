-- Dedicated UnComplete permissions and audit fields for purchase documents.
-- Interim default grants follow the existing inventory action-permission pattern
-- until approval workflows own assignment of document actions.

ALTER TABLE public.purchase_invoice
    ADD COLUMN IF NOT EXISTS uncompleted_at timestamp without time zone,
    ADD COLUMN IF NOT EXISTS uncompleted_by bigint;

ALTER TABLE public.purchase_return
    ADD COLUMN IF NOT EXISTS uncompleted_at timestamp without time zone,
    ADD COLUMN IF NOT EXISTS uncompleted_by bigint;

INSERT INTO permissions (code, module, name, description, type)
VALUES
    ('PURCHASE_INVOICE_UNCOMPLETE', 'INVENTORY', 'UnComplete Purchase Invoice',
     'Move a complete purchase invoice back to draft for editing.', 'ACTION'),
    ('PURCHASE_RETURN_UNCOMPLETE', 'INVENTORY', 'UnComplete Purchase Return',
     'Move a complete purchase return back to draft for editing.', 'ACTION')
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
    'PURCHASE_INVOICE_UNCOMPLETE',
    'PURCHASE_RETURN_UNCOMPLETE'
)
WHERE r.code IN ('OWNER', 'SYS_ADMIN')
ON CONFLICT (role_id, permission_id) DO NOTHING;
