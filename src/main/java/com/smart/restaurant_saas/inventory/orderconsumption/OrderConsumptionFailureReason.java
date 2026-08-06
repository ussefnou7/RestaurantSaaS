package com.smart.restaurant_saas.inventory.orderconsumption;

/**
 * Why a material of an order consumption doc was not consumed. Drives the doc's derived status:
 * {@link #TECHNICAL_FAILURE} makes the doc CONFLICT and takes precedence over
 * {@link #INSUFFICIENT_STOCK}, which makes it PARTIAL (D94).
 */
public enum OrderConsumptionFailureReason {

    /** Open batches could not cover the requirement. Expected, per-material, user-fixable (D94). */
    INSUFFICIENT_STOCK,

    /** The material's REQUIRES_NEW consumption transaction threw. Systemic (D30). */
    TECHNICAL_FAILURE
}
