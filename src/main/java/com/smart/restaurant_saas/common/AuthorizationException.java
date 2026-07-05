package com.smart.restaurant_saas.common;

import java.util.Map;

/**
 * The caller is authenticated but not permitted to perform the action.
 * Conventional default status: 403 Forbidden (from the supplied {@link ErrorCode}).
 */
public class AuthorizationException extends AppException {

    public AuthorizationException(ErrorCode errorCode, String debugMessage, Map<String, Object> params) {
        super(errorCode, debugMessage, params);
    }

    public AuthorizationException(ErrorCode errorCode, String debugMessage) {
        super(errorCode, debugMessage);
    }
}
