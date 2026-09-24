package com.smart.restaurant_saas.pos.shift.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.smart.restaurant_saas.pos.shift.ShiftListProjection;
import com.smart.restaurant_saas.pos.shift.ShiftStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A row of the shifts list (D125).
 *
 * <p><b>The three variance fields are omitted server-side, not hidden by the client.</b> For a
 * caller without {@code SHIFTS_VIEW_VARIANCE} they are nulled before the DTO is built and then
 * dropped from the JSON entirely by {@code NON_NULL} — so the keys are absent, not present-and-
 * null. The numbers never reach the wire, cannot be read from a network trace, and a UI regression
 * cannot expose them (D123).
 *
 * <p>{@code NON_NULL} also drops {@code closedAt}, {@code closedByUserName} and
 * {@code durationMinutes} on an open shift, which is the same absent-means-null contract the
 * client already handles.
 *
 * <p>{@code durationMinutes} is derived, not stored: it is a presentation of the two timestamps
 * and a stored copy would be a second truth that drifts.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ShiftListItemResponse(
        Long id,
        LocalDate businessDate,
        Long deviceId,
        String deviceName,
        Long branchId,
        String branchName,
        Long cashierUserId,
        String cashierName,
        Long closedByUserId,
        String closedByUserName,
        LocalDateTime openedAt,
        LocalDateTime closedAt,
        Long durationMinutes,
        ShiftStatus status,
        Boolean forcedClose,
        BigDecimal openingCount,
        BigDecimal closingCount,
        BigDecimal expectedCash,
        BigDecimal variance,
        BigDecimal handoverVariance
) {

    public static ShiftListItemResponse from(ShiftListProjection p, boolean canViewVariance) {
        return new ShiftListItemResponse(
                p.getId(),
                p.getBusinessDate(),
                p.getDeviceId(),
                p.getDeviceName(),
                p.getBranchId(),
                p.getBranchName(),
                p.getOpenedByUserId(),
                p.getOpenedByUserName(),
                p.getClosedByUserId(),
                p.getClosedByUserName(),
                p.getOpenedAt(),
                p.getClosedAt(),
                durationMinutes(p.getOpenedAt(), p.getClosedAt()),
                p.getStatus(),
                p.getForcedClose(),
                // The counts are gated with the variance, not published alongside the identity
                // fields. expectedCash = openingCount + cash orders - drawer expenses, so a caller
                // holding the opening float can recover the figure the count is meant to be blind
                // to (D123) — and a cashier about to force-close a colleague's drawer would be
                // aiming at it (D122). Withholding the three derived fields while publishing their
                // largest single input is not a gate. The POS never reads this DTO: it sees
                // ShiftResponse from /open, /current and /close, which carries its own count.
                canViewVariance ? p.getOpeningCount() : null,
                canViewVariance ? p.getClosingCount() : null,
                canViewVariance ? p.getExpectedCash() : null,
                canViewVariance ? p.getVariance() : null,
                canViewVariance ? p.getHandoverVariance() : null
        );
    }

    /** Null while the shift is still open — a running duration is the client's to render. */
    private static Long durationMinutes(LocalDateTime openedAt, LocalDateTime closedAt) {
        if (openedAt == null || closedAt == null) {
            return null;
        }
        return Duration.between(openedAt, closedAt).toMinutes();
    }
}
