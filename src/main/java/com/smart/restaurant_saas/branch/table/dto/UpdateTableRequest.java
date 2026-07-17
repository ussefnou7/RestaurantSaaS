package com.smart.restaurant_saas.branch.table.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateTableRequest(
        @NotBlank @Size(max = 50) String tableNo,
        @NotNull @Min(1) Integer capacity
) {
}
