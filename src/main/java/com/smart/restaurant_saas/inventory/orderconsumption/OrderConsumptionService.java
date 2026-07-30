package com.smart.restaurant_saas.inventory.orderconsumption;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.inventory.core.InventoryErrorCode;
import com.smart.restaurant_saas.inventory.core.InventoryLedgerService;
import com.smart.restaurant_saas.inventory.core.LedgerCommand;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionDirection;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionType;
import com.smart.restaurant_saas.inventory.mapper.OrderConsumptionDocMapper;
import com.smart.restaurant_saas.inventory.orderconsumption.dto.OrderConsumptionDocDetailResponse;
import com.smart.restaurant_saas.inventory.orderconsumption.dto.OrderConsumptionDocListResponse;
import com.smart.restaurant_saas.inventory.orderconsumption.dto.OrderConsumptionDocResponse;
import com.smart.restaurant_saas.inventory.orderconsumption.dto.OrderConsumptionMaterialsSummaryResponse;
import com.smart.restaurant_saas.inventory.repository.WarehouseRepository;
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
import java.util.Collection;
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
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final OrderConsumptionRepository docRepository;
    private final OrderConsumptionLineRepository lineRepository;
    private final WarehouseRepository warehouseRepository;
    private final RecipeItemRepository recipeItemRepository;
    private final InventoryLedgerService ledgerService;
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
        List<OrderConsumptionErrorDetail> errors = parseErrorDetails(doc);
        List<OrderConsumptionLineView> lines = lineRepository.findLinesByDocId(docId);
        return mapper.toDetailResponse(doc, errors, lines);
    }

    /**
     * The per-material failure details recorded on a CONFLICT doc; null when the doc is not in
     * CONFLICT. Exposed so a caller that a conflicting doc blocks — the physical count freeze — can
     * name the failing materials in its own structured error without re-parsing the JSON itself.
     */
    @Transactional(readOnly = true)
    public List<OrderConsumptionErrorDetail> findErrorDetails(Long docId, Long tenantId) {
        OrderConsumption doc = docRepository.findByIdAndTenantId(docId, tenantId)
            .orElseThrow(() -> notFound(docId));
        return parseErrorDetails(doc);
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
            line.setConsumed(false);
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
     * D45 manual retry — the "fix the cause, then retry" flow. Enabled only for CONFLICT docs; it
     * re-runs the full D29 algorithm on the same doc (never selective). Kept as a single
     * transaction: it is a user-initiated retry on an already-terminal CONFLICT doc, not the
     * automatic trigger path (which splits claim from process — see {@link #claimDoc}).
     */
    @Transactional
    public OrderConsumptionDocResponse recalculate(Long docId, Long tenantId, Long userId) {
        OrderConsumption doc = docRepository.findByIdAndTenantIdForUpdate(docId, tenantId)
            .orElseThrow(() -> notFound(docId));
        if (doc.getStatus() != OrderConsumptionStatus.CONFLICT) {
            throw new BusinessException(InventoryErrorCode.ORDER_CONSUMPTION_RECALCULATE_NOT_CONFLICT,
                "Order consumption doc can only be recalculated from CONFLICT status",
                ErrorParams.of("entityId", docId, "status", doc.getStatus().name()));
        }

        doc.setStatus(OrderConsumptionStatus.IN_PROGRESS);
        doc.setErrorDetails(null);
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
        doc.setErrorDetails(null);
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
     * D29/D30 core: aggregate material consumption for the doc, consume each material in its own
     * try/catch, and set the terminal status — POSTED with all lines consumed on full success, or
     * CONFLICT (no lines consumed) with per-material error details on any failure. Assumes the doc
     * is loaded and already IN_PROGRESS. Reused by both the manual retry and the D58 process step.
     */
    private void processDocConsumption(OrderConsumption doc, Long userId) {
        Long tenantId = doc.getTenantId();
        Long docId = doc.getId();
        Map<Long, MaterialConsumption> consumptions = aggregateMaterialConsumptions(
            tenantId, lineRepository.sumRecipeQuantitiesByDocId(docId));

        List<OrderConsumptionErrorDetail> errors = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (MaterialConsumption consumption : consumptions.values()) {
            try {
                recordConsumption(doc, consumption, now, userId);
            } catch (Exception ex) {
                errors.add(new OrderConsumptionErrorDetail(
                    consumption.materialId(),
                    consumption.materialName(),
                    ex.getClass().getName(),
                    ex.getMessage()));
            }
        }

        doc.setProcessedAt(LocalDateTime.now());
        doc.setUpdatedBy(userId);
        if (errors.isEmpty()) {
            doc.setStatus(OrderConsumptionStatus.POSTED);
            doc.setErrorDetails(null);
            lineRepository.updateConsumedByDocId(docId, true);
        } else {
            doc.setStatus(OrderConsumptionStatus.CONFLICT);
            doc.setErrorDetails(toJson(errors));
            lineRepository.updateConsumedByDocId(docId, false);
        }
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

    private Map<Long, MaterialConsumption> aggregateMaterialConsumptions(
            Long tenantId,
            List<RecipeQuantity> recipeQuantities) {
        if (recipeQuantities.isEmpty()) {
            return Map.of();
        }

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

        Map<Long, MaterialConsumption> consumptions = new HashMap<>();
        for (Map.Entry<Long, MutableMaterialConsumption> entry : mutableConsumptions.entrySet()) {
            MutableMaterialConsumption value = entry.getValue();
            consumptions.put(entry.getKey(), new MaterialConsumption(
                entry.getKey(), value.materialName, value.uomId, value.quantity));
        }
        return consumptions;
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
        if (existing != null && !existing.uomId.equals(uom.getId())) {
            throw new BusinessException(InventoryErrorCode.ORDER_CONSUMPTION_MIXED_UOM,
                "Order consumption aggregation found multiple UOMs for one material",
                ErrorParams.of("materialId", materialId,
                    "firstUomId", existing.uomId, "secondUomId", uom.getId()));
        }
        if (existing == null) {
            consumptions.put(materialId, new MutableMaterialConsumption(
                item.getMaterial().getName(), uom.getId(), quantity));
            return;
        }
        existing.quantity = existing.quantity.add(quantity).setScale(SCALE, ROUNDING);
    }

    private LedgerCommand toLedgerCommand(
            OrderConsumption doc,
            MaterialConsumption consumption,
            LocalDateTime movementDate,
            Long userId) {
        return LedgerCommand.builder()
            .tenantId(doc.getTenantId())
            .warehouseId(doc.getWarehouse().getId())
            .materialId(consumption.materialId())
            .transactionType(InventoryTransactionType.CONSUMPTION_SUMMARY)
            .direction(InventoryTransactionDirection.OUT)
            .enteredQuantity(consumption.quantity())
            .enteredUomId(consumption.uomId())
            .enteredUnitCost(null)
            .referenceType(REFERENCE_TYPE)
            .referenceId(doc.getId())
            .idempotencyKey("ORDER_CONSUMPTION_DOC:" + doc.getId() + ":MATERIAL:" + consumption.materialId())
            .movementDate(movementDate)
            .createdBy(userId)
            .build();
    }

    private void recordConsumption(
            OrderConsumption doc,
            MaterialConsumption consumption,
            LocalDateTime movementDate,
            Long userId) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.executeWithoutResult(status ->
            ledgerService.record(toLedgerCommand(doc, consumption, movementDate, userId)));
    }

    private String toJson(Collection<OrderConsumptionErrorDetail> errors) {
        try {
            return OBJECT_MAPPER.writeValueAsString(errors);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(InventoryErrorCode.ORDER_CONSUMPTION_ERROR_SERIALIZATION_FAILED,
                "Could not serialize order consumption error details",
                ErrorParams.of("errorCount", errors.size()));
        }
    }

    private List<OrderConsumptionErrorDetail> parseErrorDetails(OrderConsumption doc) {
        if (doc.getStatus() != OrderConsumptionStatus.CONFLICT || doc.getErrorDetails() == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(
                doc.getErrorDetails(), new TypeReference<List<OrderConsumptionErrorDetail>>() {});
        } catch (JsonProcessingException ex) {
            throw new BusinessException(InventoryErrorCode.ORDER_CONSUMPTION_ERROR_SERIALIZATION_FAILED,
                "Could not deserialize order consumption error details",
                ErrorParams.of("entityId", doc.getId()));
        }
    }

    private ResourceNotFoundException notFound(Long docId) {
        return new ResourceNotFoundException(InventoryErrorCode.RESOURCE_NOT_FOUND,
            "Order consumption document not found: " + docId,
            ErrorParams.of("entityType", "OrderConsumptionDoc", "entityId", docId));
    }

    private static class MutableMaterialConsumption {
        private final String materialName;
        private final Long uomId;
        private BigDecimal quantity;

        private MutableMaterialConsumption(String materialName, Long uomId, BigDecimal quantity) {
            this.materialName = materialName;
            this.uomId = uomId;
            this.quantity = quantity;
        }
    }
}
