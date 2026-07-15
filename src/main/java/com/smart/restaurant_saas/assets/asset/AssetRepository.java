package com.smart.restaurant_saas.assets.asset;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AssetRepository extends JpaRepository<Asset, Long> {

    List<Asset> findByTenantIdOrderByIdDesc(Long tenantId);

    Optional<Asset> findByIdAndTenantId(Long id, Long tenantId);
}
