package com.smart.restaurant_saas.loyalty;

import com.smart.restaurant_saas.common.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Error codes for the loyalty module only. Never reuse another module's code enum. Kept minimal
 * for the V1 scope — add a code only when a real throw site needs it.
 */
@Getter
@RequiredArgsConstructor
public enum LoyaltyErrorCode implements ErrorCode {

    CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND),
    CUSTOMER_PHONE_REQUIRED(HttpStatus.BAD_REQUEST);

    private final HttpStatus defaultStatus;

    @Override
    public String getCode() {
        return name();
    }
}
