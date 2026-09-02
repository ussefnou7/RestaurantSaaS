package com.smart.restaurant_saas.inventory.core;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.smart.restaurant_saas.inventory.core.enums.DocumentType;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PhysicalCountRequest;
import com.smart.restaurant_saas.tenant.TenantTimeZoneService;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * D112: the production allocator must release its counter row before its caller continues work.
 *
 * <p>The first real {@link PhysicalCountService#create} is paused immediately after allocation.
 * The second create must still allocate and persist while the first caller transaction is open.
 * That distinguishes {@code REQUIRES_NEW} from the old default propagation: under
 * {@code REQUIRED}, the first caller still owns the counter-row lock and the second allocation is
 * cancelled by the configured five-second {@code lock_timeout}.
 */
@SpringBootTest
@Import(DocumentSequencePropagationIntegrationTest.BlockingAllocatorConfiguration.class)
class DocumentSequencePropagationIntegrationTest {

    private static final Long TENANT_ID = 994_001L;
    private static final Long BRANCH_ID = 994_101L;
    private static final Long UOM_ID = 994_201L;
    private static final Long CATEGORY_ID = 994_301L;
    private static final Long WAREHOUSE_ID = 994_401L;
    private static final Long MATERIAL_ID = 994_501L;

    @Autowired
    private PhysicalCountService physicalCountService;

    @Autowired
    private BlockingDocumentSequenceService blockingAllocator;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        cleanUp();
        jdbcTemplate.update("""
            INSERT INTO tenants (id, name, code, status, created_at, timezone)
            VALUES (?, 'Propagation Tenant', 'PROP', 'ACTIVE', CURRENT_TIMESTAMP, 'Africa/Cairo')
            """, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO branches (id, tenant_id, name, code, is_active, created_at)
            VALUES (?, ?, 'Propagation Branch', 'PROP-BR', TRUE, CURRENT_TIMESTAMP)
            """, BRANCH_ID, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO uom (id, tenant_id, code, name, symbol, type, factor_to_base, entered_factor,
                             active, created_at)
            VALUES (?, ?, 'PROP-KG', 'Kilogram', 'kg', 'WEIGHT', 1, 1, TRUE, CURRENT_TIMESTAMP)
            """, UOM_ID, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO material_category (id, tenant_id, code, name, active, created_at)
            VALUES (?, ?, 'PROP-FOOD', 'Food', TRUE, CURRENT_TIMESTAMP)
            """, CATEGORY_ID, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO warehouse (id, tenant_id, branch_id, code, name, type, active, created_at)
            VALUES (?, ?, ?, 'PROP-WH', 'Propagation Warehouse', 'CENTRAL', TRUE, CURRENT_TIMESTAMP)
            """, WAREHOUSE_ID, TENANT_ID, BRANCH_ID);
        jdbcTemplate.update("""
            INSERT INTO material (id, tenant_id, category_id, stock_uom_id, display_uom_id,
                                  code, name, active, created_at)
            VALUES (?, ?, ?, ?, ?, 'PROP-MAT', 'Propagation Material', TRUE, CURRENT_TIMESTAMP)
            """, MATERIAL_ID, TENANT_ID, CATEGORY_ID, UOM_ID, UOM_ID);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("DELETE FROM physical_count_line WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM physical_count WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM document_sequence WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM material WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM warehouse WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM branches WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM material_category WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM uom WHERE tenant_id = ?", TENANT_ID);
        jdbcTemplate.update("DELETE FROM tenants WHERE id = ?", TENANT_ID);
    }

    @Test
    void concurrentSpringWiredCreatesDoNotHoldCounterLockThroughCallerWork() throws Exception {
        PhysicalCountRequest request = new PhysicalCountRequest();
        request.setWarehouseId(WAREHOUSE_ID);
        request.setScheduledDate(LocalDate.of(2026, 9, 2));
        request.setMaterialIds(List.of(MATERIAL_ID));
        blockingAllocator.arm();

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<String> first = executor.submit(
                () -> physicalCountService.create(request, TENANT_ID, 77L).getCode());
            blockingAllocator.awaitFirstAllocation();

            Future<String> second = executor.submit(
                () -> physicalCountService.create(request, TENANT_ID, 88L).getCode());

            String secondCode;
            try {
                secondCode = second.get(8, SECONDS);
            } finally {
                blockingAllocator.releaseFirstCaller();
            }
            String firstCode = first.get(8, SECONDS);

            assertThat(List.of(firstCode, secondCode))
                .containsExactlyInAnyOrder("PC/26/000001", "PC/26/000002");
            assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM physical_count WHERE tenant_id = ?", Integer.class, TENANT_ID))
                .isEqualTo(2);
            assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM physical_count_line WHERE tenant_id = ?", Integer.class, TENANT_ID))
                .isEqualTo(2);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class BlockingAllocatorConfiguration {

        @Bean
        @Primary
        BlockingDocumentSequenceService blockingDocumentSequenceService(
                @Qualifier("documentSequenceService") DocumentSequenceService delegate,
                JdbcTemplate jdbcTemplate,
                TenantTimeZoneService tenantTimeZoneService) {
            return new BlockingDocumentSequenceService(
                jdbcTemplate, tenantTimeZoneService, delegate);
        }
    }

    static class BlockingDocumentSequenceService extends DocumentSequenceService {

        private final DocumentSequenceService delegate;
        private final AtomicBoolean pauseNext = new AtomicBoolean();
        private CountDownLatch firstAllocated;
        private CountDownLatch releaseFirstCaller;

        BlockingDocumentSequenceService(JdbcTemplate jdbcTemplate,
                                        TenantTimeZoneService tenantTimeZoneService,
                                        DocumentSequenceService delegate) {
            super(jdbcTemplate, tenantTimeZoneService);
            this.delegate = delegate;
        }

        void arm() {
            pauseNext.set(true);
            firstAllocated = new CountDownLatch(1);
            releaseFirstCaller = new CountDownLatch(1);
        }

        void awaitFirstAllocation() throws InterruptedException {
            assertThat(firstAllocated.await(8, SECONDS)).isTrue();
        }

        void releaseFirstCaller() {
            releaseFirstCaller.countDown();
        }

        @Override
        public String next(Long tenantId, DocumentType documentType) {
            String allocated = delegate.next(tenantId, documentType);
            if (TENANT_ID.equals(tenantId)
                    && documentType == DocumentType.PHYSICAL_COUNT
                    && pauseNext.compareAndSet(true, false)) {
                firstAllocated.countDown();
                try {
                    if (!releaseFirstCaller.await(15, SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to release first create");
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Interrupted while pausing first create", ex);
                }
            }
            return allocated;
        }
    }
}
