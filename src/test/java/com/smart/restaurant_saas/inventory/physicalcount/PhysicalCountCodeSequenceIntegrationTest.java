package com.smart.restaurant_saas.inventory.physicalcount;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
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

@SpringBootTest
class PhysicalCountCodeSequenceIntegrationTest {

    private static final Long TENANT_ID = 993_001L;
    private static final Long WAREHOUSE_ID = 993_101L;
    private static final LocalDate SCHEDULED_DATE = LocalDate.of(2026, 8, 1);

    @Autowired
    private PhysicalCountCodeSequenceService sequenceService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void clearCounters() {
        jdbcTemplate.update(
            "DELETE FROM physical_count_code_sequence WHERE tenant_id = ?", TENANT_ID);
    }

    @Test
    void sequenceIncrementsWithinWarehouseDayAndRestartsForAnotherScope() {
        assertThat(sequenceService.next(TENANT_ID, WAREHOUSE_ID, SCHEDULED_DATE)).isEqualTo(1);
        assertThat(sequenceService.next(TENANT_ID, WAREHOUSE_ID, SCHEDULED_DATE)).isEqualTo(2);
        assertThat(sequenceService.next(TENANT_ID, WAREHOUSE_ID + 1, SCHEDULED_DATE)).isEqualTo(1);
        assertThat(sequenceService.next(TENANT_ID, WAREHOUSE_ID, SCHEDULED_DATE.plusDays(1)))
            .isEqualTo(1);
    }

    @Test
    void concurrentFirstAllocationsAreUniqueAndGapFree() throws Exception {
        int allocationCount = 8;
        CountDownLatch ready = new CountDownLatch(allocationCount);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(allocationCount)) {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < allocationCount; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return sequenceService.next(TENANT_ID, WAREHOUSE_ID, SCHEDULED_DATE);
                }));
            }
            ready.await();
            start.countDown();

            List<Integer> allocated = new ArrayList<>();
            for (Future<Integer> future : futures) {
                allocated.add(future.get());
            }
            assertThat(allocated).containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6, 7, 8);
        }
    }
}
