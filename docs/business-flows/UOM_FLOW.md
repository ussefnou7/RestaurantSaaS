# Unit of Measure (UOM) — Business Flow

## Overview

UOMs define how inventory quantities are measured and converted.
Two tiers exist: Global UOMs and Tenant UOMs.

> **The invariant, first, because everything else depends on it.**
> Every `factorToBase` in the table is relative to the **root** of its chain. This is what makes
> `physicalConvert` valid: it multiplies by the source factor and divides by the target factor, and
> that division is only meaningful when both operands were measured from the same zero. The tree is
> flat by construction — every non-root points directly at a root.

See **D102** for the decision and the reasoning behind it.

## Tiers

### Global UOMs (SysAdmin)
- Created by SysAdmin from the Admin Panel
- Available to ALL tenants automatically — no setup needed
- Cannot be deleted once created
- Can be deactivated — disappears from all tenant screens
- Historical data is never affected by deactivation

### Tenant UOMs (Custom)
- Created by the tenant for their own non-standard units
- Only visible to the owning tenant
- Can be deleted if not used anywhere
- Can be deactivated if in use but no longer needed going forward

A tenant UOM may be the parent of another tenant UOM. Sack → box → kilogram is legal; it is
flattened on write like anything else.

## What `baseUom` actually means

`baseUom` is **not a step in the conversion.** `physicalConvert` never visits it — KILOGRAM → TON
works today and neither is a root.

It marks the **calibration point**: the shared zero that makes two factors comparable. `sameBaseUom`
is a comparability guard, not a step in the arithmetic. Two units convert if and only if they
resolve to the same root.

`baseUom` is NULL for a root, which pairs with `factorToBase = 1`.

## Storage

Every UOM stores four conversion-related values:

| Column | Meaning |
|---|---|
| `base_uom_id` | The root of the chain. NULL means "this **is** a root" |
| `factor_to_base` | How many **root** units equal 1 of this UOM. The engine reads only this |
| `entered_factor` | The factor as the user typed it. `NOT NULL`. Display only |
| `entered_against_uom_id` | The parent the user picked — not necessarily the root. NULL for roots |

### The entered pair is display metadata

`entered_factor` and `entered_against_uom_id` are written only inside
`UomService.createForTenant`, and are absent from every calculation. They exist so the form can
show "25 KILOGRAM" instead of "25000 GRAM".

**No code may read them for arithmetic.** A divergence between the entered pair and `factorToBase`
would be worse than no pair at all — the screen would confidently display a number the engine does
not use.

### Examples

| UOM             | baseUom | factorToBase | enteredFactor | enteredAgainst |
|-----------------|---------|--------------|---------------|----------------|
| GRAM            | null    | 1            | 1             | null           |
| KG              | GRAM    | 1000         | 1000          | GRAM           |
| TON             | GRAM    | 1000000      | 1000000       | GRAM           |
| Box of Tomatoes | GRAM    | 6000         | 6             | KG             |
| Oil Can         | ML      | 3000         | 3             | LITRE          |

Note the last two rows: what the user typed and what the engine stores differ, and both are kept.

## Normalization on write

The user picks any visible unit of the same type as the parent and types a factor against **that**
unit. `UomService.buildUom` normalizes before persisting:

```
user enters:  sack ← KILOGRAM ← 25

stored:       base_uom_id = GRAM
              factor_to_base = 25 × 1000 = 25000
              entered_factor = 25
              entered_against_uom_id = KILOGRAM
```

The parent is already root-calibrated, so one multiplication is the whole normalization. Choosing a
root as the parent multiplies by 1, so there is no special case — one path covers both.

`buildUom` is the only place any of these four values is written.

### `type` is derived from the parent

A unit cannot be a different physical type from the thing it is calibrated against, so `type` is
taken from the parent and a disagreeing request value is **ignored, not rejected**. The type
selector on the tenant form is a filter that narrows the parent list; it is not a submitted field.

### The parent must be visible to the tenant

Resolved through `resolveParentUom`, which rejects another tenant's private UOM with
`UOM_BASE_NOT_AVAILABLE`. A tenant never creates a root, so a missing parent on the tenant path is
rejected with `UOM_BASE_REQUIRED`.

## Database constraints

