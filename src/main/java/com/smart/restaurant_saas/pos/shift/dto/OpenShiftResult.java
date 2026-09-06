package com.smart.restaurant_saas.pos.shift.dto;

/**
 * Whether the open call created a shift or resumed one, so the controller can answer 201 or 200
 * honestly.
 *
 * <p>A resume writes nothing (D120). Returning 201 for it would claim a row was created when none
 * was, and would leave the client unable to tell the two apart except by comparing the count it
 * sent against the count it got back — which is exactly the kind of inference that goes wrong
 * quietly when the two happen to be equal.
 */
public record OpenShiftResult(ShiftResponse shift, boolean created) {

    public static OpenShiftResult created(ShiftResponse shift) {
        return new OpenShiftResult(shift, true);
    }

    public static OpenShiftResult resumed(ShiftResponse shift) {
        return new OpenShiftResult(shift, false);
    }
}
