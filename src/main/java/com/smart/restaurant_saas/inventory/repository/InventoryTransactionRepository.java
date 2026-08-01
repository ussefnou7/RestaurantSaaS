package com.smart.restaurant_saas.inventory.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.smart.restaurant_saas.inventory.core.InventoryTransaction;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionDirection;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionType;
import com.smart.restaurant_saas.inventory.physicalcount.PhysicalCountMovementRow;
import com.smart.restaurant_saas.inventory.physicalcount.PostFreezeMovementSummary;

@Repository
public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    Optional<InventoryTransaction> findByTenantIdAndIdempotencyKey(Long tenantId, String idempotencyKey);

    @Query("""
        SELECT t FROM InventoryTransaction t
        WHERE t.reversesTransactionId = :originalTxId
        """)
    Optional<InventoryTransaction> findReversalOf(@Param("originalTxId") Long originalTxId);

    // original (non-reversal) transactions for a given source document
    @Query("""
        SELECT t FROM InventoryTransaction t
        WHERE t.tenantId = :tenantId
        AND t.referenceType = :referenceType
        AND t.referenceId = :referenceId
        AND t.reversesTransactionId IS NULL
        """)
    List<InventoryTransaction> findOriginalsByReference(
        @Param("tenantId") Long tenantId,
        @Param("referenceType") String referenceType,
        @Param("referenceId") Long referenceId
    );

    @Query("""
        SELECT CASE WHEN COUNT(t) > 0 THEN TRUE ELSE FALSE END
        FROM InventoryTransaction t
        WHERE t.tenantId = :tenantId
        AND t.referenceType = :referenceType
        AND t.referenceId = :referenceId
        """)
    boolean existsByReference(
        @Param("tenantId") Long tenantId,
        @Param("referenceType") String referenceType,
        @Param("referenceId") Long referenceId
    );

    @Query("""
            select transaction
            from InventoryTransaction transaction
            join fetch transaction.warehouse warehouse
            join fetch transaction.material material
            join fetch material.category category
            join fetch transaction.enteredUom enteredUom
            join fetch transaction.stockUom stockUom
            where transaction.tenantId = :tenantId
              and (:warehouseId is null or warehouse.id = :warehouseId)
              and (:materialId is null or material.id = :materialId)
              and (:categoryId is null or category.id = :categoryId)
              and (:transactionType is null or transaction.transactionType = :transactionType)
              and (:direction is null or transaction.direction = :direction)
              and (:dateFrom is null or transaction.transactionDate >= :dateFrom)
              and (:dateTo is null or transaction.transactionDate <= :dateTo)
              and (:referenceType is null or transaction.referenceType = :referenceType)
              and (
                  :search is null
                  or lower(warehouse.code) like :search
                  or lower(warehouse.name) like :search
                  or lower(warehouse.nameAr) like :search
                  or lower(material.code) like :search
                  or lower(material.name) like :search
                  or lower(material.nameAr) like :search
                  or lower(category.code) like :search
                  or lower(category.name) like :search
                  or lower(category.nameAr) like :search
                  or lower(transaction.referenceType) like :search
              )
            order by transaction.transactionDate desc, transaction.id desc
            """)
    List<InventoryTransaction> findByTenantIdAndFilters(
            @Param("tenantId") Long tenantId,
            @Param("warehouseId") Long warehouseId,
            @Param("materialId") Long materialId,
            @Param("categoryId") Long categoryId,
            @Param("transactionType") InventoryTransactionType transactionType,
            @Param("direction") InventoryTransactionDirection direction,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            @Param("referenceType") String referenceType,
            @Param("search") String search
    );

    // for reversal: the last valid (non-reversed) purchase per material in one query
    @Query("""
        SELECT t FROM InventoryTransaction t
        WHERE t.tenantId = :tenantId
        AND t.warehouse.id = :warehouseId
        AND t.material.id IN :materialIds
        AND t.transactionType = 'PURCHASE'
        AND t.direction = 'IN'
        AND t.id NOT IN (
            SELECT t2.reversesTransactionId
            FROM InventoryTransaction t2
            WHERE t2.reversesTransactionId IS NOT NULL
            AND t2.tenantId = :tenantId
        )
        ORDER BY t.transactionDate DESC
        """)
    List<InventoryTransaction> findLastValidPurchases(
        @Param("tenantId") Long tenantId,
        @Param("warehouseId") Long warehouseId,
        @Param("materialIds") List<Long> materialIds
    );

    /**
     * Fetches the stock-UOM ledger rows needed for one physical-count netting calculation. The query
     * covers the widest document window; each line's own inclusive upper bound is applied in memory.
     */
    @Query("""
        SELECT new com.smart.restaurant_saas.inventory.physicalcount.PhysicalCountMovementRow(
               t.material.id,
               CASE WHEN t.direction = 'IN'
                    THEN t.stockQuantity
                    ELSE -t.stockQuantity
               END,
               t.movementDate)
        FROM InventoryTransaction t
        WHERE t.tenantId = :tenantId
        AND t.warehouse.id = :warehouseId
        AND t.material.id IN :materialIds
        AND t.createdAt > :frozenAt
        AND t.movementDate <= :maxCutoff
        AND (
            t.referenceType IS NULL
            OR t.referenceType <> 'PHYSICAL_COUNT'
            OR t.referenceId IS NULL
            OR t.referenceId <> :countId
        )
        ORDER BY t.material.id ASC, t.movementDate ASC, t.id ASC
        """)
    List<PhysicalCountMovementRow> findPhysicalCountMovements(
        @Param("tenantId") Long tenantId,
        @Param("warehouseId") Long warehouseId,
        @Param("materialIds") List<Long> materialIds,
        @Param("frozenAt") LocalDateTime frozenAt,
        @Param("maxCutoff") LocalDateTime maxCutoff,
        @Param("countId") Long countId
    );

    /**
     * Warehouse-wide movement summary after a physical count's freeze cutoff, one row per material
     * that moved. It remains open-ended and informational; reconciliation uses the separate bounded
     * row query above. Reversals are counted like any other row, while this count's own corrections
     * are excluded so the explanation never feeds the document back into itself.
     */
    @Query("""
        SELECT m.id AS materialId,
               m.code AS materialCode,
               m.name AS materialName,
               m.nameAr AS materialNameAr,
               COUNT(t) AS movementCount,
               COALESCE(SUM(CASE WHEN t.direction = 'IN' THEN t.stockQuantity ELSE 0 END), 0) AS quantityIn,
               COALESCE(SUM(CASE WHEN t.direction = 'OUT' THEN t.stockQuantity ELSE 0 END), 0) AS quantityOut
        FROM InventoryTransaction t
        JOIN t.material m
        WHERE t.tenantId = :tenantId
        AND t.warehouse.id = :warehouseId
        AND t.createdAt > :frozenAt
        AND (
            t.referenceType IS NULL
            OR t.referenceType <> 'PHYSICAL_COUNT'
            OR t.referenceId IS NULL
            OR t.referenceId <> :countId
        )
        GROUP BY m.id, m.code, m.name, m.nameAr
        ORDER BY m.name ASC
        """)
    List<PostFreezeMovementSummary> summarizeMovementsAfterFreeze(
        @Param("tenantId") Long tenantId,
        @Param("warehouseId") Long warehouseId,
        @Param("frozenAt") LocalDateTime frozenAt,
        @Param("countId") Long countId
    );
}
