package com.smart.restaurant_saas.tenant.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateTenantStatusRequest(
        @NotBlank String status
) {
}
