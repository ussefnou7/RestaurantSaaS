package com.smart.restaurant_saas.menu.product;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    @EntityGraph(attributePaths = "menuCategory")
    List<Product> findByTenantIdOrderByNameAsc(Long tenantId);

    @EntityGraph(attributePaths = "menuCategory")
    Optional<Product> findByIdAndTenantId(Long id, Long tenantId);

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
}
