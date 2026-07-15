package com.smart.restaurant_saas.assets.assetline.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateAssetLineRequest {

    private String label;

    @NotNull(message = "quantity is required")
    @Positive(message = "quantity must be positive")
    private BigDecimal quantity;

    @NotNull(message = "unitCost is required")
    @PositiveOrZero(message = "unitCost must be zero or positive")
    private BigDecimal unitCost;

    @NotNull(message = "purchaseDate is required")
    private LocalDate purchaseDate;
}
