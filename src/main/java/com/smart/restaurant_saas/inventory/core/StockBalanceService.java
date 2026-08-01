package com.smart.restaurant_saas.inventory.core;

import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionDirection;
import com.smart.restaurant_saas.inventory.batch.dto.StockBatchResponse;
import com.smart.restaurant_saas.inventory.mapper.StockBalanceMapper;
import com.smart.restaurant_saas.inventory.mapper.StockBatchMapper;
import com.smart.restaurant_saas.inventory.orderconsumption.OrderConsumptionAvailabilityService;
import com.smart.restaurant_saas.inventory.repository.OpenBatchTotals;
import com.smart.restaurant_saas.inventory.repository.StockBatchRepository;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.material.OpeningBalanceService;
import com.smart.restaurant_saas.inventory.material.dto.OpeningBalanceRequest;
import com.smart.restaurant_saas.inventory.repository.MaterialRepository;
import com.smart.restaurant_saas.inventory.repository.StockBalanceRepository;
import com.smart.restaurant_saas.inventory.repository.WarehouseRepository;
import com.smart.restaurant_saas.inventory.stock.StockBalance;
import com.smart.restaurant_saas.inventory.stock.dto.AddMaterialToWarehouseRequest;
import com.smart.restaurant_saas.inventory.stock.dto.StockBalanceResponse;
import com.smart.restaurant_saas.inventory.stock.dto.UpdateStockSettingsRequest;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;

