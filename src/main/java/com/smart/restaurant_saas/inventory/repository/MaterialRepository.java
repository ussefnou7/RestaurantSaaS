package com.smart.restaurant_saas.inventory.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.smart.restaurant_saas.inventory.material.Material;

@Repository
public interface MaterialRepository extends JpaRepository<Material, Long> {

    Optional<Material> findByIdAndTenantId(Long id, Long tenantId);

    boolean existsByTenantIdAndCode(Long tenantId, String code);

    @Query("""
        SELECT m FROM Material m
        LEFT JOIN FETCH m.category
        LEFT JOIN FETCH m.stockUom
        LEFT JOIN FETCH m.displayUom
        WHERE m.tenantId = :tenantId
        AND (CAST(:search AS string) IS NULL
             OR LOWER(m.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
             OR LOWER(m.code) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
        AND (:categoryId IS NULL OR m.category.id = :categoryId)
        AND (:defaultUomId IS NULL OR m.stockUom.id = :defaultUomId)
        AND (:active IS NULL OR m.active = :active)
        ORDER BY m.name ASC
        """)
    List<Material> findByFilters(
        @Param("tenantId") Long tenantId,
        @Param("search") String search,
        @Param("categoryId") Long categoryId,
        @Param("defaultUomId") Long defaultUomId,
        @Param("active") Boolean active
    );

    /**
     * Materials with both UOM sides eagerly loaded, for callers that must convert between the
     * ledger's stock layer and the display layer (D87). Both associations are
     * {@code optional = false}, so an inner {@code JOIN FETCH} cannot drop a row.
     *
     * <p>Exists so the date-ranged ledger reports resolve every material's conversion pair in one
     * query instead of lazy-loading two UOMs per aggregated material.
     */
    @Query("""
        SELECT m FROM Material m
        JOIN FETCH m.stockUom
        JOIN FETCH m.displayUom
        WHERE m.tenantId = :tenantId
        AND m.id IN :ids
        """)
    List<Material> findAllWithUomsByIdIn(
        @Param("tenantId") Long tenantId,
        @Param("ids") List<Long> ids
    );

    /**
     * Catalog ids that the tenant has already imported (has a material linked to).
     */
    @Query("""
        SELECT m.catalog.id FROM Material m
        WHERE m.tenantId = :tenantId
        AND m.catalog.id IN :catalogIds
        """)
    List<Long> findAlreadyImportedCatalogIds(
        @Param("tenantId") Long tenantId,
        @Param("catalogIds") List<Long> catalogIds
    );

    /**
     * Pairs of [catalogId, materialId] for the tenant's catalog-linked materials.
     * Used to flag already-imported catalog items in the browse list.
     */
    @Query("""
        SELECT m.catalog.id, m.id FROM Material m
        WHERE m.tenantId = :tenantId
        AND m.catalog.id IN :catalogIds
        """)
    List<Object[]> findImportedCatalogPairs(
        @Param("tenantId") Long tenantId,
        @Param("catalogIds") List<Long> catalogIds
    );
}
