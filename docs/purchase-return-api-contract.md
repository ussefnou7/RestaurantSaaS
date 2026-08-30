# Purchase Return — FE API Contract

> **Last verified against code:** backend `63ff8e7e` on 2026-08-30 by Claude Code
> (doc drift audit — [../claude/DOC_DRIFT_AUDIT.md](../claude/DOC_DRIFT_AUDIT.md)).
> Claims below this line are only as current as that commit.

Base path: `/api/inventory/purchase-returns`

A purchase return is created against a **POSTED** purchase invoice. The flow is
header-first (same shape as Purchase Invoice): save the header, fetch the returnable
lines, add/edit lines individually, then `complete` → `post`.

---

## Conventions

### Required headers (all endpoints)
| Header | Required | Notes |
|---|---|---|
| `X-Tenant-Id` | yes | Tenant scope. |
| `X-User-Id` | optional | Acting user; recorded on create/update/post. |
| `Authorization` | yes | Standard auth; subject must hold the permission below. |

### Permissions
- **View** endpoints (`GET …`): `INVENTORY_PURCHASE_VIEW`
- **Mutating** endpoints (create/update/lines/complete/post/cancel): `INVENTORY_PURCHASE_MANAGE`
- SysAdmin bypasses the permission check.

### Money / quantity types
All amounts and quantities are decimals with up to 6 fraction digits, serialized as JSON
numbers (e.g. `12.5`, `100.000000`). Dates: `returnDate` is a **date** (`YYYY-MM-DD`);
all `*At` fields are **date-times** (ISO-8601).

### Error response (all non-2xx)

`ApiErrorResponse`, emitted by `GlobalExceptionHandler`:

```jsonc
{
  "errorCode": "INVALID_STATE_TRANSITION",   // render user text from this + params
  "message": "Only DRAFT returns can be completed",  // English debug text, LOGS ONLY — never shown
  "params": { "entityType": "PurchaseReturn", "currentStatus": "COMPLETE", "requiredStatus": "DRAFT" },
  "status": 409,
  "timestamp": "2026-06-27T10:15:30",
  "path": "/api/inventory/purchase-returns/42/complete",
  "fieldErrors": null                        // populated on 400: [{ field, errorCode, params }]
}
```

> There is no `success` envelope field. Per D12 and [CONVENTIONS](CONVENTIONS.md), the FE builds
> user-facing text from `errorCode` + `params` through `translateApiError`; rendering `message`
> is a defect.

| HTTP | When |
|---|---|
| `400 Bad Request` | Bean-validation failure — `fieldErrors[]` carries one entry per field. |
| `401 Unauthorized` | Auth failure. |
| `403 Forbidden` | Missing permission. |
| `404 Not Found` | Return / invoice / line not found for this tenant. |
| `409 Conflict` | Business rule violation (wrong status, qty exceeds returnable, etc.). |

---

## Enums

**`reason`** (`PurchaseReturnReason`): `DAMAGED`, `WRONG_QUANTITY`, `WRONG_SPEC`, `EXPIRED`, `OTHER`

**`status`** (`DocumentStatus`): `DRAFT`, `COMPLETE`, `POSTED`, `CANCELLED`

### Lifecycle
```
DRAFT ──complete──▶ COMPLETE ──post──▶ POSTED
  ▲                    ▲   │              │
  └──uncomplete────────┘   │              │
  ▲                        │              │
  └────────────────────────┴──unpost──────┘   (unpost returns to COMPLETE)
  │                    │
  └────── cancel ──────┴──▶ CANCELLED            (POSTED cannot be cancelled)
```
- Header and lines are editable only in **DRAFT**.
- `complete` requires at least one line.
- `post` applies stock-out to inventory at the source batch's original cost.
- **POSTED is reversible.** `unpost` returns the document to COMPLETE and, unlike a purchase
  invoice, carries **no** batch-consumption guard because restoring a return is additive (D9);
  the restore is capped at the source batch's original quantity. `uncomplete` returns a COMPLETE
  document to DRAFT.

---

## Objects

