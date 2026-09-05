package com.smart.restaurant_saas.pos.shift;

import com.smart.restaurant_saas.common.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ShiftErrorCode implements ErrorCode {

    SHIFT_NOT_FOUND(HttpStatus.NOT_FOUND),

    SHIFT_ALREADY_CLOSED(HttpStatus.CONFLICT),

    /**
     * The drawer has an open shift belonging to someone else. Distinct from a resume, which is not
     * an error at all: the client's next move is a force close, which is a separate call under a
     * separate permission (D122).
     */
    SHIFT_OPEN_BY_ANOTHER_USER(HttpStatus.CONFLICT),

    /**
     * Raised on order creation, never on shift open. A shift is selected or the request fails --
     * order creation must never create one implicitly.
     */
    NO_OPEN_SHIFT_FOR_DEVICE(HttpStatus.CONFLICT),

    /** Closing a colleague's shift without {@code SHIFTS_FORCE_CLOSE} (D122). */
    SHIFT_FORCE_CLOSE_NOT_PERMITTED(HttpStatus.FORBIDDEN),

    /** Closing one's own shift without {@code SHIFTS_CLOSE}. */
    SHIFT_CLOSE_NOT_PERMITTED(HttpStatus.FORBIDDEN),

    DEVICE_NOT_FOUND(HttpStatus.NOT_FOUND);

    private final HttpStatus defaultStatus;

    @Override
    public String getCode() {
        return name();
    }
}
