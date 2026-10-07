package com.smart.restaurant_saas.dashboard;

/**
 * The grain of the dashboard's sales trend.
 *
 * <p><b>Derived from the length of the requested range, never passed in.</b> That is the same rule
 * the reports follow — the grouping of a series is fixed and is never a filter (D86) — and here it
 * also removes a whole class of nonsense request: a year bucketed by hour is 8,760 points nobody
 * can read, and a single day bucketed by month is one bar. The caller says which dates it wants;
 * the server decides what shape answers that honestly.
 */
public enum TrendBucket {

    /** A single day, or less. Reuses the hourly sales report query. */
    HOUR,

    /** Up to roughly two months. Reuses the daily sales report query. */
    DAY,

    /** Anything longer. */
    MONTH;

    /** A day or less gets hours. */
    private static final long MAX_HOUR_BUCKET_DAYS = 1;

    /**
     * Two months, not one.
     *
     * <p>62 rather than 31 so that the common "last two months" range still shows daily shape
     * instead of collapsing to two bars. The number is the longest run of days that still reads as
     * a chart, not a property of the calendar.
     */
    private static final long MAX_DAY_BUCKET_DAYS = 62;

    /**
     * Picks the grain for a range of {@code days} calendar days (inclusive of both ends).
     *
     * @param days the number of days the range covers; at least 1.
     */
    public static TrendBucket forRangeOf(long days) {
        if (days <= MAX_HOUR_BUCKET_DAYS) {
            return HOUR;
        }
        if (days <= MAX_DAY_BUCKET_DAYS) {
            return DAY;
        }
        return MONTH;
    }
}
