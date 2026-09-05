package com.smart.restaurant_saas.expense.mapper;

import com.smart.restaurant_saas.expense.category.ExpenseCategory;
import com.smart.restaurant_saas.expense.category.dto.ExpenseCategoryResponse;
import org.springframework.stereotype.Component;

@Component
public class ExpenseCategoryMapper {

    public ExpenseCategoryResponse toResponse(ExpenseCategory category) {
        return ExpenseCategoryResponse.builder()
            .id(category.getId())
            .tenantId(category.getTenantId())
            .name(category.getName())
            .nameAr(category.getNameAr())
            .active(category.getActive())
            .global(category.getTenantId() == null)
            .createdBy(category.getCreatedBy())
            .createdAt(category.getCreatedAt())
            .build();
    }
}
