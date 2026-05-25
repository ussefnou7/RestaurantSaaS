package com.smart.restaurant_saas.hr.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateJobTitleRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 100) String code,
        String description,
        Boolean active
) {
}
