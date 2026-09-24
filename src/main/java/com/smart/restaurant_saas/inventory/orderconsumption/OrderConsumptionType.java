package com.smart.restaurant_saas.inventory.orderconsumption;

/**
 * What the doc's lines consumed stock for (D20). Both run the identical lifecycle; the type only
 * decides which ledger movement the posting writes.
 */
public enum OrderConsumptionType {
    /** Sold and paid for — posts as CONSUMPTION_SUMMARY. */
    ORDINARY,
    /** Cooked then taken off the order — posts as WASTE. */
    WASTE
}
