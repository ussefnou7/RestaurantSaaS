package com.smart.restaurant_saas.media;

import com.smart.restaurant_saas.media.storage.StorageService;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drains {@code media_deletion_queue}: the second half of "commit first, bytes second".
 *
 * <p>A row here means the database has already forgotten the file. The only thing left is the
 * object, and until this runs it is storage nobody is accounting for.
 *
 * <p>Lag is harmless and failure is recoverable — a key that cannot be deleted stays queued and is
 * retried on the next tick. The one outcome worth avoiding is dropping a row whose object survived,
 * which is why the row is removed only after {@code delete} returns.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.media.deletion-queue", name = "enabled",
    havingValue = "true", matchIfMissing = true)
public class MediaDeletionScheduler {

    private final MediaDeletionQueueRepository queueRepository;
    private final StorageService storageService;
    private final MediaProperties properties;

    @Scheduled(fixedDelayString = "${app.media.deletion-queue.poll-interval:5m}")
    @SchedulerLock(name = "mediaDeletionQueueDrain", lockAtMostFor = "PT10M", lockAtLeastFor = "PT1S")
    @Transactional
    public void drain() {
        List<MediaDeletionQueueEntry> batch = queueRepository.findByOrderByIdAsc(
                Limit.of(properties.getDeletionQueue().getBatchSize()));
        if (batch.isEmpty()) {
            return;
        }

        List<MediaDeletionQueueEntry> deleted = new ArrayList<>(batch.size());
        for (MediaDeletionQueueEntry entry : batch) {
            try {
                storageService.delete(entry.getStorageKey());
                deleted.add(entry);
            } catch (Exception ex) {
                // One unreachable key must not strand the rest of the batch behind it.
                log.error("Media deletion: could not remove {} (tenant {}); it stays queued",
                        entry.getStorageKey(), entry.getTenantId(), ex);
            }
        }
        queueRepository.deleteAll(deleted);
        log.info("Media deletion: removed {} of {} queued object(s)", deleted.size(), batch.size());
    }
}
