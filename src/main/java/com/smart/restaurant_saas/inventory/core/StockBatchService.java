package com.smart.restaurant_saas.inventory.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.inventory.batch.StockBatch;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionDirection;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionType;
import com.smart.restaurant_saas.inventory.core.enums.StockBatchStatus;
import com.smart.restaurant_saas.inventory.repository.StockBatchRepository;
import com.smart.restaurant_saas.inventory.stock.StockBalance;
import com.smart.restaurant_saas.inventory.uom.Uom;

/**
 * Sole writer to the stock_batch table.
 *
 * Creates one batch per inbound movement, mirroring the unit and cost basis of
 * {@link StockBalanceService#applyMovement}: quantities and unit cost are stored in
 * the material's display UOM. Must run inside the ledger's transaction so the batch and
 * the balance commit atomically.
 *
 * Also the sole point of batch depletion: {@link #consumeFifo} eats a balance's open batches
 * oldest-first for outbound consuming movements and returns the actual cost of issue. Both
 * creation and depletion live here so {@code stock_batch} has a single writer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockBatchService {

    private static final int SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private static final String PURCHASE_INVOICE_REFERENCE = "PURCHASE_INVOICE";

    /**
     * Transaction types that open a batch when stock enters. A batch is created only when
     * the type is one of these AND the direction is IN — this excludes a purchase reversal,
     * which keeps type PURCHASE but flips direction to OUT.
     *
     * COUNT_ADJUSTMENT is included for the surplus case (positive variance, direction IN): a
     * physical-count surplus must open a batch so the extra stock can later be FIFO-consumed.
     * Its unit cost is special — it carries no transaction cost and is instead valued at the
     * balance's current average cost (see {@link #resolveBatchUnitCost}). A COUNT_ADJUSTMENT
     * shortage is direction OUT and FIFO-consumes instead of opening a batch.
     */
    private static final Set<InventoryTransactionType> BATCH_OPENING_TYPES = EnumSet.of(
        InventoryTransactionType.PURCHASE,
        InventoryTransactionType.OPENING_BALANCE,
        InventoryTransactionType.TRANSFER_IN,
        InventoryTransactionType.COUNT_ADJUSTMENT);

    /**
     * Transaction types that issue stock and must deplete FIFO batches. Combined with a
     * direction-OUT and non-reversal guard in {@link #consumes}. Notable exclusions:
     *   - PURCHASE_RETURN: direction OUT but must come out of its original (source) batch,
     *     not oldest-first; PurchaseReturnService handles that explicit source-batch depletion.
     *     The average cost is re-derived from the remaining open batches afterwards.
     *   - Reversals of any kind: excluded by the reversesTransactionId guard, so they never
     *     FIFO-consume (a reversal that ends up OUT reverses an original IN by depleting its
     *     source batch; the average is then re-derived from the open batches).
     *   - COUNT_ADJUSTMENT only consumes on a decrease, which the direction-OUT guard enforces.
     */
    private static final Set<InventoryTransactionType> CONSUMING_TYPES = EnumSet.of(
        InventoryTransactionType.MANUAL_CONSUMPTION,
        InventoryTransactionType.CONSUMPTION_SUMMARY,
        InventoryTransactionType.WASTE,
        InventoryTransactionType.TRANSFER_OUT,
        InventoryTransactionType.COUNT_ADJUSTMENT);

    private final StockBatchRepository stockBatchRepository;
    private final UomConversionService uomConversionService;

    /**
     * @param tx      the inbound ledger transaction
     * @param balance the balance resolved for this movement (created/saved by
     *                {@link StockBalanceService#resolveBalance}; its quantity and average are only
     *                written later, once, by {@link StockBalanceService#applyMovement}). Passed in to
     *                link the batch without re-querying; the single source of truth for warehouse +
     *                material, and — for a COUNT_ADJUSTMENT surplus — the pre-movement average cost.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockBatch createBatchFromInbound(InventoryTransaction tx, StockBalance balance) {
        if (!opensBatch(tx)) {
            return null;
        }

        Uom displayUom = tx.getMaterial().getDisplayUom();

        // Same conversion approach as StockBalanceService.applyMovement: quantity via
        // the conversion service, unit cost via inverse scaling — so the batch shares the
        // balance's unit and cost basis (the display UOM).
        BigDecimal quantityInDisplayUom = uomConversionService.convert(
            tx.getStockQuantity(), tx.getStockUom(), displayUom,
            tx.getMaterial(), tx.getTenantId());

        BigDecimal unitCostInDisplayUom = resolveBatchUnitCost(tx, balance, displayUom);

        StockBatch batch = new StockBatch();
        batch.setTenantId(tx.getTenantId());
        batch.setStockBalance(balance);
        batch.setOriginalQuantity(quantityInDisplayUom);
        batch.setRemainingQuantity(quantityInDisplayUom);
        batch.setUnitCost(unitCostInDisplayUom);
        batch.setMovementDate(tx.getMovementDate());
        batch.setSourceTransactionId(tx.getId());
        batch.setStatus(StockBatchStatus.OPEN);
        batch.setCreatedBy(tx.getCreatedBy());

        // Optional invoice references — only present for purchases.
        if (PURCHASE_INVOICE_REFERENCE.equals(tx.getReferenceType())) {
            batch.setSourceInvoiceId(tx.getReferenceId());
        }
        // The source line id is carried through the ledger from the invoice; set it whenever
        // present so a batch can later be traced to its exact originating line.
        if (tx.getSourceInvoiceLineId() != null) {
            batch.setSourceInvoiceLineId(tx.getSourceInvoiceLineId());
        }

        StockBatch saved = stockBatchRepository.save(batch);
        log.info("Opened stock batch id={} tenant={} material={} qty={} sourceTx={}",
            saved.getId(), tx.getTenantId(), tx.getMaterial().getId(),
            quantityInDisplayUom, tx.getId());
        return saved;
    }

    /**
     * Depletes open batches FIFO (oldest first) for an outbound consuming movement and returns
     * the actual cost of issue, computed from each batch's own unit cost (never an average).
     *
     * <p>No-ops and returns {@code null} when {@code tx} is not a consuming movement (see
     * {@link #consumes}), so the caller can invoke it unconditionally at the single ledger
     * save point and only write cost back when a value is returned.
     *
     * <p>The depletion quantity is converted to the material's display UOM — the unit batches
     * are stored in — exactly as {@link StockBalanceService#applyMovement} converts before
     * touching the balance.
     *
     * <p>Shortfall: when the requested quantity exceeds the total remaining across open batches,
     * the matched part is valued from the batches and the unmatched remainder is valued at the
     * balance's PRE-movement average cost — this runs before {@link StockBalanceService#applyMovement}
     * re-derives the average, so the fallback reads the average as it stood before this movement. It
     * never blocks and never drives a batch negative — mirroring the balance, whose quantity is
     * likewise permitted to go negative when applyMovement later applies the signed delta; this
     * method only costs the remainder.
     *
     * @param tx      the consuming ledger transaction (already saved; balance not yet moved)
     * @param balance the balance resolved for this movement — single source of truth for the
     *                batches' display UOM and the pre-movement fallback average cost
     * @return the total cost of issue in display-UOM money (scale {@code SCALE}), or {@code null}
     *         when the movement does not consume
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal consumeFifo(InventoryTransaction tx, StockBalance balance) {
        if (!consumes(tx)) {
            return null;
        }

        // Quantity to consume, in display UOM (batches are in display UOM), mirroring
        // StockBalanceService.applyMovement's conversion of the stock quantity.
        BigDecimal needed = uomConversionService.convert(
            tx.getStockQuantity(), tx.getStockUom(), balance.getUom(),
            tx.getMaterial(), tx.getTenantId());

        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal remaining = needed;

        List<StockBatch> openBatches = stockBatchRepository
            .findByStockBalanceIdAndStatusOrderByIdAsc(balance.getId(), StockBatchStatus.OPEN);

        for (StockBatch batch : openBatches) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            // Eat the smaller of what is still needed and what this batch has left.
            BigDecimal taken = remaining.min(batch.getRemainingQuantity());
            if (taken.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal batchUnitCost = batch.getUnitCost() != null
                ? batch.getUnitCost()
                : BigDecimal.ZERO;
            totalCost = totalCost.add(taken.multiply(batchUnitCost));

            BigDecimal batchRemaining = batch.getRemainingQuantity().subtract(taken);
            batch.setRemainingQuantity(batchRemaining);
            if (batchRemaining.compareTo(BigDecimal.ZERO) == 0) {
                batch.setStatus(StockBatchStatus.CLOSED);
            }
            stockBatchRepository.save(batch);

            remaining = remaining.subtract(taken);
        }

        // Shortfall: value the unmatched remainder at the balance's current average cost.
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal averageCost = balance.getAverageCost() != null
                ? balance.getAverageCost()
                : BigDecimal.ZERO;
            totalCost = totalCost.add(remaining.multiply(averageCost));
            log.warn("FIFO shortfall: balance={} tenant={} material={} short={} valued at avgCost={}",
                balance.getId(), tx.getTenantId(), tx.getMaterial().getId(), remaining, averageCost);
        }

        BigDecimal costOfIssue = totalCost.setScale(SCALE, ROUNDING);
        log.info("FIFO consumed balance={} tenant={} material={} qtyDisplayUom={} costOfIssue={}",
            balance.getId(), tx.getTenantId(), tx.getMaterial().getId(), needed, costOfIssue);
        return costOfIssue;
    }

    /**
     * Reversing an inbound batch-opening movement must remove the still-available source batch
     * quantity. The caller's guard should have already rejected consumed batches; this check is
     * the transactional backstop for concurrent consumption between guard and reversal.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reverseSourceBatchIfOpened(InventoryTransaction reversal) {
        if (!reversesBatchOpening(reversal)) {
            return;
        }

        stockBatchRepository
            .findByTenantIdAndSourceTransactionId(
                reversal.getTenantId(), reversal.getReversesTransactionId())
            .ifPresent(batch -> {
                BigDecimal reversedQuantity = uomConversionService.convert(
                    reversal.getStockQuantity(), reversal.getStockUom(),
                    batch.getStockBalance().getUom(), reversal.getMaterial(),
                    reversal.getTenantId());

                if (reversedQuantity.compareTo(batch.getRemainingQuantity()) > 0) {
                    throw new BusinessException(InventoryErrorCode.BATCH_SHORTFALL,
                        "Cannot reverse source batch " + batch.getId()
                            + ": only " + batch.getRemainingQuantity()
                            + " remains, requested " + reversedQuantity,
                        ErrorParams.of("batchId", batch.getId(),
                            "availableInBatch", batch.getRemainingQuantity(),
                            "requested", reversedQuantity,
                            "sourceTransactionId", reversal.getReversesTransactionId()));
                }

                BigDecimal newRemaining = batch.getRemainingQuantity().subtract(reversedQuantity);
                batch.setRemainingQuantity(newRemaining);
                if (newRemaining.compareTo(BigDecimal.ZERO) == 0) {
                    batch.setStatus(StockBatchStatus.CLOSED);
                }
                stockBatchRepository.save(batch);
                log.info("Reversed source batch id={} sourceTx={} by {} ; remaining={}",
                    batch.getId(), reversal.getReversesTransactionId(),
                    reversedQuantity, newRemaining);
            });
    }

    /**
     * Locates the batch opened by the given purchase invoice line for use in a return's guard
     * pass (read-only; no transaction constraint). Throws loudly when no batch is found — this
     * surfaces data-integrity gaps (e.g. invoice posted before source-line tracking was enabled)
     * rather than silently skipping batch depletion.
     */
    public StockBatch requireSourceBatch(Long stockBalanceId, Long sourceInvoiceLineId) {
        return stockBatchRepository
            .findByStockBalanceIdAndSourceInvoiceLineId(stockBalanceId, sourceInvoiceLineId)
            .orElseThrow(() -> new ResourceNotFoundException(
                InventoryErrorCode.RESOURCE_NOT_FOUND,
                "No source batch found for invoice line " + sourceInvoiceLineId
                + " (balance " + stockBalanceId + ") — the originating invoice may have been"
                + " posted before per-line batch tracking was enabled",
                ErrorParams.of("entityType", "StockBatch",
                    "invoiceLineId", sourceInvoiceLineId, "stockBalanceId", stockBalanceId)));
    }

    /**
     * Depletes the batch opened by the specific purchase invoice line (not FIFO). Used when
     * posting a purchase return so that goods leave the exact source batch, not oldest-first.
     *
     * <p>Fails loudly when:
     * <ul>
     *   <li>no batch is found for the given line (data-integrity gap — see {@link #requireSourceBatch})</li>
     *   <li>the return quantity exceeds the batch's remaining quantity (goods already consumed)</li>
     * </ul>
     *
     * <p>Must run inside the caller's transaction (propagation MANDATORY).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void depleteSourceBatch(Long stockBalanceId, Long sourceInvoiceLineId,
                                   BigDecimal returnQtyDisplayUom) {
        StockBatch batch = stockBatchRepository
            .findByStockBalanceIdAndSourceInvoiceLineId(stockBalanceId, sourceInvoiceLineId)
            .orElseThrow(() -> new ResourceNotFoundException(
                InventoryErrorCode.RESOURCE_NOT_FOUND,
                "No source batch found for invoice line " + sourceInvoiceLineId
                + " (balance " + stockBalanceId + ") — the originating invoice may have been"
                + " posted before per-line batch tracking was enabled",
                ErrorParams.of("entityType", "StockBatch",
                    "invoiceLineId", sourceInvoiceLineId, "stockBalanceId", stockBalanceId)));

        if (returnQtyDisplayUom.compareTo(batch.getRemainingQuantity()) > 0) {
            throw new BusinessException(InventoryErrorCode.BATCH_SHORTFALL, String.format(
                "Cannot return %.6f: only %.6f remains from this purchase batch"
                + " (the rest has already been consumed). Invoice line: %d",
                returnQtyDisplayUom, batch.getRemainingQuantity(), sourceInvoiceLineId),
                ErrorParams.of("availableInBatch", batch.getRemainingQuantity(),
                    "requested", returnQtyDisplayUom, "invoiceLineId", sourceInvoiceLineId));
        }

        BigDecimal newRemaining = batch.getRemainingQuantity().subtract(returnQtyDisplayUom);
        batch.setRemainingQuantity(newRemaining);
        if (newRemaining.compareTo(BigDecimal.ZERO) == 0) {
            batch.setStatus(StockBatchStatus.CLOSED);
        }
        stockBatchRepository.save(batch);
        log.info("Depleted source batch id={} invoiceLine={} by {} ; remaining={}",
            batch.getId(), sourceInvoiceLineId, returnQtyDisplayUom, newRemaining);
    }

    /**
     * Restores quantity to the exact source batch depleted by a purchase return unpost.
     * This is the inverse of {@link #depleteSourceBatch}; the ledger reversal restores the
     * stock balance, while this restores batch traceability.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void restoreSourceBatch(Long stockBalanceId, Long sourceInvoiceLineId,
                                   BigDecimal restoredQtyDisplayUom, Long actingUserId) {
        StockBatch batch = stockBatchRepository
            .findByStockBalanceIdAndSourceInvoiceLineId(stockBalanceId, sourceInvoiceLineId)
            .orElseThrow(() -> new ResourceNotFoundException(
                InventoryErrorCode.RESOURCE_NOT_FOUND,
                "No source batch found for invoice line " + sourceInvoiceLineId
                + " (balance " + stockBalanceId + ") — cannot restore purchase return",
                ErrorParams.of("entityType", "StockBatch",
                    "invoiceLineId", sourceInvoiceLineId, "stockBalanceId", stockBalanceId)));

        BigDecimal newRemaining = batch.getRemainingQuantity().add(restoredQtyDisplayUom);
        if (newRemaining.compareTo(batch.getOriginalQuantity()) > 0) {
            throw new BusinessException(InventoryErrorCode.BATCH_SHORTFALL,
                "Cannot restore source batch " + batch.getId()
                    + ": restored quantity would exceed the original batch quantity",
                ErrorParams.of("batchId", batch.getId(),
                    "originalQuantity", batch.getOriginalQuantity(),
                    "remainingQuantity", batch.getRemainingQuantity(),
                    "requestedRestore", restoredQtyDisplayUom));
        }

        batch.setRemainingQuantity(newRemaining);
        if (newRemaining.compareTo(BigDecimal.ZERO) > 0) {
            batch.setStatus(StockBatchStatus.OPEN);
        }
        batch.setUpdatedBy(actingUserId);
        stockBatchRepository.save(batch);
        log.info("Restored source batch id={} invoiceLine={} by {} ; remaining={}",
            batch.getId(), sourceInvoiceLineId, restoredQtyDisplayUom, newRemaining);
    }

    /**
     * A batch opens only when the type is an opening type AND the movement is inbound.
     * Requiring direction IN guards the purchase-reversal case (type PURCHASE, direction OUT).
     */
    private boolean opensBatch(InventoryTransaction tx) {
        return BATCH_OPENING_TYPES.contains(tx.getTransactionType())
            && tx.getDirection() == InventoryTransactionDirection.IN;
    }

    private boolean reversesBatchOpening(InventoryTransaction tx) {
        return tx.getReversesTransactionId() != null
            && tx.getDirection() == InventoryTransactionDirection.OUT
            && BATCH_OPENING_TYPES.contains(tx.getTransactionType());
    }

    /**
     * A movement FIFO-consumes only when all three hold:
     *   1. its type is a consuming type (issues stock),
     *   2. its direction is OUT (so a COUNT_ADJUSTMENT only depletes on a decrease), and
     *   3. it is not a reversal (reversesTransactionId == null) — a reversal must never trigger
     *      FIFO depletion; its value is already handled through the weighted-average.
     */
    private boolean consumes(InventoryTransaction tx) {
        return CONSUMING_TYPES.contains(tx.getTransactionType())
            && tx.getDirection() == InventoryTransactionDirection.OUT
            && tx.getReversesTransactionId() == null;
    }

    /**
     * Resolves the unit cost (per display UOM) to stamp on a newly opened batch, branching on
     * transaction type:
     *   - COUNT_ADJUSTMENT surplus: the count sends no cost, so value the batch at the balance's
     *     current {@code averageCost} (already per display UOM). The average is re-derived from
     *     the open batches only after this batch is created; reading it here (before that
     *     recalculation) yields the pre-surplus average, so the surplus enters at the existing
     *     average and the recalculation leaves the average unchanged.
     *   - Any normal inbound (PURCHASE / OPENING_BALANCE / TRANSFER_IN): use the transaction's
     *     own unit cost converted from stock UOM to display UOM.
     */
    private BigDecimal resolveBatchUnitCost(InventoryTransaction tx, StockBalance balance, Uom displayUom) {
        if (tx.getTransactionType() == InventoryTransactionType.COUNT_ADJUSTMENT) {
            return balance.getAverageCost();
        }
        return convertUnitCostToDisplayUom(tx, displayUom);
    }

    /**
     * Converts the transaction's unit cost — expressed per stock UOM — into a cost per
     * display UOM, mirroring StockBalanceService. Returns null when the source carried no
     * unit cost.
     */
    private BigDecimal convertUnitCostToDisplayUom(InventoryTransaction tx, Uom displayUom) {
        if (tx.getUnitCost() == null) {
            return null;
        }
        BigDecimal stockUnitsPerDisplayUnit = uomConversionService.convert(
            BigDecimal.ONE, displayUom, tx.getStockUom(),
            tx.getMaterial(), tx.getTenantId());
        return tx.getUnitCost().multiply(stockUnitsPerDisplayUnit).setScale(SCALE, ROUNDING);
    }
}
