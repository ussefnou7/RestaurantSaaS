package com.smart.restaurant_saas.inventory.backfill;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Runs the {@link StockBalanceAverageCostBackfill} once on startup, but only when
 * {@code inventory.backfill.average-cost.enabled=true}. Off by default so the correction is an
 * explicit, opt-in operational action rather than something that fires on every boot.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "inventory.backfill.average-cost.enabled", havingValue = "true")
public class AverageCostBackfillRunner implements ApplicationRunner {

    private final StockBalanceAverageCostBackfill backfill;

    @Override
    public void run(ApplicationArguments args) {
        log.info("inventory.backfill.average-cost.enabled=true — running stock-balance average-cost backfill");
        backfill.backfill();
    }
}
