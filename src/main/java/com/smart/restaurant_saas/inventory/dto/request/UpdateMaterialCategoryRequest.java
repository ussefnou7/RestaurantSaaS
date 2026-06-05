package com.smart.restaurant_saas.inventory.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateMaterialCategoryRequest(
        @NotBlank @Size(max = 100) String code,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String nameAr,
        Boolean active,
        Integer sortOrder
) {
}
