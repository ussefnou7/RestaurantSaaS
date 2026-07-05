package com.smart.restaurant_saas.common;

import java.util.Map;

/**
 * A downstream/external dependency failed. Scaffolded for future use — not thrown yet.
 * Conventional default status: 502 Bad Gateway (from the supplied {@link ErrorCode}).
 */
public class ExternalServiceException extends AppException {

    public ExternalServiceException(ErrorCode errorCode, String debugMessage, Map<String, Object> params) {
        super(errorCode, debugMessage, params);
    }

    public ExternalServiceException(ErrorCode errorCode, String debugMessage) {
        super(errorCode, debugMessage);
    }

    public ExternalServiceException(ErrorCode errorCode, String debugMessage,
                                    Map<String, Object> params, Throwable cause) {
        super(errorCode, debugMessage, params, cause);
    }
}
