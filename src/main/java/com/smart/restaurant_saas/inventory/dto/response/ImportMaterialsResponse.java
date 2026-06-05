package com.smart.restaurant_saas.inventory.dto.response;

import java.util.List;

public record ImportMaterialsResponse(
        int requestedCount,
        int createdCount,
        int skippedCount,
        List<MaterialResponse> createdMaterials,
        List<SkippedMaterialImportResponse> skippedMaterials
) {
}
