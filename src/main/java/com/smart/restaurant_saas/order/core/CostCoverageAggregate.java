package com.smart.restaurant_saas.order.core;

import java.math.BigDecimal;

/**
 * How much of a window's sales has had its cost posted to the ledger.
 *
 * <p>The three sales figures partition {@link #getTotalSales()} exactly, and the partition is the
 * point. Coverage is {@code postedSales / totalSales}, but a single ratio cannot say <em>why</em>
 * it is below 100%, and the two reasons call for different actions:
 *
 * <ul>
 *   <li>{@link #getUnpostedSales()} — a consumption document exists and is stuck. Someone clears
 *       it on the order-consumption screen, and alert A1 is already pointing at it.</li>
 *   <li>{@link #getNoConsumptionSales()} — no document was ever created, because the product has
 *       no active recipe (D14). Nothing is stuck; a recipe is missing, and no amount of clearing
 *       documents will fix it.</li>
 * </ul>
 *
 * <p>Collapsing the two would make the screen say "4% of sales have unposted cost" in both cases
 * and send the owner to the wrong screen in one of them.
 */
public interface CostCoverageAggregate {

    /** COMPLETE orders in the window. */
    Long getOrderCount();

    /** {@code SUM(subtotal)} over those orders — the denominator. */
    BigDecimal getTotalSales();

    /** Sales whose every consumption document is POSTED. The numerator. */
    BigDecimal getPostedSales();

    /** Sales held up by a document that is not POSTED. */
    BigDecimal getUnpostedSales();

    /** Sales with no consumption document at all — a missing recipe, not a stuck document. */
    BigDecimal getNoConsumptionSales();
}
