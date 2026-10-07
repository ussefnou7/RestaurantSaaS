package com.smart.restaurant_saas.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.smart.restaurant_saas.auth.security.CurrentUserPrincipal;
import com.smart.restaurant_saas.inventory.core.InventoryLedgerService;
import com.smart.restaurant_saas.inventory.core.LedgerCommand;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionDirection;
import com.smart.restaurant_saas.inventory.core.enums.InventoryTransactionType;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** L043: real HTTP binding, services and migrated PostgreSQL, with no mocked authorization. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ActorAttributionIntegrationTest {
    private static final Long TENANT = 918_001L;
    private static final Long ACTOR = 918_101L;
    private static final Long BRANCH = 918_201L;
    private static final Long WAREHOUSE = 918_301L;
    private static final Long UOM = 918_401L;
    private static final Long CATEGORY = 918_501L;
    private static final Long MATERIAL = 918_601L;
    private static final Long WASTE = 918_701L;
    private static final Long ASSET = 918_801L;
    private static final Long ASSET_LINE = 918_901L;

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private InventoryLedgerService ledgerService;

    @BeforeEach
    void seed() {
        jdbc.update("""
            INSERT INTO tenants (id, name, code, status, created_at, timezone)
            VALUES (?, 'Actor Tenant', 'ACTOR', 'ACTIVE', CURRENT_TIMESTAMP, 'Africa/Cairo')
            """, TENANT);
        jdbc.update("""
            INSERT INTO branches (id, tenant_id, name, code, is_active, created_at)
            VALUES (?, ?, 'Actor Branch', 'ACTOR-BR', TRUE, CURRENT_TIMESTAMP)
            """, BRANCH, TENANT);
        jdbc.update("""
            INSERT INTO warehouse (id, tenant_id, branch_id, code, name, type, active, created_at)
            VALUES (?, ?, ?, 'ACTOR-WH', 'Actor Warehouse', 'CENTRAL', TRUE, CURRENT_TIMESTAMP)
            """, WAREHOUSE, TENANT, BRANCH);
        jdbc.update("""
            INSERT INTO uom (id, tenant_id, code, name, symbol, type, factor_to_base, entered_factor,
                             active, created_at)
            VALUES (?, ?, 'ACTOR-KG', 'Kilogram', 'kg', 'WEIGHT', 1, 1, TRUE, CURRENT_TIMESTAMP)
            """, UOM, TENANT);
        jdbc.update("""
            INSERT INTO material_category (id, tenant_id, code, name, active, created_at)
            VALUES (?, ?, 'ACTOR-FOOD', 'Food', TRUE, CURRENT_TIMESTAMP)
            """, CATEGORY, TENANT);
        jdbc.update("""
            INSERT INTO material (id, tenant_id, category_id, stock_uom_id, display_uom_id,
                                  code, name, active, created_at)
            VALUES (?, ?, ?, ?, ?, 'ACTOR-MAT', 'Material', TRUE, CURRENT_TIMESTAMP)
            """, MATERIAL, TENANT, CATEGORY, UOM, UOM);
        jdbc.update("""
            INSERT INTO waste_document (id, tenant_id, warehouse_id, code, waste_date, reason_code,
                                        status, posted_to_inventory, created_at)
            VALUES (?, ?, ?, 'ACTOR-WASTE', '2026-01-01', 'SPOILED', 'COMPLETE', FALSE, CURRENT_TIMESTAMP)
            """, WASTE, TENANT, WAREHOUSE);
        jdbc.update("""
            INSERT INTO waste_line (waste_document_id, material_id, quantity, uom_id)
            VALUES (?, ?, 2, ?)
            """, WASTE, MATERIAL, UOM);
        jdbc.update("""
            INSERT INTO asset (id, tenant_id, branch_id, name, category, status, created_at)
            VALUES (?, ?, ?, 'Oven', 'KITCHEN_EQUIPMENT', 'ACTIVE', CURRENT_TIMESTAMP)
            """, ASSET, TENANT, BRANCH);
        jdbc.update("""
            INSERT INTO asset_line (id, tenant_id, asset_id, quantity, remaining_quantity,
                                    unit_cost, total_cost, purchase_date, status, created_at)
            VALUES (?, ?, ?, 2, 2, 100, 200, '2026-01-01', 'ACTIVE', CURRENT_TIMESTAMP)
            """, ASSET_LINE, TENANT, ASSET);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"999", "not-a-user-id"})
    void expenseCategoryCreationAndUpdateUseAuthenticatedActor(String header) throws Exception {
        mvc.perform(asActor(post("/api/expense-categories"), header)
                .content("{\"name\":\"Supplies\"}"))
                .andExpect(status().isCreated());
        Long id = jdbc.queryForObject("SELECT id FROM expense_category WHERE tenant_id = ?", Long.class, TENANT);
        mvc.perform(asActor(put("/api/expense-categories/{id}", id), header)
                .content("{\"name\":\"Office supplies\"}"))
                .andExpect(status().isOk());
        entityManager.flush();
        assertThat(jdbc.queryForMap("SELECT created_by, updated_by FROM expense_category WHERE id = ?", id))
                .containsEntry("created_by", ACTOR).containsEntry("updated_by", ACTOR);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"999", "not-a-user-id"})
    void disposalUsesAuthenticatedActorInsteadOfOptionalHeader(String header) throws Exception {
        mvc.perform(asActor(post("/api/assets/{assetId}/lines/{lineId}/disposals", ASSET, ASSET_LINE), header)
                .content("""
                    {"assetId":918801,"assetLineId":918901,"quantityDisposed":1,
                     "reason":"DAMAGED","disposalDate":"2026-01-02"}
                    """))
                .andExpect(status().isCreated());
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT created_by FROM asset_disposal WHERE tenant_id = ?",
                Long.class, TENANT)).isEqualTo(ACTOR);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"999", "not-a-user-id"})
    void wastePostingAttributesBothDocumentAndLedgerToAuthenticatedActor(String header) throws Exception {
        ledgerService.record(LedgerCommand.builder().tenantId(TENANT).warehouseId(WAREHOUSE)
                .materialId(MATERIAL).transactionType(InventoryTransactionType.PURCHASE)
                .direction(InventoryTransactionDirection.IN).enteredQuantity(BigDecimal.TEN)
                .enteredUomId(UOM).enteredUnitCost(BigDecimal.ONE).createdBy(ACTOR).build());
        mvc.perform(asActor(post("/api/inventory/waste-documents/{id}/post", WASTE), header))
                .andExpect(status().isOk());
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT posted_by FROM waste_document WHERE id = ?", Long.class, WASTE))
                .isEqualTo(ACTOR);
        assertThat(jdbc.queryForObject("""
            SELECT created_by FROM inventory_transaction
            WHERE tenant_id = ? AND reference_type = 'WASTE_DOCUMENT' AND reference_id = ?
            """, Long.class, TENANT, WASTE)).isEqualTo(ACTOR);
    }

    @Test
    void forgedActorDoesNotGrantPermissions() throws Exception {
        var principal = new CurrentUserPrincipal(ACTOR, TENANT, "no-permissions", "OWNER", null, false, null);
        mvc.perform(post("/api/inventory/waste-documents/{id}/post", WASTE)
                .with(authentication(new UsernamePasswordAuthenticationToken(principal, null, List.of())))
                .header("X-User-Id", "1"))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT status FROM waste_document WHERE id = ?", String.class, WASTE))
                .isEqualTo("COMPLETE");
    }

    private MockHttpServletRequestBuilder asActor(MockHttpServletRequestBuilder request, String header) {
        var principal = new CurrentUserPrincipal(ACTOR, TENANT, "actor", "SYS_ADMIN", null, false, null);
        request.with(authentication(new UsernamePasswordAuthenticationToken(principal, null, List.of())))
                .header("X-Tenant-Id", TENANT).contentType(MediaType.APPLICATION_JSON);
        if (header != null) {
            request.header("X-User-Id", header);
        }
        return request;
    }
}
