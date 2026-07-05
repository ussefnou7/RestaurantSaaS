package com.smart.restaurant_saas.inventory.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.smart.restaurant_saas.inventory.purchase.Supplier;

@Repository
public interface SupplierRepository extends JpaRepository<Supplier, Long> {

    Optional<Supplier> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByTenantIdAndCode(Long tenantId, String code);

    @Query("""
        SELECT s FROM Supplier s
        WHERE s.tenantId = :tenantId
        AND (CAST(:search AS string) IS NULL
             OR LOWER(s.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
             OR LOWER(s.code) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
        AND (:active IS NULL OR s.active = :active)
        ORDER BY s.name ASC
        """)
    List<Supplier> findByFilters(
        @Param("tenantId") Long tenantId,
        @Param("search") String search,
        @Param("active") Boolean active
    );
}
