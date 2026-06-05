package com.smart.restaurant_saas.inventory.mapper;

import com.smart.restaurant_saas.inventory.dto.response.PurchaseInvoiceLineResponse;
import com.smart.restaurant_saas.inventory.dto.response.PurchaseInvoiceResponse;
import com.smart.restaurant_saas.inventory.entity.Material;
import com.smart.restaurant_saas.inventory.entity.MaterialCategory;
import com.smart.restaurant_saas.inventory.entity.PurchaseInvoice;
import com.smart.restaurant_saas.inventory.entity.PurchaseInvoiceLine;
import com.smart.restaurant_saas.inventory.entity.Supplier;
import com.smart.restaurant_saas.inventory.entity.Uom;
import com.smart.restaurant_saas.inventory.entity.Warehouse;
import java.util.Comparator;
import org.springframework.stereotype.Component;

@Component
public class PurchaseInvoiceMapper {

    public PurchaseInvoiceResponse toResponse(PurchaseInvoice invoice) {
        Supplier supplier = invoice.getSupplier();
        Warehouse warehouse = invoice.getWarehouse();
        return new PurchaseInvoiceResponse(
                invoice.getId(),
                invoice.getTenantId(),
                supplier == null ? null : supplier.getId(),
                supplier == null ? null : supplier.getCode(),
                supplier == null ? null : supplier.getName(),
                supplier == null ? null : supplier.getNameAr(),
                warehouse.getId(),
                warehouse.getCode(),
                warehouse.getName(),
                warehouse.getNameAr(),
                invoice.getInvoiceNumber(),
                invoice.getInvoiceDate(),
                invoice.getReceiptDate(),
                invoice.getStatus(),
                invoice.getSubtotal(),
                invoice.getDiscountAmount(),
                invoice.getTaxAmount(),
                invoice.getTotalAmount(),
                invoice.getPaidAmount(),
                invoice.getPaymentStatus(),
                invoice.getNotes(),
                invoice.getCreatedBy(),
                invoice.getCreatedAt(),
                invoice.getUpdatedAt(),
                invoice.getPostedToInventory(),
                invoice.getPostedAt(),
                invoice.getPostedBy(),
                invoice.getCompletedAt(),
                invoice.getCompletedBy(),
                invoice.getCancelledAt(),
                invoice.getCancelledBy(),
                invoice.getCancelReason(),
                invoice.getLines().stream()
                        .sorted(Comparator.comparing(PurchaseInvoiceLine::getId, Comparator.nullsLast(Long::compareTo)))
                        .map(this::toLineResponse)
                        .toList()
        );
    }

    private PurchaseInvoiceLineResponse toLineResponse(PurchaseInvoiceLine line) {
        Material material = line.getMaterial();
        MaterialCategory category = material.getCategory();
        Uom uom = line.getUom();
        return new PurchaseInvoiceLineResponse(
                line.getId(),
                material.getId(),
                material.getCode(),
                material.getName(),
                material.getNameAr(),
                category.getId(),
                category.getCode(),
                category.getName(),
                category.getNameAr(),
                line.getQuantity(),
                uom.getId(),
                uom.getCode(),
                uom.getName(),
                uom.getNameAr(),
                uom.getSymbol(),
                line.getUnitCost(),
                line.getLineTotal(),
                line.getNotes()
        );
    }
}
