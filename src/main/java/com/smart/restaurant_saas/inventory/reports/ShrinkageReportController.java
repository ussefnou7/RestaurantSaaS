package com.smart.restaurant_saas.inventory.reports;

import com.smart.restaurant_saas.inventory.reports.dto.ShrinkageRow;
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
public class ShrinkageReportController {

    private final ShrinkageReportService shrinkageReportService;

    @GetMapping("/shrinkage")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_REPORTS_VIEW')")
    @Operation(
        summary = "Shrinkage report",
        description = "One row per material, netting the COUNT_ADJUSTMENT movements that physical "
                    + "counts wrote in the date range — the stock that went missing with no recorded "
                    + "cause. dateFrom/dateTo are required calendar days, both inclusive; an inverted "
                    + "range is rejected rather than silently returning empty. Optional filters: "
                    + "warehouseId (all warehouses when absent), categoryId, negativesOnly — AND-ed "
                    + "when supplied. Signs are preserved: a shortage is negative, a surplus "
                    + "positive, and a material with both nets to the difference. Rows are ordered "
                    + "by absolute netValue descending and are not paginated. netQuantity is in the "
                    + "material's display UOM and carries uomId/uomSymbol; it is null (with both UOM "
                    + "fields null) when the material has no conversion path, in which case netValue "
                    + "is still exact. netQuantity/netValue are scale-6 decimal strings."
    )
    public List<ShrinkageRow> shrinkage(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            // Bound as optional and enforced in ReportDateRange, not by required = true: Spring's
            // MissingServletRequestParameterException is unhandled by GlobalExceptionHandler and
            // falls through to the catch-all as a 500. Validating in the service keeps every
            // date-range failure — missing or inverted — a structured VALIDATION_FAILED 400.
            // @Parameter keeps the OpenAPI contract honest about them being required.
            @Parameter(required = true)
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @Parameter(required = true)
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false, defaultValue = "false") boolean negativesOnly) {
        return shrinkageReportService.shrinkage(
            tenantId, dateFrom, dateTo, warehouseId, categoryId, negativesOnly);
    }
}
