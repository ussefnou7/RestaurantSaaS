package com.smart.restaurant_saas.menu.recipe;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RecipeItemRepository extends JpaRepository<RecipeItem, Long> {

    @Query("""
        SELECT r FROM RecipeItem r
        JOIN FETCH r.material
        JOIN FETCH r.uom
        WHERE r.product.id = :productId
          AND r.tenantId = :tenantId
        ORDER BY r.id ASC
        """)
    List<RecipeItem> findByProductId(@Param("productId") Long productId,
                                     @Param("tenantId") Long tenantId);

    @Modifying(flushAutomatically = true)
    @Query("""
        DELETE FROM RecipeItem r
        WHERE r.product.id = :productId
          AND r.tenantId = :tenantId
        """)
    void deleteByProductId(@Param("productId") Long productId,
                           @Param("tenantId") Long tenantId);
}
