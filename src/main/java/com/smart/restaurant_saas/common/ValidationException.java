package com.smart.restaurant_saas.common;

import java.util.Map;

/**
 * Input failed a semantic validation rule (beyond bean-validation field checks).
 * Conventional default status: 400 Bad Request (from the supplied {@link ErrorCode}).
 */
public class ValidationException extends AppException {

    public ValidationException(ErrorCode errorCode, String debugMessage, Map<String, Object> params) {
        super(errorCode, debugMessage, params);
    }

    public ValidationException(ErrorCode errorCode, String debugMessage) {
        super(errorCode, debugMessage);
    }
}
