package com.smart.restaurant_saas.inventory.reports.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * One row of the low-stock report: a material whose on-hand quantity in a warehouse has fallen
 * below its configured minimum, with the shortfall to reorder.
 *
 * <p>Quantity fields are {@code String} for the same reason as {@link StockValuationRow} — the
 * scale-6 decimal survives JSON transport exactly; conversion happens in
 * {@code LowStockReportService}.
 */
@Getter
@Builder
public class LowStockRow {

    private final Long warehouseId;
    private final String warehouseName;
    private final String warehouseNameAr;
    private final Long materialId;
    private final String materialName;
    private final String materialNameAr;
    private final Long categoryId;
    private final String categoryName;
    private final String categoryNameAr;
    private final String quantity;
    private final String minQuantity;
    private final String shortfall;
}
