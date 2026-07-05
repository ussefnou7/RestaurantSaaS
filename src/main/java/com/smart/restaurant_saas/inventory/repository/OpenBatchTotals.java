package com.smart.restaurant_saas.inventory.repository;

import java.math.BigDecimal;

/**
 * Aggregate of a stock balance's OPEN batches, used to derive the balance's average cost
 * (and to validate its quantity during backfill) from the batches — the sole source of truth.
 *
 * <p>Both fields are {@code null} when the balance has no open batches (SUM over zero rows),
 * which callers treat as "no stock". {@code totalValue} sums {@code remainingQuantity * unitCost}
 * with a null unit cost coalesced to zero, matching how FIFO consumption values null-cost stock.
 */
public class OpenBatchTotals {

    private final BigDecimal totalRemaining;
    private final BigDecimal totalValue;

    public OpenBatchTotals(BigDecimal totalRemaining, BigDecimal totalValue) {
        this.totalRemaining = totalRemaining;
        this.totalValue = totalValue;
    }

    /** Sum of {@code remainingQuantity} over open batches (display UOM); null when none. */
    public BigDecimal getTotalRemaining() {
        return totalRemaining;
    }

    /** Sum of {@code remainingQuantity * unitCost} over open batches (money); null when none. */
    public BigDecimal getTotalValue() {
        return totalValue;
    }
}
