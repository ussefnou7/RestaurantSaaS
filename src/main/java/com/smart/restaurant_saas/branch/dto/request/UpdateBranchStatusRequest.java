package com.smart.restaurant_saas.branch.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdateBranchStatusRequest(
        @NotNull Boolean active
) {
}
