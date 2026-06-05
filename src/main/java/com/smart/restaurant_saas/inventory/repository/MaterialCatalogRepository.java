package com.smart.restaurant_saas.inventory.repository;

import com.smart.restaurant_saas.inventory.entity.MaterialCatalog;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MaterialCatalogRepository extends JpaRepository<MaterialCatalog, Long> {

    boolean existsByCode(String code);

    Optional<MaterialCatalog> findByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    @Query("""
            select material
            from MaterialCatalog material
            join fetch material.category category
            join fetch material.defaultStockUom defaultStockUom
            join fetch material.defaultDisplayUom defaultDisplayUom
            where material.id = :id
            """)
    Optional<MaterialCatalog> findDetailedById(@Param("id") Long id);

    @Query("""
            select material
            from MaterialCatalog material
            join fetch material.category category
            join fetch material.defaultStockUom defaultStockUom
            join fetch material.defaultDisplayUom defaultDisplayUom
            where (:categoryId is null or category.id = :categoryId)
              and (:stockUomId is null or defaultStockUom.id = :stockUomId)
              and (:displayUomId is null or defaultDisplayUom.id = :displayUomId)
              and (:active is null or material.active = :active)
              and (
                  :search is null
                  or lower(material.code) like :search
                  or lower(material.name) like :search
                  or lower(material.nameAr) like :search
              )
            order by case when material.sortOrder is null then 1 else 0 end,
                     material.sortOrder asc,
                     material.name asc,
                     material.id asc
            """)
    List<MaterialCatalog> findByFilters(
            @Param("categoryId") Long categoryId,
            @Param("stockUomId") Long stockUomId,
            @Param("displayUomId") Long displayUomId,
            @Param("search") String search,
            @Param("active") Boolean active
    );
}
