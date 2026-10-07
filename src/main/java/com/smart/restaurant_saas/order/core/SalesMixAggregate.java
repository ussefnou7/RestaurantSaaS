package com.smart.restaurant_saas.order.core;

import java.math.BigDecimal;

/**
 * One slice of a breakdown — an order type, or a cancellation stage.
 *
 * <p>Shared by two queries that differ only in the dimension they group on and in which orders
 * they count. {@code dimension} is the raw enum name; the frontend owns the translation key for
 * each value, so no user-facing text crosses the wire (D12).
 */
public interface SalesMixAggregate {

    /** The raw enum name of the dimension value — {@code DELIVERY}, {@code AFTER_DONE}, … */
    String getDimension();

    Long getOrderCount();

    BigDecimal getNetSales();

    BigDecimal getTotalAmount();

    /**
     * This slice's share of the window's total, or null where a share is meaningless.
     *
     * <p>Null for cancellations: a share of cancellations only means something against the sales
     * they would have been, and dividing them by their own total would produce percentages that
     * add to 100 and say nothing. Returned as null rather than omitted so one projection can serve
     * both queries without the consumer guessing which it is holding.
     */
    BigDecimal getSharePercent();
}
