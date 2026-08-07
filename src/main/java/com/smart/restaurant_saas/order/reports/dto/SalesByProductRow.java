package com.smart.restaurant_saas.order.reports.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * One product in the sales-by-product report — the menu-trimming and marketing view, and the
 * foundation the food-cost / menu-engineering report will build on.
 *
 * <p><b>Calculation:</b> order lines belonging to COMPLETE orders in the window and matching the
 * filters, grouped by product. {@code quantitySold} sums line quantity; {@code revenue} sums
 * {@code lineTotal}; {@code revenueSharePercent} is this product's share of the summed
 * {@code lineTotal} <b>within the filtered scope</b>, so the column adds to 100.
 *
 * <p><b>⚠ This report is PRE-TAX and cannot be otherwise.</b> Tax is stored on the order, not the
 * line, so attributing it across products would mean inventing an apportionment rule. {@code
 * lineTotal} is the honest figure.
 *
 * <p><b>Do not compare this report's revenue to sales-over-time's {@code totalAmount} — they will
 * differ, and the difference is exactly the tax.</b> The figure it should agree with is that
 * report's {@code subtotal}, which is the same sum of line totals viewed by day instead of by
 * product. This is stated here because someone will otherwise sum the column, find the mismatch,
 * and file a bug.
 *
 * <p><b>No product code and no Arabic name.</b> The {@code product} table has neither column — only
 * {@code name}. They are omitted rather than returned as nulls, because a nullable field for data
 * that does not exist is a promise the schema cannot keep.
 *
 * <p>The product name is the <em>current</em> one: renaming a product retro-labels its sales
 * history. Deleting a product that has ever sold is blocked by the foreign key, so rows never
 * vanish from this report.
 *
 * <p>Sorted by revenue descending.
 */
@Getter
@Builder
public class SalesByProductRow {

    private final Long productId;

    /** The product's current name. A rename retro-labels history — see the class javadoc. */
    private final String productName;

    /** Sum of line quantity. */
    private final String quantitySold;

    /** Sum of {@code lineTotal}. <b>Pre-tax.</b> */
    private final String revenue;

    /** Share of the summed {@code lineTotal} within the filtered scope. Adds to 100. */
    private final String revenueSharePercent;
}
