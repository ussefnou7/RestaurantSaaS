package com.smart.restaurant_saas.assets.disposal.dto;

import com.smart.restaurant_saas.assets.core.enums.AssetCategory;
import com.smart.restaurant_saas.assets.core.enums.AssetDisposalReason;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import lombok.Getter;

@Getter
public class AssetDisposalListItemResponse {

    private static final int SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final Long id;
    private final Long assetId;
    private final String assetName;
    private final String assetNameAr;
    private final AssetCategory category;
    private final Long branchId;
    private final Long assetLineId;
    private final String assetLineLabel;
    private final BigDecimal unitCost;
    private final BigDecimal quantityDisposed;
    private final BigDecimal disposalValue;
    private final LocalDate disposalDate;
    private final AssetDisposalReason reason;
    private final String notes;

    public AssetDisposalListItemResponse(Long id, Long assetId, String assetName, String assetNameAr,
                                         AssetCategory category, Long branchId, Long assetLineId,
                                         String assetLineLabel, BigDecimal unitCost,
                                         BigDecimal quantityDisposed, LocalDate disposalDate,
                                         AssetDisposalReason reason, String notes) {
        this.id = id;
        this.assetId = assetId;
        this.assetName = assetName;
        this.assetNameAr = assetNameAr;
        this.category = category;
        this.branchId = branchId;
        this.assetLineId = assetLineId;
        this.assetLineLabel = assetLineLabel;
        this.unitCost = unitCost;
        this.quantityDisposed = quantityDisposed;
        this.disposalValue = quantityDisposed.multiply(unitCost).setScale(SCALE, ROUNDING);
        this.disposalDate = disposalDate;
        this.reason = reason;
        this.notes = notes;
    }
}
