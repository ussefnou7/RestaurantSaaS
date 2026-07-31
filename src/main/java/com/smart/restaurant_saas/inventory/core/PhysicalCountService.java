package com.smart.restaurant_saas.inventory.core;

import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import com.smart.restaurant_saas.inventory.core.enums.CountLineAction;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionDirection;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionType;
import com.smart.restaurant_saas.inventory.core.enums.PhysicalCountStatus;
import com.smart.restaurant_saas.inventory.mapper.PhysicalCountMapper;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.orderconsumption.OrderConsumption;
import com.smart.restaurant_saas.inventory.orderconsumption.OrderConsumptionErrorDetail;
import com.smart.restaurant_saas.inventory.orderconsumption.OrderConsumptionRepository;
import com.smart.restaurant_saas.inventory.orderconsumption.OrderConsumptionService;
import com.smart.restaurant_saas.inventory.orderconsumption.OrderConsumptionStatus;
import com.smart.restaurant_saas.inventory.physicalcount.PhysicalCount;
import com.smart.restaurant_saas.inventory.physicalcount.PhysicalCountLine;
import com.smart.restaurant_saas.inventory.physicalcount.PhysicalCountLineCalculation;
import com.smart.restaurant_saas.inventory.physicalcount.PhysicalCountMovementRow;
import com.smart.restaurant_saas.inventory.physicalcount.PostFreezeMovementSummary;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PhysicalCountRequest;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PhysicalCountResponse;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PhysicalCountSummaryResponse;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PostFreezeMaterialMovementResponse;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PostFreezeMovementsResponse;
import com.smart.restaurant_saas.inventory.physicalcount.dto.UpdateCountedQuantitiesRequest;
import com.smart.restaurant_saas.inventory.physicalcount.dto.UpdateCountedQuantityRequest;
import com.smart.restaurant_saas.inventory.repository.InventoryTransactionRepository;
import com.smart.restaurant_saas.inventory.repository.MaterialRepository;
import com.smart.restaurant_saas.inventory.repository.PhysicalCountLineRepository;
import com.smart.restaurant_saas.inventory.repository.PhysicalCountRepository;
import com.smart.restaurant_saas.inventory.repository.StockBalanceRepository;
import com.smart.restaurant_saas.inventory.repository.WarehouseRepository;
import com.smart.restaurant_saas.inventory.stock.StockBalance;
import com.smart.restaurant_saas.inventory.uom.Uom;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhysicalCountService {

    private static final BigDecimal LARGE_VARIANCE_THRESHOLD = new BigDecimal("500");
    private static final int FREEZE_CONFLICT_MATERIAL_NAME_LIMIT = 5;
    private static final int SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    /** Consumption doc statuses that leave the warehouse's stock not yet fully deducted. */
    private static final List<OrderConsumptionStatus> UNSETTLED_CONSUMPTION_STATUSES = List.of(
        OrderConsumptionStatus.PENDING,
        OrderConsumptionStatus.IN_PROGRESS,
        OrderConsumptionStatus.CONFLICT);

    private final PhysicalCountRepository countRepository;
    private final PhysicalCountLineRepository countLineRepository;
    private final WarehouseRepository warehouseRepository;
    private final MaterialRepository materialRepository;
    private final StockBalanceRepository stockBalanceRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final OrderConsumptionRepository consumptionRepository;
    private final OrderConsumptionService consumptionService;
    private final UomConversionService uomConversionService;
    private final InventoryLedgerService ledgerService;
    private final PhysicalCountMapper mapper;
    private final PlatformTransactionManager transactionManager;

    @Transactional(readOnly = true)
    public List<PhysicalCountSummaryResponse> findAll(Long tenantId) {
        return countRepository.findByTenantIdOrderByScheduledDateDesc(tenantId)
            .stream().map(mapper::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public List<PhysicalCountSummaryResponse> findAllByWarehouse(Long tenantId, Long warehouseId) {
        return countRepository
            .findByTenantIdAndWarehouseIdOrderByScheduledDateDesc(tenantId, warehouseId)
            .stream().map(mapper::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public PhysicalCountResponse findById(Long id, Long tenantId) {
        PhysicalCount count = loadDetailOwned(id, tenantId);
        if (count.getStatus() == PhysicalCountStatus.RECONCILED) {
            // A reconciled count is an audit record. Its persisted values must continue to match
            // the ledger movements it actually produced, regardless of later warehouse activity.
            return mapper.toResponse(count);
        }
        if (count.getFrozenAt() == null) {
            return mapper.toResponse(count);
        }

        Map<Long, PhysicalCountLineCalculation> calculations =
            calculateAdjustedExpectedQuantities(count, tenantId, LocalDateTime.now());
        return mapper.toResponse(count, calculations);
    }

    /**
     * Reports the open-ended activity in this count's warehouse since its freeze cutoff. Read-only
     * and advisory: reconciliation uses only each counted material's subset through its countedAt.
     * There is no time limit and no same-day rule.
     */
    @Transactional(readOnly = true)
    public PostFreezeMovementsResponse findPostFreezeMovements(Long id, Long tenantId) {
        PhysicalCount count = loadOwned(id, tenantId);
        LocalDateTime cutoff = count.getFrozenAt();
        if (cutoff == null) {
            throw new BusinessException(InventoryErrorCode.INVALID_STATE_TRANSITION,
                "A count must be frozen before its post-freeze movements can be reported",
                ErrorParams.of("entityType", "PhysicalCount", "currentStatus", count.getStatus().name(),
                    "requiredStatus", "IN_PROGRESS,RECONCILED", "action", "postFreezeMovements"));
        }

        List<PostFreezeMovementSummary> summaries = transactionRepository
            .summarizeMovementsAfterFreeze(tenantId, count.getWarehouse().getId(), cutoff, count.getId());
        Map<Long, PhysicalCountLine> linesByMaterial = count.getLines().stream()
            .collect(Collectors.toMap(line -> line.getMaterial().getId(), line -> line));
        List<PostFreezeMaterialMovementResponse> materials = summaries.stream()
            .filter(summary -> linesByMaterial.containsKey(summary.getMaterialId()))
            .map(summary -> {
                PhysicalCountLine line = linesByMaterial.get(summary.getMaterialId());
                Material material = line.getMaterial();
                Uom stockUom = material.getStockUom();
                Uom lineUom = line.getUom();
                BigDecimal quantityIn = BigDecimal.ZERO.setScale(SCALE, ROUNDING);
                BigDecimal quantityOut = BigDecimal.ZERO.setScale(SCALE, ROUNDING);
                try {
                    if (summary.getQuantityIn().compareTo(BigDecimal.ZERO) != 0) {
                        quantityIn = uomConversionService.convert(
                            summary.getQuantityIn(), stockUom, lineUom, material, tenantId);
                    }
                    if (summary.getQuantityOut().compareTo(BigDecimal.ZERO) != 0) {
                        quantityOut = uomConversionService.convert(
                            summary.getQuantityOut(), stockUom, lineUom, material, tenantId);
                    }
                } catch (UomConversionException ex) {
                    throw new BusinessException(InventoryErrorCode.UOM_CONVERSION_FAILED,
                        "Cannot convert post-freeze movement totals into the physical-count line UOM",
                        ErrorParams.of("materialId", material.getId(),
                            "materialName", material.getName(),
                            "materialCode", material.getCode(),
                            "fromUom", stockUom.getCode(),
                            "toUom", lineUom.getCode()));
                }
                BigDecimal netQuantity = quantityIn.subtract(quantityOut).setScale(SCALE, ROUNDING);
                return PostFreezeMaterialMovementResponse.builder()
                    .materialId(summary.getMaterialId())
                    .materialCode(summary.getMaterialCode())
                    .materialName(summary.getMaterialName())
                    .materialNameAr(summary.getMaterialNameAr())
                    .uomId(lineUom.getId())
                    .uomSymbol(lineUom.getSymbol())
                    .movementCount(summary.getMovementCount().intValue())
                    .quantityIn(quantityIn)
                    .quantityOut(quantityOut)
                    .netQuantity(netQuantity)
                    .build();
            })
            .toList();
        return mapper.toPostFreezeMovementsResponse(count, summaries, materials);
    }

    /**
     * Creation is unrestricted: a DRAFT count has no snapshot and no netting window, so it cannot
     * conflict with anything. The material-level guard fires at the freeze ({@link #start}) — the
     * only gate (D91).
     */
    @Transactional
    public PhysicalCountResponse create(PhysicalCountRequest request, Long tenantId, Long userId) {
        Warehouse warehouse = resolveWarehouse(request.getWarehouseId(), tenantId);

        PhysicalCount count = new PhysicalCount();
        count.setTenantId(tenantId);
        count.setWarehouse(warehouse);
        count.setStatus(PhysicalCountStatus.DRAFT);
        count.setScheduledDate(request.getScheduledDate());
        count.setNotes(request.getNotes());
        count.setCreatedBy(userId);
        count.setCode("PC-" + warehouse.getCode() + "-" + request.getScheduledDate());

        for (Long materialId : new LinkedHashSet<>(request.getMaterialIds())) {
            count.getLines().add(buildLine(count, materialId, tenantId));
        }

        PhysicalCount saved = countRepository.save(count);
        log.info("Created physical count id={} code={} tenant={} lines={}",
            saved.getId(), saved.getCode(), tenantId, saved.getLines().size());
        return mapper.toResponse(saved);
    }

    @Transactional
    public PhysicalCountResponse addMaterials(Long id, List<Long> materialIds, Long tenantId) {
        PhysicalCount count = loadOwned(id, tenantId);
        if (count.getStatus() != PhysicalCountStatus.DRAFT) {
            throw new BusinessException(InventoryErrorCode.INVALID_STATE_TRANSITION,
                "Materials can only be added to a DRAFT count",
                ErrorParams.of("entityType", "PhysicalCount", "currentStatus", count.getStatus().name(),
                    "requiredStatus", "DRAFT", "action", "addMaterials"));
        }

        Set<Long> existing = count.getLines().stream()
            .map(l -> l.getMaterial().getId())
            .collect(Collectors.toSet());

        for (Long materialId : new LinkedHashSet<>(materialIds)) {
            if (existing.contains(materialId)) {
                continue;
            }
            count.getLines().add(buildLine(count, materialId, tenantId));
        }
        return mapper.toResponse(countRepository.save(count));
    }

    @Transactional
    public PhysicalCountResponse removeMaterials(Long id, List<Long> materialIds, Long tenantId) {
        PhysicalCount count = loadOwned(id, tenantId);
        if (count.getStatus() != PhysicalCountStatus.DRAFT) {
            throw new BusinessException(InventoryErrorCode.INVALID_STATE_TRANSITION,
                "Materials can only be removed from a DRAFT count",
                ErrorParams.of("entityType", "PhysicalCount", "currentStatus", count.getStatus().name(),
                    "requiredStatus", "DRAFT", "action", "removeMaterials"));
        }

        Set<Long> toRemove = new LinkedHashSet<>(materialIds);
        // orphanRemoval = true on the lines collection persists the deletion. Material ids
        // not present in the count are silently skipped (idempotent), mirroring addMaterials.
        count.getLines().removeIf(line -> toRemove.contains(line.getMaterial().getId()));

        return mapper.toResponse(countRepository.save(count));
    }

    @Transactional
    public PhysicalCountResponse start(Long id, Long tenantId, Long userId) {
        PhysicalCount count = loadOwned(id, tenantId);
        if (count.getStatus() != PhysicalCountStatus.DRAFT) {
            throw new BusinessException(InventoryErrorCode.INVALID_STATE_TRANSITION,
                "Only a DRAFT count can be started",
                ErrorParams.of("entityType", "PhysicalCount", "currentStatus", count.getStatus().name(),
                    "requiredStatus", "DRAFT", "action", "start"));
        }

        Long warehouseId = count.getWarehouse().getId();
        List<Long> materialIds = count.getLines().stream()
            .map(l -> l.getMaterial().getId()).toList();

        // Freeze-conflict guard: reject if any of our materials are already frozen by a
        // different IN_PROGRESS count in the same warehouse. Single query — no N+1.
        if (!materialIds.isEmpty()) {
            List<com.smart.restaurant_saas.inventory.physicalcount.MaterialConflictProjection> conflicts =
                countRepository.findFreezeConflicts(tenantId, warehouseId, count.getId(), materialIds);
            if (!conflicts.isEmpty()) {
                String detail = conflicts.stream()
                    .map(c -> "material '" + c.getMaterialName()
                        + "' is held by count " + c.getCountCode() + " (IN_PROGRESS)")
                    .collect(Collectors.joining("; "));
                List<Map<String, Object>> conflictParams = conflicts.stream()
                    .map(c -> ErrorParams.of("materialName", c.getMaterialName(),
                        "conflictingCountCode", c.getCountCode()))
                    .toList();
                throw new BusinessException(InventoryErrorCode.FREEZE_CONFLICT,
                    "Cannot start count: " + detail,
                    ErrorParams.of("conflicts", conflictParams));
            }
        }

        // Settle, then snapshot — back to back. The warehouse's outstanding order consumption is
        // posted to the ledger before the balances are read, so the frozen quantities already
        // account for everything sold. Orders completing after this point flow to the next doc as
        // normal and are simply post-cutoff; order intake is never blocked, paused, or queued.
        settleOutstandingConsumption(tenantId, warehouseId, userId);

        Map<Long, StockBalance> balanceMap = stockBalanceRepository
            .findByWarehouseAndMaterials(tenantId, warehouseId, materialIds).stream()
            .collect(Collectors.toMap(sb -> sb.getMaterial().getId(), sb -> sb));

        for (PhysicalCountLine line : count.getLines()) {
            StockBalance balance = balanceMap.get(line.getMaterial().getId());
            if (balance != null) {
                line.setUom(balance.getUom());
                line.setExpectedQuantity(balance.getQuantity());
                line.setUnitCostAtFreeze(balance.getAverageCost());
            } else {
                line.setExpectedQuantity(BigDecimal.ZERO);
                line.setUnitCostAtFreeze(BigDecimal.ZERO);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        count.setFrozenAt(now);
        count.setStartedAt(now);
        count.setStatus(PhysicalCountStatus.IN_PROGRESS);
        log.info("Started physical count id={} tenant={} frozenAt={}", id, tenantId, now);
        return mapper.toResponse(countRepository.save(count));
    }

    @Transactional
    public PhysicalCountResponse revertToDraft(Long id, Long tenantId, Long userId) {
        PhysicalCount count = loadOwned(id, tenantId);
        if (count.getStatus() != PhysicalCountStatus.IN_PROGRESS) {
            throw new BusinessException(InventoryErrorCode.INVALID_STATE_TRANSITION,
                "Only an IN_PROGRESS count can be reverted to DRAFT",
                ErrorParams.of("entityType", "PhysicalCount", "currentStatus", count.getStatus().name(),
                    "requiredStatus", "IN_PROGRESS", "action", "revertToDraft"));
        }

        count.setFrozenAt(null);
        count.setStartedAt(null);
        count.setStatus(PhysicalCountStatus.DRAFT);

        for (PhysicalCountLine line : count.getLines()) {
            resetLineToDraftState(line);
        }

        log.info("Reverted physical count id={} tenant={} to DRAFT by user={}", id, tenantId, userId);
        return mapper.toResponse(countRepository.save(count));
    }

    @Transactional
    public PhysicalCountResponse updateCountedQuantities(Long id, UpdateCountedQuantitiesRequest request,
                                                         Long tenantId, Long userId) {
        PhysicalCount count = loadOwned(id, tenantId);
        if (count.getStatus() != PhysicalCountStatus.IN_PROGRESS) {
            throw new BusinessException(InventoryErrorCode.INVALID_STATE_TRANSITION,
                "Counted quantities can only be updated while IN_PROGRESS",
                ErrorParams.of("entityType", "PhysicalCount", "currentStatus", count.getStatus().name(),
                    "requiredStatus", "IN_PROGRESS", "action", "updateCountedQuantities"));
        }

        Map<Long, PhysicalCountLine> lineMap = count.getLines().stream()
            .collect(Collectors.toMap(PhysicalCountLine::getId, l -> l));

        LocalDateTime now = LocalDateTime.now();
        for (UpdateCountedQuantityRequest item : request.getLines()) {
            PhysicalCountLine line = lineMap.get(item.getLineId());
            if (line == null) {
                throw new ResourceNotFoundException(InventoryErrorCode.RESOURCE_NOT_FOUND,
                    "Line not found in count: " + item.getLineId(),
                    ErrorParams.of("entityType", "PhysicalCountLine", "entityId", item.getLineId()));
            }
            BigDecimal countedQuantity = item.getCountedQuantity();
            line.setCountedQuantity(countedQuantity);
            line.setCountedAt(countedQuantity != null ? now : null);
            line.setVariance(countedQuantity != null
                ? countedQuantity.subtract(line.getExpectedQuantity()).setScale(SCALE, ROUNDING)
                : null);
            if (item.getNotes() != null) {
                line.setNotes(item.getNotes());
            }
        }
        return mapper.toResponse(countRepository.save(count));
    }

    @Transactional
    public PhysicalCountResponse reconcile(Long id, Long tenantId, Long userId) {
        PhysicalCount count = loadOwned(id, tenantId);
        if (count.getStatus() != PhysicalCountStatus.IN_PROGRESS) {
            throw new BusinessException(InventoryErrorCode.INVALID_STATE_TRANSITION,
                "Only an IN_PROGRESS count can be reconciled",
                ErrorParams.of("entityType", "PhysicalCount", "currentStatus", count.getStatus().name(),
                    "requiredStatus", "IN_PROGRESS", "action", "reconcile"));
        }

        List<PhysicalCountLine> lines = count.getLines();
        boolean allCounted = lines.stream().allMatch(l -> l.getCountedQuantity() != null);
        if (!allCounted) {
            throw new BusinessException(InventoryErrorCode.VALIDATION_FAILED,
                "All lines must have counted quantities",
                ErrorParams.of("field", "countedQuantity"));
        }

        LocalDateTime frozenAt = count.getFrozenAt();
        if (frozenAt == null) {
            throw new BusinessException(InventoryErrorCode.VALIDATION_FAILED,
                "Cannot reconcile a count that carries no freeze timestamp",
                ErrorParams.of("entityType", "PhysicalCount", "entityId", id, "field", "frozenAt"));
        }

        for (PhysicalCountLine line : lines) {
            if (line.getCountedAt() == null) {
                Material material = line.getMaterial();
                throw new BusinessException(InventoryErrorCode.VALIDATION_FAILED,
                    "Cannot reconcile a counted line that carries no count timestamp",
                    ErrorParams.of("entityType", "PhysicalCountLine", "entityId", line.getId(),
                        "field", "countedAt", "materialId", material.getId(),
                        "materialName", material.getName()));
            }
        }

        LocalDateTime now = LocalDateTime.now();
        Long warehouseId = count.getWarehouse().getId();
        Map<Long, PhysicalCountLineCalculation> calculations =
            calculateAdjustedExpectedQuantities(count, tenantId, null);
        for (PhysicalCountLine line : lines) {
            PhysicalCountLineCalculation calculation = calculations.get(line.getId());
            line.setAdjustedExpectedQuantity(calculation.adjustedExpectedQuantity());
            line.setVariance(calculation.variance());
            line.setVarianceValue(calculation.varianceValue());
        }

        // Step 2: post transactions for lines with a variance. A count produces exactly one kind of
        // movement — COUNT_ADJUSTMENT — and the sign carries the meaning: a shortage leaves as an
        // OUT (FIFO-consumed at open-batch cost), a surplus enters as an IN (opening a batch at the
        // current average, per D2). A count never writes a waste-typed row or a waste document;
        // reference_type PHYSICAL_COUNT is what distinguishes a count movement for reporting.
        for (PhysicalCountLine line : lines) {
            int cmp = line.getVariance().compareTo(BigDecimal.ZERO);
            if (cmp == 0) {
                line.setActionTaken(CountLineAction.NO_DIFFERENCE);
                continue;
            }
            InventoryTransactionDirection direction = cmp > 0
                ? InventoryTransactionDirection.IN
                : InventoryTransactionDirection.OUT;

            // Send NO unit cost: the FIFO/batch layer owns costing. A shortage (OUT) is FIFO-
            // consumed at the batches' real prices and the cost of issue is written back by the
            // ledger; a surplus (IN) opens a COUNT_ADJUSTMENT batch valued at the balance's
            // current average cost (see StockBatchService). The frozen cost must not drive it.
            // The correction is dated at this material's count time, which is also the upper bound
            // of its netting window. FIFO remains insertion-id ordered, so back-dating is safe.
            LedgerCommand cmd = LedgerCommand.builder()
                .tenantId(tenantId)
                .warehouseId(warehouseId)
                .materialId(line.getMaterial().getId())
                .transactionType(InventoryTransactionType.COUNT_ADJUSTMENT)
                .direction(direction)
                .enteredQuantity(line.getVariance().abs())
                .enteredUomId(line.getUom().getId())
                .referenceType("PHYSICAL_COUNT")
                .referenceId(count.getId())
                .movementDate(line.getCountedAt())
                .createdBy(userId)
                .build();
            InventoryTransaction tx = ledgerService.record(cmd);

            line.setActionTaken(CountLineAction.ADJUSTMENT);
            line.setAdjustmentTransactionId(tx.getId());
        }

        // Step 3: update StockBalance last count info for adjusted materials
        List<Long> adjustedMaterialIds = lines.stream()
            .filter(l -> l.getVariance().compareTo(BigDecimal.ZERO) != 0)
            .map(l -> l.getMaterial().getId())
            .toList();
        if (!adjustedMaterialIds.isEmpty()) {
            Map<Long, PhysicalCountLine> lineByMaterial = lines.stream()
                .collect(Collectors.toMap(l -> l.getMaterial().getId(), l -> l, (a, b) -> a));
            // "Last count date" tracks the actual reconciliation event, not the planned date.
            List<StockBalance> balances = stockBalanceRepository
                .findByWarehouseAndMaterials(tenantId, warehouseId, adjustedMaterialIds);
            for (StockBalance balance : balances) {
                PhysicalCountLine line = lineByMaterial.get(balance.getMaterial().getId());
                balance.setLastCountDate(now);
                balance.setLastCountQuantity(line != null ? line.getCountedQuantity() : null);
            }
            stockBalanceRepository.saveAll(balances);
        }

        // Step 4: large variance
        BigDecimal totalVarianceValue = lines.stream()
            .map(l -> l.getVarianceValue() != null ? l.getVarianceValue() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        count.setHasLargeVariance(totalVarianceValue.compareTo(LARGE_VARIANCE_THRESHOLD) > 0);
        count.setLargeVarianceValue(totalVarianceValue);

        // Step 5: finalize
        count.setStatus(PhysicalCountStatus.RECONCILED);
        count.setReconciledAt(now);
        count.setReconciledBy(userId);

        // Step 6: persist
        countLineRepository.saveAll(lines);
        log.info("Reconciled physical count id={} tenant={} largeVariance={} totalVarianceValue={}",
            id, tenantId, count.getHasLargeVariance(), totalVarianceValue);
        return mapper.toResponse(countRepository.save(count));
    }

    @Transactional
    public PhysicalCountResponse cancel(Long id, String reason, Long tenantId, Long userId) {
        PhysicalCount count = loadOwned(id, tenantId);
        if (count.getStatus() == PhysicalCountStatus.RECONCILED) {
            throw new BusinessException(InventoryErrorCode.INVALID_STATE_TRANSITION,
                "Reconciled counts cannot be cancelled",
                ErrorParams.of("entityType", "PhysicalCount", "currentStatus", count.getStatus().name(),
                    "action", "cancel"));
        }
        if (count.getStatus() != PhysicalCountStatus.DRAFT
                && count.getStatus() != PhysicalCountStatus.IN_PROGRESS) {
            throw new BusinessException(InventoryErrorCode.INVALID_STATE_TRANSITION,
                "Only DRAFT or IN_PROGRESS counts can be cancelled",
                ErrorParams.of("entityType", "PhysicalCount", "currentStatus", count.getStatus().name(),
                    "requiredStatus", "DRAFT,IN_PROGRESS", "action", "cancel"));
        }
        count.setStatus(PhysicalCountStatus.CANCELLED);
        count.setCancelledAt(LocalDateTime.now());
        count.setCancelledBy(userId);
        count.setCancelReason(reason);
        return mapper.toResponse(countRepository.save(count));
    }

    @Transactional
    public void delete(Long id, Long tenantId) {
        PhysicalCount count = loadOwned(id, tenantId);
        if (count.getStatus() != PhysicalCountStatus.DRAFT
                && count.getStatus() != PhysicalCountStatus.IN_PROGRESS) {
            throw new BusinessException(InventoryErrorCode.INVALID_STATE_TRANSITION,
                "Only DRAFT or IN_PROGRESS physical counts can be deleted",
                ErrorParams.of("entityType", "PhysicalCount", "currentStatus", count.getStatus().name(),
                    "requiredStatus", "DRAFT,IN_PROGRESS", "action", "delete"));
        }
        countRepository.delete(count);
        log.info("Deleted physical count id={} tenant={} status={}", id, tenantId, count.getStatus());
    }

    // =========================================================================
    // Internals
    // =========================================================================

    /**
     * Moves each frozen expectation through its line's count time. Ledger rows are summed in stock
     * UOM first, then the single net is converted to the line/display UOM.
     */
    private Map<Long, PhysicalCountLineCalculation> calculateAdjustedExpectedQuantities(
            PhysicalCount count,
            Long tenantId,
            LocalDateTime uncountedCutoff) {
        List<PhysicalCountLine> lines = count.getLines();
        LocalDateTime frozenAt = count.getFrozenAt();
        Long warehouseId = count.getWarehouse().getId();
        List<Long> materialIds = lines.stream()
            .map(line -> line.getMaterial().getId())
            .toList();
        LocalDateTime maxCutoff = lines.stream()
            .map(line -> line.getCountedAt() != null ? line.getCountedAt() : uncountedCutoff)
            .max(LocalDateTime::compareTo)
            .orElse(frozenAt);
        List<PhysicalCountMovementRow> movements = materialIds.isEmpty()
            ? List.of()
            : transactionRepository.findPhysicalCountMovements(
                tenantId, warehouseId, materialIds, frozenAt, maxCutoff, count.getId());
        Map<Long, List<PhysicalCountMovementRow>> movementsByMaterial = movements.stream()
            .collect(Collectors.groupingBy(PhysicalCountMovementRow::materialId));
        Map<Long, PhysicalCountLineCalculation> calculations = new LinkedHashMap<>();

        for (PhysicalCountLine line : lines) {
            LocalDateTime cutoff = line.getCountedAt() != null
                ? line.getCountedAt()
                : uncountedCutoff;
            boolean provisional = line.getCountedAt() == null;
            BigDecimal netStockQuantity = movementsByMaterial
                .getOrDefault(line.getMaterial().getId(), List.of())
                .stream()
                .filter(movement -> !movement.movementDate().isAfter(cutoff))
                .map(PhysicalCountMovementRow::signedStockQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(SCALE, ROUNDING);

            BigDecimal netLineQuantity = BigDecimal.ZERO.setScale(SCALE, ROUNDING);
            if (netStockQuantity.compareTo(BigDecimal.ZERO) != 0) {
                try {
                    netLineQuantity = uomConversionService.convert(
                        netStockQuantity,
                        line.getMaterial().getStockUom(),
                        line.getUom(),
                        line.getMaterial(),
                        tenantId);
                } catch (UomConversionException ex) {
                    Material material = line.getMaterial();
                    throw new BusinessException(InventoryErrorCode.UOM_CONVERSION_FAILED,
                        "Cannot convert count-window movements into the physical-count line UOM",
                        ErrorParams.of("materialId", material.getId(),
                            "materialName", material.getName(),
                            "materialCode", material.getCode(),
                            "fromUom", material.getStockUom().getCode(),
                            "toUom", line.getUom().getCode()));
                }
            }

            BigDecimal expectedAtCount = line.getExpectedQuantity()
                .add(netLineQuantity)
                .setScale(SCALE, ROUNDING);
            BigDecimal variance = line.getCountedQuantity() != null
                ? line.getCountedQuantity().subtract(expectedAtCount).setScale(SCALE, ROUNDING)
                : null;
            BigDecimal varianceValue = variance != null
                ? variance.abs().multiply(line.getUnitCostAtFreeze())
                : null;
            calculations.put(line.getId(), new PhysicalCountLineCalculation(
                expectedAtCount, variance, varianceValue, provisional));
        }
        return calculations;
    }

    /**
     * Settles the warehouse's outstanding order consumption so the freeze snapshot is taken against
     * fully-posted balances.
     *
     * <p>A PENDING doc is claimed and processed through the very same two service calls the
     * {@code OrderConsumptionBatchingScheduler} makes, in two separate committed transactions: the
     * IN_PROGRESS flip must be visible before D29 runs, so a concurrent order completion branches to
     * a fresh PENDING doc instead of appending lines to the doc being processed (those lines would
     * be marked consumed without ever being consumed). The D29 algorithm is not reimplemented here.
     *
     * <p>Each step runs REQUIRES_NEW: the settlement is real ledger work that must stay committed
     * whether or not this freeze goes on to succeed, and the freeze's own transaction must not hold
     * the doc open across processing.
     */
    private void settleOutstandingConsumption(Long tenantId, Long warehouseId, Long userId) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        Long docId = template.execute(status -> consumptionRepository
            .findFirstByTenantIdAndWarehouseIdAndStatusInOrderByIdAsc(
                tenantId, warehouseId, UNSETTLED_CONSUMPTION_STATUSES)
            .map(OrderConsumption::getId)
            .orElse(null));
        if (docId == null) {
            return;
        }

        if (Boolean.TRUE.equals(template.execute(status -> consumptionService.claimDoc(docId, userId)))) {
            template.executeWithoutResult(status -> consumptionService.processClaimedDoc(docId, userId));
        }

        OrderConsumptionStatus settled = template.execute(status -> consumptionRepository
            .findById(docId)
            .map(OrderConsumption::getStatus)
            .orElse(null));
        if (settled == null || settled == OrderConsumptionStatus.POSTED) {
            return;
        }
        if (settled == OrderConsumptionStatus.CONFLICT) {
            throw consumptionConflict(docId, tenantId, warehouseId);
        }
        // Still PENDING or IN_PROGRESS: the scheduler (or a concurrent freeze) holds the doc and is
        // mid-flight. Snapshotting now would freeze balances that are about to move, so the freeze
        // is refused rather than silently taken against half-settled stock. Retryable.
        throw new BusinessException(InventoryErrorCode.FREEZE_CONSUMPTION_NOT_SETTLED,
            "Order consumption for this warehouse is still being processed; retry the freeze",
            ErrorParams.of("entityType", "OrderConsumptionDoc", "docId", docId,
                "warehouseId", warehouseId, "currentStatus", settled.name()));
    }

    /**
     * Builds the freeze blocker for a CONFLICT doc, carrying the doc id plus the material ids and
     * names from its recorded errorDetails so the UI can name what failed.
     */
    private BusinessException consumptionConflict(Long docId, Long tenantId, Long warehouseId) {
        List<OrderConsumptionErrorDetail> details =
            consumptionService.findErrorDetails(docId, tenantId);
        List<Map<String, Object>> materials =
            (details != null ? details : List.<OrderConsumptionErrorDetail>of()).stream()
                .map(detail -> ErrorParams.of(
                    "materialId", detail.materialId(), "materialName", detail.materialName()))
                .toList();
        String materialNames = formatMaterialNames(details);
        return new BusinessException(InventoryErrorCode.FREEZE_BLOCKED_BY_CONSUMPTION_CONFLICT,
            "Cannot start count: order consumption doc " + docId
                + " is in CONFLICT and must be resolved before the warehouse can be frozen",
            ErrorParams.of("entityType", "OrderConsumptionDoc", "docId", docId,
                "warehouseId", warehouseId, "materials", materials, "materialNames", materialNames));
    }

    private String formatMaterialNames(List<OrderConsumptionErrorDetail> details) {
        List<String> names = (details != null ? details : List.<OrderConsumptionErrorDetail>of()).stream()
            .map(OrderConsumptionErrorDetail::materialName)
            .filter(name -> name != null && !name.isBlank())
            .distinct()
            .toList();
        if (names.isEmpty()) {
            return "";
        }
        String joined = names.stream()
            .limit(FREEZE_CONFLICT_MATERIAL_NAME_LIMIT)
            .collect(Collectors.joining(", "));
        int remaining = names.size() - FREEZE_CONFLICT_MATERIAL_NAME_LIMIT;
        return remaining > 0 ? joined + " … +" + remaining : joined;
    }

    private void resetLineToDraftState(PhysicalCountLine line) {
        line.setExpectedQuantity(BigDecimal.ZERO);
        line.setUnitCostAtFreeze(BigDecimal.ZERO);
        line.setCountedQuantity(null);
        line.setCountedAt(null);
        line.setVariance(null);
        line.setAdjustedExpectedQuantity(null);
        line.setVarianceValue(null);
        line.setActionTaken(CountLineAction.PENDING);
        line.setAdjustmentTransactionId(null);
        line.setNotes(null);
    }

    private PhysicalCountLine buildLine(PhysicalCount count, Long materialId, Long tenantId) {
        Material material = resolveMaterial(materialId, tenantId);
        PhysicalCountLine line = new PhysicalCountLine();
        line.setTenantId(tenantId);
        line.setPhysicalCount(count);
        line.setMaterial(material);
        line.setUom(material.getDisplayUom());
        line.setExpectedQuantity(BigDecimal.ZERO);
        line.setUnitCostAtFreeze(BigDecimal.ZERO);
        line.setActionTaken(CountLineAction.PENDING);
        return line;
    }

    private Warehouse resolveWarehouse(Long warehouseId, Long tenantId) {
        return warehouseRepository.findByIdAndTenantId(warehouseId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(
                InventoryErrorCode.RESOURCE_NOT_FOUND,
                "Warehouse not found: " + warehouseId,
                ErrorParams.of("entityType", "Warehouse", "entityId", warehouseId)));
    }

    private Material resolveMaterial(Long materialId, Long tenantId) {
        return materialRepository.findByIdAndTenantId(materialId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(
                InventoryErrorCode.RESOURCE_NOT_FOUND,
                "Material not found: " + materialId,
                ErrorParams.of("entityType", "Material", "entityId", materialId)));
    }

    private PhysicalCount loadOwned(Long id, Long tenantId) {
        return countRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(
                InventoryErrorCode.RESOURCE_NOT_FOUND,
                "Physical count not found: " + id,
                ErrorParams.of("entityType", "PhysicalCount", "entityId", id)));
    }

    private PhysicalCount loadDetailOwned(Long id, Long tenantId) {
        return countRepository.findDetailByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(
                InventoryErrorCode.RESOURCE_NOT_FOUND,
                "Physical count not found: " + id,
                ErrorParams.of("entityType", "PhysicalCount", "entityId", id)));
    }
}
