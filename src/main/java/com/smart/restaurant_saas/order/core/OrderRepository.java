package com.smart.restaurant_saas.order.core;

import com.smart.restaurant_saas.common.ScopedConditionAggregate;
import com.smart.restaurant_saas.order.core.enums.OrderSource;
import com.smart.restaurant_saas.order.core.enums.OrderStatus;
import com.smart.restaurant_saas.order.core.enums.OrderType;
import com.smart.restaurant_saas.order.reports.SalesByHourAggregate;
import com.smart.restaurant_saas.order.reports.SalesByPaymentMethodAggregate;
import com.smart.restaurant_saas.order.reports.SalesOverTimeAggregate;
import com.smart.restaurant_saas.tenant.TenantUnscoped;
import java.math.BigDecimal;
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

    /**
     * Receipt lookup (D123): an order is reachable only when the caller supplies both the printed
     * number and the printed total, and only within the branch they are working in.
     *
     * <p>The amount is half of the key on purpose. {@code orderNo} is a per-device display counter
     * and is enumerable, so matching on it alone would let a cashier walk the numbers and read back
     * the per-order amounts the blind count depends on withholding. Requiring the total means a
     * successful match hands back a receipt the caller was already holding.
     *
     * <p><b>Scoped to the branch, not the device.</b> The customer comes back to the restaurant
     * they bought from, not to the machine they paid at — a two-till branch would otherwise send
     * them away whenever they reached the other counter. The branch is read from the caller's own
     * signed device, never from a request field, so it cannot be widened by the client.
     *
     * <p>{@code orderNo} is unique only per device, so two tills in one branch can both issue a
     * "15". Matching the total as well makes a collision require the same number and the same money
     * on the same day's history, and the caller is handed the newest — which is the one a customer
     * is standing there holding.
     */
    @EntityGraph(attributePaths = {"branch", "warehouse", "table", "lines", "lines.product", "lines.recipe"})
    @Query("""
        SELECT o FROM RestaurantOrder o
        WHERE o.tenantId = :tenantId
          AND o.orderNo = :orderNo
          AND o.totalAmount = :totalAmount
          AND o.branch.id = :branchId
        ORDER BY o.id DESC
        """)
    List<Order> findForReceiptLookup(
            @Param("tenantId") Long tenantId,
            @Param("orderNo") String orderNo,
            @Param("totalAmount") BigDecimal totalAmount,
            @Param("branchId") Long branchId);

    // Delete guards (D76/D78): a table — or a section's tables — can only be
    // deleted while no order references it.
    @TenantUnscoped("tableId must be a table already loaded for the acting tenant. As a delete "
        + "guard the unscoped count fails safe — a foreign order can only refuse a delete, never "
        + "permit one — but it does reveal that some tenant has orders on that table.")
    @Query("SELECT COUNT(o) > 0 FROM RestaurantOrder o WHERE o.table.id = :tableId")
    boolean existsByTableId(@Param("tableId") Long tableId);

    @TenantUnscoped("sectionId must be a section already loaded for the acting tenant; same "
        + "fail-safe delete-guard reasoning as existsByTableId.")
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
     * Cash taken on a shift: the sales term of {@code expectedCash} (D121, D93, D100).
     *
     * <p>{@code COMPLETE} only, and {@code total_amount} rather than a re-derived
     * {@code subtotal + tax_amount} — the stored column is the reconciliation column, and the two
     * components are stored at a finer scale than it, so re-deriving drifts by thousandths per
     * order (see {@code aggregateByShift}'s neighbours).
     *
     * <p><b>There is no refunds counterpart.</b> D121's formula subtracts cash refunds, but
     * refunds do not exist anywhere in this system: {@code ORDERS_REFUND} is a seeded permission
     * with no entity, column, endpoint or service behind it. The term is structurally zero rather
     * than omitted by choice, and {@code expectedCash} is complete only for as long as that stays
     * true — whoever builds refunds owns subtracting them here.
     */
    @Query(nativeQuery = true, value = """
        SELECT COALESCE(SUM(total_amount), 0)
        FROM   orders
        WHERE  shift_id       = :shiftId
          AND  tenant_id      = :tenantId
          AND  status         = 'COMPLETE'
          AND  payment_method = 'CASH'
        """)
    BigDecimal sumCompletedCashByShift(
            @Param("shiftId") Long shiftId,
            @Param("tenantId") Long tenantId
    );

    /**
     * Every order attached to a shift, for its detail screen (D125).
     *
     * <p>Cancellations are included, and that is the point: a shift's cancelled orders next to its
     * completed ones is the only cancellation signal the backend can currently produce, since the
     * POS sends no event trail (O54). It is incomplete — everything voided inside the POS before
     * payment is invisible — which is why this feeds a detail screen and not a performance ratio.
     *
     * <p>{@code createdBy} is carried because a colleague may take payment on a ticket somebody
     * else opened, and the order is attributed to whoever took it (D126).
     */
    @Query("""
        SELECT o.id AS id,
               o.orderNo AS orderNo,
               o.orderDate AS orderDate,
               o.status AS status,
               o.paymentMethod AS paymentMethod,
               o.totalAmount AS totalAmount,
               o.createdBy AS createdBy,
               u.fullName AS createdByName
        FROM RestaurantOrder o
        LEFT JOIN User u ON u.id = o.createdBy AND u.tenantId = o.tenantId
        WHERE o.tenantId = :tenantId
          AND o.shift.id = :shiftId
        ORDER BY o.orderDate ASC, o.id ASC
        """)
    List<ShiftOrderProjection> findByShift(
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

    // ---------------------------------------------------------------------------------------
    // Dashboard.
    //
    // These live here rather than in a dashboard-owned repository for one reason: "net sales"
    // must have exactly one definition. It is SUM(subtotal) over COMPLETE orders in the window,
    // it is written above for the reports, and the dashboard calls the same predicate rather
    // than restating it. The failure this avoids is not a crash — it is the dashboard reading
    // 2,847,300 while the sales report reads 2,851,900, after which the owner is right to
    // trust neither. The agreement is pinned by a reconciliation test, because a shared-method
    // rule erodes the first time someone is in a hurry.
    // ---------------------------------------------------------------------------------------

    /**
     * Dashboard KPI row: one set of totals for the window.
     *
     * <p>The aggregate twin of {@link #aggregateSalesOverTime} — same status rule, same half-open
     * window, same branch predicate, no grouping. It exists because the reports return rows and
     * the dashboard needs the total, and re-summing the rows client-side would make the total a
     * second definition that can disagree with the first.
     *
     * <p>{@code netSales} is {@code SUM(subtotal)} — pre-tax, because tax is collected for the
     * state and is not revenue (D100). {@code averageOrderValue} divides the stored
     * {@code total_amount} by the order count, matching the reports exactly; it is not derived
     * from {@code netSales}, which would make the dashboard's average check differ from the
     * report's by the tax rate.
     */
    @Query(nativeQuery = true, value = """
        SELECT COUNT(*)                                               AS "orderCount",
               COALESCE(SUM(o.subtotal), 0)                           AS "netSales",
               COALESCE(SUM(o.tax_amount), 0)                         AS "taxAmount",
               COALESCE(SUM(o.total_amount), 0)                       AS "totalAmount",
               COALESCE(SUM(o.total_amount), 0) / NULLIF(COUNT(*), 0) AS "averageOrderValue"
        FROM orders o
        WHERE o.tenant_id = :tenantId
          AND o.status = 'COMPLETE'
          AND o.order_date >= :fromInclusive
          AND o.order_date <  :toExclusive
          AND (CAST(:branchId AS bigint) IS NULL OR o.branch_id = CAST(:branchId AS bigint))
        """)
    SalesTotalsAggregate aggregateSalesTotals(
            @Param("tenantId") Long tenantId,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive,
            @Param("branchId") Long branchId
    );

    /**
     * The same totals split per branch — the dashboard's branch breakdown.
     *
     * <p><b>Numbers only: no score, no rank colouring.</b> The composite branch score was parked,
     * and the reason is worth keeping next to the query that replaced it: a single score is easy
     * to read and easy to fool. The classic failure is a branch that never takes a physical count,
     * reports zero shrinkage, and therefore scores perfectly on losses — the less it records, the
     * better it looks. A plain breakdown cannot be gamed that way because it does not claim to
     * rank anything.
     *
     * <p>Branches with no COMPLETE orders in the window are omitted, like the daily report's empty
     * days; the caller knows which branches exist and decides whether an absence reads as "closed"
     * or as "no sales".
     */
    @Query(nativeQuery = true, value = """
        SELECT o.branch_id                                            AS "branchId",
               COUNT(*)                                               AS "orderCount",
               COALESCE(SUM(o.subtotal), 0)                           AS "netSales",
               COALESCE(SUM(o.tax_amount), 0)                         AS "taxAmount",
               COALESCE(SUM(o.total_amount), 0)                       AS "totalAmount",
               COALESCE(SUM(o.total_amount), 0) / NULLIF(COUNT(*), 0) AS "averageOrderValue"
        FROM orders o
        WHERE o.tenant_id = :tenantId
          AND o.status = 'COMPLETE'
          AND o.order_date >= :fromInclusive
          AND o.order_date <  :toExclusive
          AND (CAST(:branchId AS bigint) IS NULL OR o.branch_id = CAST(:branchId AS bigint))
        GROUP BY o.branch_id
        ORDER BY COALESCE(SUM(o.subtotal), 0) DESC
        """)
    List<BranchSalesAggregate> aggregateSalesByBranch(
            @Param("tenantId") Long tenantId,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive,
            @Param("branchId") Long branchId
    );

    /**
     * Monthly series, for the dashboard's year range.
     *
     * <p>A third fixed grouping alongside {@link #aggregateSalesOverTime} (day) and
     * {@link #aggregateSalesByHour} (hour), rather than a granularity parameter on any of them —
     * the grouping of a series is fixed and is never a filter (D86). The dashboard picks which of
     * the three to call from the length of the requested range, so one request cannot ask for a
     * year in hours.
     *
     * <p>{@code monthStart} is the first day of the month, so the frontend gets a date to place on
     * an axis rather than a year and a month to reassemble.
     */
    @Query(nativeQuery = true, value = """
        SELECT CAST(DATE_TRUNC('month', o.order_date) AS date)        AS "bucketStart",
               COUNT(*)                                               AS "orderCount",
               COALESCE(SUM(o.subtotal), 0)                           AS "netSales",
               COALESCE(SUM(o.total_amount), 0)                       AS "totalAmount"
        FROM orders o
        WHERE o.tenant_id = :tenantId
          AND o.status = 'COMPLETE'
          AND o.order_date >= :fromInclusive
          AND o.order_date <  :toExclusive
          AND (CAST(:branchId AS bigint) IS NULL OR o.branch_id = CAST(:branchId AS bigint))
        GROUP BY DATE_TRUNC('month', o.order_date)
        ORDER BY DATE_TRUNC('month', o.order_date) ASC
        """)
    List<SalesBucketAggregate> aggregateSalesByMonth(
            @Param("tenantId") Long tenantId,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive,
            @Param("branchId") Long branchId
    );

    /**
     * Channel mix: sales by order type, with each type's share of the window.
     *
     * <p>Share is computed against the window total via {@code SUM(SUM(..)) OVER ()}, the same way
     * the payment-method report does it, so the percentages add to 100 within the filtered scope
     * rather than against some outside total.
     */
    @Query(nativeQuery = true, value = """
        SELECT CAST(o.order_type AS varchar)                          AS "dimension",
               COUNT(*)                                               AS "orderCount",
               COALESCE(SUM(o.subtotal), 0)                           AS "netSales",
               COALESCE(SUM(o.total_amount), 0)                       AS "totalAmount",
               COALESCE(SUM(o.total_amount), 0) * 100.0
                   / NULLIF(SUM(SUM(o.total_amount)) OVER (), 0)      AS "sharePercent"
        FROM orders o
        WHERE o.tenant_id = :tenantId
          AND o.status = 'COMPLETE'
          AND o.order_date >= :fromInclusive
          AND o.order_date <  :toExclusive
          AND (CAST(:branchId AS bigint) IS NULL OR o.branch_id = CAST(:branchId AS bigint))
        GROUP BY CAST(o.order_type AS varchar)
        ORDER BY COALESCE(SUM(o.total_amount), 0) DESC
        """)
    List<SalesMixAggregate> aggregateSalesByOrderType(
            @Param("tenantId") Long tenantId,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive,
            @Param("branchId") Long branchId
    );

    /**
     * Cancellations by stage over the window — count and value of what was not collected.
     *
     * <p><b>Grouped by stage, never by hour.</b> The cancellation timestamp is not reliable, so
     * any hourly view of this data would be confidently wrong; the stage is recorded correctly and
     * is the dimension that carries the finding anyway. A cancellation before the kitchen costs
     * nothing but a till correction; one after the food was cooked is pure loss.
     *
     * <p>{@code chk_orders_cancellation_stage_status} (V13) guarantees every CANCELLED order
     * carries a stage and no COMPLETE order does, so the grouping key is never null and this
     * cannot silently double-count against the sales figures.
     */
    @Query(nativeQuery = true, value = """
        SELECT CAST(o.cancellation_stage AS varchar)   AS "dimension",
               COUNT(*)                                AS "orderCount",
               COALESCE(SUM(o.subtotal), 0)            AS "netSales",
               COALESCE(SUM(o.total_amount), 0)        AS "totalAmount",
               CAST(NULL AS numeric)                   AS "sharePercent"
        FROM orders o
        WHERE o.tenant_id = :tenantId
          AND o.status = 'CANCELLED'
          AND o.order_date >= :fromInclusive
          AND o.order_date <  :toExclusive
          AND (CAST(:branchId AS bigint) IS NULL OR o.branch_id = CAST(:branchId AS bigint))
        GROUP BY CAST(o.cancellation_stage AS varchar)
        ORDER BY COALESCE(SUM(o.total_amount), 0) DESC
        """)
    List<SalesMixAggregate> aggregateCancellationsByStage(
            @Param("tenantId") Long tenantId,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive,
            @Param("branchId") Long branchId
    );

    /**
     * Cost coverage: how much of the window's sales has had its cost posted to the ledger.
     *
     * <p>The single number that says whether the rest of the cost section is worth reading. Below
     * 100% means some sales carry revenue and no cost, so every margin computed over the window is
     * overstated — it is the period-level twin of alert A1.
     *
     * <p><b>Three buckets, not a ratio, and the third is the reason.</b> An order is covered when
     * it has consumption lines and all of their documents are POSTED, and uncovered when any
     * document is not. The third case — an order with <em>no</em> consumption line at all — is
     * neither, and folding it into either one would be a lie in a different direction each way.
     * It happens when a product has no active recipe, so nothing was ever scheduled to be
     * consumed (D14); calling that "covered" would claim its cost was posted, and calling it
     * "uncovered" would imply a stuck document that does not exist. It is reported separately so
     * the screen can say which of the two shapes the gap has.
     *
     * <p><b>Measured in {@code subtotal}, not order count.</b> One cancelled banquet matters more
     * than forty covered coffees, and a count would weigh them equally.
     *
     * <p>The LEFT JOINs are what make the third bucket observable: an inner join would drop
     * exactly the orders with no consumption line, and the ratio would then read as 100% while
     * silently excluding them.
     */
    @Query(nativeQuery = true, value = """
        WITH order_coverage AS (
            SELECT o.id                                                        AS order_id,
                   MAX(o.subtotal)                                             AS subtotal,
                   COUNT(ocl.id)                                               AS consumption_lines,
                   COUNT(ocl.id) FILTER (WHERE doc.status <> 'POSTED')         AS unposted_lines
            FROM orders o
            JOIN order_line ol                 ON ol.order_id = o.id
            LEFT JOIN order_consumption_line ocl ON ocl.order_line_id = ol.id
            LEFT JOIN order_consumption doc      ON doc.id = ocl.doc_id
            WHERE o.tenant_id = :tenantId
              AND o.status = 'COMPLETE'
              AND o.order_date >= :fromInclusive
              AND o.order_date <  :toExclusive
              AND (CAST(:branchId AS bigint) IS NULL OR o.branch_id = CAST(:branchId AS bigint))
            GROUP BY o.id
        )
        SELECT COUNT(*)                                                         AS "orderCount",
               COALESCE(SUM(subtotal), 0)                                       AS "totalSales",
               COALESCE(SUM(subtotal) FILTER (WHERE consumption_lines > 0
                                                AND unposted_lines = 0), 0)     AS "postedSales",
               COALESCE(SUM(subtotal) FILTER (WHERE consumption_lines > 0
                                                AND unposted_lines > 0), 0)     AS "unpostedSales",
               COALESCE(SUM(subtotal) FILTER (WHERE consumption_lines = 0), 0)  AS "noConsumptionSales"
        FROM order_coverage
        """)
    CostCoverageAggregate aggregateCostCoverage(
            @Param("tenantId") Long tenantId,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive,
            @Param("branchId") Long branchId
    );

    /**
     * Dashboard alert C3: orders cancelled after the food was cooked, per branch.
     *
     * <p>{@code IN_KITCHEN_COOKED} and {@code AFTER_DONE} are the two stages at which the
     * ingredients were already committed to a pan. The other two — before the kitchen, and in the
     * kitchen but not yet cooked — cost a till correction and nothing else, so including them
     * would bury the stage that actually loses food.
     *
     * <p><b>The value is revenue not collected, and it understates the loss.</b> Cooked-and-
     * cancelled food is not deducted from stock today, so the ingredients are still carried as
     * though they existed: the real cost is this figure plus a stock write-off that never
     * happened. Stated here because the natural assumption on reading the number is the opposite
     * one — that it is the full picture.
     *
     * <p>Rewritten from a comparison ("more than twice the branch's four-week average") to an
     * absolute condition, because the dashboard shows no comparisons. Any such cancellation in
     * the window is reported; for a well-run branch that is zero rows and no strip entry.
     */
    @Query(nativeQuery = true, value = """
        SELECT o.branch_id                        AS "branchId",
               CAST(NULL AS bigint)               AS "warehouseId",
               COUNT(*)                           AS "itemCount",
               COALESCE(SUM(o.total_amount), 0)   AS "totalValue",
               MIN(o.order_date)                  AS "oldestAt"
        FROM orders o
        WHERE o.tenant_id = :tenantId
          AND o.status = 'CANCELLED'
          AND o.cancellation_stage IN ('IN_KITCHEN_COOKED', 'AFTER_DONE')
          AND o.order_date >= :fromInclusive
          AND (CAST(:branchId AS bigint) IS NULL OR o.branch_id = CAST(:branchId AS bigint))
        GROUP BY o.branch_id
        """)
    List<ScopedConditionAggregate> aggregateAfterCookCancellations(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("fromInclusive") LocalDateTime fromInclusive
    );

    /**
     * Dashboard alert D1: a branch trading with a shift open and taking no orders.
     *
     * <p>Catches a dead POS, a stuck outbox or a network partition — failures that nothing else on
     * the dashboard will notice, because every other number is computed from orders that did
     * arrive. A branch whose till has silently stopped reporting looks, to every sales figure, like
     * a branch having a quiet day.
     *
     * <p><b>The open shift is what makes this absolute rather than a comparison.</b> The original
     * condition measured against the same weekday's average, which the no-comparisons rule
     * removed; an open shift is a better signal anyway, because someone opened that drawer to
     * trade, so silence contradicts the branch's own account of itself rather than a historical
     * pattern. A branch with no shift open is closed, and a closed branch taking no orders is not
     * a fault.
     *
     * <p>{@code oldestAt} is the branch's last order at any time, and is null for a branch that
     * has opened a shift and never sold anything — which is the strongest form of this alert and
     * must not be dressed up with a fabricated timestamp.
     */
    @Query(nativeQuery = true, value = """
        SELECT trading.branch_id          AS "branchId",
               CAST(NULL AS bigint)       AS "warehouseId",
               1                          AS "itemCount",
               CAST(NULL AS numeric)      AS "totalValue",
               last_order.last_at         AS "oldestAt"
        FROM (
            SELECT d.branch_id, COUNT(*) AS open_shifts
            FROM shift s
            JOIN device d ON d.id = s.device_id
            WHERE s.tenant_id = :tenantId
              AND s.status = 'OPEN'
              AND (CAST(:branchId AS bigint) IS NULL OR d.branch_id = CAST(:branchId AS bigint))
            GROUP BY d.branch_id
        ) trading
        LEFT JOIN (
            SELECT o.branch_id, MAX(o.order_date) AS last_at
            FROM orders o
            WHERE o.tenant_id = :tenantId
            GROUP BY o.branch_id
        ) last_order ON last_order.branch_id = trading.branch_id
        WHERE last_order.last_at IS NULL
           OR last_order.last_at < :silentSince
        """)
    List<ScopedConditionAggregate> aggregateSilentTradingBranches(
            @Param("tenantId") Long tenantId,
            @Param("branchId") Long branchId,
            @Param("silentSince") LocalDateTime silentSince
    );
}
