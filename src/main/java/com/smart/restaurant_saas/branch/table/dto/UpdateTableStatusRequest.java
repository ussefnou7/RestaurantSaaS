package com.smart.restaurant_saas.branch.table.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateTableStatusRequest(
        @NotNull Boolean active
) {
}
