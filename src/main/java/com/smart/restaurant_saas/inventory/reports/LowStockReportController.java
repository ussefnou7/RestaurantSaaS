package com.smart.restaurant_saas.inventory.reports;

import com.smart.restaurant_saas.inventory.reports.dto.LowStockRow;
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
@Tag(name = "Inventory - Reports", description = "Inventory reporting (stock valuation, low stock)")
public class LowStockReportController {

    private final LowStockReportService lowStockReportService;

    @GetMapping("/low-stock")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_REPORTS_VIEW')")
    @Operation(
        summary = "Low stock report",
        description = "One row per (warehouse, material) balance that has fallen below its "
                    + "configured minimum, with the shortfall to reorder (minQuantity - quantity). "
                    + "Materials with no minimum configured are never reported. Optional filters: "
                    + "branchId (the warehouse's branch), warehouseId, categoryId — AND-ed when "
                    + "supplied. Ordered by warehouse name then material name, not paginated; "
                    + "quantity/minQuantity/shortfall are scale-6 decimal strings."
    )
    public List<LowStockRow> lowStock(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long categoryId) {
        return lowStockReportService.lowStock(tenantId, branchId, warehouseId, categoryId);
    }
}
