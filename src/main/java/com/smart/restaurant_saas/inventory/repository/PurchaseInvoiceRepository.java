package com.smart.restaurant_saas.inventory.repository;

import com.smart.restaurant_saas.inventory.entity.PurchaseInvoice;
import com.smart.restaurant_saas.inventory.enums.DocumentStatus;
import com.smart.restaurant_saas.inventory.enums.PurchasePaymentStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurchaseInvoiceRepository extends JpaRepository<PurchaseInvoice, Long> {

    boolean existsByTenantIdAndInvoiceNumber(Long tenantId, String invoiceNumber);

    boolean existsByTenantIdAndInvoiceNumberAndIdNot(Long tenantId, String invoiceNumber, Long id);

    @Query("""
            select distinct invoice
            from PurchaseInvoice invoice
            left join fetch invoice.supplier supplier
            join fetch invoice.warehouse warehouse
            left join fetch invoice.lines line
            left join fetch line.material material
            left join fetch material.category category
            left join fetch line.uom uom
            where invoice.id = :id
              and invoice.tenantId = :tenantId
            """)
    Optional<PurchaseInvoice> findDetailedByIdAndTenantId(
            @Param("id") Long id,
            @Param("tenantId") Long tenantId
    );

    @Query("""
            select distinct invoice
            from PurchaseInvoice invoice
            left join fetch invoice.supplier supplier
            join fetch invoice.warehouse warehouse
            left join fetch invoice.lines line
            left join fetch line.material material
            left join fetch material.category category
            left join fetch line.uom uom
            where invoice.tenantId = :tenantId
              and (:supplierId is null or supplier.id = :supplierId)
              and (:warehouseId is null or warehouse.id = :warehouseId)
              and (:status is null or invoice.status = :status)
              and (:paymentStatus is null or invoice.paymentStatus = :paymentStatus)
              and (:dateFrom is null or invoice.invoiceDate >= :dateFrom)
              and (:dateTo is null or invoice.invoiceDate <= :dateTo)
              and (
                  :search is null
                  or lower(invoice.invoiceNumber) like :search
                  or lower(invoice.notes) like :search
                  or lower(supplier.code) like :search
                  or lower(supplier.name) like :search
                  or lower(supplier.nameAr) like :search
                  or lower(warehouse.code) like :search
                  or lower(warehouse.name) like :search
                  or lower(warehouse.nameAr) like :search
              )
            order by invoice.invoiceDate desc, invoice.id desc
            """)
    List<PurchaseInvoice> findByTenantIdAndFilters(
            @Param("tenantId") Long tenantId,
            @Param("search") String search,
            @Param("supplierId") Long supplierId,
            @Param("warehouseId") Long warehouseId,
            @Param("status") DocumentStatus status,
            @Param("paymentStatus") PurchasePaymentStatus paymentStatus,
            @Param("dateFrom") LocalDate dateFrom,
            @Param("dateTo") LocalDate dateTo
    );
}
