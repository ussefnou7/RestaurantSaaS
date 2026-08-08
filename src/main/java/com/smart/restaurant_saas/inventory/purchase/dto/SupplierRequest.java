package com.smart.restaurant_saas.inventory.purchase.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SupplierRequest {

    @NotBlank(message = "name is required")
    private String name;

    private String nameAr;

    private String phone;

    @Email(message = "email must be a valid email address")
    private String email;

    private String address;

    private String taxNumber;

    @NotNull(message = "active is required")
    private Boolean active = true;

    private String notes;
}
