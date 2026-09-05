package com.smart.restaurant_saas.auth.dto.response;

public record TokenRefreshResponse(
        String accessToken,
        String refreshToken
) {
}
