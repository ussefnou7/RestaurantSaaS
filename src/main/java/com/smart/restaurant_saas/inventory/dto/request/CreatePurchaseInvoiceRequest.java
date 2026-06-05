package com.smart.restaurant_saas.inventory.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreatePurchaseInvoiceRequest(
        Long supplierId,
        @NotNull Long warehouseId,
        @Size(max = 100) String invoiceNumber,
        @NotNull LocalDate invoiceDate,
        LocalDate receiptDate,
        @DecimalMin(value = "0.0") BigDecimal discountAmount,
        @DecimalMin(value = "0.0") BigDecimal taxAmount,
        @DecimalMin(value = "0.0") BigDecimal paidAmount,
        String notes,
        List<@Valid PurchaseInvoiceLineRequest> lines
) {
}
