package com.smart.restaurant_saas.inventory.repository;

import com.smart.restaurant_saas.inventory.entity.Warehouse;
import com.smart.restaurant_saas.inventory.enums.WarehouseType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    boolean existsByTenantIdAndCode(Long tenantId, String code);

    Optional<Warehouse> findByTenantIdAndCode(Long tenantId, String code);

    boolean existsByTenantIdAndCodeAndIdNot(Long tenantId, String code, Long id);

    @Query("""
            select warehouse
            from Warehouse warehouse
            left join fetch warehouse.branch branch
            where warehouse.id = :id
              and warehouse.tenantId = :tenantId
            """)
    Optional<Warehouse> findDetailedByIdAndTenantId(
            @Param("id") Long id,
            @Param("tenantId") Long tenantId
    );

    @Query("""
            select warehouse
            from Warehouse warehouse
            left join fetch warehouse.branch branch
            where warehouse.tenantId = :tenantId
              and (:branchId is null or branch.id = :branchId)
              and (:type is null or warehouse.type = :type)
              and (:active is null or warehouse.active = :active)
              and (
                  :search is null
                  or lower(warehouse.code) like :search
                  or lower(warehouse.name) like :search
                  or lower(warehouse.nameAr) like :search
              )
            order by warehouse.id desc
            """)
    List<Warehouse> findByTenantIdAndFilters(
            @Param("tenantId") Long tenantId,
            @Param("search") String search,
            @Param("branchId") Long branchId,
            @Param("type") WarehouseType type,
            @Param("active") Boolean active
    );
}
