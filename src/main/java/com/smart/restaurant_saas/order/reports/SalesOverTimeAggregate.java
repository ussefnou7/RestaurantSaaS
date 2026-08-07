package com.smart.restaurant_saas.order.reports;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Projection for the daily sales-over-time query in {@code OrderRepository}: one row per calendar
 * day on which at least one COMPLETE order was placed.
 *
 * <p>The money components are kept separate all the way out to the API — see
 * {@code SalesOverTimeRow} for why tax must never be folded into a single "revenue" figure.
 */
public interface SalesOverTimeAggregate {

    LocalDate getSalesDate();

    Long getOrderCount();

    BigDecimal getSubtotal();

    BigDecimal getTaxAmount();

    BigDecimal getTotalAmount();

    BigDecimal getAverageOrderValue();
}
