package com.smart.restaurant_saas.inventory.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.restaurant_saas.common.TestZones;
import com.smart.restaurant_saas.inventory.core.enums.DocumentType;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * D112: the shared document-number allocator.
 *
 * <p>Deliberately <b>not</b> {@code @Transactional}. The concurrency test needs each allocation to
 * commit independently; a test-managed transaction would serialise every thread onto one connection
 * and prove nothing about the upsert. Rows are cleaned up explicitly instead.
 *
 * <p>This test covers the SQL upsert only; it does not cover transaction propagation or lock
 * lifetime through a Spring-wired production caller. The subject is constructed directly rather
 * than autowired, using the package-private
 * {@code Clock} constructor. Autowiring would bind the real {@code TenantTimeZoneService}, which
 * resolves a zone by reading {@code branch}/{@code tenant} and throws for a tenant that has none —
 * so a test tenant would need a full row fixture merely to obtain a year. Pinning the zone with
 * {@link TestZones}, the existing convention for this, keeps the year deterministic and the fixture
 * empty. Note this means Spring's {@code @Transactional} proxy is absent and each allocation
 * autocommits; the atomicity under test belongs to the SQL upsert, not to the proxy.
 */
@SpringBootTest
class DocumentSequenceIntegrationTest {

    private static final Long TENANT_ID = 995_001L;
    private static final Long OTHER_TENANT_ID = 995_002L;
    private static final Long KIRITIMATI_TENANT_ID = 995_003L;
    private static final Long MIDWAY_TENANT_ID = 995_004L;

    /** Fixed so the formatted year is an assertion, not whatever year the suite happens to run in. */
    private static final Clock CLOCK_2026 =
        Clock.fixed(Instant.parse("2026-06-15T12:00:00Z"), ZoneOffset.UTC);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private DocumentSequenceService service;

    private DocumentSequenceService serviceAt(ZoneId zone, Clock clock) {
        return new DocumentSequenceService(jdbcTemplate, TestZones.fixedAt(zone), clock);
    }

    @BeforeEach
    @AfterEach
    void clearCounters() {
        jdbcTemplate.update(
            "DELETE FROM document_sequence WHERE tenant_id IN (?, ?, ?, ?)",
            TENANT_ID, OTHER_TENANT_ID, KIRITIMATI_TENANT_ID, MIDWAY_TENANT_ID);
        service = serviceAt(TestZones.CAIRO, CLOCK_2026);
    }

    @Test
    void everyDocumentTypeStartsAtOneAndFormatsWithItsOwnPrefix() {
        assertThat(service.next(TENANT_ID, DocumentType.PURCHASE_INVOICE)).isEqualTo("PI/26/000001");
        assertThat(service.next(TENANT_ID, DocumentType.PHYSICAL_COUNT)).isEqualTo("PC/26/000001");
        assertThat(service.next(TENANT_ID, DocumentType.WASTE)).isEqualTo("WS/26/000001");
        assertThat(service.next(TENANT_ID, DocumentType.PURCHASE_RETURN)).isEqualTo("PR/26/000001");
    }

    @Test
    void countersAreIndependentPerTypeAndPerTenant() {
        assertThat(service.next(TENANT_ID, DocumentType.WASTE)).isEqualTo("WS/26/000001");
        assertThat(service.next(TENANT_ID, DocumentType.WASTE)).isEqualTo("WS/26/000002");

        // A different type under the same tenant is a different counter, not a continuation.
        assertThat(service.next(TENANT_ID, DocumentType.PURCHASE_INVOICE)).isEqualTo("PI/26/000001");
        // And a different tenant under the same type restarts.
        assertThat(service.next(OTHER_TENANT_ID, DocumentType.WASTE)).isEqualTo("WS/26/000001");
    }

    @Test
    void yearComesFromTheTenantWallClockNotTheServerClock() {
        // One instant, one server clock, two tenants whose wall clocks fall in different years.
        Clock newYearEve = Clock.fixed(Instant.parse("2026-12-31T23:00:00Z"), ZoneOffset.UTC);
        assertThat(newYearEve.instant().atZone(ZoneOffset.UTC).getYear())
            .as("server-side year at this instant")
            .isEqualTo(2026);

        // Kiritimati is UTC+14, so it is already 2027-01-01 there.
        DocumentSequenceService kiritimati =
            serviceAt(ZoneId.of("Pacific/Kiritimati"), newYearEve);
        // Midway is UTC-11, so it is still 2026-12-31 there.
        DocumentSequenceService midway = serviceAt(ZoneId.of("Pacific/Midway"), newYearEve);

        assertThat(kiritimati.next(KIRITIMATI_TENANT_ID, DocumentType.PURCHASE_INVOICE))
            .as("tenant ahead of UTC has rolled into the new year")
            .isEqualTo("PI/27/000001");
        assertThat(midway.next(MIDWAY_TENANT_ID, DocumentType.PURCHASE_INVOICE))
            .as("tenant behind UTC has not")
            .isEqualTo("PI/26/000001");

        assertThat(jdbcTemplate.queryForObject(
            "SELECT year FROM document_sequence WHERE tenant_id = ? AND document_type = ?",
            Integer.class, KIRITIMATI_TENANT_ID, DocumentType.PURCHASE_INVOICE.name()))
            .isEqualTo(2027);
    }

    @Test
    void concurrentAllocationsInOneScopeAreUniqueAndSequential() throws Exception {
        int threads = 8;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return service.next(TENANT_ID, DocumentType.PURCHASE_INVOICE);
                }));
            }
            ready.await();
            start.countDown();

            List<String> allocated = new ArrayList<>();
            for (Future<String> future : futures) {
                allocated.add(future.get());
            }

            // No duplicate and no gap: the upsert handed out 1..8 exactly once each.
            assertThat(allocated).containsExactlyInAnyOrder(
                "PI/26/000001", "PI/26/000002", "PI/26/000003", "PI/26/000004",
                "PI/26/000005", "PI/26/000006", "PI/26/000007", "PI/26/000008");
            assertThat(allocated).doesNotHaveDuplicates();
        }
    }

    @Test
    void sequenceIsNotClampedAtSixDigits() {
        service.next(TENANT_ID, DocumentType.PURCHASE_INVOICE);
        jdbcTemplate.update(
            "UPDATE document_sequence SET seq = 999999 "
                + "WHERE tenant_id = ? AND document_type = ? AND year = 2026",
            TENANT_ID, DocumentType.PURCHASE_INVOICE.name());

        // D112 accepts the width growing rather than wrapping or clamping.
        assertThat(service.next(TENANT_ID, DocumentType.PURCHASE_INVOICE))
            .isEqualTo("PI/26/1000000");
    }

    @Test
    void aDiscardedNumberLeavesAGapThatIsNotReused() {
        String first = service.next(TENANT_ID, DocumentType.WASTE);
        String discarded = service.next(TENANT_ID, DocumentType.WASTE);
        String third = service.next(TENANT_ID, DocumentType.WASTE);

        assertThat(first).isEqualTo("WS/26/000001");
        assertThat(discarded).isEqualTo("WS/26/000002");
        assertThat(third).isEqualTo("WS/26/000003");

        // The allocator has no notion of returning a number: whatever happened to the document
        // that held WS/26/000002 — deleted as a DRAFT, rolled back — the counter does not rewind.
        assertThat(service.next(TENANT_ID, DocumentType.WASTE)).isEqualTo("WS/26/000004");
    }
}
