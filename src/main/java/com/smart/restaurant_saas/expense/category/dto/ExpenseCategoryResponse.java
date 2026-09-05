package com.smart.restaurant_saas.expense.category.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExpenseCategoryResponse {

    private final Long id;
    private final Long tenantId;
    private final String name;
    private final String nameAr;
    private final boolean active;
    private final boolean global;
    private final Long createdBy;
    private final LocalDateTime createdAt;
}
