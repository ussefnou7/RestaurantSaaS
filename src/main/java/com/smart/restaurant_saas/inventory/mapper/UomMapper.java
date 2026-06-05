package com.smart.restaurant_saas.inventory.mapper;

import com.smart.restaurant_saas.inventory.dto.response.UomResponse;
import com.smart.restaurant_saas.inventory.entity.Uom;
import org.springframework.stereotype.Component;

@Component
public class UomMapper {

    public UomResponse toResponse(Uom uom) {
        return new UomResponse(
                uom.getId(),
                uom.getCode(),
                uom.getName(),
                uom.getNameAr(),
                uom.getSymbol(),
                uom.getType(),
                uom.getBaseCode(),
                uom.getFactorToBase(),
                uom.getActive(),
                uom.getSortOrder(),
                uom.getCreatedAt(),
                uom.getUpdatedAt()
        );
    }
}
