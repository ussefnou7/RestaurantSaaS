package com.smart.restaurant_saas.inventory.reports;

import com.smart.restaurant_saas.inventory.reports.dto.PurchasePriceDriftRow;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
public class PurchasePriceDriftReportController {

    private final PurchasePriceDriftReportService purchasePriceDriftReportService;

    @GetMapping("/purchase-price-drift")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_REPORTS_VIEW')")
    @Operation(
        summary = "Purchase price drift report",
        description = "One row per material purchased in the window, comparing its FIRST and LAST "
                    + "purchase price — actual prices paid, not averages. Drives menu re-pricing and "
                    + "supplier negotiation. First/last are resolved by batch insertion order (id), "
                    + "not by timestamp, so two purchases on the same day are separated the same way "
                    + "FIFO separates them; the dates shown are the real purchase dates. "
                    + "Only purchase-origin batches count — opening balances, transfers in, and "
                    + "physical-count surpluses (which are valued at the running average, not a "
                    + "price) are excluded, as are purchases whose invoice was later cancelled. "
                    + "changePercent is signed and is NULL when firstPrice is zero — never zero, "
                    + "never infinity; those rows sort last. purchaseCount tells you whether a change "
                    + "is a trend or noise, and makes the single-purchase case (first == last, 0%) "
                    + "self-explanatory. Rows are ordered by absolute changePercent descending — a "
                    + "cheap material up 50% outranks an expensive one up 5% — and are not "
                    + "paginated. NOTE: prices are NOT UOM-converted; batch costs are already stored "
                    + "per the material's display UOM, and uomId/uomSymbol simply state that unit. "
                    + "dateFrom/dateTo are required calendar days, both inclusive; an inverted range "
                    + "is rejected. Optional filters: warehouseId, categoryId, supplierId. "
                    + "Decimals are scale-6 strings."
    )
    public List<PurchasePriceDriftRow> purchasePriceDrift(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            // Optional binding, enforced in ReportDateRange — see ShrinkageReportController for why.
            @Parameter(required = true)
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @Parameter(required = true)
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long supplierId) {
        return purchasePriceDriftReportService.purchasePriceDrift(
            tenantId, dateFrom, dateTo, warehouseId, categoryId, supplierId);
    }
}
