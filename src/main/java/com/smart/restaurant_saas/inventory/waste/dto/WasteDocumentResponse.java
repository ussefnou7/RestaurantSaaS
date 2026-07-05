package com.smart.restaurant_saas.inventory.waste.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;
import com.smart.restaurant_saas.inventory.core.enums.DocumentStatus;
import com.smart.restaurant_saas.inventory.core.enums.WasteReasonCode;

@Getter
@Builder
public class WasteDocumentResponse {

    private final Long id;
    private final Long warehouseId;
    private final String warehouseName;
    private final String code;
    private final LocalDate wasteDate;
    private final WasteReasonCode reasonCode;
    private final DocumentStatus status;
    private final String notes;
    private final Boolean postedToInventory;
    private final LocalDateTime completedAt;
    private final LocalDateTime unCompletedAt;
    private final Long unCompletedBy;
    private final LocalDateTime postedAt;
    private final LocalDateTime cancelledAt;
    private final String cancelReason;
    private final List<WasteLineResponse> lines;
    /** Advisory shortfalls computed once at COMPLETE and stored. Empty when not yet completed or no shortfalls. */
    private final List<MaterialShortfallResponse> stockWarnings;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
