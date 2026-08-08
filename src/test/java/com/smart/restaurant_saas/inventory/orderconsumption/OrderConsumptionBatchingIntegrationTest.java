package com.smart.restaurant_saas.inventory.orderconsumption;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.restaurant_saas.inventory.core.enums.WarehouseType;
import com.smart.restaurant_saas.inventory.repository.WarehouseRepository;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * D58 correctness proof against a real database. Not {@code @Transactional} — the claim must
 * genuinely COMMIT for the assertions to mean anything. The batching scheduler is disabled so it
 * cannot race the fixtures.
 */
@SpringBootTest
@TestPropertySource(properties = "order-consumption.batching.enabled=false")
class OrderConsumptionBatchingIntegrationTest {

    private static final Long TENANT_ID = 0L; // seeded "System" tenant (V4).

    @Autowired
    private OrderConsumptionService service;
    @Autowired
    private OrderConsumptionRepository docRepository;
    @Autowired
    private WarehouseRepository warehouseRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private final List<Long> createdDocIds = new ArrayList<>();
    private final List<Long> createdWarehouseIds = new ArrayList<>();

    @AfterEach
    void cleanup() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            createdDocIds.forEach(id -> jdbcTemplate.update("DELETE FROM order_consumption WHERE id = ?", id));
            createdWarehouseIds.forEach(id -> jdbcTemplate.update("DELETE FROM warehouse WHERE id = ?", id));
        });
    }

    @Test
    void claimCommitsInProgressBeforeProcessingSoConcurrentCompletionsBranchToNewDoc() {
        Long warehouseId = createWarehouse();
        Long docId = createPendingDoc(warehouseId);

        boolean claimed = service.claimDoc(docId, null);

        // Fresh reads outside the claim transaction: the IN_PROGRESS transition is already committed,
        // and no PENDING doc remains for the warehouse — so a concurrent order completion (which looks
        // up the PENDING doc) would create a NEW pending doc instead of racing into this one.
        assertThat(claimed).isTrue();
        assertThat(docRepository.findById(docId).orElseThrow().getStatus())
            .isEqualTo(OrderConsumptionStatus.IN_PROGRESS);
        assertThat(docRepository.findByTenantIdAndWarehouseIdAndStatus(
            TENANT_ID, warehouseId, OrderConsumptionStatus.PENDING)).isEmpty();
    }

    @Test
    void timeoutTriggerReturnsDocsOlderThanMaxAgeAndExcludesFreshOnes() {
        Long warehouseId = createWarehouse();
        Long oldDocId = createPendingDoc(warehouseId);
        ageDoc(oldDocId, LocalDateTime.now().minusHours(9));

        Long freshWarehouseId = createWarehouse();
        Long freshDocId = createPendingDoc(freshWarehouseId);

        List<Long> ready = docIdsOf(docRepository.findBatchingCandidates(
            OrderConsumptionStatus.PENDING, LocalDateTime.now().minusHours(8), 50L));

        assertThat(ready).contains(oldDocId).doesNotContain(freshDocId);
    }

    private static List<Long> docIdsOf(List<BatchingCandidate> candidates) {
        return candidates.stream().map(BatchingCandidate::id).toList();
    }

    @Test
    void countTriggerBranchIncludesADocByLineCountIndependentOfAge() {
        Long warehouseId = createWarehouse();
        Long freshDocId = createPendingDoc(warehouseId); // fresh (age condition false)

        // threshold 0 isolates the count OR-branch: COUNT(lines) >= 0 is satisfied, so the doc is
        // returned purely by the line-count condition even though it is not old.
        List<Long> byCount = docIdsOf(docRepository.findBatchingCandidates(
            OrderConsumptionStatus.PENDING, LocalDateTime.now().minusHours(8), 0L));
        assertThat(byCount).contains(freshDocId);

        // With a high threshold and no lines, neither branch fires for the fresh doc.
        List<Long> neither = docIdsOf(docRepository.findBatchingCandidates(
            OrderConsumptionStatus.PENDING, LocalDateTime.now().minusHours(8), 50L));
        assertThat(neither).doesNotContain(freshDocId);
    }

    private Long createWarehouse() {
        Warehouse warehouse = new Warehouse();
        warehouse.setTenantId(TENANT_ID);
        warehouse.setCode("OC-BATCH-" + System.nanoTime());
        warehouse.setName("Batching Test WH");
        warehouse.setType(WarehouseType.CENTRAL);
        warehouse.setActive(true);
        Long id = warehouseRepository.save(warehouse).getId();
        createdWarehouseIds.add(id);
        return id;
    }

    private Long createPendingDoc(Long warehouseId) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId).orElseThrow();
        OrderConsumption doc = new OrderConsumption();
        doc.setTenantId(TENANT_ID);
        doc.setWarehouse(warehouse);
        doc.setStatus(OrderConsumptionStatus.PENDING);
        Long id = docRepository.save(doc).getId();
        createdDocIds.add(id);
        return id;
    }

    private void ageDoc(Long docId, LocalDateTime createdAt) {
        // created_at is JPA-immutable, so age it with native SQL.
        jdbcTemplate.update("UPDATE order_consumption SET created_at = ? WHERE id = ?", createdAt, docId);
    }
}
