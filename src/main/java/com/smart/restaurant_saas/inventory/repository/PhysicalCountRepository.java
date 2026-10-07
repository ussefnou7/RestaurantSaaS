package com.smart.restaurant_saas.inventory.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.smart.restaurant_saas.common.ScopedConditionAggregate;
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

    /**
     * Returns one row per conflicting (material, holding-count) pair: every material in
     * {@code materialIds} that is already frozen by a different IN_PROGRESS count in the same
     * warehouse. Single query — no N+1.
     */
    @Query("""
        SELECT l.material.id   AS materialId,
               l.material.name AS materialName,
               pc.id           AS countId,
               pc.code         AS countCode
        FROM PhysicalCount pc
        JOIN pc.lines l
        WHERE pc.tenantId   = :tenantId
          AND pc.warehouse.id = :warehouseId
          AND pc.status      = com.smart.restaurant_saas.inventory.core.enums.PhysicalCountStatus.IN_PROGRESS
          AND pc.id         <> :excludeId
          AND l.material.id IN :materialIds
        ORDER BY pc.id ASC, l.material.id ASC
        """)
    List<MaterialConflictProjection> findFreezeConflicts(
        @Param("tenantId")     Long tenantId,
        @Param("warehouseId")  Long warehouseId,
        @Param("excludeId")    Long excludeId,
        @Param("materialIds")  List<Long> materialIds);

    /**
     * Dashboard alert A3: counts frozen and abandoned, one row per (branch, warehouse).
     *
     * <p>A frozen count holds its warehouse's quantities at the freeze and blocks the materials it
     * covers from being counted again ({@link #findFreezeConflicts}). That is correct for the
     * hours a count takes and corrosive for the days this alert is about: the longer it stands,
     * the further the frozen figures drift from the shelf, so reconciling it later produces a
     * variance made mostly of trading that happened after the freeze.
     *
     * <p>Aged on {@code frozen_at} rather than {@code started_at} — the freeze is what blocks, and
     * a count can be started well before it is frozen.
     *
     * <p>No value: a count in progress has no variance yet, which is the entire reason it is still
     * in progress. Reporting one would be inventing it.
     */
    @Query(nativeQuery = true, value = """
        SELECT w.branch_id           AS "branchId",
               pc.warehouse_id       AS "warehouseId",
               COUNT(*)              AS "itemCount",
               CAST(NULL AS numeric) AS "totalValue",
               MIN(pc.frozen_at)     AS "oldestAt"
        FROM physical_count pc
        JOIN warehouse w ON w.id = pc.warehouse_id
        WHERE pc.tenant_id = :tenantId
          AND pc.status = 'IN_PROGRESS'
          AND pc.frozen_at IS NOT NULL
          AND pc.frozen_at < :frozenBefore
          AND (CAST(:branchId AS bigint) IS NULL OR w.branch_id = CAST(:branchId AS bigint))
        GROUP BY w.branch_id, pc.warehouse_id
        """)
    List<ScopedConditionAggregate> aggregateFrozenCounts(
        @Param("tenantId") Long tenantId,
        @Param("branchId") Long branchId,
        @Param("frozenBefore") LocalDateTime frozenBefore
    );

    /**
     * Dashboard alert C5: recently reconciled counts that crossed the large-variance threshold.
     *
     * <p><b>Reports gross exposure, not net impact.</b> {@code gross_variance_value} is the sum of
     * absolute per-line variances — how much stock moved unexplained — and it is what
     * {@code has_large_variance} is derived from. {@code large_variance_value} is the signed net,
     * which is the right figure against the books and the wrong one for a control alert: a count
     * 40,000 short on chicken and 40,000 long on rice nets to zero while being the most
     * interesting count of the month. Falling back to the net's absolute value covers counts
     * reconciled before V51, which recorded no gross.
     *
     * <p>Windowed on {@code reconciled_at} because the finding is the reconciliation, and bounded
     * to the last few days because an alert is a thing to act on now — the full history is the
     * shrinkage report's job, not the strip's.
     */
    @Query(nativeQuery = true, value = """
        SELECT w.branch_id              AS "branchId",
               pc.warehouse_id          AS "warehouseId",
               COUNT(*)                 AS "itemCount",
               COALESCE(SUM(COALESCE(pc.gross_variance_value,
                                     ABS(COALESCE(pc.large_variance_value, 0)))), 0)
                                        AS "totalValue",
               MIN(pc.reconciled_at)    AS "oldestAt"
        FROM physical_count pc
        JOIN warehouse w ON w.id = pc.warehouse_id
        WHERE pc.tenant_id = :tenantId
          AND pc.status = 'RECONCILED'
          AND pc.has_large_variance = TRUE
          AND pc.reconciled_at >= :reconciledSince
          AND (CAST(:branchId AS bigint) IS NULL OR w.branch_id = CAST(:branchId AS bigint))
        GROUP BY w.branch_id, pc.warehouse_id
        """)
    List<ScopedConditionAggregate> aggregateLargeVarianceCounts(
        @Param("tenantId") Long tenantId,
        @Param("branchId") Long branchId,
        @Param("reconciledSince") LocalDateTime reconciledSince
    );

    /**
     * When each warehouse last reconciled a count — the evidence behind "no count in 30 days".
     *
     * <p>Exists because its absence is the finding. A branch that never counts reports no
     * shrinkage and therefore looks spotless on every loss figure the dashboard can compute; the
     * less it records, the better it looks. Returning the last reconciliation date lets the screen
     * say "no count in 47 days" instead of "zero shrinkage", which are opposite facts.
     *
     * <p>Rows are per warehouse that has <em>ever</em> reconciled one. A warehouse absent from the
     * result has never had a count reconciled at all, and the caller must treat a missing row as
     * unknown rather than as recent.
     */
    @Query(nativeQuery = true, value = """
        SELECT w.branch_id            AS "branchId",
               pc.warehouse_id        AS "warehouseId",
               MAX(pc.reconciled_at)  AS "lastReconciledAt"
        FROM physical_count pc
        JOIN warehouse w ON w.id = pc.warehouse_id
        WHERE pc.tenant_id = :tenantId
          AND pc.status = 'RECONCILED'
          AND pc.reconciled_at IS NOT NULL
          AND (CAST(:branchId AS bigint) IS NULL OR w.branch_id = CAST(:branchId AS bigint))
        GROUP BY w.branch_id, pc.warehouse_id
        """)
    List<LastCountProjection> findLastReconciledPerWarehouse(
        @Param("tenantId") Long tenantId,
        @Param("branchId") Long branchId
    );

    /** When a warehouse last reconciled a count. See {@link #findLastReconciledPerWarehouse}. */
    interface LastCountProjection {

        Long getBranchId();

        Long getWarehouseId();

        LocalDateTime getLastReconciledAt();
    }
}
