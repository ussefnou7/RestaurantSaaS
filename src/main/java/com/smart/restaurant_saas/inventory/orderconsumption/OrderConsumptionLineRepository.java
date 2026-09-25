package com.smart.restaurant_saas.inventory.orderconsumption;

import com.smart.restaurant_saas.tenant.TenantUnscoped;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderConsumptionLineRepository extends JpaRepository<OrderConsumptionLine, Long> {

    @Query("""
        SELECT line.orderLine.id
        FROM OrderConsumptionLine line
        WHERE line.orderLine.id IN :orderLineIds
        """)
    @TenantUnscoped("orderLineIds must belong to orders already resolved for the acting tenant; "
        + "the query matches order-line ids across all tenants.")
    List<Long> findExistingOrderLineIds(@Param("orderLineIds") List<Long> orderLineIds);

    @Query("""
        SELECT ol.recipe.id AS recipeId, SUM(ol.quantity) AS quantity
        FROM OrderConsumptionLine line
        JOIN line.orderLine ol
        WHERE line.doc.id = :docId
        GROUP BY ol.recipe.id
        """)
    @TenantUnscoped("docId must be a consumption doc already loaded for the acting tenant; the "
        + "query scopes through line.doc.id only.")
    List<RecipeQuantity> sumRecipeQuantitiesByDocId(@Param("docId") Long docId);

    /**
     * Recipe totals of the warehouse's PENDING docs — the only status whose requirement is still
     * computed on the fly, because a PENDING doc has not been aggregated yet and so has no
     * material rows. Once a doc is processed its outstanding quantity comes from
     * {@link OrderConsumptionMaterialRepository#sumUnconsumedRequiredQuantitiesByWarehouse}, at
     * the per-material grain consumption actually happens at.
     */
    @Query("""
        SELECT ol.recipe.id AS recipeId, SUM(ol.quantity) AS quantity
        FROM OrderConsumptionLine line
        JOIN line.doc doc
        JOIN line.orderLine ol
        WHERE doc.tenantId = :tenantId
          AND doc.warehouse.id = :warehouseId
          AND doc.status = :status
        GROUP BY ol.recipe.id
        """)
    List<RecipeQuantity> sumPendingRecipeQuantitiesByWarehouse(
        @Param("tenantId") Long tenantId,
        @Param("warehouseId") Long warehouseId,
        @Param("status") OrderConsumptionStatus status
    );

    @Query("""
        SELECT line.doc.id AS docId, COUNT(line.id) AS lineCount
        FROM OrderConsumptionLine line
        WHERE line.doc.id IN :docIds
        GROUP BY line.doc.id
        """)
    @TenantUnscoped("docIds must be consumption docs already loaded for the acting tenant; the "
        + "query scopes through line.doc.id only.")
    List<DocLineCount> countLinesByDocIds(@Param("docIds") List<Long> docIds);

    @Query("""
        SELECT line.id AS id,
               line.orderLine.order.id AS orderId,
               line.orderLine.order.createdBy AS createdBy,
               line.orderLine.lineType AS lineType,
               line.orderLine.wasteStage AS wasteStage
        FROM OrderConsumptionLine line
        WHERE line.doc.id = :docId
        ORDER BY line.id ASC
        """)
    @TenantUnscoped("docId must be a consumption doc already loaded for the acting tenant; the "
        + "query scopes through line.doc.id only. Compare summarizeMaterialsByDocId below, which "
        + "takes tenantId as well.")
    List<OrderConsumptionLineView> findLinesByDocId(@Param("docId") Long docId);

    @Query("""
        SELECT item.material.id AS materialId,
               item.material.name AS materialName,
               item.material.nameAr AS materialNameAr,
               item.uom.symbol AS uom,
               SUM(item.quantity * orderLine.quantity) AS totalQtyConsumed,
               COUNT(DISTINCT orderLine.order.id) AS orderCount
        FROM OrderConsumptionLine line
        JOIN line.orderLine orderLine
        JOIN RecipeItem item ON item.recipe.id = orderLine.recipe.id
        WHERE line.doc.id = :docId
          AND line.doc.tenantId = :tenantId
          AND item.tenantId = :tenantId
        GROUP BY item.material.id, item.material.name, item.material.nameAr, item.uom.symbol
        ORDER BY item.material.name ASC
        """)
    List<MaterialSummary> summarizeMaterialsByDocId(
        @Param("docId") Long docId,
        @Param("tenantId") Long tenantId
    );
}
