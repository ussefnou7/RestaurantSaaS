# Physical Count (Inventory Count) — Business Flow

## Overview
Physical counts allow the branch manager to verify and correct
actual stock quantities against system quantities.

## Flow
DRAFT → IN_PROGRESS → RECONCILED
DRAFT or IN_PROGRESS → CANCELLED

## Statuses

### DRAFT
- Count created with selected materials
- Expected quantities not yet captured
- Materials can be added freely

### IN_PROGRESS
- frozenAt timestamp recorded
- expectedQuantity and unitCostAtFreeze snapshot from StockBalance
- Team enters counted quantities
- Inventory continues to operate normally during counting

### RECONCILED
- All variances posted to inventory
- stock_balance updated with lastCountDate and lastCountQuantity
- Large variance flag calculated
- Irreversible

## Freeze and Adjustment Logic

When count starts, a snapshot is taken:
    expectedQuantity = stock_balance.quantity at frozenAt

When reconciling, transactions after frozenAt are factored in:
    adjustedExpected = expectedQuantity
                     + Σ IN transactions after frozenAt
                     - Σ OUT transactions after frozenAt
    variance = countedQuantity - adjustedExpected

This ensures the variance reflects only real physical discrepancies,
not legitimate transactions that happened during the counting process.

## Variance Actions

ADJUSTMENT → COUNT_ADJUSTMENT transaction
             Corrects the balance with no waste implication

WASTE       → WASTE transaction (negative variance only)
             Records the loss as waste for reporting purposes
             Linked to the physical count as reference

## Large Variance
- Threshold: 500 EGP (fixed for now)
- large_variance_value = Σ |variance × unitCostAtFreeze|
- has_large_variance = true if total > 500 EGP
- Visible in dashboard for owner review

## API Reference
Swagger UI at /swagger-ui.html
Tag: "Inventory - Physical Count"
