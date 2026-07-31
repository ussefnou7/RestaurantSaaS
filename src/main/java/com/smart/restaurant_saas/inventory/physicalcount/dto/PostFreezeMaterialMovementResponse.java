package com.smart.restaurant_saas.inventory.physicalcount.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

/** What moved for one material, in the count line's frozen UOM, after the freeze cutoff. */
@Getter
@Builder
public class PostFreezeMaterialMovementResponse {

    private final Long materialId;
    private final String materialCode;
    private final String materialName;
    private final String materialNameAr;
    private final Long uomId;
    private final String uomSymbol;
    private final Integer movementCount;
    private final BigDecimal quantityIn;
    private final BigDecimal quantityOut;
    /** quantityIn - quantityOut. */
    private final BigDecimal netQuantity;
}
