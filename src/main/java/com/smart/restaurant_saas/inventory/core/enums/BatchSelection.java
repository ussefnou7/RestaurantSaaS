package com.smart.restaurant_saas.inventory.core.enums;

/**
 * Which batches an outbound movement depletes. The calling service decides; the ledger only
 * dispatches on it, so no document type is ever named inside the engine.
 *
 * <p>{@link #SOURCE_LINE} requires {@code sourceInvoiceLineId} on the command and is bounded by
 * that batch's remaining quantity — the caller's guard must have checked it (D139).
 */
public enum BatchSelection {

    /** Oldest-first across the balance's open batches. The default for every movement. */
    FIFO,

    /** Only the batch opened by {@code sourceInvoiceLineId}, valued at that batch's own cost. */
    SOURCE_LINE
}
