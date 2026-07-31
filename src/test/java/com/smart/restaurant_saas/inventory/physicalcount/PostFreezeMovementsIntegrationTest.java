package com.smart.restaurant_saas.inventory.physicalcount;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.restaurant_saas.inventory.core.PhysicalCountService;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PostFreezeMaterialMovementResponse;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PostFreezeMovementsResponse;
import com.smart.restaurant_saas.inventory.repository.InventoryTransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * The post-freeze movement summary is entirely JPQL (grouping plus directional CASE/SUM), so it is
 * verified against real Postgres. Seeded ids are in a dedicated high range and the test is
 * transactional, so nothing survives the run.
 */
@SpringBootTest
@Transactional
class PostFreezeMovementsIntegrationTest {

    private static final Long TENANT_ID = 994_001L;
    private static final Long BRANCH_ID = 994_101L;
    private static final Long UOM_ID = 994_201L;
    private static final Long CATEGORY_ID = 994_301L;
    private static final Long WAREHOUSE_ID = 994_401L;
    private static final Long OTHER_WAREHOUSE_ID = 994_402L;

    private static final Long COUNTED_MATERIAL_ID = 994_501L;
    private static final Long UNCOUNTED_MATERIAL_ID = 994_502L;

    private static final Long COUNT_ID = 994_601L;
    private static final LocalDateTime FROZEN_AT = LocalDateTime.of(2026, 6, 30, 9, 30);

