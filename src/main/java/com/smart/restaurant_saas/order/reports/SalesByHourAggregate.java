package com.smart.restaurant_saas.order.reports;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Projection for the hourly sales-over-time query in {@code OrderRepository}: one row per
 * (calendar day, hour of day) that saw at least one COMPLETE order.
 *
 * <p>Hours are <b>calendar</b> hours, not business-day hours: an order at 02:00 belongs to that
 * calendar date, not to the previous night's trading session. See {@code SalesByHourRow}.
 */
public interface SalesByHourAggregate {

    LocalDate getSalesDate();

    /** 0–23, calendar hour of {@link #getSalesDate()}. */
    Integer getHourOfDay();

    Long getOrderCount();

    BigDecimal getSubtotal();

    BigDecimal getTaxAmount();

    BigDecimal getTotalAmount();

    BigDecimal getAverageOrderValue();
}
