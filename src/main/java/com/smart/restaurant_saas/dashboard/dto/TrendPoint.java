package com.smart.restaurant_saas.dashboard.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * One point of the sales trend, at whichever grain the range implied.
 *
 * <p>{@code label} is the bucket's key in a form the frontend can place on an axis without
 * reassembling it: an ISO date for day and month buckets, and an hour of day (0–23) for hour
 * buckets. One field rather than a union of three typed ones, because the chart treats them
 * identically and the grain is already stated once on the block.
 *
 * <p><b>Empty buckets are omitted, not zero-filled.</b> The frontend knows the requested range and
 * can fill gaps for a continuous axis; the honest reading of an absent bucket is "no COMPLETE
 * orders", which is not the same claim as "zero revenue recorded".
 */
@Getter
@Builder
public class TrendPoint {

    /** ISO date for DAY and MONTH buckets; the hour as a plain integer string for HOUR. */
    private final String label;

    private final Long orderCount;

    private final String netSales;

    private final String totalAmount;
}
