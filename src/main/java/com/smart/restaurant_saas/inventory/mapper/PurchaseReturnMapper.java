package com.smart.restaurant_saas.inventory.mapper;

import java.util.List;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.purchase.PurchaseInvoice;
import com.smart.restaurant_saas.inventory.purchase.PurchaseInvoiceLine;
import com.smart.restaurant_saas.inventory.purchase.PurchaseReturn;
import com.smart.restaurant_saas.inventory.purchase.PurchaseReturnLine;
import com.smart.restaurant_saas.inventory.purchase.Supplier;
import com.smart.restaurant_saas.inventory.purchase.dto.PurchaseReturnLineResponse;
import com.smart.restaurant_saas.inventory.purchase.dto.PurchaseReturnResponse;
import com.smart.restaurant_saas.inventory.purchase.dto.ReturnableLineResponse;
import com.smart.restaurant_saas.inventory.uom.Uom;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;

@Component
public class PurchaseReturnMapper {

    /** Summary view for list screens — lines are omitted. */
    public PurchaseReturnResponse toSummary(PurchaseReturn ret) {
        return baseBuilder(ret).lines(null).build();
    }

    /** Full view including lines, for the detail screen. */
    public PurchaseReturnResponse toResponse(PurchaseReturn ret) {
        List<PurchaseReturnLineResponse> lines = ret.getLines().stream()
            .map(this::toLineResponse)
            .toList();
        return baseBuilder(ret).lines(lines).build();
    }

    private PurchaseReturnResponse.PurchaseReturnResponseBuilder baseBuilder(PurchaseReturn ret) {
        Supplier supplier = ret.getSupplier();
        Warehouse warehouse = ret.getWarehouse();
        PurchaseInvoice invoice = ret.getOriginalInvoice();
        return PurchaseReturnResponse.builder()
            .id(ret.getId())
            .originalInvoiceId(invoice != null ? invoice.getId() : null)
            .originalInvoiceNumber(invoice != null ? invoice.getInvoiceNumber() : null)
            .supplierId(supplier != null ? supplier.getId() : null)
            .supplierName(supplier != null ? supplier.getName() : null)
            .warehouseId(warehouse != null ? warehouse.getId() : null)
            .warehouseName(warehouse != null ? warehouse.getName() : null)
            .returnNumber(ret.getReturnNumber())
            .returnDate(ret.getReturnDate())
            .reason(ret.getReason())
            .status(ret.getStatus())
            .subtotal(ret.getSubtotal())
            .totalAmount(ret.getTotalAmount())
            .postedToInventory(ret.getPostedToInventory())
            .postedAt(ret.getPostedAt())
            .unpostedAt(ret.getUnpostedAt())
            .unpostedBy(ret.getUnpostedBy())
            .unCompletedAt(ret.getUnCompletedAt())
            .unCompletedBy(ret.getUnCompletedBy())
            .notes(ret.getNotes())
            .createdAt(ret.getCreatedAt())
            .updatedAt(ret.getUpdatedAt());
    }

    /** Maps an original invoice line to its still-returnable view. */
    public ReturnableLineResponse toReturnableLine(PurchaseInvoiceLine line,
                                                   BigDecimal returnedQuantity,
                                                   BigDecimal returnableQuantity) {
        Material material = line.getMaterial();
        Uom uom = line.getUom();
        return ReturnableLineResponse.builder()
            .originalLineId(line.getId())
            .materialId(material != null ? material.getId() : null)
            .materialCode(material != null ? material.getCode() : null)
            .materialName(material != null ? material.getName() : null)
            .uomId(uom != null ? uom.getId() : null)
            .uomSymbol(uom != null ? uom.getSymbol() : null)
            .unitCost(line.getUnitCost())
            .originalQuantity(line.getQuantity())
            .returnedQuantity(returnedQuantity)
            .returnableQuantity(returnableQuantity)
            .build();
    }

    private PurchaseReturnLineResponse toLineResponse(PurchaseReturnLine line) {
        Material material = line.getMaterial();
        Uom uom = line.getUom();
        return PurchaseReturnLineResponse.builder()
            .id(line.getId())
            .originalLineId(line.getOriginalLine() != null ? line.getOriginalLine().getId() : null)
            .materialId(material != null ? material.getId() : null)
            .materialCode(material != null ? material.getCode() : null)
            .materialName(material != null ? material.getName() : null)
            .quantity(line.getQuantity())
            .uomId(uom != null ? uom.getId() : null)
            .uomSymbol(uom != null ? uom.getSymbol() : null)
            .unitCost(line.getUnitCost())
            .lineTotal(line.getLineTotal())
            .notes(line.getNotes())
            .build();
    }
}