### `PurchaseReturnResponse`
```jsonc
{
  "id": 42,
  "originalInvoiceId": 17,
  "originalInvoiceNumber": "ACME-PINV2026-0007",
  "supplierId": 3,
  "supplierName": "Acme Foods",
  "warehouseId": 5,
  "warehouseName": "Main Store",
  "returnNumber": "ACME-PRET2026-0001",   // null only before header is saved (never in responses)
  "returnDate": "2026-06-27",
  "reason": "DAMAGED",
  "status": "DRAFT",
  "subtotal": 150.000000,
  "totalAmount": 150.000000,
  "postedToInventory": false,
  "postedAt": null,
  "notes": "Two crates damaged in transit",
  "lines": [ /* PurchaseReturnLineResponse[]; null on list/summary view */ ],
  "createdAt": "2026-06-27T09:00:00Z",
  "updatedAt": "2026-06-27T09:10:00Z"
}
```

### `PurchaseReturnLineResponse`
```jsonc
{
  "id": 88,
  "originalLineId": 31,          // the invoice line this return line draws from
  "materialId": 9,
  "materialCode": "MAT-009",
  "materialName": "Tomato Paste",
  "quantity": 3.000000,
  "uomId": 2,
  "uomSymbol": "kg",
  "unitCost": 50.000000,         // copied from the original invoice line; not editable
  "lineTotal": 150.000000,       // quantity × unitCost
  "notes": null
}
```

### `ReturnableLineResponse` (from `GET /{id}/returnable-lines`)
```jsonc
{
  "originalLineId": 31,
  "materialId": 9,
  "materialCode": "MAT-009",
  "materialName": "Tomato Paste",
  "uomId": 2,
  "uomSymbol": "kg",
  "unitCost": 50.000000,
  "originalQuantity": 10.000000,
  "returnedQuantity": 2.000000,    // already taken by POSTED returns of this invoice
  "returnableQuantity": 8.000000   // originalQuantity − returnedQuantity
}
```

---

## Endpoints

### 1. List returns
`GET /api/inventory/purchase-returns` · perm: VIEW
- **200** → `PurchaseReturnResponse[]` (summary view: `lines` is `null`), newest `returnDate` first.

### 2. Get return
`GET /api/inventory/purchase-returns/{id}` · perm: VIEW
- **200** → `PurchaseReturnResponse` (full, with `lines`). · **404** if not found.

### 3. Create header
`POST /api/inventory/purchase-returns` · perm: MANAGE
- Body:
```json
{ "originalInvoiceId": 17, "returnDate": "2026-06-27", "reason": "DAMAGED", "notes": "optional" }
```
| field | type | required | notes |
|---|---|---|---|
| `originalInvoiceId` | long | yes | Must be a **POSTED** invoice in this tenant. |
| `returnDate` | date | yes | |
| `reason` | enum | yes | See `PurchaseReturnReason`. |
| `notes` | string | no | |
- **201** → `PurchaseReturnResponse` (DRAFT, `returnNumber` generated, no lines, totals = 0).
- **404** invoice not found · **409** invoice not POSTED.

### 4. Update header
`PUT /api/inventory/purchase-returns/{id}` · perm: MANAGE · DRAFT only
- Body: same as create. `originalInvoiceId` is **ignored** (cannot change the source invoice);
  send it anyway (it is `@NotNull`-validated) or the current value.
- **200** → updated `PurchaseReturnResponse` · **409** if not DRAFT.

### 5. Returnable lines  ⭐ (call after saving the header)
`GET /api/inventory/purchase-returns/{id}/returnable-lines` · perm: VIEW
- **200** → `ReturnableLineResponse[]` — the original invoice's lines that still have
  `returnableQuantity > 0` (fully-returned lines are omitted).
- `returnedQuantity` counts **POSTED** returns only; it does **not** subtract lines already
  added to *this* draft. Use it to populate the picker; the add/update endpoints enforce the
  true remaining limit including this draft.

