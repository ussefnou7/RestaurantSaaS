# MODULE — Fixed Assets

> **Last verified against code:** backend `63ff8e7e`, admin-web `c0f2155`
> on 2026-08-30 by Claude Code (doc drift audit — [../../claude/DOC_DRIFT_AUDIT.md](../../claude/DOC_DRIFT_AUDIT.md)).
> Claims below this line are only as current as those commits.

> **Backend: built** (`V16__assets.sql`, `V17__assets_view_permission.sql`; eight test files).
> **Frontend: built** — asset list/detail/registration, disposal form + disposals list,
> maintenance form + list, and reports, all on the real `assetService`. Design decisions
> D46–D52 in [DECISIONS](../DECISIONS.md); this doc holds the schema-level detail.
> Referenced from [PROJECT](../PROJECT.md) and [ROADMAP](../ROADMAP.md).

## Purpose

Tracks everything the tenant has bought for the restaurant that isn't consumable inventory —
furniture, kitchen equipment, finishing/renovation spend, electronics. Each asset can later be
written off (disposed) or have maintenance recorded against it. **V1 explicitly excludes** any
cost-coverage / ROI reporting against profit — that depends on the not-yet-built P&L/accounting
module (see O10 in DECISIONS.md).

## Package layout

Feature-based, same pattern as `inventory/`:

```
assets/
  asset/        — Asset entity + controller + service + dto/
  assetline/    — AssetLine entity + controller + service + dto/
  disposal/     — AssetDisposal entity + controller + service + dto/
  maintenance/  — AssetMaintenance entity + controller + service + dto/
  report/       — AssetReportController + service + dto/
  mapper/       — shared response mappers
  core/         — AssetErrorCode, shared enums (core/enums/)
```

## Entity / table map

| Entity (Java) | Table (snake_case) | Base class |
|---|---|---|
| `Asset` | `asset` | `TenantAwareEntity` |
| `AssetLine` | `asset_line` | `TenantAwareEntity` |
| `AssetDisposal` | `asset_disposal` | `TenantAwareEntity` |
| `AssetMaintenance` | `asset_maintenance` | `TenantAwareEntity` |

## Schema

**`asset`** — the header/parent; represents an asset *type* (e.g. "Wood Chair", "Oven").
```
id              BIGINT (identity)
tenant_id       BIGINT NOT NULL
branch_id       BIGINT NOT NULL   FK -> branch.id
name            VARCHAR(255) NOT NULL
name_ar         VARCHAR(255)
category        VARCHAR(30) NOT NULL   -- enum, EnumType.STRING
status          VARCHAR(30) NOT NULL   -- derived from aggregate line state, not written directly
```

**`asset_line`** — one row per purchase event/batch under an `Asset`. Same reason
`StockBatch` exists under a `Material`: the same asset type is commonly bought at different
prices/times and that needs to stay distinguishable (D46).
```
id                  BIGINT (identity)
tenant_id           BIGINT NOT NULL
asset_id            BIGINT NOT NULL   FK -> asset.id
label               VARCHAR(255)      -- nullable; free text, for identifying a single
                                       -- trackable unit (e.g. "Oven — North Kitchen", "OVN-01")
                                       -- when quantity = 1. No enforcement tying label
                                       -- presence to quantity.
quantity            NUMERIC(18,6) NOT NULL
remaining_quantity  NUMERIC(18,6) NOT NULL
unit_cost           NUMERIC(18,6) NOT NULL
total_cost          NUMERIC(18,6) NOT NULL
purchase_date       DATE NOT NULL
status              VARCHAR(30) NOT NULL   -- derived from remaining_quantity vs quantity
```

**`asset_disposal`** — write-off events. Manual target selection, never FIFO (D46).
```
id                  BIGINT (identity)
tenant_id           BIGINT NOT NULL
asset_id            BIGINT NOT NULL   FK -> asset.id       -- sent alongside asset_line_id, see D51
asset_line_id       BIGINT NOT NULL   FK -> asset_line.id
quantity_disposed   NUMERIC(18,6) NOT NULL   -- capped at asset_line.remaining_quantity
reason              VARCHAR(30) NOT NULL     -- enum
disposal_date       DATE NOT NULL
notes               VARCHAR(1000)
created_by          BIGINT
```

