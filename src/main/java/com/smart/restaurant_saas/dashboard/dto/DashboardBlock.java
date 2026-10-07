package com.smart.restaurant_saas.dashboard.dto;

import lombok.Getter;

/**
 * A section of the dashboard, with its status attached.
 *
 * <p>Every block is wrapped rather than only the ones that currently need it, so that adding a
 * permission to a block later is a service change and not a contract change — and so that a
 * frontend reading one block cannot develop the habit of assuming payloads are always present.
 *
 * <p>{@code data} is null for every non-{@link BlockStatus#OK} status. That pairing is enforced by
 * the factory methods rather than left to callers: a withheld block carrying data would defeat the
 * permission check, and an {@code OK} block carrying none would render as a silent zero.
 *
 * @param <T> the block's payload.
 */
@Getter
public final class DashboardBlock<T> {

    private final BlockStatus status;
    private final T data;

    private DashboardBlock(BlockStatus status, T data) {
        this.status = status;
        this.data = data;
    }

    public static <T> DashboardBlock<T> of(T data) {
        return new DashboardBlock<>(BlockStatus.OK, data);
    }

    /** The caller may not see this block's source module. */
    public static <T> DashboardBlock<T> hidden() {
        return new DashboardBlock<>(BlockStatus.HIDDEN_NO_PERMISSION, null);
    }

    /** Nothing to compute it from — see {@link BlockStatus#INSUFFICIENT_DATA}. */
    public static <T> DashboardBlock<T> insufficientData() {
        return new DashboardBlock<>(BlockStatus.INSUFFICIENT_DATA, null);
    }
}
