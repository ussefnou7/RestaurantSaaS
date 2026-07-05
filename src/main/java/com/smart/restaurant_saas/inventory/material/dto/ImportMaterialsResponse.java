package com.smart.restaurant_saas.inventory.material.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ImportMaterialsResponse {

    private final Integer requestedCount;
    private final Integer createdCount;
    private final Integer skippedCount;
    private final List<SkippedMaterialDto> skippedMaterials;
}
