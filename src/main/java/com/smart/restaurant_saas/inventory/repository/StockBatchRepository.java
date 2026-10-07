package com.smart.restaurant_saas.inventory.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.smart.restaurant_saas.inventory.batch.StockBatch;
import com.smart.restaurant_saas.inventory.core.enums.StockBatchStatus;
import com.smart.restaurant_saas.inventory.reports.PurchasePriceDriftAggregate;
import com.smart.restaurant_saas.tenant.TenantUnscoped;

@Repository
public interface StockBatchRepository extends JpaRepository<StockBatch, Long> {

    /**
     * All batches (OPEN and CLOSED) of a balance in FIFO order. The business movement date
     * leads; the generated id provides deterministic ordering for batches with the same date.
     * Tenant scoping is enforced by the caller via the balance ownership check.
     */
    @TenantUnscoped("stockBalanceId must be a balance already loaded for the acting tenant, e.g. "
        + "via StockBalanceRepository.findByIdAndTenantId.")
    List<StockBatch> findByStockBalanceIdOrderByMovementDateAscIdAsc(Long stockBalanceId);

    /**
     * Open batches of a balance in FIFO order (movement date first, then id). Drives
     * {@link com.smart.restaurant_saas.inventory.core.StockBatchService#consumeFifo};
     * served directly by idx_stock_batch_open_fifo
     * (stock_balance_id, status, movement_date, id).
     */
    @TenantUnscoped("stockBalanceId must be a balance already loaded for the acting tenant. This "
        + "one feeds FIFO consumption, so an unscoped balance id would deplete another tenant's "
        + "stock.")
    List<StockBatch> findByStockBalanceIdAndStatusOrderByMovementDateAscIdAsc(
        Long stockBalanceId, StockBatchStatus status);

    /**
     * Finds the single batch opened by a specific purchase invoice line (OPEN or CLOSED).
     * Used by purchase-return posting to locate the exact source batch to deplete, rather
     * than FIFO-consuming oldest-first.
     */
    @TenantUnscoped("stockBalanceId must be a balance already loaded for the acting tenant; "
        + "sourceInvoiceLineId alone carries no tenant.")
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
    @TenantUnscoped("balanceId must be a balance already loaded for the acting tenant; the sums "
        + "are taken over b.stockBalance.id alone.")
    OpenBatchTotals sumOpenBatchTotals(@Param("balanceId") Long balanceId);

