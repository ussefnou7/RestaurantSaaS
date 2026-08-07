package com.smart.restaurant_saas.inventory.reports;

import java.math.BigDecimal;

/**
 * Raw aggregate row for the waste analysis report, one per (material, reason), straight out of
 * {@code InventoryTransactionRepository.aggregateWaste}.
 *
 * <p>Same layering as {@link ShrinkageAggregate}: {@code netStockQuantity} is stock UOM (D87
 * layer 1) and is converted by {@code WasteAnalysisReportService} before it crosses the API
 * boundary (D88). {@code netValue} is money and does not convert.
 *
 * <p>{@code reasonCode} is already COALESCEd to {@code UNSPECIFIED} by the query, so it is never
 * null — a null reason must surface as its own bucket rather than vanish, or the rendered rows
 * would no longer sum to the real total.
 */
public record WasteAggregate(
    Long materialId,
    String materialCode,
    String materialName,
    String materialNameAr,
    String reasonCode,
    BigDecimal netStockQuantity,
    BigDecimal netValue,
    Long movementCount
) {
}
