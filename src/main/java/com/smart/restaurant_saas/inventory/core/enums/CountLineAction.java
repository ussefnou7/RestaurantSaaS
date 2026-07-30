package com.smart.restaurant_saas.inventory.core.enums;

public enum CountLineAction {
    PENDING,         // not yet reconciled
    NO_DIFFERENCE,   // variance = 0
    ADJUSTMENT,      // COUNT_ADJUSTMENT transaction — the only outcome a variance can produce

    /**
     * Legacy only, never written. A count no longer offers a waste option: every variance posts a
     * COUNT_ADJUSTMENT and the direction carries the sign. Retained so lines reconciled before V35
     * still deserialize — historical rows are left exactly as they are.
     */
    WASTE
}
