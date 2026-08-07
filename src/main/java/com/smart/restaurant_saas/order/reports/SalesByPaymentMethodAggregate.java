package com.smart.restaurant_saas.order.reports;

import java.math.BigDecimal;

/**
 * Projection for the sales-by-payment-method query in {@code OrderRepository}: one row per payment
 * method used by a COMPLETE order in the window.
 *
 * <p>{@code paymentMethod} is COALESCEd to {@code UNSPECIFIED} by the query. The column is
 * {@code NOT NULL} with a CHECK constraint (V13), so that bucket is currently unreachable — it stays
 * because a dropped row would make this report's total silently disagree with sales-over-time, and
 * those two must reconcile exactly.
 */
public interface SalesByPaymentMethodAggregate {

    String getPaymentMethod();

    Long getOrderCount();

    BigDecimal getSubtotal();

    BigDecimal getTaxAmount();

    BigDecimal getTotalAmount();

    BigDecimal getTotalSharePercent();
}
