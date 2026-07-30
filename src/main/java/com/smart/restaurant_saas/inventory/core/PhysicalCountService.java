package com.smart.restaurant_saas.inventory.core;

import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smart.restaurant_saas.inventory.core.enums.CountLineAction;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionDirection;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionType;
import com.smart.restaurant_saas.inventory.core.enums.PhysicalCountStatus;
import com.smart.restaurant_saas.inventory.mapper.PhysicalCountMapper;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.physicalcount.PhysicalCount;
import com.smart.restaurant_saas.inventory.physicalcount.PhysicalCountLine;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PhysicalCountRequest;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PhysicalCountResponse;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PhysicalCountSummaryResponse;
import com.smart.restaurant_saas.inventory.physicalcount.dto.ReconcileCountRequest;
import com.smart.restaurant_saas.inventory.physicalcount.dto.ReconcileLineAction;
import com.smart.restaurant_saas.inventory.physicalcount.dto.UpdateCountedQuantitiesRequest;
import com.smart.restaurant_saas.inventory.physicalcount.dto.UpdateCountedQuantityRequest;
import com.smart.restaurant_saas.inventory.repository.MaterialRepository;
import com.smart.restaurant_saas.inventory.repository.PhysicalCountLineRepository;
import com.smart.restaurant_saas.inventory.repository.PhysicalCountRepository;
import com.smart.restaurant_saas.inventory.repository.StockBalanceRepository;
import com.smart.restaurant_saas.inventory.repository.WarehouseRepository;
import com.smart.restaurant_saas.inventory.stock.StockBalance;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;

@Slf4j
@Service
@RequiredArgsConstructor
public class PhysicalCountService {

    private static final BigDecimal LARGE_VARIANCE_THRESHOLD = new BigDecimal("500");

