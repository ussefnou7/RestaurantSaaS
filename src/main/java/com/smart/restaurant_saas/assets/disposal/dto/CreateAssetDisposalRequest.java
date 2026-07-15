package com.smart.restaurant_saas.assets.disposal.dto;

import com.smart.restaurant_saas.assets.core.enums.AssetDisposalReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateAssetDisposalRequest {

    @NotNull(message = "assetId is required")
    private Long assetId;

    @NotNull(message = "assetLineId is required")
    private Long assetLineId;

    @NotNull(message = "quantityDisposed is required")
    @Positive(message = "quantityDisposed must be positive")
    private BigDecimal quantityDisposed;

    @NotNull(message = "reason is required")
    private AssetDisposalReason reason;

    @NotNull(message = "disposalDate is required")
    private LocalDate disposalDate;

    private String notes;
}
