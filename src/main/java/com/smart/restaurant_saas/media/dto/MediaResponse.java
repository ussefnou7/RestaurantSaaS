package com.smart.restaurant_saas.media.dto;

import com.smart.restaurant_saas.media.enums.MediaOwnerType;
import com.smart.restaurant_saas.media.enums.MediaPurpose;
import java.util.List;

/** One attachment: the link, the file behind it, and every rendition the client may ask for. */
public record MediaResponse(
        Long linkId,
        Long mediaFileId,
        MediaOwnerType ownerType,
        Long ownerId,
        MediaPurpose purpose,
        Integer sortOrder,
        String originalFilename,
        String contentType,
        Long sizeBytes,
        Integer width,
        Integer height,
        List<MediaVariantResponse> variants) {}
