# Unit of Measure (UOM) — Business Flow

## Overview
UOMs define how inventory quantities are measured and converted.
The system uses a two-tier model: Global UOMs and Tenant UOMs.

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

## Conversion Logic

Every UOM stores:
- baseUom → the reference unit for its physical type (e.g., GRAM for weight)
- factorToBase → how many base units equal 1 of this UOM

### Examples

| UOM             | baseUom | factorToBase |
|-----------------|---------|--------------|
| GRAM            | null    | 1            |
| KG              | GRAM    | 1000         |
| TON             | GRAM    | 1000000      |
| Box of Tomatoes | GRAM    | 6000         |
| Oil Can         | ML      | 3000         |

### Conversion Formula

result = value × from.factorToBase ÷ to.factorToBase

Example: 2 KG → GRAM
= 2 × 1000 ÷ 1 = 2000 GRAM

Example: 1 Box of Tomatoes → KG
= 1 × 6000 ÷ 1000 = 6 KG

## Why No material_uom_conversion Table?
Earlier design had a separate conversion table per material.
This was removed because:
- factorToBase on the UOM itself handles all conversions
- Non-standard units (box, can) are modeled as their own UOM with their own factorToBase
- No redundant data, no consistency risk, simpler service layer

## Lifecycle Rules

Global UOM:  CREATE → [ACTIVE] → [INACTIVE]
                                      ↑ only transition allowed, cannot be reversed via API

Tenant UOM:  CREATE → [ACTIVE] → [INACTIVE]
                          ↓
                       [DELETED] only if zero references

## API Reference
See Swagger UI at /swagger-ui.html
Tags: "Inventory - UOM" and "SysAdmin - UOM"
