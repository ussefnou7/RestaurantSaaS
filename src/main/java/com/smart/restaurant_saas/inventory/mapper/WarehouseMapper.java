package com.smart.restaurant_saas.inventory.mapper;

import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.inventory.dto.response.WarehouseResponse;
import com.smart.restaurant_saas.inventory.entity.Warehouse;
import org.springframework.stereotype.Component;

@Component
public class WarehouseMapper {

    public WarehouseResponse toResponse(Warehouse warehouse) {
        Branch branch = warehouse.getBranch();
        return new WarehouseResponse(
                warehouse.getId(),
                warehouse.getTenantId(),
                branch == null ? null : branch.getId(),
                branch == null ? null : branch.getCode(),
                branch == null ? null : branch.getName(),
                warehouse.getCode(),
                warehouse.getName(),
                warehouse.getNameAr(),
                warehouse.getType(),
                warehouse.getActive(),
                warehouse.getNotes(),
                warehouse.getCreatedAt(),
                warehouse.getUpdatedAt()
        );
    }
}
