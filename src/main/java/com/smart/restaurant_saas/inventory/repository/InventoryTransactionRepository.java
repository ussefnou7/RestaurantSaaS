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
import com.smart.restaurant_saas.inventory.physicalcount.PhysicalCountMovementReference;
import com.smart.restaurant_saas.inventory.physicalcount.PostFreezeMovementSummary;
import com.smart.restaurant_saas.inventory.purchase.dto.BackdatedConsumptionCheckResponse;
import com.smart.restaurant_saas.inventory.reports.ShrinkageAggregate;

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
     * Invoice materials whose stock was already consumed on a <b>later calendar day</b> than the
     * invoice's receipt date — backdating the receipt would insert a new batch ahead of that
     * consumption and change which batch it drew from (D10).
     *
     * <p><b>The boundary is a day, not an instant.</b> A purchase movement is stamped
     * {@code receiptDate.atStartOfDay()}, so a same-day consumption carrying a real clock time
     * always sorts after it — but D10 breaks that tie on {@code id}, not on time, so the receipt
     * lands ahead of the consumption regardless and nothing about the batch selection changes.
     * Warning on a same-day consumption would describe a reordering that cannot happen. Callers
     * therefore pass the start of the day <em>after</em> the receipt date, and the predicate is
     * inclusive of it: a conflict is a consumption dated 00:00 on receiptDate + 1 or later.
     *
     * <p>The transaction types are an explicit inclusion list, not "everything with direction OUT".
     * Only a movement that FIFO-consumes can have its batch selection changed by a backdated
     * receipt, so only those types warrant the warning. Membership is deliberately opt-in: a new
     * outbound type must be added here consciously rather than qualifying by default. The included
     * types mirror {@code StockBatchService.CONSUMING_TYPES} minus {@code TRANSFER_OUT}, which is
     * in that set but has no writer yet — whoever implements warehouse transfers should add it here
     * if a transfer out is to count as consumption for this warning.
     *
     * <p>Notable outbound movements that are excluded because they do not FIFO-consume:
     * <ul>
     *   <li>{@code PURCHASE_RETURN} — direction OUT, but depletes its own source invoice's batch
     *       specifically (D9), never oldest-first, so no receipt date can affect it.</li>
     *   <li>Reversals — a reversal keeps the original's type and flips its direction, and depletes
     *       the source batch instead of FIFO ({@code StockBatchService.consumes}). The
     *       {@code reversesTransactionId IS NULL} guard drops them, which covers both a purchase
     *       reversal (type {@code PURCHASE}, direction OUT) and a reversed physical-count surplus
     *       (type {@code COUNT_ADJUSTMENT}, direction OUT).</li>
     * </ul>
     *
     * <p>The direction filter is still required: {@code COUNT_ADJUSTMENT} is bidirectional and only
     * consumes on a shortage.
     */
    @Query("""
        SELECT new com.smart.restaurant_saas.inventory.purchase.dto.BackdatedConsumptionCheckResponse(
               t.material.id,
               t.material.name,
               t.material.nameAr,
               MAX(t.movementDate))
        FROM InventoryTransaction t
        WHERE t.tenantId = :tenantId
        AND t.warehouse.id = :warehouseId
        AND t.material.id IN :materialIds
        AND t.direction = 'OUT'
        AND t.transactionType IN ('CONSUMPTION_SUMMARY', 'MANUAL_CONSUMPTION',
                                  'WASTE', 'COUNT_ADJUSTMENT')
        AND t.reversesTransactionId IS NULL
        AND t.movementDate >= :dayAfterReceipt
        GROUP BY t.material.id, t.material.name, t.material.nameAr
        ORDER BY t.material.name ASC
        """)
    List<BackdatedConsumptionCheckResponse> findBackdatedConsumptionConflicts(
        @Param("tenantId") Long tenantId,
        @Param("warehouseId") Long warehouseId,
        @Param("materialIds") List<Long> materialIds,
        @Param("dayAfterReceipt") LocalDateTime dayAfterReceipt
    );

    /**
     * Fetches the stock-UOM ledger rows needed for one physical-count netting calculation. The query
     * covers the widest document window; each line's own inclusive upper bound is applied in memory.
     */
    @Query("""
        SELECT new com.smart.restaurant_saas.inventory.physicalcount.PhysicalCountMovementRow(
               t.id,
               t.material.id,
               CASE WHEN t.direction = 'IN'
                    THEN t.stockQuantity
                    ELSE -t.stockQuantity
               END,
               t.direction,
               t.movementDate,
               t.createdAt,
               t.referenceType,
               t.referenceId)
        FROM InventoryTransaction t
        WHERE t.tenantId = :tenantId
        AND t.warehouse.id = :warehouseId
        AND t.material.id IN :materialIds
        AND t.createdAt > :frozenAt
        AND (:includeAfterCutoff = TRUE OR t.movementDate <= :maxCutoff)
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
        @Param("includeAfterCutoff") boolean includeAfterCutoff,
        @Param("countId") Long countId
    );

    default List<PhysicalCountMovementRow> findPhysicalCountMovements(
            Long tenantId,
            Long warehouseId,
            List<Long> materialIds,
            LocalDateTime frozenAt,
            LocalDateTime maxCutoff,
            Long countId) {
        return findPhysicalCountMovements(
            tenantId, warehouseId, materialIds, frozenAt, maxCutoff, false, countId);
    }

    /**
     * Shrinkage report source: net physical-count variance per material over a movement-date window,
     * in <b>stock UOM</b> (D87 layer 1). Read-only; nothing here writes (D4).
     *
     * <p><b>Signs are carried by direction, never by ABS.</b> A count surplus is IN and a shortage is
     * OUT, so summing {@code ±stockQuantity} and {@code ±totalCost} nets a material that had both
     * into one honest figure. Summing absolute values would report a material that came up 5 short
     * and 5 over as a 10-unit problem instead of the non-event it is.
     *
     * <p><b>Opening balances cannot leak in</b>: {@code OpeningBalanceService} sets no
     * {@code referenceType}, so those rows are NULL-referenced and the equality predicate drops
     * them. Without that, a warehouse's entire opening stock would read as one enormous shortage.
     *
     * <p><b>No {@code reversesTransactionId IS NULL} guard, deliberately.</b> Reconcile is terminal
     * (D89) and {@code ledgerService.reverse} has only purchase-side callers, so a reversed count
     * row cannot exist today. If one ever did, the signed sum would net it to zero — which is
     * correct. Filtering reversals out would instead keep the original and drop its undo, reporting
     * a loss that never happened.
     *
     * <p>{@code totalCost} is COALESCEd to zero rather than dropped: a cost-less row is still a real
     * physical variance and must keep contributing its quantity and its movement count. This is also
     * why {@code negativesOnly} filters on net <b>quantity</b> while the sort is by net
     * <b>value</b> — quantity is NOT NULL at the column level, so it has no hole a genuine shortage
     * could slip through.
     */
    @Query("""
        SELECT new com.smart.restaurant_saas.inventory.reports.ShrinkageAggregate(
               m.id,
               m.code,
               m.name,
               m.nameAr,
               SUM(CASE WHEN t.direction = 'IN' THEN t.stockQuantity ELSE -t.stockQuantity END),
               SUM(CASE WHEN t.direction = 'IN' THEN COALESCE(t.totalCost, 0) ELSE -COALESCE(t.totalCost, 0) END),
               COUNT(t))
        FROM InventoryTransaction t
        JOIN t.material m
        JOIN t.warehouse w
        WHERE t.tenantId = :tenantId
          AND t.referenceType = :referenceType
          AND t.movementDate >= :fromInclusive
          AND t.movementDate < :toExclusive
          AND m.active = true
          AND w.active = true
          AND (:warehouseId IS NULL OR w.id = :warehouseId)
          AND (:categoryId IS NULL OR m.category.id = :categoryId)
        GROUP BY m.id, m.code, m.name, m.nameAr
        HAVING (:negativesOnly = FALSE
                OR SUM(CASE WHEN t.direction = 'IN' THEN t.stockQuantity ELSE -t.stockQuantity END) < 0)
        ORDER BY ABS(SUM(CASE WHEN t.direction = 'IN' THEN COALESCE(t.totalCost, 0) ELSE -COALESCE(t.totalCost, 0) END)) DESC,
                 m.name ASC
        """)
    List<ShrinkageAggregate> aggregateShrinkage(
        @Param("tenantId") Long tenantId,
        @Param("referenceType") String referenceType,
        @Param("fromInclusive") LocalDateTime fromInclusive,
        @Param("toExclusive") LocalDateTime toExclusive,
        @Param("warehouseId") Long warehouseId,
        @Param("categoryId") Long categoryId,
        @Param("negativesOnly") boolean negativesOnly
    );

    @Query(value = """
        SELECT tx.id AS "transactionId",
               COALESCE(invoice.invoice_number,
                        purchase_return.return_number,
                        waste.code,
                        physical_count.code) AS "referenceCode"
        FROM inventory_transaction tx
        LEFT JOIN purchase_invoice invoice
          ON tx.reference_type = 'PURCHASE_INVOICE'
         AND invoice.id = tx.reference_id
         AND invoice.tenant_id = tx.tenant_id
        LEFT JOIN purchase_return purchase_return
          ON tx.reference_type = 'PURCHASE_RETURN'
         AND purchase_return.id = tx.reference_id
         AND purchase_return.tenant_id = tx.tenant_id
        LEFT JOIN waste_document waste
          ON tx.reference_type = 'WASTE_DOCUMENT'
         AND waste.id = tx.reference_id
         AND waste.tenant_id = tx.tenant_id
        LEFT JOIN physical_count physical_count
          ON tx.reference_type = 'PHYSICAL_COUNT'
         AND physical_count.id = tx.reference_id
         AND physical_count.tenant_id = tx.tenant_id
        WHERE tx.id IN (:transactionIds)
        """, nativeQuery = true)
    List<PhysicalCountMovementReference> findPhysicalCountMovementReferences(
        @Param("transactionIds") List<Long> transactionIds
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
