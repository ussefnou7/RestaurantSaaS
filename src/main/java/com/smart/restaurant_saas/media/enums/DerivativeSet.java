package com.smart.restaurant_saas.media.enums;

import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The renditions a purpose stores beyond the original.
 *
 * <p>{@code ORIGINAL} is not listed in any set because it is stored unconditionally, for every
 * purpose and every content type — listing it would invite a set that omits it. The sizes below are
 * a <em>longest edge</em>: aspect ratio is preserved and an image is never upscaled, so a 96px
 * avatar uploaded against {@link #AVATAR} stores three renditions of identical dimensions and only
 * {@code ORIGINAL}'s bytes are meaningful.
 *
 * <p>There is no server-side cropping. {@code AVATAR} fits, it does not crop to square — the UI
 * squares the frame with {@code object-fit}. Cropping is destructive, and the crop a server guesses
 * is wrong on exactly the faces it matters for.
 */
@Getter
@RequiredArgsConstructor
public enum DerivativeSet {

    FULL(List.of(
            new Rendition(MediaVariantType.LARGE, 1600),
            new Rendition(MediaVariantType.MEDIUM, 800),
            new Rendition(MediaVariantType.THUMB, 200))),

    AVATAR(List.of(
            new Rendition(MediaVariantType.MEDIUM, 400),
            new Rendition(MediaVariantType.THUMB, 96))),

    DOCUMENT(List.of(
            new Rendition(MediaVariantType.THUMB, 200)));

    private final List<Rendition> renditions;

    /** A derivative to produce: which variant, and the longest edge it fits inside. */
    public record Rendition(MediaVariantType variant, int longestEdge) {}
}
