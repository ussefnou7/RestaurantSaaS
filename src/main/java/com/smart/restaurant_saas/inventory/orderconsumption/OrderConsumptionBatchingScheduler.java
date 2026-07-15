package com.smart.restaurant_saas.inventory.orderconsumption;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * D58 dual-trigger batching poll. On each tick it asks the DB which warehouses' PENDING docs have
 * crossed EITHER threshold (line count >= configured count, OR oldest line older than the configured
 * max age), then processes each in two separate transactions: {@link OrderConsumptionService#claimDoc}
 * (commits IN_PROGRESS) followed by {@link OrderConsumptionService#processClaimedDoc} (runs D29).
 *
 * <p>Each doc is processed under its own per-doc ShedLock (programmatic API), so variable-duration
 * D29 processing for one doc cannot block other docs and cannot expire a shared lock mid-batch.
 * The poll/select step itself runs unlocked — it is a cheap idempotent read.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "order-consumption.batching", name = "enabled",
    havingValue = "true", matchIfMissing = true)
public class OrderConsumptionBatchingScheduler {

    private static final Duration LOCK_AT_MOST = Duration.ofMinutes(10);
    private static final Duration LOCK_AT_LEAST = Duration.ofSeconds(1);

    private final OrderConsumptionRepository docRepository;
    private final OrderConsumptionService consumptionService;
    private final OrderConsumptionBatchingProperties properties;
    private final LockingTaskExecutor lockingTaskExecutor;

    @Scheduled(fixedDelayString = "${order-consumption.batching.poll-interval:60s}")
    public void pollAndBatch() {
        LocalDateTime ageCutoff = LocalDateTime.now().minus(properties.getMaxAge());
        List<Long> docIds = docRepository.findDocIdsReadyForBatching(
            OrderConsumptionStatus.PENDING, ageCutoff, properties.getThresholdCount());
        if (docIds.isEmpty()) {
            return;
        }
        log.info("Order consumption batching: {} warehouse doc(s) crossed a threshold", docIds.size());
        for (Long docId : docIds) {
            tryBatchOneWithLock(docId);
        }
    }

    private void tryBatchOneWithLock(Long docId) {
        try {
            LockingTaskExecutor.TaskResult<Void> result = lockingTaskExecutor.executeWithLock(
                () -> { batchOne(docId); return null; },
                new LockConfiguration(Instant.now(), "orderConsumptionBatching:" + docId,
                    LOCK_AT_MOST, LOCK_AT_LEAST)
            );
            if (!result.wasExecuted()) {
                log.debug("Order consumption batching: doc {} skipped, lock held by another instance", docId);
            }
        } catch (Throwable t) {
            log.error("Order consumption batching: lock infrastructure error for doc {}", docId, t);
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
