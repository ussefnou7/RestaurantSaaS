package com.smart.restaurant_saas.expense.dto;

import com.smart.restaurant_saas.pos.shift.SelectableShiftProjection;
import com.smart.restaurant_saas.pos.shift.ShiftStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One option in the manager's shift picker (D124).
 *
 * <p>Enough to tell one drawer's shift from another's, and no money whatsoever — no opening count,
 * no expected figure, no variance. This list is reachable by anyone who can record an expense,
 * which is a wider audience than the one allowed to see variances (D123).
 *
 * <p>{@code closed} is stated rather than left to be inferred from {@code status}, because the
 * client has to render the consequence and not just the state: a closed shift can be linked, and
 * doing so will not change its recorded variance.
 */
public record SelectableShiftResponse(
        Long id,
        LocalDate businessDate,
        Long deviceId,
        String deviceName,
        Long cashierUserId,
        String cashierName,
        LocalDateTime openedAt,
        LocalDateTime closedAt,
        ShiftStatus status,
        boolean closed,
        Long branchDeviceCount
) {

    public static SelectableShiftResponse from(SelectableShiftProjection projection) {
        return new SelectableShiftResponse(
                projection.getId(),
                projection.getBusinessDate(),
                projection.getDeviceId(),
                projection.getDeviceName(),
                projection.getCashierUserId(),
                projection.getCashierName(),
                projection.getOpenedAt(),
                projection.getClosedAt(),
                projection.getStatus(),
                projection.getStatus() == ShiftStatus.CLOSED,
                projection.getBranchDeviceCount()
        );
    }
}
