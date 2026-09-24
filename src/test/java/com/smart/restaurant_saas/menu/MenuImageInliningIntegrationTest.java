package com.smart.restaurant_saas.menu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.auth.service.SecurityService;
import com.smart.restaurant_saas.media.enums.MediaVariantType;
import com.smart.restaurant_saas.media.storage.StorageService;
import com.smart.restaurant_saas.menu.dto.MenuItemResponse;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bytes, not urls: what {@code findMenu(tenantId, true)} puts on the wire.
 *
 * <p>Deliberately not folded into {@link MenuReadModelIntegrationTest}. That class asserts an
 * exact prepared-statement count, which is a claim about query shape and is sensitive to any
 * fixture sharing its tenant; this one writes real files to the storage root, which no
 * transaction rolls back. They are kept apart so neither can quietly erode the other.
 */
@SpringBootTest
@Transactional
class MenuImageInliningIntegrationTest {

    private static final Long TENANT_ID = 988_001L;
    private static final Long PRODUCT_ID = 988_201L;
    private static final Long CATEGORY_ID = 988_101L;

    @Autowired
    private MenuService menuService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private StorageService storageService;

    /** The image path checks PRODUCTS_VIEW, and a service-level test has no security context. */
    @MockitoBean
    private SecurityService securityService;

    @BeforeEach
    void seedOneProduct() {
        when(securityService.hasPermission(anyString())).thenReturn(true);

        jdbcTemplate.update("""
            INSERT INTO tenants (id, name, code, status, created_at, timezone)
            VALUES (?, 'Menu Image Inlining Tenant', 'MENU_IMAGE_INLINE_TEST', 'ACTIVE',
                    CURRENT_TIMESTAMP, 'Africa/Cairo')
            ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, status = EXCLUDED.status
            """, TENANT_ID);

        jdbcTemplate.update("""
            INSERT INTO menu_category (id, tenant_id, name, sort_order, is_active, created_at)
            VALUES (?, ?, 'Plates', 1, TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO UPDATE SET tenant_id = EXCLUDED.tenant_id, name = EXCLUDED.name
            """, CATEGORY_ID, TENANT_ID);

        jdbcTemplate.update("""
            INSERT INTO product (id, tenant_id, name, parent_product_id, selling_price,
                                 is_active, menu_category_id, is_menu, created_at)
            VALUES (?, ?, 'Chicken Rice', NULL, 85.00, TRUE, ?, TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO UPDATE SET tenant_id = EXCLUDED.tenant_id, name = EXCLUDED.name
            """, PRODUCT_ID, TENANT_ID, CATEGORY_ID);

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void embedsTheThumbBytesAndOnlyTheThumbBytes() {
        attachImage(960_001L);
        byte[] thumbBytes = "thumb-jpeg-bytes".getBytes(StandardCharsets.UTF_8);
        storageService.put(thumbKey(960_001L), thumbBytes, "image/jpeg");

        MenuItemResponse item = onlyItem(menuService.findMenu(TENANT_ID, true));

        assertThat(item.getImage().variants())
            .filteredOn(variant -> variant.variant() == MediaVariantType.THUMB)
            .singleElement()
            .satisfies(thumb -> assertThat(thumb.dataUri()).isEqualTo(
                "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(thumbBytes)));

        // MEDIUM keeps its url and nothing else. Inlining an 800px rendition would put it on
        // every row of a catalog that may run to hundreds, to fill a tile 80px tall.
        assertThat(item.getImage().variants())
            .filteredOn(variant -> variant.variant() != MediaVariantType.THUMB)
            .allSatisfy(other -> assertThat(other.dataUri()).isNull());
    }

    @Test
    void defaultProjectionEmbedsNothing() {
        attachImage(960_002L);
        storageService.put(thumbKey(960_002L), "bytes".getBytes(StandardCharsets.UTF_8), "image/jpeg");

        MenuItemResponse item = onlyItem(menuService.findMenu(TENANT_ID));

        // The admin web app reads this endpoint too, from a session that can load image urls
        // directly. It must not start paying for base64 copies because the POS needed them.
        assertThat(item.getImage().variants())
            .allSatisfy(variant -> assertThat(variant.dataUri()).isNull());
    }

    @Test
    void aMissingRenditionFileCostsTheTileItsPictureAndNothingElse() {
        // Row present, bytes absent — a half-finished upload, or a storage root restored
        // without its files. A terminal cannot work without the menu; one tile falling back
        // to a placeholder is the smaller failure, so this degrades instead of throwing.
        attachImage(960_003L);

        MenuItemResponse item = onlyItem(menuService.findMenu(TENANT_ID, true));

        assertThat(item.getImage()).isNotNull();
        assertThat(item.getImage().variants())
            .allSatisfy(variant -> assertThat(variant.dataUri()).isNull());
    }

    private MenuItemResponse onlyItem(List<MenuItemResponse> menu) {
        return menu.stream().filter(item -> item.getId().equals(PRODUCT_ID)).findFirst().orElseThrow();
    }

    private String thumbKey(Long mediaFileId) {
        return "t%d/product/seed-%d/thumb.jpg".formatted(TENANT_ID, mediaFileId);
    }

    /** Rows written directly: the transcoder is not what is under test here. */
    private void attachImage(Long mediaFileId) {
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
            mediaFileId, thumbKey(mediaFileId));
        jdbcTemplate.update("""
            INSERT INTO media_link (tenant_id, media_file_id, owner_type, owner_id, purpose,
                                    sort_order, created_at)
            VALUES (?, ?, 'PRODUCT', ?, 'PRODUCT_IMAGE', 0, CURRENT_TIMESTAMP)
            """, TENANT_ID, mediaFileId, PRODUCT_ID);
        entityManager.flush();
        entityManager.clear();
    }
}
