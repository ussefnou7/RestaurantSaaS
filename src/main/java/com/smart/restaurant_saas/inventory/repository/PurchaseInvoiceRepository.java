package com.smart.restaurant_saas.inventory.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.smart.restaurant_saas.inventory.core.enums.DocumentStatus;
import com.smart.restaurant_saas.inventory.purchase.PurchaseInvoice;

@Repository
public interface PurchaseInvoiceRepository extends JpaRepository<PurchaseInvoice, Long> {

    List<PurchaseInvoice> findByTenantIdOrderByInvoiceDateDesc(Long tenantId);

    // for the dropdown in the return form (posted invoices only)
    List<PurchaseInvoice> findByTenantIdAndStatusOrderByInvoiceDateDesc(
        Long tenantId, DocumentStatus status);

    // verify the invoice belongs to the tenant
    Optional<PurchaseInvoice> findByIdAndTenantId(Long id, Long tenantId);
}
