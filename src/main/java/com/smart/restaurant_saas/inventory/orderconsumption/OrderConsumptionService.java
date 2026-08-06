package com.smart.restaurant_saas.inventory.orderconsumption;

import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.inventory.core.InventoryErrorCode;
import com.smart.restaurant_saas.inventory.core.IdempotencyService;
import com.smart.restaurant_saas.inventory.core.InventoryLedgerService;
import com.smart.restaurant_saas.inventory.core.LedgerCommand;
import com.smart.restaurant_saas.inventory.core.UomConversionService;
import com.smart.restaurant_saas.inventory.core.enums.IdempotencyScope;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionDirection;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionType;
import com.smart.restaurant_saas.inventory.mapper.OrderConsumptionDocMapper;
import com.smart.restaurant_saas.inventory.orderconsumption.dto.OrderConsumptionDocDetailResponse;
import com.smart.restaurant_saas.inventory.orderconsumption.dto.OrderConsumptionDocListResponse;
import com.smart.restaurant_saas.inventory.orderconsumption.dto.OrderConsumptionDocMaterialResponse;
import com.smart.restaurant_saas.inventory.orderconsumption.dto.OrderConsumptionDocResponse;
import com.smart.restaurant_saas.inventory.orderconsumption.dto.OrderConsumptionMaterialsSummaryResponse;
import com.smart.restaurant_saas.inventory.repository.WarehouseRepository;
import com.smart.restaurant_saas.inventory.repository.OpenBatchTotals;
import com.smart.restaurant_saas.inventory.repository.StockBalanceRepository;
import com.smart.restaurant_saas.inventory.repository.StockBatchRepository;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.stock.StockBalance;
import com.smart.restaurant_saas.inventory.uom.Uom;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;
import com.smart.restaurant_saas.menu.recipe.RecipeItem;
import com.smart.restaurant_saas.menu.recipe.RecipeItemRepository;
import com.smart.restaurant_saas.order.core.Order;
import com.smart.restaurant_saas.order.core.OrderLine;
import com.smart.restaurant_saas.order.core.enums.OrderStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderConsumptionService {

    private static final int SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final String REFERENCE_TYPE = "ORDER_CONSUMPTION_DOC";

    private final OrderConsumptionRepository docRepository;
    private final OrderConsumptionLineRepository lineRepository;
    private final OrderConsumptionMaterialRepository materialRepository;
    private final WarehouseRepository warehouseRepository;
    private final RecipeItemRepository recipeItemRepository;
    private final InventoryLedgerService ledgerService;
    private final IdempotencyService idempotencyService;
    private final StockBalanceRepository stockBalanceRepository;
    private final StockBatchRepository stockBatchRepository;
    private final UomConversionService uomConversionService;
    private final OrderConsumptionDocMapper mapper;
    private final PlatformTransactionManager transactionManager;

    @Transactional(readOnly = true)
    public Page<OrderConsumptionDocListResponse> list(
            Long tenantId,
            Long warehouseId,
            OrderConsumptionStatus status,
            LocalDate dateFrom,
            LocalDate dateTo,
            Pageable pageable) {
        LocalDateTime from = dateFrom != null ? dateFrom.atStartOfDay() : null;
        LocalDateTime toExclusive = dateTo != null ? dateTo.plusDays(1).atStartOfDay() : null;
        Page<OrderConsumption> docs = docRepository.findByFilters(
            tenantId, warehouseId, status, from, toExclusive, pageable);
        if (docs.isEmpty()) {
            return docs.map(doc -> mapper.toListResponse(doc, 0));
        }

        Map<Long, Long> lineCounts = lineRepository.countLinesByDocIds(
                docs.getContent().stream().map(OrderConsumption::getId).toList())
            .stream()
            .collect(java.util.stream.Collectors.toMap(DocLineCount::getDocId, DocLineCount::getLineCount));
        return docs.map(doc -> mapper.toListResponse(doc, lineCounts.getOrDefault(doc.getId(), 0L)));
    }

    @Transactional(readOnly = true)
    public OrderConsumptionDocDetailResponse getById(Long docId, Long tenantId) {
        OrderConsumption doc = docRepository.findByIdAndTenantId(docId, tenantId)
            .orElseThrow(() -> notFound(docId));
        List<OrderConsumptionMaterial> materials = materialRepository.findByDocId(docId);
        List<OrderConsumptionLineView> lines = lineRepository.findLinesByDocId(docId);
        return mapper.toDetailResponse(doc, materials, lines);
    }

    /**
     * The doc's materials that did not consume, with the reason each failed. Exposed so callers
     * outside this package can name the affected materials without touching the entity.
     */
    @Transactional(readOnly = true)
    public List<OrderConsumptionDocMaterialResponse> findUnconsumedMaterials(Long docId, Long tenantId) {
        docRepository.findByIdAndTenantId(docId, tenantId)
            .orElseThrow(() -> notFound(docId));
        return materialRepository.findByDocId(docId).stream()
            .filter(material -> !material.isConsumed())
            .map(mapper::toMaterialResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public OrderConsumptionMaterialsSummaryResponse getMaterialsSummary(Long docId, Long tenantId) {
        docRepository.findByIdAndTenantId(docId, tenantId)
            .orElseThrow(() -> notFound(docId));
        List<MaterialSummary> summaries = lineRepository.summarizeMaterialsByDocId(docId, tenantId);
        return mapper.toMaterialsSummaryResponse(docId, summaries);
    }

    @Transactional
    public void recordCompletedOrder(Order order, Long userId) {
        if (order.getStatus() != OrderStatus.COMPLETE) {
            return;
        }
        validateOrderLinesHaveResolvableRecipes(order);

        OrderConsumption doc = findOrCreatePendingDoc(order.getTenantId(), order.getWarehouse().getId(), userId);
        List<Long> orderLineIds = order.getLines().stream().map(OrderLine::getId).toList();
        Set<Long> existingOrderLineIds = new HashSet<>(lineRepository.findExistingOrderLineIds(orderLineIds));
        List<OrderConsumptionLine> lines = new ArrayList<>();
        for (OrderLine orderLine : order.getLines()) {
            if (existingOrderLineIds.contains(orderLine.getId())) {
                continue;
            }
            OrderConsumptionLine line = new OrderConsumptionLine();
            line.setDoc(doc);
            line.setOrderLine(orderLine);
            line.setCreatedBy(userId);
            lines.add(line);
        }
        try {
            lineRepository.saveAll(lines);
        } catch (DataIntegrityViolationException ex) {
            // Duplicate order-completion event (race): a concurrent recordCompletedOrder for the
            // same order already inserted one of these lines. The unique index on order_line_id
            // (uk_order_consumption_line_order_line, V15) is the guard; this catch is the
            // retry-safety net so the calling transaction is not rolled back.
            log.debug("Duplicate order consumption line detected for doc {} (concurrent insert), skipping",
                doc.getId());
        }
    }

    /**
     * D45/D94 manual retry — the "fix the cause, then retry" flow. Enabled for PARTIAL and
     * CONFLICT docs. The doc's existing material rows are reused rather than re-aggregated: a
     * PARTIAL doc is closed to new lines (D94), so no new quantity can have entered. The
     * per-material ledger idempotency key short-circuits materials that already posted.
     */
    @Transactional
    public OrderConsumptionDocResponse recalculate(Long docId, Long tenantId, Long userId) {
        OrderConsumption doc = docRepository.findByIdAndTenantIdForUpdate(docId, tenantId)
            .orElseThrow(() -> notFound(docId));
        if (doc.getStatus() != OrderConsumptionStatus.CONFLICT
                && doc.getStatus() != OrderConsumptionStatus.PARTIAL) {
            throw new BusinessException(InventoryErrorCode.ORDER_CONSUMPTION_RECALCULATE_NOT_CONFLICT,
                "Order consumption doc can only be recalculated from PARTIAL or CONFLICT status",
                ErrorParams.of("entityId", docId, "status", doc.getStatus().name(),
                    "requiredStatuses", List.of(
                        OrderConsumptionStatus.PARTIAL.name(),
                        OrderConsumptionStatus.CONFLICT.name())));
        }

        doc.setStatus(OrderConsumptionStatus.IN_PROGRESS);
        doc.setProcessedAt(null);
        doc.setUpdatedBy(userId);

        processDocConsumption(doc, userId);
        return mapper.toResponse(doc);
    }

    /**
     * D58 claim step. Locks the doc and, if still PENDING, flips it to IN_PROGRESS. Runs as its own
     * short transaction so the IN_PROGRESS transition COMMITS before D29 processing begins — a
     * concurrent order completion then sees the doc is no longer PENDING and lands on a fresh
     * PENDING doc for the warehouse (via {@link #findOrCreatePendingDoc}) instead of racing into the
     * one being processed. Lines already exist on the doc (written at order completion), so there is
     * nothing to bulk-insert here.
     *
     * @return true if this call claimed the doc (PENDING -> IN_PROGRESS); false if it was already
     *         claimed or processed.
     */
    @Transactional
    public boolean claimDoc(Long docId, Long userId) {
        OrderConsumption doc = docRepository.findByIdForUpdate(docId).orElse(null);
        if (doc == null || doc.getStatus() != OrderConsumptionStatus.PENDING) {
            return false;
        }
        doc.setStatus(OrderConsumptionStatus.IN_PROGRESS);
        doc.setProcessedAt(null);
        doc.setUpdatedBy(userId);
        docRepository.save(doc);
        return true;
    }

    /**
     * D58 process step. Runs the D29 3-step algorithm on a doc already claimed (IN_PROGRESS,
     * committed) by {@link #claimDoc}. Separate transaction from the claim, so the IN_PROGRESS
     * commit is visible to concurrent completions before processing runs. No-op if the doc is not
     * IN_PROGRESS (e.g. it was already processed).
     */
    @Transactional
    public void processClaimedDoc(Long docId, Long userId) {
        OrderConsumption doc = docRepository.findByIdForUpdate(docId)
            .orElseThrow(() -> notFound(docId));
        if (doc.getStatus() != OrderConsumptionStatus.IN_PROGRESS) {
            return;
        }
        processDocConsumption(doc, userId);
    }

    /**
     * D29/D30/D94 core: process each of the doc's materials in its own transaction and derive the
     * doc status from the outcomes. Insufficient materials are skipped and leave the doc PARTIAL;
     * technical failures make the doc CONFLICT and take precedence over insufficiency.
     *
     * <p>The material rows are the unit of record. On a doc's first run they are written here,
     * from the D29 aggregation, in this transaction and before any consumption is attempted — not
     * at order completion, which would take a row lock per material on the order-completion path.
     * On a retry they already exist and are reused rather than re-aggregated: a PARTIAL doc is
     * closed to new lines (D94), so the requirement cannot have changed.
     */
    private void processDocConsumption(OrderConsumption doc, Long userId) {
        List<OrderConsumptionMaterial> materials = materialRepository.findByDocId(doc.getId());
        if (materials.isEmpty()) {
            materials = createMaterialRows(doc, userId);
        }

        LocalDateTime now = LocalDateTime.now();
        for (OrderConsumptionMaterial material : materials) {
            try {
                ConsumptionAttempt attempt = recordConsumption(doc, material, now, userId);
                if (attempt.consumed()) {
                    material.markConsumed();
                } else {
                    material.markInsufficient(attempt.availableQuantity());
                }
            } catch (Exception ex) {
                material.markTechnicalFailure(ex);
            }
            material.setUpdatedBy(userId);
        }
        materialRepository.saveAll(materials);

        doc.setProcessedAt(LocalDateTime.now());
        doc.setUpdatedBy(userId);
        doc.setStatus(deriveStatus(materials));
    }

    /**
     * Writes the doc's material rows from the D29 aggregation, all unconsumed. Saved before the
     * consumption loop runs so the rows exist for every material the doc requires, whatever
     * happens to any single one of them.
     */
    private List<OrderConsumptionMaterial> createMaterialRows(OrderConsumption doc, Long userId) {
        List<OrderConsumptionMaterial> materials = aggregateMaterialConsumptions(
            doc, lineRepository.sumRecipeQuantitiesByDocId(doc.getId()), userId);
        return materialRepository.saveAll(materials);
    }

    /**
     * D94 status precedence, derived from the rows rather than tracked alongside them: any
     * technical failure is CONFLICT, otherwise any material still outstanding is PARTIAL,
     * otherwise POSTED. A row left unconsumed with no reason recorded counts as outstanding — the
     * doc is not POSTED while any material has not committed.
     */
    private OrderConsumptionStatus deriveStatus(List<OrderConsumptionMaterial> materials) {
        boolean outstanding = false;
        for (OrderConsumptionMaterial material : materials) {
            if (material.isConsumed()) {
                continue;
            }
            if (material.getFailureReason() == OrderConsumptionFailureReason.TECHNICAL_FAILURE) {
                return OrderConsumptionStatus.CONFLICT;
            }
            outstanding = true;
        }
        return outstanding ? OrderConsumptionStatus.PARTIAL : OrderConsumptionStatus.POSTED;
    }

    private OrderConsumption findOrCreatePendingDoc(Long tenantId, Long warehouseId, Long userId) {
        Warehouse lockedWarehouse = warehouseRepository.findByIdAndTenantIdForUpdate(warehouseId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(InventoryErrorCode.RESOURCE_NOT_FOUND,
                "Warehouse not found: " + warehouseId,
                ErrorParams.of("entityType", "Warehouse", "entityId", warehouseId)));

        return docRepository.findByTenantIdAndWarehouseIdAndStatus(
                tenantId, warehouseId, OrderConsumptionStatus.PENDING)
            .orElseGet(() -> createPendingDoc(tenantId, warehouseId, lockedWarehouse, userId));
    }

    private OrderConsumption createPendingDoc(Long tenantId, Long warehouseId, Warehouse lockedWarehouse, Long userId) {
        try {
            OrderConsumption doc = new OrderConsumption();
            doc.setTenantId(tenantId);
            doc.setWarehouse(lockedWarehouse);
            doc.setStatus(OrderConsumptionStatus.PENDING);
            doc.setCreatedBy(userId);
            return docRepository.saveAndFlush(doc);
        } catch (DataIntegrityViolationException ex) {
            return docRepository.findByTenantIdAndWarehouseIdAndStatus(
                    tenantId, warehouseId, OrderConsumptionStatus.PENDING)
                .orElseThrow(() -> new BusinessException(InventoryErrorCode.ORDER_CONSUMPTION_PENDING_DOC_RACE_LOST,
                    "Concurrent pending order consumption doc creation could not be recovered",
                    ErrorParams.of("tenantId", tenantId, "warehouseId", warehouseId)));
        }
    }

    private void validateOrderLinesHaveResolvableRecipes(Order order) {
        Set<Long> recipeIds = new HashSet<>();
        for (OrderLine line : order.getLines()) {
            if (line.getId() == null || line.getRecipe() == null || line.getRecipe().getId() == null) {
                throw new BusinessException(InventoryErrorCode.ORDER_CONSUMPTION_RECIPE_NOT_RESOLVED,
                    "Order line has no persisted frozen recipe reference",
                    ErrorParams.of("orderId", order.getId(), "orderLineId", line.getId()));
            }
            recipeIds.add(line.getRecipe().getId());
        }

        Map<Long, List<RecipeItem>> itemsByRecipeId = recipeItemRepository.findByRecipeIds(
                List.copyOf(recipeIds), order.getTenantId())
            .stream()
            .collect(java.util.stream.Collectors.groupingBy(item -> item.getRecipe().getId()));
        for (Long recipeId : recipeIds) {
            if (!itemsByRecipeId.containsKey(recipeId)) {
                throw new BusinessException(InventoryErrorCode.ORDER_CONSUMPTION_RECIPE_HAS_NO_ITEMS,
                    "Recipe has no resolvable material items: " + recipeId,
                    ErrorParams.of("recipeId", recipeId, "orderId", order.getId()));
            }
        }
    }

    /**
     * D29 steps 1–2: fold the doc's recipe totals into one entry per material. A material used by
     * several recipes — or by several lines of the same recipe — collapses into a single row.
     *
     * <p>Each row carries both UOM layers (D87): {@code enteredQuantity} in the recipe item's own
     * UOM, which the ledger converts to stock UOM, and {@code requiredQuantity} converted once
     * into the material's display UOM, which is the layer balances and open batches live in and
     * therefore the layer the shortfall check and the availability figure use.
     */
    private List<OrderConsumptionMaterial> aggregateMaterialConsumptions(
            OrderConsumption doc,
            List<RecipeQuantity> recipeQuantities,
            Long userId) {
        if (recipeQuantities.isEmpty()) {
            return List.of();
        }

        Long tenantId = doc.getTenantId();
        Map<Long, List<RecipeItem>> itemsByRecipeId = loadItemsByRecipeId(recipeQuantities, tenantId);
        Map<Long, MutableMaterialConsumption> mutableConsumptions = new HashMap<>();
        for (RecipeQuantity recipeQuantity : recipeQuantities) {
            List<RecipeItem> items = itemsByRecipeId.get(recipeQuantity.getRecipeId());
            if (items == null || items.isEmpty()) {
                throw new BusinessException(InventoryErrorCode.ORDER_CONSUMPTION_RECIPE_HAS_NO_ITEMS,
                    "Recipe has no resolvable material items: " + recipeQuantity.getRecipeId(),
                    ErrorParams.of("recipeId", recipeQuantity.getRecipeId()));
            }
            for (RecipeItem item : items) {
                BigDecimal quantity = item.getQuantity()
                    .multiply(recipeQuantity.getQuantity())
                    .setScale(SCALE, ROUNDING);
                addConsumption(mutableConsumptions, item, quantity);
            }
        }

        List<OrderConsumptionMaterial> materials = new ArrayList<>();
        for (MutableMaterialConsumption value : mutableConsumptions.values()) {
            Uom displayUom = value.material.getDisplayUom();
            OrderConsumptionMaterial material = new OrderConsumptionMaterial();
            material.setDoc(doc);
            material.setMaterial(value.material);
            material.setEnteredQuantity(value.quantity);
            material.setEnteredUom(value.enteredUom);
            material.setRequiredQuantity(uomConversionService.convert(
                value.quantity, value.enteredUom, displayUom, value.material, tenantId));
            material.setRequiredUom(displayUom);
            material.setConsumed(false);
            material.setCreatedBy(userId);
            materials.add(material);
        }
        materials.sort(Comparator.comparing(material -> material.getMaterial().getName()));
        return materials;
    }

    private Map<Long, List<RecipeItem>> loadItemsByRecipeId(List<RecipeQuantity> recipeQuantities, Long tenantId) {
        Set<Long> recipeIds = new HashSet<>();
        for (RecipeQuantity recipeQuantity : recipeQuantities) {
            recipeIds.add(recipeQuantity.getRecipeId());
        }
        return recipeItemRepository.findByRecipeIds(List.copyOf(recipeIds), tenantId)
            .stream()
            .collect(java.util.stream.Collectors.groupingBy(item -> item.getRecipe().getId()));
    }

    private void addConsumption(
            Map<Long, MutableMaterialConsumption> consumptions,
            RecipeItem item,
            BigDecimal quantity) {
        Long materialId = item.getMaterial().getId();
        Uom uom = item.getUom();
        MutableMaterialConsumption existing = consumptions.get(materialId);
        if (existing != null && !existing.enteredUom.getId().equals(uom.getId())) {
            throw new BusinessException(InventoryErrorCode.ORDER_CONSUMPTION_MIXED_UOM,
                "Order consumption aggregation found multiple UOMs for one material",
                ErrorParams.of("materialId", materialId,
                    "firstUomId", existing.enteredUom.getId(), "secondUomId", uom.getId()));
        }
        if (existing == null) {
            consumptions.put(materialId, new MutableMaterialConsumption(
                uom, item.getMaterial(), quantity));
            return;
        }
        existing.quantity = existing.quantity.add(quantity).setScale(SCALE, ROUNDING);
    }

    private LedgerCommand toLedgerCommand(
            OrderConsumption doc,
            OrderConsumptionMaterial material,
            LocalDateTime movementDate,
            Long userId) {
        Long materialId = material.getMaterial().getId();
        return LedgerCommand.builder()
            .tenantId(doc.getTenantId())
            .warehouseId(doc.getWarehouse().getId())
            .materialId(materialId)
            .transactionType(InventoryTransactionType.CONSUMPTION_SUMMARY)
            .direction(InventoryTransactionDirection.OUT)
            .enteredQuantity(material.getEnteredQuantity())
            .enteredUomId(material.getEnteredUom().getId())
            .enteredUnitCost(null)
            .referenceType(REFERENCE_TYPE)
            .referenceId(doc.getId())
            .idempotencyKey("ORDER_CONSUMPTION_DOC:" + doc.getId() + ":MATERIAL:" + materialId)
            .movementDate(movementDate)
            .createdBy(userId)
            .build();
    }

    private ConsumptionAttempt recordConsumption(
            OrderConsumption doc,
            OrderConsumptionMaterial material,
            LocalDateTime movementDate,
            Long userId) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template.execute(status -> {
            LedgerCommand command = toLedgerCommand(doc, material, movementDate, userId);
            if (idempotencyService.exists(
                    doc.getTenantId(), IdempotencyScope.INVENTORY_TRANSACTION,
                    command.getIdempotencyKey())) {
                return new ConsumptionAttempt(true, material.getRequiredQuantity());
            }

            BigDecimal availableQuantity = findAvailableQuantity(doc, material.getMaterial().getId());
            if (availableQuantity.compareTo(material.getRequiredQuantity()) < 0) {
                return new ConsumptionAttempt(false, availableQuantity);
            }

            ledgerService.record(command);
            return new ConsumptionAttempt(true, availableQuantity);
        });
    }

    /** Open-batch total in display UOM (D87 layer 2), directly comparable to requiredQuantity. */
    private BigDecimal findAvailableQuantity(OrderConsumption doc, Long materialId) {
        StockBalance balance = stockBalanceRepository
            .findByTenantIdAndWarehouseIdAndMaterialId(
                doc.getTenantId(), doc.getWarehouse().getId(), materialId)
            .orElse(null);
        if (balance == null) {
            return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
        }
        OpenBatchTotals totals = stockBatchRepository.sumOpenBatchTotals(balance.getId());
        BigDecimal available = totals != null ? totals.getTotalRemaining() : null;
        return (available != null ? available : BigDecimal.ZERO).setScale(SCALE, ROUNDING);
    }

    private ResourceNotFoundException notFound(Long docId) {
        return new ResourceNotFoundException(InventoryErrorCode.RESOURCE_NOT_FOUND,
            "Order consumption document not found: " + docId,
            ErrorParams.of("entityType", "OrderConsumptionDoc", "entityId", docId));
    }

    private static class MutableMaterialConsumption {
        private final Uom enteredUom;
        private final Material material;
        private BigDecimal quantity;

        private MutableMaterialConsumption(Uom enteredUom, Material material, BigDecimal quantity) {
            this.enteredUom = enteredUom;
            this.material = material;
            this.quantity = quantity;
        }
    }

    private record ConsumptionAttempt(boolean consumed, BigDecimal availableQuantity) {
    }
}