    private final PhysicalCountRepository countRepository;
    private final PhysicalCountLineRepository countLineRepository;
    private final WarehouseRepository warehouseRepository;
    private final MaterialRepository materialRepository;
    private final StockBalanceRepository stockBalanceRepository;
    private final InventoryLedgerService ledgerService;
    private final PhysicalCountMapper mapper;

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
        return mapper.toResponse(loadOwned(id, tenantId));
    }

    @Transactional
    public PhysicalCountResponse create(PhysicalCountRequest request, Long tenantId, Long userId) {
        Warehouse warehouse = resolveWarehouse(request.getWarehouseId(), tenantId);

        if (countRepository.existsByTenantIdAndWarehouseIdAndScheduledDateAndStatusIn(
                tenantId, warehouse.getId(), request.getScheduledDate(),
                java.util.List.of(PhysicalCountStatus.DRAFT, PhysicalCountStatus.IN_PROGRESS))) {
            throw new BusinessException(InventoryErrorCode.DUPLICATE_OPERATION,
                "An active physical count already exists for this warehouse on " + request.getScheduledDate(),
                ErrorParams.of("entityType", "PhysicalCount",
                    "warehouseId", warehouse.getId(), "scheduledDate", request.getScheduledDate()));
        }

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

        Map<Long, StockBalance> balanceMap = stockBalanceRepository
            .findByWarehouseAndMaterials(tenantId, warehouseId, materialIds).stream()
            .collect(Collectors.toMap(sb -> sb.getMaterial().getId(), sb -> sb));

        for (PhysicalCountLine line : count.getLines()) {
            StockBalance balance = balanceMap.get(line.getMaterial().getId());
            line.setExpectedQuantity(balance != null ? balance.getQuantity() : BigDecimal.ZERO);
            line.setUnitCostAtFreeze(balance != null ? balance.getAverageCost() : BigDecimal.ZERO);
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
            line.setCountedQuantity(item.getCountedQuantity());
            line.setCountedAt(now);
            line.setVariance(item.getCountedQuantity().subtract(line.getExpectedQuantity()));
            if (item.getNotes() != null) {
                line.setNotes(item.getNotes());
            }
        }
        return mapper.toResponse(countRepository.save(count));
    }

    @Transactional
    public PhysicalCountResponse reconcile(Long id, ReconcileCountRequest request,
                                           Long tenantId, Long userId) {
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

        // The freeze instant is the cutoff this count measures against, and the business date
        // every resulting movement carries. It is always set on an IN_PROGRESS count (start()
        // writes it); the guard exists so a corrupt row fails loudly here rather than silently
        // falling back to the record date inside InventoryLedgerService.
        LocalDateTime cutoff = count.getFrozenAt();
        if (cutoff == null) {
            throw new BusinessException(InventoryErrorCode.VALIDATION_FAILED,
                "Cannot reconcile a count that carries no freeze timestamp",
                ErrorParams.of("entityType", "PhysicalCount", "entityId", id, "field", "frozenAt"));
        }
        LocalDateTime now = LocalDateTime.now();
        Long warehouseId = count.getWarehouse().getId();

        // Step 1: variance is measured against the frozen snapshot, never re-derived. A count
        // answers "what was actually here at the cutoff", so movements recorded after the freeze
        // belong to the periods after it and must not be netted back into the expected quantity —
        // netting them and then dating the correction at the cutoff would double-count them.
        // Those movements are reported, read-only, by findPostFreezeMovements.
        for (PhysicalCountLine line : lines) {
            line.setVariance(line.getCountedQuantity().subtract(line.getExpectedQuantity()));
            line.setVarianceValue(line.getVariance().abs().multiply(line.getUnitCostAtFreeze()));
        }

        // Step 2: build action map and validate WASTE only on negative variance
        Map<Long, CountLineAction> actionMap = request.getLines().stream()
            .collect(Collectors.toMap(ReconcileLineAction::getLineId, ReconcileLineAction::getAction));

        Map<Long, PhysicalCountLine> lineById = lines.stream()
            .collect(Collectors.toMap(PhysicalCountLine::getId, l -> l));
        for (Map.Entry<Long, CountLineAction> entry : actionMap.entrySet()) {
            if (entry.getValue() == CountLineAction.WASTE) {
                PhysicalCountLine line = lineById.get(entry.getKey());
                if (line == null) {
                    throw new ResourceNotFoundException(InventoryErrorCode.RESOURCE_NOT_FOUND,
                        "Line not found in count: " + entry.getKey(),
                        ErrorParams.of("entityType", "PhysicalCountLine", "entityId", entry.getKey()));
                }
                if (line.getVariance().compareTo(BigDecimal.ZERO) >= 0) {
                    throw new BusinessException(
                        InventoryErrorCode.WASTE_NOT_ALLOWED_POSITIVE_VARIANCE,
                        "WASTE action is only allowed for a negative variance on material: "
                            + line.getMaterial().getName(),
                        ErrorParams.of("materialName", line.getMaterial().getName()));
                }
            }
        }

        // Step 3: post transactions for lines with a variance
        for (PhysicalCountLine line : lines) {
            int cmp = line.getVariance().compareTo(BigDecimal.ZERO);
            if (cmp == 0) {
                line.setActionTaken(CountLineAction.NO_DIFFERENCE);
                continue;
            }
            CountLineAction action = actionMap.getOrDefault(line.getId(), CountLineAction.ADJUSTMENT);
            InventoryTransactionType txType = action == CountLineAction.WASTE
                ? InventoryTransactionType.WASTE
                : InventoryTransactionType.COUNT_ADJUSTMENT;
            InventoryTransactionDirection direction = cmp > 0
                ? InventoryTransactionDirection.IN
                : InventoryTransactionDirection.OUT;

            // Send NO unit cost: the FIFO/batch layer owns costing. A shortage (OUT) is FIFO-
            // consumed at the batches' real prices and the cost of issue is written back by the
            // ledger; a surplus (IN) opens a COUNT_ADJUSTMENT batch valued at the balance's
            // current average cost (see StockBatchService). The frozen cost must not drive it.
            // movementDate is the CUTOFF, not the posting time: a count frozen on 30 June and
            // reconciled on 2 July lands in June, because that is when the discrepancy existed.
            // FIFO batch ordering remains insertion-id based, so back-dating cannot reorder it.
            LedgerCommand cmd = LedgerCommand.builder()
                .tenantId(tenantId)
                .warehouseId(warehouseId)
                .materialId(line.getMaterial().getId())
                .transactionType(txType)
                .direction(direction)
                .enteredQuantity(line.getVariance().abs())
                .enteredUomId(line.getUom().getId())
                .referenceType("PHYSICAL_COUNT")
                .referenceId(count.getId())
                .movementDate(cutoff)
                .createdBy(userId)
                .build();
            InventoryTransaction tx = ledgerService.record(cmd);

            line.setActionTaken(action);
            if (action == CountLineAction.WASTE) {
                line.setWasteTransactionId(tx.getId());
            } else {
                line.setAdjustmentTransactionId(tx.getId());
            }
        }

        // Step 4: update StockBalance last count info for adjusted materials
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

        // Step 5: large variance
        BigDecimal totalVarianceValue = lines.stream()
            .map(l -> l.getVarianceValue() != null ? l.getVarianceValue() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        count.setHasLargeVariance(totalVarianceValue.compareTo(LARGE_VARIANCE_THRESHOLD) > 0);
        count.setLargeVarianceValue(totalVarianceValue);

        // Step 6: finalize
        count.setStatus(PhysicalCountStatus.RECONCILED);
        count.setReconciledAt(now);
        count.setReconciledBy(userId);

        // Step 7: persist
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
        line.setWasteTransactionId(null);
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
}
