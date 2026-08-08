package com.smart.restaurant_saas.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Audit columns shared by every persisted entity.
 *
 * <p>Timestamps are stamped by {@link TenantTimestampListener} in the owning tenant's wall clock
 * (D101), not by {@code @PrePersist} methods here. The callbacks that used to live on this class
 * called {@code LocalDateTime.now()}, which reads the JVM's zone — so which wall clock a row
 * recorded depended on where the server happened to sit. An entity is not a Spring bean and cannot
 * reach the zone service; a listener can.
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(TenantTimestampListener.class)
public abstract class BaseEntity {

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;
}
