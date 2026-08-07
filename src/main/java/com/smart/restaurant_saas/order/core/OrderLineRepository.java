package com.smart.restaurant_saas.order.core;

import com.smart.restaurant_saas.order.reports.SalesByProductAggregate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderLineRepository extends JpaRepository<OrderLine, Long> {

    List<OrderLine> findByOrderIdAndTenantIdOrderByIdAsc(Long orderId, Long tenantId);

    /**
     * Sales by product: one row per product sold on a COMPLETE order in the window. Read-only.
     *
     * <p><b>This report is pre-tax and cannot be otherwise.</b> {@code tax_amount} lives on the
     * order, not the line, so attributing it across products would require inventing an
     * apportionment rule. {@code line_total} is the honest figure. The consequence is that summing
     * this report's revenue and comparing it to sales-over-time's {@code totalAmount} will show a
     * difference, and that difference is exactly the tax — it should match that report's
     * {@code subtotal} instead.
     *
     * <p><b>Share is computed against the sum of {@code line_total} within the filtered scope</b>
     * ({@code SUM(SUM(..)) OVER ()}), not against order {@code total_amount}. Mixing the two would
     * produce percentages that do not add to 100.
     *
     * <p>The grain is the line but the filters are all on the order, so the join is mandatory rather
     * than a convenience — the status rule in particular must be applied to the parent order.
     *
     * <p>Product identity is {@code order_line.product_id}. It survives deletion because
     * {@code fk_order_line_product} carries no {@code ON DELETE} action, so removing a product that
     * has ever sold is blocked outright — historical sales can never be orphaned or silently
     * dropped. It does <em>not</em> survive a rename: the report shows the product's current name,
     * so renaming retro-labels history.
     *
     * <p>There is no product code or Arabic name in the output because the {@code product} table has
     * neither column.
     */
    @Query(nativeQuery = true, value = """
        SELECT p.id                                          AS "productId",
               p.name                                        AS "productName",
               COALESCE(SUM(l.quantity), 0)                  AS "quantitySold",
               COALESCE(SUM(l.line_total), 0)                AS "revenue",
               COALESCE(SUM(l.line_total), 0) * 100.0
                   / NULLIF(SUM(SUM(l.line_total)) OVER (), 0) AS "revenueSharePercent"
        FROM order_line l
        JOIN orders o  ON o.id = l.order_id
        JOIN product p ON p.id = l.product_id
        WHERE o.tenant_id = :tenantId
          AND o.status = 'COMPLETE'
          AND o.order_date >= :fromInclusive
          AND o.order_date <  :toExclusive
          AND (CAST(:branchId      AS bigint)  IS NULL OR o.branch_id  = CAST(:branchId      AS bigint))
          AND (CAST(:cashierUserId AS bigint)  IS NULL OR o.created_by = CAST(:cashierUserId AS bigint))
          AND (CAST(:orderType     AS varchar) IS NULL OR o.order_type = CAST(:orderType     AS varchar))
        GROUP BY p.id, p.name
        ORDER BY COALESCE(SUM(l.line_total), 0) DESC, p.name ASC
        """)
    List<SalesByProductAggregate> aggregateSalesByProduct(
            @Param("tenantId") Long tenantId,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive,
            @Param("branchId") Long branchId,
            @Param("cashierUserId") Long cashierUserId,
            @Param("orderType") String orderType
    );
}
