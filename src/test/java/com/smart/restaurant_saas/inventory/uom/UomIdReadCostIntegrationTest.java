package com.smart.restaurant_saas.inventory.uom;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Establishes what it actually costs to read a {@code uomId} off a lazy association, which is the
 * fact the whole D111 phase-3 join question turns on (O41).
 *
 * <p>The claim under test is that {@code uom.getId()} on an uninitialized proxy hits the database,
 * because {@link Uom} maps its {@code @Id} by field access and Hibernate can only short-circuit the
 * identifier getter under property access. If that is true, dropping the fetch joins while the
 * mappers still call {@code getId()} turns one join into a SELECT per distinct unit. It is far too
 * load-bearing a claim to take on reading — hence a measurement.
 */
@SpringBootTest
@Transactional
class UomIdReadCostIntegrationTest {

    private static final Long TENANT_ID = 977_300L;
    private static final Long UOM_ID = 977_301L;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM uom WHERE id = ?", UOM_ID);
        jdbcTemplate.update("""
            INSERT INTO tenants (id, name, code, status, created_at, timezone)
            VALUES (?, 'Uom Id Cost Tenant', 'UOM_IDCOST_TENANT', 'ACTIVE', CURRENT_TIMESTAMP,
                    'Africa/Cairo')
            ON CONFLICT (id) DO NOTHING
            """, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO uom (id, tenant_id, base_uom_id, code, name, name_ar, symbol, symbol_ar,
                             type, factor_to_base, entered_factor, active, created_at, updated_at)
            VALUES (?, ?, NULL, 'IDCOST_SEED', 'Id Cost Seed', 'تكلفة', 'ic', 'ت-ك', 'COUNT',
                    1, 1, TRUE, TIMESTAMP '2026-01-01 08:00:00', NULL)
            """, UOM_ID, TENANT_ID);

        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        entityManager.flush();
        entityManager.clear();
    }

    @AfterEach
    void tearDown() {
        if (statistics != null) {
            statistics.setStatisticsEnabled(false);
        }
        jdbcTemplate.update("DELETE FROM uom WHERE id = ?", UOM_ID);
    }

    @Test
    @DisplayName("getId() on an uninitialized Uom proxy is free and leaves it uninitialized")
    void gettingTheIdOffAProxyCostsNothing() {
        Uom proxy = entityManager.getReference(Uom.class, UOM_ID);
        assertThat(Hibernate.isInitialized(proxy))
            .as("getReference must hand back an uninitialized proxy, or this test proves nothing")
            .isFalse();

        statistics.clear();
        Long id = proxy.getId();

        assertThat(id).isEqualTo(UOM_ID);
        assertThat(statistics.getPrepareStatementCount())
            .as("""
                Hibernate short-circuits the identifier read on a proxy, so a mapper that emits \
                only uomId never needs the association loaded. This is what makes dropping the \
                D111 fetch joins safe rather than a per-row SELECT (O41). If this ever becomes 1, \
                the joins have to come back.""")
            .isZero();
        assertThat(Hibernate.isInitialized(proxy))
            .as("reading the id must not have triggered a load")
            .isFalse();
    }

    @Test
    @DisplayName("reading any other field does initialize the proxy")
    void gettingASymbolOffAProxyCostsAQuery() {
        Uom proxy = entityManager.getReference(Uom.class, UOM_ID);
        statistics.clear();

        assertThat(proxy.getSymbol()).isEqualTo("ic");

        assertThat(statistics.getPrepareStatementCount())
            .as("the contrast that makes the id case meaningful: uomSymbol was the expensive read")
            .isEqualTo(1);
    }

    @Test
    @DisplayName("reading the id off an already-loaded Uom is free")
    void gettingTheIdOffALoadedEntityIsFree() {
        Uom loaded = entityManager.find(Uom.class, UOM_ID);
        assertThat(Hibernate.isInitialized(loaded)).isTrue();

        statistics.clear();
        assertThat(loaded.getId()).isEqualTo(UOM_ID);

        assertThat(statistics.getPrepareStatementCount())
            .as("this is why the fetch joins are load-bearing: they make getId() free")
            .isZero();
    }
}
