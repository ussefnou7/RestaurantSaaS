package com.smart.restaurant_saas.inventory.material.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SkippedMaterialDto {

    private final Long catalogId;
    private final String code;
    private final String name;

    /** One of: ALREADY_IMPORTED, INACTIVE_CATALOG_MATERIAL, NOT_FOUND, CODE_ALREADY_EXISTS. */
    private final String reason;
}
