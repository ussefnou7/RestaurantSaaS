package com.smart.restaurant_saas.inventory.material.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateMaterialRequest extends MaterialRequest {

    @Size(max = 100)
    private String code;
}
