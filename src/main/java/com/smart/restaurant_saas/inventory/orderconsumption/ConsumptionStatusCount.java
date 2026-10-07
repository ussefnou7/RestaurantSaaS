package com.smart.restaurant_saas.inventory.orderconsumption;

import java.time.LocalDateTime;

/**
 * One status of the consumption pipeline, for the dashboard's pipeline block.
 *
 * <p>{@code status} is the raw column value rather than the enum: the query is native, so Hibernate
 * has no enum conversion to apply, and the frontend already owns a translation key per status.
 */
public interface ConsumptionStatusCount {

    String getStatus();

    Long getDocCount();

    /**
     * The oldest document in this status. Carries the finding for PENDING — a pipeline with 40
     * pending documents whose oldest is four minutes old is working; one whose oldest is nine
     * hours old has stopped, and the count alone cannot tell the two apart.
     */
    LocalDateTime getOldestAt();
}
