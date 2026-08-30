package com.smart.restaurant_saas.menu.category.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MenuCategoryResponse {

    private final Long id;
    private final String name;
    private final String nameAr;
    private final Integer sortOrder;
    @JsonProperty("isActive")
    private final Boolean isActive;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
