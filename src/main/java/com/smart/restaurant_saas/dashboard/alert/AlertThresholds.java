package com.smart.restaurant_saas.dashboard.alert;

import java.math.BigDecimal;

/** The numbers that decide when a condition becomes an alert. */
public final class AlertThresholds {

    /** A4 — a shift open longer than this has outlived any plausible working day. */
    public static final long SHIFT_OPEN_HOURS = 14;

    /** A2 — days after receipt before an unposted invoice is an alert. */
    public static final long INVOICE_UNPOSTED_DAYS = 2;

    /** A3 — days a count may stay frozen. */
    public static final long COUNT_FROZEN_DAYS = 3;

    /** A5 — grace added to the batching age trigger before a PENDING document is an alert. */
    public static final long CONSUMPTION_OVERDUE_GRACE_HOURS = 1;

    /** B3 — the trailing window used to derive a daily consumption rate. */
    public static final int DAYS_OF_COVER_WINDOW_DAYS = 14;

    /** B3 — report a material when its cover is this many days or fewer. */
    public static final BigDecimal DAYS_OF_COVER_MAX = new BigDecimal("2");

    /** B5 — batches expiring within this many days are "value at risk". */
    public static final long EXPIRING_SOON_DAYS = 3;

    /** C1 — a shift's cash miss is reportable past either of two tolerances, whichever it crosses. */
    public static final BigDecimal CASH_VARIANCE_ABSOLUTE = new BigDecimal("200");

    /** The relative arm of {@link #CASH_VARIANCE_ABSOLUTE}, as a fraction of expected cash. */
    public static final BigDecimal CASH_VARIANCE_RELATIVE = new BigDecimal("0.01");

    /** C1 — how far back closed shifts are scanned. A week keeps the strip about the present. */
    public static final long CASH_VARIANCE_LOOKBACK_DAYS = 7;

    /** C2 — shifts short within {@link #CASHIER_PATTERN_WINDOW_DAYS} before it is a pattern. */
    public static final long CASHIER_PATTERN_MIN_SHIFTS = 3;

    public static final long CASHIER_PATTERN_WINDOW_DAYS = 14;

    /** C5 — how far back reconciled counts are scanned for a large variance. */
    public static final long COUNT_VARIANCE_LOOKBACK_DAYS = 7;

    /** C6 — percentage rise in a material's purchase price that is worth telling the owner about. */
    public static final BigDecimal PRICE_JUMP_PERCENT = new BigDecimal("15");

    /** C6 — the window across which a price rise is measured. */
    public static final long PRICE_JUMP_WINDOW_DAYS = 30;

    /** D1 — minutes of silence from a branch with an open shift before it is an alert. */
    public static final long BRANCH_SILENT_MINUTES = 90;

    private AlertThresholds() {
    }
}
