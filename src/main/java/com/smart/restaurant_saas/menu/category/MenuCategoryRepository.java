package com.smart.restaurant_saas.menu.category;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MenuCategoryRepository extends JpaRepository<MenuCategory, Long> {

    List<MenuCategory> findByTenantIdOrderBySortOrderAscIdAsc(Long tenantId);

    Optional<MenuCategory> findByIdAndTenantId(Long id, Long tenantId);
}
