package com.smart.restaurant_saas.branch.table.dto;

import com.smart.restaurant_saas.branch.table.RestaurantTable;
import java.time.LocalDateTime;

public record TableResponse(
        Long id,
        Long branchId,
        String branchName,
        String tableNo,
        Integer capacity,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static TableResponse from(RestaurantTable table) {
        return new TableResponse(
                table.getId(),
                table.getBranch().getId(),
                table.getBranch().getName(),
                table.getTableNo(),
                table.getCapacity(),
                table.getActive(),
                table.getCreatedAt(),
                table.getUpdatedAt()
        );
    }
}
