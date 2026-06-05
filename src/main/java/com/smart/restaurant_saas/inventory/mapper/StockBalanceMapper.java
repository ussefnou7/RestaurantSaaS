package com.smart.restaurant_saas.inventory.mapper;

import com.smart.restaurant_saas.inventory.dto.response.StockBalanceResponse;
import com.smart.restaurant_saas.inventory.entity.Material;
import com.smart.restaurant_saas.inventory.entity.MaterialCategory;
import com.smart.restaurant_saas.inventory.entity.StockBalance;
import com.smart.restaurant_saas.inventory.entity.Uom;
import com.smart.restaurant_saas.inventory.entity.Warehouse;
import com.smart.restaurant_saas.inventory.service.UomConversionService;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockBalanceMapper {

    private final UomConversionService uomConversionService;

    public StockBalanceResponse toResponse(StockBalance balance) {
        Warehouse warehouse = balance.getWarehouse();
        Material material = balance.getMaterial();
        MaterialCategory category = material.getCategory();
        Uom uom = balance.getUom();
        Uom displayUom = material.getDisplayUom();
        BigDecimal quantity = nullToZero(balance.getQuantity());
        BigDecimal averageCost = nullToZero(balance.getAverageCost());
        BigDecimal minimumStockLevel = nullToZero(material.getMinimumStockLevel());
        BigDecimal displayQuantity = uomConversionService.convert(quantity, uom, displayUom);
        return new StockBalanceResponse(
                balance.getId(),
                balance.getTenantId(),
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
                uom.getId(),
                uom.getCode(),
                uom.getName(),
                uom.getNameAr(),
                uom.getSymbol(),
                quantity,
                averageCost,
                quantity.multiply(averageCost),
                displayQuantity,
                displayUom.getId(),
                displayUom.getCode(),
                displayUom.getName(),
                displayUom.getNameAr(),
                displayUom.getSymbol(),
                minimumStockLevel,
                quantity.compareTo(minimumStockLevel) <= 0,
                balance.getUpdatedAt()
        );
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
