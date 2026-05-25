package com.smart.restaurant_saas.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank @Size(max = 255) String fullName,
        @Size(max = 50) String phone,
        @NotBlank @Size(max = 100) String roleCode,
        Long branchId,
        Boolean active
) {
}
