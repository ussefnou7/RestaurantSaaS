package com.smart.restaurant_saas.assets.mapper;

import com.smart.restaurant_saas.assets.disposal.AssetDisposal;
import com.smart.restaurant_saas.assets.disposal.dto.AssetDisposalResponse;
import org.springframework.stereotype.Component;

@Component
public class AssetDisposalMapper {

    public AssetDisposalResponse toResponse(AssetDisposal disposal) {
        return AssetDisposalResponse.builder()
            .id(disposal.getId())
            .assetId(disposal.getAssetId())
            .assetLineId(disposal.getAssetLineId())
            .quantityDisposed(disposal.getQuantityDisposed())
            .reason(disposal.getReason())
            .disposalDate(disposal.getDisposalDate())
            .notes(disposal.getNotes())
            .createdBy(disposal.getCreatedBy())
            .createdAt(disposal.getCreatedAt())
            .build();
    }
}
