package com.smart.restaurant_saas.tenant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTenantRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 100) String code,
        @NotBlank @Size(max = 64) String timezone
) {
}
