package com.smart.restaurant_saas.media;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * A storage key whose row is already gone and whose bytes are not yet.
 *
 * <p>Written in the same transaction as the row deletion and drained by
 * {@link MediaDeletionScheduler} afterwards. It is transactional by being an ordinary table; that
 * is the entire mechanism.
 *
 * <p>Not a {@code TenantAwareEntity}: it carries {@code tenantId} for operability but no audit
 * columns, because nothing about it is a business record and a row's whole life is measured in
 * minutes.
 */
@Getter
@Setter
@Entity
@Table(name = "media_deletion_queue")
public class MediaDeletionQueueEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Column(name = "enqueued_at", nullable = false)
    private LocalDateTime enqueuedAt;
}
