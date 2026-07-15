package com.smart.restaurant_saas.assets.core.enums;

/**
 * Fixed backend catalogue of asset categories (D47). Not a tenant-configurable table.
 * The CHECK constraint on {@code asset.category} mirrors these values exactly.
 */
public enum AssetCategory {
    FURNITURE,
    KITCHEN_EQUIPMENT,
    FINISHING,
    ELECTRONICS,
    OTHER
}
