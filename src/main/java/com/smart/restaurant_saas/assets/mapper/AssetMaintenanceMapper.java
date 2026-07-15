package com.smart.restaurant_saas.assets.mapper;

import com.smart.restaurant_saas.assets.maintenance.AssetMaintenance;
import com.smart.restaurant_saas.assets.maintenance.dto.AssetMaintenanceResponse;
import org.springframework.stereotype.Component;

@Component
public class AssetMaintenanceMapper {

    public AssetMaintenanceResponse toResponse(AssetMaintenance maintenance) {
        return AssetMaintenanceResponse.builder()
            .id(maintenance.getId())
            .assetId(maintenance.getAssetId())
            .assetLineId(maintenance.getAssetLineId())
            .cost(maintenance.getCost())
            .maintenanceDate(maintenance.getMaintenanceDate())
            .description(maintenance.getDescription())
            .vendor(maintenance.getVendor())
            .createdBy(maintenance.getCreatedBy())
            .createdAt(maintenance.getCreatedAt())
            .build();
    }
}
