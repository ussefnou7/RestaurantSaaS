package com.smart.restaurant_saas.menu.recipe;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RecipeItemRepository extends JpaRepository<RecipeItem, Long> {

    @Query("""
        SELECT r FROM RecipeItem r
        JOIN FETCH r.material
        JOIN FETCH r.uom
        WHERE r.recipe.id = :recipeId
          AND r.tenantId = :tenantId
        ORDER BY r.id ASC
        """)
    List<RecipeItem> findByRecipeId(@Param("recipeId") Long recipeId,
                                    @Param("tenantId") Long tenantId);

    @Query("""
        SELECT r FROM RecipeItem r
        JOIN FETCH r.recipe recipe
        JOIN FETCH r.material
        JOIN FETCH r.uom
        WHERE r.recipe.id IN :recipeIds
          AND r.tenantId = :tenantId
        ORDER BY recipe.createdAt DESC, recipe.id DESC, r.id ASC
        """)
    List<RecipeItem> findByRecipeIds(@Param("recipeIds") List<Long> recipeIds,
                                     @Param("tenantId") Long tenantId);
}
