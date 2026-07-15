package com.smart.restaurant_saas.assets.asset.dto;

import com.smart.restaurant_saas.assets.core.enums.AssetCategory;
import com.smart.restaurant_saas.assets.core.enums.AssetStatus;
import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AssetResponse {

    private final Long id;
    private final Long branchId;
    private final String name;
    private final String nameAr;
    private final AssetCategory category;
    private final AssetStatus status;
    private final long lineCount;
    private final BigDecimal totalCurrentValue;
}
