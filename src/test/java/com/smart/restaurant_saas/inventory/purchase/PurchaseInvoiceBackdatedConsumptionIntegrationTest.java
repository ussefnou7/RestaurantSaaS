package com.smart.restaurant_saas.inventory.purchase;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.restaurant_saas.inventory.core.PurchaseInvoiceService;
import com.smart.restaurant_saas.inventory.purchase.dto.BackdatedConsumptionCheckResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class PurchaseInvoiceBackdatedConsumptionIntegrationTest {

    private static final Long TENANT_ID = 996_001L;
    private static final Long OTHER_TENANT_ID = 996_002L;
    private static final Long BRANCH_ID = 996_101L;
    private static final Long UOM_ID = 996_201L;
    private static final Long CATEGORY_ID = 996_301L;
    private static final Long WAREHOUSE_ID = 996_401L;
    private static final Long OTHER_WAREHOUSE_ID = 996_402L;
    private static final Long INVOICE_ID = 996_501L;
    private static final Long EMPTY_INVOICE_ID = 996_502L;

    private static final Long CONFLICT_MATERIAL_ID = 996_601L;
    private static final Long BEFORE_MATERIAL_ID = 996_602L;
    private static final Long INBOUND_ONLY_MATERIAL_ID = 996_603L;
    private static final Long NEVER_MOVED_MATERIAL_ID = 996_604L;
    private static final Long OTHER_WAREHOUSE_MATERIAL_ID = 996_605L;
    private static final Long BACKDATED_OUTBOUND_MATERIAL_ID = 996_606L;
    private static final Long OTHER_TENANT_MOVEMENT_MATERIAL_ID = 996_607L;

    private static final LocalDate RECEIPT_DATE = LocalDate.of(2026, 7, 10);
    private static final LocalDateTime LAST_CONSUMPTION_DATE =
        LocalDateTime.of(2026, 7, 14, 15, 30);

    @Autowired
    private PurchaseInvoiceService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        insertTenant(TENANT_ID, "BACKDATED_CHECK");
        insertTenant(OTHER_TENANT_ID, "BACKDATED_CHECK_OTHER");
        jdbcTemplate.update("""
            INSERT INTO branches (id, tenant_id, name, code, is_active, created_at)
            VALUES (?, ?, 'Backdated Branch', 'BDC-BR', TRUE, CURRENT_TIMESTAMP)
            """, BRANCH_ID, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO uom (id, tenant_id, code, name, symbol, type, factor_to_base, active, created_at)
            VALUES (?, ?, 'BDC-KG', 'Kilogram', 'kg', 'WEIGHT', 1, TRUE, CURRENT_TIMESTAMP)
            """, UOM_ID, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO material_category (id, tenant_id, code, name, active, created_at)
            VALUES (?, ?, 'BDC-CAT', 'Backdated materials', TRUE, CURRENT_TIMESTAMP)
            """, CATEGORY_ID, TENANT_ID);
        insertWarehouse(WAREHOUSE_ID, "BDC-WH-1", "Invoice Warehouse");
        insertWarehouse(OTHER_WAREHOUSE_ID, "BDC-WH-2", "Other Warehouse");

        insertMaterial(CONFLICT_MATERIAL_ID, "BDC-MAT-1", "Flour", "دقيق");
        insertMaterial(BEFORE_MATERIAL_ID, "BDC-MAT-2", "Salt", "ملح");
        insertMaterial(INBOUND_ONLY_MATERIAL_ID, "BDC-MAT-3", "Oil", "زيت");
        insertMaterial(NEVER_MOVED_MATERIAL_ID, "BDC-MAT-4", "Rice", "أرز");
        insertMaterial(OTHER_WAREHOUSE_MATERIAL_ID, "BDC-MAT-5", "Sugar", "سكر");
        insertMaterial(BACKDATED_OUTBOUND_MATERIAL_ID, "BDC-MAT-6", "Milk", "حليب");
        insertMaterial(OTHER_TENANT_MOVEMENT_MATERIAL_ID, "BDC-MAT-7", "Tea", "شاي");

        insertInvoice(INVOICE_ID, "PINV-BDC-1");
        insertInvoice(EMPTY_INVOICE_ID, "PINV-BDC-2");
        long lineId = 996_701L;
        for (Long materialId : new Long[] {
                CONFLICT_MATERIAL_ID,
                BEFORE_MATERIAL_ID,
                INBOUND_ONLY_MATERIAL_ID,
                NEVER_MOVED_MATERIAL_ID,
                OTHER_WAREHOUSE_MATERIAL_ID,
                BACKDATED_OUTBOUND_MATERIAL_ID,
                OTHER_TENANT_MOVEMENT_MATERIAL_ID}) {
            insertInvoiceLine(lineId++, INVOICE_ID, materialId);
        }
        insertInvoiceLine(lineId, EMPTY_INVOICE_ID, NEVER_MOVED_MATERIAL_ID);

        insertMovement(996_801L, TENANT_ID, WAREHOUSE_ID, CONFLICT_MATERIAL_ID, "OUT",
            LocalDateTime.of(2026, 7, 12, 9, 0), LocalDateTime.of(2026, 7, 12, 9, 0));
        insertMovement(996_802L, TENANT_ID, WAREHOUSE_ID, CONFLICT_MATERIAL_ID, "OUT",
            LAST_CONSUMPTION_DATE, LAST_CONSUMPTION_DATE);
        insertMovement(996_803L, TENANT_ID, WAREHOUSE_ID, BEFORE_MATERIAL_ID, "OUT",
            LocalDateTime.of(2026, 7, 9, 18, 0), LocalDateTime.of(2026, 7, 9, 18, 0));
        insertMovement(996_804L, TENANT_ID, WAREHOUSE_ID, INBOUND_ONLY_MATERIAL_ID, "IN",
            LocalDateTime.of(2026, 7, 20, 8, 0), LocalDateTime.of(2026, 7, 20, 8, 0));
        insertMovement(996_805L, TENANT_ID, OTHER_WAREHOUSE_ID, OTHER_WAREHOUSE_MATERIAL_ID, "OUT",
            LocalDateTime.of(2026, 7, 21, 8, 0), LocalDateTime.of(2026, 7, 21, 8, 0));
        insertMovement(996_806L, TENANT_ID, WAREHOUSE_ID, BACKDATED_OUTBOUND_MATERIAL_ID, "OUT",
            LocalDateTime.of(2026, 7, 8, 8, 0), LocalDateTime.of(2026, 7, 25, 8, 0));
        insertMovement(996_807L, OTHER_TENANT_ID, WAREHOUSE_ID, CONFLICT_MATERIAL_ID, "OUT",
            LocalDateTime.of(2026, 7, 31, 8, 0), LocalDateTime.of(2026, 7, 31, 8, 0));
        insertMovement(996_808L, OTHER_TENANT_ID, WAREHOUSE_ID,
            OTHER_TENANT_MOVEMENT_MATERIAL_ID, "OUT",
            LocalDateTime.of(2026, 7, 30, 8, 0), LocalDateTime.of(2026, 7, 30, 8, 0));
    }

    @Test
    void returnsOnlyInvoiceWarehouseOutboundMovementsAfterReceiptDateUsingMovementDate() {
        var response = service.findBackdatedConsumptionConflicts(INVOICE_ID, TENANT_ID);

        assertThat(response).singleElement().satisfies(conflict -> {
            assertThat(conflict.getMaterialId()).isEqualTo(CONFLICT_MATERIAL_ID);
            assertThat(conflict.getMaterialName()).isEqualTo("Flour");
            assertThat(conflict.getMaterialNameAr()).isEqualTo("دقيق");
            assertThat(conflict.getLastConsumptionDate()).isEqualTo(LAST_CONSUMPTION_DATE);
        });
    }

    @Test
    void returnsEmptyListWhenNoInvoiceMaterialHasLaterConsumption() {
        assertThat(service.findBackdatedConsumptionConflicts(EMPTY_INVOICE_ID, TENANT_ID)).isEmpty();
    }

    private void insertTenant(Long id, String code) {
        jdbcTemplate.update("""
            INSERT INTO tenants (id, name, code, status, created_at)
            VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP)
            """, id, code, code);
    }

    private void insertWarehouse(Long id, String code, String name) {
        jdbcTemplate.update("""
            INSERT INTO warehouse (id, tenant_id, branch_id, code, name, type, active, created_at)
            VALUES (?, ?, ?, ?, ?, 'CENTRAL', TRUE, CURRENT_TIMESTAMP)
            """, id, TENANT_ID, BRANCH_ID, code, name);
    }

    private void insertMaterial(Long id, String code, String name, String nameAr) {
        jdbcTemplate.update("""
            INSERT INTO material (id, tenant_id, category_id, stock_uom_id, display_uom_id,
                                  code, name, name_ar, active, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, TRUE, CURRENT_TIMESTAMP)
            """, id, TENANT_ID, CATEGORY_ID, UOM_ID, UOM_ID, code, name, nameAr);
    }

    private void insertInvoice(Long id, String invoiceNumber) {
        jdbcTemplate.update("""
            INSERT INTO purchase_invoice (id, tenant_id, warehouse_id, invoice_number,
                                          invoice_date, receipt_date, status, created_at)
            VALUES (?, ?, ?, ?, ?, ?, 'COMPLETE', CURRENT_TIMESTAMP)
            """, id, TENANT_ID, WAREHOUSE_ID, invoiceNumber, RECEIPT_DATE, RECEIPT_DATE);
    }

    private void insertInvoiceLine(Long id, Long invoiceId, Long materialId) {
        jdbcTemplate.update("""
            INSERT INTO purchase_invoice_line (id, purchase_invoice_id, material_id, quantity,
                                               uom_id, unit_cost, line_total)
            VALUES (?, ?, ?, 1, ?, 1, 1)
            """, id, invoiceId, materialId, UOM_ID);
    }

    private void insertMovement(
            Long id,
            Long tenantId,
            Long warehouseId,
            Long materialId,
            String direction,
            LocalDateTime movementDate,
            LocalDateTime createdAt) {
        jdbcTemplate.update("""
            INSERT INTO inventory_transaction (id, tenant_id, warehouse_id, material_id,
                                               transaction_type, direction, entered_quantity,
                                               entered_uom_id, stock_quantity, stock_uom_id,
                                               transaction_date, movement_date, created_at)
            VALUES (?, ?, ?, ?, 'MANUAL_CONSUMPTION', ?, 1, ?, 1, ?, ?, ?, ?)
            """, id, tenantId, warehouseId, materialId, direction, UOM_ID, UOM_ID,
            createdAt, movementDate, createdAt);
    }
}
