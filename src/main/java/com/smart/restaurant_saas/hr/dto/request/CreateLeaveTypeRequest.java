package com.smart.restaurant_saas.hr.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateLeaveTypeRequest(
        @Size(max = 255) String nameEn,
        @Size(max = 255) String nameAr,
        @NotBlank @Size(max = 100) String code,
        String descriptionEn,
        String descriptionAr,
        @NotNull @DecimalMin(value = "0.0") BigDecimal defaultDays,
        Boolean paid,
        Boolean active,
        @Size(max = 255) String name,
        String description
) {
}
