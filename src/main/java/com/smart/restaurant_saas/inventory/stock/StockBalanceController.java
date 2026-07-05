package com.smart.restaurant_saas.inventory.stock;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.smart.restaurant_saas.inventory.batch.dto.StockBatchResponse;
import com.smart.restaurant_saas.inventory.core.StockBalanceService;

@RestController
@RequestMapping("/api/inventory/stock-balances")
@RequiredArgsConstructor
@Tag(name = "Inventory - Stock Balance", description = "Stock balances and their batches")
public class StockBalanceController {

    private final StockBalanceService stockBalanceService;

    @GetMapping("/{balanceId}/batches")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_STOCK_VIEW')")
    @Operation(
        summary = "List batches of a stock balance",
        description = "Returns all batches (OPEN and CLOSED) for the balance, oldest first "
                    + "(ascending id = FIFO order), for the expandable sub-row in the stock view. "
                    + "Quantities and unit cost are per the balance's display UOM. No totals are "
                    + "computed server-side — the frontend sums remaining quantities itself."
    )
    public List<StockBatchResponse> getBatches(
            @PathVariable Long balanceId,
            @RequestHeader("X-Tenant-Id") Long tenantId) {
        return stockBalanceService.findBatchesForBalance(balanceId, tenantId);
    }
}
