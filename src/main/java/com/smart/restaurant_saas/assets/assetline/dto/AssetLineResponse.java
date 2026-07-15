package com.smart.restaurant_saas.assets.assetline.dto;

import com.smart.restaurant_saas.assets.core.enums.AssetLineStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AssetLineResponse {

    private final Long id;
    private final Long assetId;
    private final String label;
    private final BigDecimal quantity;
    private final BigDecimal remainingQuantity;
    private final BigDecimal unitCost;
    private final BigDecimal totalCost;
    private final LocalDate purchaseDate;
    private final AssetLineStatus status;
}
