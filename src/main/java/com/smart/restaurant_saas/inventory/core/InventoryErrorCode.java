package com.smart.restaurant_saas.inventory.core;

import com.smart.restaurant_saas.common.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Error codes for the inventory module only. Never reuse another module's code enum, and never
 * throw a raw {@code RuntimeException} or the base exception from inventory feature code.
 */
@Getter
@RequiredArgsConstructor
public enum InventoryErrorCode implements ErrorCode {

    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    RESOURCE_NOT_AVAILABLE_FOR_TENANT(HttpStatus.BAD_REQUEST),
    DUPLICATE_OPERATION(HttpStatus.CONFLICT),
    DUPLICATE_CODE(HttpStatus.CONFLICT),
    CODE_IMMUTABLE(HttpStatus.CONFLICT),
    ALREADY_PROCESSED(HttpStatus.CONFLICT),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT),
    BATCH_SHORTFALL(HttpStatus.CONFLICT),
    UNPOST_BLOCKED_HAS_RETURN(HttpStatus.CONFLICT),
    UNPOST_BLOCKED_BATCH_CONSUMED(HttpStatus.CONFLICT),
    UNPOST_BLOCKED_ORIGINAL_INVOICE_NOT_POSTED(HttpStatus.CONFLICT),
    BATCH_NOT_REVERSIBLE(HttpStatus.CONFLICT),
    FREEZE_CONFLICT(HttpStatus.CONFLICT),
    FREEZE_BLOCKED_BY_CONSUMPTION_CONFLICT(HttpStatus.CONFLICT),
    FREEZE_CONSUMPTION_NOT_SETTLED(HttpStatus.CONFLICT),
    RETURN_QUANTITY_EXCEEDED(HttpStatus.CONFLICT),
    ORDER_CONSUMPTION_PENDING_DOC_RACE_LOST(HttpStatus.CONFLICT),
    ORDER_CONSUMPTION_RECIPE_NOT_RESOLVED(HttpStatus.CONFLICT),
    ORDER_CONSUMPTION_RECIPE_HAS_NO_ITEMS(HttpStatus.CONFLICT),
    ORDER_CONSUMPTION_MIXED_UOM(HttpStatus.CONFLICT),
    ORDER_CONSUMPTION_ERROR_SERIALIZATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR),
    ORDER_CONSUMPTION_RECALCULATE_NOT_CONFLICT(HttpStatus.CONFLICT),
    UOM_CONVERSION_FAILED(HttpStatus.BAD_REQUEST),
    UOM_IN_USE(HttpStatus.CONFLICT),
    GLOBAL_UOM_NOT_DELETABLE(HttpStatus.CONFLICT),
    EMPTY_DOCUMENT_LINES(HttpStatus.BAD_REQUEST),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST);

    private final HttpStatus defaultStatus;

    @Override
    public String getCode() {
        return name();
    }
}
