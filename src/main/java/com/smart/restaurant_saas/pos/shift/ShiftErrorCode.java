package com.smart.restaurant_saas.pos.shift;

import com.smart.restaurant_saas.common.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ShiftErrorCode implements ErrorCode {

    SHIFT_NOT_FOUND(HttpStatus.NOT_FOUND),
    SHIFT_ALREADY_OPEN(HttpStatus.CONFLICT),
    SHIFT_ALREADY_CLOSED(HttpStatus.CONFLICT),
    NO_OPEN_SHIFT_FOR_CASHIER(HttpStatus.CONFLICT);

    private final HttpStatus defaultStatus;

    @Override
    public String getCode() {
        return name();
    }
}
