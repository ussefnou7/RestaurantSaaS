package com.smart.restaurant_saas.loyalty.customer.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CustomerRequest {

    @NotBlank(message = "phone is required")
    private String phone;

    @NotBlank(message = "name is required")
    private String name;
}