    @Autowired
    private PhysicalCountService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private InventoryTransactionRepository transactionRepository;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("""
            INSERT INTO tenants (id, name, code, status, created_at)
            VALUES (?, 'Post Freeze Tenant', 'POST_FREEZE_MOVEMENTS', 'ACTIVE', CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, TENANT_ID);

        jdbcTemplate.update("""
            INSERT INTO branches (id, tenant_id, name, code, is_active, created_at)
            VALUES (?, ?, 'Main Branch', 'PFM-BR-1', TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, BRANCH_ID, TENANT_ID);

        jdbcTemplate.update("""
            INSERT INTO uom (id, tenant_id, code, name, symbol, type, factor_to_base, active, created_at)
            VALUES (?, ?, 'PFM-KG', 'Kilogram', 'kg', 'WEIGHT', 1, TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, UOM_ID, TENANT_ID);

        jdbcTemplate.update("""
            INSERT INTO material_category (id, tenant_id, code, name, name_ar, active, created_at)
            VALUES (?, ?, 'PFM-VEG', 'Vegetables', 'خضروات', TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, CATEGORY_ID, TENANT_ID);

        insertWarehouse(WAREHOUSE_ID, "PFM-WH-1", "Main Warehouse");
        insertWarehouse(OTHER_WAREHOUSE_ID, "PFM-WH-2", "Other Warehouse");

        insertMaterial(COUNTED_MATERIAL_ID, "PFM-MAT-1", "Tomato", "طماطم");
        insertMaterial(UNCOUNTED_MATERIAL_ID, "PFM-MAT-2", "Onion", "بصل");

        // A frozen count that contains only COUNTED_MATERIAL.
        jdbcTemplate.update("""
            INSERT INTO physical_count (id, tenant_id, warehouse_id, code, status, scheduled_date,
                                        started_at, frozen_at, has_large_variance, created_at)
            VALUES (?, ?, ?, 'PC-PFM-1', 'IN_PROGRESS', DATE '2026-06-30', ?, ?, FALSE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, COUNT_ID, TENANT_ID, WAREHOUSE_ID, FROZEN_AT, FROZEN_AT);

        jdbcTemplate.update("""
            INSERT INTO physical_count_line (id, tenant_id, physical_count_id, material_id, uom_id,
                                             expected_quantity, unit_cost_at_freeze, action_taken,
                                             created_at)
            VALUES (?, ?, ?, ?, ?, 10, 5, 'PENDING', CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, 994_701L, TENANT_ID, COUNT_ID, COUNTED_MATERIAL_ID, UOM_ID);

        // Excluded: dated exactly AT the cutoff — this is how the count's own corrections are dated.
        insertMovement(994_801L, WAREHOUSE_ID, COUNTED_MATERIAL_ID, "OUT", "1", FROZEN_AT);
        // Excluded: before the cutoff.
        insertMovement(994_802L, WAREHOUSE_ID, COUNTED_MATERIAL_ID, "IN", "100",
            FROZEN_AT.minusDays(1));
        // Excluded: a different warehouse.
        insertMovement(994_803L, OTHER_WAREHOUSE_ID, COUNTED_MATERIAL_ID, "IN", "50",
            FROZEN_AT.plusHours(1));

        // Reported, and in the breakdown: two INs and one OUT for a counted material.
        insertMovement(994_804L, WAREHOUSE_ID, COUNTED_MATERIAL_ID, "IN", "4",
            FROZEN_AT.plusHours(1));
        // Entered as one non-stock unit but converted by the ledger to six stock units.
        insertMovement(994_805L, WAREHOUSE_ID, COUNTED_MATERIAL_ID, "IN", "1", "6",
            FROZEN_AT.plusHours(2));
        insertMovement(994_806L, WAREHOUSE_ID, COUNTED_MATERIAL_ID, "OUT", "3",
            FROZEN_AT.plusHours(3));
        insertMovement(994_808L, WAREHOUSE_ID, COUNTED_MATERIAL_ID, "OUT", "99",
            FROZEN_AT.plusMinutes(90), "PHYSICAL_COUNT", COUNT_ID);

        // Reported in the totals only: this material is not in the count document.
        insertMovement(994_807L, WAREHOUSE_ID, UNCOUNTED_MATERIAL_ID, "OUT", "7",
            FROZEN_AT.plusHours(4));
    }

    @Test
    void totalsSpanTheWarehouseWhileTheBreakdownIsLimitedToTheCountedMaterials() {
        PostFreezeMovementsResponse response = service.findPostFreezeMovements(COUNT_ID, TENANT_ID);

        assertThat(response.getCountId()).isEqualTo(COUNT_ID);
        assertThat(response.getWarehouseId()).isEqualTo(WAREHOUSE_ID);
        assertThat(response.getFrozenAt()).isEqualTo(FROZEN_AT);
        // Four movements across two materials: the three counted ones plus the uncounted one.
        assertThat(response.getTotalMovementCount()).isEqualTo(4);
        assertThat(response.getAffectedMaterialCount()).isEqualTo(2);
        assertThat(response.getMaterials())
            .extracting(PostFreezeMaterialMovementResponse::getMaterialId)
            .containsExactly(COUNTED_MATERIAL_ID);
    }

    @Test
    void aggregatesQuantitiesByDirectionForEachCountedMaterial() {
        PostFreezeMaterialMovementResponse movement =
            service.findPostFreezeMovements(COUNT_ID, TENANT_ID).getMaterials().getFirst();

        assertThat(movement.getMovementCount()).isEqualTo(3);
        assertThat(movement.getQuantityIn()).isEqualByComparingTo("10.000000");
        assertThat(movement.getQuantityOut()).isEqualByComparingTo("3.000000");
        assertThat(movement.getNetQuantity()).isEqualByComparingTo("7.000000");
        assertThat(movement.getMaterialName()).isEqualTo("Tomato");
        assertThat(movement.getMaterialNameAr()).isEqualTo("طماطم");
    }

    @Test
    void reportingIsReadOnlyAndLeavesTheFrozenExpectedQuantityUntouched() {
        service.findPostFreezeMovements(COUNT_ID, TENANT_ID);

        BigDecimal expectedQuantity = jdbcTemplate.queryForObject(
            "SELECT expected_quantity FROM physical_count_line WHERE id = ?",
            BigDecimal.class, 994_701L);
        assertThat(expectedQuantity).isEqualByComparingTo("10.000000");
    }

    @Test
    void countWindowRowsUseSignedStockQuantityAndExactBoundariesWhileExcludingOwnMovement() {
        var rows = transactionRepository.findPhysicalCountMovements(
            TENANT_ID,
            WAREHOUSE_ID,
            java.util.List.of(COUNTED_MATERIAL_ID),
            FROZEN_AT,
            FROZEN_AT.plusHours(2),
            COUNT_ID);

        assertThat(rows).hasSize(2);
        assertThat(rows).extracting(PhysicalCountMovementRow::materialId)
            .containsOnly(COUNTED_MATERIAL_ID);
        assertThat(rows).extracting(PhysicalCountMovementRow::signedStockQuantity)
            .usingElementComparator(BigDecimal::compareTo)
            .containsExactly(new BigDecimal("4.000000"), new BigDecimal("6.000000"));
        assertThat(rows).extracting(PhysicalCountMovementRow::movementDate)
            .containsExactly(FROZEN_AT.plusHours(1), FROZEN_AT.plusHours(2));
    }

    private void insertWarehouse(Long id, String code, String name) {
        jdbcTemplate.update("""
            INSERT INTO warehouse (id, tenant_id, branch_id, code, name, type, active, created_at)
            VALUES (?, ?, ?, ?, ?, 'CENTRAL', TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, id, TENANT_ID, BRANCH_ID, code, name);
    }

    private void insertMaterial(Long id, String code, String name, String nameAr) {
        jdbcTemplate.update("""
            INSERT INTO material (id, tenant_id, category_id, stock_uom_id, display_uom_id,
                                  code, name, name_ar, active, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, id, TENANT_ID, CATEGORY_ID, UOM_ID, UOM_ID, code, name, nameAr);
    }

    private void insertMovement(Long id, Long warehouseId, Long materialId,
                                String direction, String quantity, LocalDateTime movementDate) {
        insertMovement(id, warehouseId, materialId, direction, quantity, quantity, movementDate);
    }

    private void insertMovement(Long id, Long warehouseId, Long materialId,
                                String direction, String enteredQuantity, String stockQuantity,
                                LocalDateTime movementDate) {
        insertMovement(id, warehouseId, materialId, direction, enteredQuantity, stockQuantity,
            movementDate, null, null);
    }

    private void insertMovement(Long id, Long warehouseId, Long materialId,
                                String direction, String quantity, LocalDateTime movementDate,
                                String referenceType, Long referenceId) {
        insertMovement(id, warehouseId, materialId, direction, quantity, quantity, movementDate,
            referenceType, referenceId);
    }

    private void insertMovement(Long id, Long warehouseId, Long materialId,
                                String direction, String enteredQuantity, String stockQuantity,
                                LocalDateTime movementDate, String referenceType, Long referenceId) {
        jdbcTemplate.update("""
            INSERT INTO inventory_transaction (id, tenant_id, warehouse_id, material_id,
                                               transaction_type, direction, entered_quantity,
                                               entered_uom_id, stock_quantity, stock_uom_id,
                                               transaction_date, movement_date, reference_type,
                                               reference_id, created_at)
            VALUES (?, ?, ?, ?, 'PURCHASE', ?, CAST(? AS numeric), ?, CAST(? AS numeric), ?,
                    CURRENT_TIMESTAMP, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, id, TENANT_ID, warehouseId, materialId, direction, enteredQuantity, UOM_ID,
            stockQuantity, UOM_ID, movementDate, referenceType, referenceId);
    }
}
