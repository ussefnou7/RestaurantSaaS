package com.smart.restaurant_saas.media;

import com.smart.restaurant_saas.common.TenantAwareEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** The bytes' identity. Knows nothing about who owns it — that is {@link MediaLink}'s job. */
@Getter
@Setter
@Entity
@Table(name = "media_file")
public class MediaFile extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** As uploaded, for display only. Never used to build a storage key. */
    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    /** Of the original bytes. Served as the ETag; nothing reads it for deduplication. */
    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;
}
