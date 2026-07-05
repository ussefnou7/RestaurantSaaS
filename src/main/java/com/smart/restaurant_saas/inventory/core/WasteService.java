package com.smart.restaurant_saas.inventory.core;

import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smart.restaurant_saas.inventory.core.enums.DocumentStatus;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionDirection;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionType;
import com.smart.restaurant_saas.inventory.mapper.WasteDocumentMapper;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.purchase.InvoiceSequenceService;
import com.smart.restaurant_saas.inventory.repository.MaterialRepository;
import com.smart.restaurant_saas.inventory.repository.StockBalanceRepository;
import com.smart.restaurant_saas.inventory.repository.UomRepository;
import com.smart.restaurant_saas.inventory.repository.WarehouseRepository;
import com.smart.restaurant_saas.inventory.repository.WasteDocumentRepository;
import com.smart.restaurant_saas.inventory.stock.StockBalance;
import com.smart.restaurant_saas.inventory.uom.Uom;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;
import com.smart.restaurant_saas.inventory.waste.MaterialShortfall;
import com.smart.restaurant_saas.inventory.waste.WasteDocument;
import com.smart.restaurant_saas.inventory.waste.WasteLine;
import com.smart.restaurant_saas.inventory.waste.dto.WasteDocumentRequest;
import com.smart.restaurant_saas.inventory.waste.dto.WasteDocumentResponse;
import com.smart.restaurant_saas.inventory.waste.dto.WasteLineRequest;
import com.smart.restaurant_saas.inventory.waste.dto.WasteUpdateLineRequest;
import com.smart.restaurant_saas.inventory.waste.dto.UncompleteWasteRequest;

