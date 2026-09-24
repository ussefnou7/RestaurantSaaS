package com.smart.restaurant_saas.menu.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.smart.restaurant_saas.media.dto.MediaSummaryResponse;
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
    /**
     * The product's image, absent when it has none (this class is {@code NON_NULL}).
     *
     * <p>Embedded rather than left to the caller because the alternative is one {@code /api/media}
     * request per tile, and the surface that reads this draws every product at once.
     */
    private final MediaSummaryResponse image;
    private final List<MenuVariantResponse> variants;
    private final List<MenuAddOnResponse> addOns;
}
