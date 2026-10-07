package com.smart.restaurant_saas.dashboard.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * How much of the range's sales has had its cost posted to the ledger.
 *
 * <p>The headline of the cost section, and the number that says whether the rest of it is worth
 * reading: below 100% means some sales carry revenue and no cost, so any margin over the range is
 * overstated. It is the period-level twin of the unposted-consumption alert.
 *
 * <p><b>The gap is split in two, and that split is the whole design.</b> A single "4% uncovered"
 * figure cannot say which of two unrelated problems it is, and the two send the owner to different
 * screens: {@link #getUnpostedSales()} is a document that exists and is stuck, which somebody
 * clears; {@link #getNoConsumptionSales()} is a product with no recipe, so no document was ever
 * created and there is nothing to clear. Reporting them together would be accurate and useless.
 */
@Getter
@Builder
public class CostCoverage {

    /** {@code postedSales / totalSales} as a percentage. Never present when total sales is zero. */
    private final String coveragePercent;

    private final String totalSales;

    /** Sales whose every consumption document is POSTED. */
    private final String postedSales;

    /** Sales held up by a document that is not POSTED — someone clears it. */
    private final String unpostedSales;

    /** Sales with no consumption document at all — a missing recipe, not a stuck document. */
    private final String noConsumptionSales;
}
