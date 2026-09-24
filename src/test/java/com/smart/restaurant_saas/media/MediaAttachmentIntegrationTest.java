package com.smart.restaurant_saas.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.smart.restaurant_saas.auth.service.SecurityService;
import com.smart.restaurant_saas.common.AppException;
import com.smart.restaurant_saas.media.dto.MediaResponse;
import com.smart.restaurant_saas.media.enums.MediaOwnerType;
import com.smart.restaurant_saas.media.enums.MediaPurpose;
import com.smart.restaurant_saas.media.enums.MediaVariantType;
import com.smart.restaurant_saas.media.storage.StorageService;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * D128 end to end against a real database and a real filesystem root.
 *
 * <p>{@link SecurityService} is the one thing mocked. It reads the security context, which a
 * service-level test has no business populating, and the permission <em>mapping</em> — which code
 * each purpose demands — is what these tests are asserting, not how a permission is looked up.
 */
@SpringBootTest
@Transactional
class MediaAttachmentIntegrationTest {

    private static final Long TENANT_ID = 977_001L;
    private static final Long OTHER_TENANT_ID = 977_002L;
    private static final Long PRODUCT_ID = 977_101L;
    private static final Long OTHER_TENANT_PRODUCT_ID = 977_102L;
    private static final Long EMPLOYEE_ID = 977_301L;

    @Autowired
    private MediaService mediaService;

    @Autowired
    private MediaLinkRepository mediaLinkRepository;

    @Autowired
    private MediaVariantRepository mediaVariantRepository;

    @Autowired
    private MediaFileRepository mediaFileRepository;

    @Autowired
    private MediaDeletionQueueRepository deletionQueueRepository;

    @Autowired
    private StorageService storageService;

    @Autowired
    private MediaProperties mediaProperties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private SecurityService securityService;

    private final Set<String> granted = new HashSet<>();

