package com.smart.restaurant_saas.inventory.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.smart.restaurant_saas.inventory.stock.StockBalance;

@Repository
public interface StockBalanceRepository extends JpaRepository<StockBalance, Long> {

    @Query("""
        SELECT s FROM StockBalance s
        WHERE s.tenantId = :tenantId
          AND s.warehouse.id = :warehouseId
          AND s.material.id = :materialId
        """)
    Optional<StockBalance> findByTenantWarehouseMaterial(
        @Param("tenantId") Long tenantId,
        @Param("warehouseId") Long warehouseId,
        @Param("materialId") Long materialId
    );

    @Query("""
        SELECT sb FROM StockBalance sb
        LEFT JOIN FETCH sb.material m
        LEFT JOIN FETCH sb.warehouse w
        LEFT JOIN FETCH sb.uom u
        WHERE sb.tenantId = :tenantId
        AND sb.warehouse.id = :warehouseId
        AND (CAST(:search AS string) IS NULL
             OR LOWER(m.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
             OR LOWER(m.code) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
        AND (:categoryId IS NULL OR m.category.id = :categoryId)
        AND (:belowMinimum IS NULL
             OR (:belowMinimum = true AND sb.quantity < sb.minimumQuantity)
             OR (:belowMinimum = false AND sb.quantity >= sb.minimumQuantity))
        ORDER BY m.name ASC
        """)
    List<StockBalance> findByWarehouse(
        @Param("tenantId") Long tenantId,
        @Param("warehouseId") Long warehouseId,
        @Param("search") String search,
        @Param("categoryId") Long categoryId,
        @Param("belowMinimum") Boolean belowMinimum
    );

    Optional<StockBalance> findByTenantIdAndWarehouseIdAndMaterialId(
        Long tenantId, Long warehouseId, Long materialId
    );

    Optional<StockBalance> findByIdAndTenantId(Long id, Long tenantId);

    // Batch fetch for invoice/return posting
    @Query("""
        SELECT sb FROM StockBalance sb
        WHERE sb.tenantId = :tenantId
        AND sb.warehouse.id = :warehouseId
        AND sb.material.id IN :materialIds
        """)
    List<StockBalance> findByWarehouseAndMaterials(
        @Param("tenantId") Long tenantId,
        @Param("warehouseId") Long warehouseId,
        @Param("materialIds") List<Long> materialIds
    );
}
