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

    /** The manager-selected shift whose drawer paid this (D124). Null for most expenses. */
    private final Long paidFromShiftId;

    /**
     * True when this expense was recorded after its linked shift had already closed. Null when
     * there is no linked shift.
     *
     * <p>Derived by comparing {@code createdAt} with the shift's {@code closedAt}; not stored. It
     * exists so the expenses screen can label the row: a late expense is linked and visible but has
     * not moved — and must never move — that shift's recorded variance (D124).
     */
    private final Boolean recordedAfterShiftClose;
}
