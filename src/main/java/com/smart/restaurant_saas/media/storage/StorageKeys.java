package com.smart.restaurant_saas.media.storage;

import com.smart.restaurant_saas.media.enums.MediaOwnerType;
import com.smart.restaurant_saas.media.enums.MediaVariantType;
import java.util.Locale;
import java.util.Map;

/**
 * The one place a storage key is built. Layout is fixed by D128 §7:
 * {@code t{tenantId}/{ownerType}/{uuid}/{variant}.{ext}}.
 *
 * <p>The {@code uuid} is generated per upload and <strong>the uploaded filename never appears in a
 * key</strong> — it is display text, and display text in a key is how a path separator or a
 * non-ASCII byte becomes a storage bug.
 *
 * <p>A fresh uuid per upload is also what makes {@code Cache-Control: immutable} safe: content is
 * never rewritten under an existing key, because a replacement is a new uuid and therefore a new
 * key. Cache invalidation is not needed and must not be implemented — that property is what a CDN
 * will later depend on.
 */
public final class StorageKeys {

    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "image/webp", "webp",
            "application/pdf", "pdf");

    private StorageKeys() {}

    public static String build(Long tenantId, MediaOwnerType ownerType, String uuid,
                               MediaVariantType variant, String contentType) {
        return "t%d/%s/%s/%s.%s".formatted(
                tenantId,
                ownerType.name().toLowerCase(Locale.ROOT),
                uuid,
                variant.name().toLowerCase(Locale.ROOT),
                extensionFor(contentType));
    }

    public static String extensionFor(String contentType) {
        return EXTENSIONS.getOrDefault(
                contentType == null ? "" : contentType.toLowerCase(Locale.ROOT), "bin");
    }
}
