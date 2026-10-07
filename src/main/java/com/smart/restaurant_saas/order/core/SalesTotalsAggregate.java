package com.smart.restaurant_saas.order.core;

import java.math.BigDecimal;

/**
 * Window totals for the dashboard KPI row — the aggregate twin of {@code SalesOverTimeAggregate}.
 *
 * <p>Money stays in components, as it does in every sales report: tax is collected on behalf of
 * the state and is not revenue, so a single blended "revenue" figure would be ambiguous by exactly
 * the amount it hides.
 */
public interface SalesTotalsAggregate {

    Long getOrderCount();

    /** {@code SUM(subtotal)} — pre-tax revenue. The dashboard's "net sales". */
    BigDecimal getNetSales();

    /** Not revenue. Carried so the KPI row can show it separately rather than fold it in. */
    BigDecimal getTaxAmount();

    /** {@code SUM(total_amount)} — the stored column, and the reconciliation figure. */
    BigDecimal getTotalAmount();

    /**
     * {@code totalAmount / orderCount}, null when the window has no orders.
     *
     * <p>Divides the stored total rather than {@code netSales}, so that the dashboard's average
     * check is the same number the sales report shows and not a tax-rate away from it.
     */
    BigDecimal getAverageOrderValue();
}
