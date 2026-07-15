package com.smart.restaurant_saas.assets.maintenance;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AssetMaintenanceRepository extends JpaRepository<AssetMaintenance, Long> {

    List<AssetMaintenance> findByTenantIdAndAssetLineIdOrderByIdDesc(Long tenantId, Long assetLineId);

    long countByTenantIdAndAssetLineId(Long tenantId, Long assetLineId);
}
