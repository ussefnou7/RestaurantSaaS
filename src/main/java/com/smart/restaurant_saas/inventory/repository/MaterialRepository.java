package com.smart.restaurant_saas.inventory.repository;

import com.smart.restaurant_saas.inventory.entity.Material;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MaterialRepository extends JpaRepository<Material, Long> {

    boolean existsByTenantIdAndCode(Long tenantId, String code);

    Optional<Material> findByTenantIdAndCode(Long tenantId, String code);

    boolean existsByTenantIdAndCodeAndIdNot(Long tenantId, String code, Long id);

    @Query("""
            select count(material) > 0
            from Material material
            where material.tenantId = :tenantId
              and material.catalog.id = :catalogId
            """)
    boolean existsByTenantIdAndCatalogId(
            @Param("tenantId") Long tenantId,
            @Param("catalogId") Long catalogId
    );

    @Query("""
            select material
            from Material material
            left join fetch material.catalog catalog
            join fetch material.category category
            join fetch material.stockUom stockUom
            join fetch material.displayUom displayUom
            where material.id = :id
              and material.tenantId = :tenantId
            """)
    Optional<Material> findDetailedByIdAndTenantId(
            @Param("id") Long id,
            @Param("tenantId") Long tenantId
    );

    @Query("""
            select material
            from Material material
            left join fetch material.catalog catalog
            join fetch material.category category
            join fetch material.stockUom stockUom
            join fetch material.displayUom displayUom
            where material.tenantId = :tenantId
              and (:categoryId is null or category.id = :categoryId)
              and (:stockUomId is null or stockUom.id = :stockUomId)
              and (:displayUomId is null or displayUom.id = :displayUomId)
              and (:active is null or material.active = :active)
              and (:catalogId is null or catalog.id = :catalogId)
              and (
                  :search is null
                  or lower(material.code) like :search
                  or lower(material.name) like :search
                  or lower(material.nameAr) like :search
              )
            order by material.id desc
            """)
    List<Material> findByTenantIdAndFilters(
            @Param("tenantId") Long tenantId,
            @Param("search") String search,
            @Param("categoryId") Long categoryId,
            @Param("stockUomId") Long stockUomId,
            @Param("displayUomId") Long displayUomId,
            @Param("active") Boolean active,
            @Param("catalogId") Long catalogId
    );
}
