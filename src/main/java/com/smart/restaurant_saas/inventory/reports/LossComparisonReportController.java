package com.smart.restaurant_saas.inventory.reports;

import com.smart.restaurant_saas.inventory.reports.dto.LossComparisonRow;
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
public class LossComparisonReportController {

    private final LossComparisonReportService lossComparisonReportService;

    @GetMapping("/loss-comparison")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('INVENTORY_REPORTS_VIEW')")
    @Operation(
        summary = "Waste vs shrinkage comparison report",
        description = "One row per material, putting deliberate waste beside unexplained "
                    + "physical-count variance for the same window. The ratio is the diagnosis: high "
                    + "waste with near-zero shrinkage is a storage/purchasing problem; high shrinkage "
                    + "with near-zero waste is a control problem. "
                    + "WARNING — the two sides use DIFFERENT SIGN CONVENTIONS in the same row. "
                    + "wasteQuantity/wasteValue are positive magnitudes (waste is always an outflow, "
                    + "so a sign would carry no information). shrinkageQuantity/shrinkageValue are "
                    + "SIGNED: negative is a shortage, positive is a surplus. Do not apply one "
                    + "formatter to all four — rendering shrinkageValue as a magnitude turns a "
                    + "surplus into a loss. totalValue is the combined loss, loss-positive "
                    + "(wasteValue - shrinkageValue), so a surplus reduces it. "
                    + "dateFrom/dateTo are required calendar days, both inclusive; an inverted range "
                    + "is rejected. Optional filters: warehouseId, categoryId. No reason filter — "
                    + "reasons belong to the waste report. Materials with no waste and no shrinkage "
                    + "are INCLUDED and sorted last; rows with activity come first, ordered by "
                    + "absolute totalValue descending. Not paginated. Quantities are in the "
                    + "material's display UOM with uomId/uomSymbol, and go null (with both UOM "
                    + "fields null) when the material has no conversion path — the values stay exact "
                    + "either way. Decimals are scale-6 strings."
    )
    public List<LossComparisonRow> lossComparison(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            // Optional binding, enforced in ReportDateRange — see ShrinkageReportController for why.
            @Parameter(required = true)
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @Parameter(required = true)
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long categoryId) {
        return lossComparisonReportService.lossComparison(
            tenantId, dateFrom, dateTo, warehouseId, categoryId);
    }
}
