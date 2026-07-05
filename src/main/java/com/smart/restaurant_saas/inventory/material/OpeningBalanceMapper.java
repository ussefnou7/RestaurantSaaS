package com.smart.restaurant_saas.inventory.material;

import org.springframework.stereotype.Component;
import com.smart.restaurant_saas.inventory.core.InventoryTransaction;
import com.smart.restaurant_saas.inventory.material.dto.OpeningBalanceResponse;

@Component
public class OpeningBalanceMapper {

    public OpeningBalanceResponse toResponse(InventoryTransaction tx, boolean idempotentHit) {
        return OpeningBalanceResponse.builder()
            .transactionId(tx.getId())
            .warehouseId(tx.getWarehouse().getId())
            .warehouseName(tx.getWarehouse().getName())
            .materialId(tx.getMaterial().getId())
            .materialName(tx.getMaterial().getName())
            .stockQuantity(tx.getStockQuantity())
            .stockUomCode(tx.getStockUom().getCode())
            .stockUomSymbol(tx.getStockUom().getSymbol())
            .stockUnitCost(tx.getUnitCost())
            .totalCost(tx.getTotalCost())
            .enteredQuantity(tx.getEnteredQuantity())
            .enteredUomCode(tx.getEnteredUom().getCode())
            .transactionDate(tx.getTransactionDate())
            .idempotentHit(idempotentHit)
            .build();
    }
}
