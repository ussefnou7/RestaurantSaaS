package com.smart.restaurant_saas.inventory.material.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MaterialCatalogResponse {

    private final Long id;
    private final String code;
    private final String name;
    private final String nameAr;

    private final Long categoryId;
    private final String categoryName;

    private final Long stockUomId;
    private final String stockUomName;
    private final String stockUomCode;
    private final String stockUomSymbol;

    private final Long displayUomId;
    private final String displayUomName;
    private final String displayUomCode;
    private final String displayUomSymbol;

    /** Same as stockUomId — the base unit for the catalog item. */
    private final Long defaultUomId;
    private final String defaultUomName;
    private final String defaultUomCode;
    private final String defaultUomSymbol;

    private final Boolean active;

    /** True if the current tenant has already imported this catalog item. */
    private final Boolean alreadyImported;

    /** The tenant's existing material id when alreadyImported is true. */
    private final Long importedMaterialId;
}
