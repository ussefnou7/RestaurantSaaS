package com.smart.restaurant_saas.dashboard.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * One slice of a breakdown — an order type, a payment method, or a cancellation stage.
 *
 * <p>{@code dimension} is the raw enum name. The frontend owns a translation key per value, so no
 * user-facing text and no Arabic string crosses the wire — the same contract as {@code errorCode}
 * plus params (D12).
 */
@Getter
@Builder
public class MixRow {

    /** Raw enum name: {@code DELIVERY}, {@code CARD}, {@code AFTER_DONE}, … */
    private final String dimension;

    private final Long orderCount;

    private final String netSales;

    private final String totalAmount;

    /**
     * Share of the range's total, or null where a share would be meaningless.
     *
     * <p>Null on cancellations: a share of cancellations only means something measured against the
     * sales they would have been, and dividing them by their own total yields percentages that add
     * to 100 and say nothing.
     */
    private final String sharePercent;
}
