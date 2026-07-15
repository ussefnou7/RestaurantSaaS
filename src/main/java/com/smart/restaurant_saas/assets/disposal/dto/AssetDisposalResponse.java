package com.smart.restaurant_saas.assets.disposal.dto;

import com.smart.restaurant_saas.assets.core.enums.AssetDisposalReason;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AssetDisposalResponse {

    private final Long id;
    private final Long assetId;
    private final Long assetLineId;
    private final BigDecimal quantityDisposed;
    private final AssetDisposalReason reason;
    private final LocalDate disposalDate;
    private final String notes;
    private final Long createdBy;
    private final LocalDateTime createdAt;
}
