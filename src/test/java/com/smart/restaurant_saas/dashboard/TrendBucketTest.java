package com.smart.restaurant_saas.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The bucket derivation, pinned at its boundaries.
 *
 * <p>Worth its own test because the rule is invisible from either side of the wire: the client
 * never asks for a grain and the server never explains why it chose one. An off-by-one at a
 * boundary produces a chart that is merely a bit odd — a two-month range collapsing to two bars —
 * which is exactly the class of bug that ships and survives.
 */
class TrendBucketTest {

    @Test
    @DisplayName("a single day is bucketed by hour")
    void singleDayUsesHours() {
        assertThat(TrendBucket.forRangeOf(1)).isEqualTo(TrendBucket.HOUR);
    }

    @Test
    @DisplayName("two days is already too many for hourly buckets")
    void twoDaysUsesDays() {
        assertThat(TrendBucket.forRangeOf(2)).isEqualTo(TrendBucket.DAY);
    }

    @Test
    @DisplayName("a month stays daily")
    void monthUsesDays() {
        assertThat(TrendBucket.forRangeOf(31)).isEqualTo(TrendBucket.DAY);
    }

    @Test
    @DisplayName("two months is the last range that stays daily")
    void sixtyTwoDaysIsTheDayCeiling() {
        assertThat(TrendBucket.forRangeOf(62)).isEqualTo(TrendBucket.DAY);
        assertThat(TrendBucket.forRangeOf(63)).isEqualTo(TrendBucket.MONTH);
    }

    @Test
    @DisplayName("a year is bucketed by month")
    void yearUsesMonths() {
        assertThat(TrendBucket.forRangeOf(365)).isEqualTo(TrendBucket.MONTH);
    }
}
