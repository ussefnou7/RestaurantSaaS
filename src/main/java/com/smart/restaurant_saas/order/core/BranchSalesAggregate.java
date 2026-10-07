package com.smart.restaurant_saas.order.core;

import java.math.BigDecimal;

/**
 * One branch's window totals, for the dashboard's branch breakdown.
 *
 * <p>Identical columns to {@link SalesTotalsAggregate} plus the branch, deliberately: the
 * breakdown has to sum back to the KPI row, and it can only be relied on to do that if both come
 * out of the same predicate with the same expressions. A separate shape would invite one of them
 * to gain a filter the other does not have.
 *
 * <p>Carries no score and no rank. See {@code OrderRepository.aggregateSalesByBranch} for why a
 * composite branch score was dropped rather than postponed.
 */
public interface BranchSalesAggregate {

    Long getBranchId();

    Long getOrderCount();

    BigDecimal getNetSales();

    BigDecimal getTaxAmount();

    BigDecimal getTotalAmount();

    BigDecimal getAverageOrderValue();
}
