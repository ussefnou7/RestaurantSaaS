package com.smart.restaurant_saas.inventory.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.smart.restaurant_saas.inventory.purchase.PurchaseReturn;
import com.smart.restaurant_saas.inventory.purchase.PurchaseReturnLine;

@Repository
public interface PurchaseReturnRepository extends JpaRepository<PurchaseReturn, Long> {

    Optional<PurchaseReturn> findByIdAndTenantId(Long id, Long tenantId);

    List<PurchaseReturn> findByTenantIdOrderByReturnDateDesc(Long tenantId);

    @Query("""
        SELECT pr.id AS returnId, pr.returnNumber AS returnCode
        FROM PurchaseReturn pr
        WHERE pr.tenantId = :tenantId
          AND pr.originalInvoice.id = :invoiceId
        ORDER BY pr.id ASC
        """)
    List<ReturnReferenceSummary> findReturnSummariesByOriginalInvoice(
        @Param("tenantId") Long tenantId,
        @Param("invoiceId") Long invoiceId);

    // Posted return lines for an invoice. Service converts each quantity into the original line UOM
    // before summing, because return lines may be entered in mixed convertible UOMs.
    @Query("""
        SELECT prl
        FROM PurchaseReturnLine prl
        WHERE prl.purchaseReturn.originalInvoice.id = :invoiceId
          AND prl.purchaseReturn.tenantId = :tenantId
          AND prl.purchaseReturn.status = 'POSTED'
        """)
    List<PurchaseReturnLine> findPostedReturnLinesByInvoiceId(
        @Param("tenantId") Long tenantId,
        @Param("invoiceId") Long invoiceId);

    interface ReturnReferenceSummary {
        Long getReturnId();
        String getReturnCode();
    }
}
