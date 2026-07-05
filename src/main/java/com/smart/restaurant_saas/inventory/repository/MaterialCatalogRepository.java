package com.smart.restaurant_saas.inventory.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.smart.restaurant_saas.inventory.material.MaterialCatalog;

@Repository
public interface MaterialCatalogRepository extends JpaRepository<MaterialCatalog, Long> {

    /**
     * Browse the active global catalog with optional filters. The entity exposes
     * the stock/display unit as defaultStockUom / defaultDisplayUom.
     */
    @Query("""
        SELECT c FROM MaterialCatalog c
        LEFT JOIN FETCH c.category
        LEFT JOIN FETCH c.defaultStockUom
        LEFT JOIN FETCH c.defaultDisplayUom
        WHERE c.active = true
        AND (CAST(:search AS string) IS NULL
             OR LOWER(c.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
             OR LOWER(c.code) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
        AND (:categoryId IS NULL OR c.category.id = :categoryId)
        AND (:uomId IS NULL OR c.defaultStockUom.id = :uomId)
        ORDER BY c.name ASC
        """)
    List<MaterialCatalog> findByFilters(
        @Param("search") String search,
        @Param("categoryId") Long categoryId,
        @Param("uomId") Long uomId
    );
}
