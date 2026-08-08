package com.smart.restaurant_saas.table;

import com.smart.restaurant_saas.common.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TableErrorCode implements ErrorCode {

    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    SECTION_NOT_FOUND(HttpStatus.NOT_FOUND),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    SECTION_BRANCH_MISMATCH(HttpStatus.BAD_REQUEST),
    TABLE_HAS_ORDERS(HttpStatus.CONFLICT),
    SECTION_HAS_ORDERS(HttpStatus.CONFLICT);

    private final HttpStatus defaultStatus;

    @Override
    public String getCode() {
        return name();
    }
}
