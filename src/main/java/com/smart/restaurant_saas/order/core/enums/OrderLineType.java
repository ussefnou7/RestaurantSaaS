package com.smart.restaurant_saas.order.core.enums;

/**
 * What an order line represents (D20).
 *
 * <p>Both kinds consume stock; they differ in why, and in which ledger movement records it. A
 * {@code SALE} line was paid for and posts as {@code CONSUMPTION_SUMMARY}. A {@code WASTE} line was
 * cooked and then taken off the order, so the food was made and binned — it posts as
 * {@code WASTE} and carries no money.
 *
 * <p>There is deliberately no value for an item cancelled before the kitchen started it. Nothing
 * was consumed, so there is nothing to record: D20 maps those stages to no consumption at all,
 * and a line for them would be a row that exists only to say nothing happened.
 */
public enum OrderLineType {
    SALE,
    WASTE
}
