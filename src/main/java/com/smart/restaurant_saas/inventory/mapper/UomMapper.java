package com.smart.restaurant_saas.inventory.mapper;

import org.springframework.stereotype.Component;
import com.smart.restaurant_saas.inventory.uom.Uom;
import com.smart.restaurant_saas.inventory.uom.dto.UomResponse;

@Component
public class UomMapper {

    public UomResponse toResponse(Uom uom) {
        Uom base = uom.getBaseUom();
        return UomResponse.builder()
            .id(uom.getId())
            .code(uom.getCode())
            .name(uom.getName())
            .nameAr(uom.getNameAr())
            .symbol(uom.getSymbol())
            .type(uom.getType())
            .baseUomId(base != null ? base.getId() : null)
            .baseUomName(base != null ? base.getName() : null)
            .factorToBase(uom.getFactorToBase())
            .active(uom.getActive())
            .tenantId(uom.getTenantId())
            .isGlobal(uom.getTenantId() == null)
            .build();
    }
}
