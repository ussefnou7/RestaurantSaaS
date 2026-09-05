package com.smart.restaurant_saas.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefreshTokenRequest(
        @NotBlank @Size(max = 512) String refreshToken
) {
}
