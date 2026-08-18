package com.smart.restaurant_saas.menu;

import static org.assertj.core.api.Assertions.assertThat;

import com.smart.restaurant_saas.menu.dto.MenuItemResponse;
import com.smart.restaurant_saas.menu.dto.MenuItemType;
import com.smart.restaurant_saas.menu.product.Product;
import com.smart.restaurant_saas.menu.product.ProductRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class MenuReadModelIntegrationTest {

    private static final Long TENANT_ID = 989_001L;
    private static final Long PARENT_ID = 989_201L;
    private static final Long SMALL_ID = 989_202L;
    private static final Long MEDIUM_ID = 989_203L;
    private static final Long LARGE_ID = 989_204L;
    private static final Long STANDALONE_ID = 989_205L;
    private static final Long ADD_ON_ID = 989_206L;
    private static final Long RECIPE_PRODUCT_ID = 989_207L;

    @Autowired
    private MenuService menuService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void seedCatalog() {
        jdbcTemplate.update("""
            INSERT INTO tenants (id, name, code, status, created_at, timezone)
            VALUES (?, 'Menu Read Model Tenant', 'MENU_READ_MODEL_TEST', 'ACTIVE', CURRENT_TIMESTAMP, 'Africa/Cairo')
            ON CONFLICT (id) DO UPDATE
            SET name = EXCLUDED.name,
                code = EXCLUDED.code,
                status = EXCLUDED.status
            """, TENANT_ID);

        jdbcTemplate.update("""
            INSERT INTO menu_category (id, tenant_id, name, sort_order, is_active, created_at)
            VALUES
                (989101, ?, 'Pizza', 1, TRUE, CURRENT_TIMESTAMP),
                (989102, ?, 'Plates', 2, TRUE, CURRENT_TIMESTAMP),
                (989103, ?, 'Add-ons', 3, TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO UPDATE
            SET tenant_id = EXCLUDED.tenant_id,
                name = EXCLUDED.name,
                sort_order = EXCLUDED.sort_order,
                is_active = EXCLUDED.is_active
            """, TENANT_ID, TENANT_ID, TENANT_ID);

        jdbcTemplate.update("""
            INSERT INTO product (
                id, tenant_id, name, parent_product_id, variant_label, variant_label_ar,
                selling_price, is_active, menu_category_id, is_menu, created_at
            )
            VALUES
                (?, ?, 'Cheese Pizza', NULL, NULL, NULL, 0.00, TRUE, 989101, TRUE, CURRENT_TIMESTAMP),
                (?, ?, 'Cheese Pizza Small', ?, 'Small', 'صغير', 70.00, TRUE, 989101, FALSE, CURRENT_TIMESTAMP),
                (?, ?, 'Cheese Pizza Medium', ?, 'Medium', 'وسط', 100.00, TRUE, 989101, FALSE, CURRENT_TIMESTAMP),
                (?, ?, 'Cheese Pizza Large', ?, 'Large', 'كبير', 140.00, TRUE, 989101, FALSE, CURRENT_TIMESTAMP),
                (?, ?, 'Chicken Rice', NULL, NULL, NULL, 85.00, TRUE, 989102, TRUE, CURRENT_TIMESTAMP),
                (?, ?, 'Extra Cheese', NULL, NULL, NULL, 20.00, TRUE, 989103, FALSE, CURRENT_TIMESTAMP),
                (?, ?, 'Omelette', NULL, NULL, NULL, 60.00, TRUE, 989102, TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO UPDATE
            SET tenant_id = EXCLUDED.tenant_id,
                name = EXCLUDED.name,
                parent_product_id = EXCLUDED.parent_product_id,
                variant_label = EXCLUDED.variant_label,
                variant_label_ar = EXCLUDED.variant_label_ar,
                selling_price = EXCLUDED.selling_price,
                is_active = EXCLUDED.is_active,
                menu_category_id = EXCLUDED.menu_category_id,
                is_menu = EXCLUDED.is_menu
            """,
            PARENT_ID, TENANT_ID,
            SMALL_ID, TENANT_ID, PARENT_ID,
            MEDIUM_ID, TENANT_ID, PARENT_ID,
            LARGE_ID, TENANT_ID, PARENT_ID,
            STANDALONE_ID, TENANT_ID,
            ADD_ON_ID, TENANT_ID,
            RECIPE_PRODUCT_ID, TENANT_ID);

        jdbcTemplate.update("""
            INSERT INTO recipe (id, tenant_id, product_id, is_active, created_at)
            VALUES (989301, ?, ?, TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO UPDATE
            SET tenant_id = EXCLUDED.tenant_id,
                product_id = EXCLUDED.product_id,
                is_active = EXCLUDED.is_active
            """, TENANT_ID, RECIPE_PRODUCT_ID);

        jdbcTemplate.update("""
            INSERT INTO product_add_on (tenant_id, product_id, add_on_product_id, created_at)
            VALUES (?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (tenant_id, product_id, add_on_product_id) DO NOTHING
            """, TENANT_ID, PARENT_ID, ADD_ON_ID);

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void menuNestsVariantsAndAddOnsWithTwoStatementsRegardlessOfCatalogSize() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean statisticsWereEnabled = statistics.isStatisticsEnabled();
        List<MenuItemResponse> menu;
        long statementCount;
        try {
            statistics.setStatisticsEnabled(true);
            statistics.clear();
            menu = menuService.findMenu(TENANT_ID);
            statementCount = statistics.getPrepareStatementCount();
        } finally {
            statistics.setStatisticsEnabled(statisticsWereEnabled);
        }

        assertThat(statementCount).isEqualTo(2L);
        assertThat(menu).extracting(MenuItemResponse::getId)
            .containsExactly(PARENT_ID, STANDALONE_ID, RECIPE_PRODUCT_ID)
            .doesNotContain(SMALL_ID, MEDIUM_ID, LARGE_ID, ADD_ON_ID);

        MenuItemResponse parent = menu.stream()
            .filter(item -> item.getId().equals(PARENT_ID))
            .findFirst()
            .orElseThrow();
        assertThat(parent.getType()).isEqualTo(MenuItemType.PARENT);
        assertThat(parent.getSellingPrice()).isNull();
        assertThat(parent.getMinPrice()).isEqualByComparingTo("70.00");
        assertThat(parent.getMaxPrice()).isEqualByComparingTo("140.00");
        assertThat(parent.getVariants()).extracting("id")
            .containsExactlyInAnyOrder(SMALL_ID, MEDIUM_ID, LARGE_ID);
        assertThat(parent.getAddOns()).extracting("id").containsExactly(ADD_ON_ID);
    }

    @Test
    void parentEligibleExcludesChildrenActiveRecipesAndEditedProduct() {
        List<Product> candidates = productRepository.findParentEligible(TENANT_ID, null);

        assertThat(candidates).extracting(Product::getId)
            .contains(PARENT_ID, STANDALONE_ID, ADD_ON_ID)
            .doesNotContain(SMALL_ID, MEDIUM_ID, LARGE_ID, RECIPE_PRODUCT_ID);

        assertThat(productRepository.findParentEligible(TENANT_ID, STANDALONE_ID))
            .extracting(Product::getId)
            .doesNotContain(STANDALONE_ID);
    }
}
