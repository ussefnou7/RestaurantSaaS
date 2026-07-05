package com.smart.restaurant_saas.inventory.mapper;

import org.springframework.stereotype.Component;
import com.smart.restaurant_saas.inventory.purchase.Supplier;
import com.smart.restaurant_saas.inventory.purchase.dto.SupplierResponse;

@Component
public class SupplierMapper {

    public SupplierResponse toResponse(Supplier s) {
        return SupplierResponse.builder()
            .id(s.getId())
            .code(s.getCode())
            .name(s.getName())
            .nameAr(s.getNameAr())
            .phone(s.getPhone())
            .email(s.getEmail())
            .address(s.getAddress())
            .taxNumber(s.getTaxNumber())
            .active(s.getActive())
            .notes(s.getNotes())
            .createdAt(s.getCreatedAt())
            .updatedAt(s.getUpdatedAt())
            .build();
    }
}
