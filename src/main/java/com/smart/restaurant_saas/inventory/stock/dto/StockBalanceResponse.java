package com.smart.restaurant_saas.inventory.stock.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class StockBalanceResponse {

    private final Long id;
    private final Long warehouseId;
    private final String warehouseName;
    private final Long materialId;
    private final String materialCode;
    private final String materialName;
    private final String materialNameAr;
    private final BigDecimal quantity;
    private final BigDecimal openingBalance;
    private final Long uomId;
    /**
     * Held back from the D111 phase-3 cut. The Flutter app reads this straight off
     * {@code GET /warehouses/{id}/stocks} and renders it beside every quantity; it parses no
     * {@code uomId} and has no lookup cache, so removing this would silently turn every mobile
     * stock figure into a bare number. Remove only once mobile can resolve ids. See O42.
     */
    private final String uomSymbol;
    private final BigDecimal averageCost;
    private final BigDecimal totalValue;
    private final BigDecimal minimumQuantity;
    private final BigDecimal maximumQuantity;
    private final Boolean isBelowMinimum;
    private final BigDecimal lastPurchasePrice;
    private final LocalDateTime lastPurchaseDate;
}
