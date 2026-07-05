package com.smart.restaurant_saas.inventory.purchase.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PurchaseReturnLineResponse {

    private final Long id;
    private final Long originalLineId;
    private final Long materialId;
    private final String materialCode;
    private final String materialName;
    private final BigDecimal quantity;
    private final Long uomId;
    private final String uomSymbol;
    private final BigDecimal unitCost;
    private final BigDecimal lineTotal;
    private final String notes;
}
