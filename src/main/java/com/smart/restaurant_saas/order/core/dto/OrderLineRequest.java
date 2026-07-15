package com.smart.restaurant_saas.order.core.dto;

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
}
