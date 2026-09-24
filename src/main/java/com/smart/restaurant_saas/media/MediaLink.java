package com.smart.restaurant_saas.media;

import com.smart.restaurant_saas.common.TenantAwareEntity;
import com.smart.restaurant_saas.media.enums.MediaOwnerType;
import com.smart.restaurant_saas.media.enums.MediaPurpose;
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
 * The generic ownership edge — polymorphic, and deliberately so.
 *
 * <p>{@code ownerId} carries no foreign key, so the database cannot enforce that it names a live
 * row. That is the price of letting any record become attachable without a schema change. It is
 * bought back by {@code uk_media_link_single} and by {@code MediaOwnerResolver.exists}, which is
 * always called with the request's tenant.
 */
@Getter
@Setter
@Entity
@Table(name = "media_link")
public class MediaLink extends TenantAwareEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "media_file_id", nullable = false)
    private Long mediaFileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 40)
    private MediaOwnerType ownerType;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 60)
    private MediaPurpose purpose;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
}
