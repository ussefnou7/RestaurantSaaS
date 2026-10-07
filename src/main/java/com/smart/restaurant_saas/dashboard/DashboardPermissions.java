package com.smart.restaurant_saas.dashboard;

/**
 * The permission codes the dashboard checks, as constants.
 *
 * <p>The dashboard is the one screen that reads across every module, so it is also the one screen
 * that can accidentally become a side channel: a block sourced from the shift report must not show
 * cash variance to a user who was denied {@code SHIFTS_VIEW_VARIANCE} on the shift screen itself.
 * Each block and each alert therefore declares the permission of <em>its source</em>, and
 * {@code DASHBOARD_VIEW} only opens the page.
 *
 * <p>Held as constants because {@link com.smart.restaurant_saas.dashboard.alert.AlertCode} and the
 * block enum need them in field initialisers, where a {@code @PreAuthorize} string literal cannot
 * reach. These are deliberately the <em>existing</em> module permissions and not a new dashboard-
 * specific set: a second set would drift from the first, and the drift would show up as the
 * dashboard revealing something the owning screen hides.
 */
public final class DashboardPermissions {

    /** Opens the page. Carries no data access of its own — every block is gated on its source. */
    public static final String DASHBOARD_VIEW = "DASHBOARD_VIEW";

    public static final String REPORTS_VIEW_SALES = "REPORTS_VIEW_SALES";
    public static final String ORDERS_VIEW = "ORDERS_VIEW";
    public static final String SHIFTS_VIEW = "SHIFTS_VIEW";
    public static final String SHIFTS_VIEW_VARIANCE = "SHIFTS_VIEW_VARIANCE";
    public static final String INVENTORY_REPORTS_VIEW = "INVENTORY_REPORTS_VIEW";
    public static final String INVENTORY_STOCK_VIEW = "INVENTORY_STOCK_VIEW";
    public static final String INVENTORY_PURCHASE_VIEW = "INVENTORY_PURCHASE_VIEW";

    private DashboardPermissions() {
    }
}
