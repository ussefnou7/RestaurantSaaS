package com.smart.restaurant_saas.inventory.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import com.smart.restaurant_saas.common.ScopedConditionAggregate;
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

    /**
     * Dashboard alert A2: goods received but never posted, one row per (branch, warehouse).
     *
     * <p>DRAFT and COMPLETE are the two statuses in which the stock is physically on the shelf and
     * absent from the ledger, so the warehouse under-reports by the invoice's quantity and the
     * material looks closer to its minimum than it is. POSTED is done; CANCELLED never happened.
     *
     * <p><b>Aged on {@code receipt_date}, not {@code created_at}.</b> The delay the alert is about
     * is between the goods arriving and the paperwork catching up — and an invoice keyed in a week
     * late would show no age at all on {@code created_at}, which is exactly the case worth
     * catching. The caller passes a cutoff date, so "older than two days" is a tenant-local
     * calendar statement rather than a 48-hour arithmetic one (D101).
     *
     * <p>{@code totalValue} is the invoice total: the money whose stock is unaccounted for, which
     * is what ranks this against the other alerts.
     */
    @Query(nativeQuery = true, value = """
        SELECT w.branch_id                           AS "branchId",
               pi.warehouse_id                       AS "warehouseId",
               COUNT(*)                              AS "itemCount",
               COALESCE(SUM(pi.total_amount), 0)     AS "totalValue",
               MIN(CAST(pi.receipt_date AS timestamp)) AS "oldestAt"
        FROM purchase_invoice pi
        JOIN warehouse w ON w.id = pi.warehouse_id
        WHERE pi.tenant_id = :tenantId
          AND pi.status IN ('DRAFT', 'COMPLETE')
          AND pi.receipt_date < :receiptCutoff
          AND (CAST(:branchId AS bigint) IS NULL OR w.branch_id = CAST(:branchId AS bigint))
        GROUP BY w.branch_id, pi.warehouse_id
        """)
    List<ScopedConditionAggregate> aggregateUnpostedInvoices(
        @Param("tenantId") Long tenantId,
        @Param("branchId") Long branchId,
        @Param("receiptCutoff") LocalDate receiptCutoff
    );
}
