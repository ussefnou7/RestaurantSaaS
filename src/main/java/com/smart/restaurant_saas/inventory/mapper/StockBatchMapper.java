package com.smart.restaurant_saas.inventory.mapper;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;
import com.smart.restaurant_saas.inventory.batch.StockBatch;
import com.smart.restaurant_saas.inventory.batch.dto.StockBatchResponse;

@Component
public class StockBatchMapper {

    private static final String SOURCE_PURCHASE = "PURCHASE";
    private static final String SOURCE_OTHER = "OTHER";

    /**
     * @param uomSymbol the parent balance's display UOM symbol, resolved once by the caller
     *                  (all batches of a balance share it) to avoid a per-batch UOM load.
     */
    public StockBatchResponse toResponse(StockBatch batch, String uomSymbol, LocalDate today) {
        int ageDays = Math.toIntExact(
            ChronoUnit.DAYS.between(batch.getWarehouseEntryDate(), today));
        boolean expiryTracked = batch.getStockBalance().getMaterial().isExpiryTracked();
        Integer daysRemaining;
        if (expiryTracked) {
            daysRemaining = batch.getExpiryDate() == null
                ? null
                : Math.toIntExact(ChronoUnit.DAYS.between(today, batch.getExpiryDate()));
        } else {
            int maxAgeDays = batch.getStockBalance().getMaxAgeDays();
            daysRemaining = maxAgeDays == 0 ? null : maxAgeDays - ageDays;
        }

        return StockBatchResponse.builder()
            .id(batch.getId())
            .originalQuantity(batch.getOriginalQuantity())
            .remainingQuantity(batch.getRemainingQuantity())
            .unitCost(batch.getUnitCost())
            .movementDate(batch.getMovementDate())
            .warehouseEntryDate(batch.getWarehouseEntryDate())
            .expiryDate(batch.getExpiryDate())
            .ageDays(ageDays)
            .daysRemaining(daysRemaining)
            .status(batch.getStatus())
            .uomSymbol(uomSymbol)
            .sourceInvoiceId(batch.getSourceInvoiceId())
            .sourceType(batch.getSourceInvoiceId() != null ? SOURCE_PURCHASE : SOURCE_OTHER)
            .build();
    }
}
