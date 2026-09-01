package com.smart.restaurant_saas.inventory.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.restaurant_saas.inventory.material.OpeningBalanceService;
import com.smart.restaurant_saas.inventory.material.dto.OpeningBalanceRequest;
import com.smart.restaurant_saas.inventory.purchase.dto.PurchaseInvoiceResponse;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ExpiryAndAgeIntegrationTest {

    private static final Long TENANT_ID = 994_001L;
    private static final Long BRANCH_ID = 994_101L;
    private static final Long UOM_ID = 994_201L;
    private static final Long CATEGORY_ID = 994_301L;
    private static final Long WAREHOUSE_ID = 994_401L;
    private static final Long MATERIAL_ID = 994_501L;
    private static final Long INVOICE_ID = 994_601L;
    private static final Long INVOICE_LINE_ID = 994_701L;
    private static final Long USER_ID = 994_801L;
    private static final LocalDate RECEIPT_DATE = LocalDate.of(2026, 8, 20);
    private static final LocalDate EXPIRY_DATE = LocalDate.of(2027, 2, 20);

    @Autowired
    private PurchaseInvoiceService purchaseInvoiceService;

    @Autowired
    private OpeningBalanceService openingBalanceService;

    @Autowired
    private StockBatchService stockBatchService;

    @Autowired
    private StockBalanceService stockBalanceService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void seedMasterData() {
        jdbcTemplate.update("""
            INSERT INTO tenants (id, name, code, status, created_at, timezone)
            VALUES (?, 'Expiry Tenant', 'EXPIRY_AGE', 'ACTIVE', CURRENT_TIMESTAMP, 'Africa/Cairo')
            """, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO branches (id, tenant_id, name, code, is_active, created_at)
            VALUES (?, ?, 'Expiry Branch', 'EXP-BR-1', TRUE, CURRENT_TIMESTAMP)
            """, BRANCH_ID, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO uom (id, tenant_id, code, name, symbol, type, factor_to_base,
                             entered_factor, active, created_at)
            VALUES (?, ?, 'EXP-KG', 'Kilogram', 'kg', 'WEIGHT', 1, 1, TRUE, CURRENT_TIMESTAMP)
            """, UOM_ID, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO material_category (id, tenant_id, code, name, active, created_at)
            VALUES (?, ?, 'EXP-FOOD', 'Food', TRUE, CURRENT_TIMESTAMP)
            """, CATEGORY_ID, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO warehouse (id, tenant_id, branch_id, code, name, type, active, created_at)
            VALUES (?, ?, ?, 'EXP-WH-1', 'Expiry Warehouse', 'CENTRAL', TRUE, CURRENT_TIMESTAMP)
            """, WAREHOUSE_ID, TENANT_ID, BRANCH_ID);
        jdbcTemplate.update("""
            INSERT INTO material (id, tenant_id, category_id, stock_uom_id, display_uom_id,
                                  code, name, active, expiry_tracked, created_at)
            VALUES (?, ?, ?, ?, ?, 'EXP-YOGURT', 'Yogurt', TRUE, TRUE, CURRENT_TIMESTAMP)
            """, MATERIAL_ID, TENANT_ID, CATEGORY_ID, UOM_ID, UOM_ID);
    }

    @Test
    void purchaseInvoicePostCopiesLineExpiryAndReceiptDateToLiveBatch() {
        seedCompletePurchaseInvoice();

        PurchaseInvoiceResponse response =
            purchaseInvoiceService.post(INVOICE_ID, TENANT_ID, USER_ID);
        entityManager.flush();
        entityManager.clear();

        assertThat(response.getLines()).singleElement()
            .extracting(line -> line.getExpiryDate())
            .isEqualTo(EXPIRY_DATE);
        assertThat(batchDate("expiry_date")).isEqualTo(EXPIRY_DATE);
        assertThat(batchDate("warehouse_entry_date")).isEqualTo(RECEIPT_DATE);
        assertThat(batchTimestamp("movement_date"))
            .isEqualTo(RECEIPT_DATE.atStartOfDay());
    }

    @Test
    void openingBalanceUsesLedgerRecordDateAndLeavesExpiryNull() {
        OpeningBalanceRequest request = new OpeningBalanceRequest();
        request.setWarehouseId(WAREHOUSE_ID);
        request.setMaterialId(MATERIAL_ID);
        request.setQuantity(new BigDecimal("2.000000"));
        request.setUomId(UOM_ID);
        request.setUnitCost(new BigDecimal("5.000000"));

        var response = openingBalanceService.create(request, TENANT_ID, USER_ID);
        entityManager.flush();
        entityManager.clear();

        LocalDate movementDate = jdbcTemplate.queryForObject("""
            SELECT movement_date::date
            FROM inventory_transaction
            WHERE id = ?
            """, LocalDate.class, response.getTransactionId());
        LocalDate warehouseEntryDate = jdbcTemplate.queryForObject("""
            SELECT warehouse_entry_date
            FROM stock_batch
            WHERE source_transaction_id = ?
            """, LocalDate.class, response.getTransactionId());
        LocalDate expiryDate = jdbcTemplate.queryForObject("""
            SELECT expiry_date
            FROM stock_batch
            WHERE source_transaction_id = ?
            """, LocalDate.class, response.getTransactionId());

        assertThat(warehouseEntryDate).isEqualTo(movementDate);
        assertThat(expiryDate).isNull();
    }

    @Test
    void purchaseReturnRestorePreservesOriginalWarehouseEntryDate() {
        seedCompletePurchaseInvoice();
        purchaseInvoiceService.post(INVOICE_ID, TENANT_ID, USER_ID);
        entityManager.flush();
        Long balanceId = jdbcTemplate.queryForObject("""
            SELECT stock_balance_id FROM stock_batch WHERE source_invoice_line_id = ?
            """, Long.class, INVOICE_LINE_ID);
        LocalDate originalEntryDate = batchDate("warehouse_entry_date");

        stockBatchService.depleteSourceBatch(
            balanceId, INVOICE_LINE_ID, new BigDecimal("1.000000"));
        stockBatchService.restoreSourceBatch(
            balanceId, INVOICE_LINE_ID, new BigDecimal("1.000000"), USER_ID);
        entityManager.flush();
        entityManager.clear();

        assertThat(batchDate("warehouse_entry_date")).isEqualTo(originalEntryDate);
        assertThat(batchDate("warehouse_entry_date")).isEqualTo(RECEIPT_DATE);
    }

    @Test
    void schemaStoresOnlySourceDatesAndKeepsBothBatchDatesSeparate() {
        assertThat(columns("stock_batch"))
            .contains("movement_date", "warehouse_entry_date", "expiry_date")
            .doesNotContain("days_remaining", "age_days");
        assertThat(columns("purchase_invoice_line")).contains("expiry_date");
        assertThat(columns("material")).contains("expiry_tracked");
        assertThat(columns("stock_balance")).contains("max_age_days");
        assertThat(columns("physical_count_line")).doesNotContain("expiry_date");

        Integer computedColumnCount = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM information_schema.columns
            WHERE table_schema = 'public'
              AND column_name IN ('days_remaining', 'age_days')
            """, Integer.class);
        assertThat(computedColumnCount).isZero();
    }

    @Test
    void datedExpiredBatchReturnsNegativeDaysRemainingAndIgnoresMaxAge() {
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Cairo"));
        seedCompletePurchaseInvoice(today.minusDays(2), today.minusDays(2));
        purchaseInvoiceService.post(INVOICE_ID, TENANT_ID, USER_ID);
        entityManager.flush();

        Long balanceId = batchBalanceId();
        jdbcTemplate.update("UPDATE stock_balance SET max_age_days = 1 WHERE id = ?", balanceId);
        entityManager.clear();

        assertThat(stockBalanceService.findBatchesForBalance(balanceId, TENANT_ID))
            .singleElement()
            .satisfies(batch -> {
                assertThat(batch.getAgeDays()).isEqualTo(2);
                assertThat(batch.getDaysRemaining()).isEqualTo(-2);
            });
    }

    @Test
    void freshBatchWithinLimitReturnsPositiveDaysRemaining() {
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Cairo"));
        jdbcTemplate.update("UPDATE material SET expiry_tracked = FALSE WHERE id = ?", MATERIAL_ID);
        seedCompletePurchaseInvoice(today.minusDays(2), null);
        purchaseInvoiceService.post(INVOICE_ID, TENANT_ID, USER_ID);
        entityManager.flush();

        Long balanceId = batchBalanceId();
        jdbcTemplate.update("UPDATE stock_balance SET max_age_days = 5 WHERE id = ?", balanceId);
        entityManager.clear();

        assertThat(stockBalanceService.findBatchesForBalance(balanceId, TENANT_ID))
            .singleElement()
            .satisfies(batch -> {
                assertThat(batch.getAgeDays()).isEqualTo(2);
                assertThat(batch.getDaysRemaining()).isEqualTo(3);
            });
    }

    @Test
    void zeroBoundaryReturnsZeroForDatedAndFreshTracks() {
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Cairo"));
        seedCompletePurchaseInvoice(today.minusDays(2), today);
        purchaseInvoiceService.post(INVOICE_ID, TENANT_ID, USER_ID);
        entityManager.flush();

        Long balanceId = batchBalanceId();
        jdbcTemplate.update("UPDATE stock_balance SET max_age_days = 2 WHERE id = ?", balanceId);
        entityManager.clear();

        assertThat(stockBalanceService.findBatchesForBalance(balanceId, TENANT_ID))
            .singleElement()
            .satisfies(batch -> assertThat(batch.getDaysRemaining()).isZero());

        jdbcTemplate.update("UPDATE material SET expiry_tracked = FALSE WHERE id = ?", MATERIAL_ID);
        entityManager.clear();

        assertThat(stockBalanceService.findBatchesForBalance(balanceId, TENANT_ID))
            .singleElement()
            .satisfies(batch -> {
                assertThat(batch.getAgeDays()).isEqualTo(2);
                assertThat(batch.getDaysRemaining()).isZero();
            });
    }

    private void seedCompletePurchaseInvoice() {
        seedCompletePurchaseInvoice(RECEIPT_DATE, EXPIRY_DATE);
    }

    private void seedCompletePurchaseInvoice(LocalDate receiptDate, LocalDate expiryDate) {
        jdbcTemplate.update("""
            INSERT INTO purchase_invoice (
                id, tenant_id, warehouse_id, invoice_number, invoice_date, receipt_date,
                status, subtotal, discount_amount, tax_amount, total_amount, paid_amount,
                payment_status, posted_to_inventory, created_at)
            VALUES (?, ?, ?, 'PINV-EXP-1', ?, ?, 'COMPLETE', 10, 0, 0, 10, 0,
                    'UNPAID', FALSE, CURRENT_TIMESTAMP)
            """, INVOICE_ID, TENANT_ID, WAREHOUSE_ID, receiptDate, receiptDate);
        jdbcTemplate.update("""
            INSERT INTO purchase_invoice_line (
                id, purchase_invoice_id, material_id, quantity, uom_id, unit_cost,
                line_total, discount_percent, discount_amount, line_net_total, expiry_date)
            VALUES (?, ?, ?, 2, ?, 5, 10, 0, 0, 10, ?)
            """, INVOICE_LINE_ID, INVOICE_ID, MATERIAL_ID, UOM_ID, expiryDate);
    }

    private Long batchBalanceId() {
        return jdbcTemplate.queryForObject("""
            SELECT stock_balance_id
            FROM stock_batch
            WHERE source_invoice_line_id = ?
            """, Long.class, INVOICE_LINE_ID);
    }

    private LocalDate batchDate(String column) {
        return jdbcTemplate.queryForObject(
            "SELECT %s FROM stock_batch WHERE source_invoice_line_id = ?".formatted(column),
            LocalDate.class, INVOICE_LINE_ID);
    }

    private LocalDateTime batchTimestamp(String column) {
        return jdbcTemplate.queryForObject(
            "SELECT %s FROM stock_batch WHERE source_invoice_line_id = ?".formatted(column),
            LocalDateTime.class, INVOICE_LINE_ID);
    }

    private List<String> columns(String tableName) {
        return jdbcTemplate.queryForList("""
            SELECT column_name
            FROM information_schema.columns
            WHERE table_schema = 'public' AND table_name = ?
            """, String.class, tableName);
    }
}
