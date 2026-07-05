package com.smart.restaurant_saas.inventory.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.smart.restaurant_saas.inventory.category.MaterialCategory;

@Repository
public interface MaterialCategoryRepository extends JpaRepository<MaterialCategory, Long> {

    Optional<MaterialCategory> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByTenantIdAndCode(Long tenantId, String code);

    @Query("""
        SELECT c FROM MaterialCategory c
        WHERE (c.tenantId IS NULL OR c.tenantId = :tenantId)
        AND (CAST(:search AS string) IS NULL
             OR LOWER(c.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
             OR LOWER(c.code) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
        AND (:active IS NULL OR c.active = :active)
        ORDER BY CASE WHEN c.tenantId IS NULL THEN 0 ELSE 1 END ASC,
                 c.sortOrder ASC, c.name ASC
        """)
    List<MaterialCategory> findByFilters(
        @Param("tenantId") Long tenantId,
        @Param("search") String search,
        @Param("active") Boolean active
    );
}
