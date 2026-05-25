package com.smart.restaurant_saas.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @Size(max = 100) String tenantCode,
        @NotBlank String username,
        @NotBlank String password
) {
}
