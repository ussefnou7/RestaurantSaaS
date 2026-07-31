package com.smart.restaurant_saas.inventory.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.smart.restaurant_saas.inventory.batch.StockBatch;
import com.smart.restaurant_saas.inventory.core.enums.StockBatchStatus;

@Repository
public interface StockBatchRepository extends JpaRepository<StockBatch, Long> {

    /**
     * All batches (OPEN and CLOSED) of a balance in FIFO order. The business movement date
     * leads; the generated id provides deterministic ordering for batches with the same date.
     * Tenant scoping is enforced by the caller via the balance ownership check.
     */
    List<StockBatch> findByStockBalanceIdOrderByMovementDateAscIdAsc(Long stockBalanceId);

    /**
     * Open batches of a balance in FIFO order (movement date first, then id). Drives
     * {@link com.smart.restaurant_saas.inventory.core.StockBatchService#consumeFifo};
     * served directly by idx_stock_batch_open_fifo
     * (stock_balance_id, status, movement_date, id).
     */
    List<StockBatch> findByStockBalanceIdAndStatusOrderByMovementDateAscIdAsc(
        Long stockBalanceId, StockBatchStatus status);

    /**
     * Finds the single batch opened by a specific purchase invoice line (OPEN or CLOSED).
     * Used by purchase-return posting to locate the exact source batch to deplete, rather
     * than FIFO-consuming oldest-first.
     */
    Optional<StockBatch> findByStockBalanceIdAndSourceInvoiceLineId(Long stockBalanceId,
                                                                     Long sourceInvoiceLineId);

    Optional<StockBatch> findByTenantIdAndSourceTransactionId(Long tenantId, Long sourceTransactionId);

    /**
     * Batches opened by a purchase invoice. Used by unpost as an all-or-nothing guard before
     * generating reversals.
     */
    @Query("""
        SELECT batch
        FROM StockBatch batch
        JOIN FETCH batch.stockBalance balance
        JOIN FETCH balance.material material
        WHERE batch.tenantId = :tenantId
          AND batch.sourceInvoiceId = :invoiceId
        ORDER BY batch.id ASC
        """)
    List<StockBatch> findOpenedByPurchaseInvoice(
        @Param("tenantId") Long tenantId,
        @Param("invoiceId") Long invoiceId
    );

    /**
     * Aggregates a balance's OPEN batches into its total remaining quantity and total value,
     * from which the balance's average cost is derived (value / remaining). Filtered to
     * {@code status = OPEN AND remainingQuantity > 0} as a required part of the query: a closed
     * batch contributes exactly zero to both sums, so excluding historical batches keeps the
     * calculation cheap no matter how many closed batches accumulate over a tenant's lifetime.
     *
     * <p>A null {@code unitCost} is coalesced to zero so null-cost stock still counts toward the
     * remaining quantity (denominator) — mirroring FIFO consumption, which values a null-cost
     * batch at zero. Returns null sums when the balance has no open batches.
     *
     * <p>Served by idx_stock_batch_open_fifo
     * (stock_balance_id, status, movement_date, id).
     */
    @Query("""
        SELECT new com.smart.restaurant_saas.inventory.repository.OpenBatchTotals(
                   SUM(b.remainingQuantity),
                   SUM(b.remainingQuantity * COALESCE(b.unitCost, 0)))
        FROM StockBatch b
        WHERE b.stockBalance.id = :balanceId
          AND b.status = com.smart.restaurant_saas.inventory.core.enums.StockBatchStatus.OPEN
          AND b.remainingQuantity > 0
        """)
    OpenBatchTotals sumOpenBatchTotals(@Param("balanceId") Long balanceId);
}
