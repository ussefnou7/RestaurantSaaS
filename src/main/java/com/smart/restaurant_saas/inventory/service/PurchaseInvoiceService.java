package com.smart.restaurant_saas.inventory.service;

import static com.smart.restaurant_saas.inventory.service.CatalogInputNormalizer.searchPattern;
import static com.smart.restaurant_saas.inventory.service.CatalogInputNormalizer.trimToNull;

import com.smart.restaurant_saas.common.ApiException;
import com.smart.restaurant_saas.inventory.dto.command.InventoryTransactionCommand;
import com.smart.restaurant_saas.inventory.dto.request.CancelPurchaseInvoiceRequest;
import com.smart.restaurant_saas.inventory.dto.request.CreatePurchaseInvoiceRequest;
import com.smart.restaurant_saas.inventory.dto.request.PurchaseInvoiceLineRequest;
import com.smart.restaurant_saas.inventory.dto.request.UpdatePurchaseInvoiceRequest;
import com.smart.restaurant_saas.inventory.dto.response.PurchaseInvoiceResponse;
import com.smart.restaurant_saas.inventory.entity.Material;
import com.smart.restaurant_saas.inventory.entity.PurchaseInvoice;
import com.smart.restaurant_saas.inventory.entity.PurchaseInvoiceLine;
import com.smart.restaurant_saas.inventory.entity.Supplier;
import com.smart.restaurant_saas.inventory.entity.Uom;
import com.smart.restaurant_saas.inventory.entity.Warehouse;
import com.smart.restaurant_saas.inventory.enums.DocumentHistoryAction;
import com.smart.restaurant_saas.inventory.enums.DocumentStatus;
import com.smart.restaurant_saas.inventory.enums.DocumentType;
import com.smart.restaurant_saas.inventory.enums.InventoryTransactionDirection;
import com.smart.restaurant_saas.inventory.enums.InventoryTransactionType;
import com.smart.restaurant_saas.inventory.enums.PurchasePaymentStatus;
import com.smart.restaurant_saas.inventory.mapper.PurchaseInvoiceMapper;
import com.smart.restaurant_saas.inventory.repository.MaterialRepository;
import com.smart.restaurant_saas.inventory.repository.PurchaseInvoiceRepository;
import com.smart.restaurant_saas.inventory.repository.SupplierRepository;
import com.smart.restaurant_saas.inventory.repository.UomRepository;
import com.smart.restaurant_saas.inventory.repository.WarehouseRepository;
import com.smart.restaurant_saas.tenant.CurrentTenantProvider;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PurchaseInvoiceService {

    private static final String REFERENCE_TYPE = "PURCHASE_INVOICE";
    private static final int MONEY_SCALE = 6;

    private final CurrentTenantProvider currentTenantProvider;
    private final PurchaseInvoiceRepository purchaseInvoiceRepository;
    private final SupplierRepository supplierRepository;
    private final WarehouseRepository warehouseRepository;
    private final MaterialRepository materialRepository;
    private final UomRepository uomRepository;
    private final InventoryTransactionService inventoryTransactionService;
    private final UomConversionService uomConversionService;
    private final DocumentHistoryService documentHistoryService;
    private final PurchaseInvoiceMapper purchaseInvoiceMapper;

    @Transactional(readOnly = true)
    public List<PurchaseInvoiceResponse> listInvoices(
            String search,
            Long supplierId,
            Long warehouseId,
            String status,
            String paymentStatus,
            String dateFrom,
            String dateTo
    ) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        return purchaseInvoiceRepository.findByTenantIdAndFilters(
                        tenantId,
                        searchPattern(search),
                        supplierId,
                        warehouseId,
                        parseStatus(status),
                        parsePaymentStatus(paymentStatus),
                        parseDate(dateFrom),
                        parseDate(dateTo)
                ).stream()
                .map(purchaseInvoiceMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PurchaseInvoiceResponse getInvoice(Long id) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        return purchaseInvoiceMapper.toResponse(findInvoice(tenantId, id));
    }

    @Transactional
    public PurchaseInvoiceResponse createInvoice(CreatePurchaseInvoiceRequest request) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        PurchaseInvoice invoice = new PurchaseInvoice();
        invoice.setTenantId(tenantId);
        invoice.setStatus(DocumentStatus.DRAFT);
        invoice.setCreatedBy(currentTenantProvider.getActorUserId());
        applyCreateFields(tenantId, invoice, request);

        return purchaseInvoiceMapper.toResponse(purchaseInvoiceRepository.saveAndFlush(invoice));
    }

    @Transactional
    public PurchaseInvoiceResponse updateInvoice(Long id, UpdatePurchaseInvoiceRequest request) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        PurchaseInvoice invoice = findInvoice(tenantId, id);
        ensureDraftEditable(invoice);
        applyUpdateFields(tenantId, invoice, request);

        return purchaseInvoiceMapper.toResponse(purchaseInvoiceRepository.saveAndFlush(invoice));
    }

    @Transactional
    public PurchaseInvoiceResponse completeInvoice(Long id) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        Long actorUserId = currentTenantProvider.getActorUserId();
        PurchaseInvoice invoice = findInvoice(tenantId, id);
        if (invoice.getStatus() != DocumentStatus.DRAFT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only DRAFT purchase invoices can be completed");
        }
        if (invoice.getLines().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Purchase invoice must have at least one line before completion");
        }

        validateCompleteReadiness(tenantId, invoice);

        if (!Boolean.TRUE.equals(invoice.getPostedToInventory())) {
            for (PurchaseInvoiceLine line : invoice.getLines()) {
                inventoryTransactionService.createTransaction(new InventoryTransactionCommand(
                        invoice.getWarehouse().getId(),
                        line.getMaterial().getId(),
                        InventoryTransactionType.PURCHASE_IN,
                        InventoryTransactionDirection.IN,
                        line.getQuantity(),
                        line.getUom().getId(),
                        line.getUnitCost(),
                        REFERENCE_TYPE,
                        invoice.getId(),
                        invoice.getReceiptDate().atStartOfDay(),
                        "Purchase invoice " + referenceNumber(invoice)
                ));
            }
        }

        LocalDateTime now = LocalDateTime.now();
        invoice.setStatus(DocumentStatus.COMPLETED);
        invoice.setCompletedAt(now);
        invoice.setCompletedBy(actorUserId);
        invoice.setPostedToInventory(true);
        invoice.setPostedAt(now);
        invoice.setPostedBy(actorUserId);

        documentHistoryService.record(
                tenantId,
                DocumentType.PURCHASE_INVOICE,
                invoice.getId(),
                DocumentHistoryAction.COMPLETE,
                actorUserId,
                null
        );

        return purchaseInvoiceMapper.toResponse(purchaseInvoiceRepository.saveAndFlush(invoice));
    }

    @Transactional
    public PurchaseInvoiceResponse cancelInvoice(Long id, CancelPurchaseInvoiceRequest request) {
        Long tenantId = currentTenantProvider.getCurrentTenantId();
        Long actorUserId = currentTenantProvider.getActorUserId();
        PurchaseInvoice invoice = findInvoice(tenantId, id);
        if (invoice.getStatus() != DocumentStatus.DRAFT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only DRAFT purchase invoices can be cancelled");
        }

        LocalDateTime now = LocalDateTime.now();
        invoice.setStatus(DocumentStatus.CANCELLED);
        invoice.setCancelledAt(now);
        invoice.setCancelledBy(actorUserId);
        invoice.setCancelReason(request == null ? null : trimToNull(request.cancelReason()));

        documentHistoryService.record(
                tenantId,
                DocumentType.PURCHASE_INVOICE,
                invoice.getId(),
                DocumentHistoryAction.CANCEL,
                actorUserId,
                invoice.getCancelReason()
        );

        return purchaseInvoiceMapper.toResponse(purchaseInvoiceRepository.saveAndFlush(invoice));
    }

    private void applyCreateFields(Long tenantId, PurchaseInvoice invoice, CreatePurchaseInvoiceRequest request) {
        applyEditableFields(
                tenantId,
                invoice,
                request.supplierId(),
                request.warehouseId(),
                request.invoiceNumber(),
                request.invoiceDate(),
                request.receiptDate(),
                request.discountAmount(),
                request.taxAmount(),
                request.paidAmount(),
                request.notes(),
                request.lines(),
                null
        );
    }

    private void applyUpdateFields(Long tenantId, PurchaseInvoice invoice, UpdatePurchaseInvoiceRequest request) {
        applyEditableFields(
                tenantId,
                invoice,
                request.supplierId(),
                request.warehouseId(),
                request.invoiceNumber(),
                request.invoiceDate(),
                request.receiptDate(),
                request.discountAmount(),
                request.taxAmount(),
                request.paidAmount(),
                request.notes(),
                request.lines(),
                invoice.getId()
        );
    }

    private void applyEditableFields(
            Long tenantId,
            PurchaseInvoice invoice,
            Long supplierId,
            Long warehouseId,
            String invoiceNumber,
            LocalDate invoiceDate,
            LocalDate receiptDate,
            BigDecimal discountAmount,
            BigDecimal taxAmount,
            BigDecimal paidAmount,
            String notes,
            List<PurchaseInvoiceLineRequest> lineRequests,
            Long existingInvoiceId
    ) {
        if (invoiceDate == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invoiceDate is required");
        }

        String normalizedInvoiceNumber = trimToNull(invoiceNumber);
        validateUniqueInvoiceNumber(tenantId, normalizedInvoiceNumber, existingInvoiceId);

        invoice.setSupplier(findSupplier(tenantId, supplierId));
        invoice.setWarehouse(findWarehouse(tenantId, warehouseId));
        invoice.setInvoiceNumber(normalizedInvoiceNumber);
        invoice.setInvoiceDate(invoiceDate);
        invoice.setReceiptDate(receiptDate == null ? invoiceDate : receiptDate);
        invoice.setDiscountAmount(normalizeNonNegative(discountAmount, "discountAmount"));
        invoice.setTaxAmount(normalizeNonNegative(taxAmount, "taxAmount"));
        invoice.setPaidAmount(normalizeNonNegative(paidAmount, "paidAmount"));
        invoice.setNotes(trimToNull(notes));

        replaceLines(tenantId, invoice, lineRequests);
        recalculateTotals(invoice);
    }

    private void replaceLines(
            Long tenantId,
            PurchaseInvoice invoice,
            List<PurchaseInvoiceLineRequest> lineRequests
    ) {
        invoice.getLines().clear();
        if (lineRequests == null) {
            return;
        }

        for (PurchaseInvoiceLineRequest request : lineRequests) {
            PurchaseInvoiceLine line = buildLine(tenantId, invoice, request);
            invoice.getLines().add(line);
        }
    }

    private PurchaseInvoiceLine buildLine(
            Long tenantId,
            PurchaseInvoice invoice,
            PurchaseInvoiceLineRequest request
    ) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Purchase invoice line must not be null");
        }
        Material material = findActiveMaterial(tenantId, request.materialId());
        Uom uom = findActiveUom(request.uomId());
        if (uom.getType() != material.getStockUom().getType()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Line UOM is not compatible with material stock UOM");
        }

        BigDecimal quantity = normalizePositive(request.quantity(), "quantity");
        BigDecimal unitCost = normalizeNonNegative(request.unitCost(), "unitCost");

        PurchaseInvoiceLine line = new PurchaseInvoiceLine();
        line.setPurchaseInvoice(invoice);
        line.setMaterial(material);
        line.setQuantity(quantity);
        line.setUom(uom);
        line.setUnitCost(unitCost);
        line.setLineTotal(scaleMoney(quantity.multiply(unitCost)));
        line.setNotes(trimToNull(request.notes()));
        return line;
    }

    private void recalculateTotals(PurchaseInvoice invoice) {
        BigDecimal subtotal = invoice.getLines().stream()
                .map(PurchaseInvoiceLine::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal total = subtotal
                .subtract(invoice.getDiscountAmount())
                .add(invoice.getTaxAmount());
        if (total.compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "totalAmount must be greater than or equal to 0");
        }

        invoice.setSubtotal(scaleMoney(subtotal));
        invoice.setTotalAmount(scaleMoney(total));
        invoice.setPaymentStatus(resolvePaymentStatus(invoice.getPaidAmount(), invoice.getTotalAmount()));
    }

    private PurchasePaymentStatus resolvePaymentStatus(BigDecimal paidAmount, BigDecimal totalAmount) {
        BigDecimal paid = nullToZero(paidAmount);
        BigDecimal total = nullToZero(totalAmount);
        if (paid.compareTo(BigDecimal.ZERO) <= 0) {
            return PurchasePaymentStatus.UNPAID;
        }
        if (paid.compareTo(total) < 0) {
            return PurchasePaymentStatus.PARTIALLY_PAID;
        }
        return PurchasePaymentStatus.PAID;
    }

    private PurchaseInvoice findInvoice(Long tenantId, Long id) {
        return purchaseInvoiceRepository.findDetailedByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Purchase invoice not found: " + id));
    }

    private void ensureDraftEditable(PurchaseInvoice invoice) {
        if (invoice.getStatus() != DocumentStatus.DRAFT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Only DRAFT purchase invoices can be edited");
        }
    }

    private void validateCompleteReadiness(Long tenantId, PurchaseInvoice invoice) {
        Supplier supplier = invoice.getSupplier();
        if (supplier != null) {
            supplierRepository.findByIdAndTenantId(supplier.getId(), tenantId)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid supplier: " + supplier.getId()));
        }

        Warehouse warehouse = invoice.getWarehouse();
        warehouseRepository.findDetailedByIdAndTenantId(warehouse.getId(), tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid warehouse: " + warehouse.getId()));
        if (!Boolean.TRUE.equals(warehouse.getActive())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Warehouse is inactive: " + warehouse.getId());
        }

        for (PurchaseInvoiceLine line : invoice.getLines()) {
            Material material = line.getMaterial();
            materialRepository.findDetailedByIdAndTenantId(material.getId(), tenantId)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid material: " + material.getId()));
            if (!Boolean.TRUE.equals(material.getActive())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Material is inactive: " + material.getId());
            }

            Uom uom = line.getUom();
            Uom stockUom = material.getStockUom();
            if (!Boolean.TRUE.equals(uom.getActive())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "UOM is inactive: " + uom.getId());
            }
            try {
                uomConversionService.convert(line.getQuantity(), uom, stockUom);
            } catch (ApiException ex) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Line UOM is not compatible with material stock UOM");
            }
        }
    }

    private Supplier findSupplier(Long tenantId, Long supplierId) {
        if (supplierId == null) {
            return null;
        }
        return supplierRepository.findByIdAndTenantId(supplierId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid supplier: " + supplierId));
    }

    private Warehouse findWarehouse(Long tenantId, Long warehouseId) {
        if (warehouseId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "warehouseId is required");
        }
        return warehouseRepository.findDetailedByIdAndTenantId(warehouseId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid warehouse: " + warehouseId));
    }

    private Material findActiveMaterial(Long tenantId, Long materialId) {
        if (materialId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "materialId is required");
        }
        Material material = materialRepository.findDetailedByIdAndTenantId(materialId, tenantId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid material: " + materialId));
        if (!Boolean.TRUE.equals(material.getActive())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Material is inactive: " + materialId);
        }
        return material;
    }

    private Uom findActiveUom(Long uomId) {
        if (uomId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "uomId is required");
        }
        Uom uom = uomRepository.findById(uomId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid UOM: " + uomId));
        if (!Boolean.TRUE.equals(uom.getActive())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UOM is inactive: " + uomId);
        }
        return uom;
    }

    private void validateUniqueInvoiceNumber(Long tenantId, String invoiceNumber, Long existingInvoiceId) {
        if (invoiceNumber == null) {
            return;
        }
        boolean exists = existingInvoiceId == null
                ? purchaseInvoiceRepository.existsByTenantIdAndInvoiceNumber(tenantId, invoiceNumber)
                : purchaseInvoiceRepository.existsByTenantIdAndInvoiceNumberAndIdNot(
                        tenantId,
                        invoiceNumber,
                        existingInvoiceId
                );
        if (exists) {
            throw new ApiException(HttpStatus.CONFLICT, "Purchase invoice number already exists: " + invoiceNumber);
        }
    }

    private DocumentStatus parseStatus(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            return DocumentStatus.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid purchase invoice status: " + value
                    + ". Allowed values: " + Arrays.toString(DocumentStatus.values()));
        }
    }

    private PurchasePaymentStatus parsePaymentStatus(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            return PurchasePaymentStatus.valueOf(normalized.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid purchase payment status: " + value
                    + ". Allowed values: " + Arrays.toString(PurchasePaymentStatus.values()));
        }
    }

    private LocalDate parseDate(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        try {
            return LocalDate.parse(normalized);
        } catch (DateTimeParseException ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid date filter: " + value);
        }
    }

    private String referenceNumber(PurchaseInvoice invoice) {
        return invoice.getInvoiceNumber() == null ? String.valueOf(invoice.getId()) : invoice.getInvoiceNumber();
    }

    private BigDecimal normalizePositive(BigDecimal value, String fieldName) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, fieldName + " must be greater than 0");
        }
        return scaleMoney(value);
    }

    private BigDecimal normalizeNonNegative(BigDecimal value, String fieldName) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, fieldName + " must be greater than or equal to 0");
        }
        return scaleMoney(value);
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal scaleMoney(BigDecimal value) {
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }
}
