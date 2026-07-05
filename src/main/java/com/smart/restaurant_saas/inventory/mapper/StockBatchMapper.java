package com.smart.restaurant_saas.inventory.mapper;

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
    public StockBatchResponse toResponse(StockBatch batch, String uomSymbol) {
        return StockBatchResponse.builder()
            .id(batch.getId())
            .originalQuantity(batch.getOriginalQuantity())
            .remainingQuantity(batch.getRemainingQuantity())
            .unitCost(batch.getUnitCost())
            .movementDate(batch.getMovementDate())
            .status(batch.getStatus())
            .uomSymbol(uomSymbol)
            .sourceInvoiceId(batch.getSourceInvoiceId())
            .sourceType(batch.getSourceInvoiceId() != null ? SOURCE_PURCHASE : SOURCE_OTHER)
            .build();
    }
}
