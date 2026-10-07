package com.smart.restaurant_saas.inventory.orderconsumption;

import com.smart.restaurant_saas.common.ScopedConditionAggregate;
import com.smart.restaurant_saas.tenant.TenantUnscoped;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderConsumptionRepository extends JpaRepository<OrderConsumption, Long> {

    @EntityGraph(attributePaths = "warehouse")
    @Query(
        value = """
            SELECT doc FROM OrderConsumption doc
            WHERE doc.tenantId = :tenantId
              AND (:warehouseId IS NULL OR doc.warehouse.id = :warehouseId)
              AND (:type IS NULL OR doc.type = :type)
              AND (:status IS NULL OR doc.status = :status)
              AND (CAST(:dateFrom AS timestamp) IS NULL OR doc.createdAt >= :dateFrom)
              AND (CAST(:dateToExclusive AS timestamp) IS NULL OR doc.createdAt < :dateToExclusive)
            """,
        countQuery = """
            SELECT COUNT(doc) FROM OrderConsumption doc
            WHERE doc.tenantId = :tenantId
              AND (:warehouseId IS NULL OR doc.warehouse.id = :warehouseId)
              AND (:type IS NULL OR doc.type = :type)
              AND (:status IS NULL OR doc.status = :status)
              AND (CAST(:dateFrom AS timestamp) IS NULL OR doc.createdAt >= :dateFrom)
              AND (CAST(:dateToExclusive AS timestamp) IS NULL OR doc.createdAt < :dateToExclusive)
            """
    )
    Page<OrderConsumption> findByFilters(
        @Param("tenantId") Long tenantId,
        @Param("warehouseId") Long warehouseId,
        @Param("type") OrderConsumptionType type,
        @Param("status") OrderConsumptionStatus status,
        @Param("dateFrom") LocalDateTime dateFrom,
        @Param("dateToExclusive") LocalDateTime dateToExclusive,
        Pageable pageable
    );

    @EntityGraph(attributePaths = "warehouse")
    Optional<OrderConsumption> findByIdAndTenantId(Long id, Long tenantId);

    Optional<OrderConsumption> findByTenantIdAndWarehouseIdAndTypeAndStatus(
        Long tenantId,
        Long warehouseId,
        OrderConsumptionType type,
        OrderConsumptionStatus status
    );

    /**
     * The warehouse's oldest doc in the caller-supplied unsettled statuses. At most one PENDING doc
     * exists per warehouse, but unresolved PARTIAL/CONFLICT docs accumulate, so ordering by id
     * surfaces the oldest blocker first.
     */
    Optional<OrderConsumption> findFirstByTenantIdAndWarehouseIdAndStatusInOrderByIdAsc(
        Long tenantId,
        Long warehouseId,
        Collection<OrderConsumptionStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT doc FROM OrderConsumption doc
        JOIN FETCH doc.warehouse warehouse
        WHERE doc.id = :id
          AND doc.tenantId = :tenantId
        """)
    Optional<OrderConsumption> findByIdAndTenantIdForUpdate(
        @Param("id") Long id,
        @Param("tenantId") Long tenantId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT doc FROM OrderConsumption doc
        JOIN FETCH doc.warehouse warehouse
        WHERE doc.id = :id
        """)
    @TenantUnscoped("System-scheduler entry point: id must come from a BatchingCandidate returned "
        + "by findBatchingCandidates, and the caller must use that candidate's tenantId rather "
        + "than any request-supplied tenant. Request-serving code must use "
        + "findByIdAndTenantIdForUpdate above.")
    Optional<OrderConsumption> findByIdForUpdate(@Param("id") Long id);

    /**
     * D58 dual-trigger candidates: PENDING docs that may have crossed EITHER threshold — line count
     * at or above {@code countThreshold}, OR oldest line older than {@code ageCutoff} (approximated
     * by the doc's creation time, since a doc is created when its first line lands). Runs across all
     * tenants (the batching scheduler is system-scoped).
     *
     * <p><b>Deliberately over-selects on the age arm.</b> {@code doc.createdAt} is stored in the
     * owning tenant's wall clock (D101), so a single cutoff cannot be exact for tenants in different
     * zones; the caller passes a cutoff widened by the supported offset spread and then re-checks
     * each row against its own tenant's clock. Over-selecting costs a discarded row, whereas
     * under-selecting would leave a doc unbatched for hours — so the slack goes that way on purpose.
     */
    @Query("""
        SELECT new com.smart.restaurant_saas.inventory.orderconsumption.BatchingCandidate(
                   doc.id,
                   doc.tenantId,
                   doc.createdAt,
                   (SELECT COUNT(line) FROM OrderConsumptionLine line WHERE line.doc = doc))
        FROM OrderConsumption doc
        WHERE doc.status = :status
          AND (doc.createdAt <= :ageCutoff
               OR (SELECT COUNT(line) FROM OrderConsumptionLine line WHERE line.doc = doc) >= :countThreshold)
        """)
    @TenantUnscoped("Intentionally cross-tenant: the batching scheduler is system-scoped. Each "
        + "candidate carries its own tenantId, and the caller must adopt that value for all "
        + "downstream work rather than assuming one tenant.")
    List<BatchingCandidate> findBatchingCandidates(
        @Param("status") OrderConsumptionStatus status,
        @Param("ageCutoff") LocalDateTime ageCutoff,
        @Param("countThreshold") long countThreshold
    );

    /**
     * Docs left IN_PROGRESS by an instance that died between claim and process. The poll only
     * selects PENDING, so without this their stock never leaves the ledger.
     */
    @Query("""
        SELECT doc.id FROM OrderConsumption doc
        WHERE doc.status = :status AND doc.updatedAt < :cutoff
        """)
    @TenantUnscoped("Cross-tenant like findBatchingCandidates: the scheduler is system-scoped.")
    List<Long> findStuckDocIds(
        @Param("status") OrderConsumptionStatus status,
        @Param("cutoff") LocalDateTime cutoff
    );

    /**
     * Dashboard alert A1: documents whose cost never left stock, one row per (branch, warehouse).
     *
     * <p>PARTIAL and CONFLICT are the two statuses that mean the posting stopped and will not
     * resume on its own. While such a document is open the sales it covers have revenue and no
     * cost, so gross profit is overstated by its value — which is why this is the one alert whose
     * presence invalidates every margin on the dashboard rather than just adding a row to it.
     * POSTED is finished and PENDING/IN_PROGRESS are on their way through the scheduler; neither
     * is a problem, and including them would make the alert fire continuously during normal
     * trading and so train the owner to ignore it.
     *
     * <p><b>{@code totalValue} is deliberately not computed here.</b> The cost of the stock that
     * did not move is {@code required_quantity × average_cost}, and those two live in different
     * UOM layers — the document's requirement is in the material's display UOM while the balance's
     * average cost is per stock UOM (D87/D88). Multiplying them in SQL would produce a number that
     * is wrong by the conversion factor and looks entirely plausible. The rule computes it from
     * {@code OrderConsumptionMaterialRepository.findUnpostedCostRows} instead, converting through
     * {@code UomConversionService} the way every other quantity in the system does.
     *
     * <p><b>Age is the document's {@code created_at}</b>, which is when its first line landed —
     * the same approximation {@link #findBatchingCandidates} already makes, and for the same
     * reason: the lines carry their own timestamps but the document's is the one that answers "how
     * long has this been stuck".
     *
     * <p>Grouped per warehouse rather than per branch because a warehouse is where the fix
     * happens, and never aggregated across warehouses (D99).
     */
    @Query(nativeQuery = true, value = """
        SELECT w.branch_id           AS "branchId",
               doc.warehouse_id      AS "warehouseId",
               COUNT(*)              AS "itemCount",
               CAST(NULL AS numeric) AS "totalValue",
               MIN(doc.created_at)   AS "oldestAt"
        FROM order_consumption doc
        JOIN warehouse w ON w.id = doc.warehouse_id
        WHERE doc.tenant_id = :tenantId
          AND doc.status IN ('PARTIAL', 'CONFLICT')
          AND (CAST(:branchId AS bigint) IS NULL OR w.branch_id = CAST(:branchId AS bigint))
        GROUP BY w.branch_id, doc.warehouse_id
        """)
    List<ScopedConditionAggregate> aggregateUnpostedConsumption(
        @Param("tenantId") Long tenantId,
        @Param("branchId") Long branchId
    );

    /**
     * Dashboard alert A5: PENDING documents older than the batching age trigger plus a grace hour.
     *
     * <p>A PENDING document is normal — it is the open batch collecting lines. One that has sat
     * past its own age trigger is not, because the scheduler fires on exactly that trigger (D58),
     * so the only way a document survives past it is that nothing is firing. The alert therefore
     * reports a broken scheduler rather than anything an operator did, which is why the caller
     * passes a cutoff already widened by the grace hour: a document a minute past the trigger is
     * a poll interval away from being picked up, and alerting on it would cry wolf once a minute.
     *
     * <p>Shares the age arm's reasoning with {@link #findBatchingCandidates} — {@code created_at}
     * stands in for the oldest line — but not its cross-tenant scope: this one runs for one
     * tenant, on request, so it takes that tenant's own clock and needs no offset slack.
     */
    @Query(nativeQuery = true, value = """
        SELECT w.branch_id           AS "branchId",
               doc.warehouse_id      AS "warehouseId",
               COUNT(*)              AS "itemCount",
               CAST(NULL AS numeric) AS "totalValue",
               MIN(doc.created_at)   AS "oldestAt"
        FROM order_consumption doc
        JOIN warehouse w ON w.id = doc.warehouse_id
        WHERE doc.tenant_id = :tenantId
          AND doc.status = 'PENDING'
          AND doc.created_at < :cutoff
          AND (CAST(:branchId AS bigint) IS NULL OR w.branch_id = CAST(:branchId AS bigint))
        GROUP BY w.branch_id, doc.warehouse_id
        """)
    List<ScopedConditionAggregate> aggregateOverduePendingConsumption(
        @Param("tenantId") Long tenantId,
        @Param("branchId") Long branchId,
        @Param("cutoff") LocalDateTime cutoff
    );

    /**
     * Dashboard: documents by status for the consumption-pipeline block, one row per status.
     *
     * <p>Unlike the two alert queries above this is a <em>census</em>, not a condition: it includes
     * POSTED and PENDING precisely because the owner is reading the shape of the pipeline rather
     * than a list of problems. A pipeline that is all POSTED is healthy; one with a growing
     * PENDING tail is a scheduler slowing down, and neither fact is visible from the alerts.
     *
     * <p>Windowed on {@code created_at} rather than {@code processed_at}: an unprocessed document
     * has no {@code processed_at}, and those are the rows the block exists to show.
     */
    @Query(nativeQuery = true, value = """
        SELECT doc.status          AS "status",
               COUNT(*)            AS "docCount",
               MIN(doc.created_at) AS "oldestAt"
        FROM order_consumption doc
        JOIN warehouse w ON w.id = doc.warehouse_id
        WHERE doc.tenant_id = :tenantId
          AND doc.created_at >= :fromInclusive
          AND doc.created_at <  :toExclusive
          AND (CAST(:branchId AS bigint) IS NULL OR w.branch_id = CAST(:branchId AS bigint))
        GROUP BY doc.status
        ORDER BY doc.status ASC
        """)
    List<ConsumptionStatusCount> aggregateByStatus(
        @Param("tenantId") Long tenantId,
        @Param("branchId") Long branchId,
        @Param("fromInclusive") LocalDateTime fromInclusive,
        @Param("toExclusive") LocalDateTime toExclusive
    );
}
