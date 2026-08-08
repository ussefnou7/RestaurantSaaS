package com.smart.restaurant_saas.table.dto;

import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.inventory.core.enums.TableShape;
import com.smart.restaurant_saas.table.RestaurantTable;
import com.smart.restaurant_saas.table.section.TableSection;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TableResponse(
        Long id,
        Long branchId,
        String branchName,
        String name,
        Long sectionId,
        String sectionName,
        Integer capacity,
        TableShape shape,
        BigDecimal posX,
        BigDecimal posY,
        Integer rotation,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static TableResponse from(RestaurantTable table) {
        Branch branch = table.getBranch();
        TableSection section = table.getSection();
        return new TableResponse(
                table.getId(),
                branch == null ? null : branch.getId(),
                branch == null ? null : branch.getName(),
                table.getName(),
                section == null ? null : section.getId(),
                section == null ? null : section.getName(),
                table.getCapacity(),
                table.getShape(),
                table.getPosX(),
                table.getPosY(),
                table.getRotation(),
                table.getActive(),
                table.getCreatedAt(),
                table.getUpdatedAt()
        );
    }
}
