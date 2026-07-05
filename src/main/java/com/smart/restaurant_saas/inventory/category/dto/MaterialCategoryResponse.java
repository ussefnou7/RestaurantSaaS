package com.smart.restaurant_saas.inventory.category.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MaterialCategoryResponse {

    private final Long id;
    private final String code;
    private final String name;
    private final String nameAr;

    /** True when this is a global category (tenantId is null). */
    private final Boolean global;

    private final Boolean active;
    private final Integer sortOrder;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
