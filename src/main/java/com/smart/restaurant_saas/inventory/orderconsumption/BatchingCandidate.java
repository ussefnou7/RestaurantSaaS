package com.smart.restaurant_saas.inventory.orderconsumption;

import java.time.LocalDateTime;

/**
 * A PENDING doc that <em>may</em> have crossed a batching threshold, with everything the scheduler
 * needs to decide precisely — without going back to the database per tenant.
 *
 * <p>The age half of D58's dual trigger compares a stored timestamp against a cutoff, and after
 * D101 those timestamps are in each tenant's own wall clock. One cutoff cannot be correct for
 * several zones at once, so the query over-selects with a widened cutoff and
 * {@code OrderConsumptionBatchingScheduler} re-checks each row against its own tenant's clock.
 * Carrying {@code tenantId}, {@code createdAt} and {@code lineCount} out of that one query is what
 * keeps the re-check free.
 */
public record BatchingCandidate(Long id, Long tenantId, LocalDateTime createdAt, long lineCount) {
}
