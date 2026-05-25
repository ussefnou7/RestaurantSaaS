package com.smart.restaurant_saas.branch.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateBranchRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 100) String code,
        String address,
        @Size(max = 50) String phone,
        Boolean active
) {
}
