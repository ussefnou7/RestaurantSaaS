package com.smart.restaurant_saas.inventory.orderconsumption.dto;

import com.smart.restaurant_saas.inventory.orderconsumption.OrderConsumptionFailureReason;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

/**
 * One persisted (doc, material) outcome row. {@code requiredQuantity} and
 * {@code availableQuantity} are both in {@code uomId}/{@code uomSymbol} — the material's display
 * UOM (D87 layer 2), carried explicitly per D88.
 */
@Getter
@Builder
public class OrderConsumptionDocMaterialResponse {

    private final Long materialId;
    private final String materialName;
    private final BigDecimal requiredQuantity;
    private final Long uomId;
    private final String uomSymbol;
    private final boolean consumed;

    /** Open-batch quantity at the time of the attempt. Set only when INSUFFICIENT_STOCK. */
    private final BigDecimal availableQuantity;

    private final OrderConsumptionFailureReason failureReason;
    private final String exceptionClass;
    private final String exceptionMessage;
}
