package com.smart.restaurant_saas.media.dto;

import com.smart.restaurant_saas.media.enums.MediaVariantType;

/**
 * One rendition as the client sees it.
 *
 * <p>{@code url} is built here, at read time, from the file id and the variant — it is never stored
 * and never comes from the database. That is what keeps a storage-provider change from becoming a
 * data migration across every row.
 *
 * <p>{@code dataUri} is the rendition's bytes inline, as {@code data:<type>;base64,...}, and is
 * null unless the caller asked a projection to embed them. It exists for clients that cannot
 * authenticate an {@code <img>} tag: {@link #url} points at a permission-gated endpoint, and a
 * browser attaches no bearer token to an image request, so a token-authenticated client can only
 * render a rendition it received in the body of a call it made itself. Embedding also collapses a
 * tile-per-request fan-out into the one request that already returns the tiles.
 *
 * <p>Base64 costs about a third more bytes than the rendition, so only a projection that has
 * chosen a small variant should ask for it — inlining {@code ORIGINAL} would put up to the
 * purpose's whole upload limit on a list response, once per row.
 */
public record MediaVariantResponse(
        MediaVariantType variant,
        Integer width,
        Integer height,
        Long sizeBytes,
        String url,
        String dataUri) {}
