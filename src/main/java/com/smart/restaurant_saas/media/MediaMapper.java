package com.smart.restaurant_saas.media;

import com.smart.restaurant_saas.media.dto.MediaResponse;
import com.smart.restaurant_saas.media.dto.MediaVariantResponse;
import com.smart.restaurant_saas.media.enums.MediaVariantType;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MediaMapper {

    public MediaResponse toResponse(MediaLink link, MediaFile file, List<MediaVariantRow> variants) {
        return new MediaResponse(
                link.getId(),
                file.getId(),
                link.getOwnerType(),
                link.getOwnerId(),
                link.getPurpose(),
                link.getSortOrder(),
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSizeBytes(),
                file.getWidth(),
                file.getHeight(),
                toVariantResponses(file.getId(), variants));
    }

    public List<MediaVariantResponse> toVariantResponses(Long mediaFileId,
                                                         List<MediaVariantRow> variants) {
        return toVariantResponses(mediaFileId, variants, Map.of());
    }

    /**
     * @param inlineBytes rendition bytes to embed, keyed by variant; absent variants carry a url
     *                    only. The caller decides which variants are small enough to inline —
     *                    see {@link MediaVariantResponse#dataUri()}.
     */
    public List<MediaVariantResponse> toVariantResponses(Long mediaFileId,
                                                         List<MediaVariantRow> variants,
                                                         Map<MediaVariantType, byte[]> inlineBytes) {
        return variants.stream()
                .map(variant -> toVariantResponse(mediaFileId, variant,
                        inlineBytes.get(variant.getVariant())))
                .toList();
    }

    private MediaVariantResponse toVariantResponse(Long mediaFileId, MediaVariantRow variant,
                                                   byte[] inlineBytes) {
        return new MediaVariantResponse(
                variant.getVariant(),
                variant.getWidth(),
                variant.getHeight(),
                variant.getSizeBytes(),
                urlFor(mediaFileId, variant.getVariant()),
                inlineBytes == null ? null : dataUri(variant.getContentType(), inlineBytes));
    }

    /** The rendition's own content type, not the file's: derivatives are re-encoded on upload. */
    private String dataUri(String contentType, byte[] bytes) {
        return "data:%s;base64,%s".formatted(contentType, Base64.getEncoder().encodeToString(bytes));
    }

    /** Built at read time, never stored. Relative, so the client's own base URL applies. */
    public String urlFor(Long mediaFileId, MediaVariantType variant) {
        return "/api/media/%d/%s".formatted(mediaFileId, variant.name().toLowerCase(Locale.ROOT));
    }
}
