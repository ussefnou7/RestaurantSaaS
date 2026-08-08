package com.smart.restaurant_saas.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.restaurant_saas.branch.Branch;
import com.smart.restaurant_saas.branch.BranchRepository;
import com.smart.restaurant_saas.common.AppException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * D101 zone resolution and the audit-timestamp listener, against a real database.
 *
 * <p>Covers verification items 1, 3, 4 and 5 of the D101 plan.
 */
@SpringBootTest
@Transactional
class TenantTimeZoneIntegrationTest {

    private static final Long RIYADH_TENANT_ID = 996_001L;
    private static final Long CAIRO_TENANT_ID = 996_002L;
    private static final Long INHERITING_BRANCH_ID = 996_101L;
    private static final Long DUBAI_BRANCH_ID = 996_102L;

    private static final ZoneId RIYADH = ZoneId.of("Asia/Riyadh");
    private static final ZoneId DUBAI = ZoneId.of("Asia/Dubai");

    @Autowired
    private TenantTimeZoneService tenantTimeZoneService;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("""
            INSERT INTO tenants (id, name, code, status, created_at, timezone)
            VALUES (?, 'Riyadh Tenant', 'TZ_RIYADH', 'ACTIVE', CURRENT_TIMESTAMP, 'Asia/Riyadh')
            ON CONFLICT (id) DO NOTHING
            """, RIYADH_TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO tenants (id, name, code, status, created_at, timezone)
            VALUES (?, 'Cairo Tenant', 'TZ_CAIRO', 'ACTIVE', CURRENT_TIMESTAMP, 'Africa/Cairo')
            ON CONFLICT (id) DO NOTHING
            """, CAIRO_TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO branches (id, tenant_id, name, code, is_active, created_at, timezone)
            VALUES (?, ?, 'Inheriting Branch', 'TZ-BR-1', TRUE, CURRENT_TIMESTAMP, NULL)
            ON CONFLICT (id) DO NOTHING
            """, INHERITING_BRANCH_ID, RIYADH_TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO branches (id, tenant_id, name, code, is_active, created_at, timezone)
            VALUES (?, ?, 'Dubai Branch', 'TZ-BR-2', TRUE, CURRENT_TIMESTAMP, 'Asia/Dubai')
            ON CONFLICT (id) DO NOTHING
            """, DUBAI_BRANCH_ID, RIYADH_TENANT_ID);
        tenantTimeZoneService.evictTenant(RIYADH_TENANT_ID);
        tenantTimeZoneService.evictTenant(CAIRO_TENANT_ID);
        tenantTimeZoneService.evictBranch(INHERITING_BRANCH_ID);
        tenantTimeZoneService.evictBranch(DUBAI_BRANCH_ID);
    }

    /** Verification 1: the tenant's own zone is what resolves, not the JVM's. */
    @Test
    void resolvesTheTenantsOwnZone() {
        assertThat(tenantTimeZoneService.zoneFor(RIYADH_TENANT_ID)).isEqualTo(RIYADH);
        assertThat(tenantTimeZoneService.zoneFor(CAIRO_TENANT_ID))
            .isEqualTo(ZoneId.of("Africa/Cairo"));
    }

    /** Verification 3: a branch override wins over its tenant's zone. */
    @Test
    void branchOverrideWinsOverTenantZone() {
        assertThat(tenantTimeZoneService.zoneFor(RIYADH_TENANT_ID, DUBAI_BRANCH_ID))
            .isEqualTo(DUBAI);
    }

    /** Verification 3, other half: a null override inherits rather than falling back to anything. */
    @Test
    void branchWithoutOverrideInheritsTheTenantZone() {
        assertThat(tenantTimeZoneService.zoneFor(RIYADH_TENANT_ID, INHERITING_BRANCH_ID))
            .isEqualTo(RIYADH);
    }

    /** Verification 4: no tenant means no zone — never a silent default. */
    @Test
    void unknownTenantThrowsRatherThanDefaulting() {
        assertThatThrownBy(() -> tenantTimeZoneService.zoneFor(-1L))
            .isInstanceOf(AppException.class);
        assertThatThrownBy(() -> tenantTimeZoneService.zoneFor(null))
            .isInstanceOf(AppException.class);
    }

    /**
     * Verification 1/5: the audit listener stamps in the tenant's zone, taking the tenant off the
     * row rather than from any ambient context — which is exactly why the batching scheduler needs
     * no request context to write a correct timestamp.
     */
    @Test
    void auditTimestampIsStampedInTheOwningTenantsZone() {
        LocalDateTime beforeInRiyadh = LocalDateTime.now(RIYADH);

        Branch branch = new Branch();
        branch.setTenantId(RIYADH_TENANT_ID);
        branch.setName("Stamped Branch");
        branch.setCode("TZ-BR-3");
        branch.setActive(true);
        Branch saved = branchRepository.saveAndFlush(branch);

        LocalDateTime afterInRiyadh = LocalDateTime.now(RIYADH);

        assertThat(saved.getCreatedAt())
            .isBetween(beforeInRiyadh.minusSeconds(5), afterInRiyadh.plusSeconds(5));
    }

    /**
     * Verification 4: a tenant-scoped row that reaches persist with no tenantId must fail loudly.
     * Unreachable through the services, which all set it — asserted anyway, because the whole point
     * of the listener is that it never guesses.
     */
    @Test
    void persistingATenantRowWithoutATenantIdThrows() {
        Branch orphan = new Branch();
        orphan.setName("Orphan Branch");
        orphan.setCode("TZ-BR-4");
        orphan.setActive(true);

        assertThatThrownBy(() -> branchRepository.saveAndFlush(orphan))
            .isInstanceOf(com.smart.restaurant_saas.common.BusinessException.class)
            .hasMessageContaining("no tenantId");
    }
}
