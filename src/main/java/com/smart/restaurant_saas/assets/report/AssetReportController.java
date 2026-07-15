package com.smart.restaurant_saas.assets.report;

import com.smart.restaurant_saas.assets.report.dto.AssetDisposalReportRow;
import com.smart.restaurant_saas.assets.report.dto.AssetSummaryReportResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assets/reports")
@RequiredArgsConstructor
@Tag(name = "Asset Reports", description = "Fixed-asset investment and disposal reporting")
public class AssetReportController {

    private final AssetReportService assetReportService;

    @GetMapping("/summary")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('ASSETS_VIEW')")
    @Operation(summary = "Asset investment summary",
        description = "Returns total original investment (SUM quantity * unitCost) and total current "
            + "value (SUM remainingQuantity * unitCost) across all asset lines of the tenant.")
    public AssetSummaryReportResponse summary(@RequestHeader("X-Tenant-Id") Long tenantId) {
        return assetReportService.summary(tenantId);
    }

    @GetMapping("/disposals")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('ASSETS_VIEW')")
    @Operation(summary = "Disposal report",
        description = "Paginated list of disposals with asset name, line label, quantity disposed, "
            + "reason, date, and disposed value (quantityDisposed * unitCost).")
    public Page<AssetDisposalReportRow> disposals(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @PageableDefault(size = 20, sort = "disposalDate", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return assetReportService.disposals(tenantId, pageable);
    }
}
