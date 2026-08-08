package com.smart.restaurant_saas.job.dto.request;

import jakarta.validation.constraints.Size;

public record CreateJobRequest(
        @Size(max = 255) String nameEn,
        @Size(max = 255) String nameAr,
        String descriptionEn,
        String descriptionAr,
        Boolean active,
        @Size(max = 255) String name,
        String description
) {
    public CreateJobRequest(String name, String description, Boolean active) {
        this(null, null, null, null, active, name, description);
    }
}
