package com.smart.restaurant_saas.inventory.repository;

import com.smart.restaurant_saas.inventory.entity.StockBalance;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockBalanceRepository extends JpaRepository<StockBalance, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select balance
            from StockBalance balance
            join fetch balance.warehouse warehouse
            join fetch balance.material material
            join fetch material.category category
            join fetch material.stockUom stockUom
            join fetch material.displayUom displayUom
            join fetch balance.uom uom
            where balance.tenantId = :tenantId
              and warehouse.id = :warehouseId
              and material.id = :materialId
            """)
    Optional<StockBalance> findForUpdate(
            @Param("tenantId") Long tenantId,
            @Param("warehouseId") Long warehouseId,
            @Param("materialId") Long materialId
    );

    @Query("""
            select balance
            from StockBalance balance
            join fetch balance.warehouse warehouse
            join fetch balance.material material
            join fetch material.category category
            join fetch material.stockUom stockUom
            join fetch material.displayUom displayUom
            join fetch balance.uom uom
            where balance.tenantId = :tenantId
              and (:warehouseId is null or warehouse.id = :warehouseId)
              and (:materialId is null or material.id = :materialId)
              and (:categoryId is null or category.id = :categoryId)
              and (
                  :lowStock is null
                  or (:lowStock = true and balance.quantity <= material.minimumStockLevel)
                  or (:lowStock = false and balance.quantity > material.minimumStockLevel)
              )
              and (
                  :search is null
                  or lower(warehouse.code) like :search
                  or lower(warehouse.name) like :search
                  or lower(warehouse.nameAr) like :search
                  or lower(material.code) like :search
                  or lower(material.name) like :search
                  or lower(material.nameAr) like :search
                  or lower(category.code) like :search
                  or lower(category.name) like :search
                  or lower(category.nameAr) like :search
              )
            order by warehouse.name asc, material.name asc, balance.id asc
            """)
    List<StockBalance> findByTenantIdAndFilters(
            @Param("tenantId") Long tenantId,
            @Param("warehouseId") Long warehouseId,
            @Param("materialId") Long materialId,
            @Param("categoryId") Long categoryId,
            @Param("lowStock") Boolean lowStock,
            @Param("search") String search
    );
}
