package com.smart.restaurant_saas.inventory.waste.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WasteLineResponse {

    private final Long id;
    private final Long materialId;
    private final String materialCode;
    private final String materialName;
    private final BigDecimal quantity;
    private final Long uomId;
    private final String uomSymbol;
    private final String notes;
    /**
     * The cost of the write-off, computed by FIFO depletion at POST and read back from the
     * WASTE ledger transaction. Null until the document is posted.
     */
    private final BigDecimal cost;
}
