package com.smart.restaurant_saas.menu.product;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    @EntityGraph(attributePaths = "menuCategory")
    List<Product> findByTenantIdOrderByNameAsc(Long tenantId);

    @Query("""
        SELECT p FROM Product p
        JOIN FETCH p.menuCategory category
        WHERE p.tenantId = :tenantId
        ORDER BY category.sortOrder ASC, category.id ASC, p.name ASC, p.id ASC
        """)
    List<Product> findMenuCatalog(@Param("tenantId") Long tenantId);

    @Query("""
        SELECT p FROM Product p
        JOIN FETCH p.menuCategory
        WHERE p.tenantId = :tenantId
          AND p.parentProductId IS NULL
          AND (:excludeProductId IS NULL OR p.id <> :excludeProductId)
          AND NOT EXISTS (
              SELECT r.id FROM Recipe r
              WHERE r.tenantId = :tenantId
                AND r.product.id = p.id
                AND r.active = TRUE
          )
        ORDER BY p.name ASC, p.id ASC
        """)
    List<Product> findParentEligible(@Param("tenantId") Long tenantId,
                                     @Param("excludeProductId") Long excludeProductId);

    @EntityGraph(attributePaths = "menuCategory")
    Optional<Product> findByIdAndTenantId(Long id, Long tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Product> findWithLockByIdAndTenantId(Long id, Long tenantId);

    @Query("""
        SELECT p FROM Product p
        JOIN FETCH p.menuCategory
        WHERE p.menuCategory.id = :menuCategoryId
          AND p.tenantId = :tenantId
        ORDER BY p.name ASC
        """)
    List<Product> findByMenuCategoryId(@Param("menuCategoryId") Long menuCategoryId,
                                       @Param("tenantId") Long tenantId);

    boolean existsByNameAndTenantId(String name, Long tenantId);

    boolean existsByMenuCategoryIdAndTenantId(Long menuCategoryId, Long tenantId);

    // Derived-parent check: a product is a parent iff another product references it.
    boolean existsByParentProductId(Long parentProductId);

    boolean existsByParentProductIdAndTenantId(Long parentProductId, Long tenantId);

    @EntityGraph(attributePaths = "menuCategory")
    List<Product> findByParentProductIdAndTenantId(Long parentProductId, Long tenantId);

    @Query("""
        SELECT CASE WHEN COUNT(p) > 0 THEN TRUE ELSE FALSE END
        FROM Product p
        WHERE p.tenantId = :tenantId
          AND p.parentProductId = :parentProductId
          AND (p.variantLabel = :variantLabel OR p.variantLabelAr = :variantLabelAr)
          AND (:excludedProductId IS NULL OR p.id <> :excludedProductId)
        """)
    boolean existsSiblingWithVariantLabel(@Param("tenantId") Long tenantId,
                                          @Param("parentProductId") Long parentProductId,
                                          @Param("variantLabel") String variantLabel,
                                          @Param("variantLabelAr") String variantLabelAr,
                                          @Param("excludedProductId") Long excludedProductId);
}
