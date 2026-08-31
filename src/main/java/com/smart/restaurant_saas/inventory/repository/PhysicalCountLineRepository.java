package com.smart.restaurant_saas.inventory.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.smart.restaurant_saas.inventory.physicalcount.PhysicalCountLine;
import com.smart.restaurant_saas.tenant.TenantUnscoped;

@Repository
public interface PhysicalCountLineRepository extends JpaRepository<PhysicalCountLine, Long> {

    @TenantUnscoped("physicalCountId must be a count already loaded for the acting tenant, e.g. "
        + "via PhysicalCountRepository.findByIdAndTenantId; the line table is reached by parent "
        + "id alone.")
    List<PhysicalCountLine> findByPhysicalCountId(Long physicalCountId);

    @Query("""
        SELECT pcl FROM PhysicalCountLine pcl
        LEFT JOIN FETCH pcl.material m
        LEFT JOIN FETCH pcl.uom u
        WHERE pcl.physicalCount.id = :countId
        """)
    @TenantUnscoped("countId must be a count already loaded for the acting tenant; same "
        + "parent-scoping requirement as findByPhysicalCountId.")
    List<PhysicalCountLine> findByPhysicalCountIdWithDetails(
        @Param("countId") Long countId);
}
