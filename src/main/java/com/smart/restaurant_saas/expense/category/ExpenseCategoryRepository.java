package com.smart.restaurant_saas.expense.category;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ExpenseCategoryRepository extends JpaRepository<ExpenseCategory, Long> {

    @Query("""
        SELECT c FROM ExpenseCategory c
        WHERE c.id = :id
          AND (c.tenantId IS NULL OR c.tenantId = :tenantId)
        """)
    Optional<ExpenseCategory> findAvailableById(
        @Param("id") Long id,
        @Param("tenantId") Long tenantId);

    Optional<ExpenseCategory> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByIdAndTenantIdIsNull(Long id);

    boolean existsByTenantIdAndNameIgnoreCase(Long tenantId, String name);

    boolean existsByTenantIdAndNameIgnoreCaseAndIdNot(Long tenantId, String name, Long id);

    @Query("""
        SELECT c FROM ExpenseCategory c
        WHERE c.tenantId IS NULL OR c.tenantId = :tenantId
        ORDER BY CASE WHEN c.tenantId IS NULL THEN 0 ELSE 1 END ASC,
                 LOWER(c.name) ASC,
                 c.id ASC
        """)
    List<ExpenseCategory> findAvailableForTenant(@Param("tenantId") Long tenantId);
}
