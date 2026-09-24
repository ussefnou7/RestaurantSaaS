package com.smart.restaurant_saas.media;

import com.smart.restaurant_saas.media.enums.MediaVariantType;
import com.smart.restaurant_saas.tenant.TenantUnscoped;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaVariantRepository extends JpaRepository<MediaVariantRow, Long> {

    /**
     * @param mediaFileId must come from a {@link MediaFile} already loaded by
     *     {@code findByIdAndTenantId}; variants carry no tenant column of their own.
     */
    @TenantUnscoped("mediaFileId must already have been resolved against the request's tenant")
    List<MediaVariantRow> findByMediaFileIdOrderByIdAsc(Long mediaFileId);

    /** @see #findByMediaFileIdOrderByIdAsc */
    @TenantUnscoped("mediaFileId must already have been resolved against the request's tenant")
    Optional<MediaVariantRow> findByMediaFileIdAndVariant(Long mediaFileId, MediaVariantType variant);

    @TenantUnscoped("mediaFileIds must already have been resolved against the request's tenant")
    List<MediaVariantRow> findByMediaFileIdIn(List<Long> mediaFileIds);

    void deleteByMediaFileId(Long mediaFileId);
}
