package com.smart.restaurant_saas.expense;

import com.smart.restaurant_saas.common.TenantAwareEntity;
import com.smart.restaurant_saas.expense.core.enums.ExpensePaymentSource;
import com.smart.restaurant_saas.expense.core.enums.ExpenseSourceType;
import com.smart.restaurant_saas.expense.core.enums.ExpenseStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "expense")
public class Expense extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "branch_id")
    private Long branchId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    /**
     * The shift whose drawer paid this, chosen explicitly by the manager (D124). Never inferred
     * from {@code expenseDate} -- that is a DATE with no time, so it cannot identify one of a
     * day's three shifts, and if it drove attribution a manager could erase any shortfall by
     * dating an expense into the shift that has it.
     *
     * <p>Null for expenses that did not come out of a drawer, which is most of them.
     */
    @Column(name = "paid_from_shift_id")
    private Long paidFromShiftId;

    @Column(name = "amount", nullable = false, precision = 18, scale = 6)
    private BigDecimal amount;

    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "payee_name")
    private String payeeName;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_source", nullable = false, length = 32)
    private ExpensePaymentSource paymentSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 32)
    private ExpenseSourceType sourceType = ExpenseSourceType.MANUAL;

    @Column(name = "source_id")
    private Long sourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ExpenseStatus status = ExpenseStatus.ACTIVE;

    @Column(name = "voided_at")
    private LocalDateTime voidedAt;

    @Column(name = "voided_by")
    private Long voidedBy;

    @Column(name = "void_reason", length = 500)
    private String voidReason;
}
