package com.smart.restaurant_saas.order.core;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One bucket of the dashboard's sales trend.
 *
 * <p>{@code bucketStart} is a date rather than a label, so the frontend places it on an axis
 * instead of parsing it. For a monthly series it is the first of the month; the day and hour
 * series reuse the existing report projections, which already carry their own keys.
 *
 * <p>Empty buckets are omitted, like every other series here. The frontend knows the requested
 * range and fills the gaps — and an absent bucket honestly means "no COMPLETE orders", which is
 * not the same claim as "zero revenue recorded".
 */
public interface SalesBucketAggregate {

    LocalDate getBucketStart();

    Long getOrderCount();

    BigDecimal getNetSales();

    BigDecimal getTotalAmount();
}
