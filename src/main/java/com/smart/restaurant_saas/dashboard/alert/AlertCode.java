package com.smart.restaurant_saas.dashboard.alert;

import com.smart.restaurant_saas.dashboard.DashboardPermissions;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** The owner dashboard's alert catalog — one constant per <em>condition</em>, not per event. */
@Getter
@RequiredArgsConstructor
public enum AlertCode {

    // ---- A. The numbers are incomplete ------------------------------------------------------

    /** A1 — consumption document stuck in PARTIAL or CONFLICT. */
    UNPOSTED_CONSUMPTION(AlertSeverity.CRITICAL, AlertLinkTarget.ORDER_CONSUMPTION_LIST,
        true, true, DashboardPermissions.INVENTORY_REPORTS_VIEW),

    /** A2 — purchase invoice still DRAFT/COMPLETE more than two days after its receipt date. */
    UNPOSTED_PURCHASE_INVOICE(AlertSeverity.WARNING, AlertLinkTarget.PURCHASE_INVOICE_LIST,
        true, false, DashboardPermissions.INVENTORY_PURCHASE_VIEW),

    /** A3 — physical count frozen (IN_PROGRESS) for more than three days, blocking the warehouse. */
    FROZEN_PHYSICAL_COUNT(AlertSeverity.WARNING, AlertLinkTarget.PHYSICAL_COUNT_LIST,
        true, false, DashboardPermissions.INVENTORY_REPORTS_VIEW),

    /** A4 — shift open longer than 14 hours; nobody has counted the drawer. */
    SHIFT_OPEN_TOO_LONG(AlertSeverity.CRITICAL, AlertLinkTarget.SHIFT_LIST,
        true, false, DashboardPermissions.SHIFTS_VIEW),

    /** A5 — a PENDING consumption document older than the batching age trigger plus an hour. */
    CONSUMPTION_SCHEDULER_STALLED(AlertSeverity.CRITICAL, AlertLinkTarget.ORDER_CONSUMPTION_LIST,
        true, false, DashboardPermissions.INVENTORY_REPORTS_VIEW),

    // ---- B. Stock ---------------------------------------------------------------------------

    /** B1 — a material with a configured minimum has run to zero or below, per warehouse. */
    STOCK_OUT(AlertSeverity.CRITICAL, AlertLinkTarget.LOW_STOCK_REPORT,
        false, false, DashboardPermissions.INVENTORY_REPORTS_VIEW),

    /** B2 — below the configured minimum but not yet out, per warehouse. Never summed across warehouses (D99). */
    BELOW_MINIMUM(AlertSeverity.WARNING, AlertLinkTarget.LOW_STOCK_REPORT,
        false, false, DashboardPermissions.INVENTORY_REPORTS_VIEW),

    /** B3 — days of cover at or below two, from the trailing 14-day consumption rate. */
    RUNNING_OUT_SOON(AlertSeverity.WARNING, AlertLinkTarget.LOW_STOCK_REPORT,
        false, true, DashboardPermissions.INVENTORY_REPORTS_VIEW),

    /** B4 — expired batch still carrying quantity. Food safety, so it is critical regardless of value. */
    EXPIRED_STOCK_ON_SHELF(AlertSeverity.CRITICAL, AlertLinkTarget.STOCK_BALANCE_LIST,
        true, false, DashboardPermissions.INVENTORY_STOCK_VIEW),

    /** B5 — batches expiring within three days. Shown as value at risk, not as a list of items. */
    EXPIRING_SOON(AlertSeverity.WARNING, AlertLinkTarget.STOCK_BALANCE_LIST,
        true, false, DashboardPermissions.INVENTORY_STOCK_VIEW),

    /** B6 — an expiry-tracked material holding a batch with no expiry date: the tracking is not working. */
    MISSING_EXPIRY_DATE(AlertSeverity.INFO, AlertLinkTarget.STOCK_BALANCE_LIST,
        false, false, DashboardPermissions.INVENTORY_STOCK_VIEW),

    // ---- C. Money leaking -------------------------------------------------------------------

    /** C1 — a closed shift whose counted cash missed expected by more than 1% or 200 EGP. */
    CASH_VARIANCE(AlertSeverity.CRITICAL, AlertLinkTarget.SHIFT_LIST,
        true, false, DashboardPermissions.SHIFTS_VIEW_VARIANCE),

    /** C2 — the same cashier short on three or more shifts within 14 days. */
    CASHIER_SHORTAGE_PATTERN(AlertSeverity.CRITICAL, AlertLinkTarget.SHIFT_LIST,
        true, false, DashboardPermissions.SHIFTS_VIEW_VARIANCE),

    /** C3 — any order cancelled after the food was cooked, today, grouped per branch. */
    CANCELLED_AFTER_COOKING(AlertSeverity.CRITICAL, AlertLinkTarget.ORDER_LIST,
        true, false, DashboardPermissions.ORDERS_VIEW),

    /** C5 — a count reconciled in the last 7 days that crossed the large-variance threshold. */
    LARGE_COUNT_VARIANCE(AlertSeverity.CRITICAL, AlertLinkTarget.PHYSICAL_COUNT_LIST,
        true, false, DashboardPermissions.INVENTORY_REPORTS_VIEW),

    /** C6 — a material's latest purchase came in at least 15% above the purchase before it. */
    SUPPLIER_PRICE_JUMP(AlertSeverity.INFO, AlertLinkTarget.PURCHASE_PRICE_DRIFT_REPORT,
        false, false, DashboardPermissions.INVENTORY_PURCHASE_VIEW),

    // ---- D. Branch health right now ---------------------------------------------------------

    /** D1 — no order for 90 minutes in a branch that has a shift open. */
    BRANCH_SILENT(AlertSeverity.CRITICAL, AlertLinkTarget.ORDER_LIST,
        true, false, DashboardPermissions.ORDERS_VIEW);

    private final AlertSeverity severity;

    /** Where the condition is cleared. */
    private final AlertLinkTarget link;

    /** Whether the source row records when the condition began — see the class javadoc. */
    private final boolean hasAge;

    /** Whether {@code value}, when present, is derived from an assumption rather than measured. */
    private final boolean valueIsEstimate;

    /** The permission a caller needs before this condition is evaluated at all. */
    private final String requiredPermission;
}