/**
 * Maintains the running stock balance for each (tenant, warehouse, material) tuple.
 *
 * Must be called within the same transaction that persists the InventoryTransaction,
 * so both the ledger entry and balance update commit atomically.
 *
 * <p>The stock batches are the sole source of truth for the balance's average cost. After any
 * operation that mutates batch quantities, {@link #recalculateFromOpenBatches(StockBalance)}
 * re-derives {@code averageCost} from the current OPEN batches — there is no incremental running
 * formula and no transaction-type classification of what does or does not affect the average.
 * The running {@code quantity} is still moved by the ledger's signed delta (see
 * {@link #applyMovement}), which — unlike the batches — is permitted to go negative on a FIFO
 * shortfall; in every non-shortfall case it equals the sum of the open batches' remaining
 * quantities, which the backfill's mismatch check verifies.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockBalanceService {

    private static final int SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private final StockBalanceRepository stockBalanceRepository;
    private final StockBalanceMapper mapper;
    private final MaterialRepository materialRepository;
    private final WarehouseRepository warehouseRepository;
    private final UomConversionService uomConversionService;
    private final StockBatchRepository stockBatchRepository;
    private final StockBatchMapper stockBatchMapper;
    private final OrderConsumptionAvailabilityService orderConsumptionAvailabilityService;

    /**
     * Lazily injected to break the construction cycle
     * StockBalanceService -> OpeningBalanceService -> InventoryLedgerService -> StockBalanceService.
     */
    @Lazy
    private final OpeningBalanceService openingBalanceService;

    @Transactional(readOnly = true)
    public List<StockBalanceResponse> findByWarehouse(Long tenantId, Long warehouseId,
                                                      String search, Long categoryId,
                                                      Boolean belowMinimum) {
        List<StockBalance> balances = stockBalanceRepository
            .findByWarehouse(tenantId, warehouseId, blankToNull(search), categoryId, null);
        return mapWithOutstandingConsumption(tenantId, warehouseId, balances).stream()
            .filter(response -> belowMinimum == null || belowMinimum.equals(response.getIsBelowMinimum()))
            .toList();
    }

    @Transactional(readOnly = true)
    public StockBalanceResponse findByWarehouseAndMaterial(Long tenantId, Long warehouseId,
                                                           Long materialId) {
        StockBalance balance = stockBalanceRepository
            .findByTenantIdAndWarehouseIdAndMaterialId(tenantId, warehouseId, materialId)
            .orElseThrow(() -> new ResourceNotFoundException(
                InventoryErrorCode.RESOURCE_NOT_FOUND,
                "Stock balance not found for material " + materialId
                    + " in warehouse " + warehouseId,
                ErrorParams.of("entityType", "StockBalance",
                    "materialId", materialId, "warehouseId", warehouseId)));
        return mapper.toResponse(balance, displayedQuantity(tenantId, warehouseId, balance));
    }

    /**
     * All batches (OPEN and CLOSED) of a tenant-owned balance in FIFO order (movement date,
     * then id). 404 if the balance does not belong to the tenant. No aggregation — the frontend
     * sums remaining quantities itself.
     */
    @Transactional(readOnly = true)
    public List<StockBatchResponse> findBatchesForBalance(Long balanceId, Long tenantId) {
        StockBalance balance = stockBalanceRepository
            .findByIdAndTenantId(balanceId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(
                InventoryErrorCode.RESOURCE_NOT_FOUND,
                "Stock balance not found: " + balanceId,
                ErrorParams.of("entityType", "StockBalance", "entityId", balanceId)));
        // All batches of a balance share its display UOM — resolve the symbol once.
        String uomSymbol = balance.getUom() != null ? balance.getUom().getSymbol() : null;
        return stockBatchRepository.findByStockBalanceIdOrderByMovementDateAscIdAsc(balanceId)
            .stream()
            .map(batch -> stockBatchMapper.toResponse(batch, uomSymbol))
            .toList();
    }

    /**
     * Adds a material to a warehouse by creating its stock-balance row. The on-hand
     * quantity starts at zero; when {@code openingQuantity > 0} an OPENING_BALANCE
     * ledger transaction is posted (which raises the quantity) via {@link OpeningBalanceService}.
     * Fails if the material is already stocked in the warehouse.
     */
    @Transactional
    public StockBalanceResponse addMaterialToWarehouse(
            Long warehouseId, AddMaterialToWarehouseRequest request,
            Long tenantId, Long actingUserId) {

        Warehouse warehouse = warehouseRepository
            .findByIdAndTenantId(warehouseId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(
                InventoryErrorCode.RESOURCE_NOT_FOUND,
                "Warehouse not found: " + warehouseId,
                ErrorParams.of("entityType", "Warehouse", "entityId", warehouseId)));

        Material material = materialRepository
            .findByIdAndTenantId(request.getMaterialId(), tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(
                InventoryErrorCode.RESOURCE_NOT_FOUND,
                "Material not found: " + request.getMaterialId(),
                ErrorParams.of("entityType", "Material", "entityId", request.getMaterialId())));

        boolean alreadyStocked = stockBalanceRepository
            .findByTenantIdAndWarehouseIdAndMaterialId(tenantId, warehouseId, request.getMaterialId())
            .isPresent();
        if (alreadyStocked) {
            throw new BusinessException(InventoryErrorCode.DUPLICATE_OPERATION,
                "Material already exists in this warehouse",
                ErrorParams.of("entityType", "StockBalance",
                    "entityId", request.getMaterialId(), "warehouseId", warehouseId));
        }

        BigDecimal openingQuantity = nz(request.getOpeningBalance());
        BigDecimal openingUnitCost = request.getAverageCost();
        boolean hasOpeningCost =
            openingQuantity.compareTo(BigDecimal.ZERO) > 0 && openingUnitCost != null;

        StockBalance balance = new StockBalance();
        balance.setTenantId(tenantId);
        balance.setWarehouse(warehouse);
        balance.setMaterial(material);
        balance.setUom(material.getDisplayUom());
        balance.setQuantity(BigDecimal.ZERO);
        balance.setOpeningQuantity(openingQuantity);
        // Seed the average cost from the supplied opening cost (display UOM); the
        // OPENING_BALANCE transaction recomputes the same value from a zero base.
        balance.setAverageCost(hasOpeningCost ? openingUnitCost : BigDecimal.ZERO);
        balance.setMinimumQuantity(nz(request.getMinimumQuantity()));
        balance.setMaximumQuantity(request.getMaximumQuantity());

        StockBalance saved = stockBalanceRepository.save(balance);

        if (openingQuantity.compareTo(BigDecimal.ZERO) > 0) {
            triggerOpeningBalance(saved, openingUnitCost, actingUserId);
        }

        return mapper.toResponse(saved);
    }

    /**
     * Updates only the stock-control settings (minimum, reorder point, maximum) for an
     * existing balance. On-hand quantity, average cost, and ledger records are untouched.
     */
    @Transactional
    public StockBalanceResponse updateSettings(
            Long warehouseId, Long materialId,
            UpdateStockSettingsRequest request, Long tenantId) {

        StockBalance balance = stockBalanceRepository
            .findByTenantIdAndWarehouseIdAndMaterialId(tenantId, warehouseId, materialId)
            .orElseThrow(() -> new ResourceNotFoundException(
                InventoryErrorCode.RESOURCE_NOT_FOUND,
                "Stock balance not found for material " + materialId
                    + " in warehouse " + warehouseId,
                ErrorParams.of("entityType", "StockBalance",
                    "materialId", materialId, "warehouseId", warehouseId)));

        balance.setMinimumQuantity(nz(request.getMinimumQuantity()));
        balance.setMaximumQuantity(request.getMaximumQuantity());

        return mapper.toResponse(stockBalanceRepository.save(balance));
    }

    /**
     * Resolves the balance row for the movement's (tenant, warehouse, material) tuple, creating
     * and persisting a blank one when none exists yet. It does NOT move the quantity or touch the
     * average — those are written together, once, by {@link #applyMovement} after the batch state
     * (the source of truth for cost) is final for this movement.
     *
     * <p>The row is persisted eagerly (even when freshly created) so that the batch operations the
     * ledger runs between resolve and finalize can link to it and query its open batches by id.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockBalance resolveBalance(InventoryTransaction tx) {
        return stockBalanceRepository
            .findByTenantWarehouseMaterial(
                tx.getTenantId(),
                tx.getWarehouse().getId(),
                tx.getMaterial().getId())
            .orElseGet(() -> stockBalanceRepository.save(createBlank(tx, BigDecimal.ZERO)));
    }

    /**
     * Writes the balance exactly once for a movement whose batch state is now final: it moves the
     * running {@code quantity} by the ledger's signed delta AND re-derives {@code averageCost} from
     * the current open batches, then persists the row in a single save.
     *
     * <p>Quantity and average are kept on different bases by design: the quantity is ledger-driven
     * (signed delta) and may go negative on a FIFO shortfall, while the average is derived purely
     * from the open batches (see {@link #recalculateFromOpenBatches}). The derivation must run after
     * the batch mutations settle, which the ledger guarantees by calling this only once batch
     * creation/consumption for the movement has completed.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockBalance applyMovement(StockBalance balance, InventoryTransaction tx) {
        // The ledger records quantity in the material's stock UOM, whereas the balance is
        // maintained in its display UOM. Convert before applying so the running quantity stays
        // consistent with the batches (which are stored in display UOM).
        BigDecimal magnitude = uomConversionService.convert(
            tx.getStockQuantity(), tx.getStockUom(), balance.getUom(),
            tx.getMaterial(), tx.getTenantId());

        // Signed delta: positive on IN, negative on OUT. Quantity always moves by it.
        BigDecimal signedDelta = tx.getDirection() == InventoryTransactionDirection.IN
            ? magnitude
            : magnitude.negate();
        balance.setQuantity(balance.getQuantity().add(signedDelta));

        // Average cost is derived from the now-final open batches (no-op when no open stock).
        deriveAverageFromOpenBatches(balance);

        // Single coherent write: quantity (ledger delta) and average (batch-derived) together.
        return stockBalanceRepository.save(balance);
    }

    /**
     * Re-derives the balance's {@code averageCost} from its current OPEN batches — the sole
     * source of truth — using the value-weighted formula over open batches only:
     *
     * <pre>
     *   averageCost = sum(remainingQuantity * unitCost) / sum(remainingQuantity)   (OPEN batches)
     * </pre>
     *
     * <p>This is unconditional and type-agnostic: every batch-mutating operation (inbound,
     * FIFO consumption, purchase return, and their reversals) triggers the same recalculation,
     * so there is no notion of a "cost-bearing" movement. It must run in the same transaction
     * as — and after — the batch mutation, so the balance and batches never diverge.
     *
     * <p>Zero-stock edge case: when there are no open batches (sum of remaining quantity is
     * null or zero), the last known average is left unchanged rather than reset to zero. The
     * average is consumed as a valuation basis (e.g. a physical-count surplus opens a batch at
     * the balance's current average, and count freezes stamp it as {@code unitCostAtFreeze}), so
     * carrying it forward keeps that valuation sensible when stock later returns to an empty
     * balance; a stored zero would wrongly value the returning stock at nothing.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void recalculateFromOpenBatches(StockBalance balance) {
        // Persist only when there was open stock to derive from; the zero-stock carry-forward
        // leaves the row untouched (no save), as callers that only re-derive the average rely on.
        if (deriveAverageFromOpenBatches(balance)) {
            stockBalanceRepository.save(balance);
        }
    }

    /**
     * Re-derives {@code averageCost} from the current OPEN batches and writes it onto {@code balance}
     * in memory <em>without persisting</em> — the value-weighted formula documented on
     * {@link #recalculateFromOpenBatches}. Returns {@code true} when there was open stock and the
     * average was (re)computed, {@code false} for the zero-stock case where the last known average is
     * carried forward untouched. Shared by {@link #recalculateFromOpenBatches} (which then saves) and
     * {@link #applyMovement} (which folds this into its single balance write).
     */
    private boolean deriveAverageFromOpenBatches(StockBalance balance) {
        OpenBatchTotals totals = stockBatchRepository.sumOpenBatchTotals(balance.getId());
        BigDecimal totalRemaining = totals != null ? totals.getTotalRemaining() : null;

        if (totalRemaining == null || totalRemaining.compareTo(BigDecimal.ZERO) <= 0) {
            // No open stock — carry the last known average forward (see javadoc).
            return false;
        }

        BigDecimal totalValue = totals.getTotalValue() != null
            ? totals.getTotalValue()
            : BigDecimal.ZERO;
        balance.setAverageCost(totalValue.divide(totalRemaining, SCALE, ROUNDING));
        return true;
    }

    /**
     * Creates the initial stock-balance row for a (tenant, warehouse, material) tuple.
     *
     * When {@code openingQuantity} is greater than zero, an OPENING_BALANCE ledger
     * transaction is recorded for the new balance via {@link OpeningBalanceService}.
     * The ledger-triggered creation path always passes {@code BigDecimal.ZERO}, so
     * it remains a pure blank-balance creation with no extra ledger entry.
     */
    private StockBalance createBlank(InventoryTransaction tx, BigDecimal openingQuantity) {
        StockBalance b = new StockBalance();
        b.setTenantId(tx.getTenantId());
        b.setWarehouse(tx.getWarehouse());
        b.setMaterial(tx.getMaterial());
        // Balances are maintained in the material's display UOM (see applyMovement).
        b.setUom(tx.getMaterial().getDisplayUom());
        b.setQuantity(BigDecimal.ZERO);
        b.setOpeningQuantity(openingQuantity != null ? openingQuantity : BigDecimal.ZERO);
        b.setAverageCost(BigDecimal.ZERO);

        if (b.getOpeningQuantity().compareTo(BigDecimal.ZERO) > 0) {
            StockBalance saved = stockBalanceRepository.save(b);
            // Blank-balance path (brand-new material via the ledger): no user-supplied
            // opening cost, so pass null.
            triggerOpeningBalance(saved, null, tx.getCreatedBy());
            return saved;
        }
        return b;
    }

    private void triggerOpeningBalance(StockBalance balance, BigDecimal openingUnitCost,
                                       Long actingUserId) {
        OpeningBalanceRequest req = new OpeningBalanceRequest();
        req.setWarehouseId(balance.getWarehouse().getId());
        req.setMaterialId(balance.getMaterial().getId());
        req.setQuantity(balance.getOpeningQuantity());
        // uomId null → the opening-balance entered UOM defaults to the material's display UOM,
        // the same unit the opening quantity is expressed in. The opening cost is also per
        // display UOM, so pass it as-is; the ledger normalizes the cost to stock UOM.
        req.setUomId(null);
        req.setUnitCost(openingUnitCost);
        req.setNotes("Opening balance");
        openingBalanceService.create(req, balance.getTenantId(), actingUserId);
    }

    private List<StockBalanceResponse> mapWithOutstandingConsumption(Long tenantId,
                                                                     Long warehouseId,
                                                                     List<StockBalance> balances) {
        var outstandingByMaterial = orderConsumptionAvailabilityService
            .findOutstandingDisplayQuantitiesByMaterial(tenantId, warehouseId);
        return balances.stream()
            .map(balance -> mapper.toResponse(balance,
                balance.getQuantity().subtract(
                    outstandingByMaterial.getOrDefault(balance.getMaterial().getId(), BigDecimal.ZERO))
                    .setScale(SCALE, ROUNDING)))
            .toList();
    }

    private BigDecimal displayedQuantity(Long tenantId, Long warehouseId, StockBalance balance) {
        var outstandingByMaterial = orderConsumptionAvailabilityService
            .findOutstandingDisplayQuantitiesByMaterial(tenantId, warehouseId);
        return balance.getQuantity().subtract(
            outstandingByMaterial.getOrDefault(balance.getMaterial().getId(), BigDecimal.ZERO))
            .setScale(SCALE, ROUNDING);
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