    @BeforeEach
    void seed() {
        granted.clear();
        when(securityService.isSysAdmin()).thenReturn(false);
        when(securityService.hasPermission(anyString()))
                .thenAnswer(invocation -> granted.contains(invocation.getArgument(0)));

        seedTenant(TENANT_ID, "MEDIA_TEST");
        seedTenant(OTHER_TENANT_ID, "MEDIA_TEST_OTHER");

        jdbcTemplate.update("""
            INSERT INTO menu_category (id, tenant_id, name, sort_order, is_active, created_at)
            VALUES (977201, ?, 'Media Test', 1, TRUE, CURRENT_TIMESTAMP),
                   (977202, ?, 'Media Test Other', 1, TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO UPDATE SET tenant_id = EXCLUDED.tenant_id
            """, TENANT_ID, OTHER_TENANT_ID);

        seedProduct(PRODUCT_ID, TENANT_ID, 977201L, "Media Test Pizza");
        seedProduct(OTHER_TENANT_PRODUCT_ID, OTHER_TENANT_ID, 977202L, "Other Tenant Pizza");

        jdbcTemplate.update("""
            INSERT INTO branches (id, tenant_id, name, code, is_active, created_at)
            VALUES (977401, ?, 'Media Test Branch', 'MEDIA_TEST_BR', TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO UPDATE SET tenant_id = EXCLUDED.tenant_id
            """, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO jobs (id, tenant_id, name, code, is_active, created_at)
            VALUES (977501, ?, 'Media Test Job', 'MEDIA_TEST_JOB', TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO UPDATE SET tenant_id = EXCLUDED.tenant_id
            """, TENANT_ID);
        jdbcTemplate.update("""
            INSERT INTO hr_employees (id, tenant_id, branch_id, job_id, code, full_name,
                                      hire_date, salary, is_active, created_at)
            VALUES (?, ?, 977401, 977501, 'MEDIA_EMP', 'Media Test Employee',
                    CURRENT_DATE, 1000.00, TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO UPDATE SET tenant_id = EXCLUDED.tenant_id
            """, EMPLOYEE_ID, TENANT_ID);

        jdbcTemplate.update("DELETE FROM media_link WHERE tenant_id IN (?, ?)", TENANT_ID, OTHER_TENANT_ID);
        jdbcTemplate.update("DELETE FROM media_deletion_queue WHERE tenant_id IN (?, ?)",
                TENANT_ID, OTHER_TENANT_ID);
    }

    // ------------------------------------------------------------------ upload

    @Test
    void uploadStoresEveryRenditionInTheProductDerivativeSet() {
        granted.add("PRODUCTS_UPDATE");

        MediaResponse response = mediaService.upload(TENANT_ID, 1L, MediaPurpose.PRODUCT_IMAGE,
                PRODUCT_ID, jpeg("pizza.jpg", 2000, 1000));

        assertThat(response.variants()).extracting(v -> v.variant())
                .containsExactlyInAnyOrder(MediaVariantType.ORIGINAL, MediaVariantType.LARGE,
                        MediaVariantType.MEDIUM, MediaVariantType.THUMB);
        assertThat(response.width()).isEqualTo(2000);
        assertThat(response.height()).isEqualTo(1000);

        // Every rendition's bytes are on disk by the time the row exists -- bytes first, commit
        // second. A row whose object is missing is the failure this ordering exists to prevent.
        mediaVariantRepository.findByMediaFileIdOrderByIdAsc(response.mediaFileId())
                .forEach(variant -> assertThat(storageService.exists(variant.getStorageKey()))
                        .as("stored object for %s", variant.getVariant())
                        .isTrue());
    }

    @Test
    void derivativesFitTheLongestEdgeAndPreserveAspectRatio() {
        granted.add("PRODUCTS_UPDATE");

        MediaResponse response = mediaService.upload(TENANT_ID, 1L, MediaPurpose.PRODUCT_IMAGE,
                PRODUCT_ID, jpeg("wide.jpg", 2000, 1000));

        Map<MediaVariantType, int[]> sizes = response.variants().stream()
                .collect(java.util.stream.Collectors.toMap(v -> v.variant(),
                        v -> new int[]{v.width(), v.height()}));
        assertThat(sizes.get(MediaVariantType.LARGE)).containsExactly(1600, 800);
        assertThat(sizes.get(MediaVariantType.MEDIUM)).containsExactly(800, 400);
        assertThat(sizes.get(MediaVariantType.THUMB)).containsExactly(200, 100);
    }

    @Test
    void derivativesAreNeverUpscaled() {
        granted.add("HR_EMPLOYEES_UPDATE");

        // 60px against the AVATAR set, whose MEDIUM is 400 and THUMB 96 -- both above the source.
        MediaResponse response = mediaService.upload(TENANT_ID, 1L, MediaPurpose.EMPLOYEE_PHOTO,
                EMPLOYEE_ID, jpeg("tiny.jpg", 60, 60));

        assertThat(response.variants())
                .as("no rendition grows past the source's 60px")
                .allSatisfy(variant -> {
                    assertThat(variant.width()).isEqualTo(60);
                    assertThat(variant.height()).isEqualTo(60);
                });
    }

    @Test
    void employeePhotoUsesTheAvatarSetNotTheFullOne() {
        granted.add("HR_EMPLOYEES_UPDATE");

        MediaResponse response = mediaService.upload(TENANT_ID, 1L, MediaPurpose.EMPLOYEE_PHOTO,
                EMPLOYEE_ID, jpeg("face.jpg", 1200, 1200));

        assertThat(response.variants()).extracting(v -> v.variant())
                .containsExactlyInAnyOrder(MediaVariantType.ORIGINAL, MediaVariantType.MEDIUM,
                        MediaVariantType.THUMB)
                .doesNotContain(MediaVariantType.LARGE);
    }

    // ------------------------------------------------------------------ cardinality

    @Test
    void secondUploadToASingleValuedPurposeReplacesRatherThanErrors() {
        granted.add("PRODUCTS_UPDATE");

        MediaResponse first = mediaService.upload(TENANT_ID, 1L, MediaPurpose.PRODUCT_IMAGE,
                PRODUCT_ID, jpeg("first.jpg", 400, 400));
        List<String> firstKeys = storageKeysOf(first.mediaFileId());

        MediaResponse second = mediaService.upload(TENANT_ID, 1L, MediaPurpose.PRODUCT_IMAGE,
                PRODUCT_ID, jpeg("second.jpg", 400, 400));

        assertThat(second.mediaFileId()).isNotEqualTo(first.mediaFileId());
        assertThat(mediaLinkRepository
                .findByTenantIdAndOwnerTypeAndOwnerIdAndPurposeOrderBySortOrderAscIdAsc(
                        TENANT_ID, MediaOwnerType.PRODUCT, PRODUCT_ID, MediaPurpose.PRODUCT_IMAGE))
                .hasSize(1);

        // The superseded file is gone from the database and its bytes are queued, not orphaned.
        assertThat(mediaFileRepository.findByIdAndTenantId(first.mediaFileId(), TENANT_ID)).isEmpty();
        assertThat(queuedKeys()).containsAll(firstKeys);
    }

    // ------------------------------------------------------------------ tenant isolation

    @Test
    void anOwnerFromAnotherTenantIsNotFound() {
        granted.add("PRODUCTS_UPDATE");

        assertThatThrownBy(() -> mediaService.upload(TENANT_ID, 1L, MediaPurpose.PRODUCT_IMAGE,
                OTHER_TENANT_PRODUCT_ID, jpeg("cross.jpg", 100, 100)))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode().getCode())
                .isEqualTo(MediaErrorCode.MEDIA_OWNER_NOT_FOUND.getCode());
    }

    @Test
    void anotherTenantCannotReadThisTenantsFile() {
        granted.add("PRODUCTS_UPDATE");
        granted.add("PRODUCTS_VIEW");
        MediaResponse mine = mediaService.upload(TENANT_ID, 1L, MediaPurpose.PRODUCT_IMAGE,
                PRODUCT_ID, jpeg("mine.jpg", 100, 100));

        assertThatThrownBy(() -> mediaService.openVariant(OTHER_TENANT_ID, mine.mediaFileId(),
                MediaVariantType.THUMB))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode().getCode())
                .isEqualTo(MediaErrorCode.MEDIA_NOT_FOUND.getCode());
    }

    // ------------------------------------------------------------------ authorization

    @Test
    void uploadRequiresTheOwnersManagePermissionNotItsViewPermission() {
        granted.add("PRODUCTS_VIEW");

        assertThatThrownBy(() -> mediaService.upload(TENANT_ID, 1L, MediaPurpose.PRODUCT_IMAGE,
                PRODUCT_ID, jpeg("denied.jpg", 100, 100)))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode().getCode())
                .isEqualTo("ACCESS_DENIED");
    }

    @Test
    void anEmployeePhotoIsNotReachableWithProductPermissions() {
        granted.add("HR_EMPLOYEES_UPDATE");
        MediaResponse photo = mediaService.upload(TENANT_ID, 1L, MediaPurpose.EMPLOYEE_PHOTO,
                EMPLOYEE_ID, jpeg("face.jpg", 200, 200));

        granted.clear();
        granted.add("PRODUCTS_VIEW");

        assertThatThrownBy(() -> mediaService.openVariant(TENANT_ID, photo.mediaFileId(),
                MediaVariantType.THUMB))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode().getCode())
                .isEqualTo("ACCESS_DENIED");
    }

    @Test
    void readingAProductImageNeedsOnlyTheViewPermission() {
        granted.add("PRODUCTS_UPDATE");
        MediaResponse image = mediaService.upload(TENANT_ID, 1L, MediaPurpose.PRODUCT_IMAGE,
                PRODUCT_ID, jpeg("menu.jpg", 300, 300));

        // A cashier holds PRODUCTS_VIEW and nothing else. Gating reads on the manage permission
        // would blank the menu for everyone who cannot edit it.
        granted.clear();
        granted.add("PRODUCTS_VIEW");

        MediaService.StreamedVariant streamed =
                mediaService.openVariant(TENANT_ID, image.mediaFileId(), MediaVariantType.THUMB);
        assertThat(streamed.contentType()).isEqualTo("image/jpeg");
        assertThat(streamed.checksumSha256()).hasSize(64);
    }

    // ------------------------------------------------------------------ validation

    @Test
    void bytesThatAreNotAnAllowedImageAreRejectedWhateverTheUploadClaimed() {
        granted.add("PRODUCTS_UPDATE");
        MockMultipartFile liar = new MockMultipartFile("file", "evil.png", "image/png",
                "<html>not an image</html>".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> mediaService.upload(TENANT_ID, 1L, MediaPurpose.PRODUCT_IMAGE,
                PRODUCT_ID, liar))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode().getCode())
                .isEqualTo(MediaErrorCode.MEDIA_UNSUPPORTED_CONTENT_TYPE.getCode());
    }

    @Test
    void heicIsRejectedByNameSoTheUiCanSayWhatToDo() {
        granted.add("HR_EMPLOYEES_UPDATE");
        // ....ftypheic -- the ISO base media header an iPhone writes.
        byte[] heic = new byte[]{0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c', 0, 0, 0, 0};
        MockMultipartFile upload =
                new MockMultipartFile("file", "IMG_0001.HEIC", "image/heic", heic);

        assertThatThrownBy(() -> mediaService.upload(TENANT_ID, 1L, MediaPurpose.EMPLOYEE_PHOTO,
                EMPLOYEE_ID, upload))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode().getCode())
                .isEqualTo(MediaErrorCode.MEDIA_HEIC_NOT_SUPPORTED.getCode());
    }

    @Test
    void aFileOverThePurposeCeilingIsRejectedWithTheCeilingInTheParams() {
        granted.add("HR_EMPLOYEES_UPDATE");
        // EMPLOYEE_PHOTO caps at 5 MB; PRODUCT_IMAGE at 10. The ceiling is a purpose property.
        byte[] oversize = new byte[(int) MediaPurpose.EMPLOYEE_PHOTO.getMaxSizeBytes() + 1];
        System.arraycopy(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, 0, oversize, 0, 3);
        MockMultipartFile upload =
                new MockMultipartFile("file", "huge.jpg", "image/jpeg", oversize);

        assertThatThrownBy(() -> mediaService.upload(TENANT_ID, 1L, MediaPurpose.EMPLOYEE_PHOTO,
                EMPLOYEE_ID, upload))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException app = (AppException) ex;
                    assertThat(app.getErrorCode().getCode())
                            .isEqualTo(MediaErrorCode.MEDIA_FILE_TOO_LARGE.getCode());
                    assertThat(app.getParams()).containsEntry("maxSizeBytes",
                            MediaPurpose.EMPLOYEE_PHOTO.getMaxSizeBytes());
                });
    }

    // ------------------------------------------------------------------ deletion

    @Test
    void deleteRemovesTheRowsAndQueuesTheBytesRatherThanRemovingThemInline() {
        granted.add("PRODUCTS_UPDATE");
        MediaResponse image = mediaService.upload(TENANT_ID, 1L, MediaPurpose.PRODUCT_IMAGE,
                PRODUCT_ID, jpeg("doomed.jpg", 300, 300));
        List<String> keys = storageKeysOf(image.mediaFileId());

        mediaService.delete(TENANT_ID, image.linkId());

        assertThat(mediaLinkRepository.findByIdAndTenantId(image.linkId(), TENANT_ID)).isEmpty();
        assertThat(mediaFileRepository.findByIdAndTenantId(image.mediaFileId(), TENANT_ID)).isEmpty();
        assertThat(queuedKeys()).containsAll(keys);

        // Commit first, bytes second: the objects are still there. Deleting them inside the
        // transaction would destroy a file whose row came back on rollback.
        keys.forEach(key -> assertThat(storageService.exists(key))
                .as("bytes for %s survive until the queue is drained", key)
                .isTrue());
    }

    @Test
    void drainingTheQueueRemovesTheBytesAndTheRows() {
        granted.add("PRODUCTS_UPDATE");
        MediaResponse image = mediaService.upload(TENANT_ID, 1L, MediaPurpose.PRODUCT_IMAGE,
                PRODUCT_ID, jpeg("drained.jpg", 200, 200));
        List<String> keys = storageKeysOf(image.mediaFileId());
        mediaService.delete(TENANT_ID, image.linkId());

        // Constructed directly: the bean is disabled in tests so a background tick cannot race a
        // test's own assertions about what is still queued.
        new MediaDeletionScheduler(deletionQueueRepository, storageService, mediaProperties).drain();

        assertThat(queuedKeys()).doesNotContainAnyElementsOf(keys);
        keys.forEach(key -> assertThat(storageService.exists(key)).isFalse());
    }

    @Test
    void anotherTenantCannotDeleteThisTenantsLink() {
        granted.add("PRODUCTS_UPDATE");
        MediaResponse image = mediaService.upload(TENANT_ID, 1L, MediaPurpose.PRODUCT_IMAGE,
                PRODUCT_ID, jpeg("mine.jpg", 100, 100));

        assertThatThrownBy(() -> mediaService.delete(OTHER_TENANT_ID, image.linkId()))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode().getCode())
                .isEqualTo(MediaErrorCode.MEDIA_NOT_FOUND.getCode());
    }

    // ------------------------------------------------------------------ listing

    @Test
    void listForOwnersReturnsOneEntryPerOwnerThatHasOne() {
        granted.add("PRODUCTS_UPDATE");
        granted.add("PRODUCTS_VIEW");
        mediaService.upload(TENANT_ID, 1L, MediaPurpose.PRODUCT_IMAGE, PRODUCT_ID,
                jpeg("listed.jpg", 100, 100));

        List<MediaResponse> found = mediaService.listForOwners(TENANT_ID,
                MediaPurpose.PRODUCT_IMAGE, List.of(PRODUCT_ID, 999_999L));

        assertThat(found).hasSize(1);
        assertThat(found.getFirst().ownerId()).isEqualTo(PRODUCT_ID);
        assertThat(found.getFirst().variants())
                .allSatisfy(variant -> assertThat(variant.url())
                        .as("URLs are built at read time, never stored")
                        .startsWith("/api/media/" + found.getFirst().mediaFileId() + "/"));
    }

    // ------------------------------------------------------------------ helpers

    private void seedTenant(Long id, String code) {
        jdbcTemplate.update("""
            INSERT INTO tenants (id, name, code, status, created_at, timezone)
            VALUES (?, ?, ?, 'ACTIVE', CURRENT_TIMESTAMP, 'Africa/Cairo')
            ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status
            """, id, code, code);
    }

    private void seedProduct(Long id, Long tenantId, Long categoryId, String name) {
        jdbcTemplate.update("""
            INSERT INTO product (id, tenant_id, name, selling_price, is_active, menu_category_id,
                                 is_menu, created_at)
            VALUES (?, ?, ?, 10.00, TRUE, ?, TRUE, CURRENT_TIMESTAMP)
            ON CONFLICT (id) DO UPDATE SET tenant_id = EXCLUDED.tenant_id, name = EXCLUDED.name
            """, id, tenantId, name, categoryId);
    }

    private List<String> storageKeysOf(Long mediaFileId) {
        return mediaVariantRepository.findByMediaFileIdOrderByIdAsc(mediaFileId).stream()
                .map(MediaVariantRow::getStorageKey)
                .toList();
    }

    private List<String> queuedKeys() {
        return deletionQueueRepository.findAll().stream()
                .map(MediaDeletionQueueEntry::getStorageKey)
                .toList();
    }

    private MockMultipartFile jpeg(String filename, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.ORANGE);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "jpg", out);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return new MockMultipartFile("file", filename, "image/jpeg", out.toByteArray());
    }
}
