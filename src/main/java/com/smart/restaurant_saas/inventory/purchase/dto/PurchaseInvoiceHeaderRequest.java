package com.smart.restaurant_saas.inventory.purchase.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PurchaseInvoiceHeaderRequest {

    private Long supplierId;

    @NotNull(message = "warehouseId is required")
    private Long warehouseId;

    @NotNull(message = "invoiceDate is required")
    private LocalDate invoiceDate;

    @NotNull(message = "receiptDate is required")
    private LocalDate receiptDate;

    @DecimalMin(value = "0", message = "discountPercent must be at least 0")
    @DecimalMax(value = "100", message = "discountPercent must be at most 100")
    private BigDecimal discountPercent = BigDecimal.ZERO;

    private BigDecimal discountAmount = BigDecimal.ZERO;

    @DecimalMin(value = "0", message = "taxPercent must be at least 0")
    @DecimalMax(value = "100", message = "taxPercent must be at most 100")
    private BigDecimal taxPercent = BigDecimal.ZERO;

    private BigDecimal taxAmount = BigDecimal.ZERO;

    private String notes;
}
