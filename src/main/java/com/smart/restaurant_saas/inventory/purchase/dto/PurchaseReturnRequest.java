package com.smart.restaurant_saas.inventory.purchase.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import com.smart.restaurant_saas.inventory.core.enums.PurchaseReturnReason;

/**
 * Header of a purchase return. Lines are managed separately via the dedicated
 * /{id}/lines endpoints. {@code originalInvoiceId} is only honored on create — it is
 * fixed for the life of the return and ignored on header update.
 */
@Getter
@Setter
public class PurchaseReturnRequest {

    @NotNull(message = "originalInvoiceId is required")
    private Long originalInvoiceId;

    @NotNull(message = "returnDate is required")
    private LocalDate returnDate;

    @NotNull(message = "reason is required")
    private PurchaseReturnReason reason;

    private String notes;
}
