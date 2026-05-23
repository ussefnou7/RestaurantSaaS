package com.smart.restaurant_saas.common;

import java.time.Instant;

public record ApiErrorResponse(
        boolean success,
        String message,
        Instant timestamp
) {

    public static ApiErrorResponse of(String message) {
        return new ApiErrorResponse(false, message, Instant.now());
    }
}
