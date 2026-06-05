package com.smart.restaurant_saas.inventory.mapper;

import com.smart.restaurant_saas.inventory.dto.response.SupplierResponse;
import com.smart.restaurant_saas.inventory.entity.Supplier;
import org.springframework.stereotype.Component;

@Component
public class SupplierMapper {

    public SupplierResponse toResponse(Supplier supplier) {
        return new SupplierResponse(
                supplier.getId(),
                supplier.getTenantId(),
                supplier.getCode(),
                supplier.getName(),
                supplier.getNameAr(),
                supplier.getPhone(),
                supplier.getEmail(),
                supplier.getAddress(),
                supplier.getTaxNumber(),
                supplier.getActive(),
                supplier.getNotes(),
                supplier.getCreatedAt(),
                supplier.getUpdatedAt()
        );
    }
}
