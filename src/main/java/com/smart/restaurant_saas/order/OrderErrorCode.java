package com.smart.restaurant_saas.order;

import com.smart.restaurant_saas.common.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum OrderErrorCode implements ErrorCode {

    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND),
    BRANCH_NOT_FOUND(HttpStatus.NOT_FOUND),
    WAREHOUSE_NOT_FOUND(HttpStatus.NOT_FOUND),
    AMBIGUOUS_WAREHOUSE_FOR_BRANCH(HttpStatus.CONFLICT),
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND),
    PRODUCT_HAS_NO_ACTIVE_RECIPE(HttpStatus.BAD_REQUEST),
    TABLE_NOT_FOUND(HttpStatus.NOT_FOUND),
    TABLE_BRANCH_MISMATCH(HttpStatus.BAD_REQUEST),
    INVALID_TABLE_FOR_ORDER_TYPE(HttpStatus.BAD_REQUEST),
    CANCELLATION_DETAILS_REQUIRED(HttpStatus.BAD_REQUEST),
    CANCELLATION_NOTE_REQUIRED_FOR_OTHER(HttpStatus.BAD_REQUEST),
    CANCELLATION_STAGE_NOT_ALLOWED(HttpStatus.BAD_REQUEST),
    /** A sales report was asked for a window that is missing a bound or starts after it ends. */
    REPORT_DATE_RANGE_INVALID(HttpStatus.BAD_REQUEST),
    INCOMING_ORDER_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND),
    INVALID_REQUEST_STATUS_TRANSITION(HttpStatus.CONFLICT);

    private final HttpStatus defaultStatus;

    @Override
    public String getCode() {
        return name();
    }
}
