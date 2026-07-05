package com.smart.restaurant_saas.inventory.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MaterialCategoryRequest {

    @NotBlank(message = "code is required")
    private String code;

    @NotBlank(message = "name is required")
    private String name;

    private String nameAr;

    @NotNull(message = "active is required")
    private Boolean active = true;

    private Integer sortOrder;
}
