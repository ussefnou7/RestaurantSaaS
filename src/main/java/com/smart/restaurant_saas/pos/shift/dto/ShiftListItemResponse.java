package com.smart.restaurant_saas.pos.shift.dto;

import com.smart.restaurant_saas.pos.shift.ShiftListProjection;
import com.smart.restaurant_saas.pos.shift.ShiftStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A row of the shifts list (D125).
 *
 * <p><b>The three variance fields are omitted server-side, not hidden by the client.</b> A caller
 * without {@code SHIFTS_VIEW_VARIANCE} receives nulls in {@code expectedCash}, {@code variance}
 * and {@code handoverVariance} — the numbers never reach the wire, so they cannot be read from a
 * network trace and a UI regression cannot expose them (D123).
 *
 * <p>{@code durationMinutes} is derived, not stored: it is a presentation of the two timestamps
 * and a stored copy would be a second truth that drifts.
 */
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
                p.getOpeningCount(),
                p.getClosingCount(),
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
