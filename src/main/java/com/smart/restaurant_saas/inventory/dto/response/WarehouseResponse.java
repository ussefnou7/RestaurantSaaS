package com.smart.restaurant_saas.inventory.dto.response;

import com.smart.restaurant_saas.inventory.enums.WarehouseType;
import java.time.LocalDateTime;

public record WarehouseResponse(
        Long id,
        Long tenantId,
        Long branchId,
        String branchCode,
        String branchName,
        String code,
        String name,
        String nameAr,
        WarehouseType type,
        Boolean active,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
