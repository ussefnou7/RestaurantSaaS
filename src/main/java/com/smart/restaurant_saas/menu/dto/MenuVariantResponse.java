package com.smart.restaurant_saas.menu.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MenuVariantResponse {

    private final Long id;
    private final String name;
    private final String variantLabel;
    private final String variantLabelAr;
    private final BigDecimal sellingPrice;
}
