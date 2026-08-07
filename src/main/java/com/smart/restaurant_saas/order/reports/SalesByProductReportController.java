package com.smart.restaurant_saas.order.reports;

import com.smart.restaurant_saas.order.core.enums.OrderType;
import com.smart.restaurant_saas.order.reports.dto.SalesByProductRow;
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
@RequestMapping("/api/orders/reports")
@RequiredArgsConstructor
@Tag(name = "Order - Reports", description = "Sales reporting (over time, by product, by payment method)")
public class SalesByProductReportController {

    private final SalesByProductReportService salesByProductReportService;

    @GetMapping("/sales-by-product")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('REPORTS_VIEW_SALES')")
    @Operation(
        summary = "Sales by product",
        description = "One row per product sold on a COMPLETE order in the window: quantity sold, "
                    + "revenue (SUM of line totals), and share of revenue within the filtered "
                    + "scope. Sorted by revenue descending. "
                    + "IMPORTANT — this report is PRE-TAX and cannot be otherwise: tax is stored on "
                    + "the order, not the line, so attributing it across products would require "
                    + "inventing an apportionment rule. Do NOT compare this revenue column to the "
                    + "sales-over-time report's totalAmount; they differ by exactly the tax. It "
                    + "should agree with that report's SUBTOTAL, which is the same sum of line "
                    + "totals viewed by day instead of by product. "
                    + "revenueSharePercent is computed against the summed line totals inside the "
                    + "same filters, so the column adds to 100. "
                    + "There is no product code and no Arabic product name because the product "
                    + "table has neither column; they are omitted rather than returned as nulls. "
                    + "The product name shown is the current one — renaming retro-labels history — "
                    + "and a product that has ever sold cannot be deleted, so rows never vanish. "
                    + "dateFrom/dateTo are required calendar days, both inclusive; an inverted range "
                    + "is rejected. Optional filters: branchId, cashierUserId, orderType. Not "
                    + "paginated. Decimals are scale-6 strings."
    )
    public List<SalesByProductRow> salesByProduct(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            // Optional binding, enforced in SalesReportDateRange — see SalesOverTimeReportController.
            @Parameter(required = true)
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @Parameter(required = true)
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long cashierUserId,
            @RequestParam(required = false) OrderType orderType) {
        return salesByProductReportService.salesByProduct(
            tenantId, dateFrom, dateTo, branchId, cashierUserId, orderType);
    }
}
