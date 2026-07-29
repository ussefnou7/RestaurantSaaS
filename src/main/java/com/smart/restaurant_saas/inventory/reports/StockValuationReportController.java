package com.smart.restaurant_saas.inventory.reports;

import com.smart.restaurant_saas.inventory.reports.dto.StockValuationRow;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory/reports")
@RequiredArgsConstructor
@Tag(name = "Inventory - Reports", description = "Inventory reporting (stock valuation)")
public class StockValuationReportController {

    private final StockValuationReportService stockValuationReportService;

    @GetMapping("/stock-valuation")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_REPORTS_VIEW')")
    @Operation(
        summary = "Stock valuation report",
        description = "One row per (warehouse, material) stock balance with quantity, moving "
                    + "average cost, and total value (quantity * averageCost). Optional filters: "
                    + "branchId (the warehouse's branch), warehouseId, categoryId — AND-ed when "
                    + "supplied. Rows are ordered by warehouse name then material name and are not "
                    + "paginated; quantity/averageCost/totalValue are scale-6 decimal strings."
    )
    public List<StockValuationRow> stockValuation(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long categoryId) {
        return stockValuationReportService.stockValuation(tenantId, branchId, warehouseId, categoryId);
    }
}
