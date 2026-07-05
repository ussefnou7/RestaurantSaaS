package com.smart.restaurant_saas.inventory.mapper;

import com.smart.restaurant_saas.branch.Branch;
import org.springframework.stereotype.Component;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;
import com.smart.restaurant_saas.inventory.warehouse.dto.WarehouseResponse;

@Component
public class WarehouseMapper {

    public WarehouseResponse toResponse(Warehouse w) {
        Branch branch = w.getBranch();
        return WarehouseResponse.builder()
            .id(w.getId())
            .code(w.getCode())
            .name(w.getName())
            .nameAr(w.getNameAr())
            .type(w.getType())
            .branchId(branch != null ? branch.getId() : null)
            .branchName(branch != null ? branch.getName() : null)
            .active(w.getActive())
            .notes(w.getNotes())
            .createdAt(w.getCreatedAt())
            .updatedAt(w.getUpdatedAt())
            .build();
    }
}
