package com.smart.restaurant_saas.order.reports.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * One payment method in the sales-by-payment-method report — the reconciliation view for delivery
 * platform statements and card processor fees.
 *
 * <p><b>Calculation:</b> the same COMPLETE orders as the sales-over-time report over the same
 * filters, grouped by {@code paymentMethod} instead of by date. {@code orderCount} counts orders;
 * the three money columns sum the stored order columns; {@code totalSharePercent} is this method's
 * share of {@code totalAmount} within the filtered scope, so the column adds to 100.
 *
 * <p><b>This report's {@code totalAmount} sum equals sales-over-time's, exactly.</b> Same orders,
 * same status rule, same window — only the grouping differs. That equality is pinned by a test
 * rather than left to inspection, because a predicate drifting between the two queries is the defect
 * class most likely to survive review: each query looks correct on its own.
 *
 * <p>Tax is shown separately for the same reason as everywhere else here: it is not revenue. Note
 * that {@code subtotal + taxAmount} can differ from {@code totalAmount} by rounding — see
 * {@link SalesOverTimeRow}.
 *
 * <p><b>A missing method reports as {@code UNSPECIFIED} and is never dropped.</b> The column is NOT
 * NULL with a CHECK constraint today, so this should be unreachable; it stays because a dropped row
 * would silently break the reconciliation above.
 *
 * <p>Sorted by {@code totalAmount} descending.
 */
@Getter
@Builder
public class SalesByPaymentMethodRow {

    /** A {@code PaymentMethod} name, or {@code UNSPECIFIED}. Never null. */
    private final String paymentMethod;

    private final Long orderCount;

    /** Sum of order subtotals — pre-tax. */
    private final String subtotal;

    /** Sum of order tax. Not revenue. */
    private final String taxAmount;

    /** Sum of stored order totals. Reconciles exactly with the sales-over-time report. */
    private final String totalAmount;

    /** Share of {@code totalAmount} within the filtered scope. Adds to 100. */
    private final String totalSharePercent;
}
