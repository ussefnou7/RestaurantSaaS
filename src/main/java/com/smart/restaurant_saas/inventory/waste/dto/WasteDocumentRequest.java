package com.smart.restaurant_saas.inventory.waste.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import com.smart.restaurant_saas.inventory.core.enums.WasteReasonCode;

/**
 * Header of a waste document. Lines are managed separately via the dedicated /{id}/lines
 * endpoints. {@code warehouseId} is only honored on create — it is fixed for the life of the
 * document and ignored on header update.
 */
@Getter
@Setter
public class WasteDocumentRequest {

    @NotNull(message = "warehouseId is required")
    private Long warehouseId;

    @NotNull(message = "wasteDate is required")
    private LocalDate wasteDate;

    @NotNull(message = "reasonCode is required")
    private WasteReasonCode reasonCode;

    private String notes;
}
