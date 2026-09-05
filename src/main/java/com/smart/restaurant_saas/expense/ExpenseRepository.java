package com.smart.restaurant_saas.expense;

import com.smart.restaurant_saas.expense.core.enums.ExpensePaymentSource;
import com.smart.restaurant_saas.expense.core.enums.ExpenseStatus;
import java.time.LocalDate;
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
               e.createdAt AS createdAt
        FROM Expense e
        JOIN ExpenseCategory c
          ON c.id = e.categoryId
         AND (c.tenantId IS NULL OR c.tenantId = e.tenantId)
        LEFT JOIN Branch b
          ON b.id = e.branchId
         AND b.tenantId = e.tenantId
        WHERE e.tenantId = :tenantId
          AND (:branchId IS NULL OR e.branchId = :branchId)
          AND (:unbranchedOnly = FALSE OR e.branchId IS NULL)
          AND (:categoryId IS NULL OR e.categoryId = :categoryId)
          AND (:dateFrom IS NULL OR e.expenseDate >= :dateFrom)
          AND (:dateTo IS NULL OR e.expenseDate <= :dateTo)
          AND (:paymentSource IS NULL OR e.paymentSource = :paymentSource)
          AND (:status IS NULL OR e.status = :status)
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
               e.createdAt AS createdAt
        FROM Expense e
        JOIN ExpenseCategory c
          ON c.id = e.categoryId
         AND (c.tenantId IS NULL OR c.tenantId = e.tenantId)
        LEFT JOIN Branch b
          ON b.id = e.branchId
         AND b.tenantId = e.tenantId
        WHERE e.id = :id AND e.tenantId = :tenantId
        """)
    Optional<ExpenseListProjection> findListItemById(
        @Param("id") Long id,
        @Param("tenantId") Long tenantId);
}
