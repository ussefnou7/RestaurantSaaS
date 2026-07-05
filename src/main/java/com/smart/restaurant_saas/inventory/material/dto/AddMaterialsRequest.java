package com.smart.restaurant_saas.inventory.material.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddMaterialsRequest {

    @NotEmpty(message = "at least one material is required")
    private List<Long> materialIds;
}
