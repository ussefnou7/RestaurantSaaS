package com.smart.restaurant_saas.inventory.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smart.restaurant_saas.inventory.core.enums.IdempotencyScope;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionDirection;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.repository.InventoryTransactionRepository;
import com.smart.restaurant_saas.inventory.repository.MaterialRepository;
import com.smart.restaurant_saas.inventory.repository.UomRepository;
import com.smart.restaurant_saas.inventory.repository.WarehouseRepository;
import com.smart.restaurant_saas.inventory.stock.StockBalance;
import com.smart.restaurant_saas.inventory.uom.Uom;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;
import com.smart.restaurant_saas.tenant.TenantTimeZoneService;

/**
 * The sole writer to the inventory_transaction table.
 *
 * Callers supply a {@link LedgerCommand}. This service:
 *   1. Enforces idempotency (early return on duplicate key).
 *   2. Resolves and validates all referenced entities.
 *   3. Converts the entered quantity to the material's stock UOM.
 *   4. Persists the InventoryTransaction.
 *   5. Delegates stock-balance mutation to {@link StockBalanceService}.
 *
 * A concurrent duplicate is handled at the DB level (unique constraint on
 * tenant_id + idempotency_key). A {@link DataIntegrityViolationException} on
 * save is caught and resolved by re-reading the existing record.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryLedgerService {

    private static final int SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final InventoryTransactionRepository transactionRepo;
    private final MaterialRepository materialRepo;
    private final WarehouseRepository warehouseRepo;
    private final UomRepository uomRepo;
    private final UomConversionService uomConversionService;
    private final IdempotencyService idempotencyService;
    private final StockBalanceService stockBalanceService;
    private final StockBatchService stockBatchService;
    private final TenantTimeZoneService tenantTimeZoneService;

    // =========================================================================
    // Public API
    // =========================================================================

    @Transactional
    public InventoryTransaction record(LedgerCommand cmd) {
        validate(cmd);

        // 1. Idempotency — fast path
        if (cmd.getIdempotencyKey() != null) {
            Optional<Long> existingId = idempotencyService.findExistingId(
                    cmd.getTenantId(), IdempotencyScope.INVENTORY_TRANSACTION, cmd.getIdempotencyKey());
            if (existingId.isPresent()) {
                return transactionRepo.findById(existingId.get())
                        .orElseThrow(() -> InventoryLedgerException.notFound(
                                "InventoryTransaction", existingId.get()));
            }
        }

        // 2. Resolve entities
        Warehouse warehouse = loadWarehouse(cmd.getWarehouseId(), cmd.getTenantId());
        Material material = loadMaterial(cmd.getMaterialId(), cmd.getTenantId());
        Uom enteredUom = uomRepo.findById(cmd.getEnteredUomId())
                .orElseThrow(() -> InventoryLedgerException.notFound("Uom", cmd.getEnteredUomId()));

        // 3. Convert to stock quantity
        BigDecimal stockQuantity = uomConversionService.convertToStockUom(
                cmd.getEnteredQuantity(), enteredUom, material, cmd.getTenantId());

        // 4. Costs — enteredUnitCost is per ENTERED UOM (same unit as enteredQuantity).
        // Total cost is unit-invariant: enteredUnitCost * enteredQuantity is the same amount
        // in any UOM. Dividing that total by the stock quantity therefore yields the unit cost
        // per STOCK UOM, which is how tx.unitCost is stored (and consumed downstream by
        // StockBalance / StockBatch). Computing totalCost from the entered side avoids a
        // re-rounding mismatch against the divided unit cost.
        BigDecimal totalCost = null;
        BigDecimal unitCost = null;
        if (cmd.getEnteredUnitCost() != null) {
            BigDecimal rawTotal = cmd.getEnteredUnitCost().multiply(cmd.getEnteredQuantity());
            totalCost = rawTotal.setScale(SCALE, ROUNDING);
            if (stockQuantity.compareTo(BigDecimal.ZERO) != 0) {
                unitCost = rawTotal.divide(stockQuantity, SCALE, ROUNDING);
            }
        }

        // 5. Build and persist
        InventoryTransaction tx = buildTransaction(
                cmd, warehouse, material, enteredUom, stockQuantity, unitCost, totalCost);

        return saveWithIdempotencyGuard(tx, cmd.getTenantId(), cmd.getIdempotencyKey());
    }

    @Transactional
    public InventoryTransaction reverse(Long originalTxId, String reasonCode,
                                        String idempotencyKey, Long actingUserId) {
        // 1. Load original
        InventoryTransaction original = transactionRepo.findById(originalTxId)
            .orElseThrow(() -> InventoryLedgerException.notFound("InventoryTransaction", originalTxId));

        Long tenantId = original.getTenantId();

        // 2. Idempotency check for the reversal. This must run before the "already reversed"
        // guard so deterministic retry keys can return the existing reversal.
        if (idempotencyKey != null) {
            Optional<Long> existingId = idempotencyService.findExistingId(
                tenantId, IdempotencyScope.INVENTORY_TRANSACTION, idempotencyKey);
            if (existingId.isPresent()) {
                return transactionRepo.findById(existingId.get())
                    .orElseThrow(() -> InventoryLedgerException.notFound(
                        "InventoryTransaction", existingId.get()));
            }
        }

        // 3. Guard: already reversed by another request/key?
        if (transactionRepo.findReversalOf(originalTxId).isPresent()) {
            throw InventoryLedgerException.alreadyReversed(originalTxId);
        }

        // 4. Build reversal (opposite direction, same quantities)
        InventoryTransactionDirection reversedDirection =
            original.getDirection() == InventoryTransactionDirection.IN
                ? InventoryTransactionDirection.OUT
                : InventoryTransactionDirection.IN;

        InventoryTransaction reversal = new InventoryTransaction();
        reversal.setTenantId(tenantId);
        reversal.setWarehouse(original.getWarehouse());
        reversal.setMaterial(original.getMaterial());
        reversal.setTransactionType(original.getTransactionType());
        reversal.setDirection(reversedDirection);
        reversal.setEnteredQuantity(original.getEnteredQuantity());
        reversal.setEnteredUom(original.getEnteredUom());
        reversal.setStockQuantity(original.getStockQuantity());
        reversal.setStockUom(original.getStockUom());
        reversal.setUnitCost(original.getUnitCost());
        reversal.setTotalCost(original.getTotalCost());
        reversal.setReferenceType(original.getReferenceType());
        reversal.setReferenceId(original.getReferenceId());
        reversal.setReasonCode(reasonCode);
        reversal.setReversesTransactionId(originalTxId);
        reversal.setIdempotencyKey(idempotencyKey);
        // The reversal was genuinely recorded now, but it reverses the original event,
        // so its business (movement) date mirrors the original's.
        reversal.setTransactionDate(LocalDateTime.now(tenantTimeZoneService.zoneFor(tenantId)));
        reversal.setMovementDate(original.getMovementDate());
        reversal.setCreatedBy(actingUserId);

        return saveWithIdempotencyGuard(reversal, tenantId, idempotencyKey);
    }

    // =========================================================================
    // Internals
    // =========================================================================

    private void validate(LedgerCommand cmd) {
        if (cmd.getTenantId() == null) {
            throw InventoryLedgerException.validation("tenantId", "tenantId is required");
        }
        if (cmd.getWarehouseId() == null) {
            throw InventoryLedgerException.validation("warehouseId", "warehouseId is required");
        }
        if (cmd.getMaterialId() == null) {
            throw InventoryLedgerException.validation("materialId", "materialId is required");
        }
        if (cmd.getTransactionType() == null) {
            throw InventoryLedgerException.validation("transactionType", "transactionType is required");
        }
        if (cmd.getDirection() == null) {
            throw InventoryLedgerException.validation("direction", "direction is required");
        }
        if (cmd.getEnteredQuantity() == null || cmd.getEnteredQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw InventoryLedgerException.validation("enteredQuantity", "enteredQuantity must be positive");
        }
        if (cmd.getEnteredUomId() == null) {
            throw InventoryLedgerException.validation("enteredUomId", "enteredUomId is required");
        }
    }

    private Warehouse loadWarehouse(Long warehouseId, Long tenantId) {
        Warehouse w = warehouseRepo.findById(warehouseId)
            .orElseThrow(() -> InventoryLedgerException.notFound("Warehouse", warehouseId));
        if (!w.getTenantId().equals(tenantId)) {
            throw InventoryLedgerException.tenantMismatch("Warehouse", warehouseId, tenantId);
        }
        return w;
    }

    private Material loadMaterial(Long materialId, Long tenantId) {
        Material m = materialRepo.findById(materialId)
            .orElseThrow(() -> InventoryLedgerException.notFound("Material", materialId));
        if (!m.getTenantId().equals(tenantId)) {
            throw InventoryLedgerException.tenantMismatch("Material", materialId, tenantId);
        }
        return m;
    }

    private InventoryTransaction buildTransaction(LedgerCommand cmd,
                                                   Warehouse warehouse,
                                                   Material material,
                                                   Uom enteredUom,
                                                   BigDecimal stockQuantity,
                                                   BigDecimal unitCost,
                                                   BigDecimal totalCost) {
        InventoryTransaction tx = new InventoryTransaction();
        tx.setTenantId(cmd.getTenantId());
        tx.setWarehouse(warehouse);
        tx.setMaterial(material);
        tx.setTransactionType(cmd.getTransactionType());
        tx.setDirection(cmd.getDirection());
        tx.setEnteredQuantity(cmd.getEnteredQuantity());
        tx.setEnteredUom(enteredUom);
        tx.setStockQuantity(stockQuantity);
        tx.setStockUom(material.getStockUom());
        tx.setUnitCost(unitCost);
        tx.setTotalCost(totalCost);
        tx.setReferenceType(cmd.getReferenceType());
        tx.setReferenceId(cmd.getReferenceId());
        // Persist the source line id whenever supplied, regardless of transaction type —
        // cheap to keep, expensive to lack when a later return needs the exact source batch.
        tx.setSourceInvoiceLineId(cmd.getSourceInvoiceLineId());
        tx.setReasonCode(cmd.getReasonCode());
        tx.setIdempotencyKey(cmd.getIdempotencyKey());
        tx.setBatchNumber(cmd.getBatchNumber());
        tx.setExpiryDate(cmd.getExpiryDate());
        tx.setShiftId(cmd.getShiftId());
        tx.setNotes(cmd.getNotes());
        LocalDateTime recordDate = cmd.getTransactionDate() != null
            ? cmd.getTransactionDate()
            : LocalDateTime.now(tenantTimeZoneService.zoneFor(cmd.getTenantId()));
        tx.setTransactionDate(recordDate);
        // movementDate is the business date of the event; fall back to the record date.
        tx.setMovementDate(cmd.getMovementDate() != null
            ? cmd.getMovementDate()
            : recordDate);
        tx.setCreatedBy(cmd.getCreatedBy());
        return tx;
    }

    private InventoryTransaction saveWithIdempotencyGuard(InventoryTransaction tx,
                                                           Long tenantId,
                                                           String idempotencyKey) {
        try {
            // Persist the transaction first — this assigns its id (which the batch it may open
            // links to via sourceTransactionId) and, for a concurrent duplicate, trips the
            // tenant+idempotency-key unique constraint here, before any batch/balance mutation, so
            // the recovery in the catch below behaves identically to the previous sequence.
            InventoryTransaction saved = transactionRepo.save(tx);

            // Resolve (load or create) the balance row WITHOUT moving its quantity or average yet.
            // It is persisted eagerly so the batch operations can link to it and query its batches.
            StockBalance balance = stockBalanceService.resolveBalance(saved);

            // Batch operations establish the truth for this movement, in order (each a no-op when
            // it does not apply):
            //  - close the source batch when this is an OUT reversal of a batch-opening inbound,
            //  - open a batch for an inbound movement (needs saved.getId(), assigned above),
            //  - FIFO-deplete open batches for an outbound consuming movement, yielding its cost.
            // consumeFifo reads the balance's average as it stands BEFORE this movement — the
            // average is only re-derived in applyMovement, after these run — so a FIFO shortfall is
            // still valued at the pre-movement average, unchanged from the previous sequence.
            stockBatchService.reverseSourceBatchIfOpened(saved);
            stockBatchService.createBatchFromInbound(saved, balance);
            BigDecimal costOfIssue = stockBatchService.consumeFifo(saved, balance);

            // Finalize the consuming transaction's cost from the FIFO cost of issue. saved is a
            // managed entity, so these fields flush with the surrounding transaction — no second
            // explicit save; the transaction is persisted exactly once.
            if (costOfIssue != null) {
                saved.setTotalCost(costOfIssue);
                // unitCost is per STOCK UOM (totalCost is unit-invariant money / stock qty),
                // matching how inbound stores tx.unitCost — so IN/OUT unit costs aggregate
                // consistently across the ledger.
                BigDecimal stockQuantity = saved.getStockQuantity();
                if (stockQuantity != null && stockQuantity.compareTo(BigDecimal.ZERO) != 0) {
                    saved.setUnitCost(costOfIssue.divide(stockQuantity, SCALE, ROUNDING));
                }
            }

            // Batch state is now final for this movement. Write the balance exactly once: move its
            // quantity by the ledger's signed delta AND re-derive the average from the now-final
            // open batches together, within this same transaction, so balance and batches never
            // diverge. Unconditional and type-agnostic — no cost-bearing classification.
            stockBalanceService.applyMovement(balance, saved);
            return saved;
        } catch (DataIntegrityViolationException ex) {
            if (idempotencyKey != null) {
                return idempotencyService
                    .findExistingId(tenantId, IdempotencyScope.INVENTORY_TRANSACTION, idempotencyKey)
                    .flatMap(transactionRepo::findById)
                    .orElseThrow(() -> ex);
            }
            throw ex;
        }
    }
}
