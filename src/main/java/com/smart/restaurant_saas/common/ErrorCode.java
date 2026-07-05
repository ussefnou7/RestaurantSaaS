package com.smart.restaurant_saas.common;

import org.springframework.http.HttpStatus;

/**
 * Machine-readable error identity. Every module defines its own enum implementing this
 * (e.g. {@code InventoryErrorCode}); the frontend derives all user-facing text from
 * {@link #getCode()} + the exception's params, never from the server debug message.
 */
public interface ErrorCode {
    String getCode();
    HttpStatus getDefaultStatus();
}
