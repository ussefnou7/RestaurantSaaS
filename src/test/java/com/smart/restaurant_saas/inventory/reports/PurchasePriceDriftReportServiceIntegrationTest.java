package com.smart.restaurant_saas.inventory.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smart.restaurant_saas.common.BusinessException;
import com.smart.restaurant_saas.inventory.core.InventoryErrorCode;
import com.smart.restaurant_saas.inventory.reports.dto.PurchasePriceDriftRow;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * The drift query is native SQL with window-free ordered aggregates and a NULLS LAST sort, so it is
 * verified against real Postgres. Seeded ids are in a dedicated high range and the test is
 * transactional.
 */
@SpringBootTest
@Transactional
class PurchasePriceDriftReportServiceIntegrationTest {

    private static final Long TENANT_ID = 997_001L;
    private static final Long OTHER_TENANT_ID = 997_002L;
    private static final Long BRANCH_ID = 997_101L;

    private static final Long UOM_KG_ID = 997_201L;
    private static final Long UOM_G_ID = 997_202L;

    private static final Long CATEGORY_ID = 997_301L;
    private static final Long OTHER_CATEGORY_ID = 997_302L;

    private static final Long WAREHOUSE_ID = 997_401L;
    private static final Long SECOND_WAREHOUSE_ID = 997_402L;

    private static final Long SUPPLIER_ID = 997_601L;
    private static final Long OTHER_SUPPLIER_ID = 997_602L;

    private static final Long CHICKEN_ID = 997_501L;
    private static final Long RICE_ID = 997_502L;
    private static final Long SALT_ID = 997_503L;
    /** Stock UOM grams, display UOM kilograms — proves prices are NOT converted. */
    private static final Long SPICE_ID = 997_504L;

    private static final LocalDate WINDOW_FROM = LocalDate.of(2026, 3, 1);
    private static final LocalDate WINDOW_TO = LocalDate.of(2026, 3, 31);

    private long nextBalanceId = 997_700L;
    private long nextBatchId = 997_800L;
    private long nextTransactionId = 997_900L;
    private long nextInvoiceId = 998_000L;

    @Autowired
    private PurchasePriceDriftReportService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        insertTenant(TENANT_ID, "Drift Tenant", "PRICE_DRIFT");
        insertTenant(OTHER_TENANT_ID, "Other Tenant", "PRICE_DRIFT_OTHER");

