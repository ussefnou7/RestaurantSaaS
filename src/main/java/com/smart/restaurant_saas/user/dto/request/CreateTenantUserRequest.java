package com.smart.restaurant_saas.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTenantUserRequest(
        @NotBlank @Size(max = 255) String fullName,
        @NotBlank @Size(max = 100) String username,
        @NotBlank String password,
        @Email @Size(max = 255) String email,
        @Size(max = 50) String phone
) {
}
