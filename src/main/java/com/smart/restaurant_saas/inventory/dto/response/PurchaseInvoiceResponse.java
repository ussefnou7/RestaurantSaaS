package com.smart.restaurant_saas.inventory.dto.response;

import com.smart.restaurant_saas.inventory.enums.DocumentStatus;
import com.smart.restaurant_saas.inventory.enums.PurchasePaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PurchaseInvoiceResponse(
        Long id,
        Long tenantId,
        Long supplierId,
        String supplierCode,
        String supplierName,
        String supplierNameAr,
        Long warehouseId,
        String warehouseCode,
        String warehouseName,
        String warehouseNameAr,
        String invoiceNumber,
        LocalDate invoiceDate,
        LocalDate receiptDate,
        DocumentStatus status,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        PurchasePaymentStatus paymentStatus,
        String notes,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Boolean postedToInventory,
        LocalDateTime postedAt,
        Long postedBy,
        LocalDateTime completedAt,
        Long completedBy,
        LocalDateTime cancelledAt,
        Long cancelledBy,
        String cancelReason,
        List<PurchaseInvoiceLineResponse> lines
) {
}
