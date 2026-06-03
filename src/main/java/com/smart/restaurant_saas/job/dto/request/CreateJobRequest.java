package com.smart.restaurant_saas.job.dto.request;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

public record CreateJobRequest(
        @Size(max = 255) String nameEn,
        @Size(max = 255) String nameAr,
        @NotBlank @Size(max = 100) String code,
        String descriptionEn,
        String descriptionAr,
        Boolean active,
        @Size(max = 255) String name,
        String description
) {
    public CreateJobRequest(String name, String code, String description, Boolean active) {
        this(null, null, code, null, null, active, name, description);
    }
}
