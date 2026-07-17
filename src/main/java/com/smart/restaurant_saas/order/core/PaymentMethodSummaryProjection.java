package com.smart.restaurant_saas.order.core;

import java.math.BigDecimal;

/**
 * Native-query projection used by {@link OrderRepository#aggregateByShift} to compute
 * per-payment-method order totals for the shift X/Z report.
 */
public interface PaymentMethodSummaryProjection {
    String getPaymentMethod();
    BigDecimal getTotal();
    Long getOrderCount();
}
