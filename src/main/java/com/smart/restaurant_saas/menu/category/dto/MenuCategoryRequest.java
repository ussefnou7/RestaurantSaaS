package com.smart.restaurant_saas.menu.category.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MenuCategoryRequest {

    @NotBlank(message = "name is required")
    private String name;

    @Size(max = 255, message = "nameAr must not exceed 255 characters")
    private String nameAr;

    @NotNull(message = "sortOrder is required")
    @PositiveOrZero(message = "sortOrder must be non-negative")
    private Integer sortOrder = 0;

    @NotNull(message = "active is required")
    private Boolean active = true;
}
