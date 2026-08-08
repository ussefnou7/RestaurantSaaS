package com.smart.restaurant_saas.inventory.core;

import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.tenant.TenantTimeZoneService;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
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
import com.smart.restaurant_saas.inventory.mapper.PurchaseReturnMapper;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.purchase.InvoiceSequenceService;
import com.smart.restaurant_saas.inventory.purchase.PurchaseInvoice;
import com.smart.restaurant_saas.inventory.purchase.PurchaseInvoiceLine;
import com.smart.restaurant_saas.inventory.purchase.PurchaseReturn;
import com.smart.restaurant_saas.inventory.purchase.PurchaseReturnLine;
import com.smart.restaurant_saas.inventory.purchase.dto.PurchaseReturnLineRequest;
import com.smart.restaurant_saas.inventory.purchase.dto.PurchaseReturnRequest;
import com.smart.restaurant_saas.inventory.purchase.dto.PurchaseReturnResponse;
import com.smart.restaurant_saas.inventory.purchase.dto.PurchaseReturnUpdateLineRequest;
import com.smart.restaurant_saas.inventory.purchase.dto.ReturnableLineResponse;
import com.smart.restaurant_saas.inventory.purchase.dto.UncompleteRequest;
import com.smart.restaurant_saas.inventory.purchase.dto.UnpostRequest;
import com.smart.restaurant_saas.inventory.repository.InventoryTransactionRepository;
import com.smart.restaurant_saas.inventory.repository.PurchaseInvoiceRepository;
import com.smart.restaurant_saas.inventory.repository.PurchaseReturnRepository;
import com.smart.restaurant_saas.inventory.batch.StockBatch;
import com.smart.restaurant_saas.inventory.repository.StockBalanceRepository;
import com.smart.restaurant_saas.inventory.repository.UomRepository;
import com.smart.restaurant_saas.inventory.stock.StockBalance;
import com.smart.restaurant_saas.inventory.uom.Uom;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseReturnService {

    private final PurchaseReturnRepository returnRepository;
    private final PurchaseInvoiceRepository invoiceRepository;
    private final StockBalanceRepository stockBalanceRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final InventoryLedgerService ledgerService;
    private final StockBatchService stockBatchService;
    private final StockBalanceService stockBalanceService;
    private final UomConversionService uomConversionService;
    private final InvoiceSequenceService invoiceSequenceService;
    private final UomRepository uomRepository;
    private final PurchaseReturnMapper mapper;
    private final TenantTimeZoneService tenantTimeZoneService;

    private static final int SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final String PURCHASE_RETURN_REFERENCE = "PURCHASE_RETURN";

    @Transactional(readOnly = true)
    public List<PurchaseReturnResponse> findAll(Long tenantId) {
        return returnRepository.findByTenantIdOrderByReturnDateDesc(tenantId)
            .stream().map(mapper::toSummary).toList();
    }

    @Transactional(readOnly = true)
    public PurchaseReturnResponse findById(Long id, Long tenantId) {
        return mapper.toResponse(loadOwned(id, tenantId));
    }

    @Transactional
    public PurchaseReturnResponse create(PurchaseReturnRequest request, Long tenantId, Long userId) {
        PurchaseInvoice invoice = invoiceRepository
            .findByIdAndTenantId(request.getOriginalInvoiceId(), tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(InventoryErrorCode.RESOURCE_NOT_FOUND,
                "Purchase invoice not found: " + request.getOriginalInvoiceId(),
                ErrorParams.of("entityType", "PurchaseInvoice", "entityId", request.getOriginalInvoiceId())));

        if (invoice.getStatus() != DocumentStatus.POSTED) {
            throw new BusinessException(InventoryErrorCode.INVALID_STATE_TRANSITION,
                "Purchase return can only be created against a POSTED invoice",
                ErrorParams.of("entityType", "PurchaseInvoice", "currentStatus", invoice.getStatus().name(),
                    "requiredStatus", "POSTED", "action", "createReturn"));
        }

        PurchaseReturn ret = new PurchaseReturn();
        ret.setTenantId(tenantId);
        ret.setOriginalInvoice(invoice);
        ret.setSupplier(invoice.getSupplier());
        ret.setWarehouse(invoice.getWarehouse());
        ret.setReturnNumber(invoiceSequenceService.generateReturnNumber(tenantId));
        ret.setReturnDate(request.getReturnDate());
        ret.setReason(request.getReason());
        ret.setNotes(request.getNotes());
        ret.setStatus(DocumentStatus.DRAFT);
        ret.setPostedToInventory(false);
        ret.setCreatedBy(userId);
        // No lines on create — totals stay zero until lines are added.
        ret.setSubtotal(BigDecimal.ZERO);
        ret.setTotalAmount(BigDecimal.ZERO);

        PurchaseReturn saved = returnRepository.save(ret);
        log.info("Created purchase return header id={} tenant={} number={} invoice={}",
            saved.getId(), tenantId, saved.getReturnNumber(), invoice.getId());
        return mapper.toResponse(saved);
    }

    @Transactional
    public PurchaseReturnResponse update(Long id, PurchaseReturnRequest request,
                                         Long tenantId, Long userId) {
        PurchaseReturn ret = loadOwned(id, tenantId);
        requireDraft(ret);
        // Header-only update. originalInvoice is fixed for the life of the return.
        ret.setReturnDate(request.getReturnDate());
        ret.setReason(request.getReason());
        ret.setNotes(request.getNotes());
        ret.setUpdatedBy(userId);
        return mapper.toResponse(returnRepository.save(ret));
    }

    /**
     * The still-returnable lines of the original invoice, for the FE to build the return
     * form after the header is saved. Returnable = original quantity − quantity already
     * taken by POSTED returns of the same invoice; lines with nothing left are omitted.
     */
    @Transactional(readOnly = true)
    public List<ReturnableLineResponse> getReturnableLines(Long id, Long tenantId) {
        PurchaseReturn ret = loadOwned(id, tenantId);
        PurchaseInvoice invoice = ret.getOriginalInvoice();
        Map<Long, BigDecimal> alreadyReturned = loadAlreadyReturnedQuantities(invoice.getId(), tenantId);

        List<ReturnableLineResponse> result = new ArrayList<>();
        for (PurchaseInvoiceLine line : invoice.getLines()) {
            BigDecimal returned = alreadyReturned.getOrDefault(line.getId(), BigDecimal.ZERO);
            BigDecimal returnable = line.getQuantity().subtract(returned);
            if (returnable.compareTo(BigDecimal.ZERO) > 0) {
                result.add(mapper.toReturnableLine(line, returned, returnable));
            }
        }
        return result;
    }

    @Transactional
    public PurchaseReturnResponse addLine(Long returnId, PurchaseReturnLineRequest request,
                                          Long tenantId, Long userId) {
        PurchaseReturn ret = loadOwned(returnId, tenantId);
        requireDraft(ret);

        PurchaseInvoiceLine originalLine = resolveOriginalLine(ret, request.getOriginalLineId());
        Uom submittedUom = resolveUom(request.getUomId(), tenantId);
        validateReturnable(
            ret, originalLine, request.getQuantity(), submittedUom, null, tenantId);

        BigDecimal unitCost = convertUnitCost(
            originalLine.getUnitCost(), originalLine.getUom(), submittedUom,
            originalLine.getMaterial(), tenantId);
        PurchaseReturnLine line = new PurchaseReturnLine();
        line.setPurchaseReturn(ret);
        line.setOriginalLine(originalLine);
        line.setMaterial(originalLine.getMaterial());
        line.setQuantity(request.getQuantity());
        line.setUom(submittedUom);
        line.setUnitCost(unitCost);
        line.setLineTotal(calculateLineTotal(request.getQuantity(), unitCost));
        line.setNotes(request.getNotes());
        ret.getLines().add(line);

        recalcTotals(ret);
        ret.setUpdatedBy(userId);
        log.info("Added line to purchase return id={} tenant={} originalLine={}",
            returnId, tenantId, originalLine.getId());
        return mapper.toResponse(returnRepository.save(ret));
    }

    @Transactional
    public PurchaseReturnResponse updateLine(Long returnId, Long lineId,
                                             PurchaseReturnUpdateLineRequest request,
                                             Long tenantId, Long userId) {
        PurchaseReturn ret = loadOwned(returnId, tenantId);
        requireDraft(ret);

        PurchaseReturnLine line = findLine(ret, lineId);
        Uom submittedUom = resolveUom(request.getUomId(), tenantId);
        validateReturnable(
            ret, line.getOriginalLine(), request.getQuantity(), submittedUom, lineId, tenantId);
        BigDecimal unitCost = convertUnitCost(
            line.getOriginalLine().getUnitCost(), line.getOriginalLine().getUom(), submittedUom,
            line.getOriginalLine().getMaterial(), tenantId);
        line.setQuantity(request.getQuantity());
        line.setUom(submittedUom);
        line.setUnitCost(unitCost);
        line.setLineTotal(calculateLineTotal(request.getQuantity(), unitCost));
        line.setNotes(request.getNotes());

        recalcTotals(ret);
        ret.setUpdatedBy(userId);
        log.info("Updated line id={} on purchase return id={} tenant={}", lineId, returnId, tenantId);
        return mapper.toResponse(returnRepository.save(ret));
    }

    @Transactional
    public PurchaseReturnResponse deleteLine(Long returnId, Long lineId, Long tenantId, Long userId) {
        PurchaseReturn ret = loadOwned(returnId, tenantId);
        requireDraft(ret);

        PurchaseReturnLine line = findLine(ret, lineId);
        ret.getLines().remove(line);

        recalcTotals(ret);
        ret.setUpdatedBy(userId);
        log.info("Deleted line id={} from purchase return id={} tenant={}", lineId, returnId, tenantId);
        return mapper.toResponse(returnRepository.save(ret));
    }

    @Transactional
    public PurchaseReturnResponse complete(Long id, Long tenantId, Long userId) {
        PurchaseReturn ret = loadOwned(id, tenantId);
        if (ret.getStatus() != DocumentStatus.DRAFT) {
            throw new BusinessException(InventoryErrorCode.INVALID_STATE_TRANSITION,
                "Only DRAFT returns can be completed",
                ErrorParams.of("entityType", "PurchaseReturn", "currentStatus", ret.getStatus().name(),
                    "requiredStatus", "DRAFT", "action", "complete"));
        }
        if (ret.getLines().isEmpty()) {
            throw new BusinessException(InventoryErrorCode.EMPTY_DOCUMENT_LINES,
                "Cannot complete a return with no lines",
                ErrorParams.of("documentType", "PurchaseReturn"));
        }
        ret.setStatus(DocumentStatus.COMPLETE);
        ret.setCompletedAt(LocalDateTime.now(tenantTimeZoneService.zoneFor(tenantId)));
        ret.setCompletedBy(userId);
        return mapper.toResponse(returnRepository.save(ret));
    }

    @Transactional
    public PurchaseReturnResponse post(Long id, Long tenantId, Long userId) {
        PurchaseReturn ret = loadOwned(id, tenantId);
        if (ret.getStatus() != DocumentStatus.COMPLETE) {
            throw new BusinessException(InventoryErrorCode.INVALID_STATE_TRANSITION,
                "Only COMPLETE returns can be posted",
                ErrorParams.of("entityType", "PurchaseReturn", "currentStatus", ret.getStatus().name(),
                    "requiredStatus", "COMPLETE", "action", "post"));
        }
        if (Boolean.TRUE.equals(ret.getPostedToInventory())) {
            throw new BusinessException(InventoryErrorCode.ALREADY_PROCESSED,
                "Return is already posted to inventory",
                ErrorParams.of("entityType", "PurchaseReturn", "entityId", ret.getId(), "action", "post"));
        }

        Long warehouseId = ret.getWarehouse().getId();
        List<Long> materialIds = ret.getLines().stream()
            .map(l -> l.getMaterial().getId()).toList();

        // Resolve balances upfront — needed for both the batch guard and the lastPurchasePrice restore.
        Map<Long, StockBalance> balanceMap = stockBalanceRepository
            .findByWarehouseAndMaterials(tenantId, warehouseId, materialIds).stream()
            .collect(Collectors.toMap(sb -> sb.getMaterial().getId(), sb -> sb));

        // PASS 1 — guard: validate every line before any ledger write.
        // If any check fails the exception rolls back with no side effects.
        // Precomputed display-UOM quantities are cached so we don't convert twice.
        Map<Long, BigDecimal> returnQtyByOrigLineId = new HashMap<>();
        for (PurchaseReturnLine line : ret.getLines()) {
            StockBalance balance = balanceMap.get(line.getMaterial().getId());
            if (balance == null) {
                throw new BusinessException(InventoryErrorCode.INSUFFICIENT_STOCK,
                    "No stock balance found for material: " + line.getMaterial().getName(),
                    ErrorParams.of("materialName", line.getMaterial().getName(),
                        "available", BigDecimal.ZERO, "requested", line.getQuantity()));
            }
            // Convert the return quantity to the batch's storage unit (display UOM), mirroring
            // createBatchFromInbound: entered qty → stock qty → display qty.
            BigDecimal stockQty = uomConversionService.convertToStockUom(
                line.getQuantity(), line.getUom(), line.getMaterial(), tenantId);
            BigDecimal returnQtyDisplayUom = uomConversionService.convert(
                stockQty, line.getMaterial().getStockUom(), balance.getUom(),
                line.getMaterial(), tenantId);

            // requireSourceBatch fails loudly when no batch exists for this line (data gap).
            // The capacity check here covers all lines before any ledger calls are issued.
            StockBatch sourceBatch =
                stockBatchService.requireSourceBatch(balance.getId(), line.getOriginalLine().getId());
            if (returnQtyDisplayUom.compareTo(sourceBatch.getRemainingQuantity()) > 0) {
                throw new BusinessException(InventoryErrorCode.BATCH_SHORTFALL, String.format(
                    "Cannot return %.6f of '%s': only %.6f remains in the source batch"
                    + " (the rest has already been consumed). Invoice line: %d",
                    returnQtyDisplayUom, line.getMaterial().getName(),
                    sourceBatch.getRemainingQuantity(), line.getOriginalLine().getId()),
                    ErrorParams.of("materialName", line.getMaterial().getName(),
                        "availableInBatch", sourceBatch.getRemainingQuantity(),
                        "requested", returnQtyDisplayUom,
                        "invoiceLineId", line.getOriginalLine().getId()));
            }
            returnQtyByOrigLineId.put(line.getOriginalLine().getId(), returnQtyDisplayUom);
        }

        // PASS 2 — execute: issue ledger transactions and deplete each source batch.
        // depleteSourceBatch runs with propagation MANDATORY — joins this transaction.
        // Sequencing is ledger-then-batch (mirroring InventoryLedgerService's
        // balance-then-batch ordering for inbound); both are safe within one @Transactional.
        for (PurchaseReturnLine line : ret.getLines()) {
            LedgerCommand cmd = LedgerCommand.builder()
                .tenantId(tenantId)
                .warehouseId(warehouseId)
                .materialId(line.getMaterial().getId())
                .transactionType(InventoryTransactionType.PURCHASE_RETURN)
                .direction(InventoryTransactionDirection.OUT)
                .enteredQuantity(line.getQuantity())
                .enteredUomId(line.getUom().getId())
                .enteredUnitCost(line.getUnitCost())
                .referenceType("PURCHASE_RETURN")
                .referenceId(ret.getId())
                .movementDate(ret.getReturnDate().atStartOfDay(tenantTimeZoneService.zoneFor(tenantId)).toLocalDateTime())
                .createdBy(userId)
                .build();
            ledgerService.record(cmd);

            StockBalance balance = balanceMap.get(line.getMaterial().getId());
            stockBatchService.depleteSourceBatch(
                balance.getId(),
                line.getOriginalLine().getId(),
                returnQtyByOrigLineId.get(line.getOriginalLine().getId()));
            // Source batch is now depleted — re-derive the balance's average cost from its
            // open batches within this transaction. The ledger's record() above ran recalc
            // before this depletion; running it again here reflects the final batch state.
            stockBalanceService.recalculateFromOpenBatches(balance);
        }

        // Step 3: restore lastPurchasePrice to the previous valid purchase
        Map<Long, InventoryTransaction> lastPurchaseMap = transactionRepository
            .findLastValidPurchases(tenantId, warehouseId, materialIds).stream()
            .collect(Collectors.toMap(
                t -> t.getMaterial().getId(),
                t -> t,
                (existing, replacement) -> existing));

        for (StockBalance balance : balanceMap.values()) {
            InventoryTransaction lastPurchase = lastPurchaseMap.get(balance.getMaterial().getId());
            if (lastPurchase == null) {
                balance.setLastPurchasePrice(null);
                balance.setLastPurchaseDate(null);
                continue;
            }
            // lastPurchase.getUnitCost() is per STOCK UOM, but lastPurchasePrice is stored per
            // display UOM. Convert via the same inverse scaling as
            // StockBalanceService.convertUnitCostToDisplayUom: cost scales inversely to
            // quantity, so multiply by the number of stock units in one display unit.
            BigDecimal displayPrice = null;
            if (lastPurchase.getUnitCost() != null) {
                BigDecimal stockUnitsPerDisplayUnit = uomConversionService.convert(
                    BigDecimal.ONE, balance.getUom(), lastPurchase.getStockUom(),
                    lastPurchase.getMaterial(), tenantId);
                displayPrice = lastPurchase.getUnitCost()
                    .multiply(stockUnitsPerDisplayUnit).setScale(SCALE, ROUNDING);
            }
            balance.setLastPurchasePrice(displayPrice);
            balance.setLastPurchaseDate(lastPurchase.getMovementDate());
        }
        stockBalanceRepository.saveAll(balanceMap.values());

        // Step 4: update return document fields
        ret.setPostedToInventory(true);
        ret.setPostedAt(LocalDateTime.now(tenantTimeZoneService.zoneFor(tenantId)));
        ret.setPostedBy(userId);
        ret.setStatus(DocumentStatus.POSTED);
        log.info("Posted purchase return id={} tenant={} lines={}",
            ret.getId(), tenantId, ret.getLines().size());
        return mapper.toResponse(returnRepository.save(ret));
    }

    @Transactional
    public PurchaseReturnResponse unpost(Long id, UnpostRequest request, Long tenantId, Long userId) {
        PurchaseReturn ret = loadOwned(id, tenantId);
        if (ret.getStatus() != DocumentStatus.POSTED) {
            throw new BusinessException(InventoryErrorCode.INVALID_STATE_TRANSITION,
                "Only POSTED returns can be unposted",
                ErrorParams.of("entityType", "PurchaseReturn", "currentStatus", ret.getStatus().name(),
                    "requiredStatus", "POSTED", "action", "unpost"));
        }

        assertOriginalInvoiceStillPosted(ret);
        Map<Long, SourceBatchImpact> sourceBatchImpacts = loadSourceBatchImpacts(ret, tenantId);

        String reasonCode = request != null ? request.getReason() : null;
        List<InventoryTransaction> originalTransactions = transactionRepository
            .findOriginalsByReference(tenantId, PURCHASE_RETURN_REFERENCE, ret.getId());

        for (InventoryTransaction original : originalTransactions) {
            String idempotencyKey = "UNPOST-RETURN-" + ret.getId() + "-" + original.getId();
            ledgerService.reverse(original.getId(), reasonCode, idempotencyKey, userId);
        }

        for (SourceBatchImpact impact : sourceBatchImpacts.values()) {
            stockBatchService.restoreSourceBatch(
                impact.stockBalanceId(),
                impact.originalLineId(),
                impact.returnQtyDisplayUom(),
                userId);
            // Source batch restored — re-derive the balance's average cost from open batches.
            stockBalanceRepository.findById(impact.stockBalanceId())
                .ifPresent(stockBalanceService::recalculateFromOpenBatches);
        }

        ret.setStatus(DocumentStatus.COMPLETE);
        ret.setPostedToInventory(false);
        ret.setUnpostedAt(LocalDateTime.now(tenantTimeZoneService.zoneFor(tenantId)));
        ret.setUnpostedBy(userId);
        ret.setUpdatedBy(userId);

        log.info("Unposted purchase return id={} tenant={} reversedTxCount={} reason={}",
            ret.getId(), tenantId, originalTransactions.size(), reasonCode);
        return mapper.toResponse(returnRepository.save(ret));
    }

    @Transactional
    public PurchaseReturnResponse cancel(Long id, String reason, Long tenantId, Long userId) {
        PurchaseReturn ret = loadOwned(id, tenantId);
        if (ret.getStatus() == DocumentStatus.POSTED) {
            throw new BusinessException(InventoryErrorCode.INVALID_STATE_TRANSITION,
                "Posted returns cannot be cancelled",
                ErrorParams.of("entityType", "PurchaseReturn", "currentStatus", ret.getStatus().name(),
                    "action", "cancel"));
        }
        if (ret.getStatus() != DocumentStatus.DRAFT
                && ret.getStatus() != DocumentStatus.COMPLETE) {
            throw new BusinessException(InventoryErrorCode.INVALID_STATE_TRANSITION,
                "Only DRAFT or COMPLETE returns can be cancelled",
                ErrorParams.of("entityType", "PurchaseReturn", "currentStatus", ret.getStatus().name(),
                    "requiredStatus", "DRAFT,COMPLETE", "action", "cancel"));
        }
        ret.setStatus(DocumentStatus.CANCELLED);
        ret.setCancelledAt(LocalDateTime.now(tenantTimeZoneService.zoneFor(tenantId)));
        ret.setCancelledBy(userId);
        ret.setCancelReason(reason);
        return mapper.toResponse(returnRepository.save(ret));
    }

    @Transactional
    public void delete(Long id, Long tenantId) {
        PurchaseReturn ret = loadOwned(id, tenantId);
        if (ret.getStatus() != DocumentStatus.DRAFT) {
            throw new BusinessException(InventoryErrorCode.INVALID_STATE_TRANSITION,
                "Only DRAFT returns can be deleted — cancel the return instead",
                ErrorParams.of("entityType", "PurchaseReturn", "currentStatus", ret.getStatus().name(),
                    "requiredStatus", "DRAFT", "action", "delete"));
        }
        if (transactionRepository.existsByReference(
                tenantId, PURCHASE_RETURN_REFERENCE, ret.getId())) {
            throw new BusinessException(InventoryErrorCode.ALREADY_PROCESSED,
                "Purchase return cannot be deleted because it has inventory ledger history",
                ErrorParams.of("entityType", "PurchaseReturn",
                    "returnId", ret.getId(),
                    "referenceType", PURCHASE_RETURN_REFERENCE,
                    "action", "delete"));
        }
        returnRepository.delete(ret);
        log.info("Deleted purchase return id={} tenant={}", id, tenantId);
    }

    @Transactional
    public PurchaseReturnResponse uncomplete(Long id, UncompleteRequest request, Long tenantId, Long userId) {
        PurchaseReturn ret = loadOwned(id, tenantId);
        if (ret.getStatus() != DocumentStatus.COMPLETE) {
            throw new BusinessException(InventoryErrorCode.INVALID_STATE_TRANSITION,
                "Only COMPLETE returns can be uncompleted",
                ErrorParams.of("entityType", "PurchaseReturn", "currentStatus", ret.getStatus().name(),
                    "requiredStatus", "COMPLETE", "action", "uncomplete"));
        }
        String reason = request != null ? request.getReason() : null;
        ret.setStatus(DocumentStatus.DRAFT);
        ret.setUnCompletedAt(LocalDateTime.now(tenantTimeZoneService.zoneFor(tenantId)));
        ret.setUnCompletedBy(userId);
        ret.setUpdatedBy(userId);
        log.info("UnCompleted purchase return id={} tenant={} reason={}",
            ret.getId(), tenantId, reason);
        return mapper.toResponse(returnRepository.save(ret));
    }

    // =========================================================================
    // Internals
    // =========================================================================

    private Map<Long, SourceBatchImpact> loadSourceBatchImpacts(PurchaseReturn ret, Long tenantId) {
        Long warehouseId = ret.getWarehouse().getId();
        List<Long> materialIds = ret.getLines().stream()
            .map(l -> l.getMaterial().getId())
            .distinct()
            .toList();

        Map<Long, StockBalance> balanceMap = stockBalanceRepository
            .findByWarehouseAndMaterials(tenantId, warehouseId, materialIds).stream()
            .collect(Collectors.toMap(sb -> sb.getMaterial().getId(), sb -> sb));

        Map<Long, SourceBatchImpact> impacts = new HashMap<>();
        for (PurchaseReturnLine line : ret.getLines()) {
            StockBalance balance = balanceMap.get(line.getMaterial().getId());
            if (balance == null) {
                throw new BusinessException(InventoryErrorCode.INSUFFICIENT_STOCK,
                    "No stock balance found for material: " + line.getMaterial().getName(),
                    ErrorParams.of("materialName", line.getMaterial().getName(),
                        "available", BigDecimal.ZERO, "requested", line.getQuantity()));
            }

            Long originalLineId = line.getOriginalLine().getId();
            BigDecimal returnQtyDisplayUom = returnQuantityInDisplayUom(line, balance, tenantId);
            SourceBatchImpact existing = impacts.get(originalLineId);
            if (existing != null) {
                impacts.put(originalLineId, new SourceBatchImpact(
                    existing.originalLineId(),
                    existing.stockBalanceId(),
                    existing.batch(),
                    existing.returnQtyDisplayUom().add(returnQtyDisplayUom)));
                continue;
            }

            StockBatch sourceBatch = stockBatchService.requireSourceBatch(balance.getId(), originalLineId);
            impacts.put(originalLineId, new SourceBatchImpact(
                originalLineId, balance.getId(), sourceBatch, returnQtyDisplayUom));
        }
        return impacts;
    }

    private BigDecimal returnQuantityInDisplayUom(PurchaseReturnLine line,
                                                  StockBalance balance,
                                                  Long tenantId) {
        BigDecimal stockQty = uomConversionService.convertToStockUom(
            line.getQuantity(), line.getUom(), line.getMaterial(), tenantId);
        return uomConversionService.convert(
            stockQty, line.getMaterial().getStockUom(), balance.getUom(),
            line.getMaterial(), tenantId);
    }

    private void assertOriginalInvoiceStillPosted(PurchaseReturn ret) {
        PurchaseInvoice invoice = ret.getOriginalInvoice();
        if (invoice.getStatus() != DocumentStatus.POSTED) {
            throw new BusinessException(InventoryErrorCode.UNPOST_BLOCKED_ORIGINAL_INVOICE_NOT_POSTED,
                "Cannot unpost purchase return " + ret.getId()
                    + " because the original invoice is not POSTED",
                ErrorParams.of("entityType", "PurchaseReturn",
                    "returnId", ret.getId(),
                    "originalInvoiceId", invoice.getId(),
                    "originalInvoiceStatus", invoice.getStatus() != null
                        ? invoice.getStatus().name()
                        : null));
        }
    }

    private Map<Long, BigDecimal> loadAlreadyReturnedQuantities(Long invoiceId, Long tenantId) {
        Map<Long, BigDecimal> result = new HashMap<>();
        for (PurchaseReturnLine line : returnRepository.findPostedReturnLinesByInvoiceId(tenantId, invoiceId)) {
            PurchaseInvoiceLine originalLine = line.getOriginalLine();
            BigDecimal quantityInOriginalUom = quantityInOriginalLineUom(
                line.getQuantity(), line.getUom(), originalLine, tenantId);
            result.merge(originalLine.getId(), quantityInOriginalUom, BigDecimal::add);
        }
        return result;
    }

    private void recalcTotals(PurchaseReturn ret) {
        BigDecimal subtotal = ret.getLines().stream()
            .map(PurchaseReturnLine::getLineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        ret.setSubtotal(subtotal);
        ret.setTotalAmount(subtotal);
    }

    private PurchaseInvoiceLine resolveOriginalLine(PurchaseReturn ret, Long originalLineId) {
        return ret.getOriginalInvoice().getLines().stream()
            .filter(l -> l.getId().equals(originalLineId))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException(InventoryErrorCode.RESOURCE_NOT_FOUND,
                "Original line " + originalLineId + " does not belong to invoice "
                    + ret.getOriginalInvoice().getId(),
                ErrorParams.of("entityType", "PurchaseInvoiceLine", "entityId", originalLineId,
                    "invoiceId", ret.getOriginalInvoice().getId())));
    }

    private PurchaseReturnLine findLine(PurchaseReturn ret, Long lineId) {
        return ret.getLines().stream()
            .filter(l -> l.getId().equals(lineId))
            .findFirst()
            .orElseThrow(() -> new ResourceNotFoundException(InventoryErrorCode.RESOURCE_NOT_FOUND,
                "Purchase return line not found: " + lineId,
                ErrorParams.of("entityType", "PurchaseReturnLine", "entityId", lineId)));
    }

    /**
     * Ensures the requested quantity for an original line does not exceed what is still
     * returnable: original quantity − quantity taken by POSTED returns − quantity already
     * on this draft's other lines for the same original line ({@code excludeLineId} skips
     * the line being updated).
     */
    private BigDecimal validateReturnable(PurchaseReturn ret, PurchaseInvoiceLine originalLine,
                                          BigDecimal requestedQuantity, Uom requestedUom,
                                          Long excludeLineId, Long tenantId) {
        BigDecimal requestedInOriginalUom = quantityInOriginalLineUom(
            requestedQuantity, requestedUom, originalLine, tenantId);
        BigDecimal postedReturned = loadAlreadyReturnedQuantities(ret.getOriginalInvoice().getId(), tenantId)
            .getOrDefault(originalLine.getId(), BigDecimal.ZERO);

        BigDecimal draftForSameLine = ret.getLines().stream()
            .filter(l -> excludeLineId == null || !excludeLineId.equals(l.getId()))
            .filter(l -> l.getOriginalLine().getId().equals(originalLine.getId()))
            .map(l -> quantityInOriginalLineUom(l.getQuantity(), l.getUom(), originalLine, tenantId))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal returnable = originalLine.getQuantity()
            .subtract(postedReturned)
            .subtract(draftForSameLine);

        if (requestedInOriginalUom.compareTo(returnable) > 0) {
            throw new BusinessException(InventoryErrorCode.RETURN_QUANTITY_EXCEEDED,
                "Return quantity exceeds returnable quantity for material: "
                    + originalLine.getMaterial().getName(),
                ErrorParams.of("materialName", originalLine.getMaterial().getName(),
                    "returnable", returnable, "requested", requestedInOriginalUom));
        }
        return requestedInOriginalUom;
    }

    private BigDecimal quantityInOriginalLineUom(BigDecimal quantity, Uom fromUom,
                                                 PurchaseInvoiceLine originalLine,
                                                 Long tenantId) {
        return uomConversionService.convert(
            quantity, fromUom, originalLine.getUom(), originalLine.getMaterial(), tenantId);
    }

    private BigDecimal calculateLineTotal(BigDecimal quantity, BigDecimal unitCost) {
        return quantity.multiply(unitCost).setScale(SCALE, ROUNDING);
    }

    private BigDecimal convertUnitCost(BigDecimal unitCost, Uom fromUom, Uom toUom,
                                       Material material, Long tenantId) {
        BigDecimal fromUnitsPerTargetUnit = uomConversionService.convert(
            BigDecimal.ONE, toUom, fromUom, material, tenantId);
        return unitCost.multiply(fromUnitsPerTargetUnit).setScale(SCALE, ROUNDING);
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

    private void requireDraft(PurchaseReturn ret) {
        if (ret.getStatus() != DocumentStatus.DRAFT) {
            throw new BusinessException(InventoryErrorCode.INVALID_STATE_TRANSITION,
                "Cannot edit a return that is not in DRAFT status",
                ErrorParams.of("entityType", "PurchaseReturn", "currentStatus", ret.getStatus().name(),
                    "requiredStatus", "DRAFT", "action", "edit"));
        }
    }

    private PurchaseReturn loadOwned(Long id, Long tenantId) {
        return returnRepository.findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException(InventoryErrorCode.RESOURCE_NOT_FOUND,
                "Purchase return not found: " + id,
                ErrorParams.of("entityType", "PurchaseReturn", "entityId", id)));
    }

    private record SourceBatchImpact(
        Long originalLineId,
        Long stockBalanceId,
        StockBatch batch,
        BigDecimal returnQtyDisplayUom
    ) {}
}
