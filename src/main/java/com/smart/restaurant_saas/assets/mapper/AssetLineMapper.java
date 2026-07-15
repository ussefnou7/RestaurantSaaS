package com.smart.restaurant_saas.assets.mapper;

import com.smart.restaurant_saas.assets.assetline.AssetLine;
import com.smart.restaurant_saas.assets.assetline.dto.AssetLineResponse;
import org.springframework.stereotype.Component;

@Component
public class AssetLineMapper {

    public AssetLineResponse toResponse(AssetLine line) {
        return AssetLineResponse.builder()
            .id(line.getId())
            .assetId(line.getAssetId())
            .label(line.getLabel())
            .quantity(line.getQuantity())
            .remainingQuantity(line.getRemainingQuantity())
            .unitCost(line.getUnitCost())
            .totalCost(line.getTotalCost())
            .purchaseDate(line.getPurchaseDate())
            .status(line.getStatus())
            .build();
    }
}
