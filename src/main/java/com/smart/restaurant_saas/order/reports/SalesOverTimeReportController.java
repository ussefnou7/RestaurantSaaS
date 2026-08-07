package com.smart.restaurant_saas.order.reports;

import com.smart.restaurant_saas.order.core.enums.OrderType;
import com.smart.restaurant_saas.order.reports.dto.SalesByHourRow;
import com.smart.restaurant_saas.order.reports.dto.SalesOverTimeRow;
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
public class SalesOverTimeReportController {

    private final SalesOverTimeReportService salesOverTimeReportService;

    private static final String SHARED_FILTERS =
        "dateFrom/dateTo are required calendar days, both inclusive; an inverted or incomplete "
        + "range is rejected rather than returning an empty report. Optional filters: branchId, "
        + "cashierUserId (the order's created_by — orders carry no separate cashier column), "
        + "orderType. Only COMPLETE orders are counted; CANCELLED ones are excluded, and a database "
        + "constraint guarantees no COMPLETE order can also be cancelled. Money is reported in "
        + "components and never blended: tax is collected for the state and is NOT revenue. Note "
        + "that subtotal + taxAmount may differ slightly from totalAmount — the components are "
        + "stored at scale 6 and totalAmount is the rounded scale-2 sum written at order time. "
        + "totalAmount is authoritative and is what reconciles against the payment-method report. "
        + "Decimals are scale-6 strings.";

    @GetMapping("/sales-over-time")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('REPORTS_VIEW_SALES')")
    @Operation(
        summary = "Sales over time (daily)",
        description = "One row per calendar day with at least one COMPLETE order: order count, "
                    + "subtotal, tax, total, and average order value (totalAmount / orderCount). "
                    + "Sorted CHRONOLOGICALLY ASCENDING, not by magnitude — it is a time series and "
                    + "the shape over time is the finding. Days with no sales are OMITTED rather "
                    + "than returned as zero rows; a chart needing a continuous axis can fill gaps "
                    + "from the requested range. Not paginated. " + SHARED_FILTERS
    )
    public List<SalesOverTimeRow> salesOverTime(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            // Optional binding, enforced in SalesReportDateRange: a missing required param surfaces
            // as an unhandled 500 (O26), so the range is validated in the service instead.
            @Parameter(required = true)
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @Parameter(required = true)
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long cashierUserId,
            @RequestParam(required = false) OrderType orderType) {
        return salesOverTimeReportService.salesOverTime(
            tenantId, dateFrom, dateTo, branchId, cashierUserId, orderType);
    }

    @GetMapping("/sales-by-hour")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('REPORTS_VIEW_SALES')")
    @Operation(
        summary = "Sales over time (hourly)",
        description = "The same series bucketed by hour — the staffing and shift-planning view. One "
                    + "row per (day, hour) with at least one COMPLETE order, sorted ascending by "
                    + "day then hour. A separate endpoint rather than a granularity parameter: the "
                    + "grouping of a report is fixed and is never a filter. KNOWN LIMITATION — "
                    + "buckets are CALENDAR hours, so an order at 02:00 belongs to that calendar "
                    + "date; a venue trading past midnight sees one session split across two dates. "
                    + "Hours with no sales are omitted. " + SHARED_FILTERS
    )
    public List<SalesByHourRow> salesByHour(
            @RequestHeader("X-Tenant-Id") Long tenantId,
            @Parameter(required = true)
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @Parameter(required = true)
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) Long branchId,
            @RequestParam(required = false) Long cashierUserId,
            @RequestParam(required = false) OrderType orderType) {
        return salesOverTimeReportService.salesByHour(
            tenantId, dateFrom, dateTo, branchId, cashierUserId, orderType);
    }
}
