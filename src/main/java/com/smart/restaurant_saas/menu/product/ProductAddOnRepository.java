package com.smart.restaurant_saas.menu.product;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductAddOnRepository extends JpaRepository<ProductAddOn, Long> {

    List<ProductAddOn> findByTenantIdAndProductId(Long tenantId, Long productId);

    boolean existsByTenantIdAndProductIdAndAddOnProductId(Long tenantId,
                                                          Long productId,
                                                          Long addOnProductId);

    Optional<ProductAddOn> findByTenantIdAndProductIdAndAddOnProductId(Long tenantId,
                                                                       Long productId,
                                                                       Long addOnProductId);
}
