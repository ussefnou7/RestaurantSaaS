package com.smart.restaurant_saas.inventory.mapper;

import java.math.BigDecimal;
import org.springframework.stereotype.Component;
import com.smart.restaurant_saas.inventory.material.Material;
import com.smart.restaurant_saas.inventory.stock.StockBalance;
import com.smart.restaurant_saas.inventory.stock.dto.StockBalanceResponse;
import com.smart.restaurant_saas.inventory.uom.Uom;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;

@Component
public class StockBalanceMapper {

    public StockBalanceResponse toResponse(StockBalance sb) {
        Warehouse warehouse = sb.getWarehouse();
        Material material = sb.getMaterial();
        Uom uom = sb.getUom();

        BigDecimal quantity = sb.getQuantity();
        BigDecimal averageCost = sb.getAverageCost();
        BigDecimal minimumQuantity = sb.getMinimumQuantity();

        return StockBalanceResponse.builder()
            .id(sb.getId())
            .warehouseId(warehouse != null ? warehouse.getId() : null)
            .warehouseName(warehouse != null ? warehouse.getName() : null)
            .materialId(material != null ? material.getId() : null)
            .materialCode(material != null ? material.getCode() : null)
            .materialName(material != null ? material.getName() : null)
            .materialNameAr(material != null ? material.getNameAr() : null)
            .quantity(quantity)
            .openingBalance(sb.getOpeningQuantity())
            .uomId(uom != null ? uom.getId() : null)
            .uomSymbol(uom != null ? uom.getSymbol() : null)
            .averageCost(averageCost)
            .totalValue(quantity.multiply(averageCost))
            .minimumQuantity(minimumQuantity)
            .maximumQuantity(sb.getMaximumQuantity())
            .isBelowMinimum(quantity.compareTo(minimumQuantity) < 0)
            .lastPurchasePrice(sb.getLastPurchasePrice())
            .lastPurchaseDate(sb.getLastPurchaseDate())
            .build();
    }
}
