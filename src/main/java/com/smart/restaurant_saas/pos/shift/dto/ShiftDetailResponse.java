package com.smart.restaurant_saas.pos.shift.dto;

import com.smart.restaurant_saas.expense.core.enums.ExpenseStatus;
import com.smart.restaurant_saas.order.core.enums.OrderStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * The Z view of one shift (D125): the header, what was sold, what was paid out, and — kept
 * separate — what arrived after the drawer was counted.
 *
 * <p><b>{@code lateExpenses} is never added into {@code variance}, and the two are returned as
 * distinct fields so a client cannot accidentally sum them.</b> Merging them would let any
 * shortfall be erased after the fact by recording an expense for the matching amount — the easiest
 * exploit available in the system, and it would turn the expenses screen into an eraser for
 * variances (D124). {@code explainedVariance} is offered pre-computed precisely so that the client
 * never does the addition itself and never gets the sign wrong.
 *
 * <p>The summary money fields are permission-gated as a group. The order and expense line arrays
 * still carry individual amounts without {@code SHIFTS_VIEW_VARIANCE}; combined with the header's
 * opening and closing counts, that currently permits reconstruction of the blind figures. This is
 * documented as an unresolved release finding rather than claimed as a completed D123 guarantee.
 */
public record ShiftDetailResponse(
        ShiftListItemResponse shift,

        /** Sales by payment method over this shift's COMPLETE orders. Gated. */
        Map<String, BigDecimal> salesByPaymentMethod,

        /** The cash sales term of {@code expectedCash}. Gated. */
        BigDecimal cashSales,

        /** Frozen at close (D124). Gated. */
        BigDecimal expensesAtClose,

        /** Recorded against this shift after it closed. Gated, and never summed into variance. */
        BigDecimal lateExpenses,

        /** {@code variance + lateExpenses}. Gated. Null while the shift is open. */
        BigDecimal explainedVariance,

        List<ShiftOrderLine> orders,
        List<ShiftExpenseLine> expenses
) {

    /** An order on this shift. Carries no cost or margin — this is a cash-reconciliation screen. */
    public record ShiftOrderLine(
            Long id,
            String orderNo,
            LocalDateTime orderDate,
            OrderStatus status,
            String paymentMethod,
            BigDecimal totalAmount,
            Long createdByUserId,
            String createdByName
    ) {
    }

    /**
     * An expense on this shift, with who attached it and when.
     *
     * <p>{@code recordedAfterClose} marks the rows that are excluded from the stored variance.
     * {@code expenseDate} sits next to {@code createdAt} because the gap between them is the
     * signal (D118).
     */
    public record ShiftExpenseLine(
            Long id,
            BigDecimal amount,
            LocalDate expenseDate,
            String description,
            String payeeName,
            Long categoryId,
            String categoryName,
            ExpenseStatus status,
            Long recordedByUserId,
            String recordedByName,
            LocalDateTime createdAt,
            boolean recordedAfterClose
    ) {
    }
}
