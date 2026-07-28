package com.smart.restaurant_saas.assets.maintenance.dto;

import com.smart.restaurant_saas.assets.core.enums.AssetCategory;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;

@Getter
public class AssetMaintenanceListItemResponse {

    private final Long id;
    private final Long assetId;
    private final String assetName;
    private final String assetNameAr;
    private final AssetCategory category;
    private final Long branchId;
    private final Long assetLineId;
    private final String assetLineLabel;
    private final BigDecimal cost;
    private final LocalDate maintenanceDate;
    private final String description;
    private final String vendor;

    public AssetMaintenanceListItemResponse(Long id, Long assetId, String assetName,
                                            String assetNameAr, AssetCategory category,
                                            Long branchId, Long assetLineId,
                                            String assetLineLabel, BigDecimal cost,
                                            LocalDate maintenanceDate, String description,
                                            String vendor) {
        this.id = id;
        this.assetId = assetId;
        this.assetName = assetName;
        this.assetNameAr = assetNameAr;
        this.category = category;
        this.branchId = branchId;
        this.assetLineId = assetLineId;
        this.assetLineLabel = assetLineLabel;
        this.cost = cost;
        this.maintenanceDate = maintenanceDate;
        this.description = description;
        this.vendor = vendor;
    }
}
