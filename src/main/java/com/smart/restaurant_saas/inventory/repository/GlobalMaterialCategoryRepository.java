package com.smart.restaurant_saas.inventory.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.smart.restaurant_saas.inventory.category.MaterialCategory;

/**
 * Read-only access to system-level (global) material categories — those with
 * tenant_id = NULL. Used by the catalog import modal filter dropdown.
 */
@Repository
public interface GlobalMaterialCategoryRepository extends JpaRepository<MaterialCategory, Long> {

    List<MaterialCategory> findByTenantIdIsNullAndActiveOrderBySortOrderAscNameAsc(Boolean active);

    List<MaterialCategory> findByTenantIdIsNullOrderBySortOrderAscNameAsc();
}
