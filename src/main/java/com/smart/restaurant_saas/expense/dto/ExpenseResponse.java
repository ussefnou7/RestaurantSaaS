package com.smart.restaurant_saas.expense.dto;

import com.smart.restaurant_saas.expense.core.enums.ExpensePaymentSource;
import com.smart.restaurant_saas.expense.core.enums.ExpenseSourceType;
import com.smart.restaurant_saas.expense.core.enums.ExpenseStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExpenseResponse {

    private final Long id;
    private final Long branchId;
    private final String branchName;
    private final Long categoryId;
    private final String categoryName;
    private final String categoryNameAr;
    private final BigDecimal amount;
    private final LocalDate expenseDate;
    private final String description;
    private final String payeeName;
    private final ExpensePaymentSource paymentSource;
    private final ExpenseSourceType sourceType;
    private final Long sourceId;
    private final ExpenseStatus status;
    private final LocalDateTime voidedAt;
    private final Long voidedBy;
    private final String voidReason;
    private final Long createdBy;
    private final LocalDateTime createdAt;
}
