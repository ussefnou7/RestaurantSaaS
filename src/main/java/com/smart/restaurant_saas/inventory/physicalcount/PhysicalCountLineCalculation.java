package com.smart.restaurant_saas.inventory.physicalcount;

import java.math.BigDecimal;

/** Physical-count values calculated from the frozen snapshot and ledger movements. */
public record PhysicalCountLineCalculation(
    BigDecimal adjustedExpectedQuantity,
    BigDecimal variance,
    BigDecimal varianceValue,
    boolean provisional
) {
}
