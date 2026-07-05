# Inventory Setup — Business Flow

## Overview
Before any inventory transactions can occur, the tenant must configure:
1. Warehouses — physical storage locations
2. Material Categories — grouping for materials
3. Materials — the actual items tracked in inventory

## Warehouse
- Each warehouse belongs to a tenant and optionally a branch
- Types: CENTRAL, BRANCH, KITCHEN, FREEZER, BAR, OTHER
- Deactivated warehouses cannot receive new transactions
- A warehouse can exist without a branch (standalone)

## Material Category
- Two tiers: Global (SysAdmin-managed, visible to all) and Tenant (custom)
- Global categories are read-only for tenants
- Used to group and filter materials across all screens

## Material
- Each material has:
  - stockUom: the BASE unit used for all inventory calculations and storage
  - displayUom: what the user sees in the UI (may differ from stockUom)
  - All quantities entered by users are converted to stockUom before storage
- Code is immutable after creation
- Materials can be created manually or imported from the global catalog

## Material Catalog (Import)
- SysAdmin maintains a global library of common restaurant materials
- Tenants can browse and import items in bulk with one click
- Imported materials are linked to the catalog via catalogId
- Already-imported items are flagged and hidden from the import list
- Partial import is supported — skipped items return a reason code

## API Reference
See Swagger UI at /swagger-ui.html
Tags: "Inventory Setup - Warehouse", "Inventory Setup - Material Category",
      "Inventory Setup - Material", "Inventory Setup - Catalog"

## Permissions
- View   → INVENTORY_SETUP_VIEW
- Manage → INVENTORY_SETUP_MANAGE

All endpoints require `Authorization: Bearer {token}` and `X-Tenant-Id: {tenantId}` headers.

## Shared Rules
- Global records (tenantId = NULL) are read-only for tenants — mutate attempts return HTTP 403
- The `active` flag controls visibility in dropdowns across all screens
- Code is immutable after creation — PUT requests that change it are rejected (HTTP 409)
- Search is server-side (case-insensitive LIKE on name and code)

## Endpoints

### Warehouse — `/api/inventory/warehouses`
| Method | Path | Permission |
|--------|------|------------|
| GET    | `/` (filters: search, branchId, type, active) | VIEW |
| GET    | `/{id}` | VIEW |
| POST   | `/` | MANAGE |
| PUT    | `/{id}` | MANAGE |
| PATCH  | `/{id}/activate` | MANAGE |
| PATCH  | `/{id}/deactivate` | MANAGE |

### Material Category — `/api/inventory/material-categories`
| Method | Path | Permission |
|--------|------|------------|
| GET    | `/` (filters: search, active) | VIEW |
| POST   | `/` | MANAGE |
| PUT    | `/{id}` (global → 403) | MANAGE |
| PATCH  | `/{id}/activate` (global → 403) | MANAGE |
| PATCH  | `/{id}/deactivate` (global → 403) | MANAGE |

### Material — `/api/inventory/materials`
| Method | Path | Permission |
|--------|------|------------|
| GET    | `/` (filters: search, categoryId, defaultUomId, active) | VIEW |
| GET    | `/{id}` | VIEW |
| POST   | `/` | MANAGE |
| POST   | `/import` (bulk catalog import) | MANAGE |
| PUT    | `/{id}` | MANAGE |
| PATCH  | `/{id}/activate` | MANAGE |
| PATCH  | `/{id}/deactivate` | MANAGE |

### Catalog — `/api/inventory/global-materials` and `/api/inventory/global-material-categories`
| Method | Path | Permission |
|--------|------|------------|
| GET    | `/api/inventory/global-materials` (filters: search, categoryId, uomId) | VIEW |
| GET    | `/api/inventory/global-material-categories` (filter: active) | VIEW |

## Stock Balance

- One StockBalance record per (tenant, warehouse, material) tuple
- Created automatically on the first transaction for that combination
- quantity is always in the material's stockUom (base unit)
- averageCost is a running weighted average updated on every IN transaction
- OUT transactions use averageCost at time of transaction
- isBelowMinimum and isBelowReorderPoint are derived at query time
- totalValue = quantity × averageCost

## Architecture Decision — One Warehouse Per Branch

Each branch has one main warehouse.
All transactions (purchases, consumption, waste) are recorded against this warehouse directly.
Sub-warehouses (kitchen, fridge, bar) are not implemented in this phase.
This keeps cost calculation simple and accurate:
- No average-of-average problem
- P&L costs are always traceable to real purchase prices
- Sub-warehouses can be added in a future phase if needed
