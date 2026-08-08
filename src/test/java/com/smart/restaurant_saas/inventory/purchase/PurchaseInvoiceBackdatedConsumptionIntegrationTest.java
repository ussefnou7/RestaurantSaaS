package com.smart.restaurant_saas.inventory.purchase;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.restaurant_saas.inventory.core.PurchaseInvoiceService;
import com.smart.restaurant_saas.inventory.purchase.dto.BackdatedConsumptionCheckResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
    private static final Long TYPE_INVOICE_ID = 996_503L;
    private static final Long DAY_BOUNDARY_INVOICE_ID = 996_504L;

    private static final Long CONFLICT_MATERIAL_ID = 996_601L;
    private static final Long BEFORE_MATERIAL_ID = 996_602L;
    private static final Long INBOUND_ONLY_MATERIAL_ID = 996_603L;
    private static final Long NEVER_MOVED_MATERIAL_ID = 996_604L;
    private static final Long OTHER_WAREHOUSE_MATERIAL_ID = 996_605L;
    private static final Long BACKDATED_OUTBOUND_MATERIAL_ID = 996_606L;
    private static final Long OTHER_TENANT_MOVEMENT_MATERIAL_ID = 996_607L;

    private static final Long RETURN_ONLY_MATERIAL_ID = 996_611L;
    private static final Long ORDER_CONSUMPTION_MATERIAL_ID = 996_612L;
    private static final Long WASTE_MATERIAL_ID = 996_613L;
    private static final Long COUNT_SHORTAGE_MATERIAL_ID = 996_614L;
    private static final Long RETURN_AND_CONSUMPTION_MATERIAL_ID = 996_615L;
    private static final Long REVERSAL_ONLY_MATERIAL_ID = 996_616L;

    private static final Long SAME_DAY_MATERIAL_ID = 996_621L;
    private static final Long SAME_DAY_LATE_MATERIAL_ID = 996_622L;
    private static final Long SAME_DAY_MIDNIGHT_MATERIAL_ID = 996_623L;
    private static final Long DAY_AFTER_MATERIAL_ID = 996_624L;
    private static final Long DAY_BEFORE_MATERIAL_ID = 996_625L;

    private static final LocalDate RECEIPT_DATE = LocalDate.of(2026, 7, 10);
    private static final LocalDateTime LAST_CONSUMPTION_DATE =
        LocalDateTime.of(2026, 7, 14, 15, 30);

    /** After {@link #RECEIPT_DATE}, and deliberately later than {@link #MIXED_CONSUMPTION_DATE}. */
    private static final LocalDateTime MIXED_RETURN_DATE = LocalDateTime.of(2026, 7, 18, 11, 0);
    private static final LocalDateTime MIXED_CONSUMPTION_DATE = LocalDateTime.of(2026, 7, 16, 11, 0);

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
            INSERT INTO uom (id, tenant_id, code, name, symbol, type, factor_to_base, entered_factor, active, created_at)
            VALUES (?, ?, 'BDC-KG', 'Kilogram', 'kg', 'WEIGHT', 1, 1, TRUE, CURRENT_TIMESTAMP)
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
        insertMaterial(RETURN_ONLY_MATERIAL_ID, "BDC-MAT-11", "Butter", "زبدة");
        insertMaterial(ORDER_CONSUMPTION_MATERIAL_ID, "BDC-MAT-12", "Cheese", "جبن");
        insertMaterial(WASTE_MATERIAL_ID, "BDC-MAT-13", "Tomato", "طماطم");
        insertMaterial(COUNT_SHORTAGE_MATERIAL_ID, "BDC-MAT-14", "Onion", "بصل");
        insertMaterial(RETURN_AND_CONSUMPTION_MATERIAL_ID, "BDC-MAT-15", "Lemon", "ليمون");
        insertMaterial(REVERSAL_ONLY_MATERIAL_ID, "BDC-MAT-16", "Pepper", "فلفل");
        insertMaterial(SAME_DAY_MATERIAL_ID, "BDC-MAT-21", "Garlic", "ثوم");
        insertMaterial(SAME_DAY_LATE_MATERIAL_ID, "BDC-MAT-22", "Ginger", "زنجبيل");
        insertMaterial(SAME_DAY_MIDNIGHT_MATERIAL_ID, "BDC-MAT-23", "Mint", "نعناع");
        insertMaterial(DAY_AFTER_MATERIAL_ID, "BDC-MAT-24", "Parsley", "بقدونس");
        insertMaterial(DAY_BEFORE_MATERIAL_ID, "BDC-MAT-25", "Basil", "ريحان");

        insertInvoice(INVOICE_ID, "PINV-BDC-1");
        insertInvoice(EMPTY_INVOICE_ID, "PINV-BDC-2");
        insertInvoice(TYPE_INVOICE_ID, "PINV-BDC-3");
        insertInvoice(DAY_BOUNDARY_INVOICE_ID, "PINV-BDC-4");
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
        insertInvoiceLine(lineId++, EMPTY_INVOICE_ID, NEVER_MOVED_MATERIAL_ID);
        for (Long materialId : new Long[] {
                RETURN_ONLY_MATERIAL_ID,
                ORDER_CONSUMPTION_MATERIAL_ID,
                WASTE_MATERIAL_ID,
                COUNT_SHORTAGE_MATERIAL_ID,
                RETURN_AND_CONSUMPTION_MATERIAL_ID,
                REVERSAL_ONLY_MATERIAL_ID}) {
            insertInvoiceLine(lineId++, TYPE_INVOICE_ID, materialId);
        }
        for (Long materialId : new Long[] {
                SAME_DAY_MATERIAL_ID,
                SAME_DAY_LATE_MATERIAL_ID,
                SAME_DAY_MIDNIGHT_MATERIAL_ID,
                DAY_AFTER_MATERIAL_ID,
                DAY_BEFORE_MATERIAL_ID}) {
            insertInvoiceLine(lineId++, DAY_BOUNDARY_INVOICE_ID, materialId);
        }

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

        // One movement type per material, all outbound and all after RECEIPT_DATE, so the only
        // thing separating them in the result is whether the type actually FIFO-consumes.
        insertTypedMovement(996_811L, RETURN_ONLY_MATERIAL_ID, "PURCHASE_RETURN", "OUT",
            LocalDateTime.of(2026, 7, 15, 10, 0));
        insertTypedMovement(996_812L, ORDER_CONSUMPTION_MATERIAL_ID, "CONSUMPTION_SUMMARY", "OUT",
            LocalDateTime.of(2026, 7, 15, 10, 0));
        insertTypedMovement(996_813L, WASTE_MATERIAL_ID, "WASTE", "OUT",
            LocalDateTime.of(2026, 7, 15, 10, 0));
        insertTypedMovement(996_814L, COUNT_SHORTAGE_MATERIAL_ID, "COUNT_ADJUSTMENT", "OUT",
            LocalDateTime.of(2026, 7, 15, 10, 0));

        // The return is the later of the two, so a query that counted it would report its date.
        insertTypedMovement(996_815L, RETURN_AND_CONSUMPTION_MATERIAL_ID, "CONSUMPTION_SUMMARY",
            "OUT", MIXED_CONSUMPTION_DATE);
        insertTypedMovement(996_816L, RETURN_AND_CONSUMPTION_MATERIAL_ID, "PURCHASE_RETURN",
            "OUT", MIXED_RETURN_DATE);

        // A reversed physical-count surplus: the reversal is COUNT_ADJUSTMENT/OUT like a genuine
        // shortage, but it restores its source batch rather than FIFO-consuming.
        insertTypedMovement(996_817L, REVERSAL_ONLY_MATERIAL_ID, "COUNT_ADJUSTMENT", "IN",
            LocalDateTime.of(2026, 7, 15, 10, 0));
        insertReversalMovement(996_818L, REVERSAL_ONLY_MATERIAL_ID, "COUNT_ADJUSTMENT", "OUT",
            LocalDateTime.of(2026, 7, 15, 10, 0), 996_817L);

        // One genuine consumption per material, walking the calendar-day boundary around
        // RECEIPT_DATE. Everything here is CONSUMPTION_SUMMARY/OUT, so the only thing that can
        // separate them in the result is which day the movement falls on.
        insertTypedMovement(996_821L, SAME_DAY_MATERIAL_ID, "CONSUMPTION_SUMMARY", "OUT",
            RECEIPT_DATE.atTime(9, 0));
        insertTypedMovement(996_822L, SAME_DAY_LATE_MATERIAL_ID, "CONSUMPTION_SUMMARY", "OUT",
            RECEIPT_DATE.atTime(23, 59, 59));
        insertTypedMovement(996_823L, SAME_DAY_MIDNIGHT_MATERIAL_ID, "CONSUMPTION_SUMMARY", "OUT",
            RECEIPT_DATE.atStartOfDay());
        insertTypedMovement(996_824L, DAY_AFTER_MATERIAL_ID, "CONSUMPTION_SUMMARY", "OUT",
            RECEIPT_DATE.plusDays(1).atStartOfDay());
        insertTypedMovement(996_825L, DAY_BEFORE_MATERIAL_ID, "CONSUMPTION_SUMMARY", "OUT",
            RECEIPT_DATE.minusDays(1).atTime(23, 59));
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

    @Test
    void reportsOnlyStockConsumingMovementTypes() {
        assertThat(conflictMaterialIds()).containsExactlyInAnyOrder(
            ORDER_CONSUMPTION_MATERIAL_ID,
            WASTE_MATERIAL_ID,
            COUNT_SHORTAGE_MATERIAL_ID,
            RETURN_AND_CONSUMPTION_MATERIAL_ID);
    }

    @Test
    void ignoresMaterialWhoseOnlyOutboundMovementIsAPurchaseReturn() {
        assertThat(conflictMaterialIds()).doesNotContain(RETURN_ONLY_MATERIAL_ID);
    }

    @Test
    void reportsMaterialConsumedByAnOrderAfterReceiptDate() {
        assertThat(conflictMaterialIds()).contains(ORDER_CONSUMPTION_MATERIAL_ID);
    }

    @Test
    void reportsMaterialWastedAfterReceiptDate() {
        assertThat(conflictMaterialIds()).contains(WASTE_MATERIAL_ID);
    }

    @Test
    void reportsMaterialShortInAPhysicalCountAfterReceiptDate() {
        assertThat(conflictMaterialIds()).contains(COUNT_SHORTAGE_MATERIAL_ID);
    }

    @Test
    void ignoresMaterialWhoseOnlyOutboundMovementReversesAnEarlierMovement() {
        assertThat(conflictMaterialIds()).doesNotContain(REVERSAL_ONLY_MATERIAL_ID);
    }

    @Test
    void datesAConflictByItsConsumptionNotByALaterPurchaseReturn() {
        assertThat(service.findBackdatedConsumptionConflicts(TYPE_INVOICE_ID, TENANT_ID))
            .filteredOn(conflict ->
                conflict.getMaterialId().equals(RETURN_AND_CONSUMPTION_MATERIAL_ID))
            .singleElement()
            .satisfies(conflict ->
                assertThat(conflict.getLastConsumptionDate()).isEqualTo(MIXED_CONSUMPTION_DATE));
    }

    @Test
    void consumptionOnTheReceiptDayIsNotBackdated() {
        // The reported defect: an invoice dated today warned about a consumption that also
        // happened today. A same-day receipt is not backdated.
        assertThat(conflictMaterialIds(DAY_BOUNDARY_INVOICE_ID))
            .doesNotContain(SAME_DAY_MATERIAL_ID);
    }

    @Test
    void consumptionLateOnTheReceiptDayIsNotBackdated() {
        // The leak, at its widest: 23:59:59 on the receipt day still cleared the old
        // "movementDate > receiptDate.atStartOfDay()" predicate by almost a full day.
        assertThat(conflictMaterialIds(DAY_BOUNDARY_INVOICE_ID))
            .doesNotContain(SAME_DAY_LATE_MATERIAL_ID);
    }

    @Test
    void consumptionAtMidnightOnTheReceiptDayIsNotBackdated() {
        // The one same-day instant the old predicate already excluded. The new boundary must not
        // shift the other way and start reporting it.
        assertThat(conflictMaterialIds(DAY_BOUNDARY_INVOICE_ID))
            .doesNotContain(SAME_DAY_MIDNIGHT_MATERIAL_ID);
    }

    @Test
    void consumptionTheDayAfterTheReceiptIsBackdated() {
        // Midnight of the following day is the earliest instant that can be a conflict.
        assertThat(conflictMaterialIds(DAY_BOUNDARY_INVOICE_ID))
            .contains(DAY_AFTER_MATERIAL_ID);
    }

    @Test
    void consumptionTheDayBeforeTheReceiptIsNotBackdated() {
        assertThat(conflictMaterialIds(DAY_BOUNDARY_INVOICE_ID))
            .doesNotContain(DAY_BEFORE_MATERIAL_ID);
    }

    @Test
    void onlyTheLaterCalendarDayConsumptionIsReportedAcrossTheDayBoundary() {
        assertThat(conflictMaterialIds(DAY_BOUNDARY_INVOICE_ID))
            .containsExactly(DAY_AFTER_MATERIAL_ID);
    }

    private List<Long> conflictMaterialIds() {
        return conflictMaterialIds(TYPE_INVOICE_ID);
    }

    private List<Long> conflictMaterialIds(Long invoiceId) {
        return service.findBackdatedConsumptionConflicts(invoiceId, TENANT_ID).stream()
            .map(BackdatedConsumptionCheckResponse::getMaterialId)
            .toList();
    }

    private void insertTenant(Long id, String code) {
        jdbcTemplate.update("""
            INSERT INTO tenants (id, name, code, status, created_at, timezone)
            VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, 'Africa/Cairo')
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
        insertMovementRow(id, tenantId, warehouseId, materialId, "MANUAL_CONSUMPTION", direction,
            movementDate, createdAt, null);
    }

    private void insertTypedMovement(
            Long id,
            Long materialId,
            String transactionType,
            String direction,
            LocalDateTime movementDate) {
        insertMovementRow(id, TENANT_ID, WAREHOUSE_ID, materialId, transactionType, direction,
            movementDate, movementDate, null);
    }

    private void insertReversalMovement(
            Long id,
            Long materialId,
            String transactionType,
            String direction,
            LocalDateTime movementDate,
            Long reversesTransactionId) {
        insertMovementRow(id, TENANT_ID, WAREHOUSE_ID, materialId, transactionType, direction,
            movementDate, movementDate, reversesTransactionId);
    }

    private void insertMovementRow(
            Long id,
            Long tenantId,
            Long warehouseId,
            Long materialId,
            String transactionType,
            String direction,
            LocalDateTime movementDate,
            LocalDateTime createdAt,
            Long reversesTransactionId) {
        jdbcTemplate.update("""
            INSERT INTO inventory_transaction (id, tenant_id, warehouse_id, material_id,
                                               transaction_type, direction, entered_quantity,
                                               entered_uom_id, stock_quantity, stock_uom_id,
                                               transaction_date, movement_date, created_at,
                                               reverses_transaction_id)
            VALUES (?, ?, ?, ?, ?, ?, 1, ?, 1, ?, ?, ?, ?, ?)
            """, id, tenantId, warehouseId, materialId, transactionType, direction, UOM_ID, UOM_ID,
            createdAt, movementDate, createdAt, reversesTransactionId);
    }
}
