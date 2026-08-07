package com.smart.restaurant_saas.order.reports;

import com.smart.restaurant_saas.order.core.enums.OrderType;
import com.smart.restaurant_saas.order.reports.dto.SalesByPaymentMethodRow;
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
public class SalesByPaymentMethodReportController {

    private final SalesByPaymentMethodReportService salesByPaymentMethodReportService;

    @GetMapping("/sales-by-payment-method")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('REPORTS_VIEW_SALES')")
    @Operation(
        summary = "Sales by payment method",
        description = "One row per payment method used by a COMPLETE order in the window: order "
                    + "count, subtotal, tax, total, and share of total within the filtered scope. "
                    + "Sorted by totalAmount descending. Built for reconciling delivery platform "
                    + "payouts and card processor fees. "
                    + "This report aggregates exactly the same orders as sales-over-time under the "
                    + "same filters — only the grouping differs — so their totalAmount sums are "
                    + "IDENTICAL, and a test pins that. A method with no value reports as "
                    + "UNSPECIFIED and is never dropped, because dropping it would silently break "
                    + "that reconciliation. "
                    + "Money is reported in components; tax is not revenue. subtotal + taxAmount may "
                    + "differ slightly from totalAmount by write-time rounding — totalAmount is the "
                    + "authoritative figure. "
                    + "dateFrom/dateTo are required calendar days, both inclusive; an inverted range "
                    + "is rejected. Optional filters: branchId, cashierUserId, orderType. Not "
                    + "paginated. Decimals are scale-6 strings."
    )
    public List<SalesByPaymentMethodRow> salesByPaymentMethod(
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
        return salesByPaymentMethodReportService.salesByPaymentMethod(
            tenantId, dateFrom, dateTo, branchId, cashierUserId, orderType);
    }
}
