package com.smart.restaurant_saas.expense;

import com.smart.restaurant_saas.expense.core.enums.ExpensePaymentSource;
import com.smart.restaurant_saas.expense.core.enums.ExpenseSourceType;
import com.smart.restaurant_saas.expense.core.enums.ExpenseStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public interface ExpenseListProjection {

    Long getId();
    Long getBranchId();
    String getBranchName();
    Long getCategoryId();
    String getCategoryName();
    String getCategoryNameAr();
    BigDecimal getAmount();
    LocalDate getExpenseDate();
    String getDescription();
    String getPayeeName();
    ExpensePaymentSource getPaymentSource();
    ExpenseSourceType getSourceType();
    Long getSourceId();
    ExpenseStatus getStatus();
    LocalDateTime getVoidedAt();
    Long getVoidedBy();
    String getVoidReason();
    Long getCreatedBy();
    LocalDateTime getCreatedAt();
}