    /**
     * Purchase price drift report source: the first and last purchase price of each material inside
     * a movement-date window, with the change between them. Read-only.
     *
     * <p><b>Prices are NOT converted and must not be.</b> {@code stock_batch.unit_cost} is already
     * per the material's display UOM (D87 layer 2). This is the one report in the module whose
     * source is not the stock-UOM ledger, so the conversion-and-degrade logic the shrinkage and
     * waste reports need has nothing to do here. The UOM is joined only to label the unit the price
     * is per (D88's intent), and the material's <em>current</em> display UOM is used because a batch
     * carries no unit of its own — a material whose display UOM was changed after these purchases
     * would be labelled with the new unit, which is the closest available truth.
     *
     * <p><b>First and last are by {@code id}, not by date.</b> Two purchases received on the same
     * day at different prices have identical {@code movement_date} (purchases are stamped
     * {@code receiptDate.atStartOfDay()}), so a date ordering would pick between them arbitrarily.
     * The batch table is insertion-ordered and FIFO already breaks exactly this tie on {@code id}
     * (D10), so this matches how the rest of the system reads the same rows. {@code movement_date}
     * still drives the window filter and the displayed dates.
     *
     * <p><b>Purchase-origin batches only.</b> {@code source_invoice_id} is set only when the opening
     * transaction carried {@code referenceType = 'PURCHASE_INVOICE'}
     * ({@code StockBatchService.createBatchFromInbound}), so {@code IS NOT NULL} is exactly that
     * filter. It excludes the other three batch-opening origins — opening balances, transfers in,
     * and physical-count surpluses. The surplus case matters most: those batches are valued at the
     * balance's current average cost (D89), so including them would register an average as a
     * purchase price and invent drift that never happened.
     *
     * <p><b>Reversed purchases are excluded too.</b> Cancelling or unposting an invoice reverses the
     * ledger, and {@code StockBatchService.reverseSourceBatchIfOpened} depletes the batch to zero and
     * closes it — it does not delete the row and does not clear {@code unit_cost}. A mistyped price
     * that was entered and then cancelled would otherwise survive as a real price point. The
     * {@code NOT EXISTS} guard drops those. (Purchase <em>returns</em> need no such guard: they only
     * reduce {@code remaining_quantity} and never touch {@code unit_cost}, so a returned purchase
     * keeps its true price — it did happen at that price.)
     *
     * <p>Percentage is computed here rather than in the service so the value that is sorted on and
     * the value that is rendered are the same expression. A zero or absent first price yields null,
     * never infinity and never a fabricated zero — hence {@code NULLS LAST}.
     *
     * <p>Served by idx_stock_batch_tenant_purchase_movement_date
     * (tenant_id, movement_date) WHERE source_invoice_id IS NOT NULL (V42).
     *
     * <p><b>{@code branchId} is for the dashboard, and the report passes null.</b> The alert strip
     * needs the same drift figure narrowed to the caller's branch scope, and a branch-scoped user
     * must not see another branch's purchase prices through the dashboard when the purchase screen
     * hides them. Widening this method rather than writing a second one is the point: the
     * exclusions that make the figure correct — non-purchase batch origins, reversed invoices —
     * are subtle enough that a second copy would be the one that loses them.
     */
    @Query(value = """
        WITH purchases AS (
            SELECT balance.material_id                                        AS material_id,
                   (array_agg(batch.unit_cost     ORDER BY batch.id ASC))[1]  AS first_price,
                   (array_agg(batch.movement_date ORDER BY batch.id ASC))[1]  AS first_purchase_date,
                   (array_agg(batch.unit_cost     ORDER BY batch.id DESC))[1] AS last_price,
                   (array_agg(batch.movement_date ORDER BY batch.id DESC))[1] AS last_purchase_date,
                   COUNT(*)                                                   AS purchase_count
            FROM stock_batch batch
            JOIN stock_balance balance ON balance.id = batch.stock_balance_id
            JOIN material material     ON material.id = balance.material_id
            JOIN warehouse warehouse   ON warehouse.id = balance.warehouse_id
            LEFT JOIN purchase_invoice invoice
                   ON invoice.id = batch.source_invoice_id
                  AND invoice.tenant_id = batch.tenant_id
            WHERE batch.tenant_id = :tenantId
              AND batch.source_invoice_id IS NOT NULL
              AND batch.movement_date >= :fromInclusive
              AND batch.movement_date <  :toExclusive
              AND (CAST(:branchId    AS bigint) IS NULL OR warehouse.branch_id   = CAST(:branchId    AS bigint))
              AND (CAST(:warehouseId AS bigint) IS NULL OR balance.warehouse_id = CAST(:warehouseId AS bigint))
              AND (CAST(:categoryId  AS bigint) IS NULL OR material.category_id = CAST(:categoryId  AS bigint))
              AND (CAST(:supplierId  AS bigint) IS NULL OR invoice.supplier_id  = CAST(:supplierId  AS bigint))
              AND NOT EXISTS (
                  SELECT 1 FROM inventory_transaction reversal
                  WHERE reversal.tenant_id = batch.tenant_id
                    AND reversal.reverses_transaction_id = batch.source_transaction_id
              )
            GROUP BY balance.material_id
        ),
        computed AS (
            SELECT purchases.*,
                   (purchases.last_price - purchases.first_price) AS price_change,
                   CASE WHEN purchases.first_price IS NULL OR purchases.first_price = 0
                        THEN NULL
                        ELSE (purchases.last_price - purchases.first_price) * 100.0 / purchases.first_price
                   END AS change_percent
            FROM purchases
        )
        SELECT material.id             AS "materialId",
               material.code           AS "materialCode",
               material.name           AS "materialName",
               material.name_ar        AS "materialNameAr",
               material.active         AS "materialActive",
               uom.id                  AS "uomId",
               uom.symbol              AS "uomSymbol",
               computed.first_price          AS "firstPrice",
               computed.first_purchase_date  AS "firstPurchaseDate",
               computed.last_price           AS "lastPrice",
               computed.last_purchase_date   AS "lastPurchaseDate",
               computed.price_change         AS "priceChange",
               computed.change_percent       AS "changePercent",
               computed.purchase_count       AS "purchaseCount"
        FROM computed
        JOIN material material ON material.id = computed.material_id
        JOIN uom uom           ON uom.id = material.display_uom_id
        ORDER BY ABS(computed.change_percent) DESC NULLS LAST, material.name ASC
        """, nativeQuery = true)
    List<PurchasePriceDriftAggregate> aggregatePurchasePriceDrift(
        @Param("tenantId") Long tenantId,
        @Param("fromInclusive") LocalDateTime fromInclusive,
        @Param("toExclusive") LocalDateTime toExclusive,
        @Param("branchId") Long branchId,
        @Param("warehouseId") Long warehouseId,
        @Param("categoryId") Long categoryId,
        @Param("supplierId") Long supplierId
    );

