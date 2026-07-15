package com.smart.restaurant_saas.inventory.orderconsumption;

public record OrderConsumptionErrorDetail(
    Long materialId,
    String materialName,
    String exceptionClass,
    String message
) {
}
