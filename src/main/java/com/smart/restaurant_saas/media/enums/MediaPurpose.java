package com.smart.restaurant_saas.media.enums;

import java.util.Set;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The single source of truth for every per-purpose rule (D128 §2).
 *
 * <p>Owner type, permissions, cardinality, allowed content types, size ceiling and derivative set
 * all live here and nowhere else. There is no {@code if (purpose == ...)} outside this enum, and
 * adding one is the defect this shape exists to prevent.
 *
 * <p><strong>Authorization is the owner's, never the media module's.</strong> There is no
 * {@code MEDIA_UPLOAD} permission and none is to be added. A generic upload permission would let
 * anyone holding it attach a file to any record in the system, which is the whole access-control
 * model defeated by one convenience.
 *
 * <p>D128 §2 named one permission per purpose. It is two here, and the split is not cosmetic:
 * reading a product image is what a cashier does on every POS tile, so gating reads on
 * {@code PRODUCTS_UPDATE} would blank the menu for everyone who cannot edit it.
 */
@Getter
@RequiredArgsConstructor
public enum MediaPurpose {

    PRODUCT_IMAGE(
            MediaOwnerType.PRODUCT,
            "PRODUCTS_VIEW",
            "PRODUCTS_UPDATE",
            true,
            MediaContentTypes.IMAGES,
            10L * 1024 * 1024,
            DerivativeSet.FULL),

    EMPLOYEE_PHOTO(
            MediaOwnerType.EMPLOYEE,
            "HR_EMPLOYEES_VIEW",
            "HR_EMPLOYEES_UPDATE",
            true,
            MediaContentTypes.IMAGES,
            5L * 1024 * 1024,
            DerivativeSet.AVATAR);

    private final MediaOwnerType ownerType;
    private final String viewPermission;
    private final String managePermission;
    /**
     * Cardinality. {@code true} is enforced by the partial unique index {@code uk_media_link_single},
     * one layer below anything a service can forget; a second upload replaces rather than errors.
     * Adding a multi-valued purpose means {@code false} here <em>and</em> dropping it from that
     * index's predicate — the index is the guarantee, this flag only describes it.
     */
    private final boolean singleValued;
    private final Set<String> allowedContentTypes;
    private final long maxSizeBytes;
    private final DerivativeSet derivativeSet;

    public boolean allows(String contentType) {
        return contentType != null && allowedContentTypes.contains(contentType.toLowerCase());
    }

    /**
     * Content-type sets, held in a nested class because an enum constant cannot reference a static
     * field of its own enum from its constructor arguments.
     */
    static final class MediaContentTypes {

        static final Set<String> IMAGES = Set.of("image/jpeg", "image/png", "image/webp");

        private MediaContentTypes() {}
    }
}
