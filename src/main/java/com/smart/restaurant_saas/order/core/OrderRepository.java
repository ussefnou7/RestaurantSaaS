package com.smart.restaurant_saas.order.core;

import com.smart.restaurant_saas.order.core.enums.OrderSource;
import com.smart.restaurant_saas.order.core.enums.OrderStatus;
import com.smart.restaurant_saas.order.core.enums.OrderType;
import com.smart.restaurant_saas.order.reports.SalesByHourAggregate;
import com.smart.restaurant_saas.order.reports.SalesByPaymentMethodAggregate;
import com.smart.restaurant_saas.order.reports.SalesOverTimeAggregate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @EntityGraph(attributePaths = {"branch", "warehouse", "table", "lines", "lines.product", "lines.recipe"})
    Optional<Order> findByIdAndTenantId(Long id, Long tenantId);

    // O16: lets OrderService short-circuit a retried submission (same
    // idempotencyKey resent after a lost response) into a replay instead of
    // a duplicate order. The real backstop is the DB-level unique
    // constraint (uk_orders_tenant_idempotency, V24) for the race case.
    @EntityGraph(attributePaths = {"branch", "warehouse", "table", "lines", "lines.product", "lines.recipe"})
    Optional<Order> findByTenantIdAndIdempotencyKey(Long tenantId, String idempotencyKey);

    // Delete guards (D76/D78): a table — or a section's tables — can only be
    // deleted while no order references it.
    @Query("SELECT COUNT(o) > 0 FROM RestaurantOrder o WHERE o.table.id = :tableId")
    boolean existsByTableId(@Param("tableId") Long tableId);

    @Query("SELECT COUNT(o) > 0 FROM RestaurantOrder o WHERE o.table.section.id = :sectionId")
    boolean existsByTableSectionId(@Param("sectionId") Long sectionId);

    @EntityGraph(attributePaths = {"branch", "warehouse", "lines", "lines.product", "lines.recipe"})
    @Query("""
        SELECT o FROM RestaurantOrder o
        WHERE o.tenantId = :tenantId
          AND (:orderType IS NULL OR o.orderType = :orderType)
          AND (:orderSource IS NULL OR o.orderSource = :orderSource)
          AND (:status IS NULL OR o.status = :status)
          AND (:branchId IS NULL OR o.branch.id = :branchId)
          AND (CAST(:fromDate AS timestamp) IS NULL OR o.orderDate >= :fromDate)
          AND (CAST(:toDate AS timestamp) IS NULL OR o.orderDate <= :toDate)
          AND (:orderNo IS NULL OR o.orderNo = :orderNo)
          AND (:createdBy IS NULL OR o.createdBy = :createdBy)
          AND (:customerId IS NULL OR o.customerId = :customerId)
        """)
    Page<Order> findByFilters(
        @Param("tenantId") Long tenantId,
        @Param("orderType") OrderType orderType,
        @Param("orderSource") OrderSource orderSource,
        @Param("status") OrderStatus status,
        @Param("branchId") Long branchId,
        @Param("fromDate") LocalDateTime fromDate,
        @Param("toDate") LocalDateTime toDate,
        @Param("orderNo") String orderNo,
        @Param("createdBy") Long createdBy,
        @Param("customerId") Long customerId,
        Pageable pageable
    );

    /**
     * Aggregates COMPLETE orders for a shift, grouped by payment method.
     * Uses native SQL so this compiles before {@code orders.shift_id} is present
     * on the Java entity — the column is added in V23.
     */
    @Query(nativeQuery = true, value = """
        SELECT payment_method  AS paymentMethod,
               COALESCE(SUM(total_amount), 0) AS total,
               COUNT(*)        AS orderCount
        FROM   orders
        WHERE  shift_id  = :shiftId
          AND  tenant_id = :tenantId
          AND  status    = 'COMPLETE'
        GROUP BY payment_method
        """)
    List<PaymentMethodSummaryProjection> aggregateByShift(
            @Param("shiftId") Long shiftId,
            @Param("tenantId") Long tenantId
    );

    /**
     * Sales over time, one row per calendar day with at least one COMPLETE order. Read-only.
     *
     * <p><b>Money is reported in components, never blended.</b> subtotal, taxAmount and totalAmount
     * are all returned because tax is collected on behalf of the state and is not revenue; a single
     * figure would be ambiguous by exactly the amount it hides.
     *
     * <p><b>totalAmount is the reconciliation column.</b> It is stored at scale 2 while the two
     * components are stored at scale 6 ({@code OrderService} rounds the sum once at write time), so
     * {@code SUM(subtotal) + SUM(tax_amount)} can differ from {@code SUM(total_amount)} by a few
     * thousandths per order. Every sales report therefore reconciles on {@code total_amount}, the
     * stored column, never on a re-derived sum.
     *
     * <p><b>The status filter provably excludes cancellations.</b> {@code OrderStatus} has exactly
     * two values, and {@code chk_orders_cancellation_stage_status} (V13) forbids a COMPLETE order
     * from carrying a cancellation stage — there is no completed-then-cancelled state to leak in.
     *
     * <p><b>Days with no sales are omitted, not zero-filled</b> — see {@code SalesOverTimeRow}.
     *
     * <p>Cashier is {@code created_by}, matching the existing order-list filter; {@code orders} has
     * no separate cashier column.
     */
    @Query(nativeQuery = true, value = """
        SELECT CAST(o.order_date AS date)                             AS "salesDate",
               COUNT(*)                                               AS "orderCount",
               COALESCE(SUM(o.subtotal), 0)                           AS "subtotal",
               COALESCE(SUM(o.tax_amount), 0)                         AS "taxAmount",
               COALESCE(SUM(o.total_amount), 0)                       AS "totalAmount",
               COALESCE(SUM(o.total_amount), 0) / NULLIF(COUNT(*), 0) AS "averageOrderValue"
        FROM orders o
        WHERE o.tenant_id = :tenantId
          AND o.status = 'COMPLETE'
          AND o.order_date >= :fromInclusive
          AND o.order_date <  :toExclusive
          AND (CAST(:branchId      AS bigint)  IS NULL OR o.branch_id  = CAST(:branchId      AS bigint))
          AND (CAST(:cashierUserId AS bigint)  IS NULL OR o.created_by = CAST(:cashierUserId AS bigint))
          AND (CAST(:orderType     AS varchar) IS NULL OR o.order_type = CAST(:orderType     AS varchar))
        GROUP BY CAST(o.order_date AS date)
        ORDER BY CAST(o.order_date AS date) ASC
        """)
    List<SalesOverTimeAggregate> aggregateSalesOverTime(
            @Param("tenantId") Long tenantId,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive,
            @Param("branchId") Long branchId,
            @Param("cashierUserId") Long cashierUserId,
            @Param("orderType") String orderType
    );

    /**
     * The same series at hourly resolution, one row per (day, hour) with at least one COMPLETE
     * order. Possible only because {@code order_date} is a timestamp rather than a date.
     *
     * <p><b>Calendar hours, not business-day hours.</b> An order at 02:00 belongs to that calendar
     * date, so a restaurant trading past midnight sees its late session split across two dates.
     * Building a business-day concept is deliberately out of scope.
     *
     * <p>A separate hand-written query rather than a granularity parameter on the daily one: the
     * grouping of a report is fixed and is never a filter (D86).
     */
    @Query(nativeQuery = true, value = """
        SELECT CAST(o.order_date AS date)                             AS "salesDate",
               CAST(EXTRACT(HOUR FROM o.order_date) AS integer)       AS "hourOfDay",
               COUNT(*)                                               AS "orderCount",
               COALESCE(SUM(o.subtotal), 0)                           AS "subtotal",
               COALESCE(SUM(o.tax_amount), 0)                         AS "taxAmount",
               COALESCE(SUM(o.total_amount), 0)                       AS "totalAmount",
               COALESCE(SUM(o.total_amount), 0) / NULLIF(COUNT(*), 0) AS "averageOrderValue"
        FROM orders o
        WHERE o.tenant_id = :tenantId
          AND o.status = 'COMPLETE'
          AND o.order_date >= :fromInclusive
          AND o.order_date <  :toExclusive
          AND (CAST(:branchId      AS bigint)  IS NULL OR o.branch_id  = CAST(:branchId      AS bigint))
          AND (CAST(:cashierUserId AS bigint)  IS NULL OR o.created_by = CAST(:cashierUserId AS bigint))
          AND (CAST(:orderType     AS varchar) IS NULL OR o.order_type = CAST(:orderType     AS varchar))
        GROUP BY CAST(o.order_date AS date), CAST(EXTRACT(HOUR FROM o.order_date) AS integer)
        ORDER BY CAST(o.order_date AS date) ASC,
                 CAST(EXTRACT(HOUR FROM o.order_date) AS integer) ASC
        """)
    List<SalesByHourAggregate> aggregateSalesByHour(
            @Param("tenantId") Long tenantId,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive,
            @Param("branchId") Long branchId,
            @Param("cashierUserId") Long cashierUserId,
            @Param("orderType") String orderType
    );

    /**
     * Sales split by payment method, for reconciliation against delivery platforms and card
     * processor statements. Read-only.
     *
     * <p><b>This query and {@link #aggregateSalesOverTime} must produce identical
     * {@code SUM(total_amount)} over the same filters</b> — same orders, same status rule, same
     * window; only the grouping differs. A divergence means a predicate drifted between the two, and
     * that is the defect most likely to survive review because each query looks correct alone. It is
     * pinned by a test rather than left to inspection.
     *
     * <p>A null method groups under {@code UNSPECIFIED} rather than being dropped — dropping it
     * would break exactly that reconciliation. The column is NOT NULL today, so the bucket is
     * defensive.
     *
     * <p>Share is computed against the window total via {@code SUM(SUM(..)) OVER ()}, so the
     * percentages add to 100 within the filtered scope rather than against some outside total.
     */
    @Query(nativeQuery = true, value = """
        SELECT COALESCE(CAST(o.payment_method AS varchar), 'UNSPECIFIED') AS "paymentMethod",
               COUNT(*)                                                   AS "orderCount",
               COALESCE(SUM(o.subtotal), 0)                               AS "subtotal",
               COALESCE(SUM(o.tax_amount), 0)                             AS "taxAmount",
               COALESCE(SUM(o.total_amount), 0)                           AS "totalAmount",
               COALESCE(SUM(o.total_amount), 0) * 100.0
                   / NULLIF(SUM(SUM(o.total_amount)) OVER (), 0)          AS "totalSharePercent"
        FROM orders o
        WHERE o.tenant_id = :tenantId
          AND o.status = 'COMPLETE'
          AND o.order_date >= :fromInclusive
          AND o.order_date <  :toExclusive
          AND (CAST(:branchId      AS bigint)  IS NULL OR o.branch_id  = CAST(:branchId      AS bigint))
          AND (CAST(:cashierUserId AS bigint)  IS NULL OR o.created_by = CAST(:cashierUserId AS bigint))
          AND (CAST(:orderType     AS varchar) IS NULL OR o.order_type = CAST(:orderType     AS varchar))
        GROUP BY COALESCE(CAST(o.payment_method AS varchar), 'UNSPECIFIED')
        ORDER BY COALESCE(SUM(o.total_amount), 0) DESC,
                 COALESCE(CAST(o.payment_method AS varchar), 'UNSPECIFIED') ASC
        """)
    List<SalesByPaymentMethodAggregate> aggregateSalesByPaymentMethod(
            @Param("tenantId") Long tenantId,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive,
            @Param("branchId") Long branchId,
            @Param("cashierUserId") Long cashierUserId,
            @Param("orderType") String orderType
    );
}
