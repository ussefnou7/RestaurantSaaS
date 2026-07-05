package com.smart.restaurant_saas.inventory.purchase.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import com.smart.restaurant_saas.inventory.core.enums.DocumentStatus;
import com.smart.restaurant_saas.inventory.core.enums.PurchasePaymentStatus;

@Getter
@Builder
public class PurchaseInvoiceResponse {

    private final Long id;
    private final Long supplierId;
    private final String supplierName;
    private final Long warehouseId;
    private final String warehouseName;
    private final String invoiceNumber;
    private final LocalDate invoiceDate;
    private final LocalDate receiptDate;
    private final DocumentStatus status;
    private final BigDecimal subtotal;
    private final BigDecimal discountPercent;
    private final BigDecimal discountAmount;
    private final BigDecimal taxPercent;
    private final BigDecimal taxAmount;
    private final BigDecimal totalAmount;
    private final BigDecimal paidAmount;
    private final PurchasePaymentStatus paymentStatus;
    private final Boolean postedToInventory;
    private final LocalDateTime postedAt;
    private final LocalDateTime unpostedAt;
    private final Long unpostedBy;
    private final LocalDateTime unCompletedAt;
    private final Long unCompletedBy;
    private final String notes;
    private final List<PurchaseInvoiceLineResponse> lines;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