### 6. Add line
`POST /api/inventory/purchase-returns/{id}/lines` · perm: MANAGE · DRAFT only
- Body:
```json
{ "originalLineId": 31, "quantity": 3, "uomId": 2, "notes": "optional" }
```
| field | type | required | notes |
|---|---|---|---|
| `originalLineId` | long | yes | Must belong to this return's original invoice. |
| `quantity` | decimal | yes | `> 0`. |
| `uomId` | long | **yes** | `@NotNull`. Material and unit cost are derived from the original line, but the UOM is **not** — the backend resolves and stores whatever is submitted, converting accordingly. |
| `notes` | string | no | |

> **The UOM lock is a UI convention, not an enforced invariant (D108).** The admin web app
> renders this field read-only, seeded from the source line. Another client can submit a
> different `uomId` and the backend will accept and convert it. See [PROJECT](PROJECT.md) →
> Known drift.
- **200** → full `PurchaseReturnResponse` (with recalculated `lines`/totals).
- **409** if `quantity` exceeds remaining returnable
  (`originalQty − POSTED-returned − this draft's qty for the same original line`), or not DRAFT.

### 7. Update line
`PUT /api/inventory/purchase-returns/{id}/lines/{lineId}` · perm: MANAGE · DRAFT only
- Body:
```json
{ "quantity": 5, "notes": "optional" }
```
- The referenced original line / material / unit cost cannot change (delete + re-add to repoint).
- **200** → full `PurchaseReturnResponse` · **404** line not found · **409** qty exceeds returnable / not DRAFT.

### 8. Delete line
`DELETE /api/inventory/purchase-returns/{id}/lines/{lineId}` · perm: MANAGE · DRAFT only
- **200** → full `PurchaseReturnResponse` · **404** line not found · **409** not DRAFT.

### 9. Complete
`POST /api/inventory/purchase-returns/{id}/complete` · perm: MANAGE
- **200** → `PurchaseReturnResponse` (status `COMPLETE`).
- **409** if not DRAFT, or if the return has no lines.

### 10. Post to inventory
`POST /api/inventory/purchase-returns/{id}/post` · perm: MANAGE
- **200** → `PurchaseReturnResponse` (status `POSTED`, `postedToInventory: true`, `postedAt` set).
- **409** if not COMPLETE, or already posted.
- ⚠️ **This endpoint is currently missing its `@PreAuthorize` annotation** — authentication
  applies but the `INVENTORY_PURCHASE_MANAGE` gate does not. A known defect, not the contract;
  treat MANAGE as required. See [PROJECT](PROJECT.md) → Known defects.

### 11. Unpost
`POST /api/inventory/purchase-returns/{id}/unpost` · perm: MANAGE
- **200** → `PurchaseReturnResponse` (status back to `COMPLETE`).
- Restores the returned quantities to their source batches, capped at each batch's original
  quantity. No batch-consumption guard (D9).
- **409** if not POSTED.

### 12. Uncomplete
`POST /api/inventory/purchase-returns/{id}/uncomplete` · perm: MANAGE
- **200** → `PurchaseReturnResponse` (status back to `DRAFT`, editable again).
- **409** if not COMPLETE.

### 13. Cancel
`POST /api/inventory/purchase-returns/{id}/cancel` · perm: MANAGE
- Body (optional): `{ "reason": "string" }`
- **200** → `PurchaseReturnResponse` (status `CANCELLED`).
- **409** if POSTED, or not in DRAFT/COMPLETE.

---

## Typical FE flow
```
1. POST   /purchase-returns                      → returnId, returnNumber   (DRAFT)
2. GET    /purchase-returns/{id}/returnable-lines → render line picker
3. POST   /purchase-returns/{id}/lines           → repeat per chosen line
   (PUT/DELETE /{id}/lines/{lineId} to adjust)
4. POST   /purchase-returns/{id}/complete         → COMPLETE
5. POST   /purchase-returns/{id}/post             → POSTED (stock-out applied)

   to reverse:  POST /{id}/unpost → COMPLETE, then POST /{id}/uncomplete → DRAFT
```
Every line endpoint returns the full return (header + recalculated totals + lines), so the FE
can refresh from a single response without re-fetching.
