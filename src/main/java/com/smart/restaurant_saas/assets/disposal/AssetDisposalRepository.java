package com.smart.restaurant_saas.assets.disposal;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AssetDisposalRepository extends JpaRepository<AssetDisposal, Long> {

    List<AssetDisposal> findByTenantIdAndAssetLineIdOrderByIdDesc(Long tenantId, Long assetLineId);

    Page<AssetDisposal> findByTenantIdOrderByDisposalDateDescIdDesc(Long tenantId, Pageable pageable);

    long countByTenantIdAndAssetLineId(Long tenantId, Long assetLineId);
}
