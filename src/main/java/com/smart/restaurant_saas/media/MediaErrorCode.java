package com.smart.restaurant_saas.media;

import com.smart.restaurant_saas.common.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum MediaErrorCode implements ErrorCode {

    MEDIA_NOT_FOUND(HttpStatus.NOT_FOUND),
    MEDIA_VARIANT_NOT_FOUND(HttpStatus.NOT_FOUND),
    /** The owner row does not exist, or belongs to another tenant — the two are indistinguishable. */
    MEDIA_OWNER_NOT_FOUND(HttpStatus.NOT_FOUND),
    MEDIA_OWNER_IMMUTABLE(HttpStatus.CONFLICT),
    MEDIA_FILE_EMPTY(HttpStatus.BAD_REQUEST),
    MEDIA_UNSUPPORTED_CONTENT_TYPE(HttpStatus.BAD_REQUEST),
    /** Sent for HEIC specifically, so the UI can say what to do rather than just refusing. */
    MEDIA_HEIC_NOT_SUPPORTED(HttpStatus.BAD_REQUEST),
    MEDIA_FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    MEDIA_IMAGE_UNREADABLE(HttpStatus.BAD_REQUEST),
    MEDIA_STORAGE_FAILURE(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus defaultStatus;

    @Override
    public String getCode() {
        return name();
    }
}
