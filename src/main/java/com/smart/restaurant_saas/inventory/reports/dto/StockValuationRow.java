package com.smart.restaurant_saas.inventory.reports.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * One row of the stock valuation report: a material's on-hand quantity and value in one warehouse.
 *
 * <p>Quantity/cost fields are {@code String}, not {@code BigDecimal}, so the exact scale-6 decimal
 * survives JSON transport without the client re-parsing it as a floating-point number. This differs
 * from older inventory DTOs (e.g. {@code StockBalanceResponse}) that expose raw {@code BigDecimal};
 * the conversion happens at the mapping boundary in {@code StockValuationReportService}.
 */
@Getter
@Builder
public class StockValuationRow {

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
    private final String averageCost;
    private final String totalValue;
}
