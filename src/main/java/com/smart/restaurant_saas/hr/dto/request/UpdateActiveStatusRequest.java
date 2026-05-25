package com.smart.restaurant_saas.hr.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdateActiveStatusRequest(
        @NotNull Boolean active
) {
}
