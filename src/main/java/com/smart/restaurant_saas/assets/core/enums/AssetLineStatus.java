package com.smart.restaurant_saas.assets.core.enums;

/**
 * Derived status of an {@code AssetLine} (D48). Recomputed from {@code remainingQuantity} versus
 * {@code quantity} after every disposal — never free-form writable by the caller.
 */
public enum AssetLineStatus {
    ACTIVE,
    PARTIALLY_DISPOSED,
    FULLY_DISPOSED
}