/**
 * Lifecycle service for waste documents (stock write-offs), mirroring the purchase
 * invoice/return document services: DRAFT → COMPLETE → POSTED → CANCELLED, lines managed
 * line-by-line, all loads tenant-scoped.
 *
 * On POST the document issues one WASTE / direction OUT ledger transaction per line. The
 * document supplies no cost — the ledger FIFO-depletes the material's batches and computes
 * the actual cost of issue. Before any ledger call, an availability guard verifies that every
 * material's total waste quantity (aggregated across lines, converted into the balance's UOM)
 * does not exceed the current on-hand quantity. The guard runs fully first, so a shortfall on
 * any material aborts the whole post and the consumption logic never sees an over-issue.
 *
 * At COMPLETE, advisory shortfalls are computed once via {@link #computeShortfalls} and
 * persisted to {@code waste_document_warning}. GET endpoints return the stored rows — no
 * balance query on read.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WasteService {

    private final WasteDocumentRepository wasteRepository;
    private final WarehouseRepository warehouseRepository;
    private final MaterialRepository materialRepository;
    private final UomRepository uomRepository;
    private final StockBalanceRepository stockBalanceRepository;
    private final InventoryLedgerService ledgerService;
    private final UomConversionService uomConversionService;
    private final InvoiceSequenceService invoiceSequenceService;
    private final WasteDocumentMapper mapper;

    public static final String REFERENCE_TYPE = "WASTE_DOCUMENT";

    @Transactional(readOnly = true)
    public List<WasteDocumentResponse> findAll(Long tenantId) {
        return wasteRepository.findByTenantIdOrderByWasteDateDesc(tenantId)
            .stream().map(mapper::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public List<WasteDocumentResponse> findAllByWarehouse(Long tenantId, Long warehouseId) {
        return wasteRepository
            .findByTenantIdAndWarehouseIdOrderByWasteDateDesc(tenantId, warehouseId)
            .stream().map(mapper::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public WasteDocumentResponse findById(Long id, Long tenantId) {
        return mapper.toResponse(loadOwned(id, tenantId));
    }

    @Transactional
    public WasteDocumentResponse create(WasteDocumentRequest request, Long tenantId, Long userId) {
        Warehouse warehouse = warehouseRepository
            .findByIdAndTenantId(request.getWarehouseId(), tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(InventoryErrorCode.RESOURCE_NOT_FOUND,
                "Warehouse not found: " + request.getWarehouseId(),
                ErrorParams.of("entityType", "Warehouse", "entityId", request.getWarehouseId())));

        WasteDocument doc = new WasteDocument();
        doc.setTenantId(tenantId);
        doc.setWarehouse(warehouse);
        doc.setCode(invoiceSequenceService.generateWasteNumber(tenantId));
        doc.setWasteDate(request.getWasteDate());
        doc.setReasonCode(request.getReasonCode());
        doc.setNotes(request.getNotes());
        doc.setStatus(DocumentStatus.DRAFT);
        doc.setPostedToInventory(false);
        doc.setCreatedBy(userId);
        // No lines on create — added via POST /{id}/lines.

        WasteDocument saved = wasteRepository.save(doc);
        log.info("Created waste document id={} tenant={} code={} warehouse={}",
            saved.getId(), tenantId, saved.getCode(), warehouse.getId());
        return mapper.toResponse(saved);
    }

    @Transactional
    public WasteDocumentResponse update(Long id, WasteDocumentRequest request,
                                        Long tenantId, Long userId) {
        WasteDocument doc = loadOwned(id, tenantId);
        requireDraft(doc);
        // Header-only update. warehouse is fixed for the life of the document.
        doc.setWasteDate(request.getWasteDate());
        doc.setReasonCode(request.getReasonCode());
        doc.setNotes(request.getNotes());
        doc.setUpdatedBy(userId);
        return mapper.toResponse(wasteRepository.save(doc));
    }

    @Transactional
    public WasteDocumentResponse addLine(Long docId, WasteLineRequest request,
                                         Long tenantId, Long userId) {
        WasteDocument doc = loadOwned(docId, tenantId);
        requireDraft(doc);

        Material material = loadMaterial(request.getMaterialId(), tenantId);
        Uom uom = resolveUom(request.getUomId(), material, tenantId);

        WasteLine line = new WasteLine();
        line.setWasteDocument(doc);
        line.setMaterial(material);
        line.setQuantity(request.getQuantity());
        line.setUom(uom);
        line.setNotes(request.getNotes());
        doc.getLines().add(line);

        doc.setUpdatedBy(userId);
        log.info("Added line to waste document id={} tenant={} material={}",
            docId, tenantId, material.getId());
        return mapper.toResponse(wasteRepository.save(doc));
    }

    @Transactional
    public WasteDocumentResponse updateLine(Long docId, Long lineId,
                                            WasteUpdateLineRequest request,
                                            Long tenantId, Long userId) {
        WasteDocument doc = loadOwned(docId, tenantId);
        requireDraft(doc);

        WasteLine line = findLine(doc, lineId);
        // The material cannot change here; the UOM may, as long as it stays convertible.
        Uom uom = resolveUom(request.getUomId(), line.getMaterial(), tenantId);
        line.setQuantity(request.getQuantity());
        line.setUom(uom);
        line.setNotes(request.getNotes());

        doc.setUpdatedBy(userId);
        log.info("Updated line id={} on waste document id={} tenant={}", lineId, docId, tenantId);
        return mapper.toResponse(wasteRepository.save(doc));
    }

    @Transactional
    public WasteDocumentResponse deleteLine(Long docId, Long lineId, Long tenantId, Long userId) {
        WasteDocument doc = loadOwned(docId, tenantId);
        requireDraft(doc);

        WasteLine line = findLine(doc, lineId);
        doc.getLines().remove(line);

        doc.setUpdatedBy(userId);
        log.info("Deleted line id={} from waste document id={} tenant={}", lineId, docId, tenantId);
        return mapper.toResponse(wasteRepository.save(doc));
    }

    @Transactional
    public WasteDocumentResponse complete(Long id, Long tenantId, Long userId) {
        WasteDocument doc = loadOwned(id, tenantId);
        if (doc.getStatus() != DocumentStatus.DRAFT) {
            throw new BusinessException(InventoryErrorCode.INVALID_STATE_TRANSITION,
                "Only DRAFT waste documents can be completed",
                ErrorParams.of("entityType", "WasteDocument", "currentStatus", doc.getStatus().name(),
                    "requiredStatus", "DRAFT", "action", "complete"));
        }
        if (doc.getLines().isEmpty()) {
            throw new BusinessException(InventoryErrorCode.EMPTY_DOCUMENT_LINES,
                "Cannot complete a waste document with no lines",
                ErrorParams.of("documentType", "WasteDocument"));
        }

        // Compute shortfalls once and persist as advisory warnings in the JSON column.
        // Does NOT block completion. The list is written into the same waste_document row —
        // no child inserts, no extra queries on subsequent reads.
        List<MaterialShortfall> shortfalls =
            computeShortfalls(doc, tenantId, doc.getWarehouse().getId());
        doc.setStockWarnings(shortfalls);

        doc.setStatus(DocumentStatus.COMPLETE);
        doc.setCompletedAt(java.time.LocalDateTime.now());
        doc.setCompletedBy(userId);
        return mapper.toResponse(wasteRepository.save(doc));
    }

    @Transactional
    public WasteDocumentResponse uncomplete(Long id, UncompleteWasteRequest request, Long tenantId, Long userId) {
        WasteDocument doc = loadOwned(id, tenantId);
        if (doc.getStatus() != DocumentStatus.COMPLETE) {
            throw new BusinessException(InventoryErrorCode.INVALID_STATE_TRANSITION,
                "Only COMPLETE waste documents can be uncompleted",
                ErrorParams.of("entityType", "WasteDocument", "currentStatus", doc.getStatus().name(),
                    "requiredStatus", "COMPLETE", "action", "uncomplete"));
        }

        String reason = request != null ? request.getReason() : null;
        doc.setStatus(DocumentStatus.DRAFT);
        doc.setUnCompletedAt(LocalDateTime.now());
        doc.setUnCompletedBy(userId);
        doc.setUpdatedBy(userId);
        doc.setStockWarnings(new ArrayList<>());
        log.info("UnCompleted waste document id={} tenant={} reason={}",
            doc.getId(), tenantId, reason);
        return mapper.toResponse(wasteRepository.save(doc));
    }

    @Transactional
    public WasteDocumentResponse post(Long id, Long tenantId, Long userId) {
        WasteDocument doc = loadOwned(id, tenantId);
        if (doc.getStatus() != DocumentStatus.COMPLETE) {
            throw new BusinessException(InventoryErrorCode.INVALID_STATE_TRANSITION,
                "Only COMPLETE waste documents can be posted",
                ErrorParams.of("entityType", "WasteDocument", "currentStatus", doc.getStatus().name(),
                    "requiredStatus", "COMPLETE", "action", "post"));
        }
        if (Boolean.TRUE.equals(doc.getPostedToInventory())) {
            throw new BusinessException(InventoryErrorCode.ALREADY_PROCESSED,
                "Waste document is already posted to inventory",
                ErrorParams.of("entityType", "WasteDocument", "entityId", doc.getId(), "action", "post"));
        }

        Long warehouseId = doc.getWarehouse().getId();

        // Step 1: availability guard — runs fully BEFORE any ledger call (all-or-nothing).
        // No stock may be written off that isn't on hand, so the FIFO consumption never sees
        // an over-issue.
        assertSufficientStock(doc, tenantId, warehouseId);

        // Step 2: issue a WASTE / direction OUT ledger transaction per line. The document
        // supplies no cost (enteredUnitCost null) — the ledger FIFO-depletes batches and
        // writes back the actual cost of issue.
        for (WasteLine line : doc.getLines()) {
            LedgerCommand cmd = LedgerCommand.builder()
                .tenantId(tenantId)
                .warehouseId(warehouseId)
                .materialId(line.getMaterial().getId())
                .transactionType(InventoryTransactionType.WASTE)
                .direction(InventoryTransactionDirection.OUT)
                .enteredQuantity(line.getQuantity())
                .enteredUomId(line.getUom().getId())
                .enteredUnitCost(null)
                .reasonCode(doc.getReasonCode().name())
                .referenceType(REFERENCE_TYPE)
                .referenceId(doc.getId())
                .movementDate(doc.getWasteDate().atStartOfDay())
                .createdBy(userId)
                .build();
            ledgerService.record(cmd);
        }

        // Step 3: mark the document posted.
        doc.setPostedToInventory(true);
        doc.setPostedAt(java.time.LocalDateTime.now());
        doc.setPostedBy(userId);
        doc.setStatus(DocumentStatus.POSTED);
        log.info("Posted waste document id={} tenant={} lines={}",
            doc.getId(), tenantId, doc.getLines().size());
        return mapper.toResponse(wasteRepository.save(doc));
    }

    @Transactional
    public WasteDocumentResponse cancel(Long id, String reason, Long tenantId, Long userId) {
        WasteDocument doc = loadOwned(id, tenantId);
        if (doc.getStatus() == DocumentStatus.POSTED) {
            throw new BusinessException(InventoryErrorCode.INVALID_STATE_TRANSITION,
                "Posted waste documents cannot be cancelled",
                ErrorParams.of("entityType", "WasteDocument", "currentStatus", doc.getStatus().name(),
                    "action", "cancel"));
        }
        if (doc.getStatus() != DocumentStatus.DRAFT
                && doc.getStatus() != DocumentStatus.COMPLETE) {
            throw new BusinessException(InventoryErrorCode.INVALID_STATE_TRANSITION,
                "Only DRAFT or COMPLETE waste documents can be cancelled",
                ErrorParams.of("entityType", "WasteDocument", "currentStatus", doc.getStatus().name(),
                    "requiredStatus", "DRAFT,COMPLETE", "action", "cancel"));
        }
        doc.setStatus(DocumentStatus.CANCELLED);
        doc.setCancelledAt(java.time.LocalDateTime.now());
        doc.setCancelledBy(userId);
        doc.setCancelReason(reason);
        return mapper.toResponse(wasteRepository.save(doc));
    }

    // =========================================================================
    // Internals
    // =========================================================================

    /**
     * Aggregates the waste quantity per material (all lines, converted to the balance's UOM),
     * then compares against on-hand stock. Returns one {@link MaterialShortfall} per material
     * that would be over-issued. Returns an empty list when all materials have sufficient stock.
     *
     * <p>Performs exactly one batch DB query (all balances for the document's materials in one
     * call). Does not mutate anything.
     */
    private List<MaterialShortfall> computeShortfalls(
            WasteDocument doc, Long tenantId, Long warehouseId) {

        List<Long> materialIds = doc.getLines().stream()
            .map(l -> l.getMaterial().getId())
            .distinct()
            .toList();

        Map<Long, StockBalance> balances = stockBalanceRepository
            .findByWarehouseAndMaterials(tenantId, warehouseId, materialIds).stream()
            .collect(Collectors.toMap(sb -> sb.getMaterial().getId(), sb -> sb));

        // Aggregate required quantity per material converted into its balance UOM (or stock UOM
        // when no balance exists). LinkedHashMap preserves line order for deterministic output.
        Map<Long, BigDecimal> requiredByMaterial = new LinkedHashMap<>();
        Map<Long, String> materialNameById = new HashMap<>();
        Map<Long, String> fallbackUomSymbolById = new HashMap<>();

        for (WasteLine line : doc.getLines()) {
            Long materialId = line.getMaterial().getId();
            StockBalance balance = balances.get(materialId);

            Uom targetUom = balance != null
                ? balance.getUom()
                : (line.getMaterial().getStockUom() != null
                    ? line.getMaterial().getStockUom()
                    : line.getUom());

            BigDecimal converted = uomConversionService.convert(
                line.getQuantity(), line.getUom(), targetUom, line.getMaterial(), tenantId);
            requiredByMaterial.merge(materialId, converted, BigDecimal::add);

            materialNameById.putIfAbsent(materialId, line.getMaterial().getName());
            if (balance == null) {
                fallbackUomSymbolById.putIfAbsent(materialId,
                    targetUom != null ? targetUom.getSymbol() : "");
            }
        }

        List<MaterialShortfall> shortfalls = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : requiredByMaterial.entrySet()) {
            Long materialId = entry.getKey();
            BigDecimal required = entry.getValue();
            StockBalance balance = balances.get(materialId);
            boolean notStocked = (balance == null);
            BigDecimal available = notStocked ? BigDecimal.ZERO : balance.getQuantity();

            if (required.compareTo(available) > 0) {
                String uomSymbol = notStocked
                    ? fallbackUomSymbolById.getOrDefault(materialId, "")
                    : (balance.getUom() != null ? balance.getUom().getSymbol() : "");
                shortfalls.add(new MaterialShortfall(
                    materialId,
                    materialNameById.get(materialId),
                    required,
                    available,
                    required.subtract(available),
                    uomSymbol,
                    notStocked));
            }
        }
        return shortfalls;
    }

    /**
     * Blocking guard used at POST. Delegates to {@link #computeShortfalls} and throws a
     * {@link BusinessException} on the first shortfall found. Message and behavior are
     * identical to the pre-refactor implementation — POST callers are unaffected.
     */
    private void assertSufficientStock(WasteDocument doc, Long tenantId, Long warehouseId) {
        List<MaterialShortfall> shortfalls = computeShortfalls(doc, tenantId, warehouseId);
        if (shortfalls.isEmpty()) {
            return;
        }
        MaterialShortfall first = shortfalls.get(0);
        if (first.notStockedInWarehouse()) {
            throw new BusinessException(InventoryErrorCode.INSUFFICIENT_STOCK,
                "Cannot waste material '" + first.materialName()
                    + "': it is not stocked in this warehouse (available 0)",
                ErrorParams.of("materialName", first.materialName(),
                    "available", BigDecimal.ZERO, "requested", first.requiredQty()));
        }
        throw new BusinessException(InventoryErrorCode.INSUFFICIENT_STOCK,
            "Cannot waste material '" + first.materialName()
                + "': waste quantity " + first.requiredQty().stripTrailingZeros().toPlainString()
                + " exceeds available stock " + first.availableQty().stripTrailingZeros().toPlainString()
                + " (short by " + first.shortfallQty().stripTrailingZeros().toPlainString()
                + " " + first.uomSymbol() + ")",
            ErrorParams.of("materialName", first.materialName(),
                "available", first.availableQty(), "requested", first.requiredQty()));
    }

    private Uom resolveUom(Long uomId, Material material, Long tenantId) {
        if (uomId == null) {
            return material.getStockUom();
        }
        Uom uom = uomRepository.findById(uomId)
            .orElseThrow(() -> new ResourceNotFoundException(InventoryErrorCode.RESOURCE_NOT_FOUND,
                "Uom not found: " + uomId,
                ErrorParams.of("entityType", "Uom", "entityId", uomId)));
        if (!uomConversionService.areConvertible(uom, material.getStockUom(), material, tenantId)) {
            throw UomConversionException.noConversionFound(
                uom.getCode(), material.getStockUom().getCode(), material.getCode());
        }
        return uom;
    }

    private WasteLine findLine(WasteDocument doc, Long lineId) {
        return doc.getLines().stream()
            .filter(l -> l.getId().equals(lineId))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException(InventoryErrorCode.RESOURCE_NOT_FOUND,
                "Waste line not found: " + lineId,
                ErrorParams.of("entityType", "WasteLine", "entityId", lineId)));
    }

    private Material loadMaterial(Long materialId, Long tenantId) {
        return materialRepository.findByIdAndTenantId(materialId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(InventoryErrorCode.RESOURCE_NOT_FOUND,
                "Material not found: " + materialId,
                ErrorParams.of("entityType", "Material", "entityId", materialId)));
    }

    private void requireDraft(WasteDocument doc) {
        if (doc.getStatus() != DocumentStatus.DRAFT) {
            throw new BusinessException(InventoryErrorCode.INVALID_STATE_TRANSITION,
                "Cannot edit a waste document that is not in DRAFT status",
                ErrorParams.of("entityType", "WasteDocument", "currentStatus", doc.getStatus().name(),
                    "requiredStatus", "DRAFT", "action", "edit"));
        }
    }

    private WasteDocument loadOwned(Long id, Long tenantId) {
        return wasteRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(InventoryErrorCode.RESOURCE_NOT_FOUND,
                "Waste document not found: " + id,
                ErrorParams.of("entityType", "WasteDocument", "entityId", id)));
    }
}
