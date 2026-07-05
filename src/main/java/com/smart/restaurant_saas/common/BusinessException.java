package com.smart.restaurant_saas.common;

import java.util.Map;

/**
 * A request that is well-formed and authorized but violates a business rule.
 * Conventional default status: 409 Conflict (from the supplied {@link ErrorCode}).
 */
public class BusinessException extends AppException {

    public BusinessException(ErrorCode errorCode, String debugMessage, Map<String, Object> params) {
        super(errorCode, debugMessage, params);
    }

    public BusinessException(ErrorCode errorCode, String debugMessage) {
        super(errorCode, debugMessage);
    }

    protected BusinessException(ErrorCode errorCode, String debugMessage,
                                Map<String, Object> params, Throwable cause) {
        super(errorCode, debugMessage, params, cause);
    }

    /**
     * Backward-compatible constructor for inventory throw sites not yet migrated to structured
     * codes in this pass. Maps to {@link CommonErrorCode#BUSINESS_RULE_VIOLATION} (409), matching
     * the pre-migration behavior. Do not use in new code — throw with an explicit module ErrorCode.
     */
    @Deprecated
    public BusinessException(String debugMessage) {
        super(CommonErrorCode.BUSINESS_RULE_VIOLATION, debugMessage);
    }
}
