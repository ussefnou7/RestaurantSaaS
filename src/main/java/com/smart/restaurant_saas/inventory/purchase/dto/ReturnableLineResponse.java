package com.smart.restaurant_saas.inventory.purchase.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

/**
 * A still-returnable line of the original invoice, for the FE to build the return form
 * after the header is saved. {@code returnableQuantity = originalQuantity - returnedQuantity},
 * where returnedQuantity is the sum already taken by POSTED returns of the same invoice.
 */
@Getter
@Builder
public class ReturnableLineResponse {

    private final Long originalLineId;
    private final Long materialId;
    private final String materialCode;
    private final String materialName;
    /** The unit only; the client resolves its name from the UOM lookup cache (D111 phase 3). */
    private final Long uomId;
    private final BigDecimal unitCost;
    private final BigDecimal originalQuantity;
    private final BigDecimal returnedQuantity;
    private final BigDecimal returnableQuantity;
}
