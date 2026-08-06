package com.smart.restaurant_saas.inventory.orderconsumption;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderConsumptionMaterialRepository extends JpaRepository<OrderConsumptionMaterial, Long> {

    /**
     * The doc's material rows with everything the processing loop, the status derivation and the
     * detail response need, so none of them triggers a lazy load per material.
     */
    @Query("""
        SELECT row
        FROM OrderConsumptionMaterial row
        JOIN FETCH row.material material
        JOIN FETCH row.requiredUom
        JOIN FETCH row.enteredUom
        WHERE row.doc.id = :docId
        ORDER BY material.name ASC
        """)
    List<OrderConsumptionMaterial> findByDocId(@Param("docId") Long docId);

    /**
     * Outstanding quantities of the warehouse's PARTIAL docs, in display UOM. Counterpart to
     * {@code OrderConsumptionLineRepository.sumOutstandingRecipeQuantitiesByWarehouse}, which
     * covers PENDING docs — those have no material rows yet.
     */
    @Query("""
        SELECT row.material.id AS materialId, SUM(row.requiredQuantity) AS quantity
        FROM OrderConsumptionMaterial row
        JOIN row.doc doc
        WHERE doc.tenantId = :tenantId
          AND doc.warehouse.id = :warehouseId
          AND doc.status = :status
          AND row.consumed = false
        GROUP BY row.material.id
        """)
    List<MaterialQuantity> sumUnconsumedRequiredQuantitiesByWarehouse(
        @Param("tenantId") Long tenantId,
        @Param("warehouseId") Long warehouseId,
        @Param("status") OrderConsumptionStatus status
    );
}
