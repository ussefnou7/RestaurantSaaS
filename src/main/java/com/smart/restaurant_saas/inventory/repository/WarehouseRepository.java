package com.smart.restaurant_saas.inventory.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.smart.restaurant_saas.inventory.core.enums.WarehouseType;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;

@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    Optional<Warehouse> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByTenantIdAndCode(Long tenantId, String code);

    @Query("""
        SELECT w FROM Warehouse w
        LEFT JOIN FETCH w.branch b
        WHERE w.tenantId = :tenantId
        AND (CAST(:search AS string) IS NULL
             OR LOWER(w.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
             OR LOWER(w.code) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
        AND (:branchId IS NULL OR b.id = :branchId)
        AND (:type IS NULL OR w.type = :type)
        AND (:active IS NULL OR w.active = :active)
        ORDER BY w.name ASC
        """)
    List<Warehouse> findByFilters(
        @Param("tenantId") Long tenantId,
        @Param("search") String search,
        @Param("branchId") Long branchId,
        @Param("type") WarehouseType type,
        @Param("active") Boolean active
    );
}
