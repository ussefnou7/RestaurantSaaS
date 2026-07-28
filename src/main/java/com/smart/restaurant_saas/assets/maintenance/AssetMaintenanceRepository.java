package com.smart.restaurant_saas.assets.maintenance;

import com.smart.restaurant_saas.assets.core.enums.AssetCategory;
import com.smart.restaurant_saas.assets.maintenance.dto.AssetMaintenanceListItemResponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AssetMaintenanceRepository extends JpaRepository<AssetMaintenance, Long> {

    List<AssetMaintenance> findByTenantIdAndAssetLineIdOrderByIdDesc(Long tenantId, Long assetLineId);

    long countByTenantIdAndAssetLineId(Long tenantId, Long assetLineId);

    @Query(value = """
        select new com.smart.restaurant_saas.assets.maintenance.dto.AssetMaintenanceListItemResponse(
            m.id, a.id, a.name, a.nameAr, a.category, a.branchId,
            l.id, l.label, m.cost, m.maintenanceDate, m.description, m.vendor)
        from AssetMaintenance m
        join AssetLine l on l.id = m.assetLineId
        join Asset a on a.id = l.assetId
        where a.tenantId = :tenantId
          and m.tenantId = :tenantId
          and l.tenantId = :tenantId
          and (:assetId is null or a.id = :assetId)
          and (:assetLineId is null or l.id = :assetLineId)
          and (:category is null or a.category = :category)
          and (:branchId is null or a.branchId = :branchId)
          and (:dateFrom is null or m.maintenanceDate >= :dateFrom)
          and (:dateTo is null or m.maintenanceDate <= :dateTo)
        """,
        countQuery = """
        select count(m)
        from AssetMaintenance m
        join AssetLine l on l.id = m.assetLineId
        join Asset a on a.id = l.assetId
        where a.tenantId = :tenantId
          and m.tenantId = :tenantId
          and l.tenantId = :tenantId
          and (:assetId is null or a.id = :assetId)
          and (:assetLineId is null or l.id = :assetLineId)
          and (:category is null or a.category = :category)
          and (:branchId is null or a.branchId = :branchId)
          and (:dateFrom is null or m.maintenanceDate >= :dateFrom)
          and (:dateTo is null or m.maintenanceDate <= :dateTo)
        """)
    Page<AssetMaintenanceListItemResponse> findListItems(
        @Param("tenantId") Long tenantId,
        @Param("assetId") Long assetId,
        @Param("assetLineId") Long assetLineId,
        @Param("category") AssetCategory category,
        @Param("branchId") Long branchId,
        @Param("dateFrom") LocalDate dateFrom,
        @Param("dateTo") LocalDate dateTo,
        Pageable pageable);
}
