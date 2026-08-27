package com.smart.restaurant_saas.menu.product.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProductResponse {

    private final Long id;
    private final String name;
    private final String description;
    private final String descriptionAr;
    private final BigDecimal sellingPrice;
    @JsonProperty("isActive")
    private final Boolean isActive;
    private final Long menuCategoryId;
    private final String menuCategoryName;
    private final String menuCategoryNameAr;
    private final Long parentProductId;
    private final String variantLabel;
    private final String variantLabelAr;
    @JsonProperty("isMenu")
    private final Boolean isMenu;
    // Derived, never persisted: true iff another product references this one via parentProductId.
    @JsonProperty("isParent")
    @Getter(onMethod_ = @JsonProperty("isParent"))
    private final boolean isParent;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
