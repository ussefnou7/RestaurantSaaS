package com.smart.restaurant_saas.menu.dto;

import java.math.BigDecimal;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MenuAddOnResponse {

    private final Long id;
    private final String name;
    private final BigDecimal sellingPrice;
}
