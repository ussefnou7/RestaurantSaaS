package com.smart.restaurant_saas.inventory.uom;

import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.tenant.TenantErrorCode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Resolves the tenant-visible UOM lookup version.
 *
 * <p>Reads go through {@link JdbcTemplate}, matching TenantTimeZoneService's cache shape: the
 * value is needed from a cross-cutting response filter, so repositories would make every request
 * pay Hibernate overhead. The value is cached indefinitely and evicted by UOM write paths.
 */
@Service
public class UomLookupVersionService {

    public static final String RESPONSE_HEADER = "X-Lookups-Version";

    /**
     * Cap on distinct tenants held in memory. The tenant id reaching this cache comes from a
     * request header, so without a bound any caller could grow the map without limit by varying
     * it. An LRU is safe here because eviction only costs the next request one aggregation.
     */
    static final int MAX_CACHED_TENANTS = 512;

    private final JdbcTemplate jdbcTemplate;

    /** Access-ordered LRU, synchronized: writes are rare and reads are cheap. */
    private final Map<Long, String> tenantVersions = Collections.synchronizedMap(
        new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, String> eldest) {
                return size() > MAX_CACHED_TENANTS;
            }
        });

    public UomLookupVersionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String versionForTenant(Long tenantId) {
        if (tenantId == null) {
            throw new ResourceNotFoundException(TenantErrorCode.TENANT_NOT_FOUND,
                "Cannot resolve a UOM lookup version without a tenant id",
                ErrorParams.of("entityType", "Tenant"));
        }
        return tenantVersions.computeIfAbsent(tenantId, this::loadVersion);
    }

    public void evictTenant(Long tenantId) {
        if (tenantId == null) {
            evictAll();
            return;
        }
        tenantVersions.remove(tenantId);
    }

    public void evictAll() {
        tenantVersions.clear();
    }

    /** Visible for tests: asserts the LRU actually bounds itself. */
    int cachedTenantCount() {
        return tenantVersions.size();
    }

    /**
     * Evicts now <em>and</em> again after the current transaction commits.
     *
     * <p>Evicting only before commit is not enough: between the eviction and the commit, a
     * concurrent request can recompute the version, read the pre-commit rows, and re-cache the old
     * value. Nothing evicts it afterwards, so the lookup then serves stale rows and {@code 304}s
     * against a version that no longer matches the database, indefinitely.
     *
     * <p>The immediate eviction is kept so a read later in the same transaction does not see a
     * value it just invalidated. Evicting twice is cheaper to reason about than proving a single
     * eviction is correctly ordered.
     */
    public void evictTenantAfterCommit(Long tenantId) {
        evictTenant(tenantId);
        afterCommit(() -> evictTenant(tenantId));
    }

    /** {@link #evictTenantAfterCommit} for changes that move every tenant's version. */
    public void evictAllAfterCommit() {
        evictAll();
        afterCommit(this::evictAll);
    }

    private void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // No transaction in play — the immediate eviction already stands alone.
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    public static String lookupHeaderValue(String version) {
        return "uom=" + version;
    }

    public static String etagValue(String version) {
        return "\"" + version.replace("\"", "\\\"") + "\"";
    }

    public static boolean matchesEtag(String ifNoneMatch, String version) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        String current = etagValue(version);
        for (String candidate : ifNoneMatch.split(",")) {
            String tag = candidate.trim();
            if ("*".equals(tag)) {
                return true;
            }
            if (tag.startsWith("W/")) {
                tag = tag.substring(2).trim();
            }
            if (current.equals(tag)) {
                return true;
            }
            if (tag.length() >= 2 && tag.startsWith("\"") && tag.endsWith("\"")) {
                tag = tag.substring(1, tag.length() - 1);
            }
            if (version.equals(tag)) {
                return true;
            }
        }
        return false;
    }

    private String loadVersion(Long tenantId) {
        return jdbcTemplate.query("""
            SELECT COUNT(*) AS row_count,
                   COALESCE(MAX(id), 0) AS max_id,
                   COALESCE(SUM(CASE WHEN active THEN 1 ELSE 0 END), 0) AS active_count,
                   COALESCE(
                       MAX(GREATEST(created_at, COALESCE(updated_at, created_at))),
                       TIMESTAMP 'epoch'
                   ) AS latest_changed_at
            FROM uom
            WHERE tenant_id IS NULL OR tenant_id = ?
            """, rs -> {
            rs.next();
            long rowCount = rs.getLong("row_count");
            long maxId = rs.getLong("max_id");
            long activeCount = rs.getLong("active_count");
            Timestamp latestTimestamp = rs.getTimestamp("latest_changed_at");
            LocalDateTime latestChangedAt = latestTimestamp.toLocalDateTime();
            return rowCount + ":" + activeCount + ":" + maxId + ":" + latestChangedAt;
        }, tenantId);
    }
}
