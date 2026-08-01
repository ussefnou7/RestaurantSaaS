package com.smart.restaurant_saas.inventory.purchase.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BackdatedConsumptionCheckResponse {

    private final Long materialId;
    private final String materialName;
    private final String materialNameAr;
    private final LocalDateTime lastConsumptionDate;
}
