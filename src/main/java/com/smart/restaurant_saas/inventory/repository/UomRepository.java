package com.smart.restaurant_saas.inventory.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.smart.restaurant_saas.inventory.core.enums.UomType;
import com.smart.restaurant_saas.inventory.uom.Uom;

@Repository
public interface UomRepository extends JpaRepository<Uom, Long> {

    /**
     * Find a Uom by code, scoped to either global (tenant_id IS NULL)
     * or a specific tenant.
     */
    @Query("""
        SELECT u FROM Uom u
        WHERE u.code = :code
          AND (u.tenantId IS NULL OR u.tenantId = :tenantId)
        """)
    Optional<Uom> findByCodeForTenant(@Param("code") String code,
                                      @Param("tenantId") Long tenantId);

    /**
     * Find all active uoms visible to the tenant (global + tenant-owned).
     */
    @Query("""
        SELECT u FROM Uom u
        WHERE u.active = true
          AND (u.tenantId IS NULL OR u.tenantId = :tenantId)
        ORDER BY u.type, u.name
        """)
    List<Uom> findAllVisibleToTenant(@Param("tenantId") Long tenantId);

    /**
     * Find all uoms of a specific type visible to the tenant.
     */
    @Query("""
        SELECT u FROM Uom u
        WHERE u.type = :type
          AND u.active = true
          AND (u.tenantId IS NULL OR u.tenantId = :tenantId)
        """)
    List<Uom> findAllByTypeForTenant(@Param("type") UomType type,
                                     @Param("tenantId") Long tenantId);

    /**
     * Find all active UOMs available to a tenant (global + their own),
     * with global UOMs listed first.
     */
    @Query("""
        SELECT u FROM Uom u
        WHERE (u.tenantId IS NULL OR u.tenantId = :tenantId)
          AND u.active = true
        ORDER BY CASE WHEN u.tenantId IS NULL THEN 0 ELSE 1 END ASC,
                 u.name ASC
        """)
    List<Uom> findAvailableForTenant(@Param("tenantId") Long tenantId);

    /**
     * Find every UOM resolvable by a tenant for display lookups, including inactive rows.
     */
    @Query("""
        SELECT u FROM Uom u
        LEFT JOIN FETCH u.baseUom
        WHERE (u.tenantId IS NULL OR u.tenantId = :tenantId)
        ORDER BY CASE WHEN u.tenantId IS NULL THEN 0 ELSE 1 END ASC,
                 u.name ASC
        """)
    List<Uom> findLookupForTenant(@Param("tenantId") Long tenantId);

    /**
     * Resolve one UOM visible to a tenant, including inactive rows.
     */
    @Query("""
        SELECT u FROM Uom u
        LEFT JOIN FETCH u.baseUom
        WHERE u.id = :id
          AND (u.tenantId IS NULL OR u.tenantId = :tenantId)
        """)
    Optional<Uom> findResolvableByIdForTenant(@Param("id") Long id,
                                              @Param("tenantId") Long tenantId);

    /**
     * Find all global UOMs only (for the SysAdmin panel), including inactive ones.
     */
    List<Uom> findByTenantIdIsNullOrderByNameAsc();

    /**
     * Count materials referencing this UOM (as stock or display UOM).
     * Used to block deletion of an in-use UOM.
     */
    @Query("""
        SELECT COUNT(m) FROM Material m
        WHERE m.stockUom.id = :uomId OR m.displayUom.id = :uomId
        """)
    long countMaterialsUsingUom(@Param("uomId") Long uomId);

    /** Uniqueness check for global UOM codes. */
    boolean existsByCodeAndTenantIdIsNull(String code);

    /** Uniqueness check for a tenant's own UOM codes. */
    boolean existsByCodeAndTenantId(String code, Long tenantId);
}