        jdbcTemplate.update("""
            INSERT INTO branches (id, tenant_id, name, code, is_active, created_at)
            VALUES (?, ?, 'Main Branch', 'PDR-BR-1', TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, BRANCH_ID, TENANT_ID);

        insertUom(UOM_KG_ID, null, "PDR-KG", "Kilogram", "kg", "1");
        insertUom(UOM_G_ID, UOM_KG_ID, "PDR-G", "Gram", "g", "0.001");

        insertCategory(CATEGORY_ID, "PDR-MEAT", "Meat");
        insertCategory(OTHER_CATEGORY_ID, "PDR-DRY", "Dry");

        insertWarehouse(WAREHOUSE_ID, TENANT_ID, "PDR-WH-1", "Main Warehouse");
        insertWarehouse(SECOND_WAREHOUSE_ID, TENANT_ID, "PDR-WH-2", "Second Warehouse");

        insertSupplier(SUPPLIER_ID, "PDR-SUP-1", "Main Supplier");
        insertSupplier(OTHER_SUPPLIER_ID, "PDR-SUP-2", "Other Supplier");

        insertMaterial(CHICKEN_ID, TENANT_ID, CATEGORY_ID, UOM_KG_ID, UOM_KG_ID, "PDR-M-1", "Chicken");
        insertMaterial(RICE_ID, TENANT_ID, OTHER_CATEGORY_ID, UOM_KG_ID, UOM_KG_ID, "PDR-M-2", "Rice");
        insertMaterial(SALT_ID, TENANT_ID, CATEGORY_ID, UOM_KG_ID, UOM_KG_ID, "PDR-M-3", "Salt");
        insertMaterial(SPICE_ID, TENANT_ID, CATEGORY_ID, UOM_G_ID, UOM_KG_ID, "PDR-M-4", "Spice");
    }

    // =========================================================================
    // The core calculation
    // =========================================================================

    @Test
    void comparesFirstAndLastPurchasePrice() {
        purchase(CHICKEN_ID, WAREHOUSE_ID, "80.00", "2026-03-05");
        purchase(CHICKEN_ID, WAREHOUSE_ID, "110.00", "2026-03-20");

        PurchasePriceDriftRow row = rowFor(report(), CHICKEN_ID);

        assertThat(row.getFirstPrice()).isEqualTo("80.000000");
        assertThat(row.getLastPrice()).isEqualTo("110.000000");
        assertThat(row.getPriceChange()).isEqualTo("30.000000");
        assertThat(row.getChangePercent()).isEqualTo("37.500000");
        assertThat(row.getPurchaseCount()).isEqualTo(2L);
    }

    @Test
    void reportsAPriceFallAsANegativeChange() {
        purchase(CHICKEN_ID, WAREHOUSE_ID, "100.00", "2026-03-05");
        purchase(CHICKEN_ID, WAREHOUSE_ID, "75.00", "2026-03-20");

        PurchasePriceDriftRow row = rowFor(report(), CHICKEN_ID);

        assertThat(row.getPriceChange()).isEqualTo("-25.000000");
        assertThat(row.getChangePercent()).isEqualTo("-25.000000");
    }

    @Test
    void resolvesFirstAndLastByIdWhenPurchasesShareADate() {
        // Purchases are stamped receiptDate.atStartOfDay(), so same-day receipts have identical
        // movement_date and only the id can separate them — which is how FIFO reads them too.
        purchase(CHICKEN_ID, WAREHOUSE_ID, "80.00", "2026-03-05");
        purchase(CHICKEN_ID, WAREHOUSE_ID, "95.00", "2026-03-05");
        purchase(CHICKEN_ID, WAREHOUSE_ID, "120.00", "2026-03-05");

        PurchasePriceDriftRow row = rowFor(report(), CHICKEN_ID);

        assertThat(row.getFirstPrice()).isEqualTo("80.000000");
        assertThat(row.getLastPrice()).isEqualTo("120.000000");
        assertThat(row.getPurchaseCount()).isEqualTo(3L);
    }

    @Test
    void aSinglePurchaseReportsNoChangeAndACountOfOne() {
        // No special flag needed: purchaseCount = 1 explains first == last on its own.
        purchase(CHICKEN_ID, WAREHOUSE_ID, "80.00", "2026-03-05");

        PurchasePriceDriftRow row = rowFor(report(), CHICKEN_ID);

        assertThat(row.getFirstPrice()).isEqualTo("80.000000");
        assertThat(row.getLastPrice()).isEqualTo("80.000000");
        assertThat(row.getPriceChange()).isEqualTo("0.000000");
        assertThat(row.getChangePercent()).isEqualTo("0.000000");
        assertThat(row.getPurchaseCount()).isEqualTo(1L);
    }

    @Test
    void aZeroFirstPriceYieldsANullPercentRatherThanInfinity() {
        purchase(CHICKEN_ID, WAREHOUSE_ID, "0.00", "2026-03-05");
        purchase(CHICKEN_ID, WAREHOUSE_ID, "50.00", "2026-03-20");

        PurchasePriceDriftRow row = rowFor(report(), CHICKEN_ID);

        assertThat(row.getChangePercent()).isNull();
        // The money delta is still real and still reported.
        assertThat(row.getPriceChange()).isEqualTo("50.000000");
    }

    @Test
    void nullPercentRowsSortAfterEveryRowWithAPercent() {
        purchase(CHICKEN_ID, WAREHOUSE_ID, "0.00", "2026-03-05");
        purchase(CHICKEN_ID, WAREHOUSE_ID, "50.00", "2026-03-20");
        purchase(RICE_ID, WAREHOUSE_ID, "10.00", "2026-03-05");
        purchase(RICE_ID, WAREHOUSE_ID, "11.00", "2026-03-20");

        assertThat(report()).extracting(PurchasePriceDriftRow::getMaterialId)
            .containsExactly(RICE_ID, CHICKEN_ID);
    }

    @Test
    void sortsByPercentageNotByAbsoluteMoney() {
        // A cheap material up 50% breaks a recipe's costing; an expensive one up 5% barely moves it.
        purchase(RICE_ID, WAREHOUSE_ID, "2.00", "2026-03-05");
        purchase(RICE_ID, WAREHOUSE_ID, "3.00", "2026-03-20");   // +1.00, +50%
        purchase(CHICKEN_ID, WAREHOUSE_ID, "200.00", "2026-03-05");
        purchase(CHICKEN_ID, WAREHOUSE_ID, "210.00", "2026-03-20"); // +10.00, +5%

        assertThat(report()).extracting(PurchasePriceDriftRow::getMaterialId)
            .containsExactly(RICE_ID, CHICKEN_ID);
    }

    // =========================================================================
    // Batch origin filtering
    // =========================================================================

    @Test
    void excludesCountSurplusBatches() {
        // A count surplus opens a batch valued at the balance's running average (D89), not at a
        // price anyone paid. Counting it would invent drift that never happened.
        purchase(CHICKEN_ID, WAREHOUSE_ID, "80.00", "2026-03-05");
        countSurplusBatch(CHICKEN_ID, WAREHOUSE_ID, "999.00", "2026-03-20");

        PurchasePriceDriftRow row = rowFor(report(), CHICKEN_ID);

        assertThat(row.getLastPrice()).isEqualTo("80.000000");
        assertThat(row.getPurchaseCount()).isEqualTo(1L);
    }

    @Test
    void excludesOpeningBalanceBatches() {
        openingBalanceBatch(CHICKEN_ID, WAREHOUSE_ID, "500.00", "2026-03-02");
        purchase(CHICKEN_ID, WAREHOUSE_ID, "80.00", "2026-03-05");

        PurchasePriceDriftRow row = rowFor(report(), CHICKEN_ID);

        assertThat(row.getFirstPrice()).isEqualTo("80.000000");
        assertThat(row.getPurchaseCount()).isEqualTo(1L);
    }

    @Test
    void excludesPurchasesWhoseInvoiceWasReversed() {
        // Cancelling an invoice reverses the ledger and depletes the batch to zero, but the row
        // survives carrying its original unit_cost — a mistyped price would otherwise live on as a
        // real data point.
        purchase(CHICKEN_ID, WAREHOUSE_ID, "80.00", "2026-03-05");
        long reversedTxId = purchase(CHICKEN_ID, WAREHOUSE_ID, "9999.00", "2026-03-20");
        insertReversal(TENANT_ID, CHICKEN_ID, WAREHOUSE_ID, reversedTxId);

        PurchasePriceDriftRow row = rowFor(report(), CHICKEN_ID);

        assertThat(row.getLastPrice()).isEqualTo("80.000000");
        assertThat(row.getPurchaseCount()).isEqualTo(1L);
    }

    // =========================================================================
    // UOM — the difference from every other report here
    // =========================================================================

    @Test
    void pricesPassThroughWithNoUomConversion() {
        // SPICE has stock UOM grams and display UOM kilograms. A ledger-sourced report would
        // convert by 1000; batch costs are ALREADY per display UOM, so 12.50 must stay 12.50.
        // Converting here would silently multiply every price in the report by the UOM factor.
        purchase(SPICE_ID, WAREHOUSE_ID, "12.50", "2026-03-05");
        purchase(SPICE_ID, WAREHOUSE_ID, "25.00", "2026-03-20");

        PurchasePriceDriftRow row = rowFor(report(), SPICE_ID);

        assertThat(row.getFirstPrice()).isEqualTo("12.500000");
        assertThat(row.getLastPrice()).isEqualTo("25.000000");
        assertThat(row.getPriceChange()).isEqualTo("12.500000");
        assertThat(row.getChangePercent()).isEqualTo("100.000000");
        // The unit is stated, but it labels the price rather than evidencing a conversion.
        assertThat(row.getUomId()).isEqualTo(UOM_KG_ID);
        assertThat(row.getUomSymbol()).isEqualTo("kg");
    }

    // =========================================================================
    // Filters and edges
    // =========================================================================

    @Test
    void aMaterialWithNoPurchasesInRangeDoesNotAppearAtAll() {
        purchase(CHICKEN_ID, WAREHOUSE_ID, "80.00", "2026-03-05");

        // Unlike the loss comparison, silence here is nothing to say, not a clean bill of health.
        assertThat(report()).extracting(PurchasePriceDriftRow::getMaterialId)
            .containsExactly(CHICKEN_ID);
    }

    @Test
    void excludesPurchasesOutsideTheWindow() {
        purchase(CHICKEN_ID, WAREHOUSE_ID, "80.00", "2026-02-28");
        purchase(CHICKEN_ID, WAREHOUSE_ID, "110.00", "2026-04-01");

        assertThat(report()).isEmpty();
    }

    @Test
    void filtersBySupplier() {
        purchaseFrom(CHICKEN_ID, WAREHOUSE_ID, "80.00", "2026-03-05", SUPPLIER_ID);
        purchaseFrom(RICE_ID, WAREHOUSE_ID, "10.00", "2026-03-05", OTHER_SUPPLIER_ID);

        assertThat(service.purchasePriceDrift(
                TENANT_ID, WINDOW_FROM, WINDOW_TO, null, null, SUPPLIER_ID))
            .extracting(PurchasePriceDriftRow::getMaterialId).containsExactly(CHICKEN_ID);
    }

    @Test
    void filtersByWarehouseAndCategory() {
        purchase(CHICKEN_ID, WAREHOUSE_ID, "80.00", "2026-03-05");
        purchase(RICE_ID, SECOND_WAREHOUSE_ID, "10.00", "2026-03-05");

        assertThat(service.purchasePriceDrift(
                TENANT_ID, WINDOW_FROM, WINDOW_TO, WAREHOUSE_ID, null, null))
            .extracting(PurchasePriceDriftRow::getMaterialId).containsExactly(CHICKEN_ID);
        assertThat(service.purchasePriceDrift(
                TENANT_ID, WINDOW_FROM, WINDOW_TO, null, OTHER_CATEGORY_ID, null))
            .extracting(PurchasePriceDriftRow::getMaterialId).containsExactly(RICE_ID);
    }

    @Test
    void isolatesTenants() {
        purchase(CHICKEN_ID, WAREHOUSE_ID, "80.00", "2026-03-05");

        Long otherWarehouseId = 997_403L;
        Long otherMaterialId = 997_505L;
        insertWarehouse(otherWarehouseId, OTHER_TENANT_ID, "PDR-WH-X", "Foreign Warehouse");
        insertMaterial(otherMaterialId, OTHER_TENANT_ID, CATEGORY_ID, UOM_KG_ID, UOM_KG_ID,
            "PDR-M-X", "Foreign");
        insertPurchaseBatch(OTHER_TENANT_ID, otherMaterialId, otherWarehouseId,
            "999.00", "2026-03-05", SUPPLIER_ID);

        assertThat(report()).extracting(PurchasePriceDriftRow::getMaterialId)
            .containsExactly(CHICKEN_ID);
        assertThat(service.purchasePriceDrift(
                OTHER_TENANT_ID, WINDOW_FROM, WINDOW_TO, null, null, null))
            .extracting(PurchasePriceDriftRow::getMaterialId).containsExactly(otherMaterialId);
    }

    @Test
    void reportsDeactivatedMaterialsWithTheFlagFalse() {
        jdbcTemplate.update("UPDATE material SET active = FALSE WHERE id = ?", CHICKEN_ID);
        purchase(CHICKEN_ID, WAREHOUSE_ID, "80.00", "2026-03-05");

        assertThat(rowFor(report(), CHICKEN_ID).getMaterialActive()).isFalse();
    }

    @Test
    void anEmptyRangeIsAnEmptyListNotAnError() {
        purchase(CHICKEN_ID, WAREHOUSE_ID, "80.00", "2026-03-05");

        assertThat(service.purchasePriceDrift(TENANT_ID,
            LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30), null, null, null)).isEmpty();
    }

    @Test
    void rejectsAnInvertedRange() {
        assertThatThrownBy(() -> service.purchasePriceDrift(
                TENANT_ID, WINDOW_TO, WINDOW_FROM, null, null, null))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", InventoryErrorCode.VALIDATION_FAILED);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private List<PurchasePriceDriftRow> report() {
        return service.purchasePriceDrift(TENANT_ID, WINDOW_FROM, WINDOW_TO, null, null, null);
    }

    private static PurchasePriceDriftRow rowFor(List<PurchasePriceDriftRow> rows, Long materialId) {
        return rows.stream().filter(r -> r.getMaterialId().equals(materialId)).findFirst()
            .orElseThrow(() -> new AssertionError("no row for material " + materialId));
    }

    private long purchase(Long materialId, Long warehouseId, String unitCost, String date) {
        return purchaseFrom(materialId, warehouseId, unitCost, date, SUPPLIER_ID);
    }

    private long purchaseFrom(Long materialId, Long warehouseId, String unitCost, String date,
                              Long supplierId) {
        return insertPurchaseBatch(TENANT_ID, materialId, warehouseId, unitCost, date, supplierId);
    }

    /** A purchase: PURCHASE_INVOICE-referenced transaction + a batch carrying source_invoice_id. */
    private long insertPurchaseBatch(Long tenantId, Long materialId, Long warehouseId,
                                     String unitCost, String date, Long supplierId) {
        long invoiceId = nextInvoiceId++;
        jdbcTemplate.update("""
            INSERT INTO purchase_invoice (id, tenant_id, warehouse_id, supplier_id, invoice_number,
                                          invoice_date, receipt_date, status, created_at)
            VALUES (?, ?, ?, ?, ?, CAST(? AS date), CAST(? AS date), 'POSTED', CURRENT_TIMESTAMP)
            """, invoiceId, tenantId, warehouseId, supplierId, "PDR-INV-" + invoiceId, date, date);

        long txId = insertTransaction(tenantId, materialId, warehouseId, "PURCHASE", "IN",
            date, "PURCHASE_INVOICE", invoiceId);
        insertBatch(tenantId, materialId, warehouseId, unitCost, date, txId, invoiceId);
        return txId;
    }

    /** A count surplus: opens a batch valued at the average, with no invoice reference. */
    private void countSurplusBatch(Long materialId, Long warehouseId, String unitCost, String date) {
        long txId = insertTransaction(TENANT_ID, materialId, warehouseId, "COUNT_ADJUSTMENT", "IN",
            date, "PHYSICAL_COUNT", 1L);
        insertBatch(TENANT_ID, materialId, warehouseId, unitCost, date, txId, null);
    }

    private void openingBalanceBatch(Long materialId, Long warehouseId, String unitCost, String date) {
        long txId = insertTransaction(TENANT_ID, materialId, warehouseId, "OPENING_BALANCE", "IN",
            date, null, null);
        insertBatch(TENANT_ID, materialId, warehouseId, unitCost, date, txId, null);
    }

    private void insertReversal(Long tenantId, Long materialId, Long warehouseId, long reversesTxId) {
        Long stockUomId = jdbcTemplate.queryForObject(
            "SELECT stock_uom_id FROM material WHERE id = ?", Long.class, materialId);
        jdbcTemplate.update("""
            INSERT INTO inventory_transaction (
                id, tenant_id, warehouse_id, material_id, transaction_type, direction,
                entered_quantity, entered_uom_id, stock_quantity, stock_uom_id,
                reverses_transaction_id, transaction_date, movement_date, created_at)
            VALUES (?, ?, ?, ?, 'PURCHASE', 'OUT', 1, ?, 1, ?, ?,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, nextTransactionId++, tenantId, warehouseId, materialId, stockUomId, stockUomId,
            reversesTxId);
    }

