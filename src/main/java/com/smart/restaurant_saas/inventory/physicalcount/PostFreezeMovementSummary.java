package com.smart.restaurant_saas.inventory.physicalcount;

import java.math.BigDecimal;

/**
 * Projection returned by the post-freeze movement summary query in InventoryTransactionRepository:
 * one row per material that moved in the warehouse after a count's freeze cutoff.
 */
public interface PostFreezeMovementSummary {
    Long getMaterialId();
    String getMaterialCode();
    String getMaterialName();
    String getMaterialNameAr();
    Long getMovementCount();
    BigDecimal getQuantityIn();
    BigDecimal getQuantityOut();
}
