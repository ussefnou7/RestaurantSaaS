package com.smart.restaurant_saas.expense;

import com.smart.restaurant_saas.expense.core.enums.ExpenseStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One expense as it appears on the shift detail screen.
 *
 * <p><b>{@code recordedByName} and {@code createdAt} are not optional decoration.</b> D124 makes
 * {@code paidFromShiftId} a sensitive field precisely because a manager chooses it, so the
 * question in any investigation is "who attached this amount to this shift, and when" — a total
 * alone cannot answer it.
 *
 * <p>{@code expenseDate} is carried alongside {@code createdAt} because the gap between them is
 * itself the signal (D118). Twenty minutes is routine; three days, for exactly the amount a shift
 * closed short, is the finding.
 */
public interface ShiftExpenseProjection {

    Long getId();
    BigDecimal getAmount();
    LocalDate getExpenseDate();
    String getDescription();
    String getPayeeName();
    Long getCategoryId();
    String getCategoryName();
    ExpenseStatus getStatus();
    Long getRecordedByUserId();
    String getRecordedByName();
    LocalDateTime getCreatedAt();
}
