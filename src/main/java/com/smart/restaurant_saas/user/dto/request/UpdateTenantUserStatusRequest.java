package com.smart.restaurant_saas.user.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateTenantUserStatusRequest(
        @NotBlank String status
) {
}
