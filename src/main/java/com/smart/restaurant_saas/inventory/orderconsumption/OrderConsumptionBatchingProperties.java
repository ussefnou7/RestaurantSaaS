package com.smart.restaurant_saas.inventory.orderconsumption;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * D58 dual-trigger batching thresholds. A warehouse's PENDING order-consumption doc is picked up
 * for processing when EITHER its unprocessed line count reaches {@link #thresholdCount} OR its
 * oldest line is older than {@link #maxAge} — whichever comes first. Externalized because both are
 * tunable operational parameters (see application.yml).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "order-consumption.batching")
public class OrderConsumptionBatchingProperties {

    /** Operational kill-switch for the batching scheduler (the manual D45 retry is unaffected). */
    private boolean enabled = true;

    /** Fire when this many unprocessed lines have accumulated for a warehouse. */
    private int thresholdCount = 50;

    /** Fire when the oldest unprocessed line for a warehouse is older than this. */
    private Duration maxAge = Duration.ofHours(8);

    /** How often the scheduler polls for warehouses crossing either threshold. */
    private Duration pollInterval = Duration.ofSeconds(60);
}
