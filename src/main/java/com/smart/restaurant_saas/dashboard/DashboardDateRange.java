package com.smart.restaurant_saas.dashboard;

import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ErrorParams;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * A validated, half-open {@code [fromInclusive, toExclusive)} window for the dashboard, together
 * with the trend grain it implies.
 *
 * <p>Deliberately the same shape and the same rules as the reports' own range types — half-open so
 * that nothing stamped late on the final day is lost, bounds computed in the tenant's zone (D101)
 * so a day boundary falls where the tenant's day actually starts, and an inverted range rejected
 * rather than returned empty. A separate type rather than a shared one because the reports' are
 * package-private records in their own report packages, and widening one of them to serve a third
 * module would couple the dashboard's validation to a report's.
 *
 * <p><b>No comparison period is computed, and none is available.</b> The V1 dashboard shows
 * performance for the selected range only — no previous-period delta, no same-weekday baseline, no
 * trend against an average. This type having no "previous window" accessor is the structural form
 * of that decision: a later caller cannot quietly reintroduce a comparison by reaching for one.
 */
public record DashboardDateRange(
    LocalDate from,
    LocalDate to,
    LocalDateTime fromInclusive,
    LocalDateTime toExclusive,
    TrendBucket bucket
) {

    public static DashboardDateRange of(LocalDate from, LocalDate to, ZoneId zone) {
        if (from == null || to == null) {
            throw new BusinessException(DashboardErrorCode.INVALID_DATE_RANGE,
                "Dashboard date range is required",
                ErrorParams.of("from", from, "to", to));
        }
        if (from.isAfter(to)) {
            throw new BusinessException(DashboardErrorCode.INVALID_DATE_RANGE,
                "Dashboard range from must not be after to",
                ErrorParams.of("from", from, "to", to));
        }

        long days = ChronoUnit.DAYS.between(from, to) + 1;
        return new DashboardDateRange(
            from,
            to,
            from.atStartOfDay(zone).toLocalDateTime(),
            to.plusDays(1).atStartOfDay(zone).toLocalDateTime(),
            TrendBucket.forRangeOf(days));
    }
}
