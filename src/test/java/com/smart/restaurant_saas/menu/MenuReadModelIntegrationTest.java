package com.smart.restaurant_saas.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.auth.service.SecurityService;
import com.smart.restaurant_saas.media.dto.MediaVariantResponse;
import com.smart.restaurant_saas.media.enums.MediaVariantType;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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

    /**
     * The menu projection now resolves images, and that path checks {@code PRODUCTS_VIEW} — the
     * same permission {@code MenuController} already gates on. A service-level test populates no
     * security context, so the real bean would throw on a missing principal rather than answer.
     */
    @MockitoBean
    private SecurityService securityService;

    private long lastStatementCount;

    @BeforeEach
    void seedCatalog() {
        when(securityService.hasPermission(anyString())).thenReturn(true);

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
    void menuNestsVariantsAndAddOnsWithAFixedNumberOfStatements() {
        List<MenuItemResponse> menu = measured();

        // Catalog + add-on links + the media link lookup. The media query that resolves renditions
        // is not run at all here, because no product in this fixture has an image and the link
        // lookup returns empty.
        assertThat(lastStatementCount).isEqualTo(3L);
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
        assertThat(parent.getImage()).as("no image attached in this fixture").isNull();
    }

    @Test
    void imagesArriveWithTheMenuAndCostTheSameTwoQueriesHoweverManyProductsHaveOne() {
        attachImage(970_001L, STANDALONE_ID);
        attachImage(970_002L, LARGE_ID);

        List<MenuItemResponse> menu = measured();

        // One more than the imageless case: the rendition lookup now has files to resolve. It is
        // two queries for two images and would be two for two hundred — that is the whole point of
        // embedding this rather than letting each tile ask.
        assertThat(lastStatementCount).isEqualTo(4L);

        MenuItemResponse standalone = itemById(menu, STANDALONE_ID);
        assertThat(standalone.getImage()).isNotNull();
        assertThat(standalone.getImage().mediaFileId()).isEqualTo(970_001L);
        assertThat(standalone.getImage().variants())
            .extracting(MediaVariantResponse::variant)
            .containsExactlyInAnyOrder(MediaVariantType.MEDIUM, MediaVariantType.THUMB);
        assertThat(standalone.getImage().variants())
            .allSatisfy(variant -> assertThat(variant.url())
                .as("URLs are built at read time, never stored")
                .isEqualTo("/api/media/970001/" + variant.variant().name().toLowerCase()));

        // A variant is a product row of its own, so its image rides on the variant, not the parent.
        MenuItemResponse parent = itemById(menu, PARENT_ID);
        assertThat(parent.getImage()).isNull();
        assertThat(parent.getVariants())
            .filteredOn(variant -> variant.getId().equals(LARGE_ID))
            .singleElement()
            .satisfies(large -> assertThat(large.getImage().mediaFileId()).isEqualTo(970_002L));
        assertThat(parent.getVariants())
            .filteredOn(variant -> variant.getId().equals(SMALL_ID))
            .singleElement()
            .satisfies(small -> assertThat(small.getImage()).isNull());
    }

    /**
     * Rows written directly rather than through an upload: this test is about the projection's
     * query shape, and routing it through the transcoder would make it a media test that happens
     * to assert a statement count.
     */
    private void attachImage(Long mediaFileId, Long productId) {
        jdbcTemplate.update("""
            INSERT INTO media_file (id, tenant_id, original_filename, content_type, size_bytes,
                                    width, height, checksum_sha256, created_at)
            VALUES (?, ?, 'seed.jpg', 'image/jpeg', 1024, 800, 600, repeat('a', 64), CURRENT_TIMESTAMP)
            """, mediaFileId, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO media_variant (media_file_id, variant, storage_key, content_type,
                                       width, height, size_bytes)
            VALUES (?, 'MEDIUM', ?, 'image/jpeg', 800, 600, 900),
                   (?, 'THUMB', ?, 'image/jpeg', 200, 150, 120)
            """, mediaFileId, "t%d/product/seed-%d/medium.jpg".formatted(TENANT_ID, mediaFileId),
            mediaFileId, "t%d/product/seed-%d/thumb.jpg".formatted(TENANT_ID, mediaFileId));
        jdbcTemplate.update("""
            INSERT INTO media_link (tenant_id, media_file_id, owner_type, owner_id, purpose,
                                    sort_order, created_at)
            VALUES (?, ?, 'PRODUCT', ?, 'PRODUCT_IMAGE', 0, CURRENT_TIMESTAMP)
            """, TENANT_ID, mediaFileId, productId);
        entityManager.flush();
        entityManager.clear();
    }

    private MenuItemResponse itemById(List<MenuItemResponse> menu, Long id) {
        return menu.stream().filter(item -> item.getId().equals(id)).findFirst().orElseThrow();
    }

    /** Runs the projection with Hibernate statistics on, leaving the count in {@link #lastStatementCount}. */
    private List<MenuItemResponse> measured() {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean statisticsWereEnabled = statistics.isStatisticsEnabled();
        try {
            statistics.setStatisticsEnabled(true);
            statistics.clear();
            List<MenuItemResponse> menu = menuService.findMenu(TENANT_ID);
            lastStatementCount = statistics.getPrepareStatementCount();
            return menu;
        } finally {
            statistics.setStatisticsEnabled(statisticsWereEnabled);
        }
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
