package com.smart.restaurant_saas.common;

import java.util.Map;

/**
 * The caller could not be authenticated (bad or missing credentials).
 * Conventional default status: 401 Unauthorized (from the supplied {@link ErrorCode}).
 */
public class AuthenticationException extends AppException {

    public AuthenticationException(ErrorCode errorCode, String debugMessage, Map<String, Object> params) {
        super(errorCode, debugMessage, params);
    }

    public AuthenticationException(ErrorCode errorCode, String debugMessage) {
        super(errorCode, debugMessage);
    }
}
