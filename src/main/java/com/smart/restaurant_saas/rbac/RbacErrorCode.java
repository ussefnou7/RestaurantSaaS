package com.smart.restaurant_saas.rbac;

import com.smart.restaurant_saas.common.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum RbacErrorCode implements ErrorCode {

    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    SELF_ACTION_BLOCKED(HttpStatus.FORBIDDEN),
    NOT_ALLOWED_FOR_ROLE(HttpStatus.FORBIDDEN),
    BRANCH_REQUIRED_FOR_ROLE(HttpStatus.BAD_REQUEST),
    BRANCH_NOT_ALLOWED_FOR_ROLE(HttpStatus.BAD_REQUEST);

    private final HttpStatus defaultStatus;

    @Override
    public String getCode() {
        return name();
    }
}
