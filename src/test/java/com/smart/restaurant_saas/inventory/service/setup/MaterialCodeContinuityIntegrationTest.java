package com.smart.restaurant_saas.inventory.service.setup;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.restaurant_saas.inventory.material.dto.MaterialRequest;
import com.smart.restaurant_saas.inventory.material.dto.MaterialResponse;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * D112 guard rail, not a D75 test.
 *
 * <p>D112 deleted {@code InvoiceSequenceService} and {@code PhysicalCountCodeSequenceService}, and
 * kept {@code TenantSequenceService}. All three names are similar, and D75's entity codes resolve
 * only through the one that was kept — so deleting the wrong one, or later dropping the table it
 * shares, breaks Material/Supplier/Warehouse/Category/Employee/Job codes while every document
 * numbering test still passes.
 *
 * <p>The sharp edge is the table name: {@code TenantSequenceCounter} is mapped to
 * {@code invoice_sequence}, which reads as though it belonged to the deleted
 * {@code InvoiceSequenceService}. It does not. Rows at {@code year = 0} are D75 entity-code
 * counters and are live; rows at a real year are the retired document counters that
 * {@code document_sequence} replaced. Dropping that table would take the entity codes with it.
 */
@SpringBootTest
@Transactional
class MaterialCodeContinuityIntegrationTest {

    private static final Long TENANT_ID = 996_001L;
    private static final Long UOM_ID = 996_201L;
    private static final Long CATEGORY_ID = 996_301L;

    @Autowired
    private MaterialService materialService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void seedMasterData() {
        jdbcTemplate.update("""
            INSERT INTO tenants (id, name, code, status, created_at, timezone)
            VALUES (?, 'Continuity Tenant', 'D75C', 'ACTIVE', CURRENT_TIMESTAMP, 'Africa/Cairo')
            """, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO uom (id, tenant_id, code, name, symbol, type, factor_to_base,
                             entered_factor, active, created_at)
            VALUES (?, ?, 'D75C-KG', 'Kilogram', 'kg', 'WEIGHT', 1, 1, TRUE, CURRENT_TIMESTAMP)
            """, UOM_ID, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO material_category (id, tenant_id, code, name, active, created_at)
            VALUES (?, ?, 'D75C-FOOD', 'Food', TRUE, CURRENT_TIMESTAMP)
            """, CATEGORY_ID, TENANT_ID);
    }

    @Test
    void materialCodeStillGeneratesThroughTenantSequenceServiceAfterTheD112Deletions() {
        MaterialResponse first = materialService.create(materialRequest("Flour"), TENANT_ID);
        MaterialResponse second = materialService.create(materialRequest("Sugar"), TENANT_ID);

        // D75's shape, untouched by D112: no slash, no year, four digits.
        assertThat(first.getCode()).isEqualTo("D75C-MAT-0001");
        assertThat(second.getCode()).isEqualTo("D75C-MAT-0002");

        // The counter is written through JPA; the reads below go straight to SQL and would
        // otherwise see the session's pending state rather than the row.
        entityManager.flush();

        // Sourced from the year-0 bucket of invoice_sequence, which D112 must not disturb.
        assertThat(jdbcTemplate.queryForObject("""
            SELECT last_seq FROM invoice_sequence
            WHERE tenant_id = ? AND year = 0 AND doc_type = 'MAT'
            """, Integer.class, TENANT_ID))
            .isEqualTo(2);

        // And nothing about creating a Material touched the D112 allocator's table.
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM document_sequence WHERE tenant_id = ?", Integer.class, TENANT_ID))
            .isZero();
    }

    private MaterialRequest materialRequest(String name) {
        MaterialRequest request = new MaterialRequest();
        request.setName(name);
        request.setCategoryId(CATEGORY_ID);
        request.setStockUomId(UOM_ID);
        request.setDisplayUomId(UOM_ID);
        return request;
    }
}