    /**
     * Dashboard alerts B4, B5 and B6: the three expiry conditions, one row per warehouse each.
     *
     * <p>One query for three alerts because they are three windows over the same scan of the same
     * open batches, and because splitting them into three queries would let the three observe
     * three different snapshots — a batch could be counted as expiring-soon by one and as expired
     * by another. The caller splits the row into occurrences.
     *
     * <pre>
     *   expired      : expiry_date &lt; today                      -- on the shelf, past its date
     *   expiringSoon : today &lt;= expiry_date &lt;= today + N      -- still sellable, N days left
     *   missingDate  : expiry_tracked material, expiry_date NULL -- the tracking is not working
     * </pre>
     *
     * <p><b>Restricted to OPEN batches holding quantity.</b> A CLOSED batch has been fully
     * depleted and a zero-remaining batch has nothing on a shelf, so neither is a food-safety
     * fact. Including them would make the expired count grow forever as history accumulates,
     * which is the shape of a number nobody looks at twice.
     *
     * <p><b>Values multiply {@code remaining_quantity} by {@code unit_cost}, both of which are
     * batch columns in the same unit</b> — unlike A1's pricing, which crosses the display/stock
     * UOM boundary. No conversion belongs here.
     *
     * <p><b>B6 counts only materials that claim to track expiry.</b> A material with
     * {@code expiry_tracked = false} has no expiry dates by design and is not a gap; counting it
     * would report the whole dry store as a data problem. The condition is specifically "this
     * material says it tracks expiry and this batch has no date", which means the receiving step
     * skipped it and the other two alerts are blind to that batch.
     */
    @Query(nativeQuery = true, value = """
        SELECT w.branch_id      AS "branchId",
               sb.warehouse_id  AS "warehouseId",
               COUNT(*) FILTER (WHERE b.expiry_date IS NOT NULL
                                  AND b.expiry_date < :today)            AS "expiredCount",
               COALESCE(SUM(b.remaining_quantity * COALESCE(b.unit_cost, 0))
                        FILTER (WHERE b.expiry_date IS NOT NULL
                                  AND b.expiry_date < :today), 0)        AS "expiredValue",
               MIN(CAST(b.expiry_date AS timestamp))
                        FILTER (WHERE b.expiry_date IS NOT NULL
                                  AND b.expiry_date < :today)            AS "oldestExpiryAt",
               COUNT(*) FILTER (WHERE b.expiry_date >= :today
                                  AND b.expiry_date <= :soonCutoff)      AS "expiringSoonCount",
               COALESCE(SUM(b.remaining_quantity * COALESCE(b.unit_cost, 0))
                        FILTER (WHERE b.expiry_date >= :today
                                  AND b.expiry_date <= :soonCutoff), 0)  AS "expiringSoonValue",
               COUNT(*) FILTER (WHERE b.expiry_date IS NULL
                                  AND m.expiry_tracked = TRUE)           AS "missingExpiryCount"
        FROM stock_batch b
        JOIN stock_balance sb ON sb.id = b.stock_balance_id
        JOIN warehouse w      ON w.id = sb.warehouse_id
        JOIN material m       ON m.id = sb.material_id
        WHERE b.tenant_id = :tenantId
          AND b.status = 'OPEN'
          AND b.remaining_quantity > 0
          AND (CAST(:branchId AS bigint) IS NULL OR w.branch_id = CAST(:branchId AS bigint))
        GROUP BY w.branch_id, sb.warehouse_id
        """)
    List<ExpiryConditionProjection> aggregateExpiryConditions(
        @Param("tenantId") Long tenantId,
        @Param("branchId") Long branchId,
        @Param("today") LocalDate today,
        @Param("soonCutoff") LocalDate soonCutoff
    );

    /**
     * The three expiry conditions for one warehouse. See {@link #aggregateExpiryConditions}.
     *
     * <p>Counts are batches, not materials: one material with three expired deliveries is three
     * things to pull off a shelf, and collapsing them to one would understate the work.
     */
    interface ExpiryConditionProjection {

        Long getBranchId();

        Long getWarehouseId();

        Long getExpiredCount();

        BigDecimal getExpiredValue();

        /**
         * The earliest expiry date among the expired batches, as a timestamp — how long the oldest
         * thing on the shelf has been out of date. The one age in the expiry family that is real:
         * a batch records its expiry date, so this is read from the row rather than inferred.
         */
        LocalDateTime getOldestExpiryAt();

        Long getExpiringSoonCount();

        /** Value at risk — the figure B5 reports instead of a list of items. */
        BigDecimal getExpiringSoonValue();

        Long getMissingExpiryCount();
    }
}