**`asset_maintenance`** — cost records only; never touches quantity (D49).
```
id                  BIGINT (identity)
tenant_id           BIGINT NOT NULL
asset_id            BIGINT NOT NULL   FK -> asset.id       -- sent alongside asset_line_id, see D51
asset_line_id       BIGINT NOT NULL   FK -> asset_line.id
cost                NUMERIC(18,6) NOT NULL
maintenance_date    DATE NOT NULL
description         VARCHAR(1000)
vendor              VARCHAR(255)
created_by          BIGINT
```

## Enums (`assets/core/enums/`)

```java
AssetCategory       { FURNITURE, KITCHEN_EQUIPMENT, FINISHING, ELECTRONICS, OTHER }   // D47
AssetStatus         { ACTIVE, PARTIALLY_DISPOSED, FULLY_DISPOSED }
AssetLineStatus     { ACTIVE, PARTIALLY_DISPOSED, FULLY_DISPOSED }
AssetDisposalReason { DAMAGED, LOST, OBSOLETE, SOLD }
```

## Error handling

`AssetErrorCode implements ErrorCode` — does not reuse `InventoryErrorCode` or any other
module's enum, per [CONVENTIONS](../CONVENTIONS.md). Values as built:
`RESOURCE_NOT_FOUND`, `INVALID_DATE_RANGE`, `LINE_ASSET_MISMATCH` (D51),
`DISPOSAL_EXCEEDS_REMAINING` (D48), `ASSET_HAS_LINES` and `LINE_HAS_CHILD_RECORDS` (D50's
delete guards).

## Permission

Two permissions (D52): `ASSETS_VIEW` gates all `GET` endpoints (including the report
endpoints); `ASSETS_MANAGE` gates all writes. `ASSETS_VIEW` was added by
`V17__assets_view_permission.sql`, following the existing RBAC permission-seed migration pattern
(mirrors `INVENTORY_PURCHASE_MANAGE`'s).

Writes are: `Asset` create/update/delete, `AssetLine` **create/delete only**, and
`AssetDisposal`/`AssetMaintenance` create. There is deliberately **no** `PUT` on an asset line
(D110) — a line is a purchase event, and correcting one means deleting and re-registering it.

## URLs

```
/api/assets                                          -- Asset CRUD (GET list/{id}, POST, PUT, DELETE)
/api/assets/{assetId}/lines                          -- AssetLine list/get/create/delete (no update, D110)
/api/assets/{assetId}/lines/{lineId}/disposals        -- AssetDisposal create/list (D51: nested, not flat)
/api/assets/{assetId}/lines/{lineId}/maintenance      -- AssetMaintenance create/list (D51: nested, not flat)
/api/assets/disposals                                 -- flat, cross-asset disposal list (read-only)
/api/assets/maintenance                               -- flat, cross-asset maintenance list (read-only)
/api/assets/reports/summary                           -- total asset value
/api/assets/reports/disposals                         -- disposal history
```

The two flat list endpoints are **reads only** — D51's "nested, not flat" rule governs the
create paths, which stay nested so both ids are always carried and validated together.

## UI flow (frontend, for context — not a backend rule)

User picks an `Asset` → UI fetches its `AssetLine`s → user picks a line. If exactly one active
line exists, the picker may pre-fill it as a convenience default — this is UI-only; the backend
always requires and validates both `assetId` and `assetLineId` explicitly (D51), and never
infers a line on its own (D46).

## V1 scope boundary

**In scope:** Asset/AssetLine register, disposal, maintenance, total-asset-value report,
disposal history list (date/reason/value).

**Explicitly out of scope for V1:** cost-coverage / ROI percentage against net profit (O10) —
blocked on the not-yet-built P&L/accounting module. Do not build a placeholder for this; there
is nothing correct to compute yet.