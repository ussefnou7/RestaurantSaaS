package com.smart.restaurant_saas.dashboard.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * The KPI row.
 *
 * <p><b>Food cost %, COGS and gross profit are absent, and the reason matters for when they come
 * back.</b> They are one number seen three ways, so dropping one drops all three, and the blocker
 * is data quality rather than modelling: recipes entered at plated weight under-cost every sale
 * until yield fields exist, cooked-and-cancelled food never leaves stock, and open PARTIAL or
 * CONFLICT documents leave sales with no cost at all. Recipe versioning is <em>not</em> the
 * problem — recipes are immutable and versioned, and each order line freezes its recipe at sale
 * time, so posted consumption is already computed against the version that was live. Publishing a
 * margin built on the three defects above would be publishing a number that is wrong in a
 * direction nobody can estimate.
 *
 * <p>What replaces them is {@link #getCostCoverage()} — not what the cost <em>is</em>, but whether
 * it has been posted at all. That question is answerable today, and it is the precondition for the
 * other three ever being meaningful.
 */
@Getter
@Builder
public class DashboardKpis {

    /** COMPLETE orders in the range. */
    private final Long orderCount;

    /** {@code SUM(subtotal)} — pre-tax. Tax is collected for the state and is not revenue. */
    private final String netSales;

    /** Carried separately, never folded into {@link #getNetSales()}. */
    private final String taxAmount;

    /** {@code SUM(total_amount)} — the stored column that reconciles against the sales reports. */
    private final String totalAmount;

    /** {@code totalAmount / orderCount}; null when the range has no orders. */
    private final String averageOrderValue;

    /**
     * Net write-off value for the range, as a magnitude.
     *
     * <p>Kept as a KPI when the waste <em>spike</em> alert was dropped: a spike is a comparison and
     * comparisons are out, but the level is a fact about the range and belongs on the row.
     */
    private final String wasteValue;

    /**
     * Stock value right now — {@code quantity × averageCost} across the caller's warehouses.
     *
     * <p><b>A stock value is a snapshot, not a range figure, and it ignores the selected dates.</b>
     * It is the one number on this row that does not change when the owner switches between today,
     * this month and this year, which the frontend has to label or it reads as a range total.
     */
    private final String stockValue;

    /** Share of the range's sales whose cost has been posted. Null when there are no sales. */
    private final String costCoverage;
}
