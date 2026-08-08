package com.smart.restaurant_saas.branch.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateBranchRequest(
        @Size(max = 255) String nameEn,
        @Size(max = 255) String nameAr,
        @NotBlank @Size(max = 100) String code,
        String addressEn,
        String addressAr,
        @Size(max = 50) String phone,
        Boolean active,
        @Size(max = 255) String name,
        String address,
        @Size(max = 64) String timezone
) {
    public UpdateBranchRequest(String name, String code, String address, String phone, Boolean active) {
        this(null, null, code, null, null, phone, active, name, address, null);
    }
}
