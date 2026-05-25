package com.smart.restaurant_saas.auth.dto.response;

public record LoginResponse(
        String accessToken,
        AuthUserResponse user
) {
}
