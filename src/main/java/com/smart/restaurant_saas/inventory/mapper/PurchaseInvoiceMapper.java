package com.smart.restaurant_saas.inventory.mapper;

import java.util.List;
import org.springframework.stereotype.Component;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.purchase.PurchaseInvoice;
import com.smart.restaurant_saas.inventory.purchase.PurchaseInvoiceLine;
import com.smart.restaurant_saas.inventory.purchase.Supplier;
import com.smart.restaurant_saas.inventory.purchase.dto.PurchaseInvoiceLineResponse;
import com.smart.restaurant_saas.inventory.purchase.dto.PurchaseInvoiceResponse;
import com.smart.restaurant_saas.inventory.uom.Uom;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;

@Component
public class PurchaseInvoiceMapper {

    /** Summary view for list screens — lines are omitted. */
    public PurchaseInvoiceResponse toSummary(PurchaseInvoice invoice) {
        return baseBuilder(invoice).lines(null).build();
    }

    /** Full view including lines, for the detail screen. */
    public PurchaseInvoiceResponse toResponse(PurchaseInvoice invoice) {
        List<PurchaseInvoiceLineResponse> lines = invoice.getLines().stream()
            .map(this::toLineResponse)
            .toList();
        return baseBuilder(invoice).lines(lines).build();
    }

    private PurchaseInvoiceResponse.PurchaseInvoiceResponseBuilder baseBuilder(PurchaseInvoice invoice) {
        Supplier supplier = invoice.getSupplier();
        Warehouse warehouse = invoice.getWarehouse();
        return PurchaseInvoiceResponse.builder()
            .id(invoice.getId())
            .supplierId(supplier != null ? supplier.getId() : null)
            .supplierName(supplier != null ? supplier.getName() : null)
            .warehouseId(warehouse != null ? warehouse.getId() : null)
            .warehouseName(warehouse != null ? warehouse.getName() : null)
            .invoiceNumber(invoice.getInvoiceNumber())
            .invoiceDate(invoice.getInvoiceDate())
            .receiptDate(invoice.getReceiptDate())
            .status(invoice.getStatus())
            .subtotal(invoice.getSubtotal())
            .discountPercent(invoice.getDiscountPercent())
            .discountAmount(invoice.getDiscountAmount())
            .taxPercent(invoice.getTaxPercent())
            .taxAmount(invoice.getTaxAmount())
            .totalAmount(invoice.getTotalAmount())
            .paidAmount(invoice.getPaidAmount())
            .paymentStatus(invoice.getPaymentStatus())
            .postedToInventory(invoice.getPostedToInventory())
            .postedAt(invoice.getPostedAt())
            .unpostedAt(invoice.getUnpostedAt())
            .unpostedBy(invoice.getUnpostedBy())
            .unCompletedAt(invoice.getUnCompletedAt())
            .unCompletedBy(invoice.getUnCompletedBy())
            .notes(invoice.getNotes())
            .createdAt(invoice.getCreatedAt())
            .updatedAt(invoice.getUpdatedAt());
    }

    private PurchaseInvoiceLineResponse toLineResponse(PurchaseInvoiceLine line) {
        Material material = line.getMaterial();
        Uom uom = line.getUom();
        return PurchaseInvoiceLineResponse.builder()
            .id(line.getId())
            .materialId(material != null ? material.getId() : null)
            .materialCode(material != null ? material.getCode() : null)
            .materialName(material != null ? material.getName() : null)
            .quantity(line.getQuantity())
            .uomId(uom != null ? uom.getId() : null)
            .uomSymbol(uom != null ? uom.getSymbol() : null)
            .unitCost(line.getUnitCost())
            .lineTotal(line.getLineTotal())
            .discountPercent(line.getDiscountPercent())
            .discountAmount(line.getDiscountAmount())
            .lineSubtotal(line.getLineTotal())
            .lineNetTotal(line.getLineNetTotal())
            .notes(line.getNotes())
            .build();
    }
}
