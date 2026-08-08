package com.smart.restaurant_saas.table;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TableRepository extends JpaRepository<RestaurantTable, Long> {

    Optional<RestaurantTable> findByIdAndTenantId(Long id, Long tenantId);

    @Query("""
            SELECT t FROM RestaurantTable t
            LEFT JOIN FETCH t.branch b
            LEFT JOIN FETCH t.section s
            WHERE t.tenantId = :tenantId
              AND (:branchId IS NULL OR b.id = :branchId)
              AND (:sectionId IS NULL OR s.id = :sectionId)
            ORDER BY b.id ASC, s.name ASC, t.name ASC, t.id ASC
            """)
    List<RestaurantTable> findByFilters(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("sectionId") Long sectionId
    );

    @Query("""
            SELECT COUNT(t) > 0 FROM RestaurantTable t
            WHERE t.section.id = :sectionId
            """)
    boolean existsBySectionId(@Param("sectionId") Long sectionId);

    // Section cascade-delete (D78, updated): all tables in a section are removed
    // together with it, once the no-orders guard has passed.
    @Query("SELECT t FROM RestaurantTable t WHERE t.section.id = :sectionId")
    List<RestaurantTable> findAllBySectionId(@Param("sectionId") Long sectionId);
}
