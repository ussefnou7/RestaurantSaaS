package com.smart.restaurant_saas.media;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaFileRepository extends JpaRepository<MediaFile, Long> {

    Optional<MediaFile> findByIdAndTenantId(Long id, Long tenantId);
}
