package com.smart.restaurant_saas.media;

import com.smart.restaurant_saas.media.enums.MediaOwnerType;
import com.smart.restaurant_saas.media.enums.MediaPurpose;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaLinkRepository extends JpaRepository<MediaLink, Long> {

    Optional<MediaLink> findByIdAndTenantId(Long id, Long tenantId);

    List<MediaLink> findByTenantIdAndOwnerTypeAndOwnerIdAndPurposeOrderBySortOrderAscIdAsc(
            Long tenantId, MediaOwnerType ownerType, Long ownerId, MediaPurpose purpose);

    List<MediaLink> findByTenantIdAndOwnerTypeAndOwnerIdOrderBySortOrderAscIdAsc(
            Long tenantId, MediaOwnerType ownerType, Long ownerId);

    /**
     * Backs the list screens and read projections, which need one image per row without one query
     * per row. Ordered so that a multi-valued purpose's "first" attachment is a stable choice
     * rather than whatever the planner returned.
     */
    List<MediaLink> findByTenantIdAndPurposeAndOwnerIdInOrderBySortOrderAscIdAsc(
            Long tenantId, MediaPurpose purpose, List<Long> ownerIds);

    /**
     * Every link pointing at a file. The read endpoint resolves permission through these, and a
     * file with no link is unreachable rather than public.
     */
    List<MediaLink> findByTenantIdAndMediaFileId(Long tenantId, Long mediaFileId);

    boolean existsByMediaFileId(Long mediaFileId);
}
