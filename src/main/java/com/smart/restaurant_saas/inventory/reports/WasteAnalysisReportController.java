package com.smart.restaurant_saas.inventory.reports;

import com.smart.restaurant_saas.inventory.reports.dto.WasteAnalysisRow;
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
public class WasteAnalysisReportController {

    private final WasteAnalysisReportService wasteAnalysisReportService;

    @GetMapping("/waste-analysis")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_REPORTS_VIEW')")
    @Operation(
        summary = "Waste analysis report",
        description = "One row per (material, reason), netting the WASTE movements posted in the "
                    + "date range — deliberate write-offs, with the recorded cause that makes them "
                    + "actionable. A material wasted for two reasons yields two rows. Same contract "
                    + "as the shrinkage report otherwise: dateFrom/dateTo required and inclusive, an "
                    + "inverted range rejected, optional warehouseId/categoryId/negativesOnly AND-ed, "
                    + "signs preserved, ordered by absolute netValue descending, not paginated, "
                    + "netQuantity in display UOM with uomId/uomSymbol and null when unconvertible. "
                    + "Additional optional filter: reasonCode (a WasteReasonCode name, or "
                    + "UNSPECIFIED); absent means all reasons. netQuantity/netValue are scale-6 "
                    + "decimal strings."
    )
    public List<WasteAnalysisRow> wasteAnalysis(
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
            @RequestParam(required = false) String reasonCode,
            @RequestParam(required = false, defaultValue = "false") boolean negativesOnly) {
        return wasteAnalysisReportService.wasteAnalysis(
            tenantId, dateFrom, dateTo, warehouseId, categoryId, reasonCode, negativesOnly);
    }
}
