package com.smart.restaurant_saas.inventory.purchase.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateSupplierRequest extends SupplierRequest {

    @Size(max = 100)
    private String code;
}