    private long insertTransaction(Long tenantId, Long materialId, Long warehouseId,
                                   String transactionType, String direction, String date,
                                   String referenceType, Long referenceId) {
        Long stockUomId = jdbcTemplate.queryForObject(
            "SELECT stock_uom_id FROM material WHERE id = ?", Long.class, materialId);
        long id = nextTransactionId++;
        jdbcTemplate.update("""
            INSERT INTO inventory_transaction (
                id, tenant_id, warehouse_id, material_id, transaction_type, direction,
                entered_quantity, entered_uom_id, stock_quantity, stock_uom_id,
                reference_type, reference_id, transaction_date, movement_date, created_at)
            VALUES (?, ?, ?, ?, ?, ?, 1, ?, 1, ?, ?, ?,
                    CAST(? AS timestamp), CAST(? AS timestamp), CURRENT_TIMESTAMP)
            """, id, tenantId, warehouseId, materialId, transactionType, direction,
            stockUomId, stockUomId, referenceType, referenceId,
            date + " 00:00:00", date + " 00:00:00");
        return id;
    }

    private void insertBatch(Long tenantId, Long materialId, Long warehouseId, String unitCost,
                             String date, long sourceTransactionId, Long sourceInvoiceId) {
        Long balanceId = resolveBalance(tenantId, materialId, warehouseId);
        jdbcTemplate.update("""
            INSERT INTO stock_batch (id, tenant_id, stock_balance_id, original_quantity,
                                     remaining_quantity, unit_cost, movement_date,
                                     source_transaction_id, source_invoice_id, status, created_at)
            VALUES (?, ?, ?, 10, 10, CAST(? AS numeric), CAST(? AS timestamp), ?, ?, 'OPEN',
                    CURRENT_TIMESTAMP)
            """, nextBatchId++, tenantId, balanceId, unitCost, date + " 00:00:00",
            sourceTransactionId, sourceInvoiceId);
    }

