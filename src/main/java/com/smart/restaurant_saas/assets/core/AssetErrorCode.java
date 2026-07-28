package com.smart.restaurant_saas.assets.core;

import com.smart.restaurant_saas.common.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Error codes for the fixed-assets module only. Never reuse another module's code enum, and never
 * throw a raw {@code RuntimeException} or a base exception from assets feature code.
 */
@Getter
@RequiredArgsConstructor
public enum AssetErrorCode implements ErrorCode {

    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST),
    LINE_ASSET_MISMATCH(HttpStatus.BAD_REQUEST),
    DISPOSAL_EXCEEDS_REMAINING(HttpStatus.CONFLICT),
    ASSET_HAS_LINES(HttpStatus.CONFLICT),
    LINE_HAS_CHILD_RECORDS(HttpStatus.CONFLICT);

    private final HttpStatus defaultStatus;

    @Override
    public String getCode() {
        return name();
    }
}
