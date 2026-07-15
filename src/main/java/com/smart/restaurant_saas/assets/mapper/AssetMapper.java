package com.smart.restaurant_saas.assets.mapper;

import com.smart.restaurant_saas.assets.asset.Asset;
import com.smart.restaurant_saas.assets.asset.dto.AssetResponse;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class AssetMapper {

    /**
     * {@code lineCount} and {@code totalCurrentValue} are derived aggregates computed by the
     * service from the asset's lines, so they are passed in rather than read off the entity.
     */
    public AssetResponse toResponse(Asset asset, long lineCount, BigDecimal totalCurrentValue) {
        return AssetResponse.builder()
            .id(asset.getId())
            .branchId(asset.getBranchId())
            .name(asset.getName())
            .nameAr(asset.getNameAr())
            .category(asset.getCategory())
            .status(asset.getStatus())
            .lineCount(lineCount)
            .totalCurrentValue(totalCurrentValue)
            .build();
    }
}
