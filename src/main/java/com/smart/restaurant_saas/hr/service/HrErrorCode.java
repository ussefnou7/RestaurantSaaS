package com.smart.restaurant_saas.hr.service;

import com.smart.restaurant_saas.common.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Error codes shared across the tightly-coupled HR / Users / Branches / Jobs domains. These
 * modules already share services (e.g. {@link HrValidationService} reaches into Branch, User and
 * Job), so a single enum keeps the codes consistent rather than fragmenting near-identical
 * generic codes (RESOURCE_NOT_FOUND, DUPLICATE_OPERATION, VALIDATION_FAILED) across four enums.
 */
@Getter
@RequiredArgsConstructor
public enum HrErrorCode implements ErrorCode {

    INVALID_STATE_TRANSITION(HttpStatus.CONFLICT),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    DUPLICATE_OPERATION(HttpStatus.CONFLICT),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    INACTIVE_REFERENCE(HttpStatus.BAD_REQUEST),
    DEACTIVATION_BLOCKED(HttpStatus.BAD_REQUEST),
    SELF_ACTION_BLOCKED(HttpStatus.FORBIDDEN),
    SYSTEM_TENANT_RESTRICTED(HttpStatus.FORBIDDEN),
    NOT_ALLOWED_FOR_ROLE(HttpStatus.FORBIDDEN),
    BRANCH_SCOPE_REQUIRED(HttpStatus.FORBIDDEN),
    TENANT_CONTEXT_REQUIRED(HttpStatus.FORBIDDEN),
    UNSUPPORTED_OPERATION(HttpStatus.BAD_REQUEST),
    INSUFFICIENT_LEAVE_BALANCE(HttpStatus.BAD_REQUEST),
    NO_ACTIVE_LEAVE_TYPES(HttpStatus.BAD_REQUEST),
    NEGATIVE_REMAINING_BALANCE(HttpStatus.BAD_REQUEST);

    private final HttpStatus defaultStatus;

    @Override
    public String getCode() {
        return name();
    }
}