```sql
ck_uom_root_factor   -- a claimed root cannot carry a factor <> 1
ck_uom_no_self_base  -- a unit cannot be its own base
```

`ck_uom_root_factor` rejects exactly the row shape the sysadmin panel produces (**O37**) — a unit
claiming to be a calibration root while carrying a real conversion factor.

`ck_uom_no_self_base` forecloses self-reference, which was considered and rejected as an
alternative to a nullable `base_uom_id`.

## Conversion

```
result = value × from.factorToBase ÷ to.factorToBase
```

Both factors are root-relative, so the division is meaningful.

```
2 KG → GRAM              = 2 × 1000 ÷ 1        = 2000 GRAM
1 Box of Tomatoes → KG   = 1 × 6000 ÷ 1000     = 6 KG
1 sack → KILOGRAM        = 1 × 25000 ÷ 1000    = 25 KG
40 sacks → TON           = 40 × 25000 ÷ 1000000 = 1 TON
```

### The compatibility rule — base identity, stated once

Two units are convertible **iff they resolve to the same root.** This one rule applies in all three
places that implement it:

| Where | Implementation |
|---|---|
| `UomConversionService.convert` | `sameBaseUom` |
| `UomService.convertValue` | delegates to `UomConversionService` |
| Frontend `inventoryUom.ts` | the `rootOf` helper |

**Type equality is too weak and must not be substituted.** Two roots of the same physical type — a
POUND with `factorToBase = 1` sitting beside GRAM — pass a type check and produce nonsense, because
their factors were never measured from the same zero. Three different rules previously coexisted
here, which was user-visible: the UI offered a unit, previewed a converted quantity, and the save
then threw.

All conversion math is `BigDecimal`, `scale = 6`, `HALF_UP` (CONVENTIONS).

## Why no `material_uom_conversion` table?

An earlier design had a per-material conversion table. Removed because:

- `factorToBase` on the UOM itself handles all conversions
- Non-standard units (box, can, sack) are modeled as their own UOM with their own `factorToBase`
- No redundant data, no consistency risk, simpler service layer

## Lifecycle Rules

```
Global UOM:  CREATE → [ACTIVE] → [INACTIVE]
                                     ↑ only transition allowed, cannot be reversed via API

Tenant UOM:  CREATE → [ACTIVE] → [INACTIVE]
                         ↓
                      [DELETED] only if zero references
```

## What is not built

Do not assume these exist — they do not.

**No update path (O35).** There is no `PUT /uom/{id}` on the tenant path and no update method on
`UomService`. A UOM cannot be edited at all today, and there is deliberately no edit button,
disabled control, or "coming soon" affordance in the web app until a backend endpoint exists.

`factorToBase`, `baseUom` and `type` are **decided to be immutable once the unit is in use** —
editing a factor after transactions exist does not change the ledger, but silently reinterprets
every number already written into it. That guard is specified in D102 but **not yet enforced**,
because there is no update path to attach it to. An update endpoint must ship together with the
normalization step and the immutability guard; any one of the three alone is worse than nothing.

**No deactivate control in the tenant web app (O35a).** The endpoint exists
(`PATCH /api/uom/{id}/deactivate`) but the UI control does not, so a wrongly-created unit currently
has no exit.

**The sysadmin panel create is broken (O37).** `AdminUomFormModal` posts `baseCode` where the DTO
declares `baseUom`, so Jackson drops it and every panel-created global UOM lands as a root. Left
unfixed deliberately: `ck_uom_root_factor` now makes it fail loudly at the database instead of
silently producing an unconvertible unit.

## Related decisions

- **D102** — this model: root-relative factors, flattening on write, the entered pair
- **D87** — the two-UOM-layer model (ledger is stock-UOM; balances, batches and count lines are
  display-UOM). A different axis from the global/tenant tiers above, and unaffected by D102
- **D88** — ledger-sourced quantities crossing an API boundary must be converted to the display
  layer and carry an explicit UOM field
- **D3** — the single entered→stock conversion happens inside `InventoryLedgerService.record()`
- **D13** — no premature abstraction. The absent per-material conversion table is one of its
  worked examples: `factorToBase` + a `baseUom` self-reference, nothing more

## API Reference

See Swagger UI at `/swagger-ui.html`
Tags: "Inventory - UOM" and "SysAdmin - UOM"
