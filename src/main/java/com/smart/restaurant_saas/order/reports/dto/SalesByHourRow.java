package com.smart.restaurant_saas.order.reports.dto;

import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;

/**
 * One (day, hour) bucket of the hourly sales-over-time report — the staffing and shift-planning
 * view. A restaurant seeing Tuesday lunch at half of Friday lunch can cut labour against it.
 *
 * <p>Same calculation, money semantics and chronological sort as {@link SalesOverTimeRow}; see that
 * class for why tax is never folded in and why {@code subtotal + taxAmount} can differ from
 * {@code totalAmount} by rounding.
 *
 * <p><b>Known limitation — calendar hours, not business days.</b> An order placed at 02:00 belongs
 * to that calendar date, so a venue trading past midnight will see one trading session split across
 * two dates. There is deliberately no business-day concept here; introducing one is a decision about
 * the whole reporting surface, not something to smuggle into an hourly bucket.
 *
 * <p>Hours with no sales are omitted, exactly as days are in the daily report.
 */
@Getter
@Builder
public class SalesByHourRow {

    private final LocalDate salesDate;

    /** 0–23, calendar hour of {@link #salesDate}. */
    private final Integer hourOfDay;

    private final Long orderCount;

    private final String subtotal;

    private final String taxAmount;

    private final String totalAmount;

    private final String averageOrderValue;
}
