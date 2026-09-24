package com.smart.restaurant_saas.order.core.dto;

import com.smart.restaurant_saas.order.core.enums.OrderLineType;
import com.smart.restaurant_saas.order.core.enums.CancellationStage;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrderLineRequest {

    @NotNull(message = "productId is required")
    private Long productId;

    @NotNull(message = "quantity is required")
    @DecimalMin(value = "0.000001", message = "quantity must be greater than zero")
    @Digits(integer = 12, fraction = 6, message = "quantity must have at most 6 decimal places")
    private BigDecimal quantity;

    @NotNull(message = "unitPrice is required")
    @DecimalMin(value = "0.00", message = "unitPrice must be non-negative")
    @Digits(integer = 16, fraction = 2, message = "unitPrice must have at most 2 decimal places")
    private BigDecimal unitPrice;

    // D129: the line amount the POS printed and charged, stored as sent rather than
    // re-derived from quantity × unitPrice. The POS rounds at line level and sums the
    // rounded lines into the header, so re-deriving here would put the record back out
    // of step with the receipt.
    @NotNull(message = "lineTotal is required")
    @DecimalMin(value = "0.00", message = "lineTotal must be non-negative")
    @Digits(integer = 16, fraction = 2, message = "lineTotal must have at most 2 decimal places")
    private BigDecimal lineTotal;

    /** SALE unless the POS binned it after cooking (D20). Absent means SALE. */
    private OrderLineType lineType;

    /** Required on a WASTE line, forbidden otherwise: only the two cooked stages are waste. */
    private CancellationStage wasteStage;
}
