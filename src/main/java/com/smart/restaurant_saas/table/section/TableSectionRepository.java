package com.smart.restaurant_saas.table.section;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TableSectionRepository extends JpaRepository<TableSection, Long> {

    Optional<TableSection> findByIdAndTenantId(Long id, Long tenantId);

    @Query("""
            SELECT s FROM TableSection s
            JOIN FETCH s.branch b
            WHERE s.tenantId = :tenantId
              AND b.id = :branchId
            ORDER BY s.active DESC, s.name ASC, s.id ASC
            """)
    List<TableSection> findByTenantIdAndBranchId(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId
    );

    @Query("""
            SELECT s FROM TableSection s
            JOIN FETCH s.branch b
            WHERE s.tenantId = :tenantId
              AND b.id = :branchId
              AND s.active = TRUE
            ORDER BY s.name ASC, s.id ASC
            """)
    List<TableSection> findActiveByTenantIdAndBranchId(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId
    );
}
