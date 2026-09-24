package com.smart.restaurant_saas.expense;

import com.smart.restaurant_saas.expense.core.enums.ExpensePaymentSource;
import com.smart.restaurant_saas.expense.core.enums.ExpenseStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {

    Optional<Expense> findByIdAndTenantId(Long id, Long tenantId);

    /**
     * The expenses term of {@code expectedCash}, frozen into {@code shift.expensesAtClose} inside
     * the close transaction (D124).
     *
     * <p>{@code ACTIVE} only -- a voided expense is money that did not leave the drawer, and
     * counting it would manufacture a shortfall. Expenses recorded against this shift *after* it
     * closed are not excluded here because at close time none exist yet; they are reported
     * separately by the shift read service after converting audit times to the branch zone.
     */
    @Query("""
        SELECT COALESCE(SUM(e.amount), 0)
        FROM Expense e
        WHERE e.tenantId = :tenantId
          AND e.paidFromShiftId = :shiftId
          AND e.status = com.smart.restaurant_saas.expense.core.enums.ExpenseStatus.ACTIVE
        """)
    BigDecimal sumActiveByShift(@Param("shiftId") Long shiftId, @Param("tenantId") Long tenantId);

    @Query("""
        SELECT e.id AS id,
               e.branchId AS branchId,
               b.name AS branchName,
               e.categoryId AS categoryId,
               c.name AS categoryName,
               c.nameAr AS categoryNameAr,
               e.amount AS amount,
               e.expenseDate AS expenseDate,
               e.description AS description,
               e.payeeName AS payeeName,
               e.paymentSource AS paymentSource,
               e.sourceType AS sourceType,
               e.sourceId AS sourceId,
               e.status AS status,
               e.voidedAt AS voidedAt,
               e.voidedBy AS voidedBy,
               e.voidReason AS voidReason,
               e.createdBy AS createdBy,
               e.createdAt AS createdAt,
               e.paidFromShiftId AS paidFromShiftId,
               ps.closedAt AS paidFromShiftClosedAt
        FROM Expense e
        JOIN ExpenseCategory c
          ON c.id = e.categoryId
         AND (c.tenantId IS NULL OR c.tenantId = e.tenantId)
        LEFT JOIN Branch b
          ON b.id = e.branchId
         AND b.tenantId = e.tenantId
        LEFT JOIN Shift ps
          ON ps.id = e.paidFromShiftId
         AND ps.tenantId = e.tenantId
        WHERE e.tenantId = :tenantId
          AND (CAST(:branchId AS long) IS NULL OR e.branchId = :branchId)
          AND (:unbranchedOnly = FALSE OR e.branchId IS NULL)
          AND (CAST(:categoryId AS long) IS NULL OR e.categoryId = :categoryId)
          AND (CAST(:dateFrom AS LocalDate) IS NULL OR e.expenseDate >= :dateFrom)
          AND (CAST(:dateTo AS LocalDate) IS NULL OR e.expenseDate <= :dateTo)
          AND (CAST(:paymentSource AS string) IS NULL OR e.paymentSource = :paymentSource)
          AND (CAST(:status AS string) IS NULL OR e.status = :status)
          AND (CAST(:search AS string) IS NULL
               OR LOWER(e.description) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
               OR LOWER(e.payeeName) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
        """)
    Page<ExpenseListProjection> findListItems(
        @Param("tenantId") Long tenantId,
        @Param("branchId") Long branchId,
        @Param("unbranchedOnly") boolean unbranchedOnly,
        @Param("categoryId") Long categoryId,
        @Param("dateFrom") LocalDate dateFrom,
        @Param("dateTo") LocalDate dateTo,
        @Param("paymentSource") ExpensePaymentSource paymentSource,
        @Param("status") ExpenseStatus status,
        @Param("search") String search,
        Pageable pageable);

    @Query("""
        SELECT e.id AS id,
               e.branchId AS branchId,
               b.name AS branchName,
               e.categoryId AS categoryId,
               c.name AS categoryName,
               c.nameAr AS categoryNameAr,
               e.amount AS amount,
               e.expenseDate AS expenseDate,
               e.description AS description,
               e.payeeName AS payeeName,
               e.paymentSource AS paymentSource,
               e.sourceType AS sourceType,
               e.sourceId AS sourceId,
               e.status AS status,
               e.voidedAt AS voidedAt,
               e.voidedBy AS voidedBy,
               e.voidReason AS voidReason,
               e.createdBy AS createdBy,
               e.createdAt AS createdAt,
               e.paidFromShiftId AS paidFromShiftId,
               ps.closedAt AS paidFromShiftClosedAt
        FROM Expense e
        JOIN ExpenseCategory c
          ON c.id = e.categoryId
         AND (c.tenantId IS NULL OR c.tenantId = e.tenantId)
        LEFT JOIN Branch b
          ON b.id = e.branchId
         AND b.tenantId = e.tenantId
        LEFT JOIN Shift ps
          ON ps.id = e.paidFromShiftId
         AND ps.tenantId = e.tenantId
        WHERE e.id = :id AND e.tenantId = :tenantId
        """)
    Optional<ExpenseListProjection> findListItemById(
        @Param("id") Long id,
        @Param("tenantId") Long tenantId);

    /**
     * Every expense charged to one shift, for its detail screen (D124).
     *
     * <p>Voided rows are included deliberately. They did not enter {@code expectedCash}, but an
     * expense recorded against a drawer and then voided is exactly the sequence an investigation
     * needs to see; filtering them here would hide it. The caller renders the status.
     */
    @Query("""
        SELECT e.id AS id,
               e.amount AS amount,
               e.expenseDate AS expenseDate,
               e.description AS description,
               e.payeeName AS payeeName,
               e.categoryId AS categoryId,
               c.name AS categoryName,
               e.status AS status,
               e.createdBy AS recordedByUserId,
               u.fullName AS recordedByName,
               e.createdAt AS createdAt
        FROM Expense e
        JOIN ExpenseCategory c
          ON c.id = e.categoryId
         AND (c.tenantId IS NULL OR c.tenantId = e.tenantId)
        LEFT JOIN User u
          ON u.id = e.createdBy
         AND u.tenantId = e.tenantId
        WHERE e.tenantId = :tenantId
          AND e.paidFromShiftId = :shiftId
        ORDER BY e.createdAt ASC, e.id ASC
        """)
    List<ShiftExpenseProjection> findByShift(
        @Param("shiftId") Long shiftId,
        @Param("tenantId") Long tenantId);
}
