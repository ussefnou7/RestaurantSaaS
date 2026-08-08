package com.smart.restaurant_saas.order.reports;

import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.order.OrderErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * A validated, half-open {@code [fromInclusive, toExclusive)} order-date window for the sales
 * reports.
 *
 * <p><b>Half-open, because {@code order_date} is a timestamp.</b> {@code dateTo} is a calendar day
 * the user picked, so the window runs to the start of the following day. A closed bound of
 * {@code dateTo} would silently drop every order placed after midnight on the final day — which is
 * most of that day's trade.
 *
 * <p><b>An inverted or incomplete range is rejected, not quietly emptied.</b> A sales report that
 * returns nothing reads as "we sold nothing", which is a materially wrong conclusion to hand
 * someone.
 *
 * <p>Deliberately a near-duplicate of {@code inventory.reports.ReportDateRange} rather than a shared
 * type. That one is package-private in the inventory module and throws {@code InventoryErrorCode};
 * making the orders module depend on inventory internals to validate two dates would be a far worse
 * coupling than repeating six lines, and the error code genuinely differs per module.
 *
 * <p><b>The bounds are computed in the tenant's zone (D101), not the server's.</b> Half-open and
 * inclusive/exclusive behaviour are untouched — a day boundary simply now falls where the tenant's
 * day actually starts. Without this, a single-day report for a Riyadh tenant on a Cairo-clocked
 * server silently included the last hour of the previous Riyadh day and dropped the last hour of
 * the report day.
 */
record SalesReportDateRange(LocalDateTime fromInclusive, LocalDateTime toExclusive) {

    static SalesReportDateRange of(LocalDate dateFrom, LocalDate dateTo, ZoneId zone) {
        if (dateFrom == null || dateTo == null) {
            throw new BusinessException(OrderErrorCode.REPORT_DATE_RANGE_INVALID,
                "Sales report date range is required",
                ErrorParams.of("dateFrom", dateFrom, "dateTo", dateTo));
        }
        if (dateFrom.isAfter(dateTo)) {
            throw new BusinessException(OrderErrorCode.REPORT_DATE_RANGE_INVALID,
                "Sales report dateFrom must not be after dateTo",
                ErrorParams.of("dateFrom", dateFrom, "dateTo", dateTo));
        }
        return new SalesReportDateRange(
            dateFrom.atStartOfDay(zone).toLocalDateTime(),
            dateTo.plusDays(1).atStartOfDay(zone).toLocalDateTime());
    }
}
