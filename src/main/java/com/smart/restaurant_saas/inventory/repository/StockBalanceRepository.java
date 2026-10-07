package com.smart.restaurant_saas.inventory.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    /**
     * Stock valuation report source rows: every balance of the tenant, optionally narrowed by
     * branch (via the warehouse's branch), warehouse, or material category. The branch join is a
     * LEFT JOIN on purpose — {@code Warehouse.branch} is nullable, and branch-less warehouses must
     * still appear when no branchId filter is supplied. Unbounded by design (bounded by material
     * count); see StockValuationReportService.
     *
     * <p>Restricted to active materials in active warehouses — retired stock must not inflate the
     * valuation total.
     */
    @Query("""
        SELECT sb FROM StockBalance sb
        JOIN FETCH sb.warehouse w
        LEFT JOIN w.branch b
        JOIN FETCH sb.material m
        JOIN FETCH m.category c
        WHERE sb.tenantId = :tenantId
          AND m.active = true
          AND w.active = true
          AND (:branchId IS NULL OR b.id = :branchId)
          AND (:warehouseId IS NULL OR w.id = :warehouseId)
          AND (:categoryId IS NULL OR c.id = :categoryId)
        ORDER BY w.name ASC, m.name ASC
        """)
    List<StockBalance> findForStockValuation(
        @Param("tenantId") Long tenantId,
        @Param("branchId") Long branchId,
        @Param("warehouseId") Long warehouseId,
        @Param("categoryId") Long categoryId
    );

    /**
     * Low-stock report source rows: same join shape and filters as
     * {@link #findForStockValuation}, narrowed to balances that have actually fallen below their
     * reorder minimum.
     *
     * <p>{@code minimum_quantity} is {@code NOT NULL DEFAULT 0} (V7), so "no minimum configured"
     * is stored as 0, not NULL — the {@code > 0} guard is what keeps those rows out. Without it,
     * a material with no minimum set would qualify the moment its quantity dipped below zero;
     * a CHECK constraint makes that impossible today, but the guard states the intent rather
     * than relying on it. No COALESCE anywhere: a missing minimum must never mean "low".
     */
    @Query("""
        SELECT sb FROM StockBalance sb
        JOIN FETCH sb.warehouse w
        LEFT JOIN w.branch b
        JOIN FETCH sb.material m
        JOIN FETCH m.category c
        WHERE sb.tenantId = :tenantId
          AND m.active = true
          AND w.active = true
          AND sb.minimumQuantity > 0
          AND sb.quantity < sb.minimumQuantity
          AND (:branchId IS NULL OR b.id = :branchId)
          AND (:warehouseId IS NULL OR w.id = :warehouseId)
          AND (:categoryId IS NULL OR c.id = :categoryId)
        ORDER BY w.name ASC, m.name ASC
        """)
    List<StockBalance> findForLowStock(
        @Param("tenantId") Long tenantId,
        @Param("branchId") Long branchId,
        @Param("warehouseId") Long warehouseId,
        @Param("categoryId") Long categoryId
    );

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

    /**
     * Dashboard KPI: the tenant's stock value, as one number.
     *
     * <p>The aggregate twin of {@link #findForStockValuation}, and it must stay predicate-identical
     * to it: same active-material-in-active-warehouse restriction, same LEFT JOIN to the branch so
     * a branch-less warehouse survives when no filter is given. {@code quantity × average_cost} is
     * the same expression the valuation report computes per row, summed here instead of in the
     * caller — a client-side sum would be a second definition of stock value, and the first time
     * the report gained a filter the two would part company.
     *
     * <p>Returns zero, not null, for a tenant with no balances: a tenant with nothing in stock
     * genuinely has zero stock value, unlike a ratio with no denominator.
     */
    @Query(nativeQuery = true, value = """
        SELECT COALESCE(SUM(sb.quantity * sb.average_cost), 0)
        FROM stock_balance sb
        JOIN warehouse w ON w.id = sb.warehouse_id
        JOIN material m  ON m.id = sb.material_id
        WHERE sb.tenant_id = :tenantId
          AND m.active = TRUE
          AND w.active = TRUE
          AND (CAST(:branchId AS bigint) IS NULL OR w.branch_id = CAST(:branchId AS bigint))
        """)
    BigDecimal sumStockValue(
        @Param("tenantId") Long tenantId,
        @Param("branchId") Long branchId
    );

    /**
     * Dashboard alerts B1 and B2: counts of out-of-stock and below-minimum materials per warehouse.
     *
     * <p><b>This query must stay predicate-identical to {@link #findForLowStock} except for the
     * split.</b> Same active-material-in-active-warehouse restriction, same {@code minimum > 0}
     * guard against treating "no minimum configured" as "low", same LEFT JOIN so a branch-less
     * warehouse survives when no branch filter is supplied. If the two drift, the dashboard says
     * 23 and the low-stock report lists 19, and the owner is right to stop trusting both — which
     * is why a reconciliation test pins the two counts against each other rather than leaving the
     * agreement to inspection.
     *
     * <p><b>One row per warehouse, two counts, never a total.</b> Stock minimums are per warehouse
     * and are not summable across them (D99): two kitchens each short of chicken is two purchase
     * decisions, and a single "9 materials low" hides which kitchen to call. The split into out
     * and low happens here rather than in two queries because the two conditions are one scan of
     * the same rows, and because a single row guarantees the two counts cannot be computed against
     * different snapshots.
     *
     * <p>No value on either: see {@code AlertCode}'s note on {@code hasAge} — a balance row records
     * neither what the shortfall is worth nor when it crossed the line.
     */
    @Query(nativeQuery = true, value = """
        SELECT w.branch_id   AS "branchId",
               sb.warehouse_id AS "warehouseId",
               COUNT(*) FILTER (WHERE sb.quantity <= 0) AS "outOfStockCount",
               COUNT(*) FILTER (WHERE sb.quantity >  0) AS "belowMinimumCount"
        FROM stock_balance sb
        JOIN warehouse w ON w.id = sb.warehouse_id
        JOIN material m  ON m.id = sb.material_id
        WHERE sb.tenant_id = :tenantId
          AND m.active = TRUE
          AND w.active = TRUE
          AND sb.minimum_quantity > 0
          AND sb.quantity < sb.minimum_quantity
          AND (CAST(:branchId AS bigint) IS NULL OR w.branch_id = CAST(:branchId AS bigint))
        GROUP BY w.branch_id, sb.warehouse_id
        """)
    List<LowStockCountProjection> aggregateLowStockCounts(
        @Param("tenantId") Long tenantId,
        @Param("branchId") Long branchId
    );

    /** Positive balances and their trailing stock-UOM consumption rate; convert before comparison (D87). */
    @Query(nativeQuery = true, value = """
        SELECT w.branch_id              AS "branchId",
               sb.warehouse_id          AS "warehouseId",
               m.id                     AS "materialId",
               m.name                   AS "materialName",
               m.name_ar                AS "materialNameAr",
               sb.quantity              AS "quantity",
               sb.uom_id                AS "balanceUomId",
               m.stock_uom_id           AS "stockUomId",
               used.consumed / CAST(:windowDays AS numeric) AS "dailyRate"
        FROM stock_balance sb
        JOIN warehouse w ON w.id = sb.warehouse_id
        JOIN material m  ON m.id = sb.material_id
        JOIN (
            SELECT t.warehouse_id, t.material_id, SUM(t.stock_quantity) AS consumed
            FROM inventory_transaction t
            WHERE t.tenant_id = :tenantId
              AND t.direction = 'OUT'
              AND t.reference_type = :consumptionReferenceType
              AND t.movement_date >= :since
              AND t.movement_date < :until
            GROUP BY t.warehouse_id, t.material_id
            HAVING SUM(t.stock_quantity) > 0
        ) used ON used.warehouse_id = sb.warehouse_id AND used.material_id = sb.material_id
        WHERE sb.tenant_id = :tenantId
          AND m.active = TRUE
          AND w.active = TRUE
          AND sb.quantity > 0
          AND (CAST(:branchId AS bigint) IS NULL OR w.branch_id = CAST(:branchId AS bigint))
        """)
    List<DaysOfCoverProjection> findConsumptionRates(
        @Param("tenantId") Long tenantId,
        @Param("branchId") Long branchId,
        @Param("consumptionReferenceType") String consumptionReferenceType,
        @Param("since") LocalDateTime since,
        @Param("until") LocalDateTime until,
        @Param("windowDays") int windowDays
    );

    /**
     * Out-of-stock and below-minimum counts for one warehouse. See
     * {@link #aggregateLowStockCounts}.
     *
     * <p>Not a {@code ScopedConditionAggregate}: this row carries two counts rather than one, and
     * squeezing them into that shape would mean either two queries over the same rows or a
     * meaningless {@code itemCount}.
     */
    interface LowStockCountProjection {

        Long getBranchId();

        Long getWarehouseId();

        /** Materials at or below zero — a configured minimum exists and stock has run out. */
        Long getOutOfStockCount();

        /** Materials under the minimum but still holding stock. */
        Long getBelowMinimumCount();
    }

    /** One material about to run out. See {@link #findConsumptionRates}. */
    interface DaysOfCoverProjection {

        Long getBranchId();

        Long getWarehouseId();

        Long getMaterialId();

        String getMaterialName();

        String getMaterialNameAr();

        Long getBalanceUomId();

        Long getStockUomId();

        /** On hand in the balance UOM. */
        BigDecimal getQuantity();

        /** Stock UOM per day over the trailing window. Never zero — the query excludes those. */
        BigDecimal getDailyRate();
    }
}
