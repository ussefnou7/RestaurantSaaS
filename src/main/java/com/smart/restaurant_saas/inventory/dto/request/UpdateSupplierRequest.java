package com.smart.restaurant_saas.inventory.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateSupplierRequest(
        @NotBlank @Size(max = 100) String code,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String nameAr,
        @Size(max = 50) String phone,
        @Email @Size(max = 255) String email,
        String address,
        @Size(max = 100) String taxNumber,
        Boolean active,
        String notes
) {
}
