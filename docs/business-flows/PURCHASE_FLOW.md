# Purchase Invoice & Purchase Return — Business Flow

## Overview
The purchasing flow covers receiving goods from suppliers and recording
the cost of materials entering the warehouse.

## Purchase Invoice Flow

### Statuses
DRAFT → COMPLETE → POSTED
DRAFT or COMPLETE → CANCELLED

### DRAFT
- Created by warehouse staff when goods arrive
- Can be edited freely (lines can be added, removed, changed)
- No inventory impact

### COMPLETE
- Reviewed and approved by manager
- No further editing allowed
- No inventory impact yet

### POSTED
- Stock In triggered for all lines
- StockBalance updated:
    - quantity increases
    - averageCost recalculated using weighted average
    - lastPurchasePrice and lastPurchaseDate updated
- Irreversible — use Purchase Return to correct
- Accounting document can be created by accountant after posting

### CANCELLED
- Only from DRAFT or COMPLETE
- No inventory impact

## Purchase Return Flow

### When to use
- Supplier delivered damaged goods
- Wrong quantity received
- Wrong specification

### Rules
- Can only be created against a POSTED invoice
- Each return line references the original invoice line
- Return quantity per line ≤ original quantity - already returned quantity
- Unit cost is always copied from the original line (cannot be changed)
- Multiple returns against the same invoice are allowed
  as long as total returned ≤ original quantity per line

### POSTED Return Impact
- Stock Out for returned quantities at original cost
- lastPurchasePrice restored to previous valid purchase
- Accounting document can be created after posting

## Cost Logic
- All quantities stored in material's stockUom (base unit)
- averageCost = weighted average, updated on every PURCHASE IN
- lastPurchasePrice = most recent posted purchase price per (warehouse, material)
- OUT transactions (return, consumption, waste) use averageCost at time of transaction
- lastPurchasePrice is NOT updated by returns — restored to previous valid purchase

## API Reference
Swagger UI at /swagger-ui.html
Tags: "Inventory - Purchase Invoice", "Inventory - Purchase Return"
