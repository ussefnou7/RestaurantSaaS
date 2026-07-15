package com.smart.restaurant_saas.assets.core.enums;

/**
 * Derived status of an {@code Asset} header (D48). Recomputed as an aggregate over its lines
 * after every mutation — never free-form writable by the caller.
 */
public enum AssetStatus {
    ACTIVE,
    PARTIALLY_DISPOSED,
    FULLY_DISPOSED
}
