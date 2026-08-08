package com.smart.restaurant_saas.inventory.category.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateMaterialCategoryRequest extends MaterialCategoryRequest {

    @Size(max = 100)
    private String code;
}
