package com.smart.restaurant_saas.tenant;

import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.common.ValidationException;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Resolves the wall clock a tenant's timestamps are written in (D101).
 *
 * <p>Resolution is {@code branch.timezone -> tenant.timezone} with <b>no third fallback</b>. A
 * tenant without a zone throws. Defaulting to Cairo at runtime is exactly the bug D101 fixes — it
 * would make correctness depend on a guess instead of on configuration.
 *
 * <p><b>Reads go through {@link JdbcTemplate}, not the repositories, on purpose.</b> The main caller
 * is {@code TenantTimestampListener}, which runs inside {@code @PrePersist} — that is, part-way
 * through a Hibernate flush. Loading an entity through the same persistence context at that moment
 * risks a re-entrant flush and can mutate the action queue Hibernate is iterating. A plain SQL read
 * of two tiny tables sidesteps the session entirely.
 *
 * <p><b>Cached indefinitely, evicted on write.</b> A zone changes roughly never, and the listener
 * would otherwise issue a query per row inserted. {@link #evictTenant} / {@link #evictBranch} are
 * called by the services that mutate the columns; nothing else may write them.
 */
@Service
@RequiredArgsConstructor
public class TenantTimeZoneService {

    private final JdbcTemplate jdbcTemplate;

    private final Map<Long, ZoneId> tenantZones = new ConcurrentHashMap<>();
    /** Branch id -> its own override. Absent-but-cached is represented by {@link #NO_OVERRIDE}. */
    private final Map<Long, ZoneId> branchZones = new ConcurrentHashMap<>();

    /**
     * Sentinel for "this branch has been looked up and has no override". ConcurrentHashMap cannot
     * store null, and without a sentinel every save from a branch that inherits its tenant's zone
     * would re-query on every row.
     */
    private static final ZoneId NO_OVERRIDE = ZoneId.of("Etc/UTC");

    /** The zone a tenant's timestamps are written in, ignoring any branch override. */
    public ZoneId zoneFor(Long tenantId) {
        if (tenantId == null) {
            throw new BusinessException(TenantErrorCode.TENANT_CONTEXT_MISSING,
                "Cannot resolve a timezone without a tenant id",
                ErrorParams.of("entityType", "Tenant"));
        }
        return tenantZones.computeIfAbsent(tenantId, this::loadTenantZone);
    }

    /** The zone for a write scoped to a branch: the branch's override when set, else the tenant's. */
    public ZoneId zoneFor(Long tenantId, Long branchId) {
        if (branchId == null) {
            return zoneFor(tenantId);
        }
        ZoneId override = branchZones.computeIfAbsent(branchId, id -> loadBranchZone(id, tenantId));
        return override == NO_OVERRIDE ? zoneFor(tenantId) : override;
    }

    public void evictTenant(Long tenantId) {
        tenantZones.remove(tenantId);
    }

    public void evictBranch(Long branchId) {
        branchZones.remove(branchId);
    }

    /**
     * Parses and validates an IANA zone id supplied by a client. Rejects numeric offsets
     * ({@code +03:00}) as well as unknown ids: an offset carries no region identity and cannot be
     * reasoned about, and D101 decision 1 requires a zone id.
     */
    public static ZoneId parseZone(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            throw new ValidationException(TenantErrorCode.INVALID_TIMEZONE,
                "Timezone is required",
                ErrorParams.of("timezone", timezone));
        }
        String trimmed = timezone.trim();
        if (!ZoneId.getAvailableZoneIds().contains(trimmed)) {
            throw new ValidationException(TenantErrorCode.INVALID_TIMEZONE,
                "Not an IANA zone id: " + trimmed,
                ErrorParams.of("timezone", trimmed));
        }
        try {
            return ZoneId.of(trimmed);
        } catch (DateTimeException ex) {
            throw new ValidationException(TenantErrorCode.INVALID_TIMEZONE,
                "Not an IANA zone id: " + trimmed,
                ErrorParams.of("timezone", trimmed));
        }
    }

    private ZoneId loadTenantZone(Long tenantId) {
        String timezone;
        try {
            timezone = jdbcTemplate.queryForObject(
                "SELECT timezone FROM tenants WHERE id = ?", String.class, tenantId);
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            throw new ResourceNotFoundException(TenantErrorCode.TENANT_NOT_FOUND,
                "Tenant not found while resolving timezone: " + tenantId,
                ErrorParams.of("entityType", "Tenant", "entityId", tenantId));
        }
        if (timezone == null || timezone.isBlank()) {
            throw new BusinessException(TenantErrorCode.TENANT_TIMEZONE_MISSING,
                "Tenant has no timezone: " + tenantId,
                ErrorParams.of("entityType", "Tenant", "entityId", tenantId));
        }
        return toZone(timezone, "Tenant", tenantId);
    }

    private ZoneId loadBranchZone(Long branchId, Long tenantId) {
        String timezone = jdbcTemplate.query(
            "SELECT timezone FROM branches WHERE id = ? AND tenant_id = ?",
            rs -> rs.next() ? rs.getString(1) : null,
            branchId, tenantId);
        // A branch that is missing, or belongs to another tenant, inherits rather than throws:
        // the caller already validated ownership, and a wrong branch id must not decide the zone.
        return timezone == null || timezone.isBlank()
            ? NO_OVERRIDE
            : toZone(timezone, "Branch", branchId);
    }

    private ZoneId toZone(String timezone, String entityType, Long entityId) {
        try {
            return ZoneId.of(timezone.trim());
        } catch (DateTimeException ex) {
            throw new BusinessException(TenantErrorCode.INVALID_TIMEZONE,
                "Stored timezone is not a valid zone id: " + timezone,
                ErrorParams.of("entityType", entityType, "entityId", entityId,
                    "timezone", timezone));
        }
    }
}
