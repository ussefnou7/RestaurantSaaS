package com.smart.restaurant_saas.menu.recipe;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    Optional<Recipe> findByIdAndTenantId(Long id, Long tenantId);

    Optional<Recipe> findByProductIdAndTenantIdAndActiveTrue(Long productId, Long tenantId);

    List<Recipe> findByProductIdAndTenantIdOrderByCreatedAtDescIdDesc(Long productId, Long tenantId);
}
