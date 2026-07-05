package com.smart.restaurant_saas.inventory.purchase.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import com.smart.restaurant_saas.inventory.core.enums.DocumentStatus;
import com.smart.restaurant_saas.inventory.core.enums.PurchaseReturnReason;

@Getter
@Builder
public class PurchaseReturnResponse {

    private final Long id;
    private final Long originalInvoiceId;
    private final String originalInvoiceNumber;
    private final Long supplierId;
    private final String supplierName;
    private final Long warehouseId;
    private final String warehouseName;
    private final String returnNumber;
    private final LocalDate returnDate;
    private final PurchaseReturnReason reason;
    private final DocumentStatus status;
    private final BigDecimal subtotal;
    private final BigDecimal totalAmount;
    private final Boolean postedToInventory;
    private final LocalDateTime postedAt;
    private final LocalDateTime unpostedAt;
    private final Long unpostedBy;
    private final LocalDateTime unCompletedAt;
    private final Long unCompletedBy;
    private final String notes;
    private final List<PurchaseReturnLineResponse> lines;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
