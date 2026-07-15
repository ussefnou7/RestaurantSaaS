package com.smart.restaurant_saas.assets.asset.dto;

import com.smart.restaurant_saas.assets.core.enums.AssetCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateAssetRequest {

    @NotBlank(message = "name is required")
    private String name;

    private String nameAr;

    @NotNull(message = "category is required")
    private AssetCategory category;
}
