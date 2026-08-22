package com.smart.restaurant_saas.inventory.uom;

import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.tenant.TenantErrorCode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

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

    private final JdbcTemplate jdbcTemplate;
    private final Map<Long, String> tenantVersions = new ConcurrentHashMap<>();

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
