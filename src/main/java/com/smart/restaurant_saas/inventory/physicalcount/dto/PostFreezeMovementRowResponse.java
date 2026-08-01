package com.smart.restaurant_saas.inventory.physicalcount.dto;

import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionDirection;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PostFreezeMovementRowResponse {

    private final Long materialId;
    private final String materialName;
    private final String materialNameAr;
    private final BigDecimal quantity;
    private final Long uomId;
    private final String uomSymbol;
    private final InventoryTransactionDirection direction;
    private final LocalDateTime movementDate;
    private final LocalDateTime createdAt;
    private final String referenceType;
    private final Long referenceId;
    private final String referenceCode;
}
