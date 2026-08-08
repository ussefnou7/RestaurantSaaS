package com.smart.restaurant_saas.inventory.reports;

import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.inventory.core.InventoryErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * A validated, half-open {@code [fromInclusive, toExclusive)} movement-date window for the
 * date-ranged inventory reports.
 *
 * <p><b>Half-open, following {@code OrderConsumptionService.list}:</b> {@code dateTo} is a calendar
 * day the user picked, so the window runs to the start of the following day. A closed upper bound
 * of {@code dateTo.atEndOfDay()} would drop movements stamped later in the final day — physical
 * counts stamp {@code line.countedAt}, a real clock time, so that loss would be silent and real.
 *
 * <p><b>An inverted range is rejected, unlike that list endpoint.</b> There the range is optional
 * and an inverted one returning empty is merely useless. Here the range is required and the answer
 * is a loss figure: "no rows" reads as "no shrinkage this period", which is a materially wrong
 * conclusion to hand someone silently. It fails as {@code VALIDATION_FAILED} instead.
 *
 * <p>Shared by both date-ranged reports rather than duplicated into each service (D13 — two real
 * callers): the two must agree on what an inverted range does, and that agreement is the point.
 *
 * <p><b>The bounds are computed in the tenant's zone (D101), not the server's.</b> Half-open and
 * inclusive/exclusive behaviour are untouched — a day boundary simply now falls where the tenant's
 * day actually starts. Without this, a single-day report for a Riyadh tenant on a Cairo-clocked
 * server silently included the last hour of the previous Riyadh day and dropped the last hour of
 * the report day.
 */
record ReportDateRange(LocalDateTime fromInclusive, LocalDateTime toExclusive) {

    static ReportDateRange of(LocalDate dateFrom, LocalDate dateTo, ZoneId zone) {
        if (dateFrom == null || dateTo == null) {
            throw new BusinessException(InventoryErrorCode.VALIDATION_FAILED,
                "Report date range is required",
                ErrorParams.of("dateFrom", dateFrom, "dateTo", dateTo));
        }
        if (dateFrom.isAfter(dateTo)) {
            throw new BusinessException(InventoryErrorCode.VALIDATION_FAILED,
                "Report dateFrom must not be after dateTo",
                ErrorParams.of("dateFrom", dateFrom, "dateTo", dateTo));
        }
        return new ReportDateRange(
            dateFrom.atStartOfDay(zone).toLocalDateTime(),
            dateTo.plusDays(1).atStartOfDay(zone).toLocalDateTime());
    }
}
