package com.smart.restaurant_saas.assets.assetline;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AssetLineRepository extends JpaRepository<AssetLine, Long> {

    List<AssetLine> findByTenantIdAndAssetIdOrderByIdAsc(Long tenantId, Long assetId);

    List<AssetLine> findByTenantId(Long tenantId);

    Optional<AssetLine> findByIdAndTenantId(Long id, Long tenantId);

    long countByTenantIdAndAssetId(Long tenantId, Long assetId);
}
