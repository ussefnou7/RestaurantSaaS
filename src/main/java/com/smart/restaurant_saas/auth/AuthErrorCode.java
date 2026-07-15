package com.smart.restaurant_saas.auth;

import com.smart.restaurant_saas.common.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Error codes for the auth module.
 */
@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(HttpStatus.FORBIDDEN),
    POS_LOGIN_NOT_PERMITTED(HttpStatus.FORBIDDEN),
    DEVICE_NOT_FOUND(HttpStatus.NOT_FOUND),
    DEVICE_BRANCH_MISMATCH(HttpStatus.FORBIDDEN);

    private final HttpStatus defaultStatus;

    @Override
    public String getCode() {
        return name();
    }
}
