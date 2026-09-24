package com.smart.restaurant_saas.media.image;

import org.springframework.stereotype.Component;

/**
 * What the bytes actually are, as opposed to what the upload claimed.
 *
 * <p>The multipart {@code Content-Type} is supplied by the client and is therefore an assertion,
 * not a fact. It matters because the stored value is echoed back on every read: believing a claim
 * of {@code image/png} over bytes that are something else means serving those bytes under a type
 * the browser was told to trust.
 *
 * <p>This is a narrow check, not a general detector — it recognises the formats a purpose may
 * allow and says "unknown" for everything else. HEIC is recognised on purpose, so the refusal can
 * name the format instead of shrugging at it.
 */
@Component
public class ContentTypeSniffer {

    public static final String JPEG = "image/jpeg";
    public static final String PNG = "image/png";
    public static final String WEBP = "image/webp";
    public static final String HEIC = "image/heic";
    public static final String PDF = "application/pdf";

    /** @return the detected media type, or {@code null} when the bytes match nothing known. */
    public String sniff(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return null;
        }
        if (startsWith(bytes, 0xFF, 0xD8, 0xFF)) {
            return JPEG;
        }
        if (startsWith(bytes, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return PNG;
        }
        if (startsWith(bytes, 0x25, 0x50, 0x44, 0x46)) {
            return PDF;
        }
        // RIFF....WEBP -- the four size bytes at offset 4 are skipped, not matched.
        if (startsWith(bytes, 0x52, 0x49, 0x46, 0x46)
                && matchesAt(bytes, 8, 0x57, 0x45, 0x42, 0x50)) {
            return WEBP;
        }
        // ISO base media: ....ftyp<brand>. HEIC brands are heic/heix/hevc/hevx/mif1/msf1.
        if (matchesAt(bytes, 4, 0x66, 0x74, 0x79, 0x70) && isHeicBrand(bytes)) {
            return HEIC;
        }
        return null;
    }

    private boolean isHeicBrand(byte[] bytes) {
        String brand = new String(bytes, 8, 4, java.nio.charset.StandardCharsets.US_ASCII);
        return switch (brand) {
            case "heic", "heix", "hevc", "hevx", "mif1", "msf1" -> true;
            default -> false;
        };
    }

    private boolean startsWith(byte[] bytes, int... signature) {
        return matchesAt(bytes, 0, signature);
    }

    private boolean matchesAt(byte[] bytes, int offset, int... signature) {
        if (bytes.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((bytes[offset + i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
