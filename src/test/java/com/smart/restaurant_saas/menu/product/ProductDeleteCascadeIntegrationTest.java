package com.smart.restaurant_saas.menu.product;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class ProductDeleteCascadeIntegrationTest {

    private static final Long TENANT_ID = 991_001L;
    private static final Long CATEGORY_ID = 991_101L;
    private static final Long PRODUCT_ID = 991_201L;
    private static final Long ADD_ON_PRODUCT_ID = 991_202L;
    private static final Long HOST_PRODUCT_ID = 991_203L;
    private static final Long RECIPE_ID = 991_301L;
    private static final Long RECIPE_ITEM_ID = 991_401L;
    private static final Long UOM_ID = 991_501L;
    private static final Long MATERIAL_CATEGORY_ID = 991_601L;
    private static final Long MATERIAL_ID = 991_701L;

    @Autowired
    private ProductService productService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void deleteProductCascadesRecipeItemsAndAddOnLinks() {
        seedProductWithRecipeAndAddOnLinks();

        productService.deleteProduct(TENANT_ID, PRODUCT_ID);
        entityManager.flush();
        entityManager.clear();

        assertThat(count("product", PRODUCT_ID)).isZero();
        assertThat(count("recipe", RECIPE_ID)).isZero();
        assertThat(count("recipe_item", RECIPE_ITEM_ID)).isZero();
        assertThat(countAddOnLinksTouching(PRODUCT_ID)).isZero();
    }

    private void seedProductWithRecipeAndAddOnLinks() {
        jdbcTemplate.update("""
            INSERT INTO tenants (id, name, code, status, created_at, timezone)
            VALUES (?, 'Product Delete Cascade Tenant', 'PRODUCT_DELETE_CASCADE', 'ACTIVE', CURRENT_TIMESTAMP, 'Africa/Cairo')
            ON CONFLICT (id) DO UPDATE
            SET name = EXCLUDED.name,
                code = EXCLUDED.code,
                status = EXCLUDED.status
            """, TENANT_ID);

        jdbcTemplate.update("""
            INSERT INTO menu_category (id, tenant_id, name, sort_order, is_active, created_at)
            VALUES (?, ?, 'Cascade Category', 0, TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO UPDATE
            SET tenant_id = EXCLUDED.tenant_id,
                name = EXCLUDED.name,
                is_active = EXCLUDED.is_active
            """, CATEGORY_ID, TENANT_ID);

        jdbcTemplate.update("""
            INSERT INTO product (id, tenant_id, name, selling_price, is_active, menu_category_id, is_menu, created_at)
            VALUES
                (?, ?, 'Delete Target', 10.00, TRUE, ?, TRUE, CURRENT_TIMESTAMP),
                (?, ?, 'Add-on Product', 3.00, TRUE, ?, TRUE, CURRENT_TIMESTAMP),
                (?, ?, 'Host Product', 12.00, TRUE, ?, TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO UPDATE
            SET tenant_id = EXCLUDED.tenant_id,
                name = EXCLUDED.name,
                selling_price = EXCLUDED.selling_price,
                is_active = EXCLUDED.is_active,
                menu_category_id = EXCLUDED.menu_category_id,
                is_menu = EXCLUDED.is_menu
            """, PRODUCT_ID, TENANT_ID, CATEGORY_ID,
            ADD_ON_PRODUCT_ID, TENANT_ID, CATEGORY_ID,
            HOST_PRODUCT_ID, TENANT_ID, CATEGORY_ID);

        jdbcTemplate.update("""
            INSERT INTO uom (id, tenant_id, code, name, symbol, type, factor_to_base, active, created_at)
            VALUES (?, ?, 'CASCADE_UNIT', 'Cascade Unit', 'cu', 'COUNT', 1, TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO UPDATE
            SET tenant_id = EXCLUDED.tenant_id,
                code = EXCLUDED.code,
                name = EXCLUDED.name,
                symbol = EXCLUDED.symbol,
                type = EXCLUDED.type,
                active = EXCLUDED.active
            """, UOM_ID, TENANT_ID);

        jdbcTemplate.update("""
            INSERT INTO material_category (id, tenant_id, code, name, active, sort_order, created_at)
            VALUES (?, ?, 'CASCADE_MAT_CAT', 'Cascade Material Category', TRUE, 0, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO UPDATE
            SET tenant_id = EXCLUDED.tenant_id,
                code = EXCLUDED.code,
                name = EXCLUDED.name,
                active = EXCLUDED.active
            """, MATERIAL_CATEGORY_ID, TENANT_ID);

        jdbcTemplate.update("""
            INSERT INTO material (
                id, tenant_id, category_id, stock_uom_id, code, name, active, display_uom_id, created_at
            )
            VALUES (?, ?, ?, ?, 'CASCADE_MAT', 'Cascade Material', TRUE, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO UPDATE
            SET tenant_id = EXCLUDED.tenant_id,
                category_id = EXCLUDED.category_id,
                stock_uom_id = EXCLUDED.stock_uom_id,
                code = EXCLUDED.code,
                name = EXCLUDED.name,
                active = EXCLUDED.active,
                display_uom_id = EXCLUDED.display_uom_id
            """, MATERIAL_ID, TENANT_ID, MATERIAL_CATEGORY_ID, UOM_ID, UOM_ID);

        jdbcTemplate.update("""
            INSERT INTO recipe (id, tenant_id, product_id, is_active, created_at)
            VALUES (?, ?, ?, TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO UPDATE
            SET tenant_id = EXCLUDED.tenant_id,
                product_id = EXCLUDED.product_id,
                is_active = EXCLUDED.is_active
            """, RECIPE_ID, TENANT_ID, PRODUCT_ID);

        jdbcTemplate.update("""
            INSERT INTO recipe_item (id, tenant_id, recipe_id, material_id, quantity, uom_id, created_at)
            VALUES (?, ?, ?, ?, 1.000000, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO UPDATE
            SET tenant_id = EXCLUDED.tenant_id,
                recipe_id = EXCLUDED.recipe_id,
                material_id = EXCLUDED.material_id,
                quantity = EXCLUDED.quantity,
                uom_id = EXCLUDED.uom_id
            """, RECIPE_ITEM_ID, TENANT_ID, RECIPE_ID, MATERIAL_ID, UOM_ID);

        jdbcTemplate.update("""
            INSERT INTO product_add_on (tenant_id, product_id, add_on_product_id, created_at)
            VALUES
                (?, ?, ?, CURRENT_TIMESTAMP),
                (?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (tenant_id, product_id, add_on_product_id) DO NOTHING
            """, TENANT_ID, PRODUCT_ID, ADD_ON_PRODUCT_ID,
            TENANT_ID, HOST_PRODUCT_ID, PRODUCT_ID);
    }

    private Long count(String tableName, Long id) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName + " WHERE id = ?",
            Long.class, id);
    }

    private Long countAddOnLinksTouching(Long productId) {
        return jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM product_add_on
            WHERE product_id = ? OR add_on_product_id = ?
            """, Long.class, productId, productId);
    }
}
