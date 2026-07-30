package com.smart.restaurant_saas.inventory.physicalcount.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import com.smart.restaurant_saas.inventory.core.enums.CountLineAction;

@Getter
@Builder
public class PhysicalCountLineResponse {

    private final Long id;
    private final Long materialId;
    private final String materialCode;
    private final String materialName;
    private final String materialNameAr;
    private final Long uomId;
    private final String uomSymbol;
    private final BigDecimal expectedQuantity;
    private final BigDecimal adjustedExpectedQuantity;
    private final BigDecimal countedQuantity;
    private final BigDecimal variance;
    private final BigDecimal varianceValue;
    private final BigDecimal unitCostAtFreeze;
    private final CountLineAction actionTaken;
    private final Long adjustmentTransactionId;
    private final LocalDateTime countedAt;
    private final String notes;
}
