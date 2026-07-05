package com.smart.restaurant_saas.inventory.stock.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AddMaterialToWarehouseRequest {

    @NotNull(message = "materialId is required")
    private Long materialId;

    /** Optional. Defaults to zero. When greater than zero an OPENING_BALANCE ledger entry is posted. */
    @PositiveOrZero(message = "openingBalance must be non-negative")
    private BigDecimal openingBalance;

    /**
     * Optional opening unit cost, expressed in the material's display UOM. Only meaningful
     * when openingBalance > 0. Seeds the balance's average cost and the OPENING_BALANCE
     * transaction's cost. Not editable afterwards — it changes only via later transactions.
     */
    @PositiveOrZero(message = "averageCost must be non-negative")
    private BigDecimal averageCost;

    @PositiveOrZero(message = "minimumQuantity must be non-negative")
    private BigDecimal minimumQuantity;

    /** Optional. Nullable — no maximum is enforced when omitted. */
    @PositiveOrZero(message = "maximumQuantity must be non-negative")
    private BigDecimal maximumQuantity;
}
