package com.smart.restaurant_saas.media.dto;

import java.util.List;

/**
 * An attachment reduced to what a read projection needs: the file id and its renditions.
 *
 * <p>Distinct from {@link MediaResponse}, which carries the link, the filename, the checksum and
 * the owner — none of which a menu tile or a list row has any use for, and all of which cost bytes
 * on a payload that repeats per product.
 *
 * <p>Deliberately a list of variants rather than named {@code thumbUrl} / {@code mediumUrl}
 * fields: naming them would freeze one purpose's derivative set into every DTO that embeds this,
 * so adding a rendition later would mean editing all of them.
 */
public record MediaSummaryResponse(Long mediaFileId, List<MediaVariantResponse> variants) {}
