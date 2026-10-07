package com.smart.restaurant_saas.inventory.orderconsumption;

import com.smart.restaurant_saas.tenant.TenantUnscoped;
import java.util.Collection;
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
    @TenantUnscoped("docId must be a consumption doc already loaded for the acting tenant; the "
        + "query scopes through row.doc.id only.")
    List<OrderConsumptionMaterial> findByDocId(@Param("docId") Long docId);

    /**
     * Outstanding quantities of the warehouse's docs in the given statuses, in display UOM.
     * Counterpart to {@link OrderConsumptionLineRepository#sumPendingRecipeQuantitiesByWarehouse},
     * which covers PENDING docs — those have no material rows yet.
     */
    @Query("""
        SELECT row.material.id AS materialId, SUM(row.requiredQuantity) AS quantity
        FROM OrderConsumptionMaterial row
        JOIN row.doc doc
        WHERE doc.tenantId = :tenantId
          AND doc.warehouse.id = :warehouseId
          AND doc.status IN :statuses
          AND row.consumed = false
        GROUP BY row.material.id
        """)
    List<MaterialQuantity> sumUnconsumedRequiredQuantitiesByWarehouse(
        @Param("tenantId") Long tenantId,
        @Param("warehouseId") Long warehouseId,
        @Param("statuses") Collection<OrderConsumptionStatus> statuses
    );

    /** Outstanding display-UOM quantity and display-unit average cost, grouped per warehouse/material.
     * A missing balance keeps a null cost; it must not hide an unposted material.
     */
    @Query(nativeQuery = true, value = """
        SELECT w.branch_id                 AS "branchId",
               doc.warehouse_id            AS "warehouseId",
               m.id                        AS "materialId",
               SUM(ocm.required_quantity)  AS "requiredQuantity",
               MAX(sb.average_cost)        AS "averageCost"
        FROM order_consumption_material ocm
        JOIN order_consumption doc ON doc.id = ocm.doc_id
        JOIN warehouse w           ON w.id = doc.warehouse_id
        JOIN material m            ON m.id = ocm.material_id
        LEFT JOIN stock_balance sb ON sb.warehouse_id = doc.warehouse_id
                                  AND sb.material_id  = ocm.material_id
                                  AND sb.tenant_id    = doc.tenant_id
        WHERE doc.tenant_id = :tenantId
          AND doc.status IN ('PARTIAL', 'CONFLICT')
          AND ocm.is_consumed = FALSE
          AND (CAST(:branchId AS bigint) IS NULL OR w.branch_id = CAST(:branchId AS bigint))
        GROUP BY w.branch_id, doc.warehouse_id, m.id
        """)
    List<UnpostedConsumptionCostRow> findUnpostedCostRows(
        @Param("tenantId") Long tenantId,
        @Param("branchId") Long branchId
    );
}
