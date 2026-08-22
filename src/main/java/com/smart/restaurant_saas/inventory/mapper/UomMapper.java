package com.smart.restaurant_saas.inventory.mapper;

import org.springframework.stereotype.Component;
import com.smart.restaurant_saas.inventory.uom.Uom;
import com.smart.restaurant_saas.inventory.uom.dto.UomLookupItemResponse;
import com.smart.restaurant_saas.inventory.uom.dto.UomResponse;

@Component
public class UomMapper {

    public UomResponse toResponse(Uom uom) {
        Uom base = uom.getBaseUom();
        // Null for roots. Touching the lazy proxy is safe here: every caller is
        // inside a read-only transaction, same as getBaseUom() above.
        Uom enteredAgainst = uom.getEnteredAgainstUom();
        return UomResponse.builder()
            .id(uom.getId())
            .code(uom.getCode())
            .name(uom.getName())
            .nameAr(uom.getNameAr())
            .symbol(uom.getSymbol())
            .symbolAr(uom.getSymbolAr())
            .type(uom.getType())
            .baseUomId(base != null ? base.getId() : null)
            .baseUomName(base != null ? base.getName() : null)
            .factorToBase(uom.getFactorToBase())
            .enteredFactor(uom.getEnteredFactor())
            .enteredAgainstUomId(enteredAgainst != null ? enteredAgainst.getId() : null)
            .enteredAgainstUomSymbol(enteredAgainst != null ? enteredAgainst.getSymbol() : null)
            .enteredAgainstUomActive(enteredAgainst != null ? enteredAgainst.getActive() : null)
            .active(uom.getActive())
            .tenantId(uom.getTenantId())
            .isGlobal(uom.getTenantId() == null)
            .build();
    }

    public UomLookupItemResponse toLookupItem(Uom uom) {
        Uom base = uom.getBaseUom();
        return UomLookupItemResponse.builder()
            .id(uom.getId())
            .code(uom.getCode())
            .symbol(uom.getSymbol())
            .symbolAr(uom.getSymbolAr())
            .name(uom.getName())
            .nameAr(uom.getNameAr())
            .factorToBase(uom.getFactorToBase())
            .baseUomId(base != null ? base.getId() : null)
            .type(uom.getType())
            .active(uom.getActive())
            .build();
    }
}
