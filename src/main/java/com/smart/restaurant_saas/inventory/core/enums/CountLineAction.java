package com.smart.restaurant_saas.inventory.core.enums;

public enum CountLineAction {
    PENDING,         // not yet reconciled
    NO_DIFFERENCE,   // variance = 0
    ADJUSTMENT,      // COUNT_ADJUSTMENT transaction
    WASTE            // WASTE transaction (negative variance only)
}
