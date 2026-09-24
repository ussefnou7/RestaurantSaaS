package com.smart.restaurant_saas.media;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where media lives and how fast deleted bytes are reclaimed.
 *
 * <p><strong>{@code storageRoot} must be outside the deployed war and must survive redeploy.</strong>
 * A path inside the servlet container is erased by the next deployment. It also means
 * {@code pg_dump} stops being a complete backup of the system: the root needs its own backup, and
 * whoever operates the server has to be told.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.media")
public class MediaProperties {

    /** Filesystem root for the local {@code StorageService}. Created at startup if absent. */
    private String storageRoot = "./media-storage";

    private final DeletionQueue deletionQueue = new DeletionQueue();

    @Getter
    @Setter
    public static class DeletionQueue {

        /** Operational kill-switch for the drain job. Bytes accumulate while it is off. */
        private boolean enabled = true;

        private Duration pollInterval = Duration.ofMinutes(5);

        /** Rows drained per tick. Deleting a file is one syscall, so this can be generous. */
        private int batchSize = 200;
    }
}
