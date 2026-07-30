package com.smart.restaurant_saas.inventory.physicalcount.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

/** What moved for one material, in stock UOM, between a count's freeze cutoff and now. */
@Getter
@Builder
public class PostFreezeMaterialMovementResponse {

    private final Long materialId;
    private final String materialCode;
    private final String materialName;
    private final String materialNameAr;
    private final Integer movementCount;
    private final BigDecimal quantityIn;
    private final BigDecimal quantityOut;
    /** quantityIn - quantityOut. */
    private final BigDecimal netQuantity;
}
