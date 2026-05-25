package com.smart.restaurant_saas.rbac.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssignUserRoleRequest(
        @NotBlank @Size(max = 100) String roleCode,
        @NotBlank @Size(max = 30) String scope,
        Long branchId
) {
}
