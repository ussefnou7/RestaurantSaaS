package com.smart.restaurant_saas.inventory.warehouse.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateWarehouseRequest extends WarehouseRequest {

    @Size(max = 100)
    private String code;
}
