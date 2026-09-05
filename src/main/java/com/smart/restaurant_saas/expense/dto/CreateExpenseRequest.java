package com.smart.restaurant_saas.expense.dto;

import com.smart.restaurant_saas.expense.core.enums.ExpensePaymentSource;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateExpenseRequest {

    private Long branchId;

    @NotNull(message = "categoryId is required")
    private Long categoryId;

    @NotNull(message = "amount is required")
    @Digits(integer = 12, fraction = 6, message = "amount must fit NUMERIC(18,6)")
    private BigDecimal amount;

    @NotNull(message = "expenseDate is required")
    private LocalDate expenseDate;

    @Size(max = 500, message = "description must not exceed 500 characters")
    private String description;

    @Size(max = 255, message = "payeeName must not exceed 255 characters")
    private String payeeName;

    @NotNull(message = "paymentSource is required")
    private ExpensePaymentSource paymentSource;
}
