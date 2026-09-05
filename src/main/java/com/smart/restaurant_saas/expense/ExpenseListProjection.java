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

    /** D124. Null for most expenses — the drawer link is the exception, not the rule. */
    Long getPaidFromShiftId();

    /**
     * The linked shift's close time, projected so {@code recordedAfterShiftClose} can be derived
     * without a second query. Null when unlinked, or when the shift is still open.
     */
    LocalDateTime getPaidFromShiftClosedAt();
}
