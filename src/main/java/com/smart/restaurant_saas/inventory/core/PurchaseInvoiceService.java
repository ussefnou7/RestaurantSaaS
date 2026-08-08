package com.smart.restaurant_saas.inventory.core;

import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.tenant.TenantTimeZoneService;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.smart.restaurant_saas.inventory.batch.StockBatch;
import com.smart.restaurant_saas.inventory.core.enums.DocumentStatus;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionDirection;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionType;
import com.smart.restaurant_saas.inventory.mapper.PurchaseInvoiceMapper;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.purchase.PurchaseInvoice;
import com.smart.restaurant_saas.inventory.purchase.PurchaseInvoiceLine;
import com.smart.restaurant_saas.inventory.purchase.InvoiceSequenceService;
import com.smart.restaurant_saas.inventory.purchase.Supplier;
import com.smart.restaurant_saas.inventory.purchase.dto.BackdatedConsumptionCheckResponse;
import com.smart.restaurant_saas.inventory.purchase.dto.PurchaseInvoiceHeaderRequest;
import com.smart.restaurant_saas.inventory.purchase.dto.PurchaseInvoiceLineRequest;
import com.smart.restaurant_saas.inventory.purchase.dto.PurchaseInvoiceUpdateLineRequest;
import com.smart.restaurant_saas.inventory.purchase.dto.PurchaseInvoiceResponse;
import com.smart.restaurant_saas.inventory.purchase.dto.UncompleteRequest;
import com.smart.restaurant_saas.inventory.purchase.dto.UnpostRequest;
import com.smart.restaurant_saas.inventory.repository.InventoryTransactionRepository;
import com.smart.restaurant_saas.inventory.repository.MaterialRepository;
import com.smart.restaurant_saas.inventory.repository.PurchaseInvoiceRepository;
import com.smart.restaurant_saas.inventory.repository.PurchaseReturnRepository;
import com.smart.restaurant_saas.inventory.repository.StockBatchRepository;
import com.smart.restaurant_saas.inventory.repository.StockBalanceRepository;
import com.smart.restaurant_saas.inventory.repository.SupplierRepository;
import com.smart.restaurant_saas.inventory.repository.UomRepository;
import com.smart.restaurant_saas.inventory.repository.WarehouseRepository;
import com.smart.restaurant_saas.inventory.stock.StockBalance;
import com.smart.restaurant_saas.inventory.uom.Uom;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseInvoiceService {

    private final PurchaseInvoiceRepository invoiceRepository;
    private final WarehouseRepository warehouseRepository;
    private final MaterialRepository materialRepository;
    private final UomRepository uomRepository;
    private final SupplierRepository supplierRepository;
    private final StockBalanceRepository stockBalanceRepository;
    private final StockBatchRepository stockBatchRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final PurchaseReturnRepository returnRepository;
    private final InventoryLedgerService ledgerService;
    private final InvoiceSequenceService invoiceSequenceService;
    private final PurchaseInvoiceMapper mapper;
    private final TenantTimeZoneService tenantTimeZoneService;

    private static final String PURCHASE_INVOICE_REFERENCE = "PURCHASE_INVOICE";
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    @Transactional(readOnly = true)
    public List<PurchaseInvoiceResponse> findAll(Long tenantId) {
        return invoiceRepository.findByTenantIdOrderByInvoiceDateDesc(tenantId)
            .stream().map(mapper::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public PurchaseInvoiceResponse findById(Long id, Long tenantId) {
        return mapper.toResponse(loadOwned(id, tenantId));
    }

    @Transactional(readOnly = true)
    public List<BackdatedConsumptionCheckResponse> findBackdatedConsumptionConflicts(
            Long id, Long tenantId) {
        PurchaseInvoice invoice = loadOwned(id, tenantId);
        if (invoice.getStatus() != DocumentStatus.COMPLETE) {
            return List.of();
        }

        List<Long> materialIds = invoice.getLines().stream()
            .map(line -> line.getMaterial().getId())
            .distinct()
            .toList();
        if (materialIds.isEmpty()) {
            return List.of();
        }

        // Only a consumption on a later calendar day is backdated relative to this receipt. A
        // same-day consumption is not: the receipt is stamped at the start of the day and D10
        // breaks the tie on id, so the batch it opens already precedes that consumption.
        return transactionRepository.findBackdatedConsumptionConflicts(
            tenantId,
            invoice.getWarehouse().getId(),
            materialIds,
            invoice.getReceiptDate().plusDays(1).atStartOfDay());
    }

    @Transactional
    public PurchaseInvoiceResponse create(PurchaseInvoiceHeaderRequest request, Long tenantId, Long userId) {
        PurchaseInvoice invoice = new PurchaseInvoice();
        invoice.setTenantId(tenantId);
        invoice.setInvoiceNumber(invoiceSequenceService.generateInvoiceNumber(tenantId));
        invoice.setStatus(DocumentStatus.DRAFT);
        invoice.setPostedToInventory(false);
        invoice.setCreatedBy(userId);
        applyHeader(invoice, request, tenantId);
        // No lines on create — totals will be zero until lines are added.
        calculateInvoiceTotalsFromLines(invoice);

        PurchaseInvoice saved = invoiceRepository.save(invoice);
        log.info("Created purchase invoice id={} tenant={}", saved.getId(), tenantId);
        return mapper.toResponse(saved);
    }

    @Transactional
    public PurchaseInvoiceResponse update(Long id, PurchaseInvoiceHeaderRequest request,
                                          Long tenantId, Long userId) {
        PurchaseInvoice invoice = loadOwned(id, tenantId);
        requireDraft(invoice);
        invoice.setUpdatedBy(userId);
        applyHeader(invoice, request, tenantId);
        // Header-only update — lines are managed via the dedicated line endpoints.
        calculateInvoiceTotalsFromLines(invoice);
        return mapper.toResponse(invoiceRepository.save(invoice));
    }

    @Transactional
    public PurchaseInvoiceResponse addLine(Long invoiceId, PurchaseInvoiceLineRequest request, Long tenantId) {
        PurchaseInvoice invoice = loadOwned(invoiceId, tenantId);
        requireDraft(invoice);

        Material material = resolveMaterial(request.getMaterialId(), tenantId);
        Uom uom = resolveUom(request.getUomId(), tenantId);

        PurchaseInvoiceLine line = new PurchaseInvoiceLine();
        line.setPurchaseInvoice(invoice);
        line.setMaterial(material);
        line.setQuantity(request.getQuantity());
        line.setUom(uom);
        line.setUnitCost(request.getUnitCost());
        line.setNotes(request.getNotes());
        calculateLine(line, request.getQuantity(), request.getUnitCost(),
            request.getDiscountPercent(), request.getDiscountAmount());
        invoice.getLines().add(line);

        calculateInvoiceTotalsFromLines(invoice);
        log.info("Added line to purchase invoice id={} tenant={} material={}",
            invoiceId, tenantId, material.getId());
        return mapper.toResponse(invoiceRepository.save(invoice));
    }

    @Transactional
    public PurchaseInvoiceResponse updateLine(Long invoiceId, Long lineId,
                                              PurchaseInvoiceUpdateLineRequest request, Long tenantId) {
        PurchaseInvoice invoice = loadOwned(invoiceId, tenantId);
        requireDraft(invoice);

        PurchaseInvoiceLine line = findLine(invoice, lineId);
        Uom uom = resolveUom(request.getUomId(), tenantId);
        line.setQuantity(request.getQuantity());
        line.setUom(uom);
        line.setUnitCost(request.getUnitCost());
        line.setNotes(request.getNotes());
        calculateLine(line, request.getQuantity(), request.getUnitCost(),
            request.getDiscountPercent(), request.getDiscountAmount());

        calculateInvoiceTotalsFromLines(invoice);
        log.info("Updated line id={} on purchase invoice id={} tenant={}",
            lineId, invoiceId, tenantId);
        return mapper.toResponse(invoiceRepository.save(invoice));
    }

    @Transactional
    public PurchaseInvoiceResponse deleteLine(Long invoiceId, Long lineId, Long tenantId) {
        PurchaseInvoice invoice = loadOwned(invoiceId, tenantId);
        requireDraft(invoice);

        PurchaseInvoiceLine line = findLine(invoice, lineId);
        invoice.getLines().remove(line);

        calculateInvoiceTotalsFromLines(invoice);
        log.info("Deleted line id={} from purchase invoice id={} tenant={}",
            lineId, invoiceId, tenantId);
        return mapper.toResponse(invoiceRepository.save(invoice));
    }

    @Transactional
    public PurchaseInvoiceResponse complete(Long id, Long tenantId, Long userId) {
        PurchaseInvoice invoice = loadOwned(id, tenantId);
        if (invoice.getStatus() != DocumentStatus.DRAFT) {
            throw new BusinessException(InventoryErrorCode.INVALID_STATE_TRANSITION,
                "Only DRAFT invoices can be completed",
                ErrorParams.of("entityType", "PurchaseInvoice", "currentStatus", invoice.getStatus().name(),
                    "requiredStatus", "DRAFT", "action", "complete"));
        }
        invoice.setStatus(DocumentStatus.COMPLETE);
        invoice.setCompletedAt(LocalDateTime.now(tenantTimeZoneService.zoneFor(tenantId)));
        invoice.setCompletedBy(userId);
        return mapper.toResponse(invoiceRepository.save(invoice));
    }

    @Transactional
    public PurchaseInvoiceResponse post(Long id, Long tenantId, Long userId) {
        PurchaseInvoice invoice = loadOwned(id, tenantId);
        if (invoice.getStatus() != DocumentStatus.COMPLETE) {
            throw new BusinessException(InventoryErrorCode.INVALID_STATE_TRANSITION,
                "Only COMPLETE invoices can be posted",
                ErrorParams.of("entityType", "PurchaseInvoice", "currentStatus", invoice.getStatus().name(),
                    "requiredStatus", "COMPLETE", "action", "post"));
        }
        if (Boolean.TRUE.equals(invoice.getPostedToInventory())) {
            throw new BusinessException(InventoryErrorCode.ALREADY_PROCESSED,
                "Invoice is already posted to inventory",
                ErrorParams.of("entityType", "PurchaseInvoice", "entityId", invoice.getId(), "action", "post"));
        }

        Long warehouseId = invoice.getWarehouse().getId();

        // Step 1 & 2: build commands and record transactions (stock balance updated inside the ledger)
        for (PurchaseInvoiceLine line : invoice.getLines()) {
            // unitCost and quantity are both per the line's (invoice) UOM. The ledger
            // normalizes the cost to stock UOM internally — pass the raw per-line cost.
            LedgerCommand cmd = LedgerCommand.builder()
                .tenantId(tenantId)
                .warehouseId(warehouseId)
                .materialId(line.getMaterial().getId())
                .transactionType(InventoryTransactionType.PURCHASE)
                .direction(InventoryTransactionDirection.IN)
                .enteredQuantity(line.getQuantity())
                .enteredUomId(line.getUom().getId())
                .enteredUnitCost(line.getUnitCost())
                .referenceType(PURCHASE_INVOICE_REFERENCE)
                .referenceId(invoice.getId())
                .sourceInvoiceLineId(line.getId())
                .movementDate(invoice.getReceiptDate().atStartOfDay())
                .createdBy(userId)
                .build();
            ledgerService.record(cmd);
        }

        // Step 3: batch update lastPurchasePrice + lastPurchaseDate on StockBalance
        List<Long> materialIds = invoice.getLines().stream()
            .map(l -> l.getMaterial().getId()).toList();

        Map<Long, StockBalance> balanceMap = stockBalanceRepository
            .findByWarehouseAndMaterials(tenantId, warehouseId, materialIds).stream()
            .collect(Collectors.toMap(sb -> sb.getMaterial().getId(), sb -> sb));

        LocalDateTime purchaseDate = invoice.getReceiptDate().atStartOfDay();
        for (PurchaseInvoiceLine line : invoice.getLines()) {
            StockBalance balance = balanceMap.get(line.getMaterial().getId());
            if (balance != null) {
                // unitCost is entered per invoiceUOM; lastPurchasePrice is shown per displayUOM
                // (the StockBalance uom). Convert unless they are the same UOM.
                Uom invoiceUom = line.getUom();
                Uom displayUom = balance.getUom();
                BigDecimal lastPurchasePrice;
                if (invoiceUom.getId().equals(displayUom.getId())) {
                    lastPurchasePrice = line.getUnitCost();
                } else {
                    lastPurchasePrice = line.getUnitCost()
                        .multiply(displayUom.getFactorToBase())
                        .divide(invoiceUom.getFactorToBase(), SCALE, ROUNDING);
                }
                balance.setLastPurchasePrice(lastPurchasePrice);
                balance.setLastPurchaseDate(purchaseDate);
            }
        }
        stockBalanceRepository.saveAll(balanceMap.values());

        // Step 4: update invoice fields
        invoice.setPostedToInventory(true);
        invoice.setPostedAt(LocalDateTime.now(tenantTimeZoneService.zoneFor(tenantId)));
        invoice.setPostedBy(userId);
        invoice.setStatus(DocumentStatus.POSTED);
        log.info("Posted purchase invoice id={} tenant={} lines={}",
            invoice.getId(), tenantId, invoice.getLines().size());
        return mapper.toResponse(invoiceRepository.save(invoice));
    }

    @Transactional
    public PurchaseInvoiceResponse unpost(Long id, UnpostRequest request, Long tenantId, Long userId) {
        PurchaseInvoice invoice = loadOwned(id, tenantId);
        if (invoice.getStatus() != DocumentStatus.POSTED) {
            throw new BusinessException(InventoryErrorCode.INVALID_STATE_TRANSITION,
                "Only POSTED invoices can be unposted",
                ErrorParams.of("entityType", "PurchaseInvoice", "currentStatus", invoice.getStatus().name(),
                    "requiredStatus", "POSTED", "action", "unpost"));
        }

        // Return-existence guard is checked first (before the batch query), so a clear
        // "unpost the return first" message wins over the vaguer batch messages below.
        assertNoPurchaseReturns(invoice, tenantId);

        // Batches opened by this invoice's post() — loaded once and shared by both the
        // consumption guard and the reversibility check + deletion below.
        List<StockBatch> openedBatches = stockBatchRepository
            .findOpenedByPurchaseInvoice(tenantId, invoice.getId());
        assertNoConsumedBatches(invoice, openedBatches);

        // The StockBatches opened by this invoice's post() are derived operational state (FIFO),
        // not part of the append-only ledger. They must be hard-deleted on unpost so a
        // Post→Unpost→Post cycle never leaves two batches pointing at the same source invoice
        // line (which would break StockBatchService.requireSourceBatch on a later return).
        // Delete them BEFORE reversing, so the ledger reversal alone nets the balance and
        // StockBatchService.reverseSourceBatchIfOpened becomes a clean no-op (the batch is gone).
        assertBatchesReversible(openedBatches);
        stockBatchRepository.deleteAll(openedBatches);

        String reasonCode = request != null ? request.getReason() : null;
        // The inventory_transaction ledger is append-only: never delete or mutate the originals.
        // Reverse each one instead (one reversal per original), preserving historical accuracy
        // even though the net quantity/value effect is zero.
        List<InventoryTransaction> originalTransactions = transactionRepository
            .findOriginalsByReference(tenantId, PURCHASE_INVOICE_REFERENCE, invoice.getId());

        for (InventoryTransaction original : originalTransactions) {
            String idempotencyKey = "UNPOST-" + invoice.getId() + "-" + original.getId();
            ledgerService.reverse(original.getId(), reasonCode, idempotencyKey, userId);
        }

        invoice.setStatus(DocumentStatus.COMPLETE);
        invoice.setPostedToInventory(false);
        invoice.setUnpostedAt(LocalDateTime.now(tenantTimeZoneService.zoneFor(tenantId)));
        invoice.setUnpostedBy(userId);
        invoice.setUpdatedBy(userId);

        log.info("Unposted purchase invoice id={} tenant={} reversedTxCount={} deletedBatchCount={} reason={}",
            invoice.getId(), tenantId, originalTransactions.size(), openedBatches.size(), reasonCode);
        return mapper.toResponse(invoiceRepository.save(invoice));
    }

    @Transactional
    public PurchaseInvoiceResponse cancel(Long id, String reason, Long tenantId, Long userId) {
        PurchaseInvoice invoice = loadOwned(id, tenantId);
        if (invoice.getStatus() == DocumentStatus.POSTED) {
            throw new BusinessException(InventoryErrorCode.INVALID_STATE_TRANSITION,
                "Posted invoices cannot be cancelled — create a Purchase Return instead",
                ErrorParams.of("entityType", "PurchaseInvoice", "currentStatus", invoice.getStatus().name(),
                    "action", "cancel"));
        }
        if (invoice.getStatus() != DocumentStatus.DRAFT
                && invoice.getStatus() != DocumentStatus.COMPLETE) {
            throw new BusinessException(InventoryErrorCode.INVALID_STATE_TRANSITION,
                "Only DRAFT or COMPLETE invoices can be cancelled",
                ErrorParams.of("entityType", "PurchaseInvoice", "currentStatus", invoice.getStatus().name(),
                    "requiredStatus", "DRAFT,COMPLETE", "action", "cancel"));
        }
        invoice.setStatus(DocumentStatus.CANCELLED);
        invoice.setCancelledAt(LocalDateTime.now(tenantTimeZoneService.zoneFor(tenantId)));
        invoice.setCancelledBy(userId);
        invoice.setCancelReason(reason);
        return mapper.toResponse(invoiceRepository.save(invoice));
    }

    @Transactional
    public void delete(Long id, Long tenantId) {
        PurchaseInvoice invoice = loadOwned(id, tenantId);
        if (invoice.getStatus() != DocumentStatus.DRAFT) {
            throw new BusinessException(InventoryErrorCode.INVALID_STATE_TRANSITION,
                "Only DRAFT invoices can be deleted — cancel the invoice instead",
                ErrorParams.of("entityType", "PurchaseInvoice", "currentStatus", invoice.getStatus().name(),
                    "requiredStatus", "DRAFT", "action", "delete"));
        }
        if (transactionRepository.existsByReference(
                tenantId, PURCHASE_INVOICE_REFERENCE, invoice.getId())) {
            throw new BusinessException(InventoryErrorCode.ALREADY_PROCESSED,
                "Purchase invoice cannot be deleted because it has inventory ledger history",
                ErrorParams.of("entityType", "PurchaseInvoice",
                    "invoiceId", invoice.getId(),
                    "referenceType", PURCHASE_INVOICE_REFERENCE,
                    "action", "delete"));
        }
        invoiceRepository.delete(invoice);
        log.info("Deleted purchase invoice id={} tenant={}", id, tenantId);
    }

    @Transactional
    public PurchaseInvoiceResponse uncomplete(Long id, UncompleteRequest request, Long tenantId, Long userId) {
        PurchaseInvoice invoice = loadOwned(id, tenantId);
        if (invoice.getStatus() != DocumentStatus.COMPLETE) {
            throw new BusinessException(InventoryErrorCode.INVALID_STATE_TRANSITION,
                "Only COMPLETE invoices can be uncompleted",
                ErrorParams.of("entityType", "PurchaseInvoice", "currentStatus", invoice.getStatus().name(),
                    "requiredStatus", "COMPLETE", "action", "uncomplete"));
        }
        String reason = request != null ? request.getReason() : null;
        invoice.setStatus(DocumentStatus.DRAFT);
        invoice.setUnCompletedAt(LocalDateTime.now(tenantTimeZoneService.zoneFor(tenantId)));
        invoice.setUnCompletedBy(userId);
        invoice.setUpdatedBy(userId);
        log.info("UnCompleted purchase invoice id={} tenant={} reason={}",
            invoice.getId(), tenantId, reason);
        return mapper.toResponse(invoiceRepository.save(invoice));
    }

    // =========================================================================
    // Internals
    // =========================================================================

    private void applyHeader(PurchaseInvoice invoice, PurchaseInvoiceHeaderRequest request, Long tenantId) {
        invoice.setWarehouse(resolveWarehouse(request.getWarehouseId(), tenantId));
        invoice.setSupplier(resolveSupplier(request.getSupplierId(), tenantId));
        // invoiceNumber is system-generated on create() and preserved across updates;
        // it is not taken from the request.
        invoice.setInvoiceDate(request.getInvoiceDate());
        invoice.setReceiptDate(request.getReceiptDate());
        invoice.setNotes(request.getNotes());
        // Store the requested invoice-level discount/tax inputs; the derived
        // amounts are computed against the current lines in calculateInvoiceTotalsFromLines.
        invoice.setDiscountPercent(defaultZero(request.getDiscountPercent()));
        invoice.setDiscountAmount(defaultZero(request.getDiscountAmount()));
        invoice.setTaxPercent(defaultZero(request.getTaxPercent()));
        invoice.setTaxAmount(defaultZero(request.getTaxAmount()));
    }

    private PurchaseInvoiceLine findLine(PurchaseInvoice invoice, Long lineId) {
        return invoice.getLines().stream()
            .filter(l -> l.getId().equals(lineId))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException(InventoryErrorCode.RESOURCE_NOT_FOUND,
                "Purchase invoice line not found: " + lineId,
                ErrorParams.of("entityType", "PurchaseInvoiceLine", "entityId", lineId)));
    }

    private void requireDraft(PurchaseInvoice invoice) {
        if (invoice.getStatus() != DocumentStatus.DRAFT) {
            throw new BusinessException(InventoryErrorCode.INVALID_STATE_TRANSITION,
                "Cannot edit invoice that is not in DRAFT status",
                ErrorParams.of("entityType", "PurchaseInvoice", "currentStatus", invoice.getStatus().name(),
                    "requiredStatus", "DRAFT", "action", "edit"));
        }
    }

    /**
     * Defense-in-depth check, independent of the return-existence and batch-consumption guards
     * above: immediately before a batch is hard-deleted on unpost, verify it is still fully
     * untouched (remainingQuantity == originalQuantity) — i.e. no FIFO consumption ever happened
     * against it. If any batch fails this, abort the whole unpost rather than silently deleting a
     * batch that carries real consumption history. This must hold even though the existing guard
     * sequence is supposed to prevent reaching this state; it is a second, independent check.
     */
    private void assertBatchesReversible(List<StockBatch> openedBatches) {
        for (StockBatch batch : openedBatches) {
            if (batch.getRemainingQuantity().compareTo(batch.getOriginalQuantity()) != 0) {
                throw new BusinessException(InventoryErrorCode.BATCH_NOT_REVERSIBLE,
                    "Cannot unpost: source batch " + batch.getId()
                        + " is not fully reversible (remaining " + batch.getRemainingQuantity()
                        + " of original " + batch.getOriginalQuantity()
                        + ") — it has been partially consumed.",
                    ErrorParams.of("entityType", "StockBatch",
                        "batchId", batch.getId(),
                        "remainingQuantity", batch.getRemainingQuantity(),
                        "originalQuantity", batch.getOriginalQuantity()));
            }
        }
    }

    private void assertNoConsumedBatches(PurchaseInvoice invoice, List<StockBatch> openedBatches) {
        List<Map<String, Object>> consumedBatches = openedBatches
            .stream()
            .filter(batch -> consumedQuantity(batch).compareTo(BigDecimal.ZERO) > 0)
            .map(batch -> ErrorParams.of(
                "materialName", materialName(batch),
                "batchId", batch.getId(),
                "consumedQuantity", consumedQuantity(batch)))
            .toList();

        if (!consumedBatches.isEmpty()) {
            throw new BusinessException(InventoryErrorCode.UNPOST_BLOCKED_BATCH_CONSUMED,
                "Cannot unpost purchase invoice " + invoice.getId()
                    + " because one or more source batches have been consumed: "
                    + consumedBatchSummary(consumedBatches),
                ErrorParams.of("entityType", "PurchaseInvoice",
                    "invoiceId", invoice.getId(),
                    "consumedBatches", consumedBatches));
        }
    }

    private void assertNoPurchaseReturns(PurchaseInvoice invoice, Long tenantId) {
        List<Map<String, Object>> returns = returnRepository
            .findReturnSummariesByOriginalInvoice(tenantId, invoice.getId())
            .stream()
            .map(ret -> ErrorParams.of(
                "returnCode", ret.getReturnCode(),
                "returnId", ret.getReturnId()))
            .toList();

        if (!returns.isEmpty()) {
            throw new BusinessException(InventoryErrorCode.UNPOST_BLOCKED_HAS_RETURN,
                "Cannot unpost purchase invoice " + invoice.getId()
                    + " because purchase returns already reference it: "
                    + returnSummary(returns)
                    + ". Unpost the return first.",
                ErrorParams.of("entityType", "PurchaseInvoice",
                    "invoiceId", invoice.getId(),
                    "returns", returns));
        }
    }

    private BigDecimal consumedQuantity(StockBatch batch) {
        return batch.getOriginalQuantity().subtract(batch.getRemainingQuantity());
    }

    private String materialName(StockBatch batch) {
        Material material = batch.getStockBalance().getMaterial();
        if (material.getName() != null && !material.getName().isBlank()) {
            return material.getName();
        }
        if (material.getCode() != null && !material.getCode().isBlank()) {
            return material.getCode();
        }
        return String.valueOf(material.getId());
    }

    private String consumedBatchSummary(List<Map<String, Object>> consumedBatches) {
        return consumedBatches.stream()
            .map(batch -> batch.get("materialName")
                + " batch " + batch.get("batchId")
                + " consumed " + batch.get("consumedQuantity"))
            .collect(Collectors.joining(", "));
    }

    private String returnSummary(List<Map<String, Object>> returns) {
        return returns.stream()
            .map(ret -> ret.get("returnCode") != null
                ? ret.get("returnCode").toString()
                : String.valueOf(ret.get("returnId")))
            .collect(Collectors.joining(", "));
    }

    // LINE CALCULATION
    private void calculateLine(PurchaseInvoiceLine line, BigDecimal qty, BigDecimal unitCost,
                                BigDecimal reqDiscountPct, BigDecimal reqDiscountAmt) {
        BigDecimal lineSubtotal = qty.multiply(unitCost)
                                     .setScale(SCALE, ROUNDING);

        // Resolve discount: pct takes priority if both provided
        BigDecimal discountPct = defaultZero(reqDiscountPct);
        BigDecimal discountAmt = defaultZero(reqDiscountAmt);

        if (discountPct.compareTo(BigDecimal.ZERO) > 0) {
            // pct provided → calculate amt
            discountAmt = lineSubtotal.multiply(discountPct)
                                      .divide(HUNDRED, SCALE, ROUNDING);
        } else if (discountAmt.compareTo(BigDecimal.ZERO) > 0) {
            // amt provided → calculate pct
            if (lineSubtotal.compareTo(BigDecimal.ZERO) > 0) {
                discountPct = discountAmt.multiply(HUNDRED)
                                         .divide(lineSubtotal, SCALE, ROUNDING);
            }
        }

        BigDecimal lineNetTotal = lineSubtotal.subtract(discountAmt)
                                              .setScale(SCALE, ROUNDING);

        line.setLineTotal(lineSubtotal);     // gross before discount
        line.setDiscountPercent(discountPct);
        line.setDiscountAmount(discountAmt);
        line.setLineNetTotal(lineNetTotal);  // net after discount
    }

    // INVOICE TOTALS CALCULATION — recomputed purely from the current lines and
    // the invoice's stored discount/tax inputs (no request object required).
    private void calculateInvoiceTotalsFromLines(PurchaseInvoice invoice) {
        // subtotal = sum of lineNetTotals
        BigDecimal subtotal = invoice.getLines().stream()
            .map(PurchaseInvoiceLine::getLineNetTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Invoice discount
        BigDecimal discountPct = defaultZero(invoice.getDiscountPercent());
        BigDecimal discountAmt = defaultZero(invoice.getDiscountAmount());

        if (discountPct.compareTo(BigDecimal.ZERO) > 0) {
            discountAmt = subtotal.multiply(discountPct)
                                  .divide(HUNDRED, SCALE, ROUNDING);
        } else if (discountAmt.compareTo(BigDecimal.ZERO) > 0) {
            if (subtotal.compareTo(BigDecimal.ZERO) > 0) {
                discountPct = discountAmt.multiply(HUNDRED)
                                         .divide(subtotal, SCALE, ROUNDING);
            }
        }

        BigDecimal afterDiscount = subtotal.subtract(discountAmt)
                                           .setScale(SCALE, ROUNDING);

        // Tax
        BigDecimal taxPct = defaultZero(invoice.getTaxPercent());
        BigDecimal taxAmt = defaultZero(invoice.getTaxAmount());

        if (taxPct.compareTo(BigDecimal.ZERO) > 0) {
            taxAmt = afterDiscount.multiply(taxPct)
                                  .divide(HUNDRED, SCALE, ROUNDING);
        } else if (taxAmt.compareTo(BigDecimal.ZERO) > 0) {
            if (afterDiscount.compareTo(BigDecimal.ZERO) > 0) {
                taxPct = taxAmt.multiply(HUNDRED)
                               .divide(afterDiscount, SCALE, ROUNDING);
            }
        }

        BigDecimal total = afterDiscount.add(taxAmt).setScale(SCALE, ROUNDING);

        invoice.setSubtotal(subtotal);
        invoice.setDiscountPercent(discountPct);
        invoice.setDiscountAmount(discountAmt);
        invoice.setTaxPercent(taxPct);
        invoice.setTaxAmount(taxAmt);
        invoice.setTotalAmount(total);
    }

    private BigDecimal defaultZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private Warehouse resolveWarehouse(Long warehouseId, Long tenantId) {
        return warehouseRepository.findByIdAndTenantId(warehouseId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(InventoryErrorCode.RESOURCE_NOT_FOUND,
                "Warehouse not found: " + warehouseId,
                ErrorParams.of("entityType", "Warehouse", "entityId", warehouseId)));
    }

    private Supplier resolveSupplier(Long supplierId, Long tenantId) {
        if (supplierId == null) {
            return null;
        }
        return supplierRepository.findByIdAndTenantId(supplierId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(InventoryErrorCode.RESOURCE_NOT_FOUND,
                "Supplier not found: " + supplierId,
                ErrorParams.of("entityType", "Supplier", "entityId", supplierId)));
    }

    private Material resolveMaterial(Long materialId, Long tenantId) {
        return materialRepository.findByIdAndTenantId(materialId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(InventoryErrorCode.RESOURCE_NOT_FOUND,
                "Material not found: " + materialId,
                ErrorParams.of("entityType", "Material", "entityId", materialId)));
    }

    private Uom resolveUom(Long uomId, Long tenantId) {
        Uom uom = uomRepository.findById(uomId)
            .orElseThrow(() -> new ResourceNotFoundException(InventoryErrorCode.RESOURCE_NOT_FOUND,
                "Uom not found: " + uomId,
                ErrorParams.of("entityType", "Uom", "entityId", uomId)));
        if (uom.getTenantId() != null && !uom.getTenantId().equals(tenantId)) {
            throw new ResourceNotFoundException(InventoryErrorCode.RESOURCE_NOT_FOUND,
                "Uom not available for tenant: " + uomId,
                ErrorParams.of("entityType", "Uom", "entityId", uomId));
        }
        return uom;
    }

    private PurchaseInvoice loadOwned(Long id, Long tenantId) {
        return invoiceRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(InventoryErrorCode.RESOURCE_NOT_FOUND,
                "Purchase invoice not found: " + id,
                ErrorParams.of("entityType", "PurchaseInvoice", "entityId", id)));
    }
}
