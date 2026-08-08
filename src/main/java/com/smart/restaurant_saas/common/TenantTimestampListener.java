package com.smart.restaurant_saas.common;

import com.smart.restaurant_saas.tenant.TenantErrorCode;
import com.smart.restaurant_saas.tenant.TenantTimeZoneService;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Stamps {@code createdAt} / {@code updatedAt} in the owning tenant's wall clock (D101).
 *
 * <p><b>The tenant comes off the row being saved, never from ambient context.</b> A ThreadLocal
 * holder was the obvious alternative and is the wrong shape here: it contradicts the explicit
 * {@code tenantId} convention this codebase uses across 38 controllers, it empties silently at
 * every async boundary, and it leaks across pooled threads. {@link TenantAwareEntity} already
 * carries the answer. The practical payoff is that the order-consumption scheduler needs no changes
 * at all — each row it writes knows its own tenant.
 *
 * <p><b>A missing tenant id throws.</b> Falling back to {@code LocalDateTime.now()} would write
 * server time, which is precisely the defect D101 exists to remove, and it would do so invisibly:
 * the row looks plausible either way. A loud failure is the entire point.
 *
 * <p>Registered by {@code @EntityListeners} on {@link BaseEntity}. Hibernate resolves it through
 * Spring's {@code SpringBeanContainer}, so constructor injection works and no static holder is
 * needed. The dependency is taken as an {@link ObjectProvider} so the listener does not force
 * {@link TenantTimeZoneService} to be built while the EntityManagerFactory is still bootstrapping.
 *
 * <p><b>Listener callbacks run before entity callbacks</b> (JPA 3.1 §3.5.4), so an entity-level
 * {@code @PrePersist} that still guards on {@code createdAt == null} — as
 * {@code InventoryTransaction} does — sees the value this listener already set and leaves it alone.
 */
@Component
public class TenantTimestampListener {

    private final ObjectProvider<TenantTimeZoneService> timeZoneService;

    public TenantTimestampListener(ObjectProvider<TenantTimeZoneService> timeZoneService) {
        this.timeZoneService = timeZoneService;
    }

    @PrePersist
    void onCreate(BaseEntity entity) {
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(LocalDateTime.now(resolve(entity)));
        }
    }

    @PreUpdate
    void onUpdate(BaseEntity entity) {
        entity.setUpdatedAt(LocalDateTime.now(resolve(entity)));
    }

    private ZoneId resolve(BaseEntity entity) {
        if (!(entity instanceof TenantAwareEntity tenantAware)) {
            return timeZoneService.getObject().systemZone();
        }
        Long tenantId = tenantAware.getTenantId();
        if (tenantId == null) {
            throw new BusinessException(TenantErrorCode.TENANT_CONTEXT_MISSING,
                "Cannot stamp an audit timestamp: " + entity.getClass().getSimpleName()
                    + " reached persist with no tenantId",
                ErrorParams.of("entityType", entity.getClass().getSimpleName()));
        }
        return timeZoneService.getObject().zoneFor(tenantId);
    }
}
