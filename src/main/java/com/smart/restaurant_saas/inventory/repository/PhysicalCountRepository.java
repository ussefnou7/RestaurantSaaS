package com.smart.restaurant_saas.inventory.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.smart.restaurant_saas.inventory.core.enums.PhysicalCountStatus;
import com.smart.restaurant_saas.inventory.physicalcount.MaterialConflictProjection;
import com.smart.restaurant_saas.inventory.physicalcount.PhysicalCount;

@Repository
public interface PhysicalCountRepository extends JpaRepository<PhysicalCount, Long> {

    Optional<PhysicalCount> findByIdAndTenantId(Long id, Long tenantId);

    @EntityGraph(attributePaths = {
        "warehouse",
        "lines",
        "lines.material",
        "lines.material.stockUom",
        "lines.material.stockUom.baseUom",
        "lines.uom",
        "lines.uom.baseUom"
    })
    @Query("""
        SELECT pc
        FROM PhysicalCount pc
        WHERE pc.id = :id
          AND pc.tenantId = :tenantId
        """)
    Optional<PhysicalCount> findDetailByIdAndTenantId(
        @Param("id") Long id,
        @Param("tenantId") Long tenantId);

    List<PhysicalCount> findByTenantIdOrderByScheduledDateDesc(Long tenantId);

    List<PhysicalCount> findByTenantIdAndWarehouseIdOrderByScheduledDateDesc(
        Long tenantId, Long warehouseId);

    boolean existsByTenantIdAndWarehouseIdAndScheduledDateAndStatusIn(
        Long tenantId, Long warehouseId,
        LocalDate scheduledDate, java.util.List<PhysicalCountStatus> statuses);

    /**
     * Returns one row per conflicting (material, holding-count) pair: every material in
     * {@code materialIds} that is already frozen by a different IN_PROGRESS count in the same
     * warehouse. Single query — no N+1.
     */
    @Query("""
        SELECT l.material.id   AS materialId,
               l.material.name AS materialName,
               pc.code         AS countCode
        FROM PhysicalCount pc
        JOIN pc.lines l
        WHERE pc.tenantId   = :tenantId
          AND pc.warehouse.id = :warehouseId
          AND pc.status      = com.smart.restaurant_saas.inventory.core.enums.PhysicalCountStatus.IN_PROGRESS
          AND pc.id         <> :excludeId
          AND l.material.id IN :materialIds
        """)
    List<MaterialConflictProjection> findFreezeConflicts(
        @Param("tenantId")     Long tenantId,
        @Param("warehouseId")  Long warehouseId,
        @Param("excludeId")    Long excludeId,
        @Param("materialIds")  List<Long> materialIds);
}