    private Long resolveBalance(Long tenantId, Long materialId, Long warehouseId) {
        List<Long> existing = jdbcTemplate.queryForList("""
            SELECT id FROM stock_balance
            WHERE tenant_id = ? AND material_id = ? AND warehouse_id = ?
            """, Long.class, tenantId, materialId, warehouseId);
        if (!existing.isEmpty()) {
            return existing.getFirst();
        }
        Long displayUomId = jdbcTemplate.queryForObject(
            "SELECT display_uom_id FROM material WHERE id = ?", Long.class, materialId);
        long id = nextBalanceId++;
        jdbcTemplate.update("""
            INSERT INTO stock_balance (id, tenant_id, warehouse_id, material_id, uom_id,
                                       quantity, minimum_quantity, average_cost, created_at)
            VALUES (?, ?, ?, ?, ?, 0, 0, 0, CURRENT_TIMESTAMP)
            """, id, tenantId, warehouseId, materialId, displayUomId);
        return id;
    }

    private void insertTenant(Long id, String name, String code) {
        jdbcTemplate.update("""
            INSERT INTO tenants (id, name, code, status, created_at)
            VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, id, name, code);
    }

    private void insertUom(Long id, Long baseUomId, String code, String name, String symbol,
                           String factorToBase) {
        jdbcTemplate.update("""
            INSERT INTO uom (id, tenant_id, base_uom_id, code, name, symbol, type,
                             factor_to_base, active, created_at)
            VALUES (?, ?, ?, ?, ?, ?, 'WEIGHT', CAST(? AS numeric), TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, id, TENANT_ID, baseUomId, code, name, symbol, factorToBase);
    }

    private void insertCategory(Long id, String code, String name) {
        jdbcTemplate.update("""
            INSERT INTO material_category (id, tenant_id, code, name, name_ar, active, created_at)
            VALUES (?, ?, ?, ?, 'تصنيف', TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, id, TENANT_ID, code, name);
    }

    private void insertSupplier(Long id, String code, String name) {
        jdbcTemplate.update("""
            INSERT INTO supplier (id, tenant_id, code, name, active, created_at)
            VALUES (?, ?, ?, ?, TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, id, TENANT_ID, code, name);
    }

    private void insertWarehouse(Long id, Long tenantId, String code, String name) {
        jdbcTemplate.update("""
            INSERT INTO warehouse (id, tenant_id, branch_id, code, name, type, active, created_at)
            VALUES (?, ?, ?, ?, ?, 'CENTRAL', TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, id, tenantId, tenantId.equals(TENANT_ID) ? BRANCH_ID : null, code, name);
    }

    private void insertMaterial(Long id, Long tenantId, Long categoryId, Long stockUomId,
                                Long displayUomId, String code, String name) {
        jdbcTemplate.update("""
            INSERT INTO material (id, tenant_id, category_id, stock_uom_id, display_uom_id,
                                  code, name, name_ar, active, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'مادة', TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO NOTHING
            """, id, tenantId, categoryId, stockUomId, displayUomId, code, name);
    }
}
