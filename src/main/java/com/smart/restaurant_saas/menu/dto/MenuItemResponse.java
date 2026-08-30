package com.smart.restaurant_saas.menu.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MenuItemResponse {

    private final Long id;
    private final String name;
    private final MenuItemType type;
    private final Long menuCategoryId;
    private final String menuCategoryName;
    private final String menuCategoryNameAr;
    private final BigDecimal sellingPrice;
    private final BigDecimal minPrice;
    private final BigDecimal maxPrice;
    private final List<MenuVariantResponse> variants;
    private final List<MenuAddOnResponse> addOns;
}
