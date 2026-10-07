package com.smart.restaurant_saas.dashboard.dto;

/**
 * Whether a dashboard block has an answer, and if not, why not.
 *
 * <p><b>This enum is how the dashboard keeps rule 5: a gap is never a zero.</b> Three different
 * situations all produce an empty block, and they mean entirely different things — the data is
 * genuinely zero, the caller is not allowed to see it, or there is nothing to compute it from. A
 * response that could not tell them apart would leave the frontend to guess, and the guess that
 * reads best on screen is always the reassuring one.
 */
public enum BlockStatus {

    /**
     * The block was computed. Its figures may still be zero, and a zero here is a real
     * measurement: no sales in the window means no sales.
     */
    OK,

    /**
     * The caller lacks the permission of the module this block is sourced from.
     *
     * <p>Not an error and not an empty block: the screen must say the figures are withheld. The
     * dashboard reads across every module, so without this it becomes a way around the permissions
     * on the screens that own the data.
     */
    HIDDEN_NO_PERMISSION,

    /**
     * There is nothing to compute the block from — a ratio with a zero denominator, a derived
     * figure with no basis.
     *
     * <p>The case this exists for is cost coverage over a window with no sales: 0/0 is not 100%,
     * and reporting "all costs posted" for a day that sold nothing is a false reassurance about
     * the exact thing the figure is supposed to warn about.
     */
    INSUFFICIENT_DATA
}
