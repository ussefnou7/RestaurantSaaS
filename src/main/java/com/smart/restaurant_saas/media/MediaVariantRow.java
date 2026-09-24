package com.smart.restaurant_saas.media;

import com.smart.restaurant_saas.media.enums.MediaVariantType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * One stored rendition of a {@link MediaFile}, including the original.
 *
 * <p>A pure child row: no tenant column and no audit columns, because it is only ever reached
 * through its parent, whose {@code tenantId} already confines it.
 *
 * <p>Named {@code MediaVariantRow} rather than {@code MediaVariant} so it does not collide with
 * {@link MediaVariantType} at a glance in the files that use both.
 */
@Getter
@Setter
@Entity
@Table(name = "media_variant")
public class MediaVariantRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "media_file_id", nullable = false)
    private Long mediaFileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "variant", nullable = false, length = 20)
    private MediaVariantType variant;

    /** Provider-independent. No URL is ever stored — every URL is built at read time. */
    @Column(name = "storage_key", nullable = false, length = 512)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;
}
