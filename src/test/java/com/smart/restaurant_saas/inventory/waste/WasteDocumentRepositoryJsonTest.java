package com.smart.restaurant_saas.inventory.waste;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.restaurant_saas.inventory.core.enums.DocumentStatus;
import com.smart.restaurant_saas.inventory.core.enums.WarehouseType;
import com.smart.restaurant_saas.inventory.core.enums.WasteReasonCode;
import com.smart.restaurant_saas.inventory.repository.WasteDocumentRepository;
import com.smart.restaurant_saas.inventory.warehouse.Warehouse;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class WasteDocumentRepositoryJsonTest {

    private static final Long TENANT_ID = 990_001L;
    private static final Long WAREHOUSE_ID = 990_101L;

    @Autowired
    private WasteDocumentRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void persistsAndReadsStockWarningsAsJsonb() {
        seedTenantAndWarehouse();

        WasteDocument doc = new WasteDocument();
        doc.setTenantId(TENANT_ID);
        doc.setWarehouse(entityManager.getReference(Warehouse.class, WAREHOUSE_ID));
        doc.setCode("WASTE-JSONB-TEST");
        doc.setWasteDate(LocalDate.of(2026, 7, 3));
        doc.setReasonCode(WasteReasonCode.SPOILED);
        doc.setStatus(DocumentStatus.COMPLETE);
        doc.setPostedToInventory(false);
        doc.setStockWarnings(List.of(new MaterialShortfall(
            10L,
            "Flour",
            new BigDecimal("12.500000"),
            new BigDecimal("7.000000"),
            new BigDecimal("5.500000"),
            "kg",
            false)));

        WasteDocument saved = repository.saveAndFlush(doc);
        entityManager.clear();

        WasteDocument reloaded = repository.findByIdAndTenantId(saved.getId(), TENANT_ID)
            .orElseThrow();

        assertThat(reloaded.getStockWarnings()).singleElement().satisfies(warning -> {
            assertThat(warning.materialId()).isEqualTo(10L);
            assertThat(warning.materialName()).isEqualTo("Flour");
            assertThat(warning.requiredQty()).isEqualByComparingTo("12.500000");
            assertThat(warning.availableQty()).isEqualByComparingTo("7.000000");
            assertThat(warning.shortfallQty()).isEqualByComparingTo("5.500000");
            assertThat(warning.uomSymbol()).isEqualTo("kg");
            assertThat(warning.notStockedInWarehouse()).isFalse();
        });

        String databaseType = jdbcTemplate.queryForObject(
            "SELECT pg_typeof(stock_warnings)::text FROM waste_document WHERE id = ?",
            String.class,
            saved.getId());
        assertThat(databaseType).isEqualTo("jsonb");
    }

    private void seedTenantAndWarehouse() {
        jdbcTemplate.update("""
            INSERT INTO tenants (id, name, code, status, created_at, timezone)
            VALUES (?, 'JSONB Test Tenant', 'JSONB_TEST_TENANT', 'ACTIVE', CURRENT_TIMESTAMP, 'Africa/Cairo')
            ON CONFLICT (id) DO UPDATE
            SET name = EXCLUDED.name,
                code = EXCLUDED.code,
                status = EXCLUDED.status
            """, TENANT_ID);

        jdbcTemplate.update("""
            INSERT INTO warehouse (id, tenant_id, code, name, type, active, created_at)
            VALUES (?, ?, 'JSONB_WH', 'JSONB Test Warehouse', ?, TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO UPDATE
            SET tenant_id = EXCLUDED.tenant_id,
                code = EXCLUDED.code,
                name = EXCLUDED.name,
                type = EXCLUDED.type,
                active = EXCLUDED.active
            """, WAREHOUSE_ID, TENANT_ID, WarehouseType.CENTRAL.name());
    }
}
