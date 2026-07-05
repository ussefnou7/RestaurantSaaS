package com.smart.restaurant_saas.inventory.warehouse.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;
import com.smart.restaurant_saas.inventory.core.enums.WarehouseType;

@Getter
@Builder
public class WarehouseResponse {

    private final Long id;
    private final String code;
    private final String name;
    private final String nameAr;
    private final WarehouseType type;
    private final Long branchId;
    private final String branchName;
    private final Boolean active;
    private final String notes;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
