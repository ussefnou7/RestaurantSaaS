package com.smart.restaurant_saas.assets.core.enums;

/**
 * Reason recorded on an {@code AssetDisposal}. The CHECK constraint on
 * {@code asset_disposal.reason} mirrors these values exactly.
 */
public enum AssetDisposalReason {
    DAMAGED,
    LOST,
    OBSOLETE,
    SOLD
}
