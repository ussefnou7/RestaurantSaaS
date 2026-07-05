package com.smart.restaurant_saas.menu.category.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MenuCategoryResponse {

    private final Long id;
    private final String name;
    private final Integer sortOrder;
    private final Boolean active;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
