package com.smart.restaurant_saas.inventory.core;

import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.tenant.TenantTimeZoneService;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
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
import com.smart.restaurant_saas.inventory.core.enums.DocumentType;
import com.smart.restaurant_saas.inventory.core.enums.WasteReasonCode;
import com.smart.restaurant_saas.inventory.purchase.PurchaseInvoice;
import com.smart.restaurant_saas.inventory.repository.PurchaseInvoiceRepository;
import java.util.Objects;
import com.smart.restaurant_saas.inventory.core.enums.BatchSelection;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionDirection;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionType;
import com.smart.restaurant_saas.inventory.mapper.WasteDocumentMapper;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.repository.MaterialRepository;
import com.smart.restaurant_saas.inventory.repository.StockBalanceRepository;
import com.smart.restaurant_saas.inventory.repository.StockBatchRepository;
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
 * document supplies no cost — it tells the ledger which batches to deplete ({@code invoiceLineId}
 * present → that purchase batch only, absent → FIFO) and the ledger computes the actual cost of
 * issue. Before any ledger call, two availability guards run fully, so a shortfall anywhere
 * aborts the whole post and the batch layer never sees an over-issue:
 * <ol>
 *   <li>{@link #assertSufficientStock} — total waste per material against on-hand quantity;</li>
 *   <li>{@link #assertSourceBatchesCoverLines} — waste per named purchase line against that
 *       batch's remaining quantity, which the balance-level check cannot see.</li>
 * </ol>
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
    private final DocumentSequenceService documentSequenceService;
    private final WasteDocumentMapper mapper;
    private final TenantTimeZoneService tenantTimeZoneService;
    private final PurchaseInvoiceRepository invoiceRepository;
    private final StockBatchRepository stockBatchRepository;

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
        assertMovementDateNotInFuture(
            request.getWasteDate(), "wasteDate", "WasteDocument", tenantId);
        Warehouse warehouse = warehouseRepository
            .findByIdAndTenantId(request.getWarehouseId(), tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(InventoryErrorCode.RESOURCE_NOT_FOUND,
                "Warehouse not found: " + request.getWarehouseId(),
                ErrorParams.of("entityType", "Warehouse", "entityId", request.getWarehouseId())));

        WasteDocument doc = new WasteDocument();
        doc.setTenantId(tenantId);
        doc.setWarehouse(warehouse);
        doc.setWasteDate(request.getWasteDate());
        doc.setReasonCode(request.getReasonCode());
        doc.setInvoiceId(request.getInvoiceId());
        // Validate before drawing a code: documentSequenceService.next commits in its own
        // transaction, so a rejection after it would leave a permanent gap in the numbering.
        resolveInvoice(doc);
        doc.setCode(documentSequenceService.next(tenantId, DocumentType.WASTE));
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
        assertMovementDateNotInFuture(
            request.getWasteDate(), "wasteDate", "WasteDocument", tenantId);
        // Header-only update. warehouse is fixed for the life of the document.
        // Lines name batches of the current invoice, so swapping it would orphan them.
        if (!Objects.equals(doc.getInvoiceId(), request.getInvoiceId()) && !doc.getLines().isEmpty()) {
            throw new BusinessException(InventoryErrorCode.INVALID_STATE_TRANSITION,
                "Remove the existing lines before changing the source invoice",
                ErrorParams.of("entityType", "WasteDocument", "entityId", doc.getId(),
                    "action", "changeInvoice", "lineCount", doc.getLines().size()));
        }
        doc.setWasteDate(request.getWasteDate());
        doc.setReasonCode(request.getReasonCode());
        doc.setInvoiceId(request.getInvoiceId());
        resolveInvoice(doc);
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
        line.setInvoiceLineId(request.getInvoiceLineId());
        assertLineMatchesSource(doc, line, sourceMaterialsByLineId(resolveInvoice(doc)));
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
        assertMovementDateNotInFuture(
            doc.getWasteDate(), "wasteDate", "WasteDocument", tenantId);

        // The source requirement is enforced here rather than at create, so a draft can be
        // started before its invoice is chosen and pre-existing drafts stay completable.
        validateDocumentSource(doc);

        // Compute shortfalls once and persist as advisory warnings in the JSON column.
        // Does NOT block completion. The list is written into the same waste_document row —
        // no child inserts, no extra queries on subsequent reads.
        List<MaterialShortfall> shortfalls = computeShortfalls(doc, tenantId,
            balancesByMaterial(doc, tenantId, doc.getWarehouse().getId()));
        doc.setStockWarnings(shortfalls);

        doc.setStatus(DocumentStatus.COMPLETE);
        doc.setCompletedAt(LocalDateTime.now(tenantTimeZoneService.zoneFor(tenantId)));
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
        doc.setUnCompletedAt(LocalDateTime.now(tenantTimeZoneService.zoneFor(tenantId)));
        doc.setUnCompletedBy(userId);
        doc.setUpdatedBy(userId);
        doc.setStockWarnings(new ArrayList<>());
        log.info("UnCompleted waste document id={} tenant={} reason={}",
            doc.getId(), tenantId, reason);
        return mapper.toResponse(wasteRepository.save(doc));
    }

    @Transactional
    public WasteDocumentResponse post(Long id, Long tenantId, Long userId) {
        // The one path that needs the row lock: it reads stock, then writes the ledger against
        // what it read. Two concurrent posts of the same document must not both pass the guard.
        WasteDocument doc = loadOwnedForUpdate(id, tenantId);
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
        assertMovementDateNotInFuture(
            doc.getWasteDate(), "wasteDate", "WasteDocument", tenantId);

        Long warehouseId = doc.getWarehouse().getId();

        // Step 1: validate the source once for the whole document, then run BOTH availability
        // guards fully BEFORE any ledger call (all-or-nothing). Nothing mutates stock between
        // the guards and the ledger loop, so what they read is what the ledger depletes.
        validateDocumentSource(doc);
        Map<Long, StockBalance> balances = balancesByMaterial(doc, tenantId, warehouseId);
        assertSufficientStock(doc, tenantId, balances);
        assertSourceBatchesCoverLines(doc, tenantId, balances);

        // Step 2: issue a WASTE / direction OUT ledger transaction per line. The document
        // supplies no cost (enteredUnitCost null) — the ledger depletes the batches this
        // document named, or FIFO when it named none, and writes back the actual cost of issue.
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
                .sourceInvoiceLineId(line.getInvoiceLineId())
                // The document decides the depletion policy; the ledger just obeys it.
                .batchSelection(line.getInvoiceLineId() != null
                    ? BatchSelection.SOURCE_LINE
                    : BatchSelection.FIFO)
                .idempotencyKey("waste-document:" + doc.getId() + ":line:" + line.getId())
                .movementDate(doc.getWasteDate().atStartOfDay(tenantTimeZoneService.zoneFor(tenantId)).toLocalDateTime())
                .createdBy(userId)
                .build();
            ledgerService.record(cmd);
        }

        // Step 3: mark the document posted.
        doc.setPostedToInventory(true);
        doc.setPostedAt(LocalDateTime.now(tenantTimeZoneService.zoneFor(tenantId)));
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
        doc.setCancelledAt(LocalDateTime.now(tenantTimeZoneService.zoneFor(tenantId)));
        doc.setCancelledBy(userId);
        doc.setCancelReason(reason);
        return mapper.toResponse(wasteRepository.save(doc));
    }

    // =========================================================================
    // Internals
    // =========================================================================

    /**
     * Loads and checks the header's source invoice, or returns null when the document has none.
     * Does NOT enforce the EXPIRED-needs-a-source rule — that belongs to the lifecycle gates
     * (see {@link #validateDocumentSource}), so a draft can be started before its invoice is
     * known. Called once per operation; callers that need the lines pass the result on rather
     * than re-reading it.
     */
    private PurchaseInvoice resolveInvoice(WasteDocument doc) {
        if (doc.getInvoiceId() == null) {
            return null;
        }
        PurchaseInvoice invoice = invoiceRepository
            .findByIdAndTenantId(doc.getInvoiceId(), doc.getTenantId())
            .orElseThrow(() -> invalidSource(doc, null));
        if (invoice.getStatus() != DocumentStatus.POSTED
                || !Boolean.TRUE.equals(invoice.getPostedToInventory())
                || !invoice.getWarehouse().getId().equals(doc.getWarehouse().getId())
                || doc.getWasteDate().isBefore(invoice.getReceiptDate())) {
            throw invalidSource(doc, null);
        }
        return invoice;
    }

    /**
     * The full source check run at COMPLETE and POST: the invoice must still be valid, EXPIRED
     * must have one, and every line must still name one of its material lines. One invoice read
     * and one pass over its lines for the whole document.
     */
    private PurchaseInvoice validateDocumentSource(WasteDocument doc) {
        PurchaseInvoice invoice = resolveInvoice(doc);
        if (invoice == null && doc.getReasonCode() == WasteReasonCode.EXPIRED) {
            throw new BusinessException(InventoryErrorCode.WASTE_INVOICE_REQUIRED,
                "Expired waste requires a purchase invoice",
                ErrorParams.of("entityType", "WasteDocument", "entityId", doc.getId()));
        }
        Map<Long, Long> sourceMaterials = sourceMaterialsByLineId(invoice);
        for (WasteLine line : doc.getLines()) {
            assertLineMatchesSource(doc, line, sourceMaterials);
        }
        return invoice;
    }

    /** Invoice line id → material id, built once so line checks are lookups, not scans. */
    private Map<Long, Long> sourceMaterialsByLineId(PurchaseInvoice invoice) {
        if (invoice == null) {
            return Map.of();
        }
        return invoice.getLines().stream().collect(Collectors.toMap(
            line -> line.getId(), line -> line.getMaterial().getId()));
    }

    /**
     * A line may name a source only when the header has one, and that source must be a line of
     * the same invoice for the same material.
     */
    private void assertLineMatchesSource(WasteDocument doc, WasteLine line,
                                         Map<Long, Long> sourceMaterialsByLineId) {
        if (doc.getInvoiceId() == null) {
            if (line.getInvoiceLineId() != null) {
                throw invalidSource(doc, line.getInvoiceLineId());
            }
            return;
        }
        Long sourceMaterialId = sourceMaterialsByLineId.get(line.getInvoiceLineId());
        if (sourceMaterialId == null || !sourceMaterialId.equals(line.getMaterial().getId())) {
            throw invalidSource(doc, line.getInvoiceLineId());
        }
    }

    /**
     * Blocking guard used at POST, alongside {@link #assertSufficientStock}: a source-backed line
     * is bounded by its own batch, which the balance-level check cannot see — other stock of the
     * same material is irrelevant to it. Aggregated per invoice line, because several lines may
     * name the same batch, and resolved in one query so the whole document is checked before the
     * ledger is touched.
     */
    private void assertSourceBatchesCoverLines(WasteDocument doc, Long tenantId,
                                               Map<Long, StockBalance> balances) {
        Map<Long, BigDecimal> requiredByInvoiceLine = new LinkedHashMap<>();
        Map<Long, WasteLine> firstLineByInvoiceLine = new HashMap<>();

        for (WasteLine line : doc.getLines()) {
            if (line.getInvoiceLineId() == null) {
                continue;
            }
            // Batches are stored in the balance's display UOM, so compare in that unit —
            // the same conversion computeShortfalls applies.
            StockBalance balance = balances.get(line.getMaterial().getId());
            Uom targetUom = balance != null ? balance.getUom() : line.getMaterial().getStockUom();
            BigDecimal converted = uomConversionService.convert(
                line.getQuantity(), line.getUom(), targetUom, line.getMaterial(), tenantId);
            requiredByInvoiceLine.merge(line.getInvoiceLineId(), converted, BigDecimal::add);
            firstLineByInvoiceLine.putIfAbsent(line.getInvoiceLineId(), line);
        }

        if (requiredByInvoiceLine.isEmpty()) {
            return;
        }

        Map<Long, BigDecimal> remaining = stockBatchRepository
            .findRemainingBySourceInvoiceLines(tenantId, requiredByInvoiceLine.keySet()).stream()
            .collect(Collectors.toMap(
                StockBatchRepository.SourceBatchRemaining::getInvoiceLineId,
                StockBatchRepository.SourceBatchRemaining::getRemainingQuantity));

        for (Map.Entry<Long, BigDecimal> entry : requiredByInvoiceLine.entrySet()) {
            // No batch for the line means nothing is left to write off against it.
            BigDecimal available = remaining.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            if (entry.getValue().compareTo(available) <= 0) {
                continue;
            }
            WasteLine line = firstLineByInvoiceLine.get(entry.getKey());
            throw new BusinessException(InventoryErrorCode.BATCH_SHORTFALL,
                "Cannot waste material '" + line.getMaterial().getName()
                    + "': only " + available.stripTrailingZeros().toPlainString()
                    + " of the selected purchase batch is left, but "
                    + entry.getValue().stripTrailingZeros().toPlainString()
                    + " was requested (the rest has already been consumed)",
                ErrorParams.of("materialName", line.getMaterial().getName(),
                    "invoiceLineId", entry.getKey(),
                    "availableInBatch", available, "requested", entry.getValue()));
        }
    }

    private WasteDocument loadOwnedForUpdate(Long id, Long tenantId) {
        return wasteRepository.findByIdAndTenantIdForUpdate(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(InventoryErrorCode.RESOURCE_NOT_FOUND,
                "Waste document not found", ErrorParams.of("entityType", "WasteDocument", "entityId", id)));
    }

    private BusinessException invalidSource(WasteDocument doc, Long invoiceLineId) {
        return new BusinessException(InventoryErrorCode.WASTE_INVALID_INVOICE_SOURCE,
            "Waste must reference a posted purchase in the same warehouse and one of its material lines",
            ErrorParams.of("invoiceId", doc.getInvoiceId(), "invoiceLineId", invoiceLineId,
                "warehouseId", doc.getWarehouse().getId()));
    }

    /** The document's materials' balances in this warehouse, in one query, keyed by material. */
    private Map<Long, StockBalance> balancesByMaterial(WasteDocument doc, Long tenantId,
                                                       Long warehouseId) {
        List<Long> materialIds = doc.getLines().stream()
            .map(line -> line.getMaterial().getId())
            .distinct()
            .toList();
        return stockBalanceRepository
            .findByWarehouseAndMaterials(tenantId, warehouseId, materialIds).stream()
            .collect(Collectors.toMap(sb -> sb.getMaterial().getId(), sb -> sb));
    }

    /**
     * Aggregates the waste quantity per material (all lines, converted to the balance's UOM),
     * then compares against on-hand stock. Returns one {@link MaterialShortfall} per material
     * that would be over-issued. Returns an empty list when all materials have sufficient stock.
     *
     * <p>Takes the balances the caller already resolved — POST feeds the same map to both guards,
     * so the whole post costs one balance query. Does not mutate anything.
     */
    private List<MaterialShortfall> computeShortfalls(
            WasteDocument doc, Long tenantId, Map<Long, StockBalance> balances) {

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
    private void assertSufficientStock(WasteDocument doc, Long tenantId,
                                       Map<Long, StockBalance> balances) {
        List<MaterialShortfall> shortfalls = computeShortfalls(doc, tenantId, balances);
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
        Uom uom = uomRepository.findResolvableByIdForTenant(uomId, tenantId)
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

    private void assertMovementDateNotInFuture(LocalDate date, String field,
                                               String documentType, Long tenantId) {
        LocalDate maxDate = LocalDate.now(tenantTimeZoneService.zoneFor(tenantId));
        if (date != null && date.isAfter(maxDate)) {
            throw new BusinessException(InventoryErrorCode.INVENTORY_MOVEMENT_DATE_IN_FUTURE,
                field + " cannot be in the future: " + date,
                ErrorParams.of("documentType", documentType, "field", field,
                    "date", date, "maxDate", maxDate));
        }
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
