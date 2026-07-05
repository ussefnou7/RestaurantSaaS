package com.smart.restaurant_saas.common;

import java.util.Map;

/**
 * A referenced resource does not exist (or is not visible to the caller's tenant).
 * Conventional default status: 404 Not Found (from the supplied {@link ErrorCode}).
 */
public class ResourceNotFoundException extends AppException {

    public ResourceNotFoundException(ErrorCode errorCode, String debugMessage, Map<String, Object> params) {
        super(errorCode, debugMessage, params);
    }

    public ResourceNotFoundException(ErrorCode errorCode, String debugMessage) {
        super(errorCode, debugMessage);
    }
}
