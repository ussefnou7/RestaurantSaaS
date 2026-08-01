package com.smart.restaurant_saas.inventory.orderconsumption;

import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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
    List<Long> findExistingOrderLineIds(@Param("orderLineIds") List<Long> orderLineIds);

    @Query("""
        SELECT ol.recipe.id AS recipeId, SUM(ol.quantity) AS quantity
        FROM OrderConsumptionLine line
        JOIN line.orderLine ol
        WHERE line.doc.id = :docId
        GROUP BY ol.recipe.id
        """)
    List<RecipeQuantity> sumRecipeQuantitiesByDocId(@Param("docId") Long docId);

    @Query("""
        SELECT ol.recipe.id AS recipeId, SUM(ol.quantity) AS quantity
        FROM OrderConsumptionLine line
        JOIN line.doc doc
        JOIN line.orderLine ol
        WHERE doc.tenantId = :tenantId
          AND doc.warehouse.id = :warehouseId
          AND (doc.status = :pendingStatus
               OR (doc.status = :partialStatus AND line.consumed = false))
        GROUP BY ol.recipe.id
        """)
    List<RecipeQuantity> sumOutstandingRecipeQuantitiesByWarehouse(
        @Param("tenantId") Long tenantId,
        @Param("warehouseId") Long warehouseId,
        @Param("pendingStatus") OrderConsumptionStatus pendingStatus,
        @Param("partialStatus") OrderConsumptionStatus partialStatus
    );

    @Query("""
        SELECT line.doc.id AS docId, COUNT(line.id) AS lineCount
        FROM OrderConsumptionLine line
        WHERE line.doc.id IN :docIds
        GROUP BY line.doc.id
        """)
    List<DocLineCount> countLinesByDocIds(@Param("docIds") List<Long> docIds);

    @Query("""
        SELECT line.id AS id,
               line.orderLine.order.id AS orderId,
               line.orderLine.order.createdBy AS createdBy,
               line.consumed AS consumed
        FROM OrderConsumptionLine line
        WHERE line.doc.id = :docId
        ORDER BY line.id ASC
        """)
    List<OrderConsumptionLineView> findLinesByDocId(@Param("docId") Long docId);

    @Query("""
        SELECT item.material.id AS materialId,
               item.material.name AS materialName,
               item.uom.symbol AS uom,
               SUM(item.quantity * orderLine.quantity) AS totalQtyConsumed,
               COUNT(DISTINCT orderLine.order.id) AS orderCount
        FROM OrderConsumptionLine line
        JOIN line.orderLine orderLine
        JOIN RecipeItem item ON item.recipe.id = orderLine.recipe.id
        WHERE line.doc.id = :docId
          AND line.doc.tenantId = :tenantId
          AND item.tenantId = :tenantId
        GROUP BY item.material.id, item.material.name, item.uom.symbol
        ORDER BY item.material.name ASC
        """)
    List<MaterialSummary> summarizeMaterialsByDocId(
        @Param("docId") Long docId,
        @Param("tenantId") Long tenantId
    );

    @Modifying
    @Query("""
        UPDATE OrderConsumptionLine line
        SET line.consumed = :consumed
        WHERE line.doc.id = :docId
        """)
    int updateConsumedByDocId(@Param("docId") Long docId, @Param("consumed") boolean consumed);

    @Modifying
    @Query(value = """
        UPDATE order_consumption_line line
        SET is_consumed = TRUE
        FROM order_line order_line
        WHERE line.doc_id = :docId
          AND line.order_line_id = order_line.id
          AND NOT EXISTS (
              SELECT 1
              FROM recipe_item item
              WHERE item.recipe_id = order_line.recipe_id
                AND item.material_id IN (:unavailableMaterialIds)
          )
        """, nativeQuery = true)
    int markConsumedLinesWithoutUnavailableMaterials(
        @Param("docId") Long docId,
        @Param("unavailableMaterialIds") Set<Long> unavailableMaterialIds
    );
}
