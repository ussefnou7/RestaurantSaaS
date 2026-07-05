package com.smart.restaurant_saas.common;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Cross-module error codes owned by the common layer (not tied to any feature module).
 * These back the generic handlers in {@link GlobalExceptionHandler} and the temporary
 * backward-compatible {@link BusinessException} string constructor.
 */
@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

    /** Generic business-rule violation (409) — default for not-yet-migrated string throws. */
    BUSINESS_RULE_VIOLATION(HttpStatus.CONFLICT),
    DATA_INTEGRITY_VIOLATION(HttpStatus.CONFLICT),
    ACCESS_DENIED(HttpStatus.FORBIDDEN),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus defaultStatus;

    @Override
    public String getCode() {
        return name();
    }
}
