package com.smart.restaurant_saas.inventory.waste.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MaterialShortfallResponse {

    private final Long materialId;
    private final String materialName;
    private final BigDecimal requiredQty;
    private final BigDecimal availableQty;
    private final BigDecimal shortfallQty;
    private final String uomSymbol;
}
