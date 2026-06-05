package com.smart.restaurant_saas.inventory.repository;

import com.smart.restaurant_saas.inventory.entity.InventoryTransaction;
import com.smart.restaurant_saas.inventory.enums.InventoryTransactionDirection;
import com.smart.restaurant_saas.inventory.enums.InventoryTransactionType;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryTransactionRepository extends JpaRepository<InventoryTransaction, Long> {

    @Query("""
            select transaction
            from InventoryTransaction transaction
            join fetch transaction.warehouse warehouse
            join fetch transaction.material material
            join fetch material.category category
            join fetch transaction.enteredUom enteredUom
            join fetch transaction.stockUom stockUom
            where transaction.tenantId = :tenantId
              and (:warehouseId is null or warehouse.id = :warehouseId)
              and (:materialId is null or material.id = :materialId)
              and (:categoryId is null or category.id = :categoryId)
              and (:transactionType is null or transaction.transactionType = :transactionType)
              and (:direction is null or transaction.direction = :direction)
              and (:dateFrom is null or transaction.transactionDate >= :dateFrom)
              and (:dateTo is null or transaction.transactionDate <= :dateTo)
              and (:referenceType is null or transaction.referenceType = :referenceType)
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
                  or lower(transaction.referenceType) like :search
              )
            order by transaction.transactionDate desc, transaction.id desc
            """)
    List<InventoryTransaction> findByTenantIdAndFilters(
            @Param("tenantId") Long tenantId,
            @Param("warehouseId") Long warehouseId,
            @Param("materialId") Long materialId,
            @Param("categoryId") Long categoryId,
            @Param("transactionType") InventoryTransactionType transactionType,
            @Param("direction") InventoryTransactionDirection direction,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            @Param("referenceType") String referenceType,
            @Param("search") String search
    );
}
