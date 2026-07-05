package com.smart.restaurant_saas.inventory.purchase.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SupplierResponse {

    private final Long id;
    private final String code;
    private final String name;
    private final String nameAr;
    private final String phone;
    private final String email;
    private final String address;
    private final String taxNumber;
    private final Boolean active;
    private final String notes;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
