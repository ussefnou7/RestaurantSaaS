package com.smart.restaurant_saas.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.smart.restaurant_saas.auth.service.JwtService;
import com.smart.restaurant_saas.inventory.core.enums.DocumentStatus;
import com.smart.restaurant_saas.tenant.support.CrossTenantFixture;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The three paths the tenant isolation audit confirmed CRITICAL on 63ff8e7. Each sends tenant A's
 * real, signed JWT together with {@code X-Tenant-Id: B} — the exact forgery the audit performed —
 * and asserts that nothing lands in tenant B.
 *
 * <p><strong>Every assertion is on stored state, not only on status codes.</strong> A status code
 * says the request ended; it does not say the write was skipped. These paths write the inventory
 * ledger, and a 500 raised after a partial write would satisfy a status-only assertion while
 * leaving tenant B's stock changed.
 *
 * <p>Note what "rejected" means per path, because it is not uniform and the difference is the
 * point of the fix rather than a gap in it:
 *
 * <ul>
 *   <li>The two {@code post} transitions address a document by id. That id belongs to tenant B,
 *       the caller resolves to tenant A, so the lookup misses and the request fails — the
 *       document is never found, so it can never be posted.</li>
 *   <li>{@code POST /api/uom} creates a new row rather than addressing an existing one, so there
 *       is nothing to miss. It succeeds, and the row is created <em>in tenant A</em>. That is the
 *       fix working: the header no longer selects a tenant, so a create cannot be aimed at B. The
 *       invariant is "nothing appears in B", not "the call fails".</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CrossTenantIsolationIntegrationTest {

    private static final long BASE = 977_000L;

    private static final long WAREHOUSE_B_ID = BASE + 401;
    private static final long UOM_B_ID = BASE + 501;
    private static final long CATEGORY_B_ID = BASE + 601;
    private static final long MATERIAL_B_ID = BASE + 701;
    private static final long SUPPLIER_B_ID = BASE + 801;
    private static final long INVOICE_B_ID = BASE + 901;
    private static final long INVOICE_LINE_B_ID = BASE + 911;
    private static final long RETURN_B_ID = BASE + 921;
    private static final long RETURN_LINE_B_ID = BASE + 931;

    /** A custom tenant UOM must name a base UOM of the same type, so tenant A needs one. */
    private static final long BASE_UOM_A_ID = BASE + 511;
    private static final long STOCK_BALANCE_B_ID = BASE + 941;
    private static final long RECEIPT_TXN_B_ID = BASE + 951;
    private static final long STOCK_BATCH_B_ID = BASE + 961;

    private static final long WAREHOUSE_A_ID = BASE + 402;
    private static final long SHARED_WEIGHT_ROOT_ID = BASE + 502;
    private static final long WASTE_UOM_A_ID = BASE + 512;
    private static final long CATEGORY_A_ID = BASE + 602;
    private static final long MATERIAL_A_ID = BASE + 702;
    private static final long STOCK_BALANCE_A_ID = BASE + 942;
    private static final long RECEIPT_TXN_A_ID = BASE + 952;
    private static final long STOCK_BATCH_A_ID = BASE + 962;
    private static final long WASTE_DOCUMENT_A_ID = BASE + 971;
    private static final long POISONED_WASTE_DOCUMENT_A_ID = BASE + 972;
    private static final long POISONED_WASTE_LINE_A_ID = BASE + 982;
    /**
     * Twin of the poisoned document, identical in every field except that its line carries
     * tenant A's own UOM. It is a separate document rather than a repair of the poisoned one
     * because this class is {@code @Transactional}: MockMvc requests share the test's
     * persistence context, so a {@code jdbcTemplate} UPDATE of the poisoned line is invisible
     * to a subsequent post, which re-reads the still-managed stale entity.
     */
    private static final long CONTROL_WASTE_DOCUMENT_A_ID = BASE + 973;
    private static final long CONTROL_WASTE_LINE_A_ID = BASE + 983;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    private CrossTenantFixture fixture;
    private String tenantAToken;
    private String tenantBToken;
    private long tenantAId;
    private long tenantBId;

    @BeforeEach
    void seed() {
        fixture = new CrossTenantFixture(jdbcTemplate, jwtService, BASE);
        tenantAId = fixture.tenantId(0);
        tenantBId = fixture.tenantId(1);

        cleanUp();
        fixture.reset(2);

        tenantAToken = fixture.seedTenantWithUser(
            0, "A", "INVENTORY_SETUP_MANAGE", "INVENTORY_PURCHASE_MANAGE",
            "INVENTORY_STOCK_MANAGE");
        tenantBToken = fixture.seedTenantWithUser(
            1, "B", "INVENTORY_SETUP_MANAGE", "INVENTORY_PURCHASE_MANAGE");

        seedTenantBPurchaseDocuments();
        seedTenantAWasteDocuments();
    }

    // ---------------------------------------------------------------- path 1

    @Test
    void uomCreateCannotBeAimedAtAnotherTenant() throws Exception {
        long uomsInBBefore = countUomsInTenantB();

        mockMvc.perform(post("/api/uom")
                .header("Authorization", CrossTenantFixture.bearer(tenantAToken))
                .header("X-Tenant-Id", tenantBId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"code":"XT-FORGED","name":"Forged","symbol":"xf","type":"COUNT",
                     "factorToBase":2,"baseUom":%d}
                    """.formatted(BASE_UOM_A_ID)));

        assertThat(countUomsInTenantB())
            .as("a create aimed at tenant B must not produce a row in tenant B")
            .isEqualTo(uomsInBBefore);

        assertThat(countUomsInTenant(tenantAId, "XT-FORGED"))
            .as("the row belongs to the caller's own tenant, resolved from the JWT")
            .isEqualTo(1);
    }

    // ---------------------------------------------------------------- path 2

    @Test
    void purchaseInvoicePostCannotBeAimedAtAnotherTenant() throws Exception {
        long movementsBefore = inventoryTransactionsFor(tenantBId);

        mockMvc.perform(post("/api/inventory/purchase-invoices/" + INVOICE_B_ID + "/post")
                .header("Authorization", CrossTenantFixture.bearer(tenantAToken))
                .header("X-Tenant-Id", tenantBId))
            .andExpect(result -> assertThat(result.getResponse().getStatus())
                .as("tenant A must not be able to post tenant B's invoice")
                .isGreaterThanOrEqualTo(400));

        assertThat(invoiceStatus(INVOICE_B_ID))
            .as("tenant B's invoice must be untouched, still awaiting its own tenant to post it")
            .isEqualTo(DocumentStatus.COMPLETE.name());
        assertThat(postedToInventory("purchase_invoice", INVOICE_B_ID)).isFalse();
        assertThat(inventoryTransactionsFor(tenantBId))
            .as("no stock movement may be written for tenant B")
            .isEqualTo(movementsBefore);

        postsSuccessfullyForItsOwnTenant(
            "/api/inventory/purchase-invoices/" + INVOICE_B_ID + "/post");
    }

    // ---------------------------------------------------------------- path 3

    @Test
    void purchaseReturnPostCannotBeAimedAtAnotherTenant() throws Exception {
        long movementsBefore = inventoryTransactionsFor(tenantBId);

        mockMvc.perform(post("/api/inventory/purchase-returns/" + RETURN_B_ID + "/post")
                .header("Authorization", CrossTenantFixture.bearer(tenantAToken))
                .header("X-Tenant-Id", tenantBId))
            .andExpect(result -> assertThat(result.getResponse().getStatus())
                .as("tenant A must not be able to post tenant B's return")
                .isGreaterThanOrEqualTo(400));

        assertThat(returnStatus(RETURN_B_ID))
            .as("tenant B's return must be untouched")
            .isEqualTo(DocumentStatus.COMPLETE.name());
        assertThat(postedToInventory("purchase_return", RETURN_B_ID)).isFalse();
        assertThat(inventoryTransactionsFor(tenantBId))
            .as("no stock movement may be written for tenant B")
            .isEqualTo(movementsBefore);

        postsSuccessfullyForItsOwnTenant(
            "/api/inventory/purchase-returns/" + RETURN_B_ID + "/post");
    }

    // ---------------------------------------------------------------- path 4

    @Test
    void wasteLinesAndLedgerRejectForeignConvertibleUom() throws Exception {
        mockMvc.perform(post("/api/inventory/waste-documents/" + WASTE_DOCUMENT_A_ID + "/lines")
                .header("Authorization", CrossTenantFixture.bearer(tenantAToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(wasteLineJson(WASTE_UOM_A_ID)))
            .andExpect(result -> assertThat(result.getResponse().getStatus())
                .as("control: tenant A's convertible UOM must be accepted. Body: %s",
                    result.getResponse().getContentAsString())
                .isBetween(200, 299));

        long ownLineId = singleWasteLineId(WASTE_DOCUMENT_A_ID);
        assertThat(wasteLineUom(ownLineId)).isEqualTo(WASTE_UOM_A_ID);

        mockMvc.perform(post("/api/inventory/waste-documents/" + WASTE_DOCUMENT_A_ID + "/lines")
                .header("Authorization", CrossTenantFixture.bearer(tenantAToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(wasteLineJson(UOM_B_ID)))
            .andExpect(result -> assertThat(result.getResponse().getStatus())
                .as("foreign convertible UOM must be rejected. Body: %s",
                    result.getResponse().getContentAsString())
                .isGreaterThanOrEqualTo(400));
        assertThat(wasteLineCount(WASTE_DOCUMENT_A_ID))
            .as("a rejected add must not create a waste_line row")
            .isEqualTo(1);

        mockMvc.perform(put("/api/inventory/waste-documents/" + WASTE_DOCUMENT_A_ID
                + "/lines/" + ownLineId)
                .header("Authorization", CrossTenantFixture.bearer(tenantAToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(wasteUpdateLineJson(UOM_B_ID)))
            .andExpect(result -> assertThat(result.getResponse().getStatus())
                .as("line update must reject a foreign convertible UOM. Body: %s",
                    result.getResponse().getContentAsString())
                .isGreaterThanOrEqualTo(400));
        assertThat(wasteLineUom(ownLineId))
            .as("a rejected update must preserve the tenant-owned UOM")
            .isEqualTo(WASTE_UOM_A_ID);

        postWasteTransition(WASTE_DOCUMENT_A_ID, "complete", 200, 299);
        postWasteTransition(WASTE_DOCUMENT_A_ID, "post", 200, 299);
        assertThat(wasteTransactionsUsingUom(WASTE_DOCUMENT_A_ID, WASTE_UOM_A_ID))
            .as("control: posting with tenant A's UOM must reach the ledger")
            .isEqualTo(1);

        mockMvc.perform(post("/api/inventory/waste-documents/"
                + POISONED_WASTE_DOCUMENT_A_ID + "/post")
                .header("Authorization", CrossTenantFixture.bearer(tenantAToken)))
            .andExpect(result -> assertThat(result.getResponse().getStatus())
                .as("ledger must reject a pre-existing line carrying a foreign convertible UOM. Body: %s",
                    result.getResponse().getContentAsString())
                .isGreaterThanOrEqualTo(400));
        assertThat(wasteTransactionsUsingUom(POISONED_WASTE_DOCUMENT_A_ID, UOM_B_ID))
            .as("no foreign entered_uom_id may be written")
            .isZero();

        postWasteTransition(CONTROL_WASTE_DOCUMENT_A_ID, "post", 200, 299);
        assertThat(wasteTransactionsUsingUom(CONTROL_WASTE_DOCUMENT_A_ID, WASTE_UOM_A_ID))
            .as("control: the twin document, differing only in uom_id, posts successfully")
            .isEqualTo(1);
        assertThat(wasteTransactionsUsingUom(CONTROL_WASTE_DOCUMENT_A_ID, UOM_B_ID))
            .as("the successful twin still must not write the foreign entered_uom_id")
            .isZero();
    }

    /**
     * The control leg, and the reason these tests are worth anything.
     *
     * <p>Asserting only "tenant A got a 4xx" is satisfied by <em>any</em> failure. Both of these
     * documents initially failed for reasons that had nothing to do with tenancy — the return for
     * a missing stock_balance, then for a missing stock_batch — so the isolation assertion passed
     * while isolation was demonstrably broken. Verified by reintroducing the vulnerability: the
     * return test passed anyway, twice.
     *
     * <p>Posting the same document as its own tenant must therefore succeed. If it does, the
     * document was genuinely postable, and tenant A's rejection can only have been about tenancy.
     */
    private void postsSuccessfullyForItsOwnTenant(String path) throws Exception {
        mockMvc.perform(post(path)
                .header("Authorization", CrossTenantFixture.bearer(tenantBToken)))
            .andExpect(result -> assertThat(result.getResponse().getStatus())
                .as("control: %s must be postable by its own tenant, otherwise the rejection "
                    + "above proves nothing about tenancy. Body: %s",
                    path, result.getResponse().getContentAsString())
                .isBetween(200, 299));
    }

    // ---------------------------------------------------------------- helpers

    private long countUomsInTenantB() {
        return jdbcTemplate.queryForObject(
            "SELECT count(*) FROM uom WHERE tenant_id = ?", Long.class, tenantBId);
    }

    private long countUomsInTenant(long tenantId, String code) {
        return jdbcTemplate.queryForObject(
            "SELECT count(*) FROM uom WHERE tenant_id = ? AND code = ?",
            Long.class, tenantId, code);
    }

    private String invoiceStatus(long id) {
        return jdbcTemplate.queryForObject(
            "SELECT status FROM purchase_invoice WHERE id = ?", String.class, id);
    }

    private String returnStatus(long id) {
        return jdbcTemplate.queryForObject(
            "SELECT status FROM purchase_return WHERE id = ?", String.class, id);
    }

    private boolean postedToInventory(String table, long id) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
            "SELECT posted_to_inventory FROM " + table + " WHERE id = ?", Boolean.class, id));
    }

    private long inventoryTransactionsFor(long tenantId) {
        return jdbcTemplate.queryForObject(
            "SELECT count(*) FROM inventory_transaction WHERE tenant_id = ?", Long.class, tenantId);
    }

    private String wasteLineJson(long uomId) {
        return """
            {"materialId":%d,"quantity":1,"uomId":%d,"notes":"tenant isolation"}
            """.formatted(MATERIAL_A_ID, uomId);
    }

    private String wasteUpdateLineJson(long uomId) {
        return """
            {"quantity":1,"uomId":%d,"notes":"tenant isolation update"}
            """.formatted(uomId);
    }

    private long singleWasteLineId(long documentId) {
        return jdbcTemplate.queryForObject(
            "SELECT id FROM waste_line WHERE waste_document_id = ?", Long.class, documentId);
    }

    private long wasteLineCount(long documentId) {
        return jdbcTemplate.queryForObject(
            "SELECT count(*) FROM waste_line WHERE waste_document_id = ?", Long.class, documentId);
    }

    private long wasteLineUom(long lineId) {
        return jdbcTemplate.queryForObject(
            "SELECT uom_id FROM waste_line WHERE id = ?", Long.class, lineId);
    }

    private long wasteTransactionsUsingUom(long documentId, long uomId) {
        return jdbcTemplate.queryForObject("""
            SELECT count(*) FROM inventory_transaction
            WHERE tenant_id = ? AND reference_type = 'WASTE_DOCUMENT' AND reference_id = ?
              AND entered_uom_id = ?
            """, Long.class, tenantAId, documentId, uomId);
    }

    private void postWasteTransition(long documentId, String transition,
                                     int minimumStatus, int maximumStatus) throws Exception {
        String path = "/api/inventory/waste-documents/" + documentId + "/" + transition;
        mockMvc.perform(post(path)
                .header("Authorization", CrossTenantFixture.bearer(tenantAToken)))
            .andExpect(result -> assertThat(result.getResponse().getStatus())
                .as("control: %s must succeed. Body: %s",
                    path, result.getResponse().getContentAsString())
                .isBetween(minimumStatus, maximumStatus));
    }

    private void cleanUp() {
        jdbcTemplate.update("DELETE FROM stock_batch WHERE id IN (?, ?)",
            STOCK_BATCH_A_ID, STOCK_BATCH_B_ID);
        jdbcTemplate.update("DELETE FROM waste_line WHERE waste_document_id IN (?, ?, ?)",
            WASTE_DOCUMENT_A_ID, POISONED_WASTE_DOCUMENT_A_ID, CONTROL_WASTE_DOCUMENT_A_ID);
        jdbcTemplate.update("DELETE FROM waste_document WHERE id IN (?, ?, ?)",
            WASTE_DOCUMENT_A_ID, POISONED_WASTE_DOCUMENT_A_ID, CONTROL_WASTE_DOCUMENT_A_ID);
        jdbcTemplate.update("DELETE FROM stock_balance WHERE id = ?", STOCK_BALANCE_B_ID);
        jdbcTemplate.update("DELETE FROM stock_balance WHERE id = ?", STOCK_BALANCE_A_ID);
        jdbcTemplate.update("DELETE FROM purchase_return_line WHERE id = ?", RETURN_LINE_B_ID);
        jdbcTemplate.update("DELETE FROM purchase_return WHERE id = ?", RETURN_B_ID);
        jdbcTemplate.update("DELETE FROM purchase_invoice_line WHERE id = ?", INVOICE_LINE_B_ID);
        jdbcTemplate.update("DELETE FROM purchase_invoice WHERE id = ?", INVOICE_B_ID);
        jdbcTemplate.update("DELETE FROM inventory_transaction WHERE tenant_id IN (?, ?)",
            fixture.tenantId(0), fixture.tenantId(1));
        jdbcTemplate.update("DELETE FROM material WHERE id = ?", MATERIAL_B_ID);
        jdbcTemplate.update("DELETE FROM material WHERE id = ?", MATERIAL_A_ID);
        jdbcTemplate.update("DELETE FROM supplier WHERE id = ?", SUPPLIER_B_ID);
        jdbcTemplate.update("DELETE FROM material_category WHERE id = ?", CATEGORY_B_ID);
        jdbcTemplate.update("DELETE FROM material_category WHERE id = ?", CATEGORY_A_ID);
        jdbcTemplate.update("DELETE FROM warehouse WHERE id = ?", WAREHOUSE_B_ID);
        jdbcTemplate.update("DELETE FROM warehouse WHERE id = ?", WAREHOUSE_A_ID);
        jdbcTemplate.update("DELETE FROM uom WHERE tenant_id IN (?, ?)",
            fixture.tenantId(0), fixture.tenantId(1));
        jdbcTemplate.update("DELETE FROM uom WHERE id = ?", SHARED_WEIGHT_ROOT_ID);
    }

    /** A COMPLETE purchase invoice and a COMPLETE purchase return, both owned by tenant B. */
    private void seedTenantBPurchaseDocuments() {
        jdbcTemplate.update("""
            INSERT INTO uom (id, tenant_id, code, name, symbol, type, factor_to_base,
                             entered_factor, active, created_at)
            VALUES (?, NULL, 'XT-WEIGHT-ROOT', 'Shared weight root', 'swr', 'WEIGHT',
                    1, 1, TRUE, CURRENT_TIMESTAMP)
            """, SHARED_WEIGHT_ROOT_ID);
        jdbcTemplate.update("""
            INSERT INTO uom (id, tenant_id, code, name, symbol, type, factor_to_base,
                             entered_factor, active, created_at)
            VALUES (?, ?, 'XT-EACH-A', 'Each', 'ea', 'COUNT', 1, 1, TRUE, CURRENT_TIMESTAMP)
            """, BASE_UOM_A_ID, tenantAId);
        jdbcTemplate.update("""
            INSERT INTO warehouse (id, tenant_id, code, name, type, active, created_at)
            VALUES (?, ?, 'XT-WH-B', 'Tenant B Warehouse', 'CENTRAL', TRUE, CURRENT_TIMESTAMP)
            """, WAREHOUSE_B_ID, tenantBId);
        jdbcTemplate.update("""
            INSERT INTO uom (id, tenant_id, code, name, symbol, type, factor_to_base,
                             base_uom_id, entered_factor, entered_against_uom_id,
                             active, created_at)
            VALUES (?, ?, 'XT-FOREIGN-B', 'Tenant B weight pack', 'bwp', 'WEIGHT',
                    10, ?, 10, ?, TRUE, CURRENT_TIMESTAMP)
            """, UOM_B_ID, tenantBId, SHARED_WEIGHT_ROOT_ID, SHARED_WEIGHT_ROOT_ID);
        jdbcTemplate.update("""
            INSERT INTO material_category (id, tenant_id, code, name, active, created_at)
            VALUES (?, ?, 'XT-CAT-B', 'Tenant B materials', TRUE, CURRENT_TIMESTAMP)
            """, CATEGORY_B_ID, tenantBId);
        jdbcTemplate.update("""
            INSERT INTO material (id, tenant_id, category_id, stock_uom_id, display_uom_id,
                                  code, name, active, created_at)
            VALUES (?, ?, ?, ?, ?, 'XT-MAT-B', 'Tenant B Flour', TRUE, CURRENT_TIMESTAMP)
            """, MATERIAL_B_ID, tenantBId, CATEGORY_B_ID, UOM_B_ID, UOM_B_ID);
        jdbcTemplate.update("""
            INSERT INTO supplier (id, tenant_id, code, name, active, created_at)
            VALUES (?, ?, 'XT-SUP-B', 'Tenant B Supplier', TRUE, CURRENT_TIMESTAMP)
            """, SUPPLIER_B_ID, tenantBId);

        jdbcTemplate.update("""
            INSERT INTO purchase_invoice (id, tenant_id, supplier_id, warehouse_id, invoice_number,
                                          invoice_date, receipt_date, status, subtotal,
                                          total_amount, created_at, posted_to_inventory)
            VALUES (?, ?, ?, ?, 'XT-PINV-B', ?, ?, ?, 100, 100, CURRENT_TIMESTAMP, FALSE)
            """, INVOICE_B_ID, tenantBId, SUPPLIER_B_ID, WAREHOUSE_B_ID,
            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1), DocumentStatus.COMPLETE.name());
        jdbcTemplate.update("""
            INSERT INTO purchase_invoice_line (id, purchase_invoice_id, material_id, quantity,
                                               uom_id, unit_cost, line_total, line_net_total)
            VALUES (?, ?, ?, 10, ?, 10, 100, 100)
            """, INVOICE_LINE_B_ID, INVOICE_B_ID, MATERIAL_B_ID, UOM_B_ID);

        jdbcTemplate.update("""
            INSERT INTO purchase_return (id, tenant_id, original_invoice_id, warehouse_id,
                                         return_date, reason, status, subtotal, total_amount,
                                         created_at, posted_to_inventory)
            VALUES (?, ?, ?, ?, ?, 'DAMAGED', ?, 20, 20, CURRENT_TIMESTAMP, FALSE)
            """, RETURN_B_ID, tenantBId, INVOICE_B_ID, WAREHOUSE_B_ID,
            LocalDate.of(2026, 7, 5), DocumentStatus.COMPLETE.name());
        jdbcTemplate.update("""
            INSERT INTO purchase_return_line (id, purchase_return_id, original_line_id, material_id,
                                              quantity, uom_id, unit_cost, line_total)
            VALUES (?, ?, ?, ?, 2, ?, 10, 20)
            """, RETURN_LINE_B_ID, RETURN_B_ID, INVOICE_LINE_B_ID, MATERIAL_B_ID, UOM_B_ID);

        // Posting a return takes stock OUT, and PurchaseReturnService rejects a material with no
        // stock_balance row as INSUFFICIENT_STOCK. Without this row the return post fails for a
        // reason that has nothing to do with tenancy, and the isolation assertion below passes
        // whether or not isolation actually holds -- verified: the test did exactly that until
        // this was added. Stocking tenant B's material leaves tenant isolation as the only thing
        // standing between tenant A and this document.
        jdbcTemplate.update("""
            INSERT INTO stock_balance (id, tenant_id, warehouse_id, material_id, quantity, uom_id,
                                       average_cost, created_at)
            VALUES (?, ?, ?, ?, 100, ?, 10, CURRENT_TIMESTAMP)
            """, STOCK_BALANCE_B_ID, tenantBId, WAREHOUSE_B_ID, MATERIAL_B_ID, UOM_B_ID);

        // The return is costed against the batch its originating invoice line created, so that
        // batch and the receipt transaction behind it have to exist too.
        jdbcTemplate.update("""
            INSERT INTO inventory_transaction (id, tenant_id, warehouse_id, material_id,
                                               transaction_type, direction, entered_quantity,
                                               entered_uom_id, stock_quantity, stock_uom_id,
                                               transaction_date, movement_date, created_at)
            VALUES (?, ?, ?, ?, 'PURCHASE', 'IN', 10, ?, 10, ?,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, RECEIPT_TXN_B_ID, tenantBId, WAREHOUSE_B_ID, MATERIAL_B_ID, UOM_B_ID, UOM_B_ID);
        jdbcTemplate.update("""
            INSERT INTO stock_batch (id, tenant_id, stock_balance_id, original_quantity,
                                     remaining_quantity, unit_cost, movement_date,
                                     source_transaction_id, source_invoice_id,
                                     source_invoice_line_id, status, created_at)
            VALUES (?, ?, ?, 10, 10, 10, CURRENT_TIMESTAMP, ?, ?, ?, 'OPEN', CURRENT_TIMESTAMP)
            """, STOCK_BATCH_B_ID, tenantBId, STOCK_BALANCE_B_ID, RECEIPT_TXN_B_ID,
            INVOICE_B_ID, INVOICE_LINE_B_ID);
    }

    private void seedTenantAWasteDocuments() {
        jdbcTemplate.update("""
            INSERT INTO warehouse (id, tenant_id, code, name, type, active, created_at)
            VALUES (?, ?, 'XT-WH-A', 'Tenant A Warehouse', 'CENTRAL', TRUE, CURRENT_TIMESTAMP)
            """, WAREHOUSE_A_ID, tenantAId);
        jdbcTemplate.update("""
            INSERT INTO uom (id, tenant_id, code, name, symbol, type, factor_to_base,
                             base_uom_id, entered_factor, entered_against_uom_id,
                             active, created_at)
            VALUES (?, ?, 'XT-OWN-A', 'Tenant A weight pack', 'awp', 'WEIGHT',
                    5, ?, 5, ?, TRUE, CURRENT_TIMESTAMP)
            """, WASTE_UOM_A_ID, tenantAId, SHARED_WEIGHT_ROOT_ID, SHARED_WEIGHT_ROOT_ID);
        jdbcTemplate.update("""
            INSERT INTO material_category (id, tenant_id, code, name, active, created_at)
            VALUES (?, ?, 'XT-CAT-A', 'Tenant A materials', TRUE, CURRENT_TIMESTAMP)
            """, CATEGORY_A_ID, tenantAId);
        jdbcTemplate.update("""
            INSERT INTO material (id, tenant_id, category_id, stock_uom_id, display_uom_id,
                                  code, name, active, created_at)
            VALUES (?, ?, ?, ?, ?, 'XT-MAT-A', 'Tenant A Flour', TRUE, CURRENT_TIMESTAMP)
            """, MATERIAL_A_ID, tenantAId, CATEGORY_A_ID, WASTE_UOM_A_ID, WASTE_UOM_A_ID);
        jdbcTemplate.update("""
            INSERT INTO stock_balance (id, tenant_id, warehouse_id, material_id, quantity, uom_id,
                                       average_cost, created_at)
            VALUES (?, ?, ?, ?, 100, ?, 10, CURRENT_TIMESTAMP)
            """, STOCK_BALANCE_A_ID, tenantAId, WAREHOUSE_A_ID, MATERIAL_A_ID, WASTE_UOM_A_ID);
        jdbcTemplate.update("""
            INSERT INTO inventory_transaction (id, tenant_id, warehouse_id, material_id,
                                               transaction_type, direction, entered_quantity,
                                               entered_uom_id, stock_quantity, stock_uom_id,
                                               transaction_date, movement_date, created_at)
            VALUES (?, ?, ?, ?, 'PURCHASE', 'IN', 100, ?, 100, ?,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, RECEIPT_TXN_A_ID, tenantAId, WAREHOUSE_A_ID, MATERIAL_A_ID,
            WASTE_UOM_A_ID, WASTE_UOM_A_ID);
        jdbcTemplate.update("""
            INSERT INTO stock_batch (id, tenant_id, stock_balance_id, original_quantity,
                                     remaining_quantity, unit_cost, movement_date,
                                     source_transaction_id, status, created_at)
            VALUES (?, ?, ?, 100, 100, 10, CURRENT_TIMESTAMP, ?, 'OPEN', CURRENT_TIMESTAMP)
            """, STOCK_BATCH_A_ID, tenantAId, STOCK_BALANCE_A_ID, RECEIPT_TXN_A_ID);
        jdbcTemplate.update("""
            INSERT INTO waste_document (id, tenant_id, warehouse_id, code, waste_date,
                                        reason_code, status, created_at, posted_to_inventory)
            VALUES (?, ?, ?, 'XT-WASTE-A', ?, 'DAMAGED', 'DRAFT', CURRENT_TIMESTAMP, FALSE),
                   (?, ?, ?, 'XT-WASTE-POISONED-A', ?, 'DAMAGED', 'COMPLETE',
                    CURRENT_TIMESTAMP, FALSE),
                   (?, ?, ?, 'XT-WASTE-CONTROL-A', ?, 'DAMAGED', 'COMPLETE',
                    CURRENT_TIMESTAMP, FALSE)
            """, WASTE_DOCUMENT_A_ID, tenantAId, WAREHOUSE_A_ID, LocalDate.of(2026, 7, 8),
            POISONED_WASTE_DOCUMENT_A_ID, tenantAId, WAREHOUSE_A_ID, LocalDate.of(2026, 7, 9),
            CONTROL_WASTE_DOCUMENT_A_ID, tenantAId, WAREHOUSE_A_ID, LocalDate.of(2026, 7, 9));
        // The two lines differ only in uom_id, so the post outcome can differ only by UOM visibility.
        jdbcTemplate.update("""
            INSERT INTO waste_line (id, waste_document_id, material_id, quantity, uom_id, notes)
            VALUES (?, ?, ?, 1, ?, 'pre-existing foreign UOM'),
                   (?, ?, ?, 1, ?, 'pre-existing foreign UOM')
            """,
            POISONED_WASTE_LINE_A_ID, POISONED_WASTE_DOCUMENT_A_ID, MATERIAL_A_ID, UOM_B_ID,
            CONTROL_WASTE_LINE_A_ID, CONTROL_WASTE_DOCUMENT_A_ID, MATERIAL_A_ID, WASTE_UOM_A_ID);
    }
}
