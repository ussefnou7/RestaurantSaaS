package com.smart.restaurant_saas.pos.shift.dto;

/**
 * The drawer's current state, asked before the POS renders its keypad (D120).
 *
 * <p><b>An empty drawer is an ordinary result, not an error.</b> {@code shift} is null when the
 * device has no open shift -- the first login of the day. The previous implementation threw
 * {@code NO_OPEN_SHIFT_FOR_CASHIER} here, which forced the client's normal path through an
 * exception branch and meant any future error would be misread as "no shift" and send the cashier
 * into an opening count on top of a live shift.
 *
 * <p>The client's three-way branch reads this body and never an error code:
 * <ul>
 *   <li>{@code shift == null} -- open screen</li>
 *   <li>{@code shift.openedByUserId} is the caller -- resume, no count</li>
 *   <li>otherwise -- force-close screen</li>
 * </ul>
 */
public record CurrentShiftResponse(ShiftResponse shift) {

    public static CurrentShiftResponse empty() {
        return new CurrentShiftResponse(null);
    }

    public static CurrentShiftResponse of(ShiftResponse shift) {
        return new CurrentShiftResponse(shift);
    }
}
