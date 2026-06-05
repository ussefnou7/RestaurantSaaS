package com.smart.restaurant_saas.inventory.dto.response;

import com.smart.restaurant_saas.inventory.enums.MaterialImportSkipReason;

public record SkippedMaterialImportResponse(
        Long catalogId,
        MaterialImportSkipReason reason
) {
}
