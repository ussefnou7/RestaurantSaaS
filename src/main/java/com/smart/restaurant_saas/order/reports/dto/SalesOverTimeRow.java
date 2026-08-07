package com.smart.restaurant_saas.order.reports.dto;

import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;

/**
 * One day of the sales-over-time report.
 *
 * <p><b>Calculation:</b> COMPLETE orders whose {@code orderDate} falls on this calendar day, within
 * the requested filters. {@code orderCount} counts orders (not lines);
 * {@code subtotal}/{@code taxAmount}/{@code totalAmount} sum the stored order columns;
 * {@code averageOrderValue = totalAmount / orderCount}.
 *
 * <p><b>Tax is shown separately because it is not revenue.</b> It is collected on behalf of the
 * state. Folding it into one "revenue" number inflates the figure and guarantees the P&amp;L has to
 * unpick it later, so this report never blends the three.
 *
 * <p><b>{@code subtotal + taxAmount} will not always equal {@code totalAmount} exactly.</b> The
 * order write path stores the two components at scale 6 and rounds their sum once to scale 2 for
 * {@code totalAmount}, so each order can carry a rounding difference of a few thousandths, which
 * accumulates across a day. {@code totalAmount} is the authoritative figure and is what reconciles
 * against the payment-method report; it is not a re-derived sum, and no report re-derives it.
 *
 * <p><b>Sorted chronologically ascending — the one report here not sorted by magnitude.</b> It is a
 * time series: the shape over time is the finding, and reordering it by value destroys exactly what
 * the report exists to show.
 *
 * <p><b>Days with no sales are omitted, not returned as zero rows.</b> Zero-filling would require
 * generating a date series server-side, and the honest reading of an absent day is "no COMPLETE
 * orders" rather than "zero revenue recorded". A chart consumer that needs a continuous axis can
 * fill the gaps from the requested range, which it already knows. Noted as a v1 decision, not a
 * permanent one.
 */
@Getter
@Builder
public class SalesOverTimeRow {

    private final LocalDate salesDate;

    /** Number of COMPLETE orders on this day. Orders, not lines. */
    private final Long orderCount;

    /** Sum of order subtotals — pre-tax revenue. */
    private final String subtotal;

    /** Sum of order tax. Not revenue; collected on behalf of the state. */
    private final String taxAmount;

    /** Sum of stored order totals. The reconciliation figure — see the class javadoc. */
    private final String totalAmount;

    /** {@code totalAmount / orderCount}. */
    private final String averageOrderValue;
}
