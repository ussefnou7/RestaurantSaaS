package com.smart.restaurant_saas.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.restaurant_saas.common.BusinessException;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Range validation and the half-open window.
 *
 * <p>The rejection cases matter more than the happy one. A missing or inverted range returning an
 * empty result would put a page of zeros in front of an owner, and a page of zeros reads as a
 * quiet month rather than as a bad request — which is the specific failure the dashboard's
 * never-a-silent-gap rule exists to prevent.
 */
class DashboardDateRangeTest {

    private static final ZoneId CAIRO = ZoneId.of("Africa/Cairo");

    @Test
    @DisplayName("the window is half-open: it runs to the start of the day after dateTo")
    void windowIsHalfOpen() {
        DashboardDateRange range = DashboardDateRange.of(
            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), CAIRO);

        assertThat(range.fromInclusive()).isEqualTo(LocalDate.of(2026, 10, 1).atStartOfDay());
        // Not 23:59:59 on the 31st: a closed upper bound would drop anything stamped later in
        // the final day, and several sources stamp a real clock time.
        assertThat(range.toExclusive()).isEqualTo(LocalDate.of(2026, 11, 1).atStartOfDay());
    }

    @Test
    @DisplayName("a single day is a one-day window, not an empty one")
    void singleDayIsInclusiveOfBothEnds() {
        LocalDate day = LocalDate.of(2026, 10, 4);
        DashboardDateRange range = DashboardDateRange.of(day, day, CAIRO);

        assertThat(range.fromInclusive()).isEqualTo(day.atStartOfDay());
        assertThat(range.toExclusive()).isEqualTo(day.plusDays(1).atStartOfDay());
        assertThat(range.bucket()).isEqualTo(TrendBucket.HOUR);
    }

    @Test
    @DisplayName("the bucket comes from the range length")
    void bucketFollowsRangeLength() {
        assertThat(DashboardDateRange.of(
            LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31), CAIRO).bucket())
            .isEqualTo(TrendBucket.DAY);

        assertThat(DashboardDateRange.of(
            LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), CAIRO).bucket())
            .isEqualTo(TrendBucket.MONTH);
    }

    @Test
    @DisplayName("an inverted range is rejected rather than returning nothing")
    void invertedRangeIsRejected() {
        assertThatThrownBy(() -> DashboardDateRange.of(
                LocalDate.of(2026, 10, 31), LocalDate.of(2026, 10, 1), CAIRO))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", DashboardErrorCode.INVALID_DATE_RANGE);
    }

    @Test
    @DisplayName("a missing bound is rejected on either side")
    void missingBoundIsRejected() {
        assertThatThrownBy(() -> DashboardDateRange.of(null, LocalDate.of(2026, 10, 1), CAIRO))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", DashboardErrorCode.INVALID_DATE_RANGE);

        assertThatThrownBy(() -> DashboardDateRange.of(LocalDate.of(2026, 10, 1), null, CAIRO))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", DashboardErrorCode.INVALID_DATE_RANGE);
    }
}
