package com.smart.restaurant_saas.inventory.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.restaurant_saas.common.BusinessException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/**
 * D101 §4: report bounds are computed in the tenant's zone.
 *
 * <p><b>Read this before trusting the D101 verification plan's item 2.</b> That item expects a
 * movement in the last hour of the previous Riyadh day to drop out of a single-day report once the
 * bounds are zoned. It does not, and it never did — because
 * {@code LocalDate.atStartOfDay()} does <em>not</em> read the JVM's zone. It is
 * {@code LocalDateTime.of(date, MIDNIGHT)}, a pure date-to-datetime widening. So
 * {@code atStartOfDay()} and {@code atStartOfDay(anyZone).toLocalDateTime()} produce the same value
 * for every zone, and the bounds these reports use were never zone-dependent.
 *
 * <p>The zone parameter is still correct to carry — it states which day boundary is meant, and it
 * is what keeps these bounds right if the columns ever become {@code Instant} / {@code TIMESTAMPTZ}
 * (see O34). But it fixes no live defect, and a test asserting that it changed which rows come back
 * would be asserting something false. These tests pin what is actually true.
 */
class ReportDateRangeZoneTest {

    private static final ZoneId RIYADH = ZoneId.of("Asia/Riyadh");
    private static final ZoneId DUBAI = ZoneId.of("Asia/Dubai");
    private static final LocalDate DAY = LocalDate.of(2026, 3, 15);

    @Test
    void boundsAreHalfOpenAroundTheRequestedDay() {
        ReportDateRange range = ReportDateRange.of(DAY, DAY, RIYADH);

        assertThat(range.fromInclusive()).isEqualTo(LocalDateTime.of(2026, 3, 15, 0, 0));
        assertThat(range.toExclusive()).isEqualTo(LocalDateTime.of(2026, 3, 16, 0, 0));
    }

    /**
     * The honest statement of what zoning these bounds did and did not change: for wall-clock
     * columns the value is identical in every zone. If this ever stops holding, the storage model
     * changed and D101's assumptions need revisiting.
     */
    @Test
    void wallClockBoundsAreIdenticalInEveryZone() {
        ReportDateRange riyadh = ReportDateRange.of(DAY, DAY, RIYADH);
        ReportDateRange dubai = ReportDateRange.of(DAY, DAY, DUBAI);

        assertThat(riyadh.fromInclusive()).isEqualTo(dubai.fromInclusive());
        assertThat(riyadh.toExclusive()).isEqualTo(dubai.toExclusive());
        assertThat(riyadh.fromInclusive()).isEqualTo(DAY.atStartOfDay());
    }

    /** An inverted or incomplete range still fails loudly rather than returning nothing. */
    @Test
    void invertedRangeIsStillRejected() {
        assertThatThrownBy(() -> ReportDateRange.of(DAY, DAY.minusDays(1), RIYADH))
            .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> ReportDateRange.of(null, DAY, RIYADH))
            .isInstanceOf(BusinessException.class);
    }
}
