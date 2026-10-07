package com.smart.restaurant_saas.dashboard;

import com.smart.restaurant_saas.dashboard.alert.AlertService;
import com.smart.restaurant_saas.dashboard.alert.dto.DashboardAlertsResponse;
import com.smart.restaurant_saas.dashboard.dto.DashboardSummaryResponse;
import com.smart.restaurant_saas.tenant.CurrentTenantId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The owner dashboard.
 *
 * <p><b>Two endpoints, because the two halves answer questions with different time scopes.</b> The
 * summary is about a range the owner picks; the alerts are about now. A PARTIAL document is open
 * or it is not, and switching the page from this month to this year must not change that — folding
 * the alerts into the summary would re-evaluate seventeen conditions on every range switch and
 * return the same answer each time.
 *
 * <p>{@code DASHBOARD_VIEW} opens the page and grants nothing: each block and each alert is gated
 * on the permission of the module it reads from. The dashboard is the one screen that reaches
 * across every module, which makes it the one screen that can become a way around the permissions
 * on the screens that own the data.
 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Owner dashboard — period summary and current alerts")
public class DashboardController {

    private final DashboardSummaryService dashboardSummaryService;
    private final AlertService alertService;

    @GetMapping("/summary")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('DASHBOARD_VIEW')")
    @Operation(
        summary = "Dashboard summary for a date range",
        description = "Pre-aggregated KPIs, sales trend, per-branch breakdown, order-type and "
                    + "payment mix, cancellations by stage, cost coverage and the consumption "
                    + "pipeline. ONE call rather than ten against the report endpoints: reports "
                    + "return rows with no totals, their visibility is granted per user per "
                    + "report, and they are free to change their columns. Every metric here is "
                    + "nonetheless computed by the SAME repository method the matching report "
                    + "uses, so the two can never disagree. "
                    + "EVERY BLOCK CARRIES A STATUS — OK, HIDDEN_NO_PERMISSION or "
                    + "INSUFFICIENT_DATA — and an empty block is not a zero: a caller without the "
                    + "source module's permission gets HIDDEN_NO_PERMISSION rather than blanks. "
                    + "The trend's bucket (hour / day / month) is DERIVED from the range length "
                    + "and is not a parameter, so a year cannot be requested in hours. "
                    + "NO COMPARISON IS RETURNED: no previous-period delta, no baseline, no "
                    + "trend-vs-average. Day boundaries are tenant-local. Decimals are scale-6 "
                    + "strings; percentages are scale-2. from/to are required calendar days, both "
                    + "inclusive, and an inverted range is rejected rather than returning empty — "
                    + "'no rows' reads as 'a quiet month', which is the wrong conclusion to hand "
                    + "someone silently. branchId is a narrowing request, never a grant."
    )
    public DashboardSummaryResponse summary(
            @CurrentTenantId Long tenantId,
            // Optional binding, enforced in DashboardDateRange: a missing required param
            // surfaces as an unhandled 500, so the range is validated in the service instead.
            @Parameter(required = true)
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(required = true)
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long branchId) {
        return dashboardSummaryService.summary(tenantId, from, to, branchId);
    }

    @GetMapping("/alerts")
    @PreAuthorize("@securityService.isSysAdmin() or @securityService.hasPermission('DASHBOARD_VIEW')")
    @Operation(
        summary = "Current alerts (the attention strip)",
        description = "Every condition that holds right now, grouped by type — one group per "
                    + "condition carrying the count, total value and oldest age, with one row per "
                    + "branch or warehouse underneath. Ordered by severity, then money, then "
                    + "count. "
                    + "NO DATE RANGE, deliberately: alerts describe NOW, so switching the "
                    + "dashboard's range does not change them, and they have their own endpoint "
                    + "for that reason. "
                    + "Nothing is stored — every condition is derived on read from documents, "
                    + "balances, batches, shifts and orders, so there is no read/unread state, "
                    + "nothing to dismiss, and a condition disappears the moment it is fixed. "
                    + "AGE IS PRESENT ONLY WHERE THE SOURCE RECORDS ONE: a stuck document, an "
                    + "unposted invoice and an open shift carry a date; 'below minimum' does not, "
                    + "because a balance row never recorded when the quantity crossed the line. "
                    + "A null age means unknown, not recent. Likewise a null value means the "
                    + "condition carries no money, never zero. "
                    + "Values marked valueIsEstimate are derived from an assumption — a recipe "
                    + "cost, a consumption rate — and must be labelled as estimates. "
                    + "WITHHELD lists codes not evaluated because the caller lacks the source "
                    + "module's permission; an empty strip means 'nothing is wrong' only when "
                    + "withheld is also empty. "
                    + "link is a semantic screen name, not a URL — the frontend owns the routes."
    )
    public DashboardAlertsResponse alerts(
            @CurrentTenantId Long tenantId,
            @RequestParam(required = false) Long branchId) {
        return alertService.alerts(tenantId, branchId);
    }
}
