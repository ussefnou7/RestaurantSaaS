package com.smart.restaurant_saas.expense.mapper;

import com.smart.restaurant_saas.expense.ExpenseListProjection;
import com.smart.restaurant_saas.expense.dto.ExpenseResponse;
import org.springframework.stereotype.Component;

@Component
public class ExpenseMapper {

    public ExpenseResponse toResponse(ExpenseListProjection expense) {
        return ExpenseResponse.builder()
            .id(expense.getId())
            .branchId(expense.getBranchId())
            .branchName(expense.getBranchName())
            .categoryId(expense.getCategoryId())
            .categoryName(expense.getCategoryName())
            .categoryNameAr(expense.getCategoryNameAr())
            .amount(expense.getAmount())
            .expenseDate(expense.getExpenseDate())
            .description(expense.getDescription())
            .payeeName(expense.getPayeeName())
            .paymentSource(expense.getPaymentSource())
            .sourceType(expense.getSourceType())
            .sourceId(expense.getSourceId())
            .status(expense.getStatus())
            .voidedAt(expense.getVoidedAt())
            .voidedBy(expense.getVoidedBy())
            .voidReason(expense.getVoidReason())
            .createdBy(expense.getCreatedBy())
            .createdAt(expense.getCreatedAt())
            .paidFromShiftId(expense.getPaidFromShiftId())
            .recordedAfterShiftClose(recordedAfterShiftClose(expense))
            .build();
    }

    /**
     * Null when there is no linked shift, so the client can tell "not a drawer expense" from "a
     * drawer expense recorded in time". False means the expense entered its shift's
     * {@code expectedCash}; true means it did not and never will (D124).
     */
    private Boolean recordedAfterShiftClose(ExpenseListProjection expense) {
        if (expense.getPaidFromShiftId() == null) {
            return null;
        }
        return expense.getPaidFromShiftClosedAt() != null
            && expense.getCreatedAt() != null
            && expense.getCreatedAt().isAfter(expense.getPaidFromShiftClosedAt());
    }
}
