package com.smart.restaurant_saas.inventory.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ImportMaterialsRequest(
        @NotEmpty List<@NotNull Long> catalogIds
) {
}
