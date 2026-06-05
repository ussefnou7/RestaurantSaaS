package com.smart.restaurant_saas.inventory.repository;

import com.smart.restaurant_saas.inventory.entity.MaterialCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MaterialCategoryRepository extends JpaRepository<MaterialCategory, Long> {

    Optional<MaterialCategory> findByIdAndTenantIdIsNull(Long id);

    Optional<MaterialCategory> findByTenantIdIsNullAndCode(String code);

    boolean existsByTenantIdIsNullAndCode(String code);

    boolean existsByTenantIdIsNullAndCodeAndIdNot(String code, Long id);

    boolean existsByTenantIdAndCode(Long tenantId, String code);

    Optional<MaterialCategory> findByTenantIdAndCode(Long tenantId, String code);

    boolean existsByTenantIdAndCodeAndIdNot(Long tenantId, String code, Long id);

    Optional<MaterialCategory> findByIdAndTenantId(Long id, Long tenantId);

    @Query("""
            select category
            from MaterialCategory category
            where category.id = :id
              and (category.tenantId is null or category.tenantId = :tenantId)
            """)
    Optional<MaterialCategory> findAccessibleById(
            @Param("id") Long id,
            @Param("tenantId") Long tenantId
    );

    @Query("""
            select category
            from MaterialCategory category
            where (category.tenantId is null or category.tenantId = :tenantId)
              and (:active is null or category.active = :active)
              and (
                  :search is null
                  or lower(category.code) like :search
                  or lower(category.name) like :search
                  or lower(category.nameAr) like :search
              )
            order by case when category.tenantId is null then 0 else 1 end,
                     case when category.sortOrder is null then 1 else 0 end,
                     category.sortOrder asc,
                     category.name asc,
                     category.id asc
            """)
    List<MaterialCategory> findAccessibleByFilters(
            @Param("tenantId") Long tenantId,
            @Param("search") String search,
            @Param("active") Boolean active
    );

    @Query("""
            select category
            from MaterialCategory category
            where category.tenantId is null
              and (:active is null or category.active = :active)
              and (
                  :search is null
                  or lower(category.code) like :search
                  or lower(category.name) like :search
                  or lower(category.nameAr) like :search
              )
            order by case when category.sortOrder is null then 1 else 0 end,
                     category.sortOrder asc,
                     category.name asc,
                     category.id asc
            """)
    List<MaterialCategory> findByFilters(
            @Param("search") String search,
            @Param("active") Boolean active
    );
}
