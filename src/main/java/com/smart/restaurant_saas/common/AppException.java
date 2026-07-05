package com.smart.restaurant_saas.common;

import java.util.Collections;
import java.util.Map;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Abstract base of the structured exception hierarchy. Feature code never throws this
 * directly — it throws one of the six concrete subclasses ({@link BusinessException},
 * {@link ResourceNotFoundException}, {@link ValidationException},
 * {@link AuthenticationException}, {@link AuthorizationException},
 * {@link ExternalServiceException}).
 *
 * <p>The HTTP status is derived from the {@link ErrorCode}'s default status — the exception
 * classes never hardcode it. The {@code message} is English debug text for server logs only;
 * the frontend renders user-facing text from {@link #getErrorCode()} + {@link #getParams()}.
 *
 * <p>Named {@code AppException} (not {@code ApiException}) so it can coexist with the retained
 * legacy {@link ApiException}, which other modules still use in this migration pass.
 */
@Getter
public abstract class AppException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus status;
    private final transient Map<String, Object> params;

    protected AppException(ErrorCode errorCode, String debugMessage, Map<String, Object> params) {
        super(debugMessage);
        this.errorCode = errorCode;
        this.status = errorCode.getDefaultStatus();
        this.params = params != null ? params : Collections.emptyMap();
    }

    protected AppException(ErrorCode errorCode, String debugMessage) {
        this(errorCode, debugMessage, Collections.emptyMap());
    }

    protected AppException(ErrorCode errorCode, String debugMessage,
                           Map<String, Object> params, Throwable cause) {
        super(debugMessage, cause);
        this.errorCode = errorCode;
        this.status = errorCode.getDefaultStatus();
        this.params = params != null ? params : Collections.emptyMap();
    }
}
