package com.smart.restaurant_saas.common;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Single handler for the entire structured hierarchy (one handler per branch, not per
     * concrete exception). Status and payload come straight off the exception's ErrorCode/params.
     */
    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiErrorResponse> handleAppException(AppException ex, HttpServletRequest req) {
        return ResponseEntity.status(ex.getStatus())
            .body(ApiErrorResponse.of(ex, req.getRequestURI()));
    }

    /**
     * Legacy exception retained for modules not yet migrated to the structured hierarchy.
     * Behavior is preserved: original status + message. (The former "Invalid credentials"
     * string-matching hack is gone — auth now throws a proper AuthenticationException.)
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleLegacyApiException(ApiException ex, HttpServletRequest req) {
        return ResponseEntity.status(ex.getStatus())
            .body(ApiErrorResponse.of("LEGACY_ERROR", ex.getMessage(),
                ex.getStatus().value(), req.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                             HttpServletRequest req) {
        List<ApiErrorResponse.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> new ApiErrorResponse.FieldError(
                fe.getField(),
                "VALIDATION_FAILED",
                ErrorParams.of(
                    "rejectedValue", String.valueOf(fe.getRejectedValue()),
                    "constraint", fe.getDefaultMessage())))
            .toList();
        return ResponseEntity.badRequest()
            .body(ApiErrorResponse.ofFieldErrors(errors, req.getRequestURI()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrity(DataIntegrityViolationException ex,
                                                               HttpServletRequest req) {
        log.warn("Data integrity violation on {}", req.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiErrorResponse.of(CommonErrorCode.DATA_INTEGRITY_VIOLATION.getCode(),
                "Operation violates a data constraint",
                HttpStatus.CONFLICT.value(), req.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(AccessDeniedException ex,
                                                              HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiErrorResponse.of(CommonErrorCode.ACCESS_DENIED.getCode(),
                "Access is denied",
                HttpStatus.FORBIDDEN.value(), req.getRequestURI()));
    }

    /** Catch-all — logs the real cause server-side and returns a non-leaking generic 500. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception on {}", req.getRequestURI(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiErrorResponse.of(CommonErrorCode.INTERNAL_ERROR.getCode(),
                "An unexpected error occurred",
                HttpStatus.INTERNAL_SERVER_ERROR.value(), req.getRequestURI()));
    }
}
