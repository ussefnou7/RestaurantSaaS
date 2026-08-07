package com.smart.restaurant_saas.inventory.reports;

import java.math.BigDecimal;

/**
 * Raw aggregate row for the loss comparison report, one per material, straight out of
 * {@code InventoryTransactionRepository.aggregateLossComparison}.
 *
 * <p>Quantities are in the material's <b>stock UOM</b> (D87 layer 1); this type sits below the API
 * boundary and is never serialized. {@code LossComparisonReportService} converts both into the
 * display layer and attaches an explicit UOM before they become a {@code LossComparisonRow} (D88).
 *
 * <p><b>The two sides carry different sign conventions on purpose</b>, and this type is where that
 * starts:
 * <ul>
 *   <li>{@code waste*} is a <b>positive magnitude</b> — waste is always an outflow, so a minus on
 *       every row would add nothing. Computed as the negated signed net, not as a raw sum, so a
 *       (currently impossible) reversal nets to zero instead of double-counting.</li>
 *   <li>{@code shrinkage*} is <b>signed</b> — a surplus is positive and reveals a wrong recipe or a
 *       rushed count, which is worth seeing.</li>
 * </ul>
 *
 * <p>{@code totalValue} is the combined loss, loss-positive: {@code wasteValue - shrinkageValue}.
 * Both sides are folded into one "outflow is positive" sum by the query, which is why a shrinkage
 * surplus correctly reduces the total.
 *
 * <p>{@code movementCount} is the activity discriminator, not a display field: it is zero for a
 * material that had no waste and no shrinkage in the window, which is what partitions those rows to
 * the end of the sort.
 */
public record LossComparisonAggregate(
    Long materialId,
    String materialCode,
    String materialName,
    String materialNameAr,
    Boolean materialActive,
    BigDecimal wasteStockQuantity,
    BigDecimal wasteValue,
    BigDecimal shrinkageStockQuantity,
    BigDecimal shrinkageValue,
    BigDecimal totalValue,
    Long movementCount
) {
}
