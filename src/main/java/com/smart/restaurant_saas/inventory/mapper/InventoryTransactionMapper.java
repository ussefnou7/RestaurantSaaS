package com.smart.restaurant_saas.inventory.mapper;

import com.smart.restaurant_saas.inventory.dto.response.InventoryTransactionResponse;
import com.smart.restaurant_saas.inventory.entity.InventoryTransaction;
import com.smart.restaurant_saas.inventory.entity.Material;
import com.smart.restaurant_saas.inventory.entity.MaterialCategory;
import com.smart.restaurant_saas.inventory.entity.Uom;
import com.smart.restaurant_saas.inventory.entity.Warehouse;
import org.springframework.stereotype.Component;

@Component
public class InventoryTransactionMapper {

    public InventoryTransactionResponse toResponse(InventoryTransaction transaction) {
        Warehouse warehouse = transaction.getWarehouse();
        Material material = transaction.getMaterial();
        MaterialCategory category = material.getCategory();
        Uom enteredUom = transaction.getEnteredUom();
        Uom stockUom = transaction.getStockUom();
        return new InventoryTransactionResponse(
                transaction.getId(),
                transaction.getTenantId(),
                transaction.getTransactionType(),
                transaction.getDirection(),
                warehouse.getId(),
                warehouse.getCode(),
                warehouse.getName(),
                warehouse.getNameAr(),
                material.getId(),
                material.getCode(),
                material.getName(),
                material.getNameAr(),
                category.getId(),
                category.getCode(),
                category.getName(),
                category.getNameAr(),
                transaction.getEnteredQuantity(),
                enteredUom.getId(),
                enteredUom.getCode(),
                enteredUom.getName(),
                enteredUom.getNameAr(),
                enteredUom.getSymbol(),
                transaction.getStockQuantity(),
                stockUom.getId(),
                stockUom.getCode(),
                stockUom.getName(),
                stockUom.getNameAr(),
                stockUom.getSymbol(),
                transaction.getUnitCost(),
                transaction.getTotalCost(),
                transaction.getReferenceType(),
                transaction.getReferenceId(),
                transaction.getTransactionDate(),
                transaction.getNotes(),
                transaction.getCreatedBy(),
                transaction.getCreatedAt()
        );
    }
}
