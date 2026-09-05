package com.smart.restaurant_saas.pos.shift.dto;

import com.smart.restaurant_saas.pos.shift.Shift;
import com.smart.restaurant_saas.pos.shift.ShiftStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * What a POS caller is allowed to see: identity, whose shift it is, and whether it is closed.
 *
 * <p><b>Five fields are absent by construction, not filtered on the way out</b> (D123):
 * {@code expectedCash}, {@code variance}, {@code handoverVariance}, {@code expensesAtClose}, and
 * any order or payment-method total. They are not members of this record, so no later edit to a
 * mapper can start populating them and no caller can read them from a network trace. The figures
 * are stored on the row and surface only through the permission-gated read endpoints (A7).
 *
 * <p>{@code openingCount} is shown only to the cashier who opened the shift. A cashier about to
 * force-close a colleague's drawer counts it blind (D122), and the opening float is the largest
 * single component of the figure they would otherwise be aiming at. {@code closingCount} is not
 * withheld: on an open shift it is null anyway, and on a close it is the caller's own input
 * echoed back.
 */
public record ShiftResponse(
        Long id,
        Long deviceId,
        String deviceName,
        Long branchId,
        String branchName,
        LocalDate businessDate,
        ShiftStatus status,
        Long openedByUserId,
        String openedByUserName,
        LocalDateTime openedAt,
        Long closedByUserId,
        LocalDateTime closedAt,
        Boolean forcedClose,
        BigDecimal openingCount,
        BigDecimal closingCount
) {

    /**
     * @param callerIsOpener whether the authenticated caller is {@code shift.openedByUserId};
     *                       gates {@code openingCount} only.
     */
    public static ShiftResponse of(Shift shift, String openedByUserName, boolean callerIsOpener) {
        return new ShiftResponse(
                shift.getId(),
                shift.getDevice().getId(),
                shift.getDevice().getName(),
                shift.getDevice().getBranch().getId(),
                shift.getDevice().getBranch().getName(),
                shift.getBusinessDate(),
                shift.getStatus(),
                shift.getOpenedByUserId(),
                openedByUserName,
                shift.getOpenedAt(),
                shift.getClosedByUserId(),
                shift.getClosedAt(),
                shift.getForcedClose(),
                callerIsOpener ? shift.getOpeningCount() : null,
                shift.getClosingCount()
        );
    }
}
