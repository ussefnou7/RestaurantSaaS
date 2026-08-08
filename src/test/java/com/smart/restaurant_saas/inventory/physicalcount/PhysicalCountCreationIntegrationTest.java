package com.smart.restaurant_saas.inventory.physicalcount;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.restaurant_saas.inventory.core.PhysicalCountService;
import com.smart.restaurant_saas.inventory.physicalcount.dto.PhysicalCountRequest;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class PhysicalCountCreationIntegrationTest {

    private static final Long TENANT_ID = 992_001L;
    private static final Long BRANCH_ID = 992_101L;
    private static final Long UOM_ID = 992_201L;
    private static final Long CATEGORY_ID = 992_301L;
    private static final Long WAREHOUSE_ID = 992_401L;
    private static final Long MATERIAL_ID = 992_501L;
    private static final LocalDate SCHEDULED_DATE = LocalDate.of(2026, 8, 1);

    @Autowired
    private PhysicalCountService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seed() {
        jdbcTemplate.update("""
            INSERT INTO tenants (id, name, code, status, created_at, timezone)
            VALUES (?, 'Count Code Tenant', 'COUNT_CODE', 'ACTIVE', CURRENT_TIMESTAMP, 'Africa/Cairo')
            """, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO branches (id, tenant_id, name, code, is_active, created_at)
            VALUES (?, ?, 'Main Branch', 'CC-BR-1', TRUE, CURRENT_TIMESTAMP)
            """, BRANCH_ID, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO uom (id, tenant_id, code, name, symbol, type, factor_to_base, entered_factor,
                             active, created_at)
            VALUES (?, ?, 'CC-KG', 'Kilogram', 'kg', 'WEIGHT', 1, 1, TRUE, CURRENT_TIMESTAMP)
            """, UOM_ID, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO material_category (id, tenant_id, code, name, active, created_at)
            VALUES (?, ?, 'CC-FOOD', 'Food', TRUE, CURRENT_TIMESTAMP)
            """, CATEGORY_ID, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO warehouse (id, tenant_id, branch_id, code, name, type, active, created_at)
            VALUES (?, ?, ?, 'CC-WH-1', 'Main Warehouse', 'CENTRAL', TRUE, CURRENT_TIMESTAMP)
            """, WAREHOUSE_ID, TENANT_ID, BRANCH_ID);
        jdbcTemplate.update("""
            INSERT INTO material (id, tenant_id, category_id, stock_uom_id, display_uom_id,
                                  code, name, active, created_at)
            VALUES (?, ?, ?, ?, ?, 'CC-FLOUR', 'Flour', TRUE, CURRENT_TIMESTAMP)
            """, MATERIAL_ID, TENANT_ID, CATEGORY_ID, UOM_ID, UOM_ID);
    }

    @Test
    void twoCountsForSameWarehouseAndDayPersistDistinctSequencedCodes() {
        PhysicalCountRequest request = new PhysicalCountRequest();
        request.setWarehouseId(WAREHOUSE_ID);
        request.setScheduledDate(SCHEDULED_DATE);
        request.setMaterialIds(List.of(MATERIAL_ID));

        var first = service.create(request, TENANT_ID, 77L);
        var second = service.create(request, TENANT_ID, 77L);

        assertThat(first.getCode()).isEqualTo("PC-CC-WH-1-2026-08-01-0001");
        assertThat(second.getCode()).isEqualTo("PC-CC-WH-1-2026-08-01-0002");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM physical_count WHERE tenant_id = ?",
            Integer.class, TENANT_ID)).isEqualTo(2);
    }
}
