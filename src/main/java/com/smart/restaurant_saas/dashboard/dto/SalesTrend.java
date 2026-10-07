package com.smart.restaurant_saas.dashboard.dto;

import com.smart.restaurant_saas.dashboard.TrendBucket;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

/**
 * The sales series, with the grain it was computed at.
 *
 * <p>The grain travels with the data because the points alone cannot say it: "14" is an hour in a
 * one-day range and a day of the month in a longer one. The frontend needs it to label the axis,
 * and it is also the server's answer to a question the client never asked — the bucket is derived
 * from the range length rather than requested, so this field is where that decision becomes
 * visible.
 */
@Getter
@Builder
public class SalesTrend {

    private final TrendBucket bucket;

    @Singular
    private final List<TrendPoint> points;
}
