package com.smart.restaurant_saas.user.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateTenantUserRequest(
        @NotBlank @Size(max = 255) String fullName,
        @NotBlank @Size(max = 100) String username,
        @Email @Size(max = 255) String email,
        @Size(max = 50) String phone,
        @Size(min = 8, max = 64, message = "PASSWORD_LENGTH")
        @Pattern(regexp = "^(?=.*\\p{L})(?=.*\\p{N})[^\\p{Cc}]+$", message = "PASSWORD_PATTERN")
        String password
) {
}
