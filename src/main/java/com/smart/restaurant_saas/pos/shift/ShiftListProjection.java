package com.smart.restaurant_saas.pos.shift;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A row of the shifts list (D125). An operational list, not a report (D83).
 *
 * <p>Both names are resolved by join on read, never stored on the shift (D124).
 *
 * <p>The three money columns are projected for every caller and then dropped by the service for
 * callers without {@code SHIFTS_VIEW_VARIANCE} — see {@code ShiftListItemResponse.from}. The
 * filtering is one step, in one place, rather than two variants of this query that could drift
 * apart.
 */
public interface ShiftListProjection {

    Long getId();
    LocalDate getBusinessDate();
    Long getDeviceId();
    String getDeviceName();
    Long getBranchId();
    String getBranchName();
    Long getOpenedByUserId();
    String getOpenedByUserName();
    Long getClosedByUserId();
    String getClosedByUserName();
    LocalDateTime getOpenedAt();
    LocalDateTime getClosedAt();
    ShiftStatus getStatus();
    Boolean getForcedClose();
    BigDecimal getOpeningCount();
    BigDecimal getClosingCount();

    /** Permission-gated (D123). */
    BigDecimal getExpectedCash();
    BigDecimal getVariance();
    BigDecimal getHandoverVariance();
    BigDecimal getExpensesAtClose();
}
