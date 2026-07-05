package com.smart.restaurant_saas.common;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Structured error response body. The frontend renders user-facing text from {@code errorCode}
 * + {@code params} (translatable to Arabic/English) — {@code message} is English debug text for
 * logs/devtools only and must not be shown to end users.
 */
public record ApiErrorResponse(
    String errorCode,
    String message,
    Map<String, Object> params,
    int status,
    LocalDateTime timestamp,
    String path,
    List<FieldError> fieldErrors
) {
    public record FieldError(String field, String errorCode, Map<String, Object> params) {}

    /** Single structured error from any {@link AppException}. */
    public static ApiErrorResponse of(AppException ex, String path) {
        return new ApiErrorResponse(
            ex.getErrorCode().getCode(),
            ex.getMessage(),
            ex.getParams(),
            ex.getStatus().value(),
            LocalDateTime.now(),
            path,
            null);
    }

    /** Generic single error not backed by an {@link AppException} (legacy/handler-level). */
    public static ApiErrorResponse of(String errorCode, String message, int status, String path) {
        return new ApiErrorResponse(
            errorCode, message, Map.of(), status, LocalDateTime.now(), path, null);
    }

    /** Bean-validation failure carrying every rejected field. */
    public static ApiErrorResponse ofFieldErrors(List<FieldError> fieldErrors, String path) {
        return new ApiErrorResponse(
            "VALIDATION_FAILED",
            "Request validation failed",
            Map.of(),
            org.springframework.http.HttpStatus.BAD_REQUEST.value(),
            LocalDateTime.now(),
            path,
            fieldErrors);
    }
}
