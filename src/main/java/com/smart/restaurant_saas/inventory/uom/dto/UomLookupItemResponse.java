package com.smart.restaurant_saas.inventory.uom.dto;

import com.smart.restaurant_saas.inventory.core.enums.UomType;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UomLookupItemResponse {

    private final Long id;
    private final String code;
    private final String symbol;
    private final String symbolAr;
    private final String name;
    private final String nameAr;
    private final BigDecimal factorToBase;
    private final Long baseUomId;
    private final UomType type;
    private final Boolean active;
}
