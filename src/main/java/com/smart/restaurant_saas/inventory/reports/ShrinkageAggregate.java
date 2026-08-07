package com.smart.restaurant_saas.inventory.reports;

import java.math.BigDecimal;

/**
 * Raw aggregate row for the shrinkage report, one per material, straight out of
 * {@code InventoryTransactionRepository.aggregateShrinkage}.
 *
 * <p>{@code netStockQuantity} is in the material's <b>stock UOM</b> (D87 layer 1) — this type sits
 * below the API boundary and is never serialized. {@code ShrinkageReportService} converts it into
 * the display layer and attaches an explicit UOM before it becomes a {@code ShrinkageRow} (D88).
 *
 * <p>{@code netValue} is money and does not convert.
 */
public record ShrinkageAggregate(
    Long materialId,
    String materialCode,
    String materialName,
    String materialNameAr,
    BigDecimal netStockQuantity,
    BigDecimal netValue,
    Long movementCount
) {
}
