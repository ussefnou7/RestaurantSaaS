package com.smart.restaurant_saas.order.reports;

import java.math.BigDecimal;

/**
 * Projection for the sales-by-product query in {@code OrderLineRepository}: one row per product sold
 * on a COMPLETE order in the window.
 *
 * <p>{@code revenue} is {@code SUM(line_total)} and is therefore <b>pre-tax</b> — tax lives on the
 * order, not the line. See {@code SalesByProductRow}.
 *
 * <p>There is no product code or Arabic name because the {@code product} table has neither column;
 * only {@code name} exists. Reported rather than faked with nulls.
 */
public interface SalesByProductAggregate {

    Long getProductId();

    String getProductName();

    BigDecimal getQuantitySold();

    BigDecimal getRevenue();

    BigDecimal getRevenueSharePercent();
}
