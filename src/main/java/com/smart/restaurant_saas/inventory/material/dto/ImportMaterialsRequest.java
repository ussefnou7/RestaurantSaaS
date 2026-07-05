package com.smart.restaurant_saas.inventory.material.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ImportMaterialsRequest {

    @NotEmpty(message = "catalogIds must contain at least one item")
    private List<Long> catalogIds;
}
