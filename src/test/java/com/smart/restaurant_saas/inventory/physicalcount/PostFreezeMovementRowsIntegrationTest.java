package com.smart.restaurant_saas.inventory.physicalcount;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.restaurant_saas.inventory.core.PhysicalCountService;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionDirection;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PostFreezeMaterialMovementResponse;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PostFreezeMovementRowResponse;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PostFreezeMovementsResponse;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class PostFreezeMovementRowsIntegrationTest {

    private static final Long TENANT_ID = 997_001L;
    private static final Long BRANCH_ID = 997_101L;
    private static final Long KG_UOM_ID = 997_201L;
    private static final Long BAG_UOM_ID = 997_202L;
    private static final Long CATEGORY_ID = 997_301L;
    private static final Long WAREHOUSE_ID = 997_401L;
    private static final Long COUNTED_MATERIAL_ID = 997_501L;
    private static final Long UNCOUNTED_MATERIAL_ID = 997_502L;
    private static final Long WAREHOUSE_ONLY_MATERIAL_ID = 997_503L;
    private static final Long COUNT_ID = 997_601L;
    private static final Long RECONCILED_COUNT_ID = 997_602L;
    private static final Long INCLUDED_TRANSACTION_ID = 997_801L;
    private static final Long AFTER_COUNT_TRANSACTION_ID = 997_802L;
    private static final Long SOURCE_INVOICE_ID = 997_901L;

    private static final LocalDateTime FROZEN_AT = LocalDateTime.of(2026, 7, 31, 9, 30);
    private static final LocalDateTime COUNTED_AT = FROZEN_AT.plusHours(2);
    private static final LocalDateTime INCLUDED_MOVEMENT_DATE = FROZEN_AT.minusDays(1);
    private static final LocalDateTime INCLUDED_CREATED_AT = FROZEN_AT.plusHours(1);
    private static final LocalDateTime AFTER_COUNT_MOVEMENT_DATE = COUNTED_AT.plusHours(1);
    private static final LocalDateTime AFTER_COUNT_CREATED_AT = FROZEN_AT.plusMinutes(90);

    @Autowired
    private PhysicalCountService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("""
            INSERT INTO tenants (id, name, code, status, created_at, timezone)
            VALUES (?, 'Movement Row Tenant', 'POST_FREEZE_ROWS', 'ACTIVE', CURRENT_TIMESTAMP, 'Africa/Cairo')
            """, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO branches (id, tenant_id, name, code, is_active, created_at)
            VALUES (?, ?, 'Movement Row Branch', 'PFR-BR', TRUE, CURRENT_TIMESTAMP)
            """, BRANCH_ID, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO uom (id, tenant_id, code, name, symbol, type, factor_to_base, entered_factor, active, created_at)
            VALUES (?, ?, 'PFR-KG', 'Kilogram', 'kg', 'WEIGHT', 1, 1, TRUE, CURRENT_TIMESTAMP)
            """, KG_UOM_ID, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO uom (id, tenant_id, base_uom_id, code, name, symbol, type,
                             factor_to_base, entered_factor, active, created_at)
            VALUES (?, ?, ?, 'PFR-BAG', 'Five kilogram bag', 'bag', 'WEIGHT',
                    5, 5, TRUE, CURRENT_TIMESTAMP)
            """, BAG_UOM_ID, TENANT_ID, KG_UOM_ID);
        jdbcTemplate.update("""
            INSERT INTO material_category (id, tenant_id, code, name, active, created_at)
            VALUES (?, ?, 'PFR-CAT', 'Movement row materials', TRUE, CURRENT_TIMESTAMP)
            """, CATEGORY_ID, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO warehouse (id, tenant_id, branch_id, code, name, type, active, created_at)
            VALUES (?, ?, ?, 'PFR-WH', 'Movement Row Warehouse', 'CENTRAL', TRUE, CURRENT_TIMESTAMP)
            """, WAREHOUSE_ID, TENANT_ID, BRANCH_ID);

        insertMaterial(COUNTED_MATERIAL_ID, "PFR-MAT-1", "Flour", "دقيق", BAG_UOM_ID);
        insertMaterial(UNCOUNTED_MATERIAL_ID, "PFR-MAT-2", "Rice", "أرز", BAG_UOM_ID);
        insertMaterial(WAREHOUSE_ONLY_MATERIAL_ID, "PFR-MAT-3", "Salt", "ملح", KG_UOM_ID);

        jdbcTemplate.update("""
            INSERT INTO physical_count (id, tenant_id, warehouse_id, code, status, scheduled_date,
                                        started_at, frozen_at, has_large_variance, created_at)
            VALUES (?, ?, ?, 'PC-PFR-1', 'IN_PROGRESS', DATE '2026-07-31', ?, ?, FALSE, ?)
            """, COUNT_ID, TENANT_ID, WAREHOUSE_ID, FROZEN_AT, FROZEN_AT, FROZEN_AT);
        jdbcTemplate.update("""
            INSERT INTO physical_count_line (id, tenant_id, physical_count_id, material_id, uom_id,
                                             expected_quantity, counted_quantity, counted_at,
                                             unit_cost_at_freeze, action_taken, created_at)
            VALUES (?, ?, ?, ?, ?, 10, 10, ?, 5, 'PENDING', ?)
            """, 997_701L, TENANT_ID, COUNT_ID, COUNTED_MATERIAL_ID, BAG_UOM_ID,
            COUNTED_AT, FROZEN_AT);
        jdbcTemplate.update("""
            INSERT INTO physical_count_line (id, tenant_id, physical_count_id, material_id, uom_id,
                                             expected_quantity, unit_cost_at_freeze, action_taken,
                                             created_at)
            VALUES (?, ?, ?, ?, ?, 10, 5, 'PENDING', ?)
            """, 997_702L, TENANT_ID, COUNT_ID, UNCOUNTED_MATERIAL_ID, BAG_UOM_ID, FROZEN_AT);
        jdbcTemplate.update("""
            INSERT INTO physical_count (id, tenant_id, warehouse_id, code, status, scheduled_date,
                                        started_at, frozen_at, reconciled_at, has_large_variance,
                                        created_at)
            VALUES (?, ?, ?, 'PC-PFR-2', 'RECONCILED', DATE '2026-07-31', ?, ?, ?, FALSE, ?)
            """, RECONCILED_COUNT_ID, TENANT_ID, WAREHOUSE_ID, FROZEN_AT, FROZEN_AT,
            COUNTED_AT, FROZEN_AT);
        jdbcTemplate.update("""
            INSERT INTO physical_count_line (id, tenant_id, physical_count_id, material_id, uom_id,
                                             expected_quantity, counted_quantity, counted_at,
                                             unit_cost_at_freeze, action_taken, created_at)
            VALUES (?, ?, ?, ?, ?, 10, 10, ?, 5, 'NO_DIFFERENCE', ?)
            """, 997_703L, TENANT_ID, RECONCILED_COUNT_ID, COUNTED_MATERIAL_ID, BAG_UOM_ID,
            COUNTED_AT, FROZEN_AT);
        jdbcTemplate.update("""
            INSERT INTO physical_count_line (id, tenant_id, physical_count_id, material_id, uom_id,
                                             expected_quantity, unit_cost_at_freeze, action_taken,
                                             created_at)
            VALUES (?, ?, ?, ?, ?, 10, 5, 'PENDING', ?)
            """, 997_704L, TENANT_ID, RECONCILED_COUNT_ID, UNCOUNTED_MATERIAL_ID, BAG_UOM_ID,
            FROZEN_AT);

        jdbcTemplate.update("""
            INSERT INTO purchase_invoice (id, tenant_id, warehouse_id, invoice_number,
                                          invoice_date, receipt_date, status, posted_to_inventory,
                                          created_at)
            VALUES (?, ?, ?, 'PINV-PFR-1', DATE '2026-07-30', DATE '2026-07-30',
                    'POSTED', TRUE, ?)
            """, SOURCE_INVOICE_ID, TENANT_ID, WAREHOUSE_ID, INCLUDED_CREATED_AT);

        insertMovement(INCLUDED_TRANSACTION_ID, COUNTED_MATERIAL_ID, "PURCHASE", "IN", "5",
            INCLUDED_MOVEMENT_DATE, INCLUDED_CREATED_AT, "PURCHASE_INVOICE", SOURCE_INVOICE_ID);
        insertMovement(AFTER_COUNT_TRANSACTION_ID, COUNTED_MATERIAL_ID, "MANUAL_CONSUMPTION", "OUT", "10",
            AFTER_COUNT_MOVEMENT_DATE, AFTER_COUNT_CREATED_AT, null, null);
        insertMovement(997_803L, UNCOUNTED_MATERIAL_ID, "PURCHASE", "IN", "5",
            FROZEN_AT.plusMinutes(30), FROZEN_AT.plusMinutes(30), null, null);
        insertMovement(997_804L, WAREHOUSE_ONLY_MATERIAL_ID, "PURCHASE", "IN", "4",
            COUNTED_AT.plusHours(2), COUNTED_AT.plusHours(2), null, null);
    }

    @Test
    void splitsRowsByCountTimeConvertsFrozenUomAndKeepsAggregatesOpenEnded() {
        PostFreezeMovementsResponse response = service.findPostFreezeMovements(COUNT_ID, TENANT_ID);

        assertThat(response.getTotalMovementCount()).isEqualTo(4);
        assertThat(response.getAffectedMaterialCount()).isEqualTo(3);
        assertThat(response.getMaterials())
            .extracting(PostFreezeMaterialMovementResponse::getMaterialId)
            .containsExactlyInAnyOrder(COUNTED_MATERIAL_ID, UNCOUNTED_MATERIAL_ID);
        PostFreezeMaterialMovementResponse aggregate = response.getMaterials().stream()
            .filter(row -> row.getMaterialId().equals(COUNTED_MATERIAL_ID))
            .findFirst()
            .orElseThrow();
        assertThat(aggregate.getMovementCount()).isEqualTo(2);
        assertThat(aggregate.getQuantityIn()).isEqualByComparingTo("1.000000");
        assertThat(aggregate.getQuantityOut()).isEqualByComparingTo("2.000000");
        assertThat(aggregate.getNetQuantity()).isEqualByComparingTo("-1.000000");

        assertThat(response.getIncluded()).singleElement().satisfies(row -> {
            assertMovementIdentity(row, InventoryTransactionDirection.IN, "1.000000");
            assertThat(row.getMovementDate()).isEqualTo(INCLUDED_MOVEMENT_DATE);
            assertThat(row.getCreatedAt()).isEqualTo(INCLUDED_CREATED_AT);
            assertThat(row.getReferenceType()).isEqualTo("PURCHASE_INVOICE");
            assertThat(row.getReferenceId()).isEqualTo(SOURCE_INVOICE_ID);
            assertThat(row.getReferenceCode()).isEqualTo("PINV-PFR-1");
        });
        assertThat(response.getAfterCount()).singleElement().satisfies(row -> {
            assertMovementIdentity(row, InventoryTransactionDirection.OUT, "2.000000");
            assertThat(row.getMovementDate()).isEqualTo(AFTER_COUNT_MOVEMENT_DATE);
            assertThat(row.getCreatedAt()).isEqualTo(AFTER_COUNT_CREATED_AT);
        });

        assertThat(response.getIncluded()).allSatisfy(row ->
            assertThat(row.getMovementDate()).isBeforeOrEqualTo(COUNTED_AT));
        assertThat(response.getIncluded()).hasSizeLessThan(aggregate.getMovementCount());
        assertThat(response.getIncluded()).noneMatch(row ->
            row.getMaterialId().equals(UNCOUNTED_MATERIAL_ID));
        assertThat(response.getAfterCount()).noneMatch(row ->
            row.getMaterialId().equals(UNCOUNTED_MATERIAL_ID));
    }

    @Test
    void reconciledCountReturnsEmptyRowsWithoutChangingOpenEndedAggregates() {
        PostFreezeMovementsResponse open = service.findPostFreezeMovements(COUNT_ID, TENANT_ID);
        PostFreezeMovementsResponse reconciled =
            service.findPostFreezeMovements(RECONCILED_COUNT_ID, TENANT_ID);

        assertThat(reconciled.getIncluded()).isEmpty();
        assertThat(reconciled.getAfterCount()).isEmpty();
        assertThat(reconciled.getTotalMovementCount()).isPositive();
        assertThat(reconciled.getTotalMovementCount()).isEqualTo(open.getTotalMovementCount());
        assertThat(reconciled.getAffectedMaterialCount()).isEqualTo(open.getAffectedMaterialCount());
        assertThat(reconciled.getMaterials())
            .usingRecursiveFieldByFieldElementComparator()
            .containsExactlyInAnyOrderElementsOf(open.getMaterials());
    }

    private void assertMovementIdentity(
            PostFreezeMovementRowResponse row,
            InventoryTransactionDirection direction,
            String quantity) {
        assertThat(row.getMaterialId()).isEqualTo(COUNTED_MATERIAL_ID);
        assertThat(row.getMaterialName()).isEqualTo("Flour");
        assertThat(row.getMaterialNameAr()).isEqualTo("دقيق");
        assertThat(row.getDirection()).isEqualTo(direction);
        assertThat(row.getQuantity()).isEqualByComparingTo(quantity);
        assertThat(row.getUomId()).isEqualTo(BAG_UOM_ID);
        assertThat(row.getUomSymbol()).isEqualTo("bag");
    }

    private void insertMaterial(
            Long id, String code, String name, String nameAr, Long displayUomId) {
        jdbcTemplate.update("""
            INSERT INTO material (id, tenant_id, category_id, stock_uom_id, display_uom_id,
                                  code, name, name_ar, active, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE, CURRENT_TIMESTAMP)
            """, id, TENANT_ID, CATEGORY_ID, KG_UOM_ID, displayUomId, code, name, nameAr);
    }

    private void insertMovement(
            Long id,
            Long materialId,
            String transactionType,
            String direction,
            String quantity,
            LocalDateTime movementDate,
            LocalDateTime createdAt,
            String referenceType,
            Long referenceId) {
        jdbcTemplate.update("""
            INSERT INTO inventory_transaction (id, tenant_id, warehouse_id, material_id,
                                               transaction_type, direction, entered_quantity,
                                               entered_uom_id, stock_quantity, stock_uom_id,
                                               transaction_date, movement_date, reference_type,
                                               reference_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?, CAST(? AS numeric), ?, CAST(? AS numeric), ?,
                    ?, ?, ?, ?, ?)
            """, id, TENANT_ID, WAREHOUSE_ID, materialId, transactionType, direction,
            quantity, KG_UOM_ID, quantity, KG_UOM_ID, createdAt, movementDate,
            referenceType, referenceId, createdAt);
    }
}
