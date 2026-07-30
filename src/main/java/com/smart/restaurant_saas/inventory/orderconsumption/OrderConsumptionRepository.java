package com.smart.restaurant_saas.inventory.orderconsumption;

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
              AND (:status IS NULL OR doc.status = :status)
              AND (CAST(:dateFrom AS timestamp) IS NULL OR doc.createdAt >= :dateFrom)
              AND (CAST(:dateToExclusive AS timestamp) IS NULL OR doc.createdAt < :dateToExclusive)
            """,
        countQuery = """
            SELECT COUNT(doc) FROM OrderConsumption doc
            WHERE doc.tenantId = :tenantId
              AND (:warehouseId IS NULL OR doc.warehouse.id = :warehouseId)
              AND (:status IS NULL OR doc.status = :status)
              AND (CAST(:dateFrom AS timestamp) IS NULL OR doc.createdAt >= :dateFrom)
              AND (CAST(:dateToExclusive AS timestamp) IS NULL OR doc.createdAt < :dateToExclusive)
            """
    )
    Page<OrderConsumption> findByFilters(
        @Param("tenantId") Long tenantId,
        @Param("warehouseId") Long warehouseId,
        @Param("status") OrderConsumptionStatus status,
        @Param("dateFrom") LocalDateTime dateFrom,
        @Param("dateToExclusive") LocalDateTime dateToExclusive,
        Pageable pageable
    );

    @EntityGraph(attributePaths = "warehouse")
    Optional<OrderConsumption> findByIdAndTenantId(Long id, Long tenantId);

    Optional<OrderConsumption> findByTenantIdAndWarehouseIdAndStatus(
        Long tenantId,
        Long warehouseId,
        OrderConsumptionStatus status
    );

    /**
     * The warehouse's oldest doc that has not reached POSTED. At most one PENDING doc exists per
     * warehouse, but unresolved CONFLICT docs accumulate, so ordering by id surfaces the oldest
     * blocker first — an unresolved conflict means stock for those materials was never deducted.
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
    Optional<OrderConsumption> findByIdForUpdate(@Param("id") Long id);

    /**
     * D58 dual-trigger: doc ids whose PENDING doc has crossed EITHER threshold — line count at or
     * above {@code countThreshold}, OR oldest line older than {@code ageCutoff} (approximated by the
     * doc's creation time, since a doc is created when its first line lands). Runs across all tenants
     * (the batching scheduler is system-scoped).
     */
    @Query("""
        SELECT doc.id FROM OrderConsumption doc
        WHERE doc.status = :status
          AND (doc.createdAt <= :ageCutoff
               OR (SELECT COUNT(line) FROM OrderConsumptionLine line WHERE line.doc = doc) >= :countThreshold)
        """)
    List<Long> findDocIdsReadyForBatching(
        @Param("status") OrderConsumptionStatus status,
        @Param("ageCutoff") LocalDateTime ageCutoff,
        @Param("countThreshold") long countThreshold
    );

}
