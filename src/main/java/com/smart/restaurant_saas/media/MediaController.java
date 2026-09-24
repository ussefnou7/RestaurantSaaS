package com.smart.restaurant_saas.media;

import com.smart.restaurant_saas.common.ErrorParams;
import com.smart.restaurant_saas.common.ResourceNotFoundException;
import com.smart.restaurant_saas.media.dto.MediaResponse;
import com.smart.restaurant_saas.media.enums.MediaOwnerType;
import com.smart.restaurant_saas.media.enums.MediaPurpose;
import com.smart.restaurant_saas.media.enums.MediaVariantType;
import com.smart.restaurant_saas.tenant.CurrentTenantId;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MultipartFile;

/**
 * Media attachments (D128).
 *
 * <p><strong>Why these methods carry {@code isAuthenticated()} rather than a permission string.</strong>
 * The permission a media request needs is a property of the purpose it names, which is request data
 * — a compile-time annotation cannot express it. Every method below delegates to
 * {@link MediaService}, whose {@code requireManage} / {@code requireView} resolve the purpose's own
 * permission and throw
 * {@code ACCESS_DENIED} exactly as {@code @PreAuthorize} would. Authorization is the owner's, never
 * the media module's: there is no {@code MEDIA_UPLOAD} permission and none is to be added.
 */
@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
@Tag(name = "Media", description = "Generic file attachments (D128)")
public class MediaController {

    private final MediaService mediaService;

    @PostMapping(path = "/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    @Operation(
        summary = "Attach a file to a record",
        description = "Requires the purpose's own manage permission. Uploading a second file to a "
            + "single-valued purpose replaces the incumbent and destroys its bytes — it does not error.")
    public ResponseEntity<MediaResponse> upload(
            @RequestParam MediaPurpose purpose,
            @RequestParam Long ownerId,
            @RequestParam("file") MultipartFile file,
            @CurrentTenantId Long tenantId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(mediaService.upload(tenantId, userId, purpose, ownerId, file));
    }

    @DeleteMapping("/links/{linkId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(
        summary = "Remove an attachment",
        description = "Permanent. The bytes are queued for deletion in the same transaction as the "
            + "row; there is no detached state and no version history.")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long linkId, @CurrentTenantId Long tenantId) {
        mediaService.delete(tenantId, linkId);
    }

    @GetMapping("/owners/{ownerType}/{ownerId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List every attachment on one record")
    public List<MediaResponse> listForOwner(
            @PathVariable MediaOwnerType ownerType,
            @PathVariable Long ownerId,
            @CurrentTenantId Long tenantId) {
        return mediaService.listForOwner(tenantId, ownerType, ownerId);
    }

    @GetMapping("/links")
    @PreAuthorize("isAuthenticated()")
    @Operation(
        summary = "List one purpose's attachments across many owners",
        description = "Backs list screens, which need an image per row without one request per row.")
    public List<MediaResponse> listForOwners(
            @RequestParam MediaPurpose purpose,
            @RequestParam List<Long> ownerIds,
            @CurrentTenantId Long tenantId) {
        return mediaService.listForOwners(tenantId, purpose, ownerIds);
    }

    /**
     * Streams one rendition.
     *
     * <p>{@code immutable} is safe because content is never rewritten under an existing key — a
     * replacement is a new uuid and therefore a new key. Cache invalidation is not needed and must
     * not be implemented; that property is what a CDN will later depend on.
     *
     * <p>{@code private} is correct while every response is permission-gated. When product images
     * move to a public bucket behind a CDN, that purpose's responses flip to {@code public} — a
     * per-purpose property, not a global one. Employee photos never become public.
     */
    @GetMapping("/{mediaFileId}/{variant}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Stream a rendition", description = "ETag + 304; cacheable for a year.")
    public ResponseEntity<Resource> stream(
            @PathVariable Long mediaFileId,
            @PathVariable String variant,
            @CurrentTenantId Long tenantId,
            WebRequest webRequest) {
        MediaVariantType variantType = parseVariant(variant);
        MediaService.StreamedVariant streamed =
            mediaService.openVariant(tenantId, mediaFileId, variantType);

        // D128 §8 names the file checksum. The variant is appended because one checksum covers
        // every rendition of a file, and an ETag that does not distinguish them is only correct
        // for as long as nothing serves two renditions under one URL.
        String etag = "\"%s-%s\"".formatted(streamed.checksumSha256(), variantType.name());
        if (webRequest.checkNotModified(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }

        return ResponseEntity.ok()
            .eTag(etag)
            .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(365)).cachePrivate().immutable())
            .contentType(MediaType.parseMediaType(streamed.contentType()))
            .contentLength(streamed.sizeBytes())
            // The stored content type is sniffed from the bytes rather than taken from the upload,
            // but a browser that sniffs for itself would undo that check.
            .header("X-Content-Type-Options", "nosniff")
            .body(streamed.resource());
    }

    /**
     * Variants appear lowercase in a URL and uppercase in the enum. Parsed here rather than by
     * Spring's converter so an unknown name answers 404 with a media error code, not a 400 with a
     * conversion message the frontend cannot translate.
     */
    private MediaVariantType parseVariant(String variant) {
        try {
            return MediaVariantType.valueOf(variant.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResourceNotFoundException(MediaErrorCode.MEDIA_VARIANT_NOT_FOUND,
                "Unknown media variant: " + variant,
                ErrorParams.of("variant", variant));
        }
    }
}
