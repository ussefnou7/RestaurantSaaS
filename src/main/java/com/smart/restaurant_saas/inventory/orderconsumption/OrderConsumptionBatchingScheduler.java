package com.smart.restaurant_saas.inventory.orderconsumption;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * D58 dual-trigger batching poll. On each tick it asks the DB which warehouses' PENDING docs have
 * crossed EITHER threshold (line count >= configured count, OR oldest line older than the configured
 * max age), then processes each in two separate transactions: {@link OrderConsumptionService#claimDoc}
 * (commits IN_PROGRESS) followed by {@link OrderConsumptionService#processClaimedDoc} (runs D29).
 *
 * <p>Guarded by ShedLock so only one application instance runs a given tick. Concurrency between the
 * claim and a racing order completion is additionally protected by the D44 doc lock.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "order-consumption.batching", name = "enabled",
    havingValue = "true", matchIfMissing = true)
public class OrderConsumptionBatchingScheduler {

    private final OrderConsumptionRepository docRepository;
    private final OrderConsumptionService consumptionService;
    private final OrderConsumptionBatchingProperties properties;

    @Scheduled(fixedDelayString = "${order-consumption.batching.poll-interval:60s}")
    @SchedulerLock(name = "orderConsumptionBatching", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1S")
    public void pollAndBatch() {
        LocalDateTime ageCutoff = LocalDateTime.now().minus(properties.getMaxAge());
        List<Long> docIds = docRepository.findDocIdsReadyForBatching(
            OrderConsumptionStatus.PENDING, ageCutoff, properties.getThresholdCount());
        if (docIds.isEmpty()) {
            return;
        }
        log.info("Order consumption batching: {} warehouse doc(s) crossed a threshold", docIds.size());
        for (Long docId : docIds) {
            batchOne(docId);
        }
    }

    private void batchOne(Long docId) {
        try {
            // Two separate transactions on purpose (D58): claimDoc COMMITS IN_PROGRESS before the
            // D29 run in processClaimedDoc, so a concurrent completion branches to a new PENDING doc.
            if (consumptionService.claimDoc(docId, null)) {
                consumptionService.processClaimedDoc(docId, null);
            }
        } catch (Exception ex) {
            // One warehouse's failure must not block the rest of this poll cycle.
            log.error("Order consumption batching failed for doc {}", docId, ex);
        }
    }
}
