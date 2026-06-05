package com.smart.restaurant_saas.inventory.dto.response;

import java.util.List;

public record InventorySeedSummaryResponse(
        int createdCount,
        int updatedCount,
        int skippedCount,
        List<String> messages
) {
}
