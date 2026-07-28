package com.smart.restaurant_saas.assets.disposal;

import com.smart.restaurant_saas.assets.core.enums.AssetCategory;
import com.smart.restaurant_saas.assets.disposal.dto.AssetDisposalListItemResponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AssetDisposalRepository extends JpaRepository<AssetDisposal, Long> {

    List<AssetDisposal> findByTenantIdAndAssetLineIdOrderByIdDesc(Long tenantId, Long assetLineId);

    Page<AssetDisposal> findByTenantIdOrderByDisposalDateDescIdDesc(Long tenantId, Pageable pageable);

    long countByTenantIdAndAssetLineId(Long tenantId, Long assetLineId);

    @Query(value = """
        select new com.smart.restaurant_saas.assets.disposal.dto.AssetDisposalListItemResponse(
            d.id, a.id, a.name, a.nameAr, a.category, a.branchId,
            l.id, l.label, l.unitCost, d.quantityDisposed, d.disposalDate, d.reason, d.notes)
        from AssetDisposal d
        join AssetLine l on l.id = d.assetLineId
        join Asset a on a.id = l.assetId
        where a.tenantId = :tenantId
          and d.tenantId = :tenantId
          and l.tenantId = :tenantId
          and (:assetId is null or a.id = :assetId)
          and (:assetLineId is null or l.id = :assetLineId)
          and (:category is null or a.category = :category)
          and (:branchId is null or a.branchId = :branchId)
          and (:dateFrom is null or d.disposalDate >= :dateFrom)
          and (:dateTo is null or d.disposalDate <= :dateTo)
        """,
        countQuery = """
        select count(d)
        from AssetDisposal d
        join AssetLine l on l.id = d.assetLineId
        join Asset a on a.id = l.assetId
        where a.tenantId = :tenantId
          and d.tenantId = :tenantId
          and l.tenantId = :tenantId
          and (:assetId is null or a.id = :assetId)
          and (:assetLineId is null or l.id = :assetLineId)
          and (:category is null or a.category = :category)
          and (:branchId is null or a.branchId = :branchId)
          and (:dateFrom is null or d.disposalDate >= :dateFrom)
          and (:dateTo is null or d.disposalDate <= :dateTo)
        """)
    Page<AssetDisposalListItemResponse> findListItems(
        @Param("tenantId") Long tenantId,
        @Param("assetId") Long assetId,
        @Param("assetLineId") Long assetLineId,
        @Param("category") AssetCategory category,
        @Param("branchId") Long branchId,
        @Param("dateFrom") LocalDate dateFrom,
        @Param("dateTo") LocalDate dateTo,
        Pageable pageable);
}
