package com.smart.restaurant_saas.inventory.material.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MaterialResponse {

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

    /** Same as stockUomId — the base unit used for all inventory calculations. */
    private final Long defaultUomId;
    private final String defaultUomName;
    private final String defaultUomCode;
    private final String defaultUomSymbol;

    private final BigDecimal minimumStockLevel;

    /** Present when the material was imported from the global catalog. */
    private final Long catalogId;

    private final Boolean active;
    private final String notes;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
